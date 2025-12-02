package com.easydarwin.webrtc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cn.easyrtc.EasyRTCSdk
import cn.easyrtc.helper.MagicFileHelper
import com.easydarwin.webrtc.fragment.AboutFragment
import com.easydarwin.webrtc.fragment.HomeFragment
import com.easydarwin.webrtc.fragment.SettingFragment
import com.easydarwin.webrtc.utils.SPUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var mBNView: BottomNavigationView? = null
    private var cFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SPUtil.init(this)

        MagicFileHelper.init(this)

        setContentView(R.layout.activity_main)

        checkPermission()

        initBNView()


    }

    private fun checkPermission() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun initBNView() {
        mBNView = findViewById<BottomNavigationView?>(R.id.bottomNavigationView)
        mBNView?.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_home -> {
                    Log.d("switch", "start switch to home")
                    switchFragment(HomeFragment(), "home")
                    Log.d("switch", "start switch to home ok")
                    true
                }

                R.id.navigation_setting -> {
                    Log.d("switch", "start switch to setting")
                    switchFragment(SettingFragment(), "setting")
                    Log.d("switch", "start switch to setting ok")
                    true
                }

                R.id.navigation_about -> {
                    switchFragment(AboutFragment(), "about")
                    true
                }

                else -> false
            }
        }

        switchFragment(HomeFragment(), "home")

    }

    private fun initRTC() {
        val rtcUserUUID = SPUtil.getInstance().rtcUserUUID
        EasyRTCSdk.init(rtcUserUUID, "RTC_${rtcUserUUID.takeLast(12)}", SPUtil.getInstance().getIsHevc()) //初始化 sdk
    }

    private fun releaseRTC() {
        EasyRTCSdk.release()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        // 隐藏当前Fragment
        cFragment?.let { transaction.hide(it) }


        // 检查Fragment是否已经存在
        val existingFragment = fragmentManager.findFragmentByTag(tag)
        if (existingFragment != null) {
            // 如果存在，显示它
            transaction.show(existingFragment)
            cFragment = existingFragment

        } else {
            // 如果不存在，添加新的Fragment
            transaction.add(R.id.fragment_container, fragment, tag)
            cFragment = fragment
        }
        transaction.commit()

        // 异步处理 RTC 初始化/释放
        handleRTCAsync(tag)
        /*if (tag == "home") {
            initRTC()
        } else {
            releaseRTC()
        }*/
    }

    private var rtcReleaseJob: Job? = null
    private var isRTCLive = false

    private fun handleRTCAsync(tag: String) {
        val shouldInit = tag == "home"

        if (shouldInit) {
            rtcReleaseJob?.cancel() // 取消之前的释放计划
            if (!isRTCLive) {
                lifecycleScope.launch(Dispatchers.IO) {
                    initRTC()
                    isRTCLive = true
                }
            }
        } else {
            // 延迟 2 秒再释放，防止快速切换回来
            rtcReleaseJob?.cancel()
            rtcReleaseJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(2000)
                if (!isFinishing && !isDestroyed) {
                    releaseRTC()
                    isRTCLive = false
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        EasyRTCSdk.setEventListener(null)
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

}