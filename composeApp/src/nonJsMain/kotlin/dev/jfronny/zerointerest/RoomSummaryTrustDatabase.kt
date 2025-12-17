package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import dev.jfronny.zerointerest.service.SummaryTrustDatabase.TrustState
import dev.jfronny.zerointerest.service.SummaryTrustDatabase.TrustState.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomSummaryTrustDatabase(val db: ZeroInterestRoomDatabase) : SummaryTrustDatabase {
    override suspend fun markTrusted(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, TRUSTED))
    }

    override suspend fun markRejected(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, REJECTED))
    }

    override suspend fun checkTrust(room: RoomId, event: EventId): TrustState {
        return db.summaryTrustDao().getTrustState(room.full, event.full) ?: UNTRUSTED
    }

    override suspend fun getHeads(room: RoomId): Set<EventId> {
        return db.summaryHeadDao().getHeads(room.full).map { EventId(it) }.toSet()
    }

    override suspend fun setHeads(room: RoomId, heads: Set<EventId>) {
        db.summaryHeadDao().clear(room.full)
        heads.forEach {
            db.summaryHeadDao().insert(SummaryHeadEntity(room.full, it.full))
        }
    }

    override suspend fun addHead(room: RoomId, head: EventId) {
        db.summaryHeadDao().insert(SummaryHeadEntity(room.full, head.full))
    }

    override suspend fun removeHeads(room: RoomId, heads: Set<EventId>) {
        heads.forEach {
            db.summaryHeadDao().removeHead(room.full, it.full)
        }
    }
}
