package cn.easyrtc.helper

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class RemoteRTCHelper(
    private val surface: Surface,
    private val videoWidth: Int = 1920,
    private val videoHeight: Int = 1080,
    private val frameRate: Int = 30,
    private val queueCapacity: Int = 30,
    private val iFrameInterval: Int = 1
) {
    // 视频解码器相关
    private var videoDecoder: MediaCodec? = null
    private var videoDecoderThread: HandlerThread? = null
    private var videoDecoderHandler: Handler? = null

    // 视频帧队列管理
    private val videoFrameQueue = LinkedBlockingQueue<ByteArray>(queueCapacity)

    // 使用原子操作保证线程安全
    private val isDecoderRunning = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    private val decoderLock = Any()

    // 当前编码类型
    private var currentCodecType: String = MediaFormat.MIMETYPE_VIDEO_AVC // 默认264

    // 性能统计
    private val framesProcessed = AtomicLong(0)
    private val framesDropped = AtomicLong(0)
    private val decoderErrorCount = AtomicLong(0)

    private var lastKeyFrame: ByteArray? = null

    // 常量
    companion object {
        private const val TAG = "RemoteRTCHelper"
        private const val MAX_DECODER_ERROR_COUNT = 5
        private const val DEQUEUE_TIMEOUT_US: Long = 10000

        // 编码类型常量
        const val CODEC_H264 = MediaFormat.MIMETYPE_VIDEO_AVC
        const val CODEC_H265 = MediaFormat.MIMETYPE_VIDEO_HEVC
    }

    init {
        // 延迟初始化，确保surface已准备好
        Handler(Looper.getMainLooper()).postDelayed({
            initVideoDecoder()
        }, 200)
    }

    // 初始化视频解码器
    fun initVideoDecoder(codecType: String? = null) {
        if (isDestroyed.get()) {
            Log.w(TAG, "Attempt to init decoder after destruction")
            return
        }

        synchronized(decoderLock) {
            var changeCodec: Boolean
            changeCodec = false

            // 如果指定了新的编码类型，则更新
            codecType?.let {
                if (it != currentCodecType) {
                    Log.d(TAG, "Switching codec from $currentCodecType to $it")
                    currentCodecType = it
                    // 先释放旧的解码器
                    releaseVideoDecoderOnly()
                    changeCodec = true
                }
            }

            // 如果解码器已经在运行，则不需要重新初始化
            if (isDecoderRunning.get() && videoDecoder != null) {
                Log.d(TAG, "Decoder already running, skip reinitialization")
                return
            }

            try {
                // 创建并配置解码器
                videoDecoder = MediaCodec.createDecoderByType(currentCodecType)
                val format = MediaFormat.createVideoFormat(currentCodecType, videoWidth, videoHeight)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

                videoDecoder?.configure(format, surface, null, 0)
                videoDecoder?.start()

                if (!changeCodec) {
                    // 新增：立即送入关键帧（如果有）
                    /*lastKeyFrame?.let { keyFrame ->
                        val inputBufferId = videoDecoder?.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inputBufferId != null && inputBufferId >= 0) {
                            videoDecoder?.getInputBuffer(inputBufferId)?.apply {
                                clear()
                                put(keyFrame)
                            }
                            videoDecoder?.queueInputBuffer(
                                inputBufferId, 0, keyFrame.size, System.nanoTime() / 1000, 0
                            )
                            Log.d(TAG, "Injected cached key frame on decoder init")
                        }
                    }*/
                }

                // 启动解码线程
                videoDecoderThread = HandlerThread("VideoDecoder").apply { start() }
                videoDecoderHandler = Handler(videoDecoderThread!!.looper)

                isDecoderRunning.set(true)
                decoderErrorCount.set(0)
                videoDecoderHandler?.post(videoFrameConsumer)
                Log.d(TAG, "Video decoder initialized successfully for $currentCodecType")
            } catch (e: Exception) {
                Log.e(TAG, "Video decoder init error: ${e.message}")
                e.printStackTrace()
                // 初始化失败时清理资源
                releaseVideoDecoderOnly()
            }
        }
    }

    // 重新初始化解码器（用于切换编码格式）
    fun reinitVideoDecoder(codecType: String) {
        if (isDestroyed.get()) return

        synchronized(decoderLock) {
            if (codecType != currentCodecType) {
                Log.d(TAG, "Reinitializing decoder from $currentCodecType to $codecType")

                // 停止当前解码器
                isDecoderRunning.set(false)
                videoFrameQueue.clear()

                // 释放解码器资源
                releaseVideoDecoderOnly()

                // 更新编码类型
                currentCodecType = codecType

                // 重新初始化
                initVideoDecoder()
            }
        }
    }

    // 仅释放解码器资源，不停止整个组件
    private fun releaseVideoDecoderOnly() {
        synchronized(decoderLock) {
            isDecoderRunning.set(false)

            videoDecoderHandler?.removeCallbacksAndMessages(null)
            videoDecoderThread?.quitSafely()

            try {
                videoDecoder?.stop()
                videoDecoder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Video decoder release error: ${e.message}")
            }

            videoDecoder = null
            videoDecoderThread = null
            videoDecoderHandler = null

            Log.d(TAG, "Video decoder released")
        }
    }

    // 消费者线程任务
    private val videoFrameConsumer = Runnable {
        val bufferInfo = MediaCodec.BufferInfo() // 复用BufferInfo对象

        while (isDecoderRunning.get() && !isDestroyed.get()) {
            try {
                val frameData = videoFrameQueue.take()
                processVideoFrame(frameData, bufferInfo)
            } catch (e: InterruptedException) {
                Log.d(TAG, "Video frame consumer interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Video frame consumer error: ${e.message}")
                // 防止异常导致循环崩溃
                Thread.sleep(10)
            }
        }
        Log.d(TAG, "Video frame consumer exited")
    }

    // 视频帧处理逻辑封装
    private fun processVideoFrame(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        val decoder = videoDecoder ?: return

        try {
            // 处理输入缓冲区
            val inputBufferId = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputBufferId >= 0) {
                decoder.getInputBuffer(inputBufferId)?.apply {
                    clear()
                    put(data)
                }
                decoder.queueInputBuffer(
                    inputBufferId, 0, data.size, System.nanoTime() / 1000, 0
                )
                framesProcessed.incrementAndGet()
            } else {
                Log.w(TAG, "No available input buffer for video frame")
                return
            }

            // 处理输出缓冲区
            while (isDecoderRunning.get() && !isDestroyed.get()) {
                val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outputBufferId >= 0 -> {
                        // 释放输出缓冲区并渲染
                        decoder.releaseOutputBuffer(outputBufferId, true)
                        break // 处理一帧后退出，避免阻塞
                    }
                    outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Decoder output format changed")
                    }
                    outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        Log.d(TAG, "Decoder output buffers changed")
                    }
                }
            }

            decoderErrorCount.set(0) // 成功处理帧，重置错误计数

        } catch (e: Exception) {
            Log.e(TAG, "Video frame processing error: ${e.message}")
            val errorCount = decoderErrorCount.incrementAndGet()

            // 如果连续出错，尝试重新初始化解码器
            if (errorCount >= MAX_DECODER_ERROR_COUNT) {
                Log.w(TAG, "Too many decoder errors ($errorCount), attempting to reinitialize...")
                Handler(Looper.getMainLooper()).post {
                    if (!isDestroyed.get()) {
                        reinitVideoDecoder(currentCodecType)
                    }
                }
            }
        }
    }

    private fun isKeyFrame(data: ByteArray, codecType: String): Boolean {
        if (data.size < 5) return false

        return when (codecType) {
            CODEC_H264 -> {
                // 查找 NALU 类型（H.264）
                for (i in 0 until data.size - 4) {
                    if (data[i] == 0x00.toByte() &&
                        data[i + 1] == 0x00.toByte() &&
                        data[i + 2] == 0x00.toByte() &&
                        data[i + 3] == 0x01.toByte()) {
                        // 找到 start code，下一个是 NALU header
                        val naluType = data[i + 4].toInt() and 0x1F
                        if (naluType == 5) { // IDR slice
                            return true
                        }
                    }
                }
                false
            }
            CODEC_H265 -> {
                // H.265: NALU type 是 (byte & 0x7E) shr 1
                for (i in 0 until data.size - 4) {
                    if (data[i] == 0x00.toByte() &&
                        data[i + 1] == 0x00.toByte() &&
                        data[i + 2] == 0x00.toByte() &&
                        data[i + 3] == 0x01.toByte()) {
                        val naluType = (data[i + 4].toInt() and 0x7E) ushr 1
                        if (naluType == 19 || naluType == 20) { // IDR
                            return true
                        }
                    }
                }
                false
            }
            else -> false
        }
    }

    // 处理远程视频帧
    fun onRemoteVideoFrame(data: ByteArray, isKey: Boolean) {
        if (!isDecoderRunning.get() || isDestroyed.get()) return

        /*if (isKey) {
            Log.w(TAG, "Get Key Frame")
            lastKeyFrame = data.clone() // 保存副本，避免被修改
        }*/
        /*if (isKeyFrame(data, currentCodecType)) {
            Log.w(TAG, "Get Key Frame222222222")
            lastKeyFrame = data.clone() // 保存副本，避免被修改
        }*/

        // 非阻塞式添加，队列满时丢弃旧帧
        if (!videoFrameQueue.offer(data)) {
            // 队列已满，丢弃最旧帧并记录统计
            if (videoFrameQueue.isNotEmpty()) {
                videoFrameQueue.poll() // 移除队首旧帧
                framesDropped.incrementAndGet()
            }
            // 再次尝试添加新帧
            if (!videoFrameQueue.offer(data)) {
                Log.w(TAG, "Failed to add video frame to queue, frame dropped")
                framesDropped.incrementAndGet()
            } else {
                Log.w(TAG, "Video frame queue full, replaced oldest frame. Queue size: ${videoFrameQueue.size}")
            }
        }

        // 调试日志（可选择性开启）
//        Log.v(TAG, "Video frame queued. Size: ${data.size}, Queue: ${videoFrameQueue.size}")
    }

    // 释放视频解码器资源
    fun releaseVideoHandler() {
        if (isDestroyed.get()) return

        synchronized(decoderLock) {
            isDecoderRunning.set(false)
            isDestroyed.set(true)

            // 清空队列
            videoFrameQueue.clear()

            videoDecoderHandler?.removeCallbacksAndMessages(null)
            videoDecoderThread?.quitSafely()

            try {
                videoDecoder?.stop()
                videoDecoder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Video decoder release error: ${e.message}")
            }

            videoDecoder = null
            videoDecoderThread = null
            videoDecoderHandler = null

            Log.d(TAG, "RemoteRTCHelper released")
        }
    }

    fun release() {
        releaseVideoHandler()
    }

    // 状态查询方法
    fun isRunning(): Boolean = isDecoderRunning.get() && !isDestroyed.get()

    fun isDestroyed(): Boolean = isDestroyed.get()

    // 获取当前使用的编码类型
    fun getCurrentCodecType(): String = currentCodecType

    // 获取性能统计
    fun getStats(): String {
        return "Processed: ${framesProcessed.get()}, " +
                "Dropped: ${framesDropped.get()}, " +
                "Queue: ${videoFrameQueue.size}, " +
                "Errors: ${decoderErrorCount.get()}"
    }

    // 重置统计信息
    fun resetStats() {
        framesProcessed.set(0)
        framesDropped.set(0)
        decoderErrorCount.set(0)
    }

    // 获取队列大小
    fun getQueueSize(): Int = videoFrameQueue.size
}