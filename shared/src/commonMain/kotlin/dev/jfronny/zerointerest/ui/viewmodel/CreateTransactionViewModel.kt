package dev.jfronny.zerointerest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
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
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.service.getActive
import dev.jfronny.zerointerest.service.getExchangeRates
import dev.jfronny.zerointerest.shared.generated.resources.Res
import dev.jfronny.zerointerest.shared.generated.resources.device_offline
import dev.jfronny.zerointerest.ui.component.TransactionLauncher
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class CreateTransactionViewModel(
    val roomId: RoomId,
    val initialTemplate: TransactionTemplate?,
    private val client: ZiClient,
    private val trustService: SummaryTrustService,
    private val transactionService: TransactionService,
    private val database: ZeroInterestDatabase,
    private val settings: Settings,
    private val distributeRandom: Random = Random(Clock.System.now().toEpochMilliseconds()),
) : ViewModel() {

    private val _state = MutableStateFlow(
        State(
            description = initialTemplate?.description ?: "",
            sender = initialTemplate?.sender ?: client.userId,
            total = initialTemplate?.receivers?.values?.sum()?.let(::MoneyState) ?: MoneyState.zero,
            recipients = initialTemplate?.receivers?.mapValues { MoneyState(it.value) } ?: emptyMap(),
        ),
    )
    val state = _state.asStateFlow()

    data class State(
        val description: String = "",
        val sender: UserId,
        val total: MoneyState = MoneyState.zero,
        val recipients: Map<UserId, MoneyState> = emptyMap(),
        val isTemplateModified: Boolean = false,
        val submitAttempted: Boolean = false,

        val isRunning: Boolean = false,
        val errorMessage: String? = null,
    ) {
        val allValid: Boolean get() = total.isValid && recipients.values.all { it.isValid }
    }

    data class MoneyState(
        val amount: Money?,
        val hint: Boolean,
        val amountStr: String,
        val isBlurred: Boolean,
    ) {
        constructor(amount: Money) : this(amount, false, amount.toString(), false)

        val isValid: Boolean get() = amount != null
        val showError: Boolean get() = amount == null && isBlurred

        companion object {
            val zero = MoneyState(Money.zero, false, "", false)

            operator fun invoke(amountStr: String, unit: MonetaryUnit): MoneyState {
                val (amount, hint) = parseAmount(amountStr, unit).fold(
                    onSuccess = { it.toMoney() to it.usedMath },
                    onFailure = { null to false },
                )
                return MoneyState(amount, hint, amountStr, false)
            }
        }
    }

    val monetaryUnit: StateFlow<MonetaryUnit> = settings.monetaryUnit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonetaryUnit.default,
    )
    val requestFullKeyboard = settings.requestFullKeyboard.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )
    val users = client.getActive(roomId, trustService).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    companion object {
        private fun parseAmount(s: String, unit: MonetaryUnit): Result<MoneyParser.Result> = try {
            Result.success(MoneyParser.parse(s, unit, getExchangeRates(unit)))
        } catch (e: MoneyParser.ParseException) {
            Result.failure(e)
        }
    }

    private fun checkForModifications() {
        if (initialTemplate == null) return
        val currentState = _state.value
        val currentRecipients = currentState.recipients.mapValues { it.value.amount ?: Money.zero }
        val modified = currentState.description != initialTemplate.description ||
            currentState.sender != initialTemplate.sender ||
            currentRecipients != initialTemplate.receivers

        _state.update { it.copy(isTemplateModified = modified) }
    }

    private fun distribute(total: Money, recipients: Set<UserId>) {
        if (recipients.isEmpty()) {
            _state.update { it.copy(recipients = emptyMap()) }
            return
        }
        val count = recipients.size
        val base = total.amount / count
        val remainder = total.amount % count

        val newInputs = mutableMapOf<UserId, MoneyState>()
        recipients.shuffled(distributeRandom).forEachIndexed { index, userId ->
            val amount = base + if (index < remainder) 1 else 0
            newInputs[userId] = MoneyState(amount.toMoney())
        }
        _state.update { it.copy(recipients = newInputs) }
        checkForModifications()
    }

    fun onTotalChanged(newTotalStr: String) {
        val newState = MoneyState(newTotalStr, monetaryUnit.value)
        _state.update {
            it.copy(
                total = newState,
            )
        }
        if (newState.amount != null) {
            distribute(newState.amount, _state.value.recipients.keys)
        }
        checkForModifications()
    }

    fun onTotalBlurred() {
        _state.update { it.copy(total = it.total.copy(isBlurred = true)) }
    }

    fun onRecipientsChanged(newRecipients: Set<UserId>) {
        val total = _state.value.total.amount
        if (total != null) {
            distribute(total, newRecipients)
        } else {
            val newInputs = newRecipients.associateWith { _state.value.recipients[it] ?: MoneyState.zero }
            _state.update { it.copy(recipients = newInputs) }
        }
        checkForModifications()
    }

    fun onIndividualAmountChanged(userId: UserId, newAmountStr: String) {
        _state.update {
            val newInputs = it.recipients.toMutableMap()
            newInputs[userId] = MoneyState(newAmountStr, monetaryUnit.value)
            val total = newInputs.values.sumOfM { it.amount ?: Money.zero }
            it.copy(
                recipients = newInputs,
                total = MoneyState(total),
            )
        }
        checkForModifications()
    }

    fun onIndividualAmountBlurred(userId: UserId) {
        _state.update {
            val newInputs = it.recipients.toMutableMap()
            newInputs[userId] = newInputs[userId]?.copy(isBlurred = true) ?: MoneyState.zero
            it.copy(recipients = newInputs)
        }
    }

    fun onDescriptionChanged(newDescription: String) {
        _state.update { it.copy(description = newDescription) }
        checkForModifications()
    }

    fun onSenderChanged(newSender: UserId) {
        _state.update { it.copy(sender = newSender) }
        checkForModifications()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveAsTemplate(onDone: () -> Unit) {
        viewModelScope.launch {
            val currentState = _state.value
            val total = currentState.total.amount ?: Money.zero
            val recipientAmounts = currentState.recipients.mapValues { it.value.amount ?: Money.zero }

            if (recipientAmounts.isNotEmpty() && total.amount > 0) {
                val template = TransactionTemplate(
                    id = Uuid.random().toString(),
                    description = currentState.description,
                    sender = currentState.sender,
                    receivers = recipientAmounts,
                )
                database.addTransactionTemplate(roomId, template)
                onDone()
            } else {
                onDone()
            }
        }
    }

    fun deleteTemplate(onDone: () -> Unit) {
        viewModelScope.launch {
            if (initialTemplate != null) {
                database.removeTransactionTemplate(roomId, initialTemplate.id)
            }
            onDone()
        }
    }

    fun submit(onDone: () -> Unit) {
        val currentState = _state.value
        if (!currentState.allValid) return

        val content = ZeroInterestTransactionEvent(
            description = currentState.description.ifBlank { ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION },
            sender = currentState.sender,
            receivers = currentState.recipients
                .mapValues { it.value.amount ?: Money.zero }
                .filter { it.value.amount > 0L },
        )

        viewModelScope.launch {
            if (client.offline) {
                _state.update { it.copy(errorMessage = getString(Res.string.device_offline), isRunning = false) }
                return@launch
            }
            _state.update { it.copy(submitAttempted = true) }
            try {
                transactionService.sendTransaction(roomId, content)
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = getString(TransactionLauncher.logAndLocalize(e), e.message ?: "")) }
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
