package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.service.MqttForegroundService
import com.example.myapplication.ui.ConfigActions
import com.example.myapplication.ui.ConfigScreen
import com.example.myapplication.util.BootPermissions
import com.example.myapplication.util.PermissionSnapshot

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PermissionSnapshot.invalidate()
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            PermissionSnapshot.invalidate()
        }

    private var launchHandled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val tick by PermissionSnapshot.tick.collectAsState()
            val config by AppGraph.settings.config.collectAsState()

            if (!launchHandled) {
                LaunchedEffect(Unit) {
                    launchHandled = true
                    requestPostNotificationsIfNeeded()
                    // 已开启开机启动时，打开应用立即连接，确保后台常驻
                    if (config.autoStartOnBoot) {
                        MqttForegroundService.start(this@MainActivity, connect = true)
                    }
                }
            }

            ConfigScreen(
                settings = AppGraph.settings,
                permissionTick = tick,
                actions = ConfigActions(
                    onConnect = { MqttForegroundService.start(this@MainActivity, connect = true) },
                    onDisconnect = { MqttForegroundService.disconnect(this@MainActivity) },
                    onAutoStartToggle = { enabled ->
                        if (enabled) {
                            MqttForegroundService.start(this@MainActivity, connect = true)
                            ensurePermissions()
                        }
                    },
                    onRequestNotificationPermission = { requestPostNotificationsIfNeeded() },
                    onRefreshPermissions = { PermissionSnapshot.invalidate() },
                    onOpenNotificationSettings = {
                        runCatching {
                            startActivity(BootPermissions.notificationSettingsIntent(this@MainActivity))
                        }
                    },
                    onOpenBatterySettings = {
                        runCatching {
                            settingsLauncher.launch(
                                BootPermissions.requestIgnoreBatteryOptimizations(this@MainActivity)
                            )
                        }
                    },
                    onOpenAutostartSettings = {
                        val intent = BootPermissions.autostartIntent(this@MainActivity)
                            ?: BootPermissions.appDetailsIntent(this@MainActivity)
                        runCatching { settingsLauncher.launch(intent) }
                    },
                    onAcknowledgeAutostart = { ack ->
                        AppGraph.settings.update { it.copy(autostartAcknowledged = ack) }
                        PermissionSnapshot.invalidate()
                    },
                    onOpenAppDetails = {
                        runCatching {
                            settingsLauncher.launch(BootPermissions.appDetailsIntent(this@MainActivity))
                        }
                    },
                    onOpenImeSettings = {
                        runCatching {
                            settingsLauncher.launch(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    }
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        PermissionSnapshot.invalidate()
    }

    /** 仅在通知权限确实缺失时才发起申请，已授予时不再弹出、也不跳转任何系统页面 */
    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * 开启「开机启动」时顺带补齐权限：先申请通知权限，
     * 若后台运行（忽略电池优化）尚未授权，则弹出系统授权对话框。
     */
    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !BootPermissions.status(this, AppGraph.settings.current().autostartAcknowledged).notificationGranted
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!BootPermissions.isIgnoringBatteryOptimizations(this)) {
            runCatching {
                settingsLauncher.launch(BootPermissions.requestIgnoreBatteryOptimizations(this))
            }
        }
    }
}
