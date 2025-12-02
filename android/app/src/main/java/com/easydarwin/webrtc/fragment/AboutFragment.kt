package com.easydarwin.webrtc.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.easydarwin.webrtc.R

class AboutFragment : Fragment() {

    companion object {
        private const val TAG = "AboutFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // 加载布局文件
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 可以在这里进行其他初始化操作
    }

    /**
     * 当Fragment不再与用户交互时调用
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // 暂停动画、传感器监听等
    }

    /**
     * 当Fragment再次可见时调用
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // 恢复动画、传感器监听等
    }

    /**
     * 当Fragment视图被销毁时调用
     */
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
    }
}