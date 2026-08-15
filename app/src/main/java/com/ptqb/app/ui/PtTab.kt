package com.ptqb.app.ui

import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.core.view.doOnAttach
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptqb.app.data.Site
import com.ptqb.app.data.SiteStore
import com.ptqb.app.data.Sites
import com.ptqb.app.data.Servers
import com.ptqb.app.data.currentClient
import com.ptqb.app.data.downloadTorrentFile
import com.ptqb.app.data.isTorrentLink
import com.ptqb.app.data.mobileUa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SitesViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val state = MutableStateFlow(SiteStore())

    init {
        viewModelScope.launch {
            Sites.flow(app).collect { state.value = it }
        }
    }

    fun setLast(id: Long) {
        val s = state.value
        if (s.lastSiteId != id) viewModelScope.launch {
            Sites.save(getApplication(), s.copy(lastSiteId = id))
        }
    }

    fun upsert(site: Site) {
        val s = state.value
        val list = if (s.sites.any { it.id == site.id }) {
            s.sites.map { if (it.id == site.id) site else it }
        } else s.sites + site
        viewModelScope.launch {
            Sites.save(getApplication(),
                s.copy(sites = list, lastSiteId = if (s.lastSiteId == 0L) site.id else s.lastSiteId))
        }
    }

    fun delete(site: Site) {
        val s = state.value
        viewModelScope.launch {
            Sites.save(getApplication(), s.copy(
                sites = s.sites - site,
                lastSiteId = if (s.lastSiteId == site.id) (s.sites - site).firstOrNull()?.id ?: 0 else s.lastSiteId,
            ))
        }
    }
}

/** PT 页：站点浏览（WebView）+ 下载链接拦截转存；active=当前是否显示（页面常驻时控制返回键归属） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PtTab(active: Boolean = true) {
    val vm: SitesViewModel = viewModel()
    val store by vm.state.collectAsStateWithLifecycle()

    var siteSheet by remember { mutableStateOf(false) }
    var showSiteAdd by remember { mutableStateOf(false) }
    var editSite by remember { mutableStateOf<Site?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    // 返回键：网页有历史则网页后退，否则走系统默认
    BackHandler(enabled = active && canGoBack) { webView?.goBack() }

    val current = store.sites.firstOrNull { it.id == store.lastSiteId } ?: store.sites.firstOrNull()

    if (store.sites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有 PT 站点")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showSiteAdd = true }) { Text("添加站点") }
            }
        }
    } else if (current != null) {
        Column(Modifier.fillMaxSize()) {
            // 站点栏：网页后退 + 点击弹切换抽屉
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Text("←") }
                TextButton(onClick = { siteSheet = true }) {
                    Text(
                        "${current.name.ifBlank { current.url }} ▾",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "共 ${store.sites.size} 站",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                key(current.id) {
                    PtWebView(
                        startUrl = current.url,
                        onDownloadLink = { pendingLink = it },
                        onWebViewCreated = {
                            webView = it
                            canGoBack = false
                        },
                        onHistoryChange = { canGoBack = it },
                    )
                }
            }
        }
    }

    // 站点切换抽屉
    if (siteSheet) {
        ModalBottomSheet(onDismissRequest = { siteSheet = false }) {
            Text(
                "站点",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            store.sites.forEach { s ->
                val isCur = s.id == current?.id
                ListItem(
                    headlineContent = { Text(s.name.ifBlank { s.url }) },
                    supportingContent = { Text(s.url) },
                    trailingContent = { if (isCur) Text("当前") },
                    modifier = Modifier.clickable {
                        vm.setLast(s.id)
                        siteSheet = false
                    },
                )
            }
            OutlinedButton(
                onClick = { siteSheet = false; showSiteAdd = true },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
            ) { Text("添加站点") }
        }
    }

    if (showSiteAdd) SiteEditDialog(null, { showSiteAdd = false }, vm)
    editSite?.let { SiteEditDialog(it, { editSite = null }, vm) }

    // 转存弹窗
    pendingLink?.let { link ->
        DownloadToTrDialog(link = link, onDismiss = { pendingLink = null })
    }
}

/** 站内 WebView：手机 UA + 缩放 + 下拉刷新 + 登录态持久化 + 拦截下载链接转存 */
@Composable
private fun PtWebView(
    startUrl: String,
    onDownloadLink: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onHistoryChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val web = WebView(ctx).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = mobileUa(context)
                // 双指缩放
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        return if (isTorrentLink(url)) {
                            onDownloadLink(url)
                            true
                        } else {
                            false
                        }
                    }

                    // 导航历史变化时同步 canGoBack
                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        onHistoryChange(view.canGoBack())
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        (view.parent as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout)
                            ?.isRefreshing = false
                        // 只放开站点的缩放限制，不强制 initial-scale，
                        // 让 overview 模式自动把页面缩到整页可见（默认最小）
                        view.evaluateJavascript(
                            "(function(){var m=document.querySelector('meta[name=viewport]');" +
                                "if(m){var c=m.getAttribute('content')||'';" +
                                "c=c.replace(/user-scalable\\s*=\\s*no/gi,'user-scalable=yes')" +
                                ".replace(/maximum-scale\\s*=\\s*[\\d.]+/gi,'maximum-scale=10');" +
                                "if(!/user-scalable/i.test(c))c+=',user-scalable=yes';" +
                                "if(!/maximum-scale/i.test(c))c+=',maximum-scale=10';" +
                                "m.setAttribute('content',c)}else{var n=document.createElement('meta');" +
                                "n.name='viewport';n.content='width=980, user-scalable=yes, maximum-scale=10';" +
                                "document.head.appendChild(n)}})()", null)
                    }
                }
                // 等 attach 到窗口后再首次加载，避免 factory 阶段加载导致白屏
                doOnAttach { (it as WebView).loadUrl(startUrl) }
            }
            onWebViewCreated(web)
            androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                addView(web)
                setOnRefreshListener { web.reload() }
            }
        },
    )
}

