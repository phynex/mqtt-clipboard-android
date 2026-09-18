package com.example.myapplication.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.BrokerConfig
import com.example.myapplication.data.parseTopics

/**
 * 订阅配置：连接到 Broker 后自动订阅下列频道，页面不提供“订阅”按钮。
 */
@Composable
fun SubscribeCard(cfg: BrokerConfig, onChange: (BrokerConfig) -> Unit) {
    SectionCard(
        title = "订阅频道",
        subtitle = "连接成功后自动订阅，无需手动点击订阅按钮"
    ) {
        Field(
            label = "订阅主题（多个请用换行或逗号分隔）",
            value = cfg.subTopics.joinToString("\n"),
            singleLine = false
        ) { onChange(cfg.copy(subTopics = parseTopics(it))) }
        QosRow("订阅 QoS", cfg.subQos) { onChange(cfg.copy(subQos = it)) }
        Text(
            text = "提示：修改后立即生效，已连接时会自动重新订阅。",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
