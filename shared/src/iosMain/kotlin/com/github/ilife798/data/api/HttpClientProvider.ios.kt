package com.github.ilife798.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

// 骨架占位：Task 6（tasks 2.3）补齐与 Android 同等的 JSON/请求头配置。
actual fun createHttpClient(): HttpClient = HttpClient(Darwin)
