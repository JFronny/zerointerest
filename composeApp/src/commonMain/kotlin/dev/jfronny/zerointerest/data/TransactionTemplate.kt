package dev.jfronny.zerointerest.data

import de.connect2x.trixnity.core.model.UserId
import kotlinx.serialization.Serializable

@Serializable
data class TransactionTemplate(
    val id: String,
    val description: String,
    val sender: UserId,
    val receivers: Map<UserId, Long>
)
