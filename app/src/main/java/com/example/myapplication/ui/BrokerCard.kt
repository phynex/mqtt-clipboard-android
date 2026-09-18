package com.example.myapplication.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.BrokerConfig

@Composable
fun BrokerCard(cfg: BrokerConfig, onChange: (BrokerConfig) -> Unit) {
    SectionCard(
        title = "Broker 配置",
        subtitle = "SSL/TLS：打开开关并使用 TLS 端口（通常 8883）"
    ) {
        Field("主机（支持 ssl:// 前缀）", cfg.host) { onChange(cfg.copy(host = it)) }
        IntField("端口", cfg.port, 1..65535) { onChange(cfg.copy(port = it)) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SSL/TLS 加密连接", modifier = androidx.compose.ui.Modifier.weight(1f), fontSize = 14.sp)
            TextButton(onClick = {
                onChange(cfg.copy(useSsl = false, port = 1883))
            }) { Text("TCP") }
            TextButton(onClick = {
                onChange(cfg.copy(useSsl = true, port = 8883))
            }) { Text("SSL") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (cfg.useSsl) "当前：SSL/TLS" else "当前：TCP 明文",
                modifier = androidx.compose.ui.Modifier.weight(1f),
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.Gray
            )
            androidx.compose.material3.Switch(
                checked = cfg.useSsl,
                onCheckedChange = { enabled ->
                    onChange(cfg.copy(useSsl = enabled, port = if (enabled) 8883 else 1883))
                }
            )
        }

        if (cfg.useSsl) {
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "信任自签名证书",
                subtitle = "关闭时按系统信任链校验并校验主机名；自签名证书请打开",
                checked = cfg.trustAllCerts
            ) { onChange(cfg.copy(trustAllCerts = it)) }
        }

        Spacer(Modifier.height(6.dp))
        Field("Client ID（留空自动生成）", cfg.clientId) { onChange(cfg.copy(clientId = it)) }
        Row {
            Spacer(androidx.compose.ui.Modifier.weight(1f))
            TextButton(onClick = { onChange(cfg.copy(clientId = randomClientId())) }) {
                Text("自动生成", fontWeight = FontWeight.Medium)
            }
        }
        Field("用户名（可选）", cfg.username) { onChange(cfg.copy(username = it)) }
        Field("密码（可选）", cfg.password, password = true) { onChange(cfg.copy(password = it)) }
        IntField("KeepAlive（秒）", cfg.keepAliveSec, 5..3600) { onChange(cfg.copy(keepAliveSec = it)) }
        SwitchRow("清除会话 Clean Session", null, cfg.cleanSession) { onChange(cfg.copy(cleanSession = it)) }
    }
}
