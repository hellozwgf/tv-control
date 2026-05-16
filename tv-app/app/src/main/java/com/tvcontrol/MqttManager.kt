package com.tvcontrol

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.*

class MqttManager(context: Context, private val deviceId: String) {
    companion object {
        private const val BROKER_URL = "tcp://broker-cn.emqx.io:1883"
        private const val QOS = 1
    }

    private val client: MqttAndroidClient
    private val stateTopic = "tv/$deviceId/state"
    private val statusTopic = "tv/$deviceId/status"
    private val unlockTopic = "tv/$deviceId/unlock"

    var onStateUpdate: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null

    init {
        val clientId = "TV_${deviceId}_${System.currentTimeMillis()}"
        client = MqttAndroidClient(context, BROKER_URL, clientId)

        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                // 自动重连（Paho 内置）
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: return
                when (topic) {
                    stateTopic -> {
                        onStateUpdate?.invoke(payload.trim())
                    }
                    unlockTopic -> {
                        // 收到解锁指令，格式: "5" (分钟数)
                        // LockService 会处理到期自动锁定
                        onStateUpdate?.invoke("unlock:$payload")
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
    }

    fun connect() {
        try {
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 60
                isAutomaticReconnect = true
            }
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // 订阅 state 和 unlock 主题
                    val topics = arrayOf(stateTopic, unlockTopic)
                    val qos = intArrayOf(QOS, QOS)
                    client.subscribe(topics, qos)
                    onConnected?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    exception?.printStackTrace()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publishStatus(status: String) {
        val message = MqttMessage(status.toByteArray()).apply { qos = QOS }
        client.publish(statusTopic, message)
    }

    fun disconnect() {
        try {
            client.disconnect()
            client.close()
        } catch (_: Exception) {}
    }
}
