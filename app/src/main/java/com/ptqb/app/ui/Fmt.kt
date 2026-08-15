package com.ptqb.app.ui

import androidx.compose.ui.graphics.Color
import com.ptqb.app.data.TrTorrent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return if (gb < 1024) "%.2f GB".format(gb) else "%.2f TB".format(gb / 1024.0)
}

fun fmtSpeed(bps: Long): String = fmtSize(bps) + "/s"

fun fmtEta(sec: Long): String = when {
    sec < 0 -> "—"
    sec >= 86400 -> "${sec / 86400}天${sec % 86400 / 3600}时"
    sec >= 3600 -> "${sec / 3600}时${sec % 3600 / 60}分"
    sec >= 60 -> "${sec / 60}分"
    else -> "${sec}秒"
}

/** 累计时长（做种/下载时长） */
fun fmtDuration(sec: Long): String = when {
    sec <= 0 -> "—"
    sec >= 86400 -> "${sec / 86400}天${sec % 86400 / 3600}时"
    sec >= 3600 -> "${sec / 3600}时${sec % 3600 / 60}分"
    else -> "${sec / 60}分"
}

fun fmtTime(epochSec: Long): String =
    if (epochSec <= 0) "—"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(epochSec * 1000))

fun statusName(t: TrTorrent): String = if (t.error != 0) "错误" else when (t.status) {
    0 -> "已停止"
    1 -> "等待校验"
    2 -> "校验中"
    3 -> "等待下载"
    4 -> "下载中"
    5 -> "等待做种"
    6 -> "做种中"
    else -> "未知"
}

/** 状态功能色：做种=绿 下载=主题黑 错误=红 停止=灰（黑白主题下的信号色） */
fun statusColor(t: TrTorrent): Color = when {
    t.error != 0 -> Color(0xFFCC2200)
    t.status in 5..6 -> Color(0xFF1E8E1E)
    t.status == 4 -> Color(0xFF0066CC)
    else -> Color(0xFF888888)
}

/** 主 tracker 的做种/下载数 */
fun TrTorrent.seeders(): Int = trackerStats.maxOfOrNull { it.seeders } ?: 0
fun TrTorrent.leechers(): Int = trackerStats.maxOfOrNull { it.leechers } ?: 0

/** 状态筛选（对应顶部 chips） */
enum class TStatus(val label: String) {
    ALL("全部"), DOWNLOADING("下载中"), SEEDING("做种中"), STOPPED("已停止"), CHECKING("校验中"), ERROR("错误");

    fun matches(t: TrTorrent): Boolean = when (this) {
        ALL -> true
        DOWNLOADING -> t.status in 3..4
        SEEDING -> t.status in 5..6
        STOPPED -> t.status == 0
        CHECKING -> t.status in 1..2
        ERROR -> t.error != 0
    }
}

enum class Sort(val label: String) {
    ADDED("添加时间"), PROGRESS("进度"), SIZE("大小"), SPEED("速度"),
    NAME("名称"), RATIO("分享率"), SEED_TIME("做种时长"), UPLOADED("上传量"),
}
