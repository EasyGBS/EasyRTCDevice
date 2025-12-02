package com.easydarwin.webrtc.utils

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Camera
import android.util.Size
import androidx.preference.PreferenceManager
import java.util.UUID
import androidx.core.content.edit

class SPUtil private constructor(context: Context) {
    private val preferences: SharedPreferences

    init {
        val appContext = context.applicationContext
        preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    }

    companion object {
        @Volatile
        private var instance: SPUtil? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(SPUtil::class.java) {
                    if (instance == null) {
                        instance = SPUtil(context)
                    }
                }
            }
        }

        fun getInstance(): SPUtil {
            return instance ?: throw IllegalStateException("SPUtil must be initialized first")
        }
    }

    //软编码
    private val KEY_SW_CODEC = "key-sw-codec"

    //true:H265   false:H264
    private val KEY_HEVC_CODEC = "key-hevc-codec"

    //音频编码  0:G711A    1:G711U    2:AAC
    private val KEY_AAC_CODEC = "key-aac-codec"

    //叠加水印
    private val KEY_ENABLE_VIDEO_OVERLAY = "key_enable_video_overlay"

    //视频码率
    private val KEY_BITRATE_ADDED_KBPS = "bitrate_added_kbps"

    //视频
    private val KEY_ENABLE_VIDEO = "key-enable-video"

    //音频
    private val KEY_ENABLE_AUDIO = "key-enable-audio"
    private val VER = "ver"
    val SERVERIP = "serverip"
    val SERVERPORT = "serverport"
    val LOCALSIPPORT = "localsipport"
    val SERVERID = "serverid"
    val SERVERDOMAIN = "serverdomain"
    val DEVICEID = "deviceid"
    val RTC_USER_UUID = "uuid"
    val PASSWORD = "password"
    val PROTOCOL = "protocol"
    val REGEXPIRES = "regexpires"
    val HEARTBEATINTERVAL = "heartbeatinterval"
    val HEARTBEATCOUNT = "heartbeatcount"

    //val INDEXCODE = "indexcode"
    val DEVICENAME = "devicename"
    var ISENVIDEO = "isenvideo"
    var ISENAUDIO = "isenaudio"
    var ISENLOCREPORT = "isenlocreport"
    var CAMERAID = "cameraid"

    //0:1920*1080  1:1280*720  2:640*480
    var VIDEORESOLUTION = "videoResolution"
    var FRAMERATE = "frameRate"
    var SAMPLINGRATE = "samplingRate"

    //0:单声道  1:立体声道
    var AUDIOCHANNEL = "audiochannel"
    var AUDIOCODERATE = "audiocoderate"
    var LOCATIONFREQ = "locationfreq"
    var LONGITUDE = "longitude"
    var LATITUDE = "latitude"


    var rtcUserUUID: String
        get() {
            val savedId = preferences.getString(RTC_USER_UUID, null)
            return if (savedId.isNullOrEmpty() || !isValidCustomId(savedId)) {
                val newId = generateCustom20DigitId()
                preferences.edit { putString(RTC_USER_UUID, newId) }
                newId
            } else {
                savedId
            }
        }
        set(value) {
            require(isValidCustomId(value)) { "ID 必须是20位数字，且第11-13位为800" }
            preferences.edit { putString(RTC_USER_UUID, value) }
        }

    // 辅助函数：验证是否符合格式
    private fun isValidCustomId(id: String): Boolean {
        return id.length == 20 &&
                id.all { it.isDigit() } &&
                id.substring(10, 13) == "800"
    }

    // 辅助函数：生成符合要求的20位数字ID
    private fun generateCustom20DigitId(): String {
        "34010000008000000001"
        // 前10位：随机数字（避免以0开头可选）
//        val firstPart = (1000000000L..9999999999L).random().toString()
        val firstPart = "3401000000"
        // 第11-13位固定为800
        val middlePart = "800"
        // 后7位：随机数字（允许前导0）
        val lastPart = (0L..9999999L).random().toString().padStart(7, '0')
        return "$firstPart$middlePart$lastPart"
    }

    var hevcCodec: Int
        get() = preferences.getInt(KEY_HEVC_CODEC, 0)
        set(hevc) = preferences.edit { putInt(KEY_HEVC_CODEC, hevc) }

    var videoResolution: Int
        get() = preferences.getInt(VIDEORESOLUTION, 1)
        set(value) = preferences.edit { putInt(VIDEORESOLUTION, value) }

    var frameRate: Int
        get() = preferences.getInt(FRAMERATE, 0)
        set(value) = preferences.edit { putInt(FRAMERATE, value) }

    var bitRateKbps: Int
        get() = preferences.getInt(KEY_BITRATE_ADDED_KBPS, 3)
        set(value) = preferences.edit { putInt(KEY_BITRATE_ADDED_KBPS, value) }

    var cameraId: Int
        get() = preferences.getInt(CAMERAID, Camera.CameraInfo.CAMERA_FACING_BACK)
        set(value) = preferences.edit { putInt(CAMERAID, value) }

    fun getIsHevc(): Boolean {
        return getInstance().hevcCodec == 1
    }

    fun getVideoResolution(): Size {
        return when (getInstance().videoResolution) {
            0 -> Size(1920, 1080)
            1 -> Size(1280, 720)
            3 -> Size(960, 540)
            else -> Size(1280, 720)
        }
    }

    fun getVideoBitRateKbps(): Int {
        return when (getInstance().bitRateKbps) {
            0 -> 4096
            1 -> 2024
            2 -> 1024
            3 -> 512
            else -> 1024 // 默认值
        } * 1024
    }

    fun getVideoFrameRate(): Int {
        return when (getInstance().frameRate) {
            0 -> 30
            1 -> 25
            2 -> 20
            3 -> 15
            else -> 25 // 默认值
        }
    }

}