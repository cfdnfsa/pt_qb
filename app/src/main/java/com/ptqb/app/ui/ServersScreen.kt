package com.ptqb.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ptqb.app.data.Server
import com.ptqb.app.data.ServerStore
import com.ptqb.app.data.Servers
import com.ptqb.app.data.TrClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ServersViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val state = MutableStateFlow(ServerStore())

    init {
        viewModelScope.launch {
            Servers.flow(app).collect { state.value = it }
        }
    }

    fun setCurrent(id: Long) {
        val s = state.value
        if (s.currentId != id) viewModelScope.launch {
            Servers.save(getApplication(), s.copy(currentId = id))
        }
    }

    fun upsert(server: Server) {
        val s = state.value
        val list = if (s.servers.any { it.id == server.id }) {
            s.servers.map { if (it.id == server.id) server else it }
        } else s.servers + server
        viewModelScope.launch { Servers.save(getApplication(), s.copy(servers = list)) }
    }

    fun delete(server: Server) {
        val s = state.value
        viewModelScope.launch {
            Servers.save(getApplication(), s.copy(
                servers = s.servers - server,
                currentId = if (s.currentId == server.id) 0 else s.currentId,
            ))
        }
    }

    suspend fun test(server: Server): String = try {
        val s = TrClient(server.host, server.username, server.password).session()
        "连接成功 Transmission ${s.version}"
    } catch (e: Exception) {
        "失败：${e.message}"
    }

    fun setPollInterval(sec: Int) {
        val s = state.value
        if (s.pollIntervalSec != sec) viewModelScope.launch {
            Servers.save(getApplication(), s.copy(pollIntervalSec = sec))
        }
    }

    // ===== 目录管理（针对当前服务器） =====

    private fun updateCurrent(transform: (Server) -> Server) {
        val s = state.value
        val cur = s.servers.firstOrNull { it.id == s.currentId } ?: return
        viewModelScope.launch {
            Servers.save(getApplication(), s.copy(
                servers = s.servers.map { if (it.id == cur.id) transform(it) else it },
            ))
        }
    }

    fun addDir(path: String) = updateCurrent { it.copy(dirs = (it.dirs + path).distinct().sorted()) }

    fun removeDir(path: String) = updateCurrent { it.copy(dirs = it.dirs - path) }

    /** 把当前服务器所有种子的实际目录合并进收藏 */
    fun importDirs() {
        viewModelScope.launch {
            try {
                val s = state.value
                val cur = s.servers.firstOrNull { it.id == s.currentId } ?: return@launch
                val seedDirs = TrClient(cur.host, cur.username, cur.password)
                    .torrents().map { it.downloadDir }.distinct()
                Servers.save(getApplication(), s.copy(
                    servers = s.servers.map {
                        if (it.id == cur.id) it.copy(dirs = (it.dirs + seedDirs).distinct().sorted()) else it
                    },
                ))
                android.widget.Toast.makeText(getApplication(), "已导入 ${seedDirs.size} 个目录", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(getApplication(), "导入失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** 设置页：服务器管理（后续站点管理等也加在这里） */
@Composable
fun SettingsTab(vm: ServersViewModel) {
    val store by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Server?>(null) }
    var showDirAdd by remember { mutableStateOf(false) }
    var deleteServer by remember { mutableStateOf<Server?>(null) }
    var removeDirPath by remember { mutableStateOf<String?>(null) }
    val currentServer = store.servers.firstOrNull { it.id == store.currentId }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "服务器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            if (store.servers.isEmpty()) {
                item { Text("还没有服务器，点右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(store.servers, key = { it.id }) { s ->
                    ServerRow(
                        server = s,
                        current = s.id == store.currentId,
                        onSwitch = { vm.setCurrent(s.id) },
                        onEdit = { editing = s },
                        onDelete = { deleteServer = s },
                    )
                }
            }
            // ===== 目录管理（当前服务器） =====
            item {
                Text(
                    "目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                if (currentServer == null) {
                    Text("未选择服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "「${currentServer.name.ifBlank { currentServer.host }}」的下载目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (currentServer.dirs.isEmpty()) {
                        Text("还没有收藏目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        currentServer.dirs.forEach { d ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { removeDirPath = d }) { Text("移除") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedButton(onClick = { showDirAdd = true }) { Text("新建目录") }
                        OutlinedButton(onClick = { vm.importDirs() }) { Text("从种子导入") }
                    }
                    Text(
                        "「移除」仅取消收藏，不会删除服务器上的目录和文件。\n新目录无需预先在服务器上创建，添加种子时 Transmission 会自动建立。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item {
                Text(
                    "偏好",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Text(
                    "自动刷新间隔（当前 ${if (store.pollIntervalSec == 0) "关" else "${store.pollIntervalSec}s"}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 15, 30, 60).forEach { sec ->
                        FilterChip(
                            selected = store.pollIntervalSec == sec,
                            onClick = { vm.setPollInterval(sec) },
                            label = { Text(if (sec == 0) "关" else "${sec}s") },
                        )
                    }
                }
            }
            item {
                Text(
                    "站点（v2）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Text("PT 站搜索与转存，开发中", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Default.Add, "添加") },
            text = { Text("添加服务器") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (showAdd) ServerEditDialog(null, { showAdd = false }, vm)
    editing?.let { ServerEditDialog(it, { editing = null }, vm) }

    if (showDirAdd) {
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDirAdd = false },
            title = { Text("新建目录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirPathField(path, { path = it })
                    Text(
                        "可先「选」现有目录再在此基础上修改。只保存到收藏，服务器上首次使用时自动创建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { vm.addDir(path.trim()); showDirAdd = false },
                    enabled = path.trim().startsWith("/"),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDirAdd = false }) { Text("取消") } },
        )
    }

    // 删除服务器确认
    deleteServer?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteServer = null },
            title = { Text("删除服务器？") },
            text = {
                Text(
                    "「${s.name.ifBlank { s.host }}」\n仅删除 App 内的连接配置和目录收藏，不影响服务器本身、种子和数据。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                OutlinedButton(onClick = { vm.delete(s); deleteServer = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteServer = null }) { Text("取消") } },
        )
    }

    // 移除目录确认
    removeDirPath?.let { d ->
        AlertDialog(
            onDismissRequest = { removeDirPath = null },
            title = { Text("移除目录？") },
            text = {
                Text(
                    "$d\n仅取消收藏，不会删除服务器上的目录和文件。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                OutlinedButton(onClick = { vm.removeDir(d); removeDirPath = null }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { removeDirPath = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(
    server: Server,
    current: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = {}, onLongClick = { menu = true })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name.ifBlank { server.host },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
                if (current) {
                    Text(
                        " · 当前",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Text(
                server.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onSwitch, enabled = !current) {
            Text(if (current) "使用中" else "切换")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun ServerEditDialog(initial: Server?, onDismiss: () -> Unit, vm: ServersViewModel) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "http://") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加服务器" else "编辑服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("地址 http://ip:端口") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("用户名（无认证可空）") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("密码") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            testing = true; testResult = null
                            scope.launch {
                                testResult = vm.test(Server(name = name, host = host.trim(), username = user, password = pass))
                                testing = false
                            }
                        },
                        enabled = host.isNotBlank() && !testing,
                    ) { Text(if (testing) "测试中…" else "测试连接") }
                    testResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    val s = (initial ?: Server()).copy(
                        name = name.trim().ifBlank { host.trim() },
                        host = host.trim(),
                        username = user,
                        password = pass,
                    )
                    vm.upsert(s)
                    onDismiss()
                },
                enabled = host.trim().startsWith("http"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
