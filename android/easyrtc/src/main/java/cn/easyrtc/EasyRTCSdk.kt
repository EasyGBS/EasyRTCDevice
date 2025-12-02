package cn.easyrtc

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// 添加呼叫信息数据类
data class EasyRTCUser(val uuid: String, val username: String)

object EasyRTCSdk {
    private const val TAG = "EasyRTCSdk"

    // 添加接听状态常量
    @JvmStatic
    val ACCEPTED: Int = 1  // 接听

    @JvmStatic
    val REJECTED: Int = 0  // 拒绝

    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_DNS_FAIL: Int = 0x01 // 连接中
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_CONNECTING: Int = 0x02 // 连接中
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_CONNECTED: Int = 0x03 // 连接成功
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_CONNECT_FALL: Int = 0x04 // 连接失败
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_DISCONNECT: Int = 0x05 // 连接断开
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_PASSIVE_CALL: Int = 0x06 // 被动呼叫

    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_START_VIDEO: Int = 0x10 // 对端请求视频数据
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_START_AUDIO: Int = 0x11 // 对端请求音频数据
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_STOP_VIDEO: Int = 0x12 // 对端关闭视频数据
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_STOP_AUDIO: Int = 0x13 // 对端关闭音频数据

    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_PEER_VIDEO: Int = 0x20 // 回调对端的视频数据
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_PEER_AUDIO: Int = 0x21 // 回调对端的音频数据
    @JvmStatic
    val EasyRTC_CALLBACK_TYPE_PEER_CUSTOM_DATA: Int = 0x22 // 回调对端的DataChannel数据
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_LOCAL_AUDIO: Int = 0x23 // 回应本地回音消除后的音频数据
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_PEER_CONNECTED: Int = 0x24  // 连接对端成功
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_PEER_CONNECT_FAIL: Int = 0x25 // 连接对端失败
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_PEER_DISCONNECT: Int = 0x26 // 与对端连接断开
    @JvmStatic
    val EASYRTC_CALLBACK_TYPE_PEER_CLOSED: Int = 0x27 // 与对端连接已关闭


    private val nativeDevice = device()

    // 线程处理
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mEasyRTCUser: EasyRTCUser? = null

    const val EASYRTC_CODEC_H264 = 1
    const val EASYRTC_CODEC_OPUS = 2
    const val EASYRTC_CODEC_VP8 = 3
    const val EASYRTC_CODEC_MULAW = 4
    const val EASYRTC_CODEC_ALAW = 5
    const val EASYRTC_CODEC_H265 = 6
    const val EASYRTC_CODEC_AAC = 7

    const val ChannelID: Int = 0

    // 事件监听器
    interface EasyRTCEventListener {
        fun onCelling(user: EasyRTCUser)
        fun onConnected()
        fun onConnectFailed()
        fun onDisconnected()
        fun onPeerClosed()
        fun onVideoDataReceived(data: ByteArray, useHevc: Boolean, isKey: Boolean)
        fun onAudioDataReceived(data: ByteArray)
        fun onOnlineUserListReceived(data: String)
        fun onDeviceLog(msg: String)
    }

    private var eventListener: EasyRTCEventListener? = null

    fun setEventListener(listener: EasyRTCEventListener?) {
        this.eventListener = listener
    }

    // 提供封装的方法
    fun init(uuid: String, username: String, isHEVC: Boolean = false): Int {
        // 设备ID 34010000008000000001
        Log.d("deviceID == ", uuid)
        nativeDevice.create(1, "36.34.0.68", 30401, "",
            uuid, 1, 0)
        return nativeDevice.SetChannelInfo(ChannelID, "34010000008000000001",
            if (isHEVC) EASYRTC_CODEC_H265 else EASYRTC_CODEC_H264, EASYRTC_CODEC_ALAW)
    }

    fun release(): Int = nativeDevice.release()


    fun openPeerConnection(user: EasyRTCUser): Int {
        mEasyRTCUser = user
        return 0
    }


    fun sendVideoFrame(data: ByteArray, size: Int, keyframe: Int, pts: Int): Int {
        if (mEasyRTCUser == null && mEasyRTCUser?.uuid == null) return 0
        return nativeDevice.SendVideoFrame(ChannelID, data, size, keyframe, pts)
    }

    fun sendAudioFrame(data: ByteArray, size: Int, keyframe: Int, pts: Int): Int {
        if (mEasyRTCUser == null && mEasyRTCUser?.uuid == null) return 0
        return nativeDevice.SendAudioFrame(ChannelID, data, size, keyframe, pts)
    }

    fun sendMetadata(msg: String, size: Int): Int = nativeDevice.SendMetadata(ChannelID, msg, size)

