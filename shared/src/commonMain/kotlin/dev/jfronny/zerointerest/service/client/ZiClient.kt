package dev.jfronny.zerointerest.service.client

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.util.Timed
import kotlinx.coroutines.flow.Flow

interface ZiClient {
    val userId: UserId
    val offline: Boolean
    
    suspend fun getTransactionEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestTransactionEvent>>?
    suspend fun getSummaryEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestSummaryEvent>>?

    fun getUsers(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>>
    
    suspend fun sendStateEvent(roomId: RoomId, event: StateEventContent, stateKey: String): Result<EventId>
    suspend fun scheduleMessageEvent(roomId: RoomId, event: MessageEventContent): Result<String>
    suspend fun awaitScheduledMessageEvent(roomId: RoomId, transactionId: String): Result<EventId>
    
    suspend fun reactToEvent(roomId: RoomId, eventId: EventId, key: String): Result<Unit>
    suspend fun redactEvent(roomId: RoomId, eventId: EventId, reason: String? = null): Result<Unit>
    
    fun getSummaryStateFlow(roomId: RoomId): Flow<ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>?>
    
    fun getTimelineEventReactionAggregation(roomId: RoomId, eventId: EventId): Flow<Map<String, Set<TimelineEvent>>>
    
    suspend fun hasPreviousSummary(roomId: RoomId, messageId: EventId): Boolean
}

interface ZiClientProvider {
    fun get(): ZiClient
}
