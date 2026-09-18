package com.example.myapplication.mqtt

import android.util.Log
import com.example.myapplication.data.BrokerConfig
import com.example.myapplication.runtime.AppStatusBus
import com.example.myapplication.runtime.ConnKind
import com.example.myapplication.runtime.ConnState
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MQTT 连接封装：
 * - 连接成功（含自动重连成功）后自动订阅配置主题，无需外部再触发订阅；
 * - 断线后按指数退避自动重连，直到主动 [disconnect] 或 [release]。
 */
class MqttEngine(
    private val onMessage: (topic: String, payload: String) -> Unit
) {

    private val TAG = "MqttEngine"

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mqtt-engine").apply { isDaemon = true } }

    @Volatile
    private var client: MqttAsyncClient? = null

    @Volatile
    private var desired: BrokerConfig? = null       // 非空表示“期望保持连接”

    @Volatile
    private var retryDelaySec = RETRY_MIN_SEC

    private var retryFuture: ScheduledFuture<*>? = null
    private val connecting = AtomicBoolean(false)

    fun connect(config: BrokerConfig) {
        desired = config
        retryDelaySec = RETRY_MIN_SEC
        cancelRetry()
        executor.execute { openConnection(config) }
    }

    fun reload(config: BrokerConfig) {
        val old = desired ?: return
        desired = config
        if (old.serverUri() != config.serverUri() ||
            old.username != config.username ||
            old.password != config.password ||
            old.clientId != config.clientId ||
            old.cleanSession != config.cleanSession ||
            old.useSsl != config.useSsl ||
            old.trustAllCerts != config.trustAllCerts ||
            old.subTopics != config.subTopics ||
            old.subQos != config.subQos
        ) {
            executor.execute {
                closeClientQuietly()
                openConnection(config)
            }
        }
    }

    fun publish(topic: String, payload: String, qos: Int, retain: Boolean) {
        if (topic.isBlank()) {
            AppStatusBus.log("发布失败：未配置发布主题")
            return
        }
        executor.execute {
            val c = client
            if (c == null || !c.isConnected) {
                AppStatusBus.log("未连接，消息暂不发送")
                return@execute
            }
            val msg = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                this.qos = qos.coerceIn(0, 2)
                this.isRetained = retain
            }
            val token = try {
                c.publish(topic, msg)
            } catch (t: Throwable) {
                AppStatusBus.log("发送异常: ${t.localizedMessage}")
                null
            }
            if (token != null) {
                AppStatusBus.log("已发送 ${payload.length} 字符 → $topic")
            }
        }
    }

    fun disconnect() {
        desired = null
        cancelRetry()
        executor.execute { closeClientQuietly() }
    }

    fun release() {
        disconnect()
        executor.execute { executor.shutdown() }
    }

    fun isConnected(): Boolean = client?.isConnected == true

    // ----------------------------------------------------------------- 内部

    private fun openConnection(cfg: BrokerConfig) {
        if (desired == null) return
        if (!connecting.compareAndSet(false, true)) return
        try {
            val host = cfg.normalizeHost()
            if (host.isBlank()) {
                AppStatusBus.setState(ConnState(ConnKind.ERROR, "未填写 Broker 主机"))
                AppStatusBus.log("未填写 Broker 主机")
                scheduleRetry()
                return
            }
            closeClientQuietly()

            val uri = cfg.serverUri()
            AppStatusBus.setState(ConnState(ConnKind.CONNECTING, "正在连接 $uri"))
            AppStatusBus.log("连接 $uri")

            val c = MqttAsyncClient(uri, cfg.effectiveClientId(), MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = cfg.cleanSession
                keepAliveInterval = cfg.keepAliveSec.coerceIn(5, 3600)
                socketFactory = SslSupport.socketFactoryFor(cfg)
                if (cfg.username.isNotBlank()) {
                    userName = cfg.username
                    password = cfg.password.toCharArray()
                }
                // 自动重连由本类统一控制（指数退避 + 状态通知），避免与 Paho 内部机制重复建链
                isAutomaticReconnect = false
                maxInflight = 100
                connectionTimeout = CONNECTION_TIMEOUT_SEC
            }
            c.setCallback(callback(cfg))
            client = c
            c.connect(options, "connect", object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    connecting.set(false)
                    // 成功分支交给 connectComplete 统一处理（含重连后的自动订阅）
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    connecting.set(false)
                    val reason = describe(exception)
                    AppStatusBus.setState(ConnState(ConnKind.ERROR, "连接失败：$reason"))
                    AppStatusBus.log("连接失败：$reason")
                    scheduleRetry()
                }
            })
        } catch (t: Throwable) {
            connecting.set(false)
            AppStatusBus.setState(ConnState(ConnKind.ERROR, "连接异常：${t.localizedMessage}"))
            AppStatusBus.log("连接异常：${t.localizedMessage}")
            scheduleRetry()
        }
    }

    private fun callback(cfgHolder: BrokerConfig) = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            connecting.set(false)
            // 连接过程中用户点了「断开」：丢弃这次连接结果，保持“已断开”
            if (desired == null) {
                AppStatusBus.log("已取消连接")
                executor.execute { closeClientQuietly() }
                return
            }
            retryDelaySec = RETRY_MIN_SEC
            AppStatusBus.setState(
                ConnState(
                    ConnKind.CONNECTED,
                    (if (reconnect) "已断线重连 · " else "已连接 · ") + (serverURI ?: desired?.serverUri().orEmpty())
                )
            )
            AppStatusBus.log(if (reconnect) "已自动重连" else "连接成功")
            subscribeAll(desired ?: cfgHolder)
        }

        override fun connectionLost(cause: Throwable?) {
            connecting.set(false)
            if (desired == null) return   // 主动断开后的关闭回调，不改写状态
            val reason = describe(cause)
            AppStatusBus.setState(ConnState(ConnKind.ERROR, "连接中断：$reason"))
            AppStatusBus.log("连接中断：$reason")
            scheduleRetry()
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val payload = runCatching { message?.payload?.toString(Charsets.UTF_8) }.getOrNull() ?: ""
            if (topic == null) return
            AppStatusBus.log("收到 $topic (${payload.length} 字符)")
            onMessage(topic, payload)
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    private fun subscribeAll(cfg: BrokerConfig) {
        val topics = cfg.subTopics.filter { it.isNotBlank() }
        if (topics.isEmpty()) {
            AppStatusBus.log("未配置订阅主题，跳过自动订阅")
            return
        }
        val c = client ?: return
        val qos = IntArray(topics.size) { cfg.subQos.coerceIn(0, 2) }
        runCatching {
            c.subscribe(topics.toTypedArray(), qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    AppStatusBus.log("已自动订阅：${topics.joinToString("、")}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    AppStatusBus.log("订阅失败：${describe(exception)}")
                }
            })
        }.onFailure { AppStatusBus.log("订阅异常：${it.localizedMessage}") }
    }

    private fun scheduleRetry() {
        if (desired == null) return
        cancelRetry()
        val delay = retryDelaySec
        retryDelaySec = (retryDelaySec * 2).coerceAtMost(RETRY_MAX_SEC)
        AppStatusBus.log("${delay}s 后重试连接")
        retryFuture = executor.schedule({
            retryFuture = null
            val cfg = desired
            if (cfg != null && !(client?.isConnected == true)) {
                connecting.set(false)
                openConnection(cfg)
            }
        }, delay.toLong(), TimeUnit.SECONDS)
    }

    private fun cancelRetry() {
        retryFuture?.cancel(false)
        retryFuture = null
    }

    private fun closeClientQuietly() {
        val c = client
        client = null
        if (c == null) return
        try {
            if (c.isConnected) c.disconnect(1000)
        } catch (t: Throwable) {
            Log.w(TAG, "断开异常", t)
        } finally {
            runCatching { c.close(true) }
        }
    }

    private fun describe(t: Throwable?): String = when {
        t == null -> "未知原因"
        t is MqttException -> "(${t.reasonCode}) ${t.message ?: ""}".trim()
        else -> t.localizedMessage ?: t.javaClass.simpleName
    }

    companion object {
        private const val CONNECTION_TIMEOUT_SEC = 12
        private const val RETRY_MIN_SEC = 5L
        private const val RETRY_MAX_SEC = 120L
    }
}
