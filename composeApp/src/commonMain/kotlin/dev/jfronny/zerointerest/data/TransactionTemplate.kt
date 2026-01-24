package dev.jfronny.zerointerest.data

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.UserId

@Serializable
data class TransactionTemplate(
    val id: String,
    val description: String,
    val sender: UserId,
    val receivers: Map<UserId, Long>
)
