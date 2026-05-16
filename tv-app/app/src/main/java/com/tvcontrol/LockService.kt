package com.tvcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class LockService : Service() {
    private var mqttManager: MqttManager? = null
    private var isLocked = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: return START_STICKY

        mqttManager = MqttManager(this, deviceId)

        mqttManager?.onConnected {
            // 连接成功后上报在线状态
            mqttManager?.publishStatus("online")
        }

        mqttManager?.onStateUpdate { message ->
            when {
                message == "closed" -> lock()
                message == "open" -> unlock()
                message.startsWith("unlock:") -> {
                    val minutes = message.substringAfter("unlock:").toIntOrNull() ?: 5
                    unlock()
                    // 到期自动锁定
                    handler.postDelayed({
                        lock()
                    }, minutes * 60000L)
                }
            }
        }

        mqttManager?.connect()
        return START_STICKY
    }

    private fun lock() {
        if (isLocked) return
        isLocked = true
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        mqttManager?.publishStatus("locked")
    }

    private fun unlock() {
        if (!isLocked) return
        isLocked = false
        handler.removeCallbacksAndMessages(null)
        mqttManager?.publishStatus("unlocked")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mqttManager?.publishStatus("offline")
        mqttManager?.disconnect()
        handler.removeCallbacksAndMessages(null)
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
