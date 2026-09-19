package com.github.ilife798

// iOS 无运行通知/前台服务；commonMain 注释已声明「其它平台为空实现」。
actual object RunNotifications {
    actual fun updateDevice(
        deviceId: String,
        deviceName: String,
        running: Boolean,
    ) {}

    actual fun updateTask(gained: Int) {}

    actual fun removeTask() {}
}
