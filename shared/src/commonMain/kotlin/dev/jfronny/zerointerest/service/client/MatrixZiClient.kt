package dev.jfronny.zerointerest.service.client

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.room.getTimelineEventReactionAggregation
import de.connect2x.trixnity.client.room.message.react
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.util.Timed
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.any
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class MatrixZiClient(
    val client: MatrixClient,
) : ZiClient {
    override val userId: UserId get() = client.userId
    override val offline: Boolean get() = client.syncState.value != SyncState.RUNNING

    override suspend fun getTransactionEventWithTimeout(
        roomId: RoomId,
        eventId: EventId
    ): Result<Timed<ZeroInterestTransactionEvent>>? {
        return withTimeoutOrNull(11.seconds) {
            client.room.getTimelineEvent(roomId, eventId) {
                fetchTimeout = 10.seconds
                allowReplaceContent = false
            }.filterNotNull().mapNotNull { event ->
                event.content?.map {
                    val content = it as? ZeroInterestTransactionEvent ?: run {
                        log.warn { "Event content is not a transaction event for $eventId: $it" }
                        return@mapNotNull null
                    }
                    Timed(event.originTimestamp, content)
                }
            }.firstOrNull()
        }
    }

    private val summaryEventCache: MutableMap<Pair<RoomId, EventId>, Timed<ZeroInterestSummaryEvent>> = ConcurrentMap()

    fun cacheSummary(event: ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>) {
        summaryEventCache[event.roomId to event.id] = Timed(event.originTimestamp, event.content)
    }

    override suspend fun getSummaryEventWithTimeout(
        roomId: RoomId,
        eventId: EventId
    ): Result<Timed<ZeroInterestSummaryEvent>>? {
        summaryEventCache[roomId to eventId]?.let { return Result.success(it) }
        return withTimeoutOrNull(6.seconds) {
            val event = try {
                client.api.room.getEvent(roomId, eventId)
                    .getOrThrow() as ClientEvent.StateBaseEvent<*>
            } catch (e: Exception) {
                return@withTimeoutOrNull Result.failure(e)
            }
            val content = event.content as? ZeroInterestSummaryEvent ?: return@withTimeoutOrNull Result.failure(IllegalStateException("Event content is not a summary event"))
            val timed = Timed(event.originTimestamp!!, content)
            summaryEventCache[roomId to eventId] = timed
            Result.success(timed)
        }
    }

    override fun getUsers(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> = client.user.getAll(roomId)

    override suspend fun sendStateEvent(
        roomId: RoomId,
        event: StateEventContent,
        stateKey: String
    ): Result<EventId> {
        return try {
            client.api.room.sendStateEvent(roomId, event, stateKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun scheduleMessageEvent(roomId: RoomId, event: MessageEventContent): Result<String> {
        return try {
            val txId = client.room.sendMessage(roomId) {
                content(event)
            }
            Result.success(txId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun awaitScheduledMessageEvent(roomId: RoomId, transactionId: String): Result<EventId> {
        return try {
            val outbox = client.room.getOutbox(roomId, transactionId)
                .filterNotNull()
                .filter { it.eventId != null || it.sendError != null }
                .firstOrNull()
            if (outbox == null || outbox.sendError != null) {
                Result.failure(Exception(outbox?.sendError?.toString() ?: "Outbox entry disappeared for tx $transactionId"))
            } else {
                Result.success(outbox.eventId!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reactToEvent(roomId: RoomId, eventId: EventId, key: String): Result<Unit> {
        return try {
            client.room.sendMessage(roomId) {
                react(eventId, key)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun redactEvent(roomId: RoomId, eventId: EventId, reason: String?): Result<Unit> {
        return try {
            client.api.room.redactEvent(roomId, eventId, reason ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSummaryStateFlow(roomId: RoomId): Flow<ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>?> {
        return client.room.getState<ZeroInterestSummaryEvent>(roomId, ZeroInterestSummaryEvent.TYPE)
            .map { it as? ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent> }
            .map {
                if (it != null) cacheSummary(it)
                it
            }
    }

    override fun getTimelineEventReactionAggregation(
        roomId: RoomId,
        eventId: EventId
    ): Flow<Map<String, Set<TimelineEvent>>> {
        return client.room.getTimelineEventReactionAggregation(roomId, eventId).map { it.reactions }
    }

    override suspend fun hasPreviousSummary(roomId: RoomId, messageId: EventId): Boolean {
        return client.room.getTimelineEvents(roomId, messageId, direction = GetEvents.Direction.BACKWARDS) {
            maxSize = 1024
            fetchTimeout = 5.seconds
            allowReplaceContent = false
        }.any { timelineEventFlow ->
            val event = timelineEventFlow.first()
            event.eventId != messageId && event.content?.getOrNull() is ZeroInterestSummaryEvent
        }
    }
}
