package com.easydarwin.webrtc.fragment

import VideoEncodeConfig
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import cn.easyrtc.EasyRTCSdk
import cn.easyrtc.EasyRTCUser
import cn.easyrtc.helper.AudioHelper
import cn.easyrtc.helper.CameraHelper
import cn.easyrtc.helper.MagicFileHelper
import cn.easyrtc.helper.RemoteRTCAudioHelper
import cn.easyrtc.helper.RemoteRTCHelper
import com.easydarwin.webrtc.R
import com.easydarwin.webrtc.modal.DrawerManager
import com.easydarwin.webrtc.utils.SPUtil


class HomeFragment : Fragment(), TextureView.SurfaceTextureListener, CameraHelper.CameraListener, AudioHelper.OnAudioDataListener, EasyRTCSdk.EasyRTCEventListener {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private lateinit var endCallButton: ImageButton
    private lateinit var switchCameraButton: ImageButton

    // 视频视图
    private lateinit var mainVideoView: TextureView
    private lateinit var smallVideoView: TextureView
    private lateinit var smallVideoContainer: View

    // 摄像头帮助类 - 用于主预览
    private var mainCameraHelper: CameraHelper? = null
    private var remoteRTCHelper: RemoteRTCHelper? = null

    // 视频尺寸
    private val previewSize = Size(1280, 720)
    private val displayOrientation = 90 //预览旋转

    // 当前显示状态
    private var isMainViewShowingLocal = true // 默认主屏显示本地预览

    // Surface 状态
    private var mainSurfaceTexture: SurfaceTexture? = null
    private var smallSurfaceTexture: SurfaceTexture? = null

    private lateinit var audioHelper: AudioHelper

    // 抽屉管理器
    private lateinit var drawerManager: DrawerManager

    // 文件工具类
    private lateinit var mMagicFileHelper: MagicFileHelper
    private var mRemoteRTCAudioHelper: RemoteRTCAudioHelper? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mMagicFileHelper = MagicFileHelper.getInstance()
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化抽屉管理器 - 现在只需要传入 View，自动初始化
        drawerManager = DrawerManager(requireContext(), view) { user ->
            EasyRTCSdk.openPeerConnection(user)
            Toast.makeText(requireContext(), "正在连接 ${user.username}", Toast.LENGTH_LONG).show()
            Log.d(TAG, "连接已发起")
        }

        initViews(view)

        audioHelper = AudioHelper()
        audioHelper.setOnAudioDataListener(this)
        audioHelper.start()

        mRemoteRTCAudioHelper = RemoteRTCAudioHelper(requireContext())

