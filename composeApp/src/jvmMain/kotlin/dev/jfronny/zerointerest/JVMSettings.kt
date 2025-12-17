package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.service.Settings
import net.folivo.trixnity.core.model.RoomId
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class JVMSettings(platform: AbstractPlatform) : Settings {
    private val path = platform.stateDir.toNioPath().resolve("settings.properties")
    private val properties = Properties().apply {
        if (path.exists()) {
            path.inputStream().use { load(it) }
        }
    }

    override suspend fun rememberRoom(roomId: RoomId) {
        properties.setProperty(Settings.rememberedRoomKey, roomId.full)
        save()
    }

    override suspend fun rememberedRoom(): RoomId? {
        return properties.getProperty(Settings.rememberedRoomKey)?.let { RoomId(it) }
    }

    override suspend fun clearRememberedRoom() {
        properties.remove(Settings.rememberedRoomKey)
        save()
    }

    private fun save() {
        path.outputStream().use { properties.store(it, null) }
    }
}
