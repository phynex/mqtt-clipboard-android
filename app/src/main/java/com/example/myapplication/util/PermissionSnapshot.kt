package com.example.myapplication.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 权限状态刷新计数：从系统设置页返回或被用户操作后自增，
 * UI 据此重新计算权限状态。
 */
object PermissionSnapshot {

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    fun invalidate() {
        _tick.update { it + 1 }
    }
}
