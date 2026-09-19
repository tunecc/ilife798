package com.github.ilife798.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

// 更新检查用独立 HttpClient（Task 6 补齐与 Android 同等的超时语义）。
actual fun createUpdateHttpClient(): HttpClient = HttpClient(Darwin)

// iOS 端不支持 APK 安装/应用内更新；系列接口按设计提供安全空实现。
actual fun currentAbis(): List<String> = emptyList()

actual fun canInstallPackages(): Boolean = false

actual fun openInstallPermissionSettings() {}

actual fun installApk(filePath: String): Boolean = false

actual suspend fun downloadApkToFile(
    url: String,
    onProgress: (Float) -> Unit,
): String {
    onProgress(0f)
    return ""
}

actual suspend fun requestNotificationPermission() {}

actual fun showUpdateProgressNotification(progress: Float) {}

actual fun cancelUpdateProgressNotification() {}

actual fun deleteDownloadedApk() {}

actual fun downloadedApkPathIfValid(
    sha256: String?,
    size: Long,
): String? = null
