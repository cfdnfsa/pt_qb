package com.ptqb.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Transmission RPC 客户端：单端点 POST /transmission/rpc。
 * Basic Auth + 409 session-id 自动重试（Interceptor 内处理）。
 */
class TrClient(
    baseUrl: String,
    username: String,
    password: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val rpcUrl = baseUrl.trim().trimEnd('/') + "/transmission/rpc"

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
            val resp = chain.proceed(req)
            if (resp.code == 409) {
                val sid = resp.header("X-Transmission-Session-Id").orEmpty()
                resp.close()
                chain.proceed(req.newBuilder().header("X-Transmission-Session-Id", sid).build())
            } else {
                resp
            }
        }
        .build()

    /** 发起 RPC 调用，成功返回 arguments，失败抛 TrException */
    private suspend fun raw(method: String, args: JsonObject? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("method", method)
                if (args != null) put("arguments", args)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            http.newCall(Request.Builder().url(rpcUrl).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw TrException(
                    when (r.code) {
                        401 -> "认证失败（检查用户名密码）"
                        else -> "HTTP ${r.code}"
                    }
                )
                val rpc = json.decodeFromString<RpcResp>(text)
                if (rpc.result != "success") throw TrException(rpc.result ?: "未知错误")
                rpc.arguments ?: JsonObject(emptyMap())
            }
        }

    private fun idsArg(ids: List<Long>): JsonObject = buildJsonObject {
        put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
    }

    suspend fun torrents(): List<TrTorrent> =
        json.decodeFromJsonElement<TorrentsResp>(raw("torrent-get", buildJsonObject {
            put("fields", JsonArray(LIST_FIELDS.map { JsonPrimitive(it) }))
        })).torrents

    suspend fun detail(hash: String): TrTorrent? =
        json.decodeFromJsonElement<TorrentsResp>(raw("torrent-get", buildJsonObject {
            put("fields", JsonArray(FULL_FIELDS.map { JsonPrimitive(it) }))
            put("ids", JsonArray(listOf(JsonPrimitive(hash))))
        })).torrents.firstOrNull()

    suspend fun stats(): TrStats = json.decodeFromJsonElement(raw("session-stats"))

    suspend fun session(): TrSession = json.decodeFromJsonElement(raw("session-get"))

    suspend fun start(ids: List<Long>) { raw("torrent-start", idsArg(ids)) }
    suspend fun startNow(ids: List<Long>) { raw("torrent-start-now", idsArg(ids)) }
    suspend fun stop(ids: List<Long>) { raw("torrent-stop", idsArg(ids)) }

    /** 重新校验 */
    suspend fun verify(ids: List<Long>) { raw("torrent-verify", idsArg(ids)) }

    /** 汇报（强制 announce） */
    suspend fun reannounce(ids: List<Long>) { raw("torrent-reannounce", idsArg(ids)) }

    /** 批量改 Tracker：remove/add 可组合（替换 = remove 旧 + add 新） */
    suspend fun setTracker(ids: List<Long>, remove: List<String> = emptyList(), add: List<String> = emptyList()) {
        raw("torrent-set", buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            if (remove.isNotEmpty()) put("trackerRemove", JsonArray(remove.map { JsonPrimitive(it) }))
            if (add.isNotEmpty()) put("trackerAdd", JsonArray(add.map { JsonPrimitive(it) }))
        })
    }

    /** 变更存储目录（move=true 移动数据） */
    suspend fun setLocation(ids: List<Long>, location: String, move: Boolean) {
        raw("torrent-set-location", buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            put("location", location)
            put("move", move)
        })
    }

    /** 设置标签（Transmission 4.0+） */
    suspend fun setLabels(ids: List<Long>, labels: List<String>) {
        raw("torrent-set", buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            put("labels", JsonArray(labels.map { JsonPrimitive(it) }))
        })
    }

    suspend fun remove(ids: List<Long>, deleteData: Boolean) {
        raw("torrent-remove", buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            put("delete-local-data", deleteData)
        })
    }

    /** 添加种子：filename=磁力/URL，metainfo=base64 的 .torrent 内容 */
    suspend fun add(filename: String? = null, metainfo: String? = null, downloadDir: String? = null): JsonObject =
        raw("torrent-add", buildJsonObject {
            filename?.let { put("filename", it) }
            metainfo?.let { put("metainfo", it) }
            downloadDir?.takeIf { it.isNotBlank() }?.let { put("download-dir", it) }
        })

    companion object {
        private val LIST_FIELDS = listOf(
            "id", "hashString", "name", "status", "error", "errorString",
            "totalSize", "sizeWhenDone", "leftUntilDone", "percentDone",
            "rateDownload", "rateUpload", "eta", "downloadDir", "addedDate",
            "uploadRatio", "isFinished", "labels", "uploadedEver", "secondsSeeding",
            "trackerStats",
        )
        private val FULL_FIELDS = LIST_FIELDS + listOf(
            "magnetLink", "files", "peers", "peersGettingToUs", "peersSendingToUs",
            "downloadedEver", "secondsDownloading", "activityDate",
        )
    }
}
