package com.example.myapplication.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.BrokerConfig
import com.example.myapplication.util.BootPermissions

/**
 * 剪贴板双向同步配置 + Android 10+ 后台读取限制说明与输入法通道入口。
 */
@Composable
fun ClipboardCard(
    cfg: BrokerConfig,
    perms: BootPermissions.Status,
    actions: ConfigActions,
    onChange: (BrokerConfig) -> Unit
) {
    SectionCard(
        title = "剪贴板同步",
        subtitle = "剪贴板更新自动发布；收到订阅消息自动写入剪贴板"
    ) {
        SwitchRow(
            title = "发送剪贴板变更",
            subtitle = "检测到新的剪贴板内容时发布到下方主题",
            checked = cfg.syncClipboardSend
        ) { onChange(cfg.copy(syncClipboardSend = it)) }
        SwitchRow(
            title = "接收消息写入剪贴板",
            subtitle = "收到订阅消息时更新本机剪贴板",
            checked = cfg.syncClipboardReceive
        ) { onChange(cfg.copy(syncClipboardReceive = it)) }

        Field("发布主题", cfg.pubTopic) { onChange(cfg.copy(pubTopic = it)) }
        QosRow("发布 QoS", cfg.pubQos) { onChange(cfg.copy(pubQos = it)) }
        SwitchRow("Retain 保留消息", null, cfg.retain) { onChange(cfg.copy(retain = it)) }

        // ---- 快捷入口：文本选择菜单 / 分享菜单（无需输入法豁免） ----
        Text(
            text = "快捷入口：任意应用中选中文本后，在弹出菜单里选择「${stringResource(R.string.process_text_label)}」，" +
                "即可复制到系统剪贴板并立即发送（文本由菜单直接传入，不受后台剪贴板限制）；" +
                "分享菜单中的「${stringResource(R.string.share_text_label)}」效果相同。",
            fontSize = 12.sp,
            color = Color.Gray
        )

        // ---- 后台读取剪贴板的限制说明与输入法通道 ----
        Text("后台读取剪贴板", fontSize = 13.sp, color = Color.Gray)
        Text(
            text = "Android 10 起，后台应用无法读取剪贴板。本应用在前台时可正常同步；" +
                "若需要在后台持续读取，请把内置输入法设为默认输入法以获得豁免。",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    perms.imeSelected -> "输入法状态：已设为默认（后台可持续读取）"
                    perms.imeEnabled -> "输入法状态：已启用，但未设为默认"
                    else -> "输入法状态：未启用"
                },
                modifier = androidx.compose.ui.Modifier.weight(1f),
                fontSize = 12.sp,
                color = if (perms.imeSelected) androidx.compose.ui.graphics.Color(0xFF2E7D32) else Color.Gray
            )
            TextButton(onClick = actions.onOpenImeSettings) { Text("设置", fontSize = 12.sp) }
        }
    }
}
