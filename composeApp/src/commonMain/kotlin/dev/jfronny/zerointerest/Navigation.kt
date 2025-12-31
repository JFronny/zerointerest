package dev.jfronny.zerointerest

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@Serializable
sealed class Destination {
    @Serializable object LoadingScreen : Destination()
    @Serializable object SelectHomeserver : Destination()
    @Serializable data class SelectLoginMethod(val homeserver: String) : Destination()
    @Serializable object PickRoom : Destination()
    @Serializable data class Room(val roomId: RoomId) : Destination() {
        @Serializable sealed class RoomDestination {
            @Serializable object Balance : RoomDestination()
            @Serializable object Transactions : RoomDestination()
        }
    }
    @Serializable data class CreateTransaction(val roomId: RoomId) : Destination()
    @Serializable data class TransactionDetails(val roomId: RoomId, val transactionId: EventId) : Destination()
}
