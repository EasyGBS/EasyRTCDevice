package com.easydarwin.webrtc.modal

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// 主响应结构
@Serializable
data class ApiResponse(
//    val devices: List<User>
    val devices: List<User>? = emptyList()
)

@Serializable
data class User(val id: String, val name: String)

