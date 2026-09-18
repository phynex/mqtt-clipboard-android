package com.example.myapplication.data

/**
 * MQTT Broker 连接配置与同步开关。
 */
data class BrokerConfig(
    /** 主机地址，可带 ssl:// / tcp:// 前缀，由 [normalizeHost] 统一处理 */
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val useSsl: Boolean = DEFAULT_SSL,
    /** 自签名证书场景：跳过证书链与主机名校验 */
    val trustAllCerts: Boolean = false,
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val keepAliveSec: Int = 60,
    val cleanSession: Boolean = true,

    /** 订阅主题，支持逗号或换行分隔；连接成功后自动订阅，无需手动点击 */
    val subTopics: List<String> = parseTopics(DEFAULT_SUB_TOPICS),
    val subQos: Int = 1,

    /** 剪贴板变更发布到的主题 */
    val pubTopic: String = DEFAULT_PUB_TOPIC,
    val pubQos: Int = 1,
    val retain: Boolean = false,

    /** 开机启动：开机后自动连接 Broker */
    val autoStartOnBoot: Boolean = false,
    /** 厂商自启动授权确认（系统无法自动检测，需用户在前去设置页授权后确认） */
    val autostartAcknowledged: Boolean = false,

    val syncClipboardSend: Boolean = true,
    val syncClipboardReceive: Boolean = true
) {
    fun normalizeHost(): String {
        var h = host.trim().trimEnd('/')
        SCHEMES.forEach { scheme ->
            if (h.startsWith(scheme, ignoreCase = true)) h = h.removePrefix(scheme).removePrefix(scheme.uppercase())
        }
        return h.trim().trim('/')
    }

    fun serverUri(): String {
        val scheme = if (useSsl) "ssl://" else "tcp://"
        return scheme + normalizeHost() + ":" + port
    }

    fun effectiveClientId(): String =
        clientId.trim().ifEmpty { "mqtt-clip-" + android.os.Build.MODEL.filter { it.isLetterOrDigit() } + "-" + hashCodeOfProcess() }

    fun isClipboardSyncEnabled(): Boolean = syncClipboardSend || syncClipboardReceive

    companion object {
        private val SCHEMES = listOf("tcp://", "ssl://", "mqtt://", "mqtts://", "ws://", "wss://")

        /** 默认配置：不预置任何服务器地址，需用户自行填写 */
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 1883
        const val DEFAULT_SSL = false
        const val DEFAULT_SUB_TOPICS = "clipboard/in"
        const val DEFAULT_PUB_TOPIC = "clipboard/out"
    }
}

private fun hashCodeOfProcess(): String =
    (System.currentTimeMillis() % 0xFFFF).toString(16).padStart(4, '0')

/** 把多行/逗号分隔的主题文本解析成列表 */
fun parseTopics(raw: String): List<String> =
    raw.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
