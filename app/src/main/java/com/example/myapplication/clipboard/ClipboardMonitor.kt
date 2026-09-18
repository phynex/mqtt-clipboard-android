package com.example.myapplication.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.runtime.AppStatusBus

/**
 * 剪贴板监听。
 *
 * 说明：Android 10 起，只有“前台应用”或“默认输入法”才能读取剪贴板内容，
 * 因此这里同时做两件事：
 *  1) 注册 OnPrimaryClipChangedListener + 定时轮询（应用在前台时可靠工作）；
 *  2) 提供可选的 [ClipboardImeService] 输入法通道，用户启用后可获得后台读取豁免。
 */
class ClipboardMonitor(
    context: Context,
    private val onChange: (String) -> Unit
) {

    private val appContext = context.applicationContext
    private val manager: ClipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())

    private var started = false

    /** 上一次观察到的本机剪贴板内容 */
    private var lastObserved: String? = null

    /** 由 MQTT 消息写入剪贴板的内容 + 时间戳，用于避免回环 */
    private var remoteWrite: Pair<String, Long>? = null

    private var blockedLogged = false

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { handleChange() }

    private val pollRunnable = object : Runnable {
        override fun run() {
            handleChange()
            if (started) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        lastObserved = readText()
        runCatching { manager.addPrimaryClipChangedListener(listener) }
        handler.post(pollRunnable)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(pollRunnable)
        runCatching { manager.removePrimaryClipChangedListener(listener) }
    }

    /** 读取当前剪贴板文本；被系统限制时返回 null */
    fun readText(): String? = try {
        val clip = manager.primaryClip
        if (clip == null) {
            null
        } else if (clip.itemCount == 0) {
            ""
        } else {
            clip.getItemAt(0)?.text?.toString()
        }
    } catch (t: SecurityException) {
        logBlockedOnce()
        null
    } catch (t: Throwable) {
        Log.w(TAG, "读取剪贴板异常", t)
        null
    }

    /** 收到订阅消息后写入剪贴板 */
    fun writeText(text: String): Boolean = try {
        manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        remoteWrite = text to System.currentTimeMillis()
        lastObserved = text
        true
    } catch (t: Throwable) {
        AppStatusBus.log("写入剪贴板失败：${t.localizedMessage}")
        false
    }

    /** 标记某段文本来自远程（本地已写入），用于本地逻辑去重 */
    fun markRemoteWrite(text: String) {
        remoteWrite = text to System.currentTimeMillis()
        lastObserved = text
    }

    /** 首次拉取时把当前内容作为基线，避免把历史内容当成新变更发出去 */
    fun snapshotBaseline() {
        lastObserved = readText()
    }

    private fun handleChange() {
        val text = readText()
        if (text.isNullOrBlank()) {
            // 可能只是被系统拒绝访问
            return
        }
        val remote = remoteWrite
        if (remote != null && remote.first == text && System.currentTimeMillis() - remote.second < REMOTE_GUARD_MS) {
            lastObserved = text
            return
        }
        if (text == lastObserved) return
        lastObserved = text
        onChange(text)
    }

    private fun logBlockedOnce() {
        if (blockedLogged) return
        blockedLogged = true
        AppStatusBus.log("剪贴板读取被系统限制（Android 10+ 后台限制），可启用内置输入法通道")
    }

    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val CLIP_LABEL = "mqtt_clipboard"
        private const val POLL_INTERVAL_MS = 1000L
        private const val REMOTE_GUARD_MS = 30_000L
    }
}
