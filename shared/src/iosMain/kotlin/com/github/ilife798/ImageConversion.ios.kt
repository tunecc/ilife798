package com.github.ilife798

import androidx.compose.ui.graphics.ImageBitmap

// 骨架占位：Task 5（tasks 2.2）替换为 Skia 解码真实现。
actual fun ByteArray.toImageBitmap(): ImageBitmap = ImageBitmap(1, 1)
