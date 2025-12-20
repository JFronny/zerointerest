package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.util.formatBalance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsScreen(
    client: MatrixClient,
    roomId: RoomId,
    transactionId: EventId,
    onBack: () -> Unit
) {
    val transactionFlow = remember(roomId, transactionId) {
        client.room.getTimelineEvent(roomId, transactionId)
            .filterNotNull()
            .map { it.content?.getOrNull() as? ZeroInterestTransactionEvent }
    }
    val transaction by transactionFlow.collectAsState(null)
    val userUI = UserUI(client, roomId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                item {
                    Text("Description", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (tx.description == ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION) "Payment" else tx.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                item {
                    Text("Sender", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        userUI(tx.sender)
                    }
                }

                item {
                    Text("Total Amount", style = MaterialTheme.typography.labelMedium)
                    Text(formatBalance(tx.total), style = MaterialTheme.typography.bodyLarge)
                }

                item {
                    Text("Recipients", style = MaterialTheme.typography.titleMedium)
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
