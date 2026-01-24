package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.runtime.LaunchedEffect
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
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToLong
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun CreateTransactionScreen(
    client: MatrixClient,
    roomId: RoomId,
    initialTemplate: TransactionTemplate?,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val trustService = koinInject<SummaryTrustService>()
    val database = koinInject<ZeroInterestDatabase>()
    val users by remember(client) { client.user.getAll(roomId) }.collectAsState(emptyMap())
    val userIds = remember(users) { users.keys.toList() }

    var description by remember { mutableStateOf(initialTemplate?.description ?: "") }
    var sender by remember { mutableStateOf(initialTemplate?.sender ?: client.userId) }
    var totalAmountStr by remember { mutableStateOf("") }
    var selectedRecipients by remember { mutableStateOf(initialTemplate?.receivers?.keys ?: setOf()) }
    var recipientAmountInputs by remember { mutableStateOf(mapOf<UserId, String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isTemplateModified by remember { mutableStateOf(false) }

    fun parseAmount(s: String): Long? {
        return s.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    }

    fun formatAmount(l: Long): String {
        val d = l / 100.0
        return d.toString()
    }
    // Initialize amounts if template provided
    LaunchedEffect(initialTemplate) {
        if (initialTemplate != null) {
            val total = initialTemplate.receivers.values.sum()
            totalAmountStr = formatAmount(total)
            recipientAmountInputs = initialTemplate.receivers.mapValues { formatAmount(it.value) }
        }
    }

    fun checkForModifications() {
        if (initialTemplate == null) return
        val currentRecipients = recipientAmountInputs.mapValues { parseAmount(it.value) ?: 0L }
        isTemplateModified = description != initialTemplate.description ||
                sender != initialTemplate.sender ||
                currentRecipients != initialTemplate.receivers
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
        checkForModifications()
    }

    fun onTotalChanged(newTotalStr: String) {
        totalAmountStr = newTotalStr
        val total = parseAmount(newTotalStr)
        if (total != null) {
            distribute(total, selectedRecipients)
        }
        checkForModifications()
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
        checkForModifications()
    }

    fun onIndividualAmountChanged(userId: UserId, newAmountStr: String) {
        val newInputs = recipientAmountInputs.toMutableMap()
        newInputs[userId] = newAmountStr
        recipientAmountInputs = newInputs

        val total = newInputs.values.sumOf { parseAmount(it) ?: 0L }
        totalAmountStr = formatAmount(total)
        checkForModifications()
    }
    
    fun onDescriptionChanged(newDescription: String) {
        description = newDescription
        checkForModifications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_transaction)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    
                    if (initialTemplate != null && !isTemplateModified) {
                         IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(Res.string.options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.delete_template)) },
                                onClick = {
                                    scope.launch {
                                        database.removeTransactionTemplate(roomId, initialTemplate.id)
                                        menuExpanded = false
                                        onBack()
                                    }
                                }
                            )
                        }
                    } else {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(Res.string.options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.save_as_template)) },
                                onClick = {
                                    scope.launch {
                                        val total = parseAmount(totalAmountStr) ?: 0L
                                        val recipientAmounts = recipientAmountInputs.mapValues { parseAmount(it.value) ?: 0L }
                                        if (recipientAmounts.isNotEmpty() && total > 0) {
                                            val template = TransactionTemplate(
                                                id = Uuid.random().toString(),
                                                description = description,
                                                sender = sender,
                                                receivers = recipientAmounts
                                            )
                                            database.addTransactionTemplate(roomId, template)
                                        }
                                        menuExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },

        floatingActionButton = {
            var launched by remember { mutableStateOf(false) }
            FloatingActionButton(onClick = {
                if (launched) return@FloatingActionButton
                val total = parseAmount(totalAmountStr) ?: 0L
                val recipientAmounts = recipientAmountInputs.mapValues { parseAmount(it.value) ?: 0L }
                val description = description.ifBlank { ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION }
                if (recipientAmounts.isNotEmpty() && total > 0) {
                    launched = true
                    scope.launch {
                        if (client.syncState.value != SyncState.RUNNING) {
                            error = getString(Res.string.device_offline)
                            launched = false
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
                            error = getString(Res.string.failed_send_message)
                            launched = false
                            return@launch
                        }
                        if (outbox.sendError != null) {
                            error = getString(Res.string.failed_send_message_with_error, outbox.sendError.toString())
                            launched = false
                            return@launch
                        }

                        try {
                            trustService.createSummary(roomId, outbox.eventId!!, content)
                        } catch (e: Exception) {
                            error = getString(Res.string.failed_create_trust_summary, e.message.toString())
                            launched = false
                            return@launch
                        }
                        onDone()
                    }
                }
            }) {
                if (launched) CircularProgressIndicator()
                else Icon(Icons.Default.Check, stringResource(Res.string.save))
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
                    onValueChange = { onDescriptionChanged(it) },
                    label = { Text(stringResource(Res.string.description)) },
                    placeholder = { Text(stringResource(Res.string.payment)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    run {
                        val flow = remember(sender, users) { users[sender]?.map { it?.name } ?: flowOf(null) }
                        val senderName by flow.collectAsState(null)
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            readOnly = true,
                            value = senderName ?: sender.full,
                            onValueChange = {},
                            label = { Text("Options") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        for ((userId, user) in users) {
                            DropdownMenuItem(
                                text = { Box {
                                    val name by user.map { it?.name }.collectAsState(null)
                                    Text(name ?: userId.full)
                                } },
                                onClick = {
                                    sender = userId
                                    checkForModifications()
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = totalAmountStr,
                    onValueChange = { onTotalChanged(it) },
                    label = { Text(stringResource(Res.string.total_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(stringResource(Res.string.recipients), style = MaterialTheme.typography.titleMedium)
            }

            items(userIds) { userId ->
                val isSelected = userId in selectedRecipients
                val name by (users[userId]?.map { it?.name } ?: flowOf(null)).collectAsState(null)

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
                        label = { Text(stringResource(Res.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
                    )
                }
            }
        }
    }
}
