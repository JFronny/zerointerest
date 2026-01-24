package dev.jfronny.zerointerest

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.serialization.Serializable

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
    @Serializable data class CreateTransaction(val roomId: RoomId, val templateId: String? = null) : Destination()
    @Serializable data class TransactionDetails(val roomId: RoomId, val transactionId: EventId) : Destination()
}
