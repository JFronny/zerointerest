package dev.jfronny.zerointerest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.service.getActive
import dev.jfronny.zerointerest.service.getExchangeRates
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

class CreateTransactionViewModel(
    val roomId: RoomId,
    val initialTemplate: TransactionTemplate?,
    private val client: ZiClient,
    private val trustService: SummaryTrustService,
    private val transactionService: TransactionService,
    private val database: ZeroInterestDatabase,
    private val settings: Settings
) : ViewModel() {

    private val _state = MutableStateFlow(
        State(
            description = initialTemplate?.description ?: "",
            sender = initialTemplate?.sender ?: client.userId,
            selectedRecipients = initialTemplate?.receivers?.keys ?: emptySet()
        )
    )
    val state = _state.asStateFlow()

    data class State(
        val description: String = "",
        val sender: UserId,
        val totalAmountStr: String = "",
        val selectedRecipients: Set<UserId> = emptySet(),
        val recipientAmountInputs: Map<UserId, String> = emptyMap(),
        val isTemplateModified: Boolean = false,
        val submitAttempted: Boolean = false,
        val totalAmountBlurred: Boolean = false,
        val recipientAmountsBlurred: Set<UserId> = emptySet(),

        val totalAmountValid: Boolean = true,
        val totalAmountError: Boolean = false,
        val allValid: Boolean = true,
        val totalHint: Money? = null,

        val isRunning: Boolean = false,
        val errorMessage: String? = null
    )

    val monetaryUnit: StateFlow<MonetaryUnit> = settings.monetaryUnit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonetaryUnit.default
    )
    val requestFullKeyboard = settings.requestFullKeyboard.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    val users = client.getActive(roomId, trustService).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    init {
        if (initialTemplate != null) {
            val total = initialTemplate.receivers.values.sum()
            _state.update {
                it.copy(
                    totalAmountStr = total.toString(),
                    recipientAmountInputs = initialTemplate.receivers.mapValues { entry -> entry.value.toString() }
                )
            }
        }
        
        viewModelScope.launch {
            _state.collect { currentState ->
                val mu = monetaryUnit.value
                val (valid, hint) = parseAmount(currentState.totalAmountStr, mu).fold(
                    onSuccess = { true to if (it.usedMath) it.toMoney() else null },
                    onFailure = { false to null }
                )
                
                val allValid = valid && currentState.selectedRecipients.all { userId ->
                    parseAmount(currentState.recipientAmountInputs[userId] ?: "0.0", mu).isSuccess
                }
                
                _state.update {
                    it.copy(
                        totalAmountValid = valid,
                        totalAmountError = !valid && (it.submitAttempted || it.totalAmountBlurred),
                        allValid = allValid,
                        totalHint = hint
                    )
                }
            }
        }
    }

    private fun parseAmount(s: String) = parseAmount(s, monetaryUnit.value)
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
        val currentRecipients = currentState.recipientAmountInputs.mapValues { 
            parseAmount(it.value).map(MoneyParser.Result::toMoney).getOrDefault(Money.zero) 
        }
        val modified = currentState.description != initialTemplate.description ||
                currentState.sender != initialTemplate.sender ||
                currentRecipients != initialTemplate.receivers
                
        _state.update { it.copy(isTemplateModified = modified) }
    }

    private fun distribute(total: Money, recipients: Set<UserId>) {
        if (recipients.isEmpty()) {
            _state.update { it.copy(recipientAmountInputs = emptyMap()) }
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
        _state.update { it.copy(recipientAmountInputs = newInputs) }
        checkForModifications()
    }

    fun onTotalChanged(newTotalStr: String) {
        _state.update { it.copy(totalAmountStr = newTotalStr, totalAmountBlurred = false) }
        val total = parseAmount(newTotalStr).map(MoneyParser.Result::toMoney).getOrNull()
        if (total != null) {
            distribute(total, _state.value.selectedRecipients)
        }
        checkForModifications()
    }
    
    fun onTotalBlurred() {
        _state.update { it.copy(totalAmountBlurred = true) }
    }

    fun onRecipientsChanged(newRecipients: Set<UserId>) {
        _state.update { it.copy(selectedRecipients = newRecipients) }
        val total = parseAmount(_state.value.totalAmountStr).map(MoneyParser.Result::toMoney).getOrNull()
        if (total != null) {
            distribute(total, newRecipients)
        } else {
            val newInputs = newRecipients.associateWith { _state.value.recipientAmountInputs[it] ?: "0.0" }
            _state.update { it.copy(recipientAmountInputs = newInputs) }
        }
        checkForModifications()
    }

    fun onIndividualAmountChanged(userId: UserId, newAmountStr: String) {
        val newInputs = _state.value.recipientAmountInputs.toMutableMap()
        newInputs[userId] = newAmountStr
        _state.update {
            val total = newInputs.values.sumOfM { parseAmount(it).map(MoneyParser.Result::toMoney).getOrDefault(Money.zero) }
            it.copy(
                recipientAmountInputs = newInputs,
                recipientAmountsBlurred = it.recipientAmountsBlurred - userId,
                totalAmountStr = total.format(monetaryUnit.value)
            )
        }
        checkForModifications()
    }
    
    fun onIndividualAmountBlurred(userId: UserId) {
        _state.update { it.copy(recipientAmountsBlurred = it.recipientAmountsBlurred + userId) }
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
            val total = parseAmount(currentState.totalAmountStr).map(MoneyParser.Result::toMoney).getOrDefault(Money.zero)
            val recipientAmounts = currentState.recipientAmountInputs.mapValues { 
                parseAmount(it.value).map(MoneyParser.Result::toMoney).getOrDefault(Money.zero) 
            }

            if (recipientAmounts.isNotEmpty() && total.amount > 0) {
                val template = TransactionTemplate(
                    id = Uuid.random().toString(),
                    description = currentState.description,
                    sender = currentState.sender,
                    receivers = recipientAmounts
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
        viewModelScope.launch {
            _state.update { it.copy(submitAttempted = true) }
            val currentState = _state.value
            if (!currentState.allValid) return@launch

            if (currentState.isRunning) return@launch
            _state.update { it.copy(isRunning = true, errorMessage = null) }
            
            if (client.offline) {
                _state.update { it.copy(errorMessage = getString(Res.string.device_offline), isRunning = false) }
                return@launch
            }

            val content = ZeroInterestTransactionEvent(
                description = currentState.description.ifBlank { ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION },
                sender = currentState.sender,
                receivers = currentState.recipientAmountInputs
                    .mapValues { parseAmount(it.value).map(MoneyParser.Result::toMoney).getOrDefault(Money.zero) }
                    .filter { it.value.amount > 0L }
            )

            if (content.receivers.isNotEmpty()) {
                try {
                    transactionService.sendTransaction(roomId, content)
                    onDone()
                } catch (e: TransactionService.FailedPrepareSummaryException) {
                    log.error(e) { "Could not prepare summary creation" }
                    _state.update { it.copy(errorMessage = getString(Res.string.failed_prepare_trust_summary, e.message.toString())) }
                } catch (e: TransactionService.FailedSendMessageException) {
                    log.error(e) { "Could not send transactions" }
                    _state.update { it.copy(errorMessage = getString(Res.string.failed_send_message_with_error, e.message.toString())) }
                } catch (e: Exception) {
                    log.error(e) { "Could not submit summary" }
                    _state.update { it.copy(errorMessage = getString(Res.string.failed_create_trust_summary, e.message.toString())) }
                }
            }
            _state.update { it.copy(isRunning = false) }
        }
    }
    
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
    
    fun isAmountValid(amountStr: String): Boolean {
        return parseAmount(amountStr).isSuccess
    }
}