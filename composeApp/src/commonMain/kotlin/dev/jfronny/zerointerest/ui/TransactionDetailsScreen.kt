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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.util.formatBalance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsScreen(
    client: MatrixClient,
    roomId: RoomId,
    transactionId: EventId,
    onBack: () -> Unit
) {
    val transactionFlow = remember(roomId, transactionId) {
        client.room.getTimelineEvent(roomId, transactionId) {
            fetchTimeout = 5.seconds
            allowReplaceContent = false
        }.map { it?.content?.getOrNull() as? ZeroInterestTransactionEvent }
    }
    val transaction by transactionFlow.collectAsState(null)
    val trust = koinInject<SummaryTrustService>()
    val includedFlow = remember(roomId, transactionId) {
        flow {
            emit(trust.getSummariesReferencingTransactions(roomId, setOf(transactionId)))
        }
    }
    val included by includedFlow.collectAsState(emptyMap())
    val userUI = UserUI(client, roomId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transaction_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val tx = transaction
        if (tx == null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (included[transactionId]?.isEmpty() != false) {
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
                        text = if (tx.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) stringResource(Res.string.payment) else tx.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                item {
                    Text(stringResource(Res.string.sender), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(tx.sender)
                    }
                }

                item {
                    Text(stringResource(Res.string.total_amount), style = MaterialTheme.typography.labelMedium)
                    Text(formatBalance(tx.total), style = MaterialTheme.typography.bodyLarge)
                }

                item {
                    Text(stringResource(Res.string.recipients), style = MaterialTheme.typography.titleMedium)
                }

                items(tx.receivers.entries.toList()) { (userId, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(userId)
                        Spacer(Modifier.weight(1f))
                        Text(formatBalance(amount))
                    }
                }
            }
        }
    }
}
