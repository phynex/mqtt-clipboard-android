package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 配置持久化：以 SharedPreferences 为底，向上提供 StateFlow，
 * UI 修改后 Service 能立刻感知（同一进程）。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<BrokerConfig> = _config.asStateFlow()

    fun current(): BrokerConfig = _config.value

    fun update(mutate: (BrokerConfig) -> BrokerConfig) {
        val next = mutate(_config.value)
        save(next)
        _config.value = next
    }

    private fun load(): BrokerConfig {
        val p = prefs
        return BrokerConfig(
            host = p.getString(KEY_HOST, BrokerConfig.DEFAULT_HOST) ?: BrokerConfig.DEFAULT_HOST,
            port = p.getInt(KEY_PORT, BrokerConfig.DEFAULT_PORT),
            useSsl = p.getBoolean(KEY_SSL, BrokerConfig.DEFAULT_SSL),
            trustAllCerts = p.getBoolean(KEY_TRUST_ALL, false),
            clientId = p.getString(KEY_CLIENT_ID, "") ?: "",
            username = p.getString(KEY_USER, "") ?: "",
            password = p.getString(KEY_PASS, "") ?: "",
            keepAliveSec = p.getInt(KEY_KEEPALIVE, 60),
            cleanSession = p.getBoolean(KEY_CLEAN_SESSION, true),
            subTopics = parseTopics(
                p.getString(KEY_SUB_TOPICS, BrokerConfig.DEFAULT_SUB_TOPICS)
                    ?: BrokerConfig.DEFAULT_SUB_TOPICS
            ),
            subQos = p.getInt(KEY_SUB_QOS, 1),
            pubTopic = p.getString(KEY_PUB_TOPIC, BrokerConfig.DEFAULT_PUB_TOPIC)
                ?: BrokerConfig.DEFAULT_PUB_TOPIC,
            pubQos = p.getInt(KEY_PUB_QOS, 1),
            retain = p.getBoolean(KEY_RETAIN, false),
            autoStartOnBoot = p.getBoolean(KEY_AUTOSTART, false),
            autostartAcknowledged = p.getBoolean(KEY_AUTOSTART_ACK, false),
            syncClipboardSend = p.getBoolean(KEY_SYNC_SEND, true),
            syncClipboardReceive = p.getBoolean(KEY_SYNC_RECV, true)
        )
    }

    private fun save(c: BrokerConfig) {
        prefs.edit()
            .putString(KEY_HOST, c.host)
            .putInt(KEY_PORT, c.port)
            .putBoolean(KEY_SSL, c.useSsl)
            .putBoolean(KEY_TRUST_ALL, c.trustAllCerts)
            .putString(KEY_CLIENT_ID, c.clientId)
            .putString(KEY_USER, c.username)
            .putString(KEY_PASS, c.password)
            .putInt(KEY_KEEPALIVE, c.keepAliveSec)
            .putBoolean(KEY_CLEAN_SESSION, c.cleanSession)
            .putString(KEY_SUB_TOPICS, c.subTopics.joinToString("\n"))
            .putInt(KEY_SUB_QOS, c.subQos)
            .putString(KEY_PUB_TOPIC, c.pubTopic)
            .putInt(KEY_PUB_QOS, c.pubQos)
            .putBoolean(KEY_RETAIN, c.retain)
            .putBoolean(KEY_AUTOSTART, c.autoStartOnBoot)
            .putBoolean(KEY_AUTOSTART_ACK, c.autostartAcknowledged)
            .putBoolean(KEY_SYNC_SEND, c.syncClipboardSend)
            .putBoolean(KEY_SYNC_RECV, c.syncClipboardReceive)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "mqtt_clipboard_sync"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_SSL = "use_ssl"
        const val KEY_TRUST_ALL = "trust_all_certs"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_KEEPALIVE = "keep_alive"
        const val KEY_CLEAN_SESSION = "clean_session"
        const val KEY_SUB_TOPICS = "sub_topics"
        const val KEY_SUB_QOS = "sub_qos"
        const val KEY_PUB_TOPIC = "pub_topic"
        const val KEY_PUB_QOS = "pub_qos"
        const val KEY_RETAIN = "retain"
        const val KEY_AUTOSTART = "auto_start_boot"
        const val KEY_AUTOSTART_ACK = "autostart_ack"
        const val KEY_SYNC_SEND = "sync_send"
        const val KEY_SYNC_RECV = "sync_recv"
    }
}
