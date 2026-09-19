package com.github.ilife798.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

// iOS 时间工具：NSDateFormatter 默认使用设备本地时区与本地化设置，
// 与 Android SimpleDateFormat(Locale.getDefault()) 行为对齐。
private fun newFormatter(pattern: String): NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = pattern
    }

// NSDate 仅提供 referenceDate（2001-01-01T00:00:00Z）构造；换算 1970 纪元差秒数。
private const val REFERENCE_DATE_EPOCH_OFFSET = 9_783_072_000.0

private fun epochMsToDate(epochMs: Long): NSDate = NSDate(timeIntervalSinceReferenceDate = epochMs / 1000.0 - REFERENCE_DATE_EPOCH_OFFSET)

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentTimeFormatted(pattern: String): String = newFormatter(pattern).stringFromDate(NSDate())

actual fun formatTimestamp(
    timestamp: Long,
    pattern: String,
): String = newFormatter(pattern).stringFromDate(epochMsToDate(timestamp))

// Android 端语义：Calendar.DAY_OF_WEEK(周日=1) 转 ISO 周几（周一=1..周日=7）；Unicode "u" 与该语义一致。
actual fun getDayOfWeek(): Int = newFormatter("u").stringFromDate(NSDate()).toIntOrNull() ?: 1

actual fun getTodayStart(now: Long): Long {
    val day = newFormatter("yyyy-MM-dd").stringFromDate(epochMsToDate(now))
    val midnight = newFormatter("yyyy-MM-dd HH:mm:ss").dateFromString("$day 00:00:00") ?: return now
    return (midnight.timeIntervalSince1970 * 1000).toLong()
}
