package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import dev.jfronny.zerointerest.service.client.ZiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
fun ZiClient.getActive(roomId: RoomId, trustService: SummaryTrustService): Flow<Map<UserId, RoomUser?>> {
    return getUsers(roomId).combine(trustService.getSummary(roomId)) { users, summary ->
        val userFlows = users.map { (id, userFlow) ->
            userFlow.map { user ->
                val isActive = if (user == null || summary == null) true
                else if (user.event.content.membership == Membership.JOIN) true
                else if (summary !is SummaryTrustService.Summary.Trusted) true
                else (summary.event.balances[id]?.amount ?: 0L) != 0L
                
                if (isActive) id to user else null
            }
        }
        
        if (userFlows.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(userFlows) { userPairs ->
                userPairs.filterNotNull().toMap()
            }
        }
    }.flatMapLatest { it }
}
