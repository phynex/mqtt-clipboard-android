package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.runtime.AppStatusBus
import com.example.myapplication.runtime.UiStatus
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.BootPermissions

data class ConfigActions(
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onAutoStartToggle: (Boolean) -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onRefreshPermissions: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onOpenAutostartSettings: () -> Unit,
    val onAcknowledgeAutostart: (Boolean) -> Unit,
    val onOpenAppDetails: () -> Unit,
    val onOpenImeSettings: () -> Unit
)

@Composable
fun ConfigScreen(
    settings: SettingsRepository,
    actions: ConfigActions,
    permissionTick: Int
) {
    val context = LocalContext.current
    val cfg by settings.config.collectAsState()
    val status: UiStatus by AppStatusBus.ui.collectAsState()

    val perms = remember(permissionTick, cfg.autostartAcknowledged) {
        BootPermissions.status(context, cfg.autostartAcknowledged)
    }

    MyApplicationTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusCard(status, cfg, actions)
                BrokerCard(cfg) { new -> settings.update { new } }
                SubscribeCard(cfg) { new -> settings.update { new } }
                ClipboardCard(cfg, perms, actions) { new -> settings.update { new } }
                BootCard(cfg, perms, actions) { new -> settings.update { new } }
                LogCard(status)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
