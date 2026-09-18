package com.example.myapplication.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.myapplication.AppGraph
import com.example.myapplication.clipboard.ClipboardMonitor
import com.example.myapplication.data.BrokerConfig
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.mqtt.MqttEngine
import com.example.myapplication.notify.Notifications
import com.example.myapplication.runtime.AppStatusBus
import com.example.myapplication.runtime.ConnKind
import com.example.myapplication.runtime.ConnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 常驻同步服务：
 * - 保持 MQTT 连接（连接成功后自动订阅配置主题）；
 * - 监听剪贴板变化并发布；
 * - 收到订阅消息后写入剪贴板；
 * - 通过常驻通知展示绿色/灰色连接状态。
 */
class MqttForegroundService : Service() {

    private lateinit var settings: SettingsRepository
    private lateinit var engine: MqttEngine
    private var clipboard: ClipboardMonitor? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var settingsJob: Job? = null
    private var statusJob: Job? = null

    @Volatile
    private var config: BrokerConfig = BrokerConfig()

    /** 用户显式选中、但尚未连接成功时排队等待发送的文本 */
    @Volatile
    private var pendingText: String? = null

    override fun onCreate() {
        super.onCreate()
        settings = AppGraph.settings
        config = settings.current()

        Notifications.resetAlertHistory()
        val initial = AppStatusBus.state().let {
            if (it.kind == ConnKind.IDLE) ConnState(ConnKind.DISCONNECTED, "服务已启动") else it
        }
        AppStatusBus.setState(initial)
        runCatching { startForeground(Notifications.ID_STATUS, Notifications.buildStatus(this, initial)) }

        engine = MqttEngine { topic, payload ->
            // Paho 回调在非主线程，剪贴板与 UI 状态统一切回主线程
            mainHandler.post { handleIncoming(topic, payload) }
        }
        clipboard = ClipboardMonitor(this) { publishClip(it) }

        // 状态变化 → 更新唯一一条常驻通知（状态首次变为已连接 / 异常时提示一次）
        statusJob = AppStatusBus.ui.onEach { ui ->
            updateNotification(ui.state, Notifications.shouldAlert(ui.state))
            if (ui.state.kind == ConnKind.CONNECTED) flushPending()
        }.launchIn(scope)

        var firstEmission = true
        settingsJob = settings.config.onEach { cfg ->
            config = cfg
            val isFirst = firstEmission
            firstEmission = false
            if (isFirst) {
                applyClipboardState(cfg)
                return@onEach
            }
            AppStatusBus.log("配置已更新")
            // 连接参数变化才会重连，纯开关类改动只需调整剪贴板监听
            engine.reload(cfg)
            applyClipboardState(cfg)
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val config = AppGraph.settings.current()

        when (action) {
            ACTION_CONNECT, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startSync(config)
            }

            ACTION_DISCONNECT -> {
                AppStatusBus.log("主动断开连接")
                AppStatusBus.setState(ConnState(ConnKind.DISCONNECTED, "已断开"))
                stopSync(stopService = true)
            }

            ACTION_RELOAD -> {
                AppStatusBus.log("重新加载连接参数")
                engine.reload(config)
                applyClipboardState(config)
            }

            ACTION_CLIP_TEXT -> {
                val text = intent?.getStringExtra(EXTRA_CLIP_TEXT).orEmpty()
                if (text.isBlank()) {
                    AppStatusBus.log("忽略空的选中文本")
                } else {
                    // 来自文本选择菜单 / 分享菜单的显式同步请求：
                    // 文本由 Intent 携带，无需读取剪贴板，不受 Android 10+ 后台限制
                    clipboard?.writeText(text)
                    startSync(config)
                    publishManual(text)
                }
            }

            else -> {
                // ACTION_START
                val connect = intent?.getBooleanExtra(EXTRA_CONNECT, true) ?: true
                if (connect) startSync(config) else startPersistentOnly(config)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 继续常驻：Android 会在之后按 START_STICKY 策略重建服务
        try {
            val restart = Intent(applicationContext, MqttForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONNECT, false)
            ContextCompat.startForegroundService(this, restart)
        } catch (t: Throwable) {
            Log.w(TAG, "任务移除后重启服务失败", t)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        settingsJob?.cancel()
        statusJob?.cancel()
        clipboard?.stop()
        clipboard = null
        engine.release()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    // ------------------------------------------------------------ 行为

    private fun startSync(cfg: BrokerConfig) {
        this.config = cfg
        applyClipboardState(cfg)
        clipboard?.snapshotBaseline()
        if (engine.isConnected() && AppStatusBus.state().kind == ConnKind.CONNECTED) {
            AppStatusBus.log("已处于连接状态")
            return
        }
        engine.connect(cfg)
    }

    private fun startPersistentOnly(cfg: BrokerConfig) {
        this.config = cfg
        applyClipboardState(cfg)
        updateNotification(AppStatusBus.state())
    }

    private fun stopSync(stopService: Boolean) {
        engine.disconnect()
        clipboard?.stop()
        updateNotification(AppStatusBus.state())
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun applyClipboardState(cfg: BrokerConfig) {
        if (cfg.isClipboardSyncEnabled()) {
            clipboard?.start()
        } else {
            clipboard?.stop()
        }
    }

    private fun publishClip(text: String) {
        val cfg = config
        if (!cfg.syncClipboardSend) return
        AppStatusBus.clipOut(text)
        if (!engine.isConnected()) {
            AppStatusBus.log("剪贴板已更新，但当前未连接到 Broker")
            return
        }
        AppStatusBus.log("剪贴板变更 → 发布到 ${cfg.pubTopic}")
        engine.publish(cfg.pubTopic, text, cfg.pubQos, cfg.retain)
    }

    /**
     * 发布用户显式选中的文本。
     * 与 [publishClip] 不同：不受“发送剪贴板变更”开关限制，因为这是明确的单次用户操作；
     * 未连接时排队，等连接成功后自动补发。
     */
    private fun publishManual(text: String) {
        AppStatusBus.clipOut(text)
        if (engine.isConnected()) {
            AppStatusBus.log("选中文本 → 发布到 ${config.pubTopic}")
            engine.publish(config.pubTopic, text, config.pubQos, config.retain)
        } else {
            pendingText = text
            AppStatusBus.log("已复制到剪贴板，等待连接后发送")
        }
    }

    /** 连接成功后补发排队中的文本 */
    private fun flushPending() {
        val text = pendingText ?: return
        pendingText = null
        AppStatusBus.log("补发排队文本 → ${config.pubTopic}")
        engine.publish(config.pubTopic, text, config.pubQos, config.retain)
    }

    private fun handleIncoming(topic: String, payload: String) {
        val cfg = config
        if (!cfg.syncClipboardReceive) {
            AppStatusBus.log("忽略 $topic：未开启“接收写入剪贴板”")
            return
        }
        if (payload.isBlank()) return
        val monitor = clipboard ?: return
        if (monitor.writeText(payload)) {
            monitor.markRemoteWrite(payload)
            AppStatusBus.clipIn(payload)
            AppStatusBus.log("已写入剪贴板：${payload.take(40)}")
        } else {
            AppStatusBus.log("写入剪贴板被系统拦截")
        }
    }

    private fun updateNotification(state: ConnState, alert: Boolean = false) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        runCatching {
            manager.notify(Notifications.ID_STATUS, Notifications.buildStatus(this, state, alert))
        }
    }

    companion object {
        private const val TAG = "MqttForegroundService"

        const val ACTION_START = "com.example.myapplication.action.START"
        const val ACTION_CONNECT = "com.example.myapplication.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.myapplication.action.DISCONNECT"
        const val ACTION_RELOAD = "com.example.myapplication.action.RELOAD"
        const val ACTION_CLIP_TEXT = "com.example.myapplication.action.CLIP_TEXT"
        const val EXTRA_CONNECT = "extra_connect"
        const val EXTRA_CLIP_TEXT = "extra_clip_text"

        /** 文本选择菜单 / 分享菜单入口：复制文本并立即同步。返回是否已交给后台服务 */
        fun publishText(context: Context, text: String): Boolean {
            val intent = Intent(context, MqttForegroundService::class.java)
                .setAction(ACTION_CLIP_TEXT)
                .putExtra(EXTRA_CLIP_TEXT, text)
            var launched = false
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onSuccess { launched = true }
                .onFailure { t ->
                    Log.w(TAG, "前台服务启动失败，回退普通启动", t)
                    runCatching { context.startService(intent) }
                        .onSuccess { launched = true }
                        .onFailure { AppStatusBus.log("启动后台服务失败：${it.localizedMessage}") }
                }
            return launched
        }

        fun start(context: Context, connect: Boolean = true) {
            val intent = Intent(context, MqttForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONNECT, connect)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.w(TAG, "启动前台服务失败", it)
                AppStatusBus.log("启动后台服务失败：${it.localizedMessage}")
            }
        }

        fun connect(context: Context) = start(context, connect = true)

        fun disconnect(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).setAction(ACTION_DISCONNECT)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
