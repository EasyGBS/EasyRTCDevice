# EasyRTCDevice

EasyRTCDevice是基于EasyRTC底层技术实现的一套完整设备与EasyGBS平台进行RTC实时通信的协议，基于EasyRTCDevice可以构建一套完美的视频交互类产品，例如智能摄像头、可视猫眼门铃、可穿戴硬件、无人机、无人车、机器人等各种场景。在EasyGBS官网（[https://www.easygbs.com](https://www.easygbs.com)）可以直接看到接入效果，相比于其他类型的视频设备接入协议，RTC技术有着与生俱来的先天优势，尤其是在机器人、机器车、无人机等实时性要求超高的场景，EasyRTC技术具有看得见体验得到的技术优势。


## EasyRTCDevice应用于

EasyRTCDevice实时通信技术，是一种基于RTC技术实现的P2P+SFU视频通信平台，平台支持实时音视频通信、数据传输等功能，适用于多种物联网应用场景。

### 设备端
- 智能穿戴：AR眼镜、VR头显
- 智能家居：智能监控、智能音箱、智慧屏、智能门锁、健身镜、家庭机器人；
- 远程操控：无人机、无人车、行车记录仪；


### 应用场景
- 实时监控
- 视频通话
- 紧急呼叫
- 远程控制
- 远程巡检
- 远程协作

## 目录说明

	./
	./android	//安卓各平台EasyRTC库文件
	./src		//调用示例代码，C&C++
	./embedded	//各种嵌入式平台EasyRTC设备端版本
	./include	//EasyRTCDevice.h头文件
	./linux		//x86架构Linux平台EasyRTC库文件
	./windows	//x86架构Windows平台EasyRTC库文件



## 调用流程

![EasyRTCDevice-Flow](https://www.easydarwin.com/images/EasyRTCDevice/EasyRTCDevice-Flow.png)


    // 调用顺序:

    int __EasyRTC_Data_Callback(void* userptr, const char* peerUUID, EASYRTC_DATA_TYPE_ENUM_E dataType, int codecID, int isBinary, char* data, int size, int keyframe, unsigned long long pts)
    {
        if (EASYRTC_CALLBACK_TYPE_START_VIDEO == dataType)
        {
            // 请求视频
            // 置发送标志位为1, 在视频发送线程中调用EasyRTC_Device_SendVideoFrame发送视频帧
        }
        else if (EASYRTC_CALLBACK_TYPE_START_AUDIO == dataType)
        {
            // 请求音频
            // 置发送标志位为1, 在音频发送线程中调用EasyRTC_Device_SendAudioFrame发送音频帧
        }
        else if (EASYRTC_CALLBACK_TYPE_STOP_VIDEO == dataType)
        {
            // 对方已关闭， 置发送标志为0, 即视频发送线程中不调用EasyRTC_Device_SendVideoFrame
        }
        else if (EASYRTC_CALLBACK_TYPE_STOP_AUDIO == dataType)
        {
            // 对方已关闭， 置发送标志为0, 即音频发送线程中不调用EasyRTC_Device_SendAudioFrame
        }
        else if (EASYRTC_CALLBACK_TYPE_PEER_VIDEO == dataType)
        {
            // codecID: 对方的视频编码ID
            // data: 对方的视频帧数据
            // size: 对方的视频帧大小
        }
        else if (EASYRTC_CALLBACK_TYPE_PEER_AUDIO == dataType)
        {
            // codecID: 此处格式为解码后的PCM
            // data: 对方的音频帧数据
            // size: 对方的音频帧大小
        }
        else
        {
            // ... 请按需处理
        }

        return 0;
    }

    // 初始化环境
    EasyRTC_Device_Init();

    EASYRTC_HANDLE  rtcHandle = NULL;       // 设备句柄

    // 创建设备
    EasyRTC_Device_Create(&rtcHandle, serverIP, serverPort, accessToken, "device", deviceID, channelNum, __EasyRTC_Data_Callback, NULL);
    for (int i = 0; i < channelNum; i++)
    {
        // 设置通道信息
        char code[64] = { 0 };
        sprintf(code, "340200000013200000%02d", i+1);
        EasyRTC_Device_SetChannelInfo(rtcHandle, i, code, EASYRTC_CODEC_H264, EASYRTC_CODEC_ALAW);
    }

    // 在发送线程中调用
    // 根据发送标志位，决定是否发送音视频帧
    // EasyRTC_Device_SendVideoFrame
    // EasyRTC_Device_SendAudioFrame


    // 删除设备
    EasyRTC_Device_Release(&rtcHandle);

    // 反初始化
    EasyRTC_Device_Deinit();

## EasyRTCDevice更多技术前景探讨

EasyGBS官网：[www.easygbs.com](https://www.easygbs.com)

