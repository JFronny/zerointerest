package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.jfronny.zerointerest.service.MatrixClientService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveDeviceVerification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

@Composable
fun VerificationDialog() {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient

    if (client != null) {
        val verification by client.verification.activeDeviceVerification.collectAsState()
        var mutVer = verification
        LaunchedEffect(verification) {
            mutVer = verification
            log.info { "Verification changed to $verification" }
        }
        mutVer?.let { VerificationDialog(client, it) { mutVer = null } }
    }
}

@Composable
fun VerificationDialog(
    client: MatrixClient,
    verification: ActiveDeviceVerification,
    onClose: () -> Unit
) {
    val rxstate by verification.state.collectAsState()
    val state = rxstate // to allow smart cast in when
    val coroutineScope = rememberCoroutineScope()
    when (state) {
        is ActiveVerificationState.OwnRequest -> {
            AlertDialog(
                onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                title = { Text("Verification Request Sent") },
                text = { Text("Waiting for other device to accept the verification request.") },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                        Text("Cancel")
                    }
                }
            )
        }

        is ActiveVerificationState.TheirRequest -> {
            AlertDialog(
                onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                title = { Text("Verification Request") },
                text = { Text("Another device is requesting verification.") },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { state.ready() } }) {
                        Text("Accept")
                    }
                },
                dismissButton = {
                    Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                        Text("Decline")
                    }
                }
            )
        }

        is ActiveVerificationState.Ready -> {
            val sas = state.methods.firstNotNullOfOrNull { it as? VerificationMethod.Sas }
            if (sas != null) {
                coroutineScope.launch {
                    state.start(sas)
                }
            } else {
                AlertDialog(
                    onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                    title = { Text("Choose Verification Method") },
                    text = {
                        Column {
                            state.methods.forEach { method ->
                                Button(onClick = { coroutineScope.launch { state.start(method) } }) {
                                    Text(method.toString())
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }

        is ActiveVerificationState.Start -> {
            // For now, we only handle SAS verification
            val sas = state.method as? ActiveSasVerificationMethod
            if (sas != null) {
                val sasState by sas.state.collectAsState()
                when (val activeSasState = sasState) {
                    is ActiveSasVerificationState.ComparisonByUser -> {
                        AlertDialog(
                            onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                            title = { Text("Compare") },
                            text = {
                                Text(
                                    "Compare this emoji sequence with the other device:\n" +
                                            activeSasState.emojis.joinToString(" ") { it.second }
                                )
                            },
                            confirmButton = {
                                Button(onClick = { coroutineScope.launch { activeSasState.match() } }) {
                                    Text("Match")
                                }
                            },
                            dismissButton = {
                                Button(onClick = { coroutineScope.launch { activeSasState.noMatch() } }) {
                                    Text("Mismatch")
                                }
                            }
                        )
                    }

                    else -> {
                        AlertDialog(
                            onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                            title = { Text("Verification in Progress") },
                            text = { Text("Please follow the instructions on your other device.") },
                            confirmButton = {
                                Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }

        is ActiveVerificationState.WaitForDone -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Waiting for other device") },
                text = { Text("Waiting for the other device to finish the verification process.") },
                confirmButton = {}
            )
        }

        ActiveVerificationState.Done -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Verification Successful") },
                text = { Text("Your device has been successfully verified.") },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text("OK")
                    }
                }
            )
        }

        is ActiveVerificationState.Cancel -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Verification Cancelled") },
                text = { Text("The verification was cancelled by ${if (state.isOurOwn) "you" else "the other device"}.\nReason: ${state.content.reason}") },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text("OK")
                    }
                }
            )
        }

        ActiveVerificationState.AcceptedByOtherDevice -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Verification Accepted") },
                text = { Text("Another device has accepted the verification request.") },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text("OK")
                    }
                }
            )
        }

        ActiveVerificationState.Undefined -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("Error") },
                text = { Text("An unexpected error occurred during verification.") },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
