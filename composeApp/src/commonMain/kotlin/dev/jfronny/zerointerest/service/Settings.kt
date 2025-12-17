package dev.jfronny.zerointerest.service

import net.folivo.trixnity.core.model.RoomId

interface Settings {
    suspend fun rememberRoom(roomId: RoomId)
    suspend fun rememberedRoom(): RoomId?
    suspend fun clearRememberedRoom()

    companion object {
        const val rememberedRoomKey = "rememberedRoom"
    }
}
