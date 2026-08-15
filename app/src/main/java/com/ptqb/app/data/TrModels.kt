package com.ptqb.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class TrException(msg: String) : Exception(msg)

// RPC 外层 {"result":"success","arguments":{...}}
@Serializable
data class RpcResp(
    val result: String? = null,
    val arguments: JsonObject? = null,
)

@Serializable
data class TorrentsResp(val torrents: List<TrTorrent> = emptyList())

// status 枚举: 0停止 1等待校验 2校验中 3等待下载 4下载中 5等待做种 6做种中
@Serializable
data class TrTorrent(
    val id: Long = 0,
    @SerialName("hashString") val hash: String = "",
    val name: String = "",
    val status: Int = 0,
    val error: Int = 0,
    @SerialName("errorString") val errorString: String = "",
    @SerialName("totalSize") val totalSize: Long = 0,
    @SerialName("sizeWhenDone") val sizeWhenDone: Long = 0,
    @SerialName("leftUntilDone") val leftUntilDone: Long = 0,
    @SerialName("percentDone") val percentDone: Float = 0f,
    @SerialName("rateDownload") val rateDownload: Long = 0,
    @SerialName("rateUpload") val rateUpload: Long = 0,
    val eta: Long = -1,
    @SerialName("downloadDir") val downloadDir: String = "",
    @SerialName("addedDate") val addedDate: Long = 0,
    @SerialName("uploadRatio") val uploadRatio: Float = 0f,
    val labels: List<String> = emptyList(),
    @SerialName("uploadedEver") val uploadedEver: Long = 0,
    @SerialName("downloadedEver") val downloadedEver: Long = 0,
    @SerialName("secondsSeeding") val secondsSeeding: Long = 0,
    @SerialName("secondsDownloading") val secondsDownloading: Long = 0,
    @SerialName("activityDate") val activityDate: Long = 0,
    @SerialName("magnetLink") val magnetLink: String = "",
    @SerialName("trackerStats") val trackerStats: List<TrTrackerStat> = emptyList(),
    val files: List<TrFile> = emptyList(),
    val peers: List<TrPeer> = emptyList(),
    @SerialName("peersGettingToUs") val peersGetting: Int = 0,
    @SerialName("peersSendingToUs") val peersSending: Int = 0,
)

@Serializable
data class TrPeer(
    val address: String = "",
    @SerialName("clientName") val client: String = "",
    val progress: Float = 0f,
    @SerialName("isDownloadingFrom") val downloadingFrom: Boolean = false,
    @SerialName("isUploadingTo") val uploadingTo: Boolean = false,
)

@Serializable
data class TrTrackerStat(
    val announce: String = "",
    @SerialName("seederCount") val seeders: Int = 0,
    @SerialName("leecherCount") val leechers: Int = 0,
    val status: Int = 0,
)

@Serializable
data class TrFile(
    val name: String = "",
    val length: Long = 0,
    @SerialName("bytesCompleted") val bytesCompleted: Long = 0,
)

@Serializable
data class TrStats(
    @SerialName("downloadSpeed") val downloadSpeed: Long = 0,
    @SerialName("uploadSpeed") val uploadSpeed: Long = 0,
    @SerialName("torrentCount") val torrentCount: Int = 0,
    @SerialName("activeTorrentCount") val activeTorrentCount: Int = 0,
    @SerialName("pausedTorrentCount") val pausedTorrentCount: Int = 0,
)

@Serializable
data class TrSession(
    val version: String = "",
    @SerialName("rpc-version") val rpcVersion: Int = 0,
    @SerialName("download-dir") val downloadDir: String = "",
    @SerialName("download-dir-free-space") val freeSpace: Long = 0,
)
