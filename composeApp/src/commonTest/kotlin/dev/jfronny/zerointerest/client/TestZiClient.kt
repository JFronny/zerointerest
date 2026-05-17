package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.util.Timed
import io.kotest.engine.flatMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TestZiClient(
    val server: TestZiServer,
    override val userId: UserId = UserId("@testuser${server.nextUserId++}:example.com"),
    var nextTransactionId: Int = 0,
    override var offline: Boolean = false,
) : ZiClient {
    val localEventHistory = linkedSetOf<Pair<RoomId, ClientEvent.RoomEvent<*>>>()
    val localStateEvents = mutableMapOf<Pair<RoomId, String>, ClientEvent.RoomEvent.StateEvent<*>>()
    val stateFlows = mutableMapOf<RoomId, MutableStateFlow<ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>?>>()

    // Simulating delay and queue
    val queuedMessages = mutableMapOf<String, MessageEventContent>()
    val queuedStateEvents = mutableMapOf<String, Pair<StateEventContent, String>>()
    val sentMessages = mutableMapOf<String, EventId>()

    private fun nextTransactionId() = "tx_${nextTransactionId++}"

    fun registerIfAbsent(roomId: RoomId, extra: MemberEventContent = MemberEventContent(membership = Membership.JOIN)) =
        server.registerIfAbsent(roomId, userId, extra)

    fun sync() {
        // Pull events from server
        server.eventHistory.forEach { localEventHistory.add(it) }
        server.stateEvents.forEach { (key, value) ->
            localStateEvents[key] = value
            if (value.content is ZeroInterestSummaryEvent) {
                stateFlows.getOrPut(key.first) { MutableStateFlow(null) }.value =
                    value as ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>
            }
        }

        // Push queued to server
        queuedMessages.forEach { (txId, content) ->
            val eventId = server.nextEventId()
            sentMessages[txId] = eventId
            val event = ClientEvent.RoomEvent.MessageEvent(
                content = content,
                id = eventId,
                sender = userId,
                roomId = localEventHistory.lastOrNull()?.first ?: RoomId("!test:example.com"),
                originTimestamp = server.nextTimestamp()
            )
            server.eventHistory.add(event.roomId to event)
        }
        queuedMessages.clear()

        queuedStateEvents.forEach { (txId, pair) ->
            val (content, stateKey) = pair
            val eventId = server.nextEventId()
            sentMessages[txId] = eventId
            val roomId = localEventHistory.lastOrNull()?.first ?: RoomId("!test:example.com")
            val event = ClientEvent.RoomEvent.StateEvent(
                content = content,
                id = eventId,
                sender = userId,
                roomId = roomId,
                originTimestamp = server.nextTimestamp(),
                stateKey = stateKey
            )
            server.eventHistory.add(roomId to event)
            server.stateEvents[roomId to stateKey] = event
        }
        queuedStateEvents.clear()

        // Pull again just in case
        server.eventHistory.forEach { localEventHistory.add(it) }
        server.stateEvents.forEach { (key, value) ->
            localStateEvents[key] = value
            if (value.content is ZeroInterestSummaryEvent) {
                stateFlows.getOrPut(key.first) { MutableStateFlow(null) }.value =
                    value as ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>
            }
        }
    }

    override suspend fun getTransactionEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestTransactionEvent>>? {
        val event = localEventHistory.find { it.first == roomId && it.second.id == eventId }?.second
        if (event == null || event.content !is ZeroInterestTransactionEvent) return null
        return Result.success(
            Timed(
                event.originTimestamp,
                event.content as ZeroInterestTransactionEvent
            )
        )
    }

    override suspend fun getSummaryEventWithTimeout(roomId: RoomId, eventId: EventId): Result<Timed<ZeroInterestSummaryEvent>>? {
        val event = localEventHistory.find { it.first == roomId && it.second.id == eventId }?.second
        if (event == null || event.content !is ZeroInterestSummaryEvent) return null
        return Result.success(
            Timed(
                event.originTimestamp,
                event.content as ZeroInterestSummaryEvent
            )
        )
    }

    override fun getUsers(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> = server.getUsers(roomId)

    override suspend fun sendStateEvent(roomId: RoomId, event: StateEventContent, stateKey: String): Result<EventId> {
        // send immediately
        val eventId = server.nextEventId()
        val clientEvent = ClientEvent.RoomEvent.StateEvent(
            content = event,
            id = eventId,
            sender = userId,
            roomId = roomId,
            originTimestamp = server.nextTimestamp(),
            stateKey = stateKey
        )
        server.eventHistory.add(roomId to clientEvent)
        server.stateEvents[roomId to stateKey] = clientEvent
        return Result.success(eventId)
    }

    override suspend fun scheduleMessageEvent(roomId: RoomId, event: MessageEventContent): Result<String> {
        val txId = nextTransactionId()
        queuedMessages[txId] = event
        return Result.success(txId)
    }

    override suspend fun awaitScheduledMessageEvent(roomId: RoomId, transactionId: String): Result<EventId> {
        if (queuedMessages.containsKey(transactionId)) {
            sync()
        }
        val eventId = sentMessages[transactionId]
        if (eventId != null) {
            return Result.success(eventId)
        }
        return Result.failure(Exception("Message not yet synced"))
    }

    override suspend fun reactToEvent(roomId: RoomId, eventId: EventId, key: String): Result<Unit> {
        val content = ReactionEventContent(RelatesTo.Annotation(eventId, key))
        return scheduleMessageEvent(roomId, content).flatMap {
            awaitScheduledMessageEvent(roomId, it)
        }.map { reactionId ->
            val map = server.reactions.getOrPut(roomId to eventId) { mutableMapOf() }
            val set = map.getOrPut(key) { mutableSetOf() }
            set.add(TimelineEvent(
                event = ClientEvent.RoomEvent.MessageEvent(
                    content = content,
                    id = reactionId,
                    sender = userId,
                    roomId = roomId,
                    originTimestamp = server.nextTimestamp()
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            ))
            Unit
        }
    }

    override suspend fun redactEvent(roomId: RoomId, eventId: EventId, reason: String?): Result<Unit> {
        // Remove reaction
        server.reactions.values.forEach { keyMap ->
            keyMap.values.forEach { set ->
                set.removeAll { it.eventId == eventId }
            }
        }
        return Result.success(Unit)
    }

    override fun getSummaryStateFlow(roomId: RoomId): Flow<ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>?> {
        return stateFlows.getOrPut(roomId) { MutableStateFlow(null) }
    }

    override fun getTimelineEventReactionAggregation(roomId: RoomId, eventId: EventId): Flow<Map<String, Set<TimelineEvent>>> {
        val sf = MutableStateFlow<Map<String, Set<TimelineEvent>>>(emptyMap())
        sf.value = server.reactions[roomId to eventId] ?: emptyMap()
        return sf
    }

    override suspend fun hasPreviousSummary(roomId: RoomId, messageId: EventId): Boolean {
        val idx = localEventHistory.indexOfFirst { it.second.id == messageId }
        if (idx == -1) return false
        val prevs = localEventHistory.toList().subList(0, idx)
        return prevs.any { it.first == roomId && it.second.content is ZeroInterestSummaryEvent }
    }
}