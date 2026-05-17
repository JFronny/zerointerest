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
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import dev.jfronny.zerointerest.data.money.toMoney
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.component.ErrorDialog
import dev.jfronny.zerointerest.ui.component.MoreOptionsButton
import dev.jfronny.zerointerest.ui.component.SimpleFilledIconButton
import dev.jfronny.zerointerest.ui.viewmodel.CreateTransactionViewModel
import dev.jfronny.zerointerest.ui.viewmodel.CreateTransactionViewModel.MoneyState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CreateTransactionScreen(
    roomId: RoomId,
    initialTemplate: TransactionTemplate?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    openSettings: () -> Unit,
) {
    val viewModel = koinViewModel<CreateTransactionViewModel> { parametersOf(roomId, initialTemplate) }
    
    val state by viewModel.state.collectAsState()
    val monetaryUnit by viewModel.monetaryUnit.collectAsState()
    val requestFullKeyboard by viewModel.requestFullKeyboard.collectAsState()
    val users by viewModel.users.collectAsState()
    
    CreateTransactionContent(
        state = state,
        monetaryUnit = monetaryUnit,
        requestFullKeyboard = requestFullKeyboard,
        users = users,
        userIds = users.keys.toList(),
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onSenderChanged = viewModel::onSenderChanged,
        onTotalChanged = viewModel::onTotalChanged,
        onTotalBlurred = viewModel::onTotalBlurred,
        onRecipientsChanged = viewModel::onRecipientsChanged,
        onIndividualAmountChanged = viewModel::onIndividualAmountChanged,
        onIndividualAmountBlurred = viewModel::onIndividualAmountBlurred,
        onSaveAsTemplate = { viewModel.saveAsTemplate(onBack) },
        onDeleteTemplate = { viewModel.deleteTemplate(onBack) },
        onSubmit = { viewModel.submit(onDone) },
        onClearError = viewModel::clearError,
        onBack = onBack,
        openSettings = openSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTransactionContent(
    state: CreateTransactionViewModel.State,
    monetaryUnit: MonetaryUnit,
    requestFullKeyboard: Boolean,
    users: Map<UserId, RoomUser?>,
    userIds: List<UserId>,
    onDescriptionChanged: (String) -> Unit,
    onSenderChanged: (UserId) -> Unit,
    onTotalChanged: (String) -> Unit,
    onTotalBlurred: () -> Unit,
    onRecipientsChanged: (Set<UserId>) -> Unit,
    onIndividualAmountChanged: (UserId, String) -> Unit,
    onIndividualAmountBlurred: (UserId) -> Unit,
    onSaveAsTemplate: () -> Unit,
    onDeleteTemplate: () -> Unit,
    onSubmit: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    openSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_transaction)) },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    MoreOptionsButton(openSettings = openSettings) { close ->
                        if (!state.isTemplateModified) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.delete_template)) },
                                onClick = {
                                    onDeleteTemplate()
                                    close()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.save_as_template)) },
                                enabled = state.recipients.isNotEmpty() && state.total.amountStr.isNotEmpty(),
                                onClick = {
                                    onSaveAsTemplate()
                                    close()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSubmit) {
                if (state.isRunning) LoadingIndicator()
                else Icon(Icons.Default.Check, stringResource(Res.string.save))
            }
        }
    ) { padding ->
        if (state.errorMessage != null) ErrorDialog(state.errorMessage, onDismiss = { onClearError() })

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChanged,
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
                            value = users[state.sender]?.event?.content?.displayName ?: state.sender.full,
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
                                    Text(user?.event?.content?.displayName ?: userId.full)
                                } },
                                onClick = {
                                    onSenderChanged(userId)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            @Composable
            fun MoneyStateField(state: CreateTransactionViewModel.MoneyState, onChange: (String) -> Unit, onBlurred: () -> Unit) = OutlinedTextField(
                value = state.amountStr,
                onValueChange = onChange,
                label = { Text(stringResource(Res.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = if (requestFullKeyboard) KeyboardType.Unspecified else KeyboardType.Decimal),
                isError = state.showError,
                supportingText = if (state.showError) { { Text(stringResource(Res.string.invalid_amount)) } } else null,
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (!it.isFocused) onBlurred()
                }
            )

            item {
                MoneyStateField(state.total, onTotalChanged, onTotalBlurred)
            }

            item {
                if (state.total.hint) {
                    val hint = state.total.amount?.format(monetaryUnit) ?: return@item
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.recipients), style = MaterialTheme.typography.titleMedium)
                    if (state.recipients.isNotEmpty()) {
                        SimpleFilledIconButton(Icons.Default.Deselect, stringResource(Res.string.deselect), onClick = {
                            onRecipientsChanged(emptySet())
                        })
                    } else {
                        SimpleFilledIconButton(Icons.Default.SelectAll, stringResource(Res.string.select_all), onClick = {
                            val newSet = state.recipients.keys + userIds
                            onRecipientsChanged(newSet)
                        })
                    }
                }
            }

            items(userIds) { userId ->
                val moneyState = state.recipients[userId]
                val isSelected = moneyState != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newSet = if (isSelected) state.recipients.keys - userId else state.recipients.keys + userId
                        onRecipientsChanged(newSet)
                    }
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val newSet = if (checked) state.recipients.keys + userId else state.recipients.keys - userId
                            onRecipientsChanged(newSet)
                        }
                    )
                    Text(users[userId]?.event?.content?.displayName ?: userId.full)
                }

                if (isSelected) {
                    MoneyStateField(moneyState, { onIndividualAmountChanged(userId, it) }, { onIndividualAmountBlurred(userId) })
                }
            }
        }
    }
}

@Preview
@Composable
fun CreateTransactionScreenPreview() {
    CreateTransactionContent(
        state = CreateTransactionViewModel.State(
            description = "Pizza",
            sender = UserId("alice", "example.com"),
            total = MoneyState(1250L.toMoney()),
            recipients = mapOf(UserId("bob", "example.com") to MoneyState(100L.toMoney())),
        ),
        monetaryUnit = MonetaryUnit.default,
        requestFullKeyboard = false,
        users = mapOf(UserId("alice", "example.com") to null, UserId("bob", "example.com") to null),
        userIds = listOf(UserId("alice", "example.com"), UserId("bob", "example.com")),
        onDescriptionChanged = {},
        onSenderChanged = {},
        onTotalChanged = {},
        onTotalBlurred = {},
        onRecipientsChanged = {},
        onIndividualAmountChanged = { _, _ -> },
        onIndividualAmountBlurred = {},
        onSaveAsTemplate = {},
        onDeleteTemplate = {},
        onSubmit = {},
        onClearError = {},
        onBack = {},
        openSettings = {}
    )
}
