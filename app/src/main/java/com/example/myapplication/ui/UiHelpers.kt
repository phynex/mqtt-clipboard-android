package com.example.myapplication.ui

import com.example.myapplication.runtime.ConnKind
import kotlin.random.Random

/** 随机 Client ID：首位字母，后续字母数字 */
fun randomClientId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val suffix = buildString {
        repeat(9) { append(chars[Random.nextInt(chars.length)]) }
    }
    return "clip-$suffix"
}

/** 连接状态标题 */
fun stateTitle(kind: ConnKind): String = when (kind) {
    ConnKind.CONNECTED -> "已连接"
    ConnKind.CONNECTING -> "连接中…"
    ConnKind.ERROR -> "连接异常"
    ConnKind.DISCONNECTED -> "已断开"
    ConnKind.IDLE -> "未启动"
}
