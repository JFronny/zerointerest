package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.SummaryTrustService
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.koin.compose.koinInject
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTransactionScreen(
    client: MatrixClient,
    roomId: RoomId,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val trustService = koinInject<SummaryTrustService>()
    val users by client.user.getAll(roomId).collectAsState(emptyMap())
    val userIds = users.keys.toList()

    var description by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf(client.userId) }
    var totalAmountStr by remember { mutableStateOf("") }
    var selectedRecipients by remember { mutableStateOf(setOf<UserId>()) }
    var recipientAmountInputs by remember { mutableStateOf(mapOf<UserId, String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseAmount(s: String): Long? {
        return s.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    }

    fun formatAmount(l: Long): String {
        val d = l / 100.0
        return d.toString()
    }

    fun distribute(total: Long, recipients: Set<UserId>) {
        if (recipients.isEmpty()) {
            recipientAmountInputs = emptyMap()
            return
        }
        val count = recipients.size
        val base = total / count
        val remainder = total % count

        val newInputs = mutableMapOf<UserId, String>()
        recipients.forEachIndexed { index, userId ->
            val amount = base + if (index < remainder) 1 else 0
            newInputs[userId] = formatAmount(amount)
        }
        recipientAmountInputs = newInputs
    }

    fun onTotalChanged(newTotalStr: String) {
        totalAmountStr = newTotalStr
        val total = parseAmount(newTotalStr)
        if (total != null) {
            distribute(total, selectedRecipients)
        }
    }

    fun onRecipientsChanged(newRecipients: Set<UserId>) {
        selectedRecipients = newRecipients
        val total = parseAmount(totalAmountStr)
        if (total != null) {
            distribute(total, newRecipients)
        } else {
            // Preserve existing inputs if possible, or init to 0
            val newInputs = newRecipients.associateWith { recipientAmountInputs[it] ?: "0.0" }
            recipientAmountInputs = newInputs
        }
    }

    fun onIndividualAmountChanged(userId: UserId, newAmountStr: String) {
        val newInputs = recipientAmountInputs.toMutableMap()
        newInputs[userId] = newAmountStr
        recipientAmountInputs = newInputs

        val total = newInputs.values.sumOf { parseAmount(it) ?: 0L }
        totalAmountStr = formatAmount(total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(onClick = {
                val total = parseAmount(totalAmountStr) ?: 0L
                val recipientAmounts = recipientAmountInputs.mapValues { parseAmount(it.value) ?: 0L }
                val description = description.ifBlank { ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION }
                if (recipientAmounts.isNotEmpty() && total > 0) {
                    scope.launch {
                        if (client.syncState.value != SyncState.RUNNING) {
                            error = "Device is offline"
                            return@launch
                        }

                        val content = ZeroInterestTransactionEvent(
                            description = description,
                            sender = sender,
                            receivers = recipientAmounts
                        )
                        val txId = client.room.sendMessage(roomId) {
                            content(content)
                        }
                        val outbox = client.room.getOutbox(roomId, txId)
                            .filterNotNull()
                            .filter { it.eventId != null || it.sendError != null }
                            .firstOrNull()
                        if (outbox == null) {
                            error = "Failed to send message"
                            return@launch
                        }
                        if (outbox.sendError != null) {
                            error = "Failed to send message: ${outbox.sendError}"
                            return@launch
                        }

                        try {
                            trustService.createSummary(roomId, outbox.eventId!!, content)
                        } catch (e: Exception) {
                            error = "Failed to create trust summary: ${e.message}"
                            return@launch
                        }
                        onDone()
                    }
                }
            }) {
                Icon(Icons.Default.Check, "Save")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Payment") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                val senderName by (users[sender]?.map { it?.name } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = senderName ?: sender.full,
                        onValueChange = {},
                        label = { Text("Sender") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        userIds.forEach { userId ->
                            val name by (users[userId]?.map { it?.name } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
                            DropdownMenuItem(
                                text = { Text(name ?: userId.full) },
                                onClick = {
                                    sender = userId
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = totalAmountStr,
                    onValueChange = { onTotalChanged(it) },
                    label = { Text("Total Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Recipients", style = MaterialTheme.typography.titleMedium)
            }

            items(userIds) { userId ->
                val isSelected = userId in selectedRecipients
                val name by (users[userId]?.map { it?.name } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newSet = if (isSelected) selectedRecipients - userId else selectedRecipients + userId
                        onRecipientsChanged(newSet)
                    }
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val newSet = if (checked) selectedRecipients + userId else selectedRecipients - userId
                            onRecipientsChanged(newSet)
                        }
                    )
                    Text(name ?: userId.full)
                }

                if (isSelected) {
                    val amountStr = recipientAmountInputs[userId] ?: ""
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { onIndividualAmountChanged(userId, it) },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
                    )
                }
            }
        }
    }
}
