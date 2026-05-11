package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent

class TestZiServer(
    var nextUserId: Int = 0,
    var nextEventId: Int = 0,
    var nextTimestamp: Long = 0L,
) {
    val eventHistory = linkedSetOf<Pair<RoomId, ClientEvent.RoomEvent<*>>>()
    val stateEvents = mutableMapOf<Pair<RoomId, String>, ClientEvent.RoomEvent.StateEvent<*>>()
    val reactions = mutableMapOf<Pair<RoomId, EventId>, MutableMap<String, MutableSet<TimelineEvent>>>()

    fun nextEventId() = EventId($$"$$${nextEventId++}")
    fun nextTimestamp() = nextTimestamp++

    fun getEvents(roomId: RoomId): List<ClientEvent.RoomEvent<*>> {
        return eventHistory.filter { it.first == roomId }.map { it.second }
    }
}

