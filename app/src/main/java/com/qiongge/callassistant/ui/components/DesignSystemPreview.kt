package com.qiongge.callassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.qiongge.callassistant.ui.theme.AppSpacing
import com.qiongge.callassistant.ui.theme.AssistantTheme

@Preview(name = "品牌浅色", widthDp = 360)
@Preview(name = "品牌深色", widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "放大字体", widthDp = 320, fontScale = 1.5f)
@Composable
private fun WorkbookDesignSystemPreview() {
    AssistantTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text("金山湖回访", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("继续联系") }
                TextButton(onClick = {}) { Text("添加客户") }
                WorkbookCustomerRow(
                    ordinal = 1,
                    name = "陈女士",
                    phone = "00000000001",
                    result = "有意向",
                    propertyLabel = "3栋 · 1202室",
                    isCurrent = true,
                    shape = MaterialTheme.shapes.large,
                    onOpen = {},
                    onRemove = {},
                )
                WorkbookCustomerRow(
                    ordinal = 2,
                    name = "Samuel Lee",
                    phone = "00000000002",
                    result = "未联系",
                    propertyLabel = null,
                    isCurrent = false,
                    shape = MaterialTheme.shapes.large,
                    onOpen = {},
                    onRemove = {},
                )
            }
        }
    }
}
