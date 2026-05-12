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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import dev.jfronny.zerointerest.data.money.Money
import dev.jfronny.zerointerest.data.money.MoneyParser
import dev.jfronny.zerointerest.data.money.sum
import dev.jfronny.zerointerest.data.money.sumOfM
import dev.jfronny.zerointerest.data.money.toMoney
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.service.TransactionService
import dev.jfronny.zerointerest.service.getActive
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.component.MoreOptionsButton
import dev.jfronny.zerointerest.ui.component.rememberTransactionLauncher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.collections.forEachIndexed
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun CreateTransactionScreen(
    client: MatrixClient,
    roomId: RoomId,
    initialTemplate: TransactionTemplate?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    openSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trustService = koinInject<SummaryTrustService>()
    val transactionService = koinInject<TransactionService>()
    val database = koinInject<ZeroInterestDatabase>()
    val settings = koinInject<Settings>()
    val monetaryUnit by settings.monetaryUnit.collectAsState(initial = MonetaryUnit.default)

    val users by remember(client) { client.user.getActive(roomId, trustService) }.collectAsState(emptyMap())
    val userIds = remember(users) { users.keys.toList() }

    var description by remember { mutableStateOf(initialTemplate?.description ?: "") }
    var sender by remember { mutableStateOf(initialTemplate?.sender ?: client.userId) }
    var totalAmountStr by remember { mutableStateOf("") }
    var selectedRecipients by remember { mutableStateOf(initialTemplate?.receivers?.keys ?: setOf()) }
    var recipientAmountInputs by remember { mutableStateOf(mapOf<UserId, String>()) }
    var isTemplateModified by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    var totalAmountBlurred by remember { mutableStateOf(false) }
    var recipientAmountsBlurred by remember { mutableStateOf(setOf<UserId>()) }
    val launcher = rememberTransactionLauncher(client)

    fun parseAmount(s: String): Result<Money> = try {
        Result.success(Money.parse(s, monetaryUnit))
    } catch (e: MoneyParser.ParseException) {
        Result.failure(e)
    }

    val totalAmountValid = parseAmount(totalAmountStr).isSuccess
    val totalAmountError = !totalAmountValid && (submitAttempted || totalAmountBlurred)
    val allValid = totalAmountValid && selectedRecipients.all { parseAmount(recipientAmountInputs[it] ?: "").isSuccess }

    // Initialize amounts if template provided
    LaunchedEffect(initialTemplate) {
        if (initialTemplate != null) {
            val total = initialTemplate.receivers.values.sum()
            totalAmountStr = total.toString()
            recipientAmountInputs = initialTemplate.receivers.mapValues { it.value.toString() }
        }
    }

    fun checkForModifications() {
        if (initialTemplate == null) return
        val currentRecipients = recipientAmountInputs.mapValues { parseAmount(it.value).getOrDefault(Money.zero) }
        isTemplateModified = description != initialTemplate.description ||
                sender != initialTemplate.sender ||
                currentRecipients != initialTemplate.receivers
    }

    fun distribute(total: Money, recipients: Set<UserId>) {
        if (recipients.isEmpty()) {
            recipientAmountInputs = emptyMap()
            return
        }
        val count = recipients.size
        val base = total.amount / count
        val remainder = total.amount % count

        val newInputs = mutableMapOf<UserId, String>()
        recipients.forEachIndexed { index, userId ->
            val amount = base + if (index < remainder) 1 else 0
            newInputs[userId] = amount.toMoney().toString()
        }
        recipientAmountInputs = newInputs
        checkForModifications()
    }

    fun onTotalChanged(newTotalStr: String) {
        totalAmountStr = newTotalStr
        val total = parseAmount(newTotalStr).getOrNull()
        if (total != null) {
            distribute(total, selectedRecipients)
        }
        checkForModifications()
    }

    fun onRecipientsChanged(newRecipients: Set<UserId>) {
        selectedRecipients = newRecipients
        val total = parseAmount(totalAmountStr).getOrNull()
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

        val total = newInputs.values.sumOfM { parseAmount(it).getOrDefault(Money.zero) }
        totalAmountStr = total.format(monetaryUnit)
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
                    BackButton(onBack = onBack)
                },
                actions = {
                    MoreOptionsButton(openSettings = openSettings) { close ->
                        if (initialTemplate != null && !isTemplateModified) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.delete_template)) },
                                onClick = {
                                    scope.launch {
                                        database.removeTransactionTemplate(roomId, initialTemplate.id)
                                        close()
                                        onBack()
                                    }
                                }
                            )
                        } else {
                            val total = parseAmount(totalAmountStr).getOrDefault(Money.zero)
                            val recipientAmounts = recipientAmountInputs.mapValues { parseAmount(it.value).getOrDefault(Money.zero) }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.save_as_template)) },
                                enabled = recipientAmounts.isNotEmpty() && total.amount > 0,
                                onClick = {
                                    scope.launch {
                                        // local copy, just to be extra sure
                                        val total = total
                                        val recipientAmounts = recipientAmounts

                                        if (recipientAmounts.isNotEmpty() && total.amount > 0) {
                                            val template = TransactionTemplate(
                                                id = Uuid.random().toString(),
                                                description = description,
                                                sender = sender,
                                                receivers = recipientAmounts
                                            )
                                            database.addTransactionTemplate(roomId, template)
                                            close()
                                            onBack()
                                        } else {
                                            close()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(onClick = {
                submitAttempted = true
                if (!allValid) return@FloatingActionButton

                val content = ZeroInterestTransactionEvent(
                    description = description.ifBlank { ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION },
                    sender = sender,
                    receivers = recipientAmountInputs
                        .mapValues { parseAmount(it.value).getOrDefault(Money.zero) }
                        .filter { it.value.amount > 0L }
                )

                if (content.receivers.isNotEmpty()) {
                    launcher.tryLaunch {
                        transactionService.sendTransaction(roomId, content)

                        onDone()
                    }
                }
            }) {
                if (launcher.isRunning) LoadingIndicator()
                else Icon(Icons.Default.Check, stringResource(Res.string.save))
            }
        }
    ) { padding ->
        launcher.ErrorDialog()

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            readOnly = true,
                            value = users[sender]?.name ?: sender.full,
                            onValueChange = {},
                            label = { Text(stringResource(Res.string.lender)) },
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
                                    Text(user?.name ?: userId.full)
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
                    onValueChange = {
                        onTotalChanged(it)
                        totalAmountBlurred = false
                    },
                    label = { Text(stringResource(Res.string.total_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = totalAmountError,
                    supportingText = if (totalAmountError) { { Text(stringResource(Res.string.invalid_amount)) } } else null,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { 
                        if (!it.isFocused) totalAmountBlurred = true
                    }
                )
            }

            item {
                Text(stringResource(Res.string.recipients), style = MaterialTheme.typography.titleMedium)
            }

            items(userIds) { userId ->
                val isSelected = userId in selectedRecipients
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
                    Text(users[userId]?.name ?: userId.full)
                }

                if (isSelected) {
                    val amountStr = recipientAmountInputs[userId] ?: ""
                    val isError = !parseAmount(amountStr).isSuccess && (submitAttempted || userId in recipientAmountsBlurred)
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            onIndividualAmountChanged(userId, it)
                            recipientAmountsBlurred = recipientAmountsBlurred - userId
                        },
                        label = { Text(stringResource(Res.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = isError,
                        supportingText = if (isError) { { Text(stringResource(Res.string.invalid_amount)) } } else null,
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp).onFocusChanged {
                            if (!it.isFocused) {
                                recipientAmountsBlurred = recipientAmountsBlurred + userId
                            }
                        }
                    )
                }
            }
        }
    }
}
