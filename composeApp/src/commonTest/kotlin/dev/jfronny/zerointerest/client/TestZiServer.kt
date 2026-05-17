package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TestZiServer(
    var nextUserId: Int = 0,
    var nextEventId: Int = 0,
    var nextTimestamp: Long = 0L,
) {
    val eventHistory = linkedSetOf<Pair<RoomId, ClientEvent.RoomEvent<*>>>()
    val stateEvents = mutableMapOf<Pair<RoomId, String>, ClientEvent.RoomEvent.StateEvent<*>>()
    val reactions = mutableMapOf<Pair<RoomId, EventId>, MutableMap<String, MutableSet<TimelineEvent>>>()
    val users = mutableMapOf<RoomId, MutableStateFlow<Map<UserId, MutableStateFlow<RoomUser?>>>>()

    fun nextEventId() = EventId($$"$$${nextEventId++}")
    fun nextTimestamp() = nextTimestamp++

    fun getEvents(roomId: RoomId): List<ClientEvent.RoomEvent<*>> {
        return eventHistory.filter { it.first == roomId }.map { it.second }
    }

    fun registerIfAbsent(roomId: RoomId, userId: UserId, extra: MemberEventContent = MemberEventContent(membership = Membership.JOIN)) {
        users.getOrPut(roomId) { MutableStateFlow(mapOf()) }
            .update {
                val current = it[userId]
                if (current == null) {
                    val roomUser = RoomUser(roomId, userId, extra.displayName ?: userId.full, ClientEvent.StrippedStateEvent(
                        content = extra,
                        sender = userId,
                        stateKey = "m.room.member"
                    ))
                    it + (userId to MutableStateFlow(roomUser))
                } else it
            }
    }

    fun register(roomId: RoomId, userId: UserId, extra: MemberEventContent = MemberEventContent(membership = Membership.JOIN)) {
        users.getOrPut(roomId) { MutableStateFlow(mapOf()) }
            .update {
                val current = it[userId]
                val roomUser = RoomUser(roomId, userId, extra.displayName ?: userId.full, ClientEvent.StrippedStateEvent(
                    content = extra,
                    sender = userId,
                    stateKey = "m.room.member"
                ))
                if (current == null) {
                    it + (userId to MutableStateFlow(roomUser))
                } else {
                    current.value = roomUser
                    it
                }
            }
    }

    fun getUsers(roomId: RoomId): Flow<Map<UserId, Flow<RoomUser?>>> {
        return users.getOrPut(roomId) { MutableStateFlow(mapOf()) }
    }
}
