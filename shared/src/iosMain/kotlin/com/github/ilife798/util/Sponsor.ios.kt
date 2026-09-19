package com.github.ilife798.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// 打开赞助页：UIApplication.open；URL 为 commonMain 常量 SPONSOR_URL。
actual fun openSponsorPage(): Boolean {
    val url = NSURL(string = SPONSOR_URL) ?: return false
    return runCatching { UIApplication.sharedApplication.openURL(url) }.getOrDefault(false)
}
