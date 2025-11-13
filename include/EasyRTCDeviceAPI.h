#ifndef __GBS_DEVICE_API_H__
#define __GBS_DEVICE_API_H__

#ifdef _WIN32
#define EASYRTC_DEVICE_API  __declspec(dllexport)
#ifndef LIB_APICALL
#define LIB_APICALL  __stdcall
#endif
//#define WIN32_LEAN_AND_MEAN
#include <stdbool.h>
#else
#define EASYRTC_DEVICE_API  __attribute__ ((visibility("default")))
#define LIB_APICALL 
#define bool int
#endif

typedef enum {
    EASYRTC_CODEC_NO    =   0,
    EASYRTC_CODEC_H264 = 1,                                                           //!< H264 video codec
    EASYRTC_CODEC_OPUS = 2,                                                           //!< OPUS audio codec
    EASYRTC_CODEC_VP8 = 3,                                                            //!< VP8 video codec.
    EASYRTC_CODEC_MULAW = 4,                                                          //!< MULAW audio codec
    EASYRTC_CODEC_ALAW = 5,                                                           //!< ALAW audio codec
    EASYRTC_CODEC_H265 = 6,                                                           //!< H265 video codec
    EASYRTC_CODEC_AAC = 7                                                             //!< AAC audio codec
} EASYRTC_CODEC;

typedef enum __EASYRTC_DATA_TYPE_ENUM_E
{
    // 以下四个连接状态必须和底层库中的CONNECT_STATUS_E定义一致

    EASYRTC_CALLBACK_TYPE_DNS_FAIL = 0x01,		    // DNS解析失败
    EASYRTC_CALLBACK_TYPE_CONNECTING,       		// 连接中
    EASYRTC_CALLBACK_TYPE_CONNECTED,				// 连接成功
    EASYRTC_CALLBACK_TYPE_CONNECT_FAIL,				// 连接失败
    EASYRTC_CALLBACK_TYPE_DISCONNECT,				// 连接断开

    EASYRTC_CALLBACK_TYPE_START_VIDEO = 0x10,		// 对端请求视频数据
    EASYRTC_CALLBACK_TYPE_START_AUDIO,		        // 对端请求音频数据
    EASYRTC_CALLBACK_TYPE_STOP_VIDEO,	        	// 对端关闭视频数据
    EASYRTC_CALLBACK_TYPE_STOP_AUDIO,		        // 对端关闭音频数据

    EASYRTC_CALLBACK_TYPE_PEER_VIDEO = 0x20,		// 回调对端的视频数据
    EASYRTC_CALLBACK_TYPE_PEER_AUDIO,				// 回调对端的音频数据
    EASYRTC_CALLBACK_TYPE_PEER_CUSTOM_DATA,			// 回调对端的DataChannel数据
    EASYRTC_CALLBACK_TYPE_LOCAL_AUDIO,				// 回调本地回音消除后的音频数据
    EASYRTC_CALLBACK_TYPE_PEER_CONNECTED,           // 连接对端成功
    EASYRTC_CALLBACK_TYPE_PEER_CONNECT_FAIL,        // 连接对端失败
    EASYRTC_CALLBACK_TYPE_PEER_DISCONNECT,          // 与对端连接断开
    EASYRTC_CALLBACK_TYPE_PEER_CLOSED,              // 与对端连接已关闭


    //EASYRTC_CALLBACK_TYPE_PASSIVE_CALL = 0x20,		// 被动呼叫
    //EASYRTC_CALLBACK_TYPE_PROACTIVE_CALL_RESPONSE,  // 主动呼叫响应

    //EASYRTC_CALLBACK_TYPE_CALL_ACCEPT,              // 对方接受呼叫
    //EASYRTC_CALLBACK_TYPE_CALL_REFUSE,              // 对方拒绝呼叫
    //EASYRTC_CALLBACK_TYPE_HANG_UP,                  // 对方挂断

    //EASYRTC_CALLBACK_TYPE_ONLINE_DEVICE = 0x30,   // 在线设备列表
}EASYRTC_DATA_TYPE_ENUM_E;

// 数据回调
typedef int (*EasyRTC_Data_Callback)(void* userptr, const char* peerUUID, EASYRTC_DATA_TYPE_ENUM_E dataType, int codecID, int isBinary, char* data, int size, int keyframe, unsigned long long pts);

typedef void* EASYRTC_HANDLE;

#ifdef __cplusplus
extern "C"
{
#endif

    // 初始化环境
    int	EASYRTC_DEVICE_API	EasyRTC_Device_Init();

    // 创建句柄
    int	EASYRTC_DEVICE_API	EasyRTC_Device_Create(EASYRTC_HANDLE* handle, const char* serverAddr, const int serverPort, const char* accessToken, const char* local_type, const char* local_id, const int channelNum, EasyRTC_Data_Callback callback, void* userptr);

    int	EASYRTC_DEVICE_API	EasyRTC_Device_GetChannelNum(EASYRTC_HANDLE handle);

    int	EASYRTC_DEVICE_API	EasyRTC_Device_SetChannelInfo(EASYRTC_HANDLE handle, const int channelId, const char *channelIDStr, EASYRTC_CODEC videoCodecID, EASYRTC_CODEC audioCodecID);

    // 发送视频帧 /*时间戳单位是:毫秒*/
    int	EASYRTC_DEVICE_API	EasyRTC_Device_SendVideoFrame(EASYRTC_HANDLE handle, const int channelId, char* framedata, const int framesize, int keyframe, unsigned long long pts/*时间戳单位是:毫秒*/);

    // 发送音频帧
    int	EASYRTC_DEVICE_API	EasyRTC_Device_SendAudioFrame(EASYRTC_HANDLE handle, const int channelId, char* framedata, const int framesize, unsigned long long pts/*时间戳单位是:毫秒*/);


    int	EASYRTC_DEVICE_API	EasyRTC_Device_aecm_process(EASYRTC_HANDLE handle, const int channelId, short* nearendNoisy, short* outPcmData, int pcmSize);

    // 发送自定义数据到指定对端或所有对端(如果peerUUID为NULL或为""则发送到所有对端)
    int	EASYRTC_DEVICE_API	EasyRTC_Device_SendCustomData(EASYRTC_HANDLE handle, const int channelId, const char* peerUUID, const int isBinary, const char* data, const int size);

    // 挂断
    int	EASYRTC_DEVICE_API	EasyRTC_Device_Hangup(EASYRTC_HANDLE handle, const int channelId, const char* peerUUID);

    // 释放句柄
    int	EASYRTC_DEVICE_API	EasyRTC_Device_Release(EASYRTC_HANDLE* handle);

    // 反初始化环境
    int	EASYRTC_DEVICE_API	EasyRTC_Device_Deinit();

#ifdef __cplusplus
}
#endif


#endif
