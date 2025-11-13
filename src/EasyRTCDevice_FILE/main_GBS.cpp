#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#if MAIN_LIVEKIT==0x00

extern "C"
{
#include "EasyRTCDeviceAPI.h"
}
#ifdef _WIN32
#include <winsock2.h>
#ifdef _DEBUG
#pragma comment(lib, "../../windows/x64/Debug/EasyRTCDevice.lib")
#else
#pragma comment(lib, "../../windows/x64/Release/EasyRTCDevice.lib")
#endif
#pragma comment(lib, "winmm.lib")
#else
#include <unistd.h>
#endif
extern "C"
{
#include "../common/osthread.h"
}

#include "FileParser/ESFileParser.h"
#include "FileParser/buff.h"
#include "FileParser/g711.h"
#include "gettimeofdayEx.h"


int channelNum = 2;

#define ENABLE_AUDIO	0x01

void SleepEx(int ms)
{
#ifdef _WIN32
	timeBeginPeriod(1);
	Sleep(ms);
	timeEndPeriod(1);

#else
	usleep(ms * 1000);
#endif
}

int		videoCodecID = 0;
int		audioCodecID = 0;
unsigned long long u64_max_value = 0xFFFFFFFFFFFFFFFF;		// unsigned long long最大值
unsigned long long u64_max_dts = u64_max_value / 10000;		// 因为库中还要再乘10000,所以此处使用的dts为unsigned long long最大值除以10000
unsigned long long u64_init_dts = 0;							// dts 初始值
unsigned long long videoPTS = u64_init_dts;					// 视频时间戳
unsigned long long audioDTS = u64_init_dts;					// 音频时间戳
#ifdef _WIN32
DWORD WINAPI __ReadVideoFileThread(void* lpParam)
#else
void* __ReadVideoFileThread(void* lpParam)
#endif
{
	OSTHREAD_OBJ_T* pThread = (OSTHREAD_OBJ_T*)lpParam;
	EASYRTC_HANDLE  rtcHandle = (EASYRTC_HANDLE)pThread->userPtr;

	pThread->flag = THREAD_STATUS_RUNNING;

	printf("read video file thread startup. [%d]\n", pThread->customId);

	// 读文件
	ESFileParser	esFileParse;
	if (0 == esFileParse.OpenEsFile("1M.h264", true))
	{
		unsigned int codecId = esFileParse.GetVideoCodec();
		if (codecId == 0x1C)	videoCodecID = EASYRTC_CODEC_H264;				// H264编码
		else if (codecId == 0xAE)	videoCodecID = EASYRTC_CODEC_H265;			// H265编码
		else	videoCodecID = EASYRTC_CODEC_H264;								// H264编码

		int interval_ms = 40;

		struct timeval tvStartTime = { 0,0 };
		while (1)
		{
			if (pThread->flag == THREAD_STATUS_EXIT)			break;

#if ENABLE_AUDIO == 0x01
			if (audioDTS < 1)		// 如果还未读到音频,则等待
			{
				SleepEx(1);
				continue;
			}
#endif

			//if (NULL == g_pRtcRtpTransceiverV)
			//{
			//	Sleep(100);
			//	continue;
			//}

			char* frameData = NULL;
			int frameSize = 0;
			int frameType = 0;
			if (esFileParse.ReadFrame(&frameData, &frameSize, &frameType) == 0)
			{
				videoPTS += interval_ms;
				if (videoPTS > u64_max_dts)
				{
					videoPTS = interval_ms;
				}

				//EasyRTC_SendVideoFrame(easyrtcDeviceHandle, (char*)frameData, frameSize, frameType, videoPTS);

				for (int i = 0; i < channelNum; i++)
				{
					EasyRTC_Device_SendVideoFrame(rtcHandle, i, frameData, frameSize, frameType, videoPTS);
				}
			}

			//Sleep(40);

			int delay = interval_ms;
			if (tvStartTime.tv_sec > 0)
			{
				struct timeval tvEndTime;
				gettimeofdayEx(&tvEndTime, NULL);
				unsigned long long u64Interval = 0;
				if (tvEndTime.tv_sec == tvStartTime.tv_sec)
				{
					u64Interval = (tvEndTime.tv_usec - tvStartTime.tv_usec) / 1000;
				}
				else
				{
					u64Interval = ((unsigned long long)(tvEndTime.tv_sec - tvStartTime.tv_sec) - 1) * 1000;
					u64Interval += (1000000 - tvStartTime.tv_usec + tvEndTime.tv_usec) / 1000;
				}

				delay -= (int)u64Interval;
			}

			if (delay > 0 && delay <= interval_ms)
			{
				SleepEx(delay);
			}
			else
			{
				printf("video delay: %d\n", delay);
			}
			gettimeofdayEx(&tvStartTime, NULL);
		}
	}

	pThread->flag = THREAD_STATUS_INIT;


	printf("read video file thread shutdown. [%d]\n", pThread->customId);

	return 0;
}

