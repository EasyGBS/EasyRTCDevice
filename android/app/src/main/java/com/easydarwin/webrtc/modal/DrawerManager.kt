package com.easydarwin.webrtc.modal

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.easyrtc.EasyRTCSdk
import cn.easyrtc.EasyRTCUser
import com.easydarwin.webrtc.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class DrawerManager(private val context: Context, rootView: View, private val onUserCallClick: (EasyRTCUser) -> Unit) {

    // 抽屉视图
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerButton: ImageButton
    private lateinit var drawerContent: View
    private lateinit var userRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var userAdapter: UserAdapter

    // 在线用户定时任务
    private var onlineClientsJob: Job? = null
    private val onlineClientsInterval = 5000L

    init {
        initDrawerViews(rootView)
    }

    /**
     * 初始化抽屉视图
     */
    private fun initDrawerViews(rootView: View) {
        drawerLayout = rootView.findViewById(R.id.drawerLayout)
        drawerButton = rootView.findViewById(R.id.drawerButton)
        drawerContent = rootView.findViewById(R.id.drawerContent)
        userRecyclerView = rootView.findViewById(R.id.userRecyclerView)
        emptyStateText = rootView.findViewById(R.id.emptyStateText)

        drawerButton.visibility = View.GONE

        setupUserRecyclerView()
        setupClickListeners()

        Log.d(TAG, "抽屉视图初始化完成")
    }

    private fun setupUserRecyclerView() {
        userAdapter = UserAdapter { user ->
            onUserCallClick(user)
            drawerLayout.closeDrawer(drawerContent)
//            Log.d(TAG, "连接已发起: ${user.username}")
        }

        userRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userAdapter
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        }
    }

    private fun setupClickListeners() {
        drawerButton.setOnClickListener {
            toggleDrawer()
        }
    }

    fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(drawerContent)) {
            drawerLayout.closeDrawer(drawerContent)
        } else {
            drawerLayout.openDrawer(drawerContent)
        }
    }

    fun startOnlineClientsTask() {
        onlineClientsJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
//                EasyRTCSdk.getOnlineClients()
                delay(onlineClientsInterval)
            }
        }
    }

    fun stopOnlineClientsTask() {
        onlineClientsJob?.cancel()
        onlineClientsJob = null
    }

    fun parseUserList(jsonData: String): List<EasyRTCUser> {
        val userList = mutableListOf<EasyRTCUser>()

        try {
//            Log.d(TAG, "原始用户数据: $jsonData")

            val jsonObject = JSONObject(jsonData)
            val devicesArray = jsonObject.getJSONArray("devices")

            for (i in 0 until devicesArray.length()) {
                val deviceObj = devicesArray.getJSONObject(i)
                val user = EasyRTCUser(
                    uuid = deviceObj.optString("id", ""),
                    username = deviceObj.optString("name", "未知设备")
                )
                userList.add(user)
//                Log.d(TAG, "解析到用户: ${user.username} (UUID: ${user.uuid})")
            }

            Log.d(TAG, "共解析到 ${userList.size} 个用户")

        } catch (e: JSONException) {
            Log.e(TAG, "JSON 解析错误: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "解析用户列表失败: ${e.message}")
            e.printStackTrace()
        }

        return userList
    }

    fun updateUserListUI(users: List<EasyRTCUser>) {
//        Log.d(TAG, "更新用户列表UI，用户数量: ${users.size}")
        userAdapter.updateUsers(users)

        if (users.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            userRecyclerView.visibility = View.GONE
            emptyStateText.text = "暂无其他在线用户"
            Log.d(TAG, "显示空状态")
        } else {
            emptyStateText.visibility = View.GONE
            userRecyclerView.visibility = View.VISIBLE
//            Log.d(TAG, "显示用户列表")
        }
    }

    fun closeDrawer() {
        if (drawerLayout.isDrawerOpen(drawerContent)) {
            drawerLayout.closeDrawer(drawerContent)
        }
    }

    fun isDrawerOpen(): Boolean {
        return drawerLayout.isDrawerOpen(drawerContent)
    }

    // 用户列表适配器
    class UserAdapter(private val onCallClick: (EasyRTCUser) -> Unit) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        private val users = mutableListOf<EasyRTCUser>()

        fun updateUsers(newUsers: List<EasyRTCUser>) {
            users.clear()
            users.addAll(newUsers)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            holder.bind(users[position])
        }

        override fun getItemCount(): Int = users.size

        inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val userName: TextView = itemView.findViewById(R.id.userName)
            private val userUuid: TextView = itemView.findViewById(R.id.userUuid)
            private val callButton: ImageButton = itemView.findViewById(R.id.callButton)

            fun bind(user: EasyRTCUser) {
//                Log.d(TAG, "绑定用户数据: ${user.username}")

                userName.text = user.username
                userUuid.text = user.uuid

                callButton.setOnClickListener {
                    Log.d(TAG, "点击呼叫按钮: ${user.username}")
                    onCallClick(user)
                }

                itemView.setOnClickListener {
                    Log.d(TAG, "点击用户项: ${user.username}")
                    onCallClick(user)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DrawerManager"
    }
}