package com.tvcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LockService : Service() {
    private var wsClient: WebSocketClient? = null
    private var isLocked = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        val authToken = prefs.getString("auth_token", null) ?: return START_STICKY
        val deviceId = prefs.getString("device_id", null) ?: return START_STICKY
        val serverUrl = prefs.getString("server_url", "http://YOUR_SERVER_IP:3000") ?: return START_STICKY

        wsClient = WebSocketClient(serverUrl, authToken, deviceId)

        wsClient?.setOnConnected {
            // 查询当前状态
            wsClient?.queryState()
        }

        wsClient?.setOnStateUpdate { state ->
            when (state) {
                "closed" -> lock()
                "open" -> unlock()
            }
        }

        wsClient?.setOnUnlock { expiresAt ->
            // 临时解锁，到期自动锁定
            unlock()
            val delay = expiresAt - System.currentTimeMillis()
            if (delay > 0) {
                android.os.Handler(mainLooper).postDelayed({
                    lock()
                }, delay)
            }
        }

        wsClient?.connect()
        return START_STICKY
    }

    private fun lock() {
        if (isLocked) return
        isLocked = true
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        wsClient?.reportStatus("locked")
    }

    private fun unlock() {
        if (!isLocked) return
        isLocked = false
        // LockActivity 会自行 finish
        wsClient?.reportStatus("unlocked")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wsClient?.disconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "tv_control",
                "电视管控",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "tv_control")
            .setContentTitle("电视管控")
            .setContentText("运行中")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