#ifdef _WIN32
DWORD WINAPI __ReadAudioFileThread(void* lpParam)
#else
void* __ReadAudioFileThread(void* lpParam)
#endif
{
	OSTHREAD_OBJ_T* pThread = (OSTHREAD_OBJ_T*)lpParam;
	EASYRTC_HANDLE  rtcHandle = (EASYRTC_HANDLE)pThread->userPtr;

	pThread->flag = THREAD_STATUS_RUNNING;

	printf("read audio file thread startup. [%d]\n", pThread->customId);

	int samplerate = 8000;
	int channels = 1;

	int pcm_buf_size_per_sec = samplerate * 16 * channels / 8;			// 每秒数据量		比如8000*16*1/8=16000
	int pcm_buf_size_per_ms = pcm_buf_size_per_sec / 1000;				// 每毫秒数据量		16000/1000=16

	int interval_ms = 20;												// 间隔20毫秒
	int bytes_per_20ms = pcm_buf_size_per_ms * interval_ms;				// 每20毫秒数据量


	BUFF_T	buff;
	memset(&buff, 0x00, sizeof(BUFF_T));
	BUFF_MALLOC(&buff, bytes_per_20ms + 1);

	BUFF_T	bufG711;
	memset(&bufG711, 0x00, sizeof(BUFF_T));
	BUFF_MALLOC(&bufG711, bytes_per_20ms + 1);

	// 读PCM文件
	FILE* fAudio = fopen("music.pcm", "rb");		// 8K,16bit,1ch
	if (NULL != fAudio)
	{
		audioCodecID = EASYRTC_CODEC_ALAW;

		struct timeval tvStartTime = { 0,0 };
		while (1)
		{
			if (pThread->flag == THREAD_STATUS_EXIT)			break;

			buff.bufpos = fread(buff.pbuf, 1, bytes_per_20ms, fAudio);
			if (buff.bufpos < bytes_per_20ms)
			{
				fseek(fAudio, 0, SEEK_SET);
				buff.bufpos = fread(buff.pbuf, 1, bytes_per_20ms, fAudio);
			}
			if (buff.bufpos == bytes_per_20ms)
			{
				// 转码为G711ALAW
				int idx = 0;
				for (int i = 0; i < buff.bufpos; i += 2) {

					unsigned char uc1 = buff.pbuf[i];
					unsigned char uc2 = buff.pbuf[i + 1];

					short s = ((uc2 << 8) & 0xFF00) | (uc1 & 0xFF);
					bufG711.pbuf[idx++] = linear2alaw(s);
				}

				bufG711.bufpos = idx;

				// 时间戳递增
				audioDTS += interval_ms;
				if (audioDTS > u64_max_dts)
				{
					audioDTS = interval_ms;
				}

				for (int i = 0; i < channelNum; i++)
				{
					EasyRTC_Device_SendAudioFrame(rtcHandle, i, (char*)bufG711.pbuf, bufG711.bufpos, audioDTS);
				}
			}
			int delay = interval_ms;
			if (tvStartTime.tv_sec > 0)
			{
				struct timeval tvEndTime;
				gettimeofdayEx(&tvEndTime, NULL);
				unsigned long long u64Interval = 0;

				if (tvEndTime.tv_sec == tvStartTime.tv_sec)
				{
					u64Interval = (tvEndTime.tv_usec - tvStartTime.tv_usec) / 1000;
				}
				else
				{
					u64Interval = (tvEndTime.tv_sec - tvStartTime.tv_sec - 1) * 1000;
					u64Interval += (1000000 - tvStartTime.tv_usec + tvEndTime.tv_usec) / 1000;
				}

				delay -= (int)u64Interval;
			}


			if (delay > 0 && delay <= interval_ms)
			{
#ifdef _WIN32
				timeBeginPeriod(1);
#endif
				SleepEx(delay);
#ifdef _WIN32
				timeEndPeriod(1);
#endif
			}
			else
			{
				printf("audio delay: %d\n", delay);
			}

			gettimeofdayEx(&tvStartTime, NULL);
		}
	}
	BUFF_FREE(&buff);
	BUFF_FREE(&bufG711);
	pThread->flag = THREAD_STATUS_INIT;


	printf("read audio file thread shutdown. [%d]\n", pThread->customId);

	return 0;
}


