package com.tvcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private var deviceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("tv_control", MODE_PRIVATE)
        var savedId = prefs.getString("device_id", null)

        if (savedId != null) {
            deviceId = savedId
            startLockService()
            finish()
        } else {
            // 生成本机设备 ID 作为配对码
            val newId = "TV-${String.format("%04d", Random().nextInt(10000))}"
            deviceId = newId
            prefs.edit().putString("device_id", newId).apply()

            findViewById<TextView>(R.id.pairingCode).text = newId
            // 显示配对说明
            findViewById<TextView>(R.id.pairingHint).text =
                "在手机上输入此配对码即可绑定控制"
        }
    }

    private fun startLockService() {
        val intent = Intent(this, LockService::class.java)
        startForegroundService(intent)
    }
}
