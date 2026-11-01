package app.duinch.adbagent.features.tunnel.data

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

class MqttSignalingClient(
    roomCode: String,
    brokerHost: String = "broker.hivemq.com",
    private val onSignal: (type: String, payload: JSONObject) -> Unit
) {
    private val normalizedRoom = roomCode.replace("-", "").trim()
    private val subTopic = "adb-agent/$normalizedRoom/host"
    private val pubTopic = "adb-agent/$normalizedRoom/client"
    private val broker = brokerHost.ifBlank { "broker.hivemq.com" }

    private var mqttClient: MqttClient? = null
    private val pendingMessages = mutableListOf<MqttMessage>()
    private val lock = Any()

    fun connect() {
        Thread {
            try {
                Log.d(TAG, "Connecting to MQTT broker $broker for room $normalizedRoom...")
                val clientId = "adb-android-" + UUID.randomUUID().toString().take(8)
                val brokerUri = "tcp://$broker:1883"
                val client = MqttClient(brokerUri, clientId, MemoryPersistence())
                val options = MqttConnectOptions().apply {
                    serverURIs = arrayOf(
                        "tcp://$broker:1883",
                        "tcp://broker.hivemq.com:1883",
                        "tcp://broker.emqx.io:1883"
                    )
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                    isAutomaticReconnect = true
                }

                client.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (message == null) return
                        val body = String(message.payload, Charsets.UTF_8)
                        Log.d(TAG, "MQTT message arrived on $topic: $body")
                        val json = runCatching { JSONObject(body) }.getOrNull() ?: return
                        val type = json.optString("type")
                        val payload = json.optJSONObject("payload")
                        if (type.isNotBlank() && payload != null) {
                            onSignal(type, payload)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                client.connect(options)
                Log.d(TAG, "MQTT connected! Subscribing to $subTopic")
                client.subscribe(subTopic, 1)

                synchronized(lock) {
                    mqttClient = client
                    for (pending in pendingMessages) {
                        runCatching {
                            Log.d(TAG, "Flushing pending MQTT message to $pubTopic")
                            client.publish(pubTopic, pending)
                        }
                    }
                    pendingMessages.clear()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "MQTT connect failed: ${t.message}", t)
            }
        }.start()
    }

    fun send(type: String, payload: JSONObject) {
        val json = JSONObject().apply {
            put("type", type)
            put("sender", "android-device")
            put("payload", payload)
        }
        val msg = MqttMessage(json.toString().toByteArray(Charsets.UTF_8)).apply {
            qos = 1
        }
        synchronized(lock) {
            val client = mqttClient
            if (client != null && client.isConnected) {
                Thread {
                    runCatching {
                        Log.d(TAG, "Publishing $type to $pubTopic")
                        client.publish(pubTopic, msg)
                    }.onFailure { Log.e(TAG, "Publish failed", it) }
                }.start()
            } else {
                Log.d(TAG, "Queueing pending $type signal")
                pendingMessages.add(msg)
            }
        }
    }

    fun close() {
        Thread {
            synchronized(lock) {
                runCatching {
                    Log.d(TAG, "Closing MQTT client")
                    mqttClient?.disconnect()
                    mqttClient?.close()
                }
                mqttClient = null
                pendingMessages.clear()
            }
        }.start()
    }

    companion object {
        private const val TAG = "MqttSignalingClient"
    }
}
