package com.ptqb.app.ui

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ptqb.app.data.TrException
import com.ptqb.app.data.currentClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTorrentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var dir by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加种子") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                link,
                { link = it },
                label = { Text("磁力链接或 .torrent 的 URL") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = fileUri == null,
            )

            if (fileUri == null) {
                OutlinedButton(onClick = { picker.launch("*/*") }) { Text("选择本地 .torrent 文件") }
            } else {
                val name = fileUri?.lastPathSegment?.substringAfterLast('/') ?: "文件"
                Column {
                    Text("已选择：$name", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { fileUri = null }) { Text("清除") }
                }
            }

            DirPathField(dir, { dir = it }, label = "下载目录（留空用服务器默认）")

            msg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(
                onClick = {
                    busy = true; msg = null
                    scope.launch {
                        try {
                            val client = currentClient(context)
                            val resp = if (fileUri != null) {
                                val bytes = context.contentResolver.openInputStream(fileUri!!)?.use { it.readBytes() }
                                    ?: throw TrException("无法读取所选文件")
                                client.add(metainfo = Base64.encodeToString(bytes, Base64.NO_WRAP), downloadDir = dir)
                            } else {
                                if (link.isBlank()) throw TrException("请填磁力/URL，或选择文件")
                                client.add(filename = link.trim(), downloadDir = dir)
                            }
                            msg = when {
                                resp.containsKey("torrent-duplicate") -> "该种子已存在于服务器"
                                resp.containsKey("torrent-added") -> "添加成功"
                                else -> "服务器已受理"
                            }
                        } catch (e: Exception) {
                            msg = "失败：${e.message}"
                        }
                        busy = false
                    }
                },
                enabled = !busy && (fileUri != null || link.isNotBlank()),
            ) { Text(if (busy) "添加中…" else "添加") }
        }
    }
}
