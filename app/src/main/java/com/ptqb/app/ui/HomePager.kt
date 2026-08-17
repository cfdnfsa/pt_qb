package com.ptqb.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val PAGE_TITLES = listOf("下载", "PT", "设置")

/**
 * 全屏主界面：无顶部标题栏，页面切换走左下角按钮 + 底部菜单。
 * 三个页面叠放 zIndex 切换，懒创建、永久保活。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePager(
    onOpenAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    var page by remember { mutableStateOf(0) }
    var visited by remember { mutableStateOf(setOf(0)) }
    LaunchedEffect(page) { visited = visited + page }

    val torrentsVm: TorrentsViewModel = viewModel()
    val serversVm: ServersViewModel = viewModel()
    val torrentsState by torrentsVm.state.collectAsStateWithLifecycle()
    val serverStore by serversVm.state.collectAsStateWithLifecycle()

    var menuSheet by remember { mutableStateOf(false) }
    var serverSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var detailHash by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // 下载页头部：服务器（点击切换）+ 速度/剩余 + 排序 刷新（仅下载页显示）
            if (page == 0) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val s = torrentsState.server
                        val st = torrentsState.stats
                        Text(
                            (s?.name?.ifBlank { s.host } ?: "未选择服务器") + " ▾",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { serverSheet = true },
                        )
                        Row {
                            st?.let {
                                Text(
                                    "↓${fmtSpeed(it.downloadSpeed)}  ↑${fmtSpeed(it.uploadSpeed)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (torrentsState.freeSpace > 0) {
                                Text(
                                    "   剩余${fmtSize(torrentsState.freeSpace)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "   累计↑${fmtSize(st?.cumulative?.uploadedBytes ?: 0)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.MoreVert, "排序") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Sort.entries.forEach { s ->
                                val selected = torrentsState.sort == s
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                selected && torrentsState.sortAsc -> "${s.label} ↑"
                                                selected -> "${s.label} ↓"
                                                else -> s.label
                                            }
                                        )
                                    },
                                    onClick = { torrentsVm.setSort(s) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { torrentsVm.refresh() }) { Icon(Icons.Default.Refresh, "刷新") }
                }
            }

            // 三页叠放：zIndex 决定谁显示（每页铺背景色避免下层透出）；懒创建、永久保活
            Box(Modifier.fillMaxSize()) {
                if (0 in visited) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (page == 0) 1f else 0f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        DownloadsTab(
                            vm = torrentsVm,
                            onOpenAdd = onOpenAdd,
                            onOpenDetail = { detailHash = it },
                            onOpenServerSheet = { serverSheet = true },
                        )
                    }
                }
                if (1 in visited) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (page == 1) 1f else 0f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        PtTab(active = page == 1)
                    }
                }
                if (2 in visited) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (page == 2) 1f else 0f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SettingsTab(vm = serversVm)
                    }
                }
            }
        }

        // 左下角：页面切换按钮
        OutlinedButton(
            onClick = { menuSheet = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
                .navigationBarsPadding(),
        ) { Text("${PAGE_TITLES[page]} ▾") }

        // 种子详情：全屏覆盖层（不走 NavHost）
        detailHash?.let { hash ->
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TorrentDetailScreen(hash = hash, onBack = { detailHash = null })
            }
        }
    }

    // 页面切换菜单（底部弹出）
    if (menuSheet) {
        ModalBottomSheet(onDismissRequest = { menuSheet = false }) {
            Text(
                "页面",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            PAGE_TITLES.forEachIndexed { i, title ->
                val current = i == page
                ListItem(
                    headlineContent = { Text("%02d · %s".format(i + 1, title)) },
                    trailingContent = { if (current) Text("当前") },
                    modifier = Modifier.clickable {
                        menuSheet = false
                        page = i
                    },
                )
            }
            Spacer(Modifier.navigationBarsPadding().padding(bottom = 12.dp))
        }
    }

    // 服务器切换底部抽屉
    if (serverSheet) {
        ModalBottomSheet(onDismissRequest = { serverSheet = false }) {
            Text(
                "切换服务器",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            serverStore.servers.forEach { s ->
                val current = s.id == serverStore.currentId
                ListItem(
                    headlineContent = { Text(s.name.ifBlank { s.host }) },
                    supportingContent = { Text(s.host) },
                    trailingContent = { if (current) Text("当前") },
                    modifier = Modifier.clickable {
                        serversVm.setCurrent(s.id)
                        serverSheet = false
                    },
                )
            }
            OutlinedButton(
                onClick = { serverSheet = false; page = 2 },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
            ) { Text("管理服务器") }
        }
    }
}
