package com.ptqb.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ptqb.app.data.Server
import com.ptqb.app.data.TrClient
import com.ptqb.app.data.TrStats
import com.ptqb.app.data.TrTorrent
import com.ptqb.app.data.Servers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TorrentsState(
    val server: Server? = null,
    val torrents: List<TrTorrent> = emptyList(),
    val stats: TrStats? = null,
    val freeSpace: Long = 0,
    val statusFilter: TStatus = TStatus.ALL,
    val dirFilter: String? = null,
    val labelFilter: String? = null,
    val sort: Sort = Sort.ADDED,
    val sortAsc: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class TorrentsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val state = MutableStateFlow(TorrentsState())
    private var pollJob: Job? = null
    private var pollIntervalSec = 10
    private var client: TrClient? = null
    private var clientForId = 0L

    init {
        viewModelScope.launch {
            Servers.flow(app).collect { store ->
                val cur = store.servers.firstOrNull { it.id == store.currentId }
                val serverChanged = cur?.id != state.value.server?.id
                val intervalChanged = store.pollIntervalSec != pollIntervalSec
                if (intervalChanged) pollIntervalSec = store.pollIntervalSec
                if (serverChanged) {
                    state.update { it.copy(server = cur, torrents = emptyList(), stats = null, error = null) }
                }
                if (serverChanged || intervalChanged) restartPoll(cur)
            }
        }
    }

    private fun clientFor(s: Server): TrClient {
        if (clientForId != s.id) {
            client = TrClient(s.host, s.username, s.password)
            clientForId = s.id
        }
        return client!!
    }

    private fun restartPoll(server: Server?) {
        pollJob?.cancel()
        if (server == null || pollIntervalSec <= 0) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetch(server)
                delay(pollIntervalSec * 1000L)
            }
        }
    }

    private suspend fun fetch(server: Server) {
        state.update { it.copy(loading = true) }
        try {
            val c = clientFor(server)
            val ts = c.torrents()
            val st = c.stats()
            val free = runCatching { c.session().freeSpace }.getOrDefault(0L)
            state.update { it.copy(torrents = ts, stats = st, freeSpace = free, error = null, loading = false) }
        } catch (e: Exception) {
            state.update { it.copy(error = e.message ?: "网络错误", loading = false) }
        }
    }

    fun refresh() {
        state.value.server?.let { s -> viewModelScope.launch { fetch(s) } }
    }

    fun setStatusFilter(f: TStatus) = state.update { it.copy(statusFilter = f) }
    fun setDirFilter(d: String?) = state.update { it.copy(dirFilter = d) }
    fun setLabelFilter(l: String?) = state.update { it.copy(labelFilter = l) }

    /** 选排序维度；再次点同一切换正反序 */
    fun setSort(s: Sort) = state.update {
        if (it.sort == s) it.copy(sortAsc = !it.sortAsc) else it.copy(sort = s)
    }

    private fun act(block: suspend (TrClient) -> Unit) {
        val s = state.value.server ?: return
        viewModelScope.launch {
            try {
                block(clientFor(s))
                fetch(s)
            } catch (e: Exception) {
                state.update { it.copy(error = e.message) }
            }
        }
    }

    fun pause(ids: List<Long>) = act { it.stop(ids) }
    fun resume(ids: List<Long>) = act { it.start(ids) }
    fun forceStart(ids: List<Long>) = act { it.startNow(ids) }
    fun remove(ids: List<Long>, deleteData: Boolean) = act { it.remove(ids, deleteData) }
    fun verify(ids: List<Long>) = act { it.verify(ids) }
    fun reannounce(ids: List<Long>) = act { it.reannounce(ids) }

    /** 替换/添加/移除 Tracker：旧+新都填=替换，只填新=添加，只填旧=移除 */
    fun replaceTracker(ids: List<Long>, oldUrl: String, newUrl: String) = act {
        it.setTracker(
            ids,
            remove = if (oldUrl.isBlank()) emptyList() else listOf(oldUrl),
            add = if (newUrl.isBlank()) emptyList() else listOf(newUrl),
        )
    }

    fun moveLocation(ids: List<Long>, location: String, move: Boolean) = act { it.setLocation(ids, location, move) }
    fun setLabels(ids: List<Long>, labels: List<String>) = act { it.setLabels(ids, labels) }
}

