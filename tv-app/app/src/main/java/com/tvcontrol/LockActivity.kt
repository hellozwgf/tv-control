package com.tvcontrol

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏黑色背景，无任何 UI
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        // 设置纯黑背景
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(root)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, DeviceAdminReceiver::class.java)

        // 尝试启用 LockTask 模式
        try {
            if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
                devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
                startLockTask()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 拦截所有按键
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 拦截所有按键，防止退出
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return true
    }

    override fun onBackPressed() {
        // 禁用返回键
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 检测到用户试图离开，立即回到锁屏
        if (!isFinishing) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        try {
            stopLockTask()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
