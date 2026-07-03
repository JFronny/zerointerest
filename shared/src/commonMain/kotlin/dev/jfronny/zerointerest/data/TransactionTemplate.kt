package dev.jfronny.zerointerest.data

import androidx.compose.runtime.Immutable
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.money.Money
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class TransactionTemplate(
    val id: String,
    val description: String,
    val sender: UserId,
    val receivers: Map<UserId, Money>,
)
