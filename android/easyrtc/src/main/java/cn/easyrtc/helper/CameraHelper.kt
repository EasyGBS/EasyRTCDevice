package cn.easyrtc.helper

import VideoEncodeConfig
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.widget.Toast
import cn.easyrtc.EasyRTCSdk
import org.easydarwin.sw.JNIUtil
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class CameraHelper(private val context: Context, private val videoEncodeConfig: VideoEncodeConfig) {
    private var camera: Camera? = null
    private val mVideoEncodeConfig = videoEncodeConfig

    private var displayOrientation: Int = mVideoEncodeConfig.getOrientation()
    private var previewWidth: Int = mVideoEncodeConfig.getWidth()
    private var previewHeight: Int = mVideoEncodeConfig.getHeight()

    private var encodedWidth: Int = mVideoEncodeConfig.getWidth()
    private var encodedHeight: Int = mVideoEncodeConfig.getHeight()
    private var encodedRotation: Int = mVideoEncodeConfig.getOrientation()

    private var mCameraID: Int = mVideoEncodeConfig.getCameraId()

    // 预览回调相关变量
    private val callbackBuffers = mutableListOf<ByteArray>()
    private val BUFFER_COUNT = 3

    // 编码相关变量
    private var mediaCodec: MediaCodec? = null
    private val isEncoding = AtomicBoolean(false)

    private val useHevc = mVideoEncodeConfig.getUseHevc()
    private val BIT_RATE = mVideoEncodeConfig.getBitRate() // 1 Mbps
    private val MIME_TYPE = mVideoEncodeConfig.getMimeType()
    private val FRAME_RATE = mVideoEncodeConfig.getFrameRate()  // 帧率
    private val IFRAME_INTERVAL =mVideoEncodeConfig.getIFrameInterval()

    var info: VideoConfig.CodecInfo = VideoConfig.CodecInfo()

    // 编码器处理线程
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null

    // CodecConfig 数据（VPS/SPS/PPS）
    private var codecConfigBuffer: ByteArray? = null
    private var codecConfigBufferSize = 0

    // 关键帧缓冲区复用
    private var keyFrameBuffer: ByteArray? = null
    private var keyFrameBufferSize = 0

    // 输出帧复用缓冲（用于非关键帧）
    private var outputFrameBuffer: ByteArray? = null
    private var outputFrameBufferSize = 0

    // 相机状态监听器
    interface CameraListener {
        fun onCameraOpened(camera: Camera)
        fun onCameraError(error: String)
    }

    private var i420_buffer: ByteArray? = null

    // 队列用于编码线程消费，容量可调
    private val frameQueue: LinkedBlockingQueue<ByteArray> = LinkedBlockingQueue(20)

    // 用于存放供编码使用的可复用缓冲池（避免频繁分配）
    private lateinit var encodeBufferPool: ArrayBlockingQueue<ByteArray>

    // 打开相机
    fun openCamera(surfaceTexture: SurfaceTexture?, listener: CameraListener) {

        if (surfaceTexture == null) {
            listener.onCameraError("SurfaceTexture is null")
            return
        }

        try {
            // 释放可能存在的旧相机实例
            releaseCamera()

            getEncodedDimensions() //获取设备 宽高以及旋转角度
            // 打开指定相机
            camera = Camera.open(mCameraID).apply {
                // 设置相机参数
                val params = parameters.apply {
                    // 查找并设置最佳预览尺寸
                    supportedPreviewSizes.find {
                        it.width == previewWidth && it.height == previewHeight
                    }?.let {
                        setPreviewSize(it.width, it.height)
                        Log.d(TAG, "Set preview size: ${it.width}x${it.height}")
                    } ?: run {
                        // 如果没有找到精确匹配，使用第一个可用尺寸
                        supportedPreviewSizes.firstOrNull()?.let {
                            setPreviewSize(it.width, it.height)
                            previewWidth = it.width
                            previewHeight = it.height
                            Log.w(TAG, "Using fallback preview size: ${it.width}x${it.height}")
                        }
                    }

                    // 自动对焦设置（保持原样）
                    val focusModes = supportedFocusModes
                    when {
                        focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) -> {
                            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                            Log.d(TAG, "启用连续视频自动对焦")
                        }

                        focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) -> {
                            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                            Log.d(TAG, "启用连续图片自动对焦")
                        }

                        focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> {
                            focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                            Log.d(TAG, "启用自动对焦")
                        }

                        else -> {
                            Log.w(TAG, "自动对焦不可用，使用默认对焦模式: $focusModes")
                        }
                    }

                    // 设置预览格式（默认为NV21）
                    previewFormat = ImageFormat.NV21
                }

                // 应用参数设置
                parameters = params

                // 设置显示方向
                setDisplayOrientation(displayOrientation)

                // 设置预览SurfaceTexture
                setPreviewTexture(surfaceTexture)

                // 开始预览
                startPreview()
                Log.d(TAG, "Camera preview started")
                // 通知监听器相机已打开
                listener.onCameraOpened(this)
            }
            // 初始化预览回调缓冲区
            setupPreviewCallback()
            // 初始化编码器（如果不存在）
            if (mediaCodec == null) {
                initMediaCodec()
            } else {
                // 如果编码器已存在，恢复编码
                resumeEncoding()
            }

        } catch (e: Exception) {
            val errorMsg = "Camera setup error: ${e.message}"
            Log.e(TAG, errorMsg)
            listener.onCameraError(errorMsg)
        }
    }

    // 初始化MediaCodec
    private fun initMediaCodec() {
        // 确保在编码器线程中执行所有操作
        encoderThread = HandlerThread("MediaCodec_Async").apply {
            start()
            encoderHandler = Handler(looper)
        }

        encoderHandler?.post {
            try {
                // 清理可能存在的旧实例
                cleanupMediaCodec()

                mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                    val format = MediaFormat.createVideoFormat(MIME_TYPE, encodedWidth, encodedHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, info.mColorFormat)
                        setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                        setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

                        // 添加一些兼容性设置
                        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                    }

                    Log.d(TAG, "Configuring MediaCodec with format: $format")

                    // 先设置回调，再配置编码器
                    setCallback(object : MediaCodec.Callback() {
                        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
//                            Log.v(TAG, "onInputBufferAvailable: $index")
                            processInputBuffer(index)
                        }

                        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
//                            Log.v(TAG, "onOutputBufferAvailable: $index, size: ${info.size}")
                            processOutputBuffer(index, info)
                        }

                        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                            Log.d(TAG, "Output format changed: $format")
                        }

                        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                            Log.e(TAG, "MediaCodec error: ${e.message}", e)
                            // 尝试恢复编码器
                            recoverMediaCodec()
                        }
                    }, encoderHandler)

                    // 配置并启动编码器
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()

                    // 确保编码状态设置
                    isEncoding.set(true)
                    Log.d(TAG, "MediaCodec started successfully")

                    // 启动后请求关键帧
                    requestKeyFrameInternal()

                    // 检查编码器状态
                    checkMediaCodecState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaCodec initialization failed: ${e.message}", e)
                // 初始化失败时清理资源
                cleanupMediaCodec()
                // 可以尝试重新初始化
                tryRecoverMediaCodec()
            }
        }
    }

    // 检查MediaCodec状态
    private fun checkMediaCodecState() {
        encoderHandler?.postDelayed({
            if (isEncoding.get() && mediaCodec != null) {
                try {
                    Log.d(TAG, "MediaCodec state check - isEncoding: ${isEncoding.get()}, mediaCodec: ${mediaCodec != null}")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaCodec state check failed: ${e.message}")
                }
            }
        }, 1000) // 延迟1秒检查
    }

    // 恢复MediaCodec的尝试
    private fun tryRecoverMediaCodec() {
        Log.w(TAG, "Attempting to recover MediaCodec...")
        encoderHandler?.postDelayed({
            if (!isEncoding.get()) {
                initMediaCodec()
            }
        }, 100) // 延迟100ms后重试
    }

    // 清理MediaCodec资源
    private fun cleanupMediaCodec() {
        mediaCodec?.let { codec ->
            try {
                if (isEncoding.get()) {
                    codec.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaCodec during cleanup: ${e.message}")
            }

            try {
                codec.release()
                Log.d(TAG, "MediaCodec released during cleanup")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaCodec during cleanup: ${e.message}")
            }
            mediaCodec = null
        }

        // 清理相关资源
        codecConfigBuffer = null
        codecConfigBufferSize = 0
        keyFrameBuffer = null
        keyFrameBufferSize = 0
        outputFrameBuffer = null
        outputFrameBufferSize = 0
    }

    // 恢复MediaCodec的错误处理
    private fun recoverMediaCodec() {
        Log.w(TAG, "Recovering MediaCodec from error state")
        encoderHandler?.post {
            if (isEncoding.get()) {
                try {
                    cleanupMediaCodec()
                    // 延迟重新初始化
                    encoderHandler?.postDelayed({
                        if (isEncoding.get()) {
                            initMediaCodec()
                        }
                    }, 50)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to recover MediaCodec: ${e.message}")
                }
            }
        }
    }

    // 处理输入缓冲区（将 frameQueue 的缓冲放入 MediaCodec）
    private fun processInputBuffer(index: Int) {
        if (!isEncoding.get()) {
            return // 如果不在编码状态，忽略输入缓冲区
        }

        mediaCodec?.let { codec ->
            try {
                val inputBuffer = codec.getInputBuffer(index)
                val yuvData = frameQueue.poll()
                if (yuvData != null && inputBuffer != null && isEncoding.get()) {
                    inputBuffer.clear()
                    inputBuffer.put(yuvData)

                    val timestamp = System.nanoTime() / 1000
                    codec.queueInputBuffer(index, 0, yuvData.size, timestamp, 0)

                    // 将 yuvData 立刻回收到池中（因为已经把数据复制到 codec 的 input buffer）
                    recycleEncodeBuffer(yuvData)
                } else {
                    // 没有可用数据或不在编码状态，返回空缓冲
                    try {
                        if (isEncoding.get()) {
                            codec.queueInputBuffer(index, 0, 0, 0, 0)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error queue empty input buffer: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing input buffer: ${e.message}")
                if (isEncoding.get()) {
                    try {
                        codec.queueInputBuffer(index, 0, 0, 0, 0)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error returning input buffer: ${ex.message}")
                    }
                }
            }
        }
    }

    // 处理输出缓冲区（复用输出缓冲，避免频繁分配）
    private fun processOutputBuffer(index: Int, info: MediaCodec.BufferInfo) {
        if (!isEncoding.get()) {
            try {
                mediaCodec?.releaseOutputBuffer(index, false)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaCodec already in wrong state when releasing output buffer")
            }
            return
        }

        mediaCodec?.let { codec ->
            try {
                val outputBuffer = codec.getOutputBuffer(index)
                if (outputBuffer != null && info.size > 0 && isEncoding.get()) {
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)

                    // 减少分配：复用 outputFrameBuffer
                    if (outputFrameBuffer == null || outputFrameBufferSize < info.size) {
                        outputFrameBuffer = ByteArray(info.size)
                        outputFrameBufferSize = info.size
                    }
                    // 将编码后数据读到复用缓冲中
                    outputBuffer.get(outputFrameBuffer, 0, info.size)

                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {

                        // 缓存 CODEC_CONFIG 数据（VPS/SPS/PPS）
                        if (codecConfigBuffer == null || codecConfigBufferSize < info.size) {
                            codecConfigBuffer = ByteArray(info.size)
                            codecConfigBufferSize = info.size
                        }
                        System.arraycopy(outputFrameBuffer!!, 0, codecConfigBuffer!!, 0, info.size)
//                        Log.d(TAG, "已缓存 CODEC_CONFIG 数据，长度: ${info.size}")
                    } else if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
//                        Log.d(TAG, "关键帧数据")
                        // 关键帧：需要在前面拼接 CODEC_CONFIG 数据
                        processKeyFrame(outputFrameBuffer!!, info)
                    } else {
                        // 非关键帧：直接发送（长度为 info.size）
                        sendVideoFrame(outputFrameBuffer!!, 0, info.size, isKeyFrame = false)
                    }
                }

                // 释放输出缓冲区
                codec.releaseOutputBuffer(index, false)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing output buffer: ${e.message}")
                try {
                    codec.releaseOutputBuffer(index, false)
                } catch (ex: Exception) {
                    Log.e(TAG, "Error releasing output buffer: ${ex.message}")
                }
            }
        }
    }

    // 处理关键帧：添加CODEC_CONFIG数据（复用 keyFrameBuffer）
    private fun processKeyFrame(frameData: ByteArray, info: MediaCodec.BufferInfo) {
        if (codecConfigBuffer == null) {
            Log.w(TAG, "CODEC_CONFIG not available, sending key frame without it")
            sendVideoFrame(frameData, 0, info.size, isKeyFrame = true)
            return
        }

        val requiredSize = codecConfigBufferSize + info.size

        val buffer = if (keyFrameBuffer != null && keyFrameBufferSize >= requiredSize) {
            keyFrameBuffer!!
        } else {
            ByteArray(requiredSize).also {
                keyFrameBuffer = it
                keyFrameBufferSize = requiredSize
            }
        }

        var offset = 0
        System.arraycopy(codecConfigBuffer, 0, buffer, offset, codecConfigBufferSize)
        offset += codecConfigBufferSize
        System.arraycopy(frameData, 0, buffer, offset, info.size)

        sendVideoFrame(buffer, 0, requiredSize, isKeyFrame = true)
    }

    // 发送视频帧数据（仍然调用底层 SDK）
    private fun sendVideoFrame(data: ByteArray, offset: Int = 0, length: Int = data.size, isKeyFrame: Boolean) {
        val frameType = if (isKeyFrame) 1 else 0
        // 注意：EasyRTCSdk.sendVideoFrame 可能会立即复制或发送数据，这里保持原调用不变
        EasyRTCSdk.sendVideoFrame(data, length, frameType, System.currentTimeMillis().toInt())

//        MagicFileHelper.getInstance().saveToFile(data, "video.h26${ if(useHevc) 5 else 4 }", true)
//        Log.v(TAG, "sendVideoFrame: length=$length, isKeyFrame=$isKeyFrame")
    }

    // 内部请求关键帧
    private fun requestKeyFrameInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mediaCodec?.apply {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                try {
                    setParameters(params)
                    Log.d(TAG, "Key frame requested")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request key frame: ${e.message}")
                }
            }
        }
    }

    // 暂停编码（保持MediaCodec实例）
    private fun pauseEncoding() {
        if (!isEncoding.get()) {
            return
        }

        isEncoding.set(false)

        // 清空帧队列，避免旧数据干扰
        frameQueue.clear()

        // 暂停MediaCodec
        try {
            mediaCodec?.stop()
            Log.d(TAG, "Encoding paused for camera switch")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing encoding: ${e.message}")
        }
    }

    // 恢复编码
    private fun resumeEncoding() {
        if (isEncoding.get()) {
            return
        }

        if (mediaCodec == null) {
            // 如果编码器不存在，重新初始化
            initMediaCodec()
            return
        }

        try {
            mediaCodec?.start()
            isEncoding.set(true)

            // 请求新的关键帧
            requestKeyFrameInternal()

            Log.d(TAG, "Encoding resumed after camera switch")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume encoding: ${e.message}")
            // 如果恢复失败，重新初始化编码器
            stopEncoding()
            initMediaCodec()
        }
    }

    // 停止编码（完全释放编码器资源）
    private fun stopEncoding() {
        if (!isEncoding.getAndSet(false)) {
            return
        }

        Log.d(TAG, "Stopping encoding...")

        // 清空帧队列
        frameQueue.clear()

        // 在编码器线程中安全释放资源
        encoderHandler?.post {
            cleanupMediaCodec()

            // 清理编码缓冲池
            if (::encodeBufferPool.isInitialized) {
                encodeBufferPool.clear()
            }

            Log.d(TAG, "Encoding fully stopped")
        }

        // 停止编码器线程
        encoderThread?.quitSafely()
        try {
            encoderThread?.join(1000) // 增加等待时间到1秒
            encoderThread = null
            encoderHandler = null
            Log.d(TAG, "Encoder thread stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Encoder thread join interrupted", e)
            Thread.currentThread().interrupt()
        }
    }

    // 初始化预览回调缓冲区（并创建 encodeBufferPool）
    private fun setupPreviewCallback() {
        camera?.let { cam ->
            // 移除旧回调
            cam.setPreviewCallbackWithBuffer(null)
            callbackBuffers.clear()

            // 计算帧缓冲区大小
            val format = cam.parameters.previewFormat
            val bitsPerPixel = when (format) {
                ImageFormat.NV21 -> 12  // NV21每像素12位 (YUV420SP)
                ImageFormat.YV12 -> 12  // YV12每像素12位 (YUV420P)
                else -> ImageFormat.getBitsPerPixel(format)
            }
            val bufferSize = previewWidth * previewHeight * bitsPerPixel / 8

            // 初始化 encodeBufferPool（池容量可比 frameQueue 稍大）
            val poolSize = 10.coerceAtLeast(BUFFER_COUNT + 2)
            encodeBufferPool = ArrayBlockingQueue(poolSize)
            // 预先填充一些池
            repeat(poolSize) {
                encodeBufferPool.offer(ByteArray(bufferSize))
            }

            // 分配 camera 回调缓冲区（相机专用）
            repeat(BUFFER_COUNT) {
                val buffer = ByteArray(bufferSize)
                callbackBuffers.add(buffer)
                cam.addCallbackBuffer(buffer)
            }

            // 设置带缓冲区的回调
            cam.setPreviewCallbackWithBuffer { data, camera ->
                if (data == null) return@setPreviewCallbackWithBuffer

                // 确保 i420_buffer 大小合适（复用）
                if (i420_buffer == null || i420_buffer!!.size != data.size) {
                    i420_buffer = ByteArray(data.size)
                }

                // 转 I420（旋转等）
                JNIUtil.ConvertToI420(data, i420_buffer, previewWidth, previewHeight, 0, 0, previewWidth, previewHeight, encodedRotation % 360, 2)

                // 将 i420 转回 codec 需要的格式到一个可复用缓冲
                // 先从池里取一个用于编码的缓冲
                val encodeBuf = encodeBufferPool.poll() ?: ByteArray(data.size) // 池空时新建（尽量避免）
                // 这里把转换后的 i420 写进 encodeBuf（注意 JNIUtil 目标为 byte[]）
                if (info.mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    JNIUtil.ConvertFromI420(i420_buffer, encodeBuf, encodedWidth, encodedHeight, 3)
                } else if (info.mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar) {
                    JNIUtil.ConvertFromI420(i420_buffer, encodeBuf, encodedWidth, encodedHeight, 3)
                } else if (info.mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    JNIUtil.ConvertFromI420(i420_buffer, encodeBuf, encodedWidth, encodedHeight, 0)
                } else {
                    JNIUtil.ConvertFromI420(i420_buffer, encodeBuf, encodedWidth, encodedWidth, 0)
                }

                // 将 encodeBuf 推入帧队列，供编码线程读取
                if (!frameQueue.offer(encodeBuf)) {
                    // 队列满了，回收 encodeBuf
                    recycleEncodeBuffer(encodeBuf)
                    Log.w(TAG, "帧队列已满，丢弃帧")
                }

                // 重要：原始 data 必须立即归还给 camera（避免复用冲突）
                camera.addCallbackBuffer(data)
            }
        }
    }

    // 回收 encodeBuffer 到池
    private fun recycleEncodeBuffer(buf: ByteArray) {
        if (::encodeBufferPool.isInitialized) {
            // 尽量放回池中，若池已满则丢弃（等待 GC）
            encodeBufferPool.offer(buf)
        }
    }

    // 释放相机资源（不释放编码器）
    fun releaseCamera() {
        // 先暂停编码
        pauseEncoding()

        // 停止预览和回调
        camera?.apply {
            try {
                setPreviewCallbackWithBuffer(null)
                stopPreview()
                Log.d(TAG, "Camera preview stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping camera preview: ${e.message}")
            }
        }

        // 清空回调缓冲区
        callbackBuffers.clear()

        // 清空帧队列
        frameQueue.clear()

        // 最后释放相机实例
        camera?.apply {
            try {
                release()
                Log.d(TAG, "Camera released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing camera: ${e.message}")
            }
            camera = null
        }
    }

    // 切换相机
    fun switchCamera(surfaceTexture: SurfaceTexture?, listener: CameraListener) {
        // 暂停编码而不是完全停止
        pauseEncoding()

        mCameraID = if (mCameraID == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }

        openCamera(surfaceTexture, listener)

        // 重新开始编码
        resumeEncoding()
    }

    // 改进的释放方法
    fun release() {
        Log.d(TAG, "Releasing CameraHelper resources")
        isEncoding.set(false)

        // 先释放相机
        releaseCamera()

        // 然后停止编码
        stopEncoding()

        // 确保线程完全停止
        encoderThread?.quitSafely()
        encoderThread = null
        encoderHandler = null

        Log.d(TAG, "CameraHelper fully released")
    }

    fun getEncodedDimensions() {

        val infos: ArrayList<VideoConfig.CodecInfo> = VideoConfig.getListEncoders(if (useHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC)

        for (info in infos) {
            Log.d(TAG, "CodecInfo: name=${info.mName}, colorFormat=${info.mColorFormat}")
        }

        if (!infos.isEmpty()) {
            val ci: VideoConfig.CodecInfo = infos.get(0)
            info.mName = ci.mName
            info.mColorFormat = ci.mColorFormat
        } else {
            Toast.makeText(this.context, "不支持 ${if (useHevc) 265 else 264} 硬件编码;请更换 硬件编码器", Toast.LENGTH_LONG).show()
        }

        encodedRotation = if (mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT) (360 - displayOrientation) % 360
        else displayOrientation

        encodedWidth = if (encodedRotation % 360 == 90 || encodedRotation % 360 == 270) previewHeight
        else previewWidth

        encodedHeight = if (encodedRotation % 360 == 90 || encodedRotation % 360 == 270) previewWidth
        else previewHeight
    }

    companion object {
        private const val TAG = "CameraHelper"
    }
}