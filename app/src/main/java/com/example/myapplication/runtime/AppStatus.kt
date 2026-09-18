package com.example.myapplication.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnKind {
    IDLE,          // 服务未启动
    CONNECTING,    // 正在连接/重连
    CONNECTED,     // 已连接
    DISCONNECTED,  // 已断开（人为）
    ERROR          // 连接失败/异常断开
}

data class ConnState(
    val kind: ConnKind = ConnKind.IDLE,
    val detail: String = "服务未启动"
)

data class UiStatus(
    val state: ConnState = ConnState(),
    val logs: List<String> = emptyList(),
    val lastClipOut: String? = null,
    val lastClipIn: String? = null
)

/**
 * UI 与 Service 之间的运行时状态总线。
 */
object AppStatusBus {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    private val _ui = MutableStateFlow(UiStatus())
    val ui: StateFlow<UiStatus> = _ui.asStateFlow()

    fun setState(state: ConnState) {
        _ui.update { it.copy(state = state) }
    }

    fun state(): ConnState = _ui.value.state

    fun log(line: String) {
        val stamp = timeFormat.format(Date())
        _ui.update { it.copy(logs = (listOf("[$stamp] $line") + it.logs).take(80)) }
    }

    fun clipOut(text: String) {
        _ui.update { it.copy(lastClipOut = text) }
    }

    fun clipIn(text: String) {
        _ui.update { it.copy(lastClipIn = text) }
    }
}