/** 下载页：长按进入多选，多选栏做批量操作（顶栏/排序/刷新在 HomePager） */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsTab(
    vm: TorrentsViewModel,
    onOpenAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenServerSheet: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var batchDialog by remember { mutableStateOf<String?>(null) } // tracker / location / labels / delete
    var confirmAction by remember { mutableStateOf<String?>(null) } // pause / resume / verify / reannounce
    var pendingTitle by remember { mutableStateOf("") }
    var pendingDetail by remember { mutableStateOf("") }
    var pendingExec by remember { mutableStateOf<(() -> Unit)?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }

    fun confirm(title: String, detail: String, exec: () -> Unit) {
        pendingTitle = title; pendingDetail = detail; pendingExec = exec
    }

    // 切换服务器等导致列表重置时退出多选
    LaunchedEffect(state.server?.id) {
        selecting = false
        selectedIds = emptySet()
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) refreshing = false
    }

    val torrents = state.torrents
    val dirs = remember(torrents) { torrents.map { it.downloadDir }.distinct().sorted() }
    // 目录序列（首项 = 全部），左右滑动循环切换
    val dirOptions = remember(dirs) { listOf<String?>(null) + dirs }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.refresh() },
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(dirs) {
                    val threshold = 60.dp.toPx()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var acc = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            acc += change.positionChange().x
                            if (acc <= -threshold || acc >= threshold) {
                                val delta = if (acc < 0) 1 else -1
                                val cur = dirOptions.indexOf(state.dirFilter).let { if (it < 0) 0 else it }
                                val next = (cur + delta + dirOptions.size) % dirOptions.size
                                vm.setDirFilter(dirOptions[next])
                                break
                            }
                        }
                    }
                }
        ) {
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            val labels = remember(torrents) { torrents.flatMap { it.labels }.distinct().sorted() }
            val filtered = remember(torrents, state.statusFilter, state.dirFilter, state.labelFilter, state.sort, state.sortAsc, search) {
                val sf = state.statusFilter
                val df = state.dirFilter
                val lf = state.labelFilter
                val q = search.trim()
                torrents
                    .filter { sf.matches(it) }
                    .filter { df == null || it.downloadDir == df }
                    .filter { lf == null || lf in it.labels }
                    .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    .sortedWith(
                        when (state.sort) {
                            Sort.ADDED -> compareBy<TrTorrent> { it.addedDate }
                            Sort.PROGRESS -> compareBy { it.percentDone }
                            Sort.SIZE -> compareBy { it.totalSize }
                            Sort.SPEED -> compareBy { it.rateDownload + it.rateUpload }
                            Sort.NAME -> compareBy { it.name }
                            Sort.RATIO -> compareBy { it.uploadRatio }
                            Sort.SEED_TIME -> compareBy { it.secondsSeeding }
                            Sort.UPLOADED -> compareBy { it.uploadedEver }
                        }
                    )
                    .let { if (state.sortAsc) it else it.asReversed() }
            }
            val filteredIds = remember(filtered) { filtered.map { it.id }.toSet() }

            // 多选操作栏
            if (selecting) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Text(
                            "已选 ${selectedIds.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    item {
                        OutlinedButton(onClick = {
                            selectedIds = if (selectedIds.containsAll(filteredIds)) emptySet() else filteredIds
                        }) { Text(if (selectedIds.containsAll(filteredIds)) "清空" else "全选") }
                    }
                    item { TextButton(onClick = { confirmAction = "pause" }) { Text("暂停") } }
                    item { TextButton(onClick = { confirmAction = "resume" }) { Text("恢复") } }
                    item { TextButton(onClick = { confirmAction = "verify" }) { Text("校验") } }
                    item { TextButton(onClick = { confirmAction = "reannounce" }) { Text("汇报") } }
                    item { OutlinedButton(onClick = { batchDialog = "tracker" }) { Text("Tracker") } }
                    item { OutlinedButton(onClick = { batchDialog = "location" }) { Text("目录") } }
                    item { OutlinedButton(onClick = { batchDialog = "labels" }) { Text("标签") } }
                    item {
                        OutlinedButton(onClick = { batchDialog = "delete" }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    item {
                        IconButton(onClick = { selecting = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "退出多选")
                        }
                    }
                }
            }

            // 名称搜索
            if (state.server != null) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("搜索名称") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TStatus.entries) { f ->
                    FilterChip(
                        selected = state.statusFilter == f,
                        onClick = { vm.setStatusFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var dirMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { dirMenu = true },
                        label = { Text(state.dirFilter?.substringAfterLast('/')?.ifBlank { state.dirFilter ?: "" } ?: "目录") },
                    )
                    DropdownMenu(expanded = dirMenu, onDismissRequest = { dirMenu = false }) {
                        DropdownMenuItem(text = { Text("全部目录") }, onClick = { vm.setDirFilter(null); dirMenu = false })
                        dirs.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d.substringAfterLast('/').ifBlank { d }) },
                                onClick = { vm.setDirFilter(d); dirMenu = false },
                            )
                        }
                    }
                }

                var labelMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { labelMenu = true },
                        label = { Text(state.labelFilter ?: "标签") },
                    )
                    DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                        DropdownMenuItem(text = { Text("全部标签") }, onClick = { vm.setLabelFilter(null); labelMenu = false })
                        labels.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l) },
                                onClick = { vm.setLabelFilter(l); labelMenu = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size}/${torrents.size}" + if (state.loading) " …" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.server == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("未选择服务器")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenServerSheet) { Text("选择服务器") }
                    }
                }

                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (torrents.isEmpty()) "没有种子" else "筛选无结果")
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.hash }) { t ->
                        TorrentItem(
                            t = t,
                            selected = t.id in selectedIds,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    selectedIds = if (t.id in selectedIds) selectedIds - t.id else selectedIds + t.id
                                } else {
                                    onOpenDetail(t.hash)
                                }
                            },
                            onLongClick = {
                                if (!selecting) {
                                    selecting = true
                                    selectedIds = setOf(t.id)
                                }
                            },
                        )
                    }
                }
            }
            }
        }

        FloatingActionButton(
            onClick = onOpenAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, "添加") }
    }

    // 批量操作确认（防手滑）
    confirmAction?.let { action ->
        val label = when (action) {
            "pause" -> "暂停"; "resume" -> "恢复"; "verify" -> "重新校验"; else -> "汇报"
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("$label ${selectedIds.size} 个种子？") },
            text = {
                if (action == "verify") Text(
                    "校验会读取全部下载数据，种子多时占用大量磁盘 IO，耗时较长。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    val ids = selectedIds.toList()
                    when (action) {
                        "pause" -> vm.pause(ids)
                        "resume" -> vm.resume(ids)
                        "verify" -> vm.verify(ids)
                        "reannounce" -> vm.reannounce(ids)
                    }
                    confirmAction = null
                    selecting = false; selectedIds = emptySet()
                }) { Text("执行") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("取消") } },
        )
    }

    val ids = selectedIds.toList()
    when (batchDialog) {
        "tracker" -> BatchTrackerDialog(count = ids.size, onDismiss = { batchDialog = null }) { old, new ->
            val actLabel = when {
                old.isNotBlank() && new.isNotBlank() -> "替换"
                new.isNotBlank() -> "添加"
                else -> "移除"
            }
            confirm("修改 Tracker（${ids.size} 个）", "$actLabel：\n$old\n→ $new") {
                vm.replaceTracker(ids, old, new)
            }
        }
        "location" -> BatchLocationDialog(count = ids.size, onDismiss = { batchDialog = null }) { path, move ->
            confirm(
                "变更存储目录（${ids.size} 个）",
                "$path\n（${if (move) "移动已下载数据" else "不移动数据，仅改记录路径"}）",
            ) {
                vm.moveLocation(ids, path, move)
            }
        }
        "labels" -> BatchLabelsDialog(count = ids.size, onDismiss = { batchDialog = null }) { ls ->
            confirm("设置标签（${ids.size} 个）", "标签：${ls.joinToString("、")}") {
                vm.setLabels(ids, ls)
            }
        }
        "delete" -> BatchDeleteDialog(count = ids.size, onDismiss = { batchDialog = null }) { del ->
            vm.remove(ids, del)
            selecting = false; selectedIds = emptySet()
        }
    }

    // 修改类操作最终确认（表单填完后）
    pendingExec?.let { exec ->
        AlertDialog(
            onDismissRequest = { pendingExec = null },
            title = { Text(pendingTitle) },
            text = { Text(pendingDetail, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                OutlinedButton(onClick = {
                    exec()
                    pendingExec = null
                    selecting = false; selectedIds = emptySet()
                }) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { pendingExec = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun BatchTrackerDialog(count: Int, onDismiss: () -> Unit, onExecute: (old: String, new: String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 Tracker（$count 个）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { old = it }, label = { Text("旧 Tracker announce URL") }, singleLine = true)
                OutlinedTextField(new, { new = it }, label = { Text("新 Tracker announce URL") }, singleLine = true)
                Text(
                    "都填 = 替换；只填新 = 添加；只填旧 = 移除。\n需完整 announce 地址（可从详情 Tracker 区复制）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = { onExecute(old.trim(), new.trim()); onDismiss() },
                enabled = old.isNotBlank() || new.isNotBlank(),
            ) { Text("执行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BatchLocationDialog(count: Int, onDismiss: () -> Unit, onExecute: (path: String, move: Boolean) -> Unit) {
    var path by remember { mutableStateOf("") }
    var move by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("变更存储目录（$count 个）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DirPathField(path, { path = it }, label = "新目录绝对路径")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(move, { move = it })
                    Text("移动已下载数据到新目录")
                }
                Text(
                    "取消勾选则只改记录路径，数据不会移动（文件不在新路径时会校验失败）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = { onExecute(path.trim(), move); onDismiss() },
                enabled = path.isNotBlank(),
            ) { Text("执行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BatchLabelsDialog(count: Int, onDismiss: () -> Unit, onExecute: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置标签（$count 个）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("标签，逗号分隔") }, singleLine = true)
                Text(
                    "将覆盖所选种子的全部标签（Transmission 4.0+ 支持）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onExecute(text.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() })
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text("执行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BatchDeleteDialog(count: Int, onDismiss: () -> Unit, onExecute: (deleteData: Boolean) -> Unit) {
    var alsoDeleteData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 $count 个种子") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(alsoDeleteData, { alsoDeleteData = it })
                Text("同时删除下载数据")
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { onExecute(alsoDeleteData); onDismiss() }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TorrentItem(t: TrTorrent, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 多选模式：左侧选择方块（实心=选中）
        if (selecting) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.name.ifBlank { "（获取元数据中）" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { t.percentDone },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(6.dp))
            // 第一行：状态（带色点）+ 速度
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(t)))
                Text(
                    "${statusName(t)} ${(t.percentDone * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(t),
                    modifier = Modifier.padding(start = 5.dp),
                )
                if (t.status == 4) {
                    Text(
                        "  ↓${fmtSpeed(t.rateDownload)}  剩余${fmtEta(t.eta)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "↑${fmtSpeed(t.rateUpload)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 第二行：大小/已传 | 种|活 分享率 做种时长
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    "${fmtSize(t.sizeWhenDone - t.leftUntilDone)}/${fmtSize(t.sizeWhenDone)}  ↑传${fmtSize(t.uploadedEver)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "种${t.seeders()}|活${t.leechers()}  分享${"%.2f".format(t.uploadRatio)}  做种${fmtDuration(t.secondsSeeding)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
