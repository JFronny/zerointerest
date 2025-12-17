package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.service.Settings
import net.folivo.trixnity.core.model.RoomId
import platform.Foundation.NSUserDefaults

class AppleSettings : Settings {
    val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun rememberRoom(roomId: RoomId) {
        defaults.setObject(roomId.full, forKey = Settings.rememberedRoomKey)
    }

    override suspend fun rememberedRoom(): RoomId? {
        return defaults.stringForKey(Settings.rememberedRoomKey)?.let { RoomId(it) }
    }

    override suspend fun clearRememberedRoom() {
        defaults.removeObjectForKey(Settings.rememberedRoomKey)
    }
}
