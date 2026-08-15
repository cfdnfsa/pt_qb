package com.ptqb.app.data

import android.content.Context
import android.webkit.WebSettings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class Site(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val url: String = "",       // 站点根地址 https://xxx.org
)

@Serializable
data class SiteStore(
    val sites: List<Site> = emptyList(),
    val lastSiteId: Long = 0,
)

object Sites {
    private val KEY = stringPreferencesKey("sites")
    private val json = Json { ignoreUnknownKeys = true }

    fun flow(context: Context): Flow<SiteStore> =
        context.dataStore.data.map { p ->
            p[KEY]?.let { runCatching { json.decodeFromString<SiteStore>(it) }.getOrNull() } ?: SiteStore()
        }

    suspend fun save(context: Context, store: SiteStore) {
        context.dataStore.edit { it[KEY] = json.encodeToString(store) }
    }
}

/** 标准手机浏览器 UA（去掉 WebView 的 "; wv" 标记，避免被站点 WAF 拦断连） */
fun mobileUa(context: Context): String =
    WebSettings.getDefaultUserAgent(context).replace("; wv", "")

/** 是否是种子下载链接（magnet / *.torrent / NexusPHP download.php） */
fun isTorrentLink(url: String): Boolean {
    if (url.startsWith("magnet:", true)) return true
    val path = url.substringBefore('?').lowercase()
    if (path.endsWith(".torrent")) return true
    return path.substringAfterLast('/') == "download.php"
}

/** 带 Cookie/Referer/UA 从站点下载 .torrent 文件内容 */
suspend fun downloadTorrentFile(url: String, cookie: String?, referer: String?, ua: String?): ByteArray =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            referer?.let { header("Referer", it) }
            ua?.let { header("User-Agent", it) }
        }.build()
        OkHttpClient().newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw TrException("下载种子文件失败 HTTP ${r.code}")
            r.body?.bytes() ?: throw TrException("种子文件为空")
        }
    }
