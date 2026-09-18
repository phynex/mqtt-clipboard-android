package com.example.myapplication.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.runtime.ConnKind
import com.example.myapplication.runtime.ConnState
import com.example.myapplication.service.MqttForegroundService

/**
 * 通知管理：
 * 只保留【一条】常驻状态通知（前台服务）——已连接为绿色，其它状态为灰色。
 * 连接成功 / 失败时复用同一条通知做一次提示（状态变化才响，同一状态不重复提示），
 * 因此不会出现两条相同通知。
 */
object Notifications {

    const val CHANNEL_STATUS = "mqtt_status"

    const val ID_STATUS = 1001

    private const val ACTION_OPEN = "com.example.myapplication.action.OPEN"
    private const val ACTION_CONNECT = "com.example.myapplication.action.CONNECT"
    private const val ACTION_DISCONNECT = "com.example.myapplication.action.DISCONNECT"

    private val COLOR_OK = Color.parseColor("#2E7D32")     // 绿：已连接
    private val COLOR_IDLE = Color.parseColor("#9E9E9E")   // 灰：未连接/失败

    @Volatile
    private var lastAlertKind: ConnKind? = null

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val status = NotificationChannel(
            CHANNEL_STATUS,
            "MQTT 连接状态",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "常驻通知：显示与 Broker 的连接状态（已连接绿色 / 其它灰色）"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(status)
    }

    /**
     * 常驻状态通知：随状态更新颜色与文案。
     * [alert] = true 时允许本次更新发出提示（仅在状态首次变为已连接 / 异常时传入），
     * 其余更新静默刷新，避免重复打扰。
     */
    fun buildStatus(context: Context, state: ConnState, alert: Boolean = false): Notification {
        val connected = state.kind == ConnKind.CONNECTED
        val accent = if (connected) COLOR_OK else COLOR_IDLE
        val title = when (state.kind) {
            ConnKind.CONNECTED -> "MQTT 已连接"
            ConnKind.CONNECTING -> "MQTT 正在连接…"
            ConnKind.ERROR -> "MQTT 连接异常"
            ConnKind.DISCONNECTED -> "MQTT 已断开"
            ConnKind.IDLE -> "MQTT 服务已停止"
        }
        val text = state.detail.ifBlank { title }

        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(context))
            .setColor(accent)
            .setColorized(true)

        // 依据当前状态提供互斥操作：已连接 → 断开；否则 → 连接
        if (connected) {
            builder.addAction(
                R.drawable.ic_stat_sync, "断开",
                serviceIntent(context, ACTION_DISCONNECT, 2)
            )
        } else if (state.kind != ConnKind.IDLE) {
            builder.addAction(
                R.drawable.ic_stat_sync, "重连",
                serviceIntent(context, ACTION_CONNECT, 3)
            )
        }

        return builder.build()
    }

    /**
     * 判断是否需要对本次状态变化发出提示：
     * 仅「已连接 / 连接异常」提示，且同一状态只提示一次（状态不变不再打扰）。
     */
    fun shouldAlert(state: ConnState): Boolean {
        when (state.kind) {
            ConnKind.CONNECTED, ConnKind.ERROR -> Unit
            else -> return false
        }
        if (lastAlertKind == state.kind) return false
        lastAlertKind = state.kind
        return true
    }

    fun resetAlertHistory() {
        lastAlertKind = null
    }

    private fun openIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_OPEN
        }
        return PendingIntent.getActivity(
            context, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val i = Intent(context, MqttForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            context, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
