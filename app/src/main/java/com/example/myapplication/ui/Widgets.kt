package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
fun IntField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(5)
            text = digits
            digits.toIntOrNull()?.let { if (it in range) onValueChange(it) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QosRow(label: String, current: Int, onSelect: (Int) -> Unit) {
    Text(label, fontSize = 13.sp, color = Color.Gray)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        (0..2).forEach { qos ->
            FilterChip(
                selected = current == qos,
                onClick = { onSelect(qos) },
                label = { Text("QoS $qos") }
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 权限项：未满足时显示提示文案 + 授权按钮 */
@Composable
fun PermissionRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF9800), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("未授权", fontSize = 12.sp, color = Color(0xFFEF6C00))
                }
                Spacer(Modifier.height(4.dp))
                Text(text, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel, fontSize = 12.sp) }
        }
    }
}

/** 提示项（非“未授权”）：无法自动检测、需用户自行确认的场景 */
@Composable
fun InfoRow(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
            .background(Color(0xFFE8F1FB), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF1565C0), RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("需手动确认", fontSize = 12.sp, color = Color(0xFF1565C0))
                }
                Spacer(Modifier.height(4.dp))
                Text(text, fontSize = 12.sp)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(actionLabel, fontSize = 12.sp) }
            }
        }
    }
}

/** 已满足的权限项 */
@Composable
fun GrantedRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun AcknowledgeRow(acknowledged: Boolean, text: String, onAcknowledge: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = acknowledged, onCheckedChange = onAcknowledge)
        Text(text, fontSize = 12.sp)
    }
}
