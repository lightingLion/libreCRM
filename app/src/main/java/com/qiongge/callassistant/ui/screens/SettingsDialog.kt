package com.qiongge.callassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.qiongge.callassistant.data.preferences.ThemeMode
import com.qiongge.callassistant.ui.theme.AppSpacing
import com.qiongge.callassistant.ui.theme.cardSurface

@Composable
internal fun SettingsDialog(
    themeMode: ThemeMode,
    directoryLabel: String?,
    directoryMessage: String?,
    onThemeChange: (ThemeMode) -> Unit,
    onChooseDirectory: () -> Unit,
    onClearDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SettingsSectionTitle("外观")
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = AppSpacing.touchTarget)
                                .selectable(mode == themeMode, role = Role.RadioButton) { onThemeChange(mode) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Text(
                                mode.label,
                                modifier = Modifier.padding(start = AppSpacing.md),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                HorizontalDivider()
                SettingsSectionTitle("导出")
                val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
                ListItem(
                    headlineContent = { Text("CSV 默认保存目录") },
                    supportingContent = { Text(directoryLabel ?: "每次导出时选择位置") },
                    colors = rowColors,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onChooseDirectory),
                )
                if (directoryLabel != null) {
                    TextButton(onClick = onClearDirectory) { Text("取消默认目录") }
                }
                if (directoryMessage != null) {
                    Text(directoryMessage, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                SettingsSectionTitle("识别")
                ListItem(
                    headlineContent = { Text("模型切换") },
                    supportingContent = { Text("使用内置 OCR 模型 · 切换暂未开放") },
                    colors = rowColors,
                    modifier = Modifier.clickable(role = Role.Button) { showModel = true },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = MaterialTheme.colorScheme.cardSurface,
    )
    if (showModel) {
        AlertDialog(
            onDismissRequest = { showModel = false },
            title = { Text("模型切换") },
            text = { Text("当前使用应用内置的 OCR 模型。模型切换暂未开放。") },
            confirmButton = { TextButton(onClick = { showModel = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.xs),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
