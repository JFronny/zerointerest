package dev.jfronny.zerointerest.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.TransactionService
import dev.jfronny.zerointerest.service.client.ZiClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val log = KotlinLogging.logger {}

class TransactionLauncher(
    private val client: ZiClient,
    private val state: MutableState<State>,
    private val scope: CoroutineScope,
) {
    var isRunning get() = state.value.isRunning
        private set(value) {
            state.value = state.value.copy(isRunning = value)
        }
    var error: String? get() = state.value.error
        private set(value) {
            state.value = state.value.copy(error = value)
        }

    fun tryLaunch(block: suspend () -> Unit) {
        if (isRunning) return
        isRunning = true
        scope.launch {
            if (client.offline) {
                error = getString(Res.string.device_offline)
                isRunning = false
                return@launch
            }

            error = null
            try {
                block()
            } catch (e: Exception) {
                error = getString(logAndLocalize(e), e.message ?: "")
                return@launch
            } finally {
                isRunning = false
            }
        }
    }

    fun clearError() {
        error = null
    }

    @Composable
    fun ErrorDialog(onDismiss: () -> Unit = { clearError() }) {
        val errorMessage = state.value.error ?: return
        ErrorDialog(errorMessage, onDismiss)
    }

    data class State(
        val isRunning: Boolean,
        val error: String?
    )

    companion object {
        fun logAndLocalize(e: Exception) = when (e) {
            is TransactionService.FailedPrepareSummaryException -> {
                log.error(e) { "Could not prepare summary creation" }
                Res.string.failed_prepare_trust_summary
            }
            is TransactionService.FailedSendMessageException -> {
                log.error(e) { "Could not send transactions" }
                Res.string.failed_send_message_with_error
            }
            else -> {
                log.error(e) { "Could not submit summary" }
                Res.string.failed_create_trust_summary
            }
        }
    }
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.ok))
            }
        },
        title = { Text(stringResource(Res.string.transaction_failed)) },
        text = { Text(message) }
    )
}

@Composable
fun rememberTransactionLauncher(client: ZiClient): TransactionLauncher {
    val state = remember { mutableStateOf(TransactionLauncher.State(false, null)) }
    val scope = rememberCoroutineScope()
    return TransactionLauncher(client, state, scope)
}
