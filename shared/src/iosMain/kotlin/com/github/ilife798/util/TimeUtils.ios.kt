package com.github.ilife798.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// 骨架占位：Task 5（tasks 2.2）替换为 NSDateFormatter 完整实现。
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentTimeFormatted(pattern: String): String = ""

actual fun formatTimestamp(
    timestamp: Long,
    pattern: String,
): String = ""

actual fun getDayOfWeek(): Int = 1

actual fun getTodayStart(now: Long): Long = now
