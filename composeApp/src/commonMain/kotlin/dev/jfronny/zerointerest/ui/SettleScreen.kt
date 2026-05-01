package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.service.TransactionService
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.component.SimpleFilledIconButton
import dev.jfronny.zerointerest.ui.component.UserUI
import dev.jfronny.zerointerest.ui.component.rememberTransactionLauncher
import dev.jfronny.zerointerest.util.calculateSettlementTransactions
import dev.jfronny.zerointerest.util.formatBalance
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleScreen(
    client: MatrixClient,
    roomId: RoomId,
    onBack: () -> Unit
) {
    val trustService = koinInject<SummaryTrustService>()
    val transactionService = koinInject<TransactionService>()
    val userUI = UserUI(client, roomId)

    val summaryState by trustService.getSummary(roomId).collectAsState(SummaryTrustService.Summary.Empty)
    
    var showConfirmDialog by remember { mutableStateOf(false) }
    val launcher = rememberTransactionLauncher(client)
    
    val suggestedTransactions = remember(summaryState) {
        val balances = when (val s = summaryState) {
            is SummaryTrustService.Summary.Trusted -> s.event.balances
            is SummaryTrustService.Summary.Untrusted -> s.content.balances
            else -> emptyMap()
        }
        calculateSettlementTransactions(balances)
    }
    
    var remainingTransactions by remember(suggestedTransactions) { 
        mutableStateOf(suggestedTransactions) 
    }

    fun acceptAll() = launcher.tryLaunch {
        transactionService.sendTransactions(roomId, remainingTransactions)
        onBack()
    }

    fun accept(transaction: ZeroInterestTransactionEvent) = launcher.tryLaunch {
        transactionService.sendTransaction(roomId, transaction)
        remainingTransactions = remainingTransactions.filter { it != transaction }
        if (remainingTransactions.isEmpty()) {
            onBack()
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(Res.string.settle_up)) },
            text = { Text(stringResource(Res.string.confirm_settle_all)) },
            confirmButton = {
                TextButton(onClick = ::acceptAll) {
                    Text(stringResource(Res.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settle_up)) },
                navigationIcon = { BackButton(onBack = onBack) }
            )
        },
        floatingActionButton = {
            if (remainingTransactions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (remainingTransactions.size < 2) {
                            acceptAll()
                        } else {
                            showConfirmDialog = true
                        }
                    },
                    icon = { Icon(Icons.Default.Check, null) },
                    text = { Text(stringResource(Res.string.accept_all)) }
                )
            }
        }
    ) { padding ->
        launcher.ErrorDialog()

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (remainingTransactions.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_debts),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(remainingTransactions, key = { it.hashCode() }) { transaction ->
                        val senderUI = userUI.component(transaction.sender)
                        val receiverId = transaction.receivers.keys.firstOrNull()
                        val receiverUI = receiverId?.let { userUI.component(it) }
                        val amount = transaction.receivers.values.sum()

                        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${senderUI.name} → ${receiverUI?.name ?: "Unknown"}")
                                    Text(formatBalance(amount), style = MaterialTheme.typography.titleMedium)
                                }
                                SimpleFilledIconButton(Icons.Default.Check, stringResource(Res.string.accept), onClick = {
                                    accept(transaction)
                                })
                            }
                        }
                    }
                }
            }
            
            if (launcher.isRunning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
