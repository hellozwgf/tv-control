// tv-app/app/src/main/java/com/tvcontrol/MainActivity.kt
package com.tvcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var socket: Socket
    private var deviceId: String? = null
    private var authToken: String? = null
    private var pairingCode: String? = null

    // 默认服务器地址，后续可配置
    private val serverUrl = "http://YOUR_SERVER_IP:3000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查是否已注册
        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        val savedToken = prefs.getString("auth_token", null)

        if (savedToken != null) {
            authToken = savedToken
            deviceId = prefs.getString("device_id", null)
            startLockService()
            finish() // 进入后台
        } else {
            connectAndRegister()
        }
    }

    private fun connectAndRegister() {
        try {
            socket = IO.socket(serverUrl)
            socket.on(Socket.EVENT_CONNECT) {
                socket.emit("register", JSONObject().put("type", "tv"))
            }
            socket.on("registered") { args ->
                val data = args[0] as JSONObject
                deviceId = data.getString("deviceId")
                authToken = data.getString("authToken")
                pairingCode = data.getString("pairingCode")

                runOnUiThread {
                    findViewById<TextView>(R.id.pairingCode).text = pairingCode
                }

                // 持久化
                getSharedPreferences("tv_control", MODE_PRIVATE).edit().apply {
                    putString("auth_token", authToken)
                    putString("device_id", deviceId)
                    apply()
                }
            }
            socket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startLockService() {
        val intent = Intent(this, LockService::class.java)
        startForegroundService(intent)
    }
}