/** 转存弹窗：下载链接 → 当前服务器，可选目录 */
@Composable
private fun DownloadToTrDialog(link: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dir by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var serverName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val st = Servers.flow(context).first()
        serverName = st.servers.firstOrNull { it.id == st.currentId }?.name ?: "未选择服务器"
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (msg?.startsWith("转存成功") == true) "已转存" else "转存到下载器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标：$serverName", style = MaterialTheme.typography.labelLarge)
                if (link.startsWith("magnet:", true)) {
                    Text("磁力链接", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        link,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DirPathField(dir, { dir = it }, label = "下载目录（留空用服务器默认）")
                msg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("转存成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    busy = true; msg = null
                    // 主线程取 Cookie 和 UA（与 WebView 一致，站点校验才通过）
                    val cookie = CookieManager.getInstance().getCookie(link)
                    val ua = mobileUa(context)
                    scope.launch {
                        try {
                            val client = currentClient(context)
                            if (link.startsWith("magnet:", true)) {
                                client.add(filename = link, downloadDir = dir)
                            } else {
                                val bytes = downloadTorrentFile(link, cookie, referer = link, ua = ua)
                                client.add(
                                    metainfo = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    downloadDir = dir,
                                )
                            }
                            msg = "转存成功，可到下载页查看"
                        } catch (e: Exception) {
                            msg = "失败：${e.message}"
                        }
                        busy = false
                    }
                },
                enabled = !busy && (msg == null || !msg!!.startsWith("转存成功")),
            ) { Text(if (busy) "转存中…" else "转存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(if (msg?.startsWith("转存成功") == true) "完成" else "取消")
            }
        },
    )
}

/** 站点添加/编辑对话框（设置页也复用） */
@Composable
fun SiteEditDialog(initial: Site?, onDismiss: () -> Unit, vm: SitesViewModel) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加站点" else "编辑站点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("地址 https://xxx.org") }, singleLine = true)
                Text(
                    "登录在 PT 页的网页里进行，账号由站点 Cookie 记住。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    val s = (initial ?: Site()).copy(
                        name = name.trim().ifBlank { url.trim() },
                        url = url.trim().trimEnd('/'),
                    )
                    vm.upsert(s)
                    onDismiss()
                },
                enabled = url.trim().startsWith("http"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
