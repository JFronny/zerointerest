package dev.jfronny.zerointerest.util

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

data class Timed<T>(val ts: Long, val value: T)

// this may break summary resolution after long time frames
// but that probably won't happen in practice, right?
private val summaryEventCache: MutableMap<Pair<RoomId, EventId>, Timed<ZeroInterestSummaryEvent>> = ConcurrentMap()

fun cacheSummary(event: ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>) {
    summaryEventCache[event.roomId to event.id] = Timed(event.originTimestamp, event.content)
}

suspend fun MatrixClient.getTransactionEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestTransactionEvent>>? {
    return withTimeoutOrNull(6.seconds) {
        val event = room.getTimelineEvent(roomId, eventId) {
            fetchTimeout = 5.seconds
            allowReplaceContent = false
        }.filterNotNull().firstOrNull() ?: return@withTimeoutOrNull null
        val content = event.content ?: return@withTimeoutOrNull null
        content.map { content ->
            Timed(event.originTimestamp, content as ZeroInterestTransactionEvent)
        }
    }
}

suspend fun MatrixClient.getSummaryEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestSummaryEvent>>? {
    summaryEventCache[roomId to eventId]?.let { return Result.success(it) }
    return withTimeoutOrNull(6.seconds) {
        val event = try {
            api.room.getEvent(roomId, eventId)
                .getOrThrow() as ClientEvent.StateBaseEvent<ZeroInterestSummaryEvent>
        } catch (e: Exception) {
            return@withTimeoutOrNull Result.failure(e)
        }
        val timed = Timed(event.originTimestamp!!, event.content)
        summaryEventCache[roomId to eventId] = timed
        Result.success(timed)
    }
}
