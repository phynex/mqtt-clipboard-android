package com.example.myapplication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myapplication.AppGraph
import com.example.myapplication.runtime.AppStatusBus
import com.example.myapplication.service.MqttForegroundService

/**
 * 开机 / 快速开机 / 升级完成 后自动启动同步服务并连接 Broker。
 * 只有在配置页开启了「开机启动」时才会执行。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action !in TRIGGERS) return

        val config = AppGraph.settings.current()
        if (!config.autoStartOnBoot) {
            Log.i(TAG, "开机广播已忽略：未开启开机启动")
            return
        }

        AppStatusBus.log("收到开机广播，自动连接 Broker")
        val result = goAsync()
        try {
            MqttForegroundService.start(context, connect = true)
        } finally {
            result?.finish()
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val TRIGGERS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWER_ON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
