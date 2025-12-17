package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomSummaryTrustDatabase(val db: ZeroInterestRoomDatabase) : SummaryTrustDatabase {
    override suspend fun markTrusted(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(
            SummaryTrustEntity(room.full, event.full, SummaryTrustDatabase.TrustState.TRUSTED)
        )
    }

    override suspend fun markRejected(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(
            SummaryTrustEntity(room.full, event.full, SummaryTrustDatabase.TrustState.REJECTED)
        )
    }

    override suspend fun checkTrust(room: RoomId, event: EventId): SummaryTrustDatabase.TrustState {
        return db.summaryTrustDao().getTrustState(room.full, event.full)
            ?: SummaryTrustDatabase.TrustState.UNTRUSTED
    }
}
