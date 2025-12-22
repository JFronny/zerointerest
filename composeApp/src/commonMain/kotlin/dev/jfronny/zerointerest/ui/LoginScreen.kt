package dev.jfronny.zerointerest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onSuccess: suspend () -> Unit) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.app_name)) }
        )
    }
) { paddingValues ->
    val matrixClient = koinInject<MatrixClientService>()
    val settings = koinInject<Settings>()
    var state by remember { mutableStateOf<State>(State.Loading) }

    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var homeserver by remember { mutableStateOf(Settings.FALLBACK_HOMESERVER) }

    // Added states for token login
    var useToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        homeserver = settings.defaultHomeserver()
    }

    fun tryLogIn(actual: suspend () -> Unit) {
        scope.launch {
            try {
                actual()
            } catch (t: Throwable) {
                state = State.Error(t.message ?: getString(Res.string.login_failed))
                log.error(t) { "Login failed" }
            }
        }
    }

    LaunchedEffect(matrixClient) {
        state = State.Restoring
        tryLogIn {
            matrixClient.restore()
            if (matrixClient.loggedIn) {
                settings.setDefaultHomeserver(homeserver)
                onSuccess()
            } else {
                state = State.Idle
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .safeContentPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )

        if (state !is State.Restoring) {
            Spacer(modifier = Modifier.height(12.dp))

            // Toggle between password and token login
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.use_access_token), modifier = Modifier.weight(1f))
                Switch(
                    checked = useToken,
                    onCheckedChange = { useToken = it },
                    enabled = state !is State.Loading
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (useToken) {
                // Token field
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(Res.string.access_token)) },
                    enabled = state !is State.Loading,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.username)) },
                    enabled = state !is State.Loading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = state !is State.Loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = homeserver,
                onValueChange = { homeserver = it },
                label = { Text(stringResource(Res.string.homeserver_url)) },
                enabled = state !is State.Loading,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = state is State.Error) {
            Text(
                text = (state as? State.Error)?.message.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state is State.Loading || state is State.Restoring) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    state = State.Loading
                    tryLogIn {
                        if (useToken) {
                            matrixClient.loginWithToken(
                                homeserver = Url(homeserver),
                                token = token
                            )
                        } else {
                            matrixClient.loginWithPassword(
                                homeserver = Url(homeserver),
                                username = username,
                                password = password
                            )
                        }
                        settings.setDefaultHomeserver(homeserver)
                        onSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.login))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val urlHandler = LocalUriHandler.current
        OutlinedButton(
            onClick = {
                urlHandler.openUri(SourceCodeUrl)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.source_code))
        }
    }
}

private sealed class State {
    object Idle : State()
    object Loading : State()
    object Restoring : State()
    data class Error(val message: String) : State()
}