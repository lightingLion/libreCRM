package com.qiongge.callassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qiongge.callassistant.data.local.entity.CustomerEntity
import com.qiongge.callassistant.ui.components.dismissKeyboardOnBlankTap
import com.qiongge.callassistant.ui.components.RaisedCard
import com.qiongge.callassistant.ui.theme.G2CornerShape

@Composable
fun TaskEditorDialog(
    initialTitle: String = "",
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var taskTitle by remember(title) { mutableStateOf(initialTitle) }

    AlertDialog(
        modifier = Modifier.dismissKeyboardOnBlankTap(),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = taskTitle,
                onValueChange = { taskTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名单名称") },
                shape = G2CornerShape(14.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(taskTitle.trim()) },
                enabled = taskTitle.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = G2CornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun CustomerEditorDialog(
    initialName: String = "",
    initialPhone: String = "",
    title: String,
    submitLabel: String = "保存",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var phone by remember(title, initialPhone) { mutableStateOf(initialPhone) }

    AlertDialog(
        modifier = Modifier.dismissKeyboardOnBlankTap(),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("姓名") },
                    shape = G2CornerShape(14.dp),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("电话号码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = G2CornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), phone.trim()) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
            ) {
                Text(submitLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = G2CornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private enum class AddCustomerMode {
    Choose,
    Create,
    SelectExisting,
}

@Composable
fun AddCustomerToTaskDialog(
    availableCustomers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    onSelectExisting: (Long) -> Unit,
) {
    var mode by remember { mutableStateOf(AddCustomerMode.Choose) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        modifier = Modifier.dismissKeyboardOnBlankTap(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (mode) {
                    AddCustomerMode.Choose -> "添加客户"
                    AddCustomerMode.Create -> "新建客户"
                    AddCustomerMode.SelectExisting -> "选择已有线索"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            when (mode) {
                AddCustomerMode.Choose -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "可以录入一位新客户，也可以把已有线索加入这份名单。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { mode = AddCustomerMode.Create },
                            modifier = Modifier.fillMaxWidth(),
                            shape = G2CornerShape(16.dp),
                        ) {
                            Text("新建客户")
                        }
                        OutlinedButton(
                            onClick = { mode = AddCustomerMode.SelectExisting },
                            modifier = Modifier.fillMaxWidth(),
                            shape = G2CornerShape(16.dp),
                        ) {
                            Text("从已有线索添加")
                        }
                    }
                }

                AddCustomerMode.Create -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("姓名") },
                            shape = G2CornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("电话号码") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = G2CornerShape(14.dp),
                        )
                    }
                }

                AddCustomerMode.SelectExisting -> {
                    if (availableCustomers.isEmpty()) {
                        Text(
                            "还没有可加入的其他线索。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(availableCustomers, key = CustomerEntity::id) { customer ->
                                RaisedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectExisting(customer.id)
                                            onDismiss()
                                        },
                                    contentPadding = PaddingValues(12.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            customer.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            customer.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == AddCustomerMode.Create) {
                TextButton(
                    onClick = { onCreate(name.trim(), phone.trim()) },
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                ) {
                    Text("添加到名单")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (mode == AddCustomerMode.Choose) onDismiss()
                    else mode = AddCustomerMode.Choose
                },
            ) {
                Text(if (mode == AddCustomerMode.Choose) "取消" else "返回")
            }
        },
        shape = G2CornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
