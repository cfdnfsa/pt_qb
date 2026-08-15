package com.ptqb.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("ptqb")

@Serializable
data class Server(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val host: String = "",          // http://ip:port
    val username: String = "",
    val password: String = "",
    val dirs: List<String> = emptyList(),   // 收藏的下载目录（Transmission 添加种子时会自动创建不存在的目录）
)

@Serializable
data class ServerStore(
    val servers: List<Server> = emptyList(),
    val currentId: Long = 0,
    val pollIntervalSec: Int = 10,
)

object Servers {
    private val KEY = stringPreferencesKey("servers")
    private val json = Json { ignoreUnknownKeys = true }

    fun flow(context: Context): Flow<ServerStore> =
        context.dataStore.data.map { p ->
            p[KEY]?.let { runCatching { json.decodeFromString<ServerStore>(it) }.getOrNull() } ?: ServerStore()
        }

    suspend fun save(context: Context, store: ServerStore) {
        context.dataStore.edit { it[KEY] = json.encodeToString(store) }
    }
}

/** 取当前服务器的 RPC 客户端；未选择服务器时抛异常 */
suspend fun currentClient(context: Context): TrClient {
    val store = Servers.flow(context).first()
    val s = store.servers.firstOrNull { it.id == store.currentId }
        ?: throw TrException("未选择服务器")
    return TrClient(s.host, s.username, s.password)
}