int __EasyRTC_Data_Callback(void* userptr, const char* peerUUID, EASYRTC_DATA_TYPE_ENUM_E dataType, int codecID, int isBinary, char* data, int size, int keyframe, unsigned long long pts)
{
	if (EASYRTC_CALLBACK_TYPE_DNS_FAIL == dataType)
	{
		printf("DNS failed.\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_CONNECTING == dataType)
	{
		printf("Connecting...\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_CONNECTED == dataType)
	{
		printf("Connected.\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_CONNECT_FAIL == dataType)
	{
		printf("Connect failed..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_DISCONNECT == dataType)
	{
		printf("Disconnect..\n");
	}

	else if (EASYRTC_CALLBACK_TYPE_START_VIDEO == dataType)
	{
		printf("Start Video..\n");

		// 此时有用户请求发送视频
	}
	else if (EASYRTC_CALLBACK_TYPE_START_AUDIO == dataType)
	{
		printf("Start Audio..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_STOP_VIDEO == dataType)
	{
		printf("Stop Video..\n");

		// 此时用户已关闭视频，停止发送
	}
	else if (EASYRTC_CALLBACK_TYPE_STOP_AUDIO == dataType)
	{
		printf("Stop Audio..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_VIDEO == dataType)
	{
		printf("OnPeerVideo..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_AUDIO == dataType)
	{
		printf("OnPeerAudio..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_LOCAL_AUDIO == dataType)
	{
		printf("Local audio..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_CONNECTED == dataType)
	{
		printf("Peer Connected..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_CONNECT_FAIL == dataType)
	{
		printf("Peer Connect failed..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_DISCONNECT == dataType)
	{
		printf("Peer Disconnect..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_PEER_CLOSED == dataType)
	{
		printf("Peer Close..\n");
	}

    return 0;
}




typedef struct __RTC_CHANNEL_T
{
	int		channelId;
	EASYRTC_HANDLE  rtcHandle;
}RTC_CHANNEL_T;


int main(int argc, char* argv[])
{
	const char* serverIP = "demo.easygbs.com";
    const int serverPort = 30401;
    const char* accessToken = "123";

	char deviceID[64] = { 0 };

	if (argc > 1)
	{
		strcpy(deviceID, argv[1]);		//"34020000008000877790"
	}

	FILE* fd = fopen("DeviceID.txt", "rb");
	if (fd)
	{
		fgets(deviceID, sizeof(deviceID), fd);
		fclose(fd);
	}

	int len = (int)strlen(deviceID);
	for (int i = 0; i < len; i++)
	{
		if ((unsigned char)deviceID[i] == '\r' ||
			(unsigned char)deviceID[i] == '\n')
		{
			deviceID[i] = '\0';
		}
	}

	// RTC设备端设备编码和通道编码需要20位设备编码, 第11-13为是【800】，不符合的会被限制注册。

	if (len < 20)
	{
		if (len<1)		printf("Not found deviceID.\n");
		else
		{
			printf("Device ID error.\n");
		}
		return 0;
	}
	

	// 初始化
	EasyRTC_Device_Init();

        EASYRTC_HANDLE  rtcHandle = NULL;

	// 创建句柄
	EasyRTC_Device_Create(&rtcHandle, serverIP, serverPort, accessToken, "device", deviceID, channelNum, __EasyRTC_Data_Callback, NULL);
	for (int i = 0; i < channelNum; i++)
	{
		char code[64] = { 0 };
		sprintf(code, "340200000013200000%02d", i+1);
		EasyRTC_Device_SetChannelInfo(rtcHandle, i, code, EASYRTC_CODEC_H264, EASYRTC_CODEC_ALAW);
	}

	// 创建读视频文件线程
	OSTHREAD_OBJ_T* readVideoFileThread = NULL;
	CreateOSThread(&readVideoFileThread, __ReadVideoFileThread, (void*)rtcHandle, 0);

#if ENABLE_AUDIO==0x01
	// 创建读音频文件线程
	OSTHREAD_OBJ_T* readAudioFileThread = NULL;
	CreateOSThread(&readAudioFileThread, __ReadAudioFileThread, (void*)rtcHandle, 0);
#endif	


	printf("按三次回车结束.\n");
	getchar();
	getchar();
        getchar();

	DeleteOSThread(&readVideoFileThread);
#if ENABLE_AUDIO==0x01
	DeleteOSThread(&readAudioFileThread);				// 关闭读音频文件线程
#endif


	// 释放句柄
	EasyRTC_Device_Release(&rtcHandle);

	// 反初始化
	EasyRTC_Device_Deinit();

    return 0;
}

#endif
