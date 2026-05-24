package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.originTimestamp
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import dev.jfronny.zerointerest.data.money.toMoney
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.component.PreviewUserUI
import dev.jfronny.zerointerest.ui.component.UserUI
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionDetailsScreen(
    client: MatrixClient,
    roomId: RoomId,
    transactionId: EventId,
    onBack: () -> Unit
) {
    val event by remember(roomId, transactionId) {
        client.room.getTimelineEvent(roomId, transactionId) {
            fetchTimeout = 5.seconds
            allowReplaceContent = false
        }
    }.collectAsState(null)
    val trust = koinInject<SummaryTrustService>()
    val includedFlow = remember(roomId, transactionId) {
        flow {
            emit(trust.getSummariesReferencingTransactions(roomId, setOf(transactionId)))
        }
    }
    val included by includedFlow.collectAsState(emptyMap())
    val userUI = UserUI(client, roomId)
    val settings = koinInject<Settings>()
    val monetaryUnit by settings.monetaryUnit.collectAsState(initial = MonetaryUnit.default)
    val includedInSummary = included[transactionId]?.isNotEmpty() == true
    val eventx = event?.let {
        TransactionDetailsEvent(
            it.content?.getOrNull() as? ZeroInterestTransactionEvent ?: return@let null,
            it.sender,
            it.originTimestamp
        )
    }

    TransactionDetailsContent(eventx, includedInSummary, userUI, monetaryUnit, onBack)
}

data class TransactionDetailsEvent(val content: ZeroInterestTransactionEvent, val sender: UserId, val timestamp: Long)

@Composable
fun TransactionDetailsContent(
    event: TransactionDetailsEvent?,
    includedInSummary: Boolean,
    userUI: UserUI,
    monetaryUnit: MonetaryUnit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transaction_details)) },
                navigationIcon = {
                    BackButton(onBack = onBack)
                }
            )
        }
    ) { padding ->
        if (event == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(Modifier.size(128.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!includedInSummary) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.not_included_in_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                item {
                    Text(stringResource(Res.string.description), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (event.content.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) stringResource(Res.string.payment) else event.content.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                item {
                    Text(stringResource(Res.string.timestamp), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = event.timestamp.toString(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                item {
                    Text(stringResource(Res.string.entered_by), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(event.sender)
                    }
                }

                item {
                    Text(stringResource(Res.string.lender), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(event.content.sender)
                    }
                }

                item {
                    Text(stringResource(Res.string.total_amount), style = MaterialTheme.typography.labelMedium)
                    Text(event.content.total.format(monetaryUnit), style = MaterialTheme.typography.bodyLarge)
                }

                item {
                    Text(stringResource(Res.string.recipients), style = MaterialTheme.typography.titleMedium)
                }

                items(event.content.receivers.entries.toList()) { (userId, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(userId)
                        Spacer(Modifier.weight(1f))
                        Text(amount.format(monetaryUnit))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TransactionDetailsScreenPreview() {
    TransactionDetailsContent(
        event = TransactionDetailsEvent(
            ZeroInterestTransactionEvent(
                ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION,
                UserId("alice", "example.com"),
                mapOf(
                    UserId("bob", "example.com") to 100L.toMoney(),
                    UserId("charlie", "example.com") to 50L.toMoney()
                )
            ),
            UserId("alice", "example.com"),
            1779611380
        ),
        includedInSummary = false,
        userUI = PreviewUserUI,
        monetaryUnit = MonetaryUnit.default,
        onBack = {}
    )
}
