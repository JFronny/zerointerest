package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.service.TransactionService
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.ui.theme.AppTheme
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val log = KotlinLogging.logger {}

class TransactionLauncher(
    private val client: ZiClient,
    state: MutableState<State>,
    private val scope: CoroutineScope,
) {
    private val _state = state

    val state get() = _state.value
    var isRunning get() = state.isRunning
        private set(value) {
            _state.value = _state.value.copy(isRunning = value)
        }
    var error: String? get() = state.error
        private set(value) {
            _state.value = _state.value.copy(error = value)
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
    fun ErrorDialog(onDismiss: () -> Unit = { clearError() }) = ErrorDialog(state, onDismiss)

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
fun ErrorDialog(state: TransactionLauncher.State, onDismiss: () -> Unit) {
    val errorMessage = state.error ?: return
    ErrorDialog(errorMessage, onDismiss)
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

@Preview
@Composable
private fun ErrorDialogPreview() = AppTheme {
    Box(Modifier.fillMaxSize()) {
        ErrorDialog(message = "Error message", onDismiss = {})
    }
}

@Composable
fun rememberTransactionLauncher(client: ZiClient): TransactionLauncher {
    val state = remember { mutableStateOf(TransactionLauncher.State(false, null)) }
    val scope = rememberCoroutineScope()
    return TransactionLauncher(client, state, scope)
}