    fun handleCall(user: EasyRTCUser, isAccepted: Int): Int {
        if (isAccepted != REJECTED) {
            mEasyRTCUser = user
            // 接受
            nativeDevice.PassiveCallResponse(user.uuid, 0)
        } else {
            // 拒绝则主动挂断
            Log.d("userID == ", user.uuid)

            nativeDevice.PassiveCallResponse( user.uuid, 1)
            mEasyRTCUser = null
        }
        return 0
    }

    fun hangUp() {
        if (mEasyRTCUser == null && mEasyRTCUser?.uuid == null) return
        Log.d(TAG, "Hangup... channelID=$ChannelID peer=$mEasyRTCUser")
        val ret: Int = nativeDevice.Hangup( mEasyRTCUser?.uuid)
        Log.d(TAG, "Hangup return = $ret")
        mEasyRTCUser = null
    }


    // 让 device 回调调用的桥接函数
    @JvmStatic
    fun handleDeviceCallback(arg1: Int, data: String?, length: Int): Int {

        executor.execute {
            data?.let {
                mainHandler.post {
                    eventListener?.onDeviceLog(it)
                }
            }
        }
        return 0
    }

    @JvmStatic
    fun handleDataCallback(arg1: Int, peerUUID: String, dataType: Int, codecID: Int, isBinary: Int,
                           data: ByteArray, len: Int, keyframe: Int, pts: Int): Int {
        executor.execute {
            when (dataType) {
                EasyRTC_CALLBACK_TYPE_CONNECTED -> {
                    mainHandler.post {
                        eventListener?.onConnected()
                    }
                    Log.d(TAG, "callback 已连接")
                }

                EASYRTC_CALLBACK_TYPE_PASSIVE_CALL -> {
                    Log.d(TAG, "callback 被动呼叫 peerUUID= $peerUUID")
                    mainHandler.post {
                        eventListener?.onCelling(
                            EasyRTCUser(
                                uuid = peerUUID,
                                username = ""
                            )
                        )
                    }
                }

                EasyRTC_CALLBACK_TYPE_CONNECT_FALL -> {
                    Log.d(TAG, "callback EasyRTC_CALLBACK_TYPE_CONNECT_FALL")
                    mainHandler.post {
                        eventListener?.onConnectFailed()
                    }
                }

                EasyRTC_CALLBACK_TYPE_PEER_VIDEO -> {
//                    Log.d(TAG, "recv video codeID =$codecID keyframe =$keyframe")
                    eventListener?.onVideoDataReceived(data,
                        codecID == EASYRTC_CODEC_H265, keyframe == 1)
                }

                EasyRTC_CALLBACK_TYPE_PEER_AUDIO -> {
//                    Log.d(TAG, "recv audio codeID =$codecID")
                    eventListener?.onAudioDataReceived(data)
                }

                EASYRTC_CALLBACK_TYPE_LOCAL_AUDIO -> {
//                    Log.d(TAG, " EASYRTC_CALLBACK_TYPE_LOCAL_AUDIO")
                }

                EasyRTC_CALLBACK_TYPE_DISCONNECT -> {
                    Log.d(TAG, "callback EasyRTC_CALLBACK_TYPE_DISCONNECT")
                    mainHandler.post {
                        eventListener?.onDisconnected()
                    }
                }

                EASYRTC_CALLBACK_TYPE_PEER_CONNECTED -> {
                    Log.d(TAG, "callback 连接对端成功, peerUUID= $peerUUID")
                }

                EASYRTC_CALLBACK_TYPE_PEER_CONNECT_FAIL -> {
                    Log.d(TAG, "callback 连接对端失败")
                }

                EASYRTC_CALLBACK_TYPE_PEER_DISCONNECT -> {
                    Log.d(TAG, "callback 与对端连接断开")
                }

                EASYRTC_CALLBACK_TYPE_PEER_CLOSED -> {
                    Log.d(TAG, "callback 与对端连接已关闭")
                    mEasyRTCUser = null
                    eventListener?.onPeerClosed()
                }

                EasyRTC_CALLBACK_TYPE_START_AUDIO -> {
                    Log.d(TAG, "callback 对端发起音频请求")
                }

                EasyRTC_CALLBACK_TYPE_START_VIDEO -> {
                    Log.d(TAG, "callback 对端发起视频请求")
                }

                EasyRTC_CALLBACK_TYPE_STOP_AUDIO -> {
                    Log.d(TAG, "callback 对端关闭音频")
                }

                EasyRTC_CALLBACK_TYPE_STOP_VIDEO -> {
                    Log.d(TAG, "callback 对端关闭视频")
                    mEasyRTCUser = null
                }
            }
        }
        return 0
    }

    @JvmStatic
    fun handleOnlineUserCallback(data: String, length: Int): Int {
        executor.execute {
            mainHandler.post {
                eventListener?.onOnlineUserListReceived(data)
            }
        }
        return 0
    }


}