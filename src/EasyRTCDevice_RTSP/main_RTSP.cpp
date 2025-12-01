#include <stdlib.h>
#include <stdio.h>
#include <string.h>



#include "EasyRTSPClientAPI.h"

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
#pragma comment(lib, "libEasyRTSPClient.lib")
#else
#include <unistd.h>
#endif
extern "C"
{
#include "../common/osthread.h"
}


typedef struct __EASYRTC_CHANNEL_T
{
	int			id;
	Easy_Handle rtspHandle;
	EASYRTC_HANDLE  rtcHandle;

	char			rtspURL[1024];

	char			channelID[32];
	unsigned int	videoCodecId;
	unsigned int	audioCodecId;

	int				sendFlag;

}EASYRTC_CHANNEL_T;

#define ENABLE_AUDIO	0x01


int __EasyRTC_Data_Callback(void* userptr, const char* peerUUID, EASYRTC_DATA_TYPE_ENUM_E dataType, int codecID, int isBinary, char* data, int size, int keyframe, unsigned long long pts)
{
	EASYRTC_CHANNEL_T* pChannel = (EASYRTC_CHANNEL_T*)userptr;

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

		// ��ʱ���û���������Ƶ
		pChannel->sendFlag = 0x01;
	}
	else if (EASYRTC_CALLBACK_TYPE_START_AUDIO == dataType)
	{
		printf("Start Audio..\n");
	}
	else if (EASYRTC_CALLBACK_TYPE_STOP_VIDEO == dataType)
	{
		printf("Stop Video..\n");

		// ��ʱ�û��ѹر���Ƶ��ֹͣ����
		pChannel->sendFlag = 0x00;
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
	else if (EASYRTC_CALLBACK_TYPE_PASSIVE_CALL == dataType)
	{
		printf("Passive call..  peerUUID[%s]\n", peerUUID);

		return 1;		// 返回1表示自动接受	如果返回0,则需要调用EasyRTC_Device_PassiveCallResponse来处理该请求: 接受或拒绝
	}                
	else if (EASYRTC_CALLBACK_TYPE_PEER_CLOSED == dataType)
	{
		printf("Peer Close..\n");
	}

	return 0;
}


/*
ws://demo.easygbs.com:30401
wss://demo.easygbs.com:30402


https://demo.easygbs.com:10010/
�û������붼�� easygbs

��ʾƽ̨������
RTC�豸���豸�����ͨ��������Ҫ20λ�豸����ĵ�11-13Ϊ�ǡ�800���������ϵĻᱻ����ע�ᡣ
*/

int Easy_APICALL __RTSPClientCallBack(int _chid, void* _chPtr, int _frameType, char* _pBuf, EASY_FRAME_INFO* _frameInfo)
{
	EASYRTC_CHANNEL_T* pChannel = (EASYRTC_CHANNEL_T*)_chPtr;

	if (_frameType == EASY_SDK_VIDEO_FRAME_FLAG)
	{
		if (pChannel->sendFlag == 0x01)
		{
			unsigned long long pts = _frameInfo->timestamp_sec * 1000 + _frameInfo->timestamp_usec / 1000;
			EasyRTC_Device_SendVideoFrame(pChannel->rtcHandle, pChannel->id, _pBuf, _frameInfo->length, _frameInfo->type, pts);
		}
	}
	else if (_frameType == EASY_SDK_AUDIO_FRAME_FLAG)
	{
		if (pChannel->sendFlag == 0x01)
		{
			unsigned long long pts = _frameInfo->timestamp_sec * 1000 + _frameInfo->timestamp_usec / 1000;
			EasyRTC_Device_SendAudioFrame(pChannel->rtcHandle, pChannel->id, _pBuf, _frameInfo->length, pts);
		}
	}
	else if (_frameType == EASY_SDK_EVENT_FRAME_FLAG)
	{
		if (NULL == _pBuf && NULL == _frameInfo)
		{
			printf("Connecting:%s ...\n", pChannel->rtspURL);
		}

		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_ERROR)
		{
			printf("Error:%s, %d :%s ...\n", pChannel->rtspURL, EasyRTSP_GetErrCode(pChannel->rtspHandle), _pBuf ? _pBuf : "null");
		}

		else if (NULL != _frameInfo && _frameInfo->codec == EASY_SDK_EVENT_CODEC_EXIT)
		{
			printf("Exit:%s,Error:%d ...\n", pChannel->rtspURL, EasyRTSP_GetErrCode(pChannel->rtspHandle));
		}
	}
	else if (_frameType == EASY_SDK_MEDIA_INFO_FLAG)
	{
		if (_pBuf != NULL)
		{
			EASY_MEDIA_INFO_T mediainfo;
			memset(&mediainfo, 0x00, sizeof(EASY_MEDIA_INFO_T));
			memcpy(&mediainfo, _pBuf, sizeof(EASY_MEDIA_INFO_T));
			printf("RTSP DESCRIBE Get Media Info: video:%u fps:%u audio:%u channel:%u sampleRate:%u \n",
				mediainfo.u32VideoCodec, mediainfo.u32VideoFps, mediainfo.u32AudioCodec, mediainfo.u32AudioChannel, mediainfo.u32AudioSamplerate);

			EASYRTC_CODEC videoCodecID = EASYRTC_CODEC_H264;
			if (mediainfo.u32VideoCodec == EASY_SDK_VIDEO_CODEC_H264)	videoCodecID = EASYRTC_CODEC_H264;
			else if (mediainfo.u32VideoCodec == EASY_SDK_VIDEO_CODEC_H265)	videoCodecID = EASYRTC_CODEC_H265;

			EASYRTC_CODEC audioCodecID = EASYRTC_CODEC_MULAW;
			if (mediainfo.u32AudioCodec == EASY_SDK_AUDIO_CODEC_G711U)	audioCodecID = EASYRTC_CODEC_MULAW;
			else if (mediainfo.u32AudioCodec == EASY_SDK_AUDIO_CODEC_G711A)	audioCodecID = EASYRTC_CODEC_ALAW;

			EasyRTC_Device_SetChannelInfo(pChannel->rtcHandle, 0, pChannel->channelID, videoCodecID, audioCodecID);
		}
	}
	else if (_frameType == EASY_SDK_SEI_FRAME_FLAG)
	{
	}
	return 0;
}

int trim(char* str)
{
	int len = (int)strlen(str);
	for (int i = 0; i < len; i++)
	{
		if ((unsigned char)str[i] == '\r' ||
			(unsigned char)str[i] == '\n')
		{
			str[i] = '\0';
		}
	}

	return 0;
}

int main(int argc, char* argv[])
{
	const char* serverIP = "demo.easygbs.com";// "36.34.0.68";// "demo.easygbs.com";
	const int serverPort = 30401;
	const char* accessToken = "123";

	char deviceID[64] = { 0 };
	char rtspURL[1024] = { 0 };

	if (argc > 1)
	{
		strcpy(deviceID, argv[1]);		//"34020000008000877790"

		if (argc > 2)
		{
			strcpy(rtspURL, argv[2]);
		}
	}

	FILE* fd = fopen("DeviceID.txt", "rb");
	if (fd)
	{
		fgets(deviceID, sizeof(deviceID), fd);
		fgets(rtspURL, sizeof(rtspURL), fd);
		fclose(fd);
	}

	trim(deviceID);
	trim(rtspURL);

	int len = (int)strlen(deviceID);
	if (len < 20)
	{
		if (len < 1)		printf("Not found deviceID.\n");
		else
		{
			printf("Device ID error.\n");
		}
		return 0;
	}


	// ��ʼ��
	EasyRTC_Device_Init();

	EASYRTC_CHANNEL_T	easyRTCChannel;
	memset(&easyRTCChannel, 0x00, sizeof(EASYRTC_CHANNEL_T));

	easyRTCChannel.id = 0;	
	strcpy(easyRTCChannel.rtspURL, rtspURL);

	// �������
	EasyRTC_Device_Create(&easyRTCChannel.rtcHandle, serverIP, serverPort, accessToken, "device", deviceID, 1, __EasyRTC_Data_Callback, (void*)&easyRTCChannel);
	sprintf(easyRTCChannel.channelID, "340200000013200000%02d", 1);		// ͨ��ID
	// ��ʱ������һ��ͨ��ID, �������Ƶ���벻ͬ,������ڻ�ȡ��ʵ�ʱ����������
	EasyRTC_Device_SetChannelInfo(easyRTCChannel.rtcHandle, easyRTCChannel.id, easyRTCChannel.channelID, EASYRTC_CODEC_H264, EASYRTC_CODEC_ALAW);

	Easy_Handle rtspHandle = NULL;
	unsigned int mediaType = EASY_SDK_VIDEO_FRAME_FLAG | EASY_SDK_AUDIO_FRAME_FLAG;
	int timeoutSecs = 5;
	EasyRTSP_Init(&rtspHandle);
	EasyRTSP_SetCallback(rtspHandle, __RTSPClientCallBack);
	EasyRTSP_OpenStream(rtspHandle, 0, rtspURL, EASY_RTP_OVER_TCP, mediaType, NULL, NULL, (void*)&easyRTCChannel, 1000, 0, 0x01, timeoutSecs);

	printf("�����λس�����.\n");
	getchar();
	getchar();
	getchar();

	EasyRTSP_CloseStream(rtspHandle);
	EasyRTSP_Deinit(&rtspHandle);

	// �ͷž��
	EasyRTC_Device_Release(&easyRTCChannel.rtcHandle);

	// ����ʼ��
	EasyRTC_Device_Deinit();

	return 0;
}

