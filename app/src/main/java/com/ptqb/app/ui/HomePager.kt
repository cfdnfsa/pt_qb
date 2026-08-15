package com.ptqb.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val PAGE_TITLES = listOf("下载", "PT", "设置")

/**
 * 全屏主界面：横滑三页（下载/PT/设置），顶栏大字标题点击弹切换菜单。
 * 无底部导航栏等常驻控件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePager(
    onOpenAdd: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val pagerState = rememberPagerState { PAGE_TITLES.size }
    val scope = rememberCoroutineScope()
    val torrentsVm: TorrentsViewModel = viewModel()
    val serversVm: ServersViewModel = viewModel()
    val torrentsState by torrentsVm.state.collectAsStateWithLifecycle()
    val serverStore by serversVm.state.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var serverSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    fun goTo(page: Int) = scope.launch { pagerState.animateScrollToPage(page) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：大字标题（点击弹切换菜单）+ 页指示点 + 当前页操作
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedContent(
                    targetState = pagerState.currentPage,
                    transitionSpec = {
                        val forward = targetState >= initialState
                        val sign = if (forward) 1 else -1
                        (slideInHorizontally { sign * it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -sign * it / 4 } + fadeOut())
                    },
                    label = "pageTitle",
                ) { page ->
                    Text(
                        PAGE_TITLES[page],
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(CircleShape).clickable { menuOpen = true },
                    )
                }
                Row(
                    Modifier.padding(start = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(PAGE_TITLES.size) { i ->
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage == 0) {
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
                                    onClick = { torrentsVm.setSort(s) },  // 不关菜单，便于切换正反序
                                )
                            }
                        }
                    }
                    IconButton(onClick = { torrentsVm.refresh() }) { Icon(Icons.Default.Refresh, "刷新") }
                }
            }

            // 下载页专属：当前服务器 + 全局速度，点击弹切换抽屉
            if (pagerState.currentPage == 0) {
                val s = torrentsState.server
                val st = torrentsState.stats
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (s?.name?.ifBlank { s.host } ?: "未选择服务器") + " ▾",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { serverSheet = true },
                    )
                    st?.let {
                        Text(
                            "   ↓${fmtSpeed(it.downloadSpeed)}  ↑${fmtSpeed(it.uploadSpeed)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (torrentsState.freeSpace > 0) {
                        Text(
                            "   剩余${fmtSize(torrentsState.freeSpace)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> DownloadsTab(vm = torrentsVm, onOpenAdd = onOpenAdd, onOpenDetail = onOpenDetail, onOpenServerSheet = { serverSheet = true })
                    1 -> PtPlaceholderTab()
                    2 -> SettingsTab(vm = serversVm)
                }
            }
        }

        // 顶部展开的页面切换菜单
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { menuOpen = false } }
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PAGE_TITLES.forEachIndexed { i, title ->
                    val current = i == pagerState.currentPage
                    val bg = if (current) MaterialTheme.colorScheme.primary else Color.Transparent
                    val fg = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    val sub = if (current) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .clickable(enabled = !current) { menuOpen = false; goTo(i) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(i + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = sub,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = if (current) FontWeight.Black else FontWeight.Normal,
                            color = fg,
                        )
                        Spacer(Modifier.weight(1f))
                        if (current) Text("当前", style = MaterialTheme.typography.labelMedium, color = sub)
                    }
                }
                HorizontalDivider()
            }
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
                onClick = { serverSheet = false; goTo(2) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
            ) { Text("管理服务器") }
        }
    }
}

@Composable
private fun PtPlaceholderTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "PT 站聚合搜索\nv2 开发中",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
