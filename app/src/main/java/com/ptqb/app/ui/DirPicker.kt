package com.ptqb.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ptqb.app.data.Servers
import com.ptqb.app.data.currentClient
import kotlinx.coroutines.flow.first

/**
 * 目录路径输入框：可直接输入，也可点「选」从现有目录（收藏 + 当前服务器种子目录）中选择。
 */
@Composable
fun DirPathField(
    path: String,
    onPathChange: (String) -> Unit,
    label: String = "目录绝对路径",
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<List<String>>(emptyList()) }

    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { showPicker = true }) { Text("选") } },
    )

    if (showPicker) {
        LaunchedEffect(Unit) {
            try {
                val store = Servers.flow(context).first()
                val cur = store.servers.firstOrNull { it.id == store.currentId }
                val seedDirs = currentClient(context).torrents().map { it.downloadDir }
                options = ((cur?.dirs ?: emptyList()) + seedDirs).distinct().sorted()
            } catch (e: Exception) {
                options = emptyList()
            }
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择目录") },
            text = {
                if (options.isEmpty()) {
                    Text(
                        "暂无目录。可在 设置 → 目录 里新建，或点「从种子导入」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        options.forEach { d ->
                            item {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onPathChange(d); showPicker = false }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(d.substringAfterLast('/').ifBlank { d }, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "  $d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        )
    }
}
