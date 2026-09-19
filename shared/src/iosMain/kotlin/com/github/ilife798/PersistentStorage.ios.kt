package com.github.ilife798

// 骨架占位：Task 4（tasks 2.1）替换为 NSUserDefaults 真实现。
actual class PersistentStorage {
    private val memory = mutableMapOf<String, Any>()

    actual fun saveString(
        key: String,
        value: String,
    ) {
        memory[key] = value
    }

    actual fun getString(key: String): String? = memory[key] as? String

    actual fun saveBoolean(
        key: String,
        value: Boolean,
    ) {
        memory[key] = value
    }

    actual fun getBoolean(key: String): Boolean = memory[key] as? Boolean ?: false

    actual fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = memory[key] as? Boolean ?: defaultValue

    actual fun saveInt(
        key: String,
        value: Int,
    ) {
        memory[key] = value
    }

    actual fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = memory[key] as? Int ?: defaultValue
}
