package com.ptqb.app.ui

import android.content.Context
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.core.view.doOnAttach
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ptqb.app.data.Site
import com.ptqb.app.data.SiteStore
import com.ptqb.app.data.Sites
import com.ptqb.app.data.Servers
import com.ptqb.app.data.TrException
import com.ptqb.app.data.currentClient
import com.ptqb.app.data.isTorrentLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Firefox(Gecko) 引擎单例：真正的浏览器内核，TLS 指纹不会被站点 WAF 拦截 */
object GeckoHolder {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
        }
}

suspend fun <T : Any> GeckoResult<T>.await(): T = suspendCancellableCoroutine { cont ->
    then<Unit>(
        { value -> cont.resume(value as T); null },
        { err -> cont.resumeWithException(err); null },
    )
}

/** 用 Firefox 引擎的网络栈下载种子文件：Cookie/UA/TLS 指纹与浏览器会话完全一致 */
private suspend fun fetchTorrentBytes(context: Context, url: String): ByteArray {
    val req = WebRequest.Builder(url)
        .header("Referer", url)
        .build()
    val resp = GeckoWebExecutor(GeckoHolder.get(context)).fetch(req).await()
    if (resp.statusCode !in 200..299) throw TrException("下载种子文件失败 HTTP ${resp.statusCode}")
    return resp.body?.use { it.readBytes() } ?: throw TrException("种子文件为空")
}

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

/** PT 页：Firefox 引擎站点浏览 + 下载链接拦截转存 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PtTab(active: Boolean = true) {
    val vm: SitesViewModel = viewModel()
    val store by vm.state.collectAsStateWithLifecycle()

    var siteSheet by remember { mutableStateOf(false) }
    var showSiteAdd by remember { mutableStateOf(false) }
    var editSite by remember { mutableStateOf<Site?>(null) }
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<GeckoSession?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableStateOf<Int?>(null) }   // null=空闲

    // 返回键：网页有历史则网页后退，否则走系统默认
    BackHandler(enabled = active && canGoBack) { session?.goBack() }

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
            // 站点栏：网页后退 | 站点切换 | 刷新
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { session?.goBack() }, enabled = canGoBack) { Text("←") }
                TextButton(onClick = { siteSheet = true }) {
                    Text(
                        "${current.name.ifBlank { current.url }} ▾",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Firefox",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { session?.reload() }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }
            // 加载进度条
            if (pageProgress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (pageProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                key(current.id) {
                    GeckoWebView(
                        startUrl = current.url,
                        onDownloadLink = { pendingLink = it },
                        onSessionCreated = {
                            session = it
                            canGoBack = false
                        },
                        onHistoryChange = { canGoBack = it },
                        onLoadingChange = { pageProgress = it },
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

/** Firefox(Gecko) 引擎的站内浏览：手机模式 + 下拉刷新 + 拦截下载链接转存 */
@Composable
private fun GeckoWebView(
    startUrl: String,
    onDownloadLink: (String) -> Unit,
    onSessionCreated: (GeckoSession) -> Unit,
    onHistoryChange: (Boolean) -> Unit,
    onLoadingChange: (Int?) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val gecko = GeckoView(ctx)
            val session = GeckoSession(
                GeckoSessionSettings.Builder()
                    .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .build()
            )
            session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(
                    s: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest,
                ): GeckoResult<AllowOrDeny>? {
                    return if (isTorrentLink(request.uri)) {
                        onDownloadLink(request.uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    } else {
                        GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    }
                }

                override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                    onHistoryChange(canGoBack)
                }

                /** target=_blank 等新窗口链接：单窗口应用，改为当前页打开 */
                override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                    if (isTorrentLink(uri)) {
                        onDownloadLink(uri)
                    } else {
                        s.loadUri(uri)
                    }
                    return GeckoResult()
                }
            }
            session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(s: GeckoSession, url: String) {
                    onLoadingChange(0)
                }

                override fun onProgressChange(s: GeckoSession, progress: Int) {
                    onLoadingChange(progress)
                }

                override fun onPageStop(s: GeckoSession, success: Boolean) {
                    onLoadingChange(null)
                }
            }
            session.open(GeckoHolder.get(context))
            onSessionCreated(session)
            // 必须 attach 到窗口后才能 setSession，否则 Gecko 渲染无处投递（白屏）
            gecko.doOnAttach {
                (it as GeckoView).setSession(session)
                session.loadUri(startUrl)
            }
            gecko
        },
        onRelease = { it.session?.close() },
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
                    scope.launch {
                        try {
                            val client = currentClient(context)
                            if (link.startsWith("magnet:", true)) {
                                client.add(filename = link, downloadDir = dir)
                            } else {
                                // Firefox 引擎网络栈下载（Cookie/UA/指纹全一致）
                                val bytes = fetchTorrentBytes(context, link)
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
                    "登录在 PT 页的网页里进行，账号由浏览器内核记住。",
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
