package dev.jfronny.zerointerest

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
sealed class Destination {
    @Serializable object Login : Destination()
    @Serializable object PickRoom : Destination()
    @Serializable data class Room(val roomId: RoomId) : Destination()
}