package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.myapplication.service.MqttForegroundService

/**
 * 文本选择菜单 / 分享菜单入口。
 *
 * 用户在任意应用选中文本后，系统浮动菜单里会出现「复制并同步」；
 * 分享菜单里会出现「同步到电脑」。两者都会把文本交给本 Activity：
 *  1) 通过 [MqttForegroundService] 复制到系统剪贴板并发布到 Broker；
 *  2) 文本由 Intent 直接携带（ACTION_PROCESS_TEXT 的 EXTRA_PROCESS_TEXT），
 *     不走 ClipboardManager 读取，因此不受 Android 10+ 后台剪贴板限制约束。
 *
 * 本 Activity 不展示任何界面：取到文本后立即结束，使用透明主题避免闪屏。
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = extractText(intent)
        if (!text.isNullOrBlank()) {
            val delivered = MqttForegroundService.publishText(this, text)
            if (!delivered) {
                // 后台服务启动失败时至少完成“复制”这一步
                copyToClipboard(text)
            }
        }
        finish()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun extractText(intent: Intent?): String? {
        if (intent == null) return null
        val processed = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!processed.isNullOrEmpty()) return processed
        return intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        }
    }

    private companion object {
        const val CLIP_LABEL = "mqtt_clipboard"
    }
}
