package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.BrokerConfig
import com.example.myapplication.runtime.AppStatusBus
import com.example.myapplication.runtime.ConnKind
import com.example.myapplication.runtime.UiStatus
import com.example.myapplication.util.BootPermissions

private val Green = Color(0xFF2E7D32)
private val Gray = Color(0xFF9E9E9E)

@Composable
fun StatusCard(status: UiStatus, cfg: BrokerConfig, actions: ConfigActions) {
    val connected = status.state.kind == ConnKind.CONNECTED
    val color = if (connected) Green else Gray

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stateTitle(status.state.kind),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = color
                )
                Spacer(Modifier.weight(1f))
                ConnectToggleButton(
                    kind = status.state.kind,
                    onConnect = actions.onConnect,
                    onDisconnect = actions.onDisconnect
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(status.state.detail, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text("地址：${cfg.serverUri()}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

/**
 * 连接 / 断开 合并按钮：图标 + 文案随当前状态变化，
 * 未连接显示「连接」（电源图标），已连接 / 连接中显示「断开」（电源断开图标，可中止连接）。
 */
@Composable
private fun ConnectToggleButton(
    kind: ConnKind,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val linked = kind == ConnKind.CONNECTED || kind == ConnKind.CONNECTING
    Button(
        onClick = { if (linked) onDisconnect() else onConnect() },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (kind == ConnKind.CONNECTED) Green else MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            painter = painterResource(
                if (linked) R.drawable.ic_power_off else R.drawable.ic_power
            ),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(if (linked) "断开" else "连接")
    }
}

@Composable
fun BootCard(
    cfg: BrokerConfig,
    perms: BootPermissions.Status,
    actions: ConfigActions,
    onChange: (BrokerConfig) -> Unit
) {
    SectionCard(
        title = "开机启动",
        subtitle = "开启后系统启动即自动连接 Broker，并在通知栏常驻连接状态"
    ) {
        SwitchRow(
            title = "开机自动连接 Broker",
            subtitle = "需要同时授予下面几项权限，否则开机后仍可能被系统拦截",
            checked = cfg.autoStartOnBoot
        ) { enabled ->
            onChange(cfg.copy(autoStartOnBoot = enabled))
            actions.onAutoStartToggle(enabled)
        }

        if (!perms.notificationGranted) {
            PermissionRow(
                text = "通知权限：无法在通知栏显示连接状态（连接成功绿色 / 异常灰色）。",
                actionLabel = "去授权",
                onAction = actions.onRequestNotificationPermission
            )
        } else {
            GrantedRow("通知权限：已授予")
        }

        if (!perms.batteryOptimizationIgnored) {
            PermissionRow(
                text = "后台运行权限（忽略电池优化）：未加入白名单时系统会限制后台服务与自启动。",
                actionLabel = "去授权",
                onAction = actions.onOpenBatterySettings
            )
        } else {
            GrantedRow("后台运行权限：已加入电池优化白名单")
        }

        // 厂商自启动没有标准查询接口，只能由用户手动确认 —— 用“需手动确认”而非“未授权”，
        // 避免权限其实已给、界面却一直报警。
        if (perms.autostartIntent != null) {
            if (perms.autostartAcknowledged) {
                GrantedRow("厂商自启动：已确认开启")
            } else {
                InfoRow(
                    text = "厂商自启动：系统不提供查询接口，请在本机自启动管理页允许本应用开机自启动，再勾选下方确认。",
                    actionLabel = "前往设置",
                    onAction = actions.onOpenAutostartSettings
                )
            }
            AcknowledgeRow(
                acknowledged = cfg.autostartAcknowledged,
                text = "我已在厂商自启动设置中允许本应用自启动",
                onAcknowledge = actions.onAcknowledgeAutostart
            )
        } else {
            InfoRow(
                text = "未能识别厂商自启动管理页；若开机后没有自动连接，请在系统设置的「自启动 / 后台运行」中手动允许本应用。",
                actionLabel = "应用详情",
                onAction = actions.onOpenAppDetails
            )
        }

        if (perms.notificationGranted &&
            perms.batteryOptimizationIgnored &&
            (perms.autostartIntent == null || perms.autostartAcknowledged)
        ) {
            GrantedRow("开机启动所需权限已满足")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions.onRefreshPermissions) { Text("重新检测", fontSize = 12.sp) }
        }
    }
}

@Composable
fun LogCard(status: UiStatus) {
    SectionCard(title = "最近日志") {
        if (status.logs.isEmpty()) {
            Text("暂无日志", fontSize = 12.sp, color = Color.Gray)
        } else {
            status.logs.take(8).forEach { line ->
                Text(line, fontSize = 11.sp, color = Color.DarkGray)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