        EasyRTCSdk.setEventListener(this)
    }

    private fun initViews(view: View) {
        mainVideoView = view.findViewById(R.id.mainVideoView)
        smallVideoView = view.findViewById(R.id.smallVideoView)
        smallVideoContainer = view.findViewById(R.id.smallVideoContainer)
        endCallButton = view.findViewById(R.id.endCallButton)
        switchCameraButton = view.findViewById(R.id.switchCameraButton)

        endCallButton.visibility = View.GONE

        // 设置 SurfaceTexture 监听
        mainVideoView.surfaceTextureListener = this
        smallVideoView.surfaceTextureListener = this


        smallVideoContainer.setOnClickListener {
            /*isMainViewShowingLocal = !isMainViewShowingLocal

            //先释放资源
            mainCameraHelper?.releaseCamera()
            remoteRTCHelper?.releaseVideoHandler()

            val config = getVideoEncodeConfig()

            mainCameraHelper = CameraHelper(requireContext(), config)

            mainCameraHelper?.openCamera(if (isMainViewShowingLocal) mainSurfaceTexture else smallSurfaceTexture, this)

            remoteRTCHelper = RemoteRTCHelper(Surface(if (isMainViewShowingLocal) smallSurfaceTexture else mainSurfaceTexture))*/

        }

        endCallButton.setOnClickListener {
            EasyRTCSdk.hangUp();
            endCallButton.visibility = View.GONE
        }

        switchCameraButton.setOnClickListener {
            switchCamera()
        }

        // 抽屉视图已经在 DrawerManager 构造函数中自动初始化
        Log.d(TAG, "视图初始化完成")
    }


    private fun getVideoEncodeConfig(): VideoEncodeConfig {
        val useHevc = SPUtil.getInstance().getIsHevc()
        val cameraId = SPUtil.getInstance().cameraId;
        val resolution = SPUtil.getInstance().getVideoResolution()
        val bitRate = SPUtil.getInstance().getVideoBitRateKbps()
        val frameRate = SPUtil.getInstance().getVideoFrameRate()

        return VideoEncodeConfig(useHevc, frameRate, cameraId, resolution, displayOrientation, bitRate)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "SurfaceTexture 可用: $width x $height")

        when (surface) {
            mainVideoView.surfaceTexture -> {
                mainSurfaceTexture = surface
                Log.d(TAG, "主视频 SurfaceTexture 已创建")
            }

            smallVideoView.surfaceTexture -> {
                smallSurfaceTexture = surface

                // 初始化 RemoteRTCHelper
                remoteRTCHelper = RemoteRTCHelper(Surface(surface), 720, 1280)
                Log.d(TAG, "小窗口 SurfaceTexture 已创建")
                // 设置远程视频渲染表面
            }
        }

        // 当两个 SurfaceTexture 都准备好后，开始摄像头预览
        if (mainSurfaceTexture != null && smallSurfaceTexture != null) {
            setupCameraPreviews()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "SurfaceTexture 尺寸改变: $width x $height")

        // 可以在这里调整摄像头预览尺寸
        when {
            surface == mainVideoView.surfaceTexture -> {
                Log.d(TAG, "主视频视图尺寸改变: $width x $height")
            }

            surface == smallVideoView.surfaceTexture -> {
                Log.d(TAG, "小窗口视图尺寸改变: $width x $height")
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d(TAG, "SurfaceTexture 被销毁")
        when {
            surface == mainVideoView.surfaceTexture -> {
                mainSurfaceTexture = null
            }

            surface == smallVideoView.surfaceTexture -> {
                smallSurfaceTexture = null
            }
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Surface 内容更新
    }

    /**
     * 设置摄像头预览
     */
    private fun setupCameraPreviews() {
        // 主屏幕显示摄像头预览
        startMainCameraPreview()
        isMainViewShowingLocal = true

    }

    /**
     * 开始主摄像头预览
     */
    private fun startMainCameraPreview() {
        try {
            val config = getVideoEncodeConfig()
            mainCameraHelper = CameraHelper(requireContext(), config)
            mainCameraHelper?.openCamera(mainSurfaceTexture!!, this)
            Log.d(TAG, "开始主摄像头预览")
        } catch (e: Exception) {
            Log.e(TAG, "启动主摄像头失败: ${e.message}")
        }
    }

    /**
     * 切换摄像头（前后置）
     */
    private fun switchCamera(): Boolean {
        Log.d(TAG, "切换视频显示模式")
        return try {
            mainCameraHelper?.switchCamera(if (isMainViewShowingLocal) mainSurfaceTexture else smallSurfaceTexture, this)
            true
        } catch (e: Exception) {
            Log.e(TAG, "切换摄像头失败: ${e.message}")
            false
        }
    }


    private fun stopCameraPreviews() {
        // 停止主摄像头预览
        mainCameraHelper?.release()
        mainCameraHelper = null

        remoteRTCHelper?.release()
        remoteRTCHelper = null

    }

    override fun onCameraOpened(camera: Camera) {
        Log.d(TAG, "摄像头已打开")
    }

    /**
     * CameraHelper 回调 - 摄像头错误
     */
    override fun onCameraError(error: String) {
        Log.e(TAG, "摄像头错误: $error")


    }


    /**
     *  RTC 事件实现
     */
    override fun onAudioData(data: ByteArray, size: Int) {
        EasyRTCSdk.sendAudioFrame(data, size, 1, (System.nanoTime() / 1000).toInt())
    }

    override fun onError(error: String) {
        TODO("Not yet implemented")
    }

    // SDK 事件回调
    override fun onCelling(user: EasyRTCUser) {
        /*EasyRTCSdk.handleCall(user, EasyRTCSdk.ACCEPTED)
        endCallButton.visibility = View.VISIBLE*/

        AlertDialog.Builder(requireContext()).setTitle("来电：${user.username}").setPositiveButton("接听") { _, _ ->
            EasyRTCSdk.handleCall(user, EasyRTCSdk.ACCEPTED)
            requireActivity().runOnUiThread {
                endCallButton.visibility = View.VISIBLE
            }
        }.setNegativeButton("拒绝") { _, _ ->
            EasyRTCSdk.handleCall(user, EasyRTCSdk.REJECTED)
        }.setCancelable(false).show()
    }

    override fun onConnected() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "已连接", Toast.LENGTH_SHORT).show()
        }
        drawerManager.startOnlineClientsTask()
    }

    override fun onConnectFailed() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "连接失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPeerClosed() {
        requireActivity().runOnUiThread {
            endCallButton.visibility = View.GONE
        }
    }

    override fun onDisconnected() {
        requireActivity().runOnUiThread {
            endCallButton.visibility = View.GONE
            Toast.makeText(requireContext(), "断开连接", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onVideoDataReceived(data: ByteArray, useHevc: Boolean, isKey: Boolean) {
//        Log.d(TAG, "收到视频数据 大小：${data.size} useHevc = $useHevc")
        remoteRTCHelper?.reinitVideoDecoder(if (useHevc) RemoteRTCHelper.CODEC_H265 else RemoteRTCHelper.CODEC_H264)
        remoteRTCHelper?.onRemoteVideoFrame(data, isKey)

//        MagicFileHelper.getInstance().saveToFile(data, "remote.h265")
    }

    override fun onAudioDataReceived(data: ByteArray) {
        mRemoteRTCAudioHelper?.processRemoteAudioFrame(data, data.size)
//        MagicFileHelper.getInstance().saveToFile(data, "remote.pcm")
//        Log.d(TAG, " onAudioDataReceived =   data: ${data.size}")
    }

    override fun onOnlineUserListReceived(data: String) {
//        Log.d(TAG, "收到在线用户列表: $data")
//        Log.d(TAG, "当前设备UUID: ${SPUtil.getUUID(requireContext())}")
        try {
            val userList = drawerManager.parseUserList(data)
            val currentUserId = SPUtil.getInstance().rtcUserUUID
            val filteredUsers = userList.filter { it.uuid != currentUserId }

//            Log.d(TAG, "过滤后用户数量: ${filteredUsers.size}")

            requireActivity().runOnUiThread {
                drawerManager.updateUserListUI(filteredUsers)
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理用户列表失败: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDeviceLog(msg: String) {
        if (msg.contains("GetOnlineDevices", ignoreCase = true)) return
        Log.d(TAG, " onDeviceLog =   msg: $msg")
    }


    /**
     *  生命周期
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")

        //EasyRTCSdk.hangUp();

        stopCameraPreviews()
        drawerManager.stopOnlineClientsTask()

        remoteRTCHelper?.release()
        remoteRTCHelper = null

        // 停止音频录制
        audioHelper.stop()
        mRemoteRTCAudioHelper?.release()
        mRemoteRTCAudioHelper = null
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (mainSurfaceTexture != null && smallSurfaceTexture != null) {
            setupCameraPreviews()
            remoteRTCHelper = RemoteRTCHelper(Surface(if (isMainViewShowingLocal) smallSurfaceTexture else mainSurfaceTexture))
        }

        drawerManager.startOnlineClientsTask()  //获取在线用户

        // 重新启动音频录制
        audioHelper.start()
        mRemoteRTCAudioHelper = RemoteRTCAudioHelper(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")

        //EasyRTCSdk.hangUp()
        EasyRTCSdk.release()

        stopCameraPreviews()
        drawerManager.stopOnlineClientsTask()
        // 彻底停止音频录制
        audioHelper.stop()
        mRemoteRTCAudioHelper?.release()
        mRemoteRTCAudioHelper = null

    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            Log.d(TAG, "Fragment 被隐藏")
            onPause()
        } else {
            Log.d(TAG, "Fragment 被显示")
            onResume()
        }
    }
}