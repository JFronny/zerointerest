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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import zerointerest.composeapp.generated.resources.*

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
                title = { Text(stringResource(Res.string.verification_request_sent)) },
                text = { Text(stringResource(Res.string.waiting_for_other_device_accept)) },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            )
        }

        is ActiveVerificationState.TheirRequest -> {
            AlertDialog(
                onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                title = { Text(stringResource(Res.string.verification_request)) },
                text = { Text(stringResource(Res.string.another_device_requesting_verification)) },
                confirmButton = {
                    Button(onClick = { coroutineScope.launch { state.ready() } }) {
                        Text(stringResource(Res.string.accept))
                    }
                },
                dismissButton = {
                    Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                        Text(stringResource(Res.string.decline))
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
                    title = { Text(stringResource(Res.string.choose_verification_method)) },
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
                            title = { Text(stringResource(Res.string.compare)) },
                            text = {
                                Text(
                                    stringResource(
                                        Res.string.compare_emoji_sequence,
                                        activeSasState.emojis.joinToString(" ") { it.second }
                                    )
                                )
                            },
                            confirmButton = {
                                Button(onClick = { coroutineScope.launch { activeSasState.match() } }) {
                                    Text(stringResource(Res.string.match))
                                }
                            },
                            dismissButton = {
                                Button(onClick = { coroutineScope.launch { activeSasState.noMatch() } }) {
                                    Text(stringResource(Res.string.mismatch))
                                }
                            }
                        )
                    }

                    else -> {
                        AlertDialog(
                            onDismissRequest = { coroutineScope.launch { verification.cancel() } },
                            title = { Text(stringResource(Res.string.verification_in_progress)) },
                            text = { Text(stringResource(Res.string.follow_instructions_other_device)) },
                            confirmButton = {
                                Button(onClick = { coroutineScope.launch { verification.cancel() } }) {
                                    Text(stringResource(Res.string.cancel))
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
                title = { Text(stringResource(Res.string.waiting_for_other_device)) },
                text = { Text(stringResource(Res.string.waiting_for_other_device_finish)) },
                confirmButton = {}
            )
        }

        ActiveVerificationState.Done -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(Res.string.verification_successful)) },
                text = { Text(stringResource(Res.string.device_verified_successfully)) },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text(stringResource(Res.string.ok))
                    }
                }
            )
        }

        is ActiveVerificationState.Cancel -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(Res.string.verification_cancelled)) },
                text = {
                    val who = if (state.isOurOwn) stringResource(Res.string.you) else stringResource(Res.string.the_other_device)
                    Text(stringResource(Res.string.verification_cancelled_by, who, state.content.reason))
                },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text(stringResource(Res.string.ok))
                    }
                }
            )
        }

        ActiveVerificationState.AcceptedByOtherDevice -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(Res.string.verification_accepted)) },
                text = { Text(stringResource(Res.string.another_device_accepted_verification)) },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text(stringResource(Res.string.ok))
                    }
                }
            )
        }

        ActiveVerificationState.Undefined -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text(stringResource(Res.string.error)) },
                text = { Text(stringResource(Res.string.unexpected_error_verification)) },
                confirmButton = {
                    Button(onClick = onClose) {
                        Text(stringResource(Res.string.ok))
                    }
                }
            )
        }
    }
}
