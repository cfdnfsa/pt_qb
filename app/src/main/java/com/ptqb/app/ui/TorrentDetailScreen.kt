package com.ptqb.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ptqb.app.data.TrClient
import com.ptqb.app.data.TrException
import com.ptqb.app.data.TrTorrent
import com.ptqb.app.data.currentClient
import kotlinx.coroutines.launch

private suspend fun loadDetail(context: Context, hash: String): TrTorrent =
    currentClient(context).detail(hash) ?: throw TrException("种子不存在")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailScreen(hash: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<TrTorrent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }
    var showSpeedLimit by remember { mutableStateOf(false) }
    var pendingTitle by remember { mutableStateOf("") }
    var pendingExec by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun confirm(title: String, exec: () -> Unit) {
        pendingTitle = title; pendingExec = exec
    }

    fun load() {
        scope.launch {
            loading = true; error = null
            try {
                t = loadDetail(context, hash)
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }
    LaunchedEffect(hash) { load() }

    fun act(block: suspend (TrClient) -> Unit) {
        scope.launch {
            try {
                block(currentClient(context)); load()
            } catch (e: Exception) {
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("种子详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    if (!loading) IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "刷新") }
                },
            )
        },
    ) { pad ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中…") }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!) }
            t == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("种子不存在") }
            else -> {
                val torrent = t!!
                LazyColumn(
                    Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ===== 常规 =====
                    item {
                        Section("常规")
                        Spacer(Modifier.height(6.dp))
                        Text(torrent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { torrent.percentDone },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("状态", statusName(torrent))
                        InfoRow("大小", "${fmtSize(torrent.sizeWhenDone - torrent.leftUntilDone)} / ${fmtSize(torrent.sizeWhenDone)}")
                        InfoRow("下载目录", torrent.downloadDir)
                        InfoRow("标签", torrent.labels.joinToString("、").ifBlank { "—" })
                        InfoRow("添加时间", fmtTime(torrent.addedDate))
                        InfoRow("最近活动", fmtTime(torrent.activityDate))
                        InfoRow("Hash", torrent.hash)
                        if (torrent.error != 0) InfoRow("错误", torrent.errorString)
                    }

                    // ===== 状态统计（快） =====
                    item {
                        Section("快")
                        Spacer(Modifier.height(6.dp))
                        InfoRow("当前下载", fmtSpeed(torrent.rateDownload))
                        InfoRow("当前上传", fmtSpeed(torrent.rateUpload))
                        InfoRow("累计下载", fmtSize(torrent.downloadedEver))
                        InfoRow("累计上传", fmtSize(torrent.uploadedEver))
                        InfoRow("分享率", "%.2f".format(torrent.uploadRatio))
                        InfoRow("做种时长", fmtDuration(torrent.secondsSeeding))
                        InfoRow("下载用时", fmtDuration(torrent.secondsDownloading))
                    }

                    // ===== 用户（peers，默认收起） =====
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        SectionHeader(
                            "用户（${torrent.peers.size}：↓获取 ${torrent.peersGetting} / ↑发送 ${torrent.peersSending}）",
                            expanded,
                        ) { expanded = !expanded }
                        AnimatedVisibility(expanded) {
                            Column {
                                torrent.peers.forEach { p ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                p.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                p.client.ifBlank { "未知客户端" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            "${(p.progress * 100).toInt()}%" +
                                                when {
                                                    p.uploadingTo -> " ↑发送"
                                                    p.downloadingFrom -> " ↓获取"
                                                    else -> ""
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (torrent.peers.isEmpty()) {
                                    Text("暂无连接", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // ===== Tracker（默认收起） =====
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        SectionHeader("Tracker（${torrent.trackerStats.size}）", expanded) { expanded = !expanded }
                        AnimatedVisibility(expanded) {
                            Column {
                                torrent.trackerStats.forEach { tr ->
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            tr.announce,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "做种 ${tr.seeders} · 下载 ${tr.leechers} · ${when (tr.status) {
                                                0 -> "已禁用"; 1 -> "待 announce"; 2 -> "正常"; 3 -> "队列中"; else -> "状态${tr.status}"
                                            }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== 文件（默认收起） =====
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        SectionHeader("文件（${torrent.files.size}）", expanded) { expanded = !expanded }
                        AnimatedVisibility(expanded) {
                            Column {
                                torrent.files.forEach { f ->
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            f.name.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${fmtSize(f.bytesCompleted)} / ${fmtSize(f.length)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== 操作 =====
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirm("开始这个种子？") { act { it.start(listOf(torrent.id)) } } }) { Text("开始") }
                            OutlinedButton(onClick = { confirm("暂停这个种子？") { act { it.stop(listOf(torrent.id)) } } }) { Text("暂停") }
                            OutlinedButton(onClick = { confirm("强制开始（跳过排队）？") { act { it.startNow(listOf(torrent.id)) } } }) { Text("强制开始") }
                            OutlinedButton(onClick = { showSpeedLimit = true }) { Text("限速") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(torrent.magnetLink))
                                Toast.makeText(context, "已复制磁力链", Toast.LENGTH_SHORT).show()
                            }) { Text("复制磁力链") }
                            OutlinedButton(onClick = { showDelete = true }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // 单种子限速
    if (showSpeedLimit) {
        t?.let { torrent ->
            var down by remember(torrent.id) {
                mutableStateOf(if (torrent.downloadLimited) torrent.downloadLimit.toString() else "")
            }
            var up by remember(torrent.id) {
                mutableStateOf(if (torrent.uploadLimited) torrent.uploadLimit.toString() else "")
            }
            AlertDialog(
                onDismissRequest = { showSpeedLimit = false },
                title = { Text("限速（KB/s）") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(down, { down = it.filter { c -> c.isDigit() } }, label = { Text("下载限速（空=不限）") }, singleLine = true)
                        OutlinedTextField(up, { up = it.filter { c -> c.isDigit() } }, label = { Text("上传限速（空=不限）") }, singleLine = true)
                        Text(
                            "当前：下载${if (torrent.downloadLimited) "${torrent.downloadLimit}KB/s" else "不限"}，" +
                                "上传${if (torrent.uploadLimited) "${torrent.uploadLimit}KB/s" else "不限"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    OutlinedButton(onClick = {
                        act {
                            it.setSpeedLimit(
                                listOf(torrent.id),
                                downloadKb = down.trim().toLongOrNull(),
                                uploadKb = up.trim().toLongOrNull(),
                            )
                        }
                        showSpeedLimit = false
                    }) { Text("应用") }
                },
                dismissButton = { TextButton(onClick = { showSpeedLimit = false }) { Text("取消") } },
            )
        }
    }

    // 单条操作确认
    pendingExec?.let { exec ->
        AlertDialog(
            onDismissRequest = { pendingExec = null },
            title = { Text(pendingTitle) },
            text = {
                t?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { exec(); pendingExec = null }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingExec = null }) { Text("取消") } },
        )
    }

    if (showDelete) {
        var alsoDeleteData by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除种子") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(alsoDeleteData, { alsoDeleteData = it })
                    Text("同时删除下载数据")
                }
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    showDelete = false
                    act { it.remove(listOf(t!!.id), alsoDeleteData) }
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { OutlinedButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
