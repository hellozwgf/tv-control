package com.tvcontrol

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class WebSocketClient(
    private val serverUrl: String,
    private val authToken: String,
    private val deviceId: String
) {
    private var socket: Socket? = null
    private var onStateUpdate: ((String) -> Unit)? = null
    private var onUnlock: ((Long) -> Unit)? = null
    private var onConnected: (() -> Unit)? = null

    fun connect() {
        try {
            socket = IO.socket(serverUrl)
            socket!!.on(Socket.EVENT_CONNECT) {
                onConnected?.invoke()
                // 注册 socket 与 deviceId 的关联
                socket!!.emit("register_connection", JSONObject().apply {
                    put("authToken", authToken)
                    put("deviceId", deviceId)
                })
            }
            socket!!.on("state_update") { args ->
                val data = args[0] as JSONObject
                val state = data.getString("state")
                onStateUpdate?.invoke(state)
            }
            socket!!.on("unlock") { args ->
                val data = args[0] as JSONObject
                val expiresAt = data.getString("expiresAt")
                onUnlock?.invoke(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(expiresAt)?.time ?: 0L)
            }
            socket!!.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reportStatus(status: String) {
        socket?.emit("status_report", JSONObject().apply {
            put("status", status)
            put("authToken", authToken)
        })
    }

    fun queryState() {
        socket?.emit("get_state", JSONObject().apply {
            put("authToken", authToken)
        })
        socket?.once("current_state") { args ->
            val data = args[0] as JSONObject
            val state = data.getString("state")
            onStateUpdate?.invoke(state)
        }
    }

    fun disconnect() {
        socket?.disconnect()
    }

    fun setOnStateUpdate(callback: (String) -> Unit) { onStateUpdate = callback }
    fun setOnUnlock(callback: (Long) -> Unit) { onUnlock = callback }
    fun setOnConnected(callback: () -> Unit) { onConnected = callback }
}
