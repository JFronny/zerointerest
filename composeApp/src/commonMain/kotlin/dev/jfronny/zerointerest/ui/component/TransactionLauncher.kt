package dev.jfronny.zerointerest.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.TransactionService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val log = KotlinLogging.logger {}

class TransactionLauncher(
    private val client: MatrixClient,
    private val error: MutableState<String?>,
    private val _isRunning: MutableState<Boolean>,
    private val scope: CoroutineScope,
) {
    val isRunning = _isRunning.value

    fun tryLaunch(block: suspend () -> Unit) {
        if (_isRunning.value) return
        _isRunning.value = true
        scope.launch {
            if (client.syncState.value != SyncState.RUNNING) {
                error.value = getString(Res.string.device_offline)
                _isRunning.value = false
                return@launch
            }

            error.value = null
            try {
                block()
            } catch (e: TransactionService.FailedPrepareSummaryException) {
                log.error(e) { "Could not prepare summary creation" }
                error.value = getString(Res.string.failed_prepare_trust_summary, e.message.toString())
                return@launch
            } catch (e: TransactionService.FailedSendMessageException) {
                log.error(e) { "Could not send transactions" }
                error.value = getString(Res.string.failed_send_message_with_error, e.message.toString())
                return@launch
            } catch (e: Exception) {
                log.error(e) { "Could not submit summary" }
                error.value = getString(Res.string.failed_create_trust_summary, e.message.toString())
                return@launch
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun clearError() {
        error.value = null
    }

    @Composable
    fun ErrorDialog(onDismiss: () -> Unit = { clearError() }) {
        val errorMessage = error.value ?: return
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.ok))
                }
            },
            title = { Text(stringResource(Res.string.transaction_failed)) },
            text = { Text(errorMessage) }
        )
    }
}

@Composable
fun rememberTransactionLauncher(client: MatrixClient): TransactionLauncher {
    val error = remember { mutableStateOf<String?>(null) }
    val isRunning = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return TransactionLauncher(client, error, isRunning, scope)
}
