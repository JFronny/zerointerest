package dev.jfronny.zerointerest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.service.MatrixClientService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import zerointerest.composeapp.generated.resources.Res
import zerointerest.composeapp.generated.resources.app_icon

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onSuccess: suspend () -> Unit) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("zerointerest") }
        )
    }
) {
    val matrixClient = koinInject<MatrixClientService>()
    var state by remember { mutableStateOf<State>(State.Loading) }

    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var homeserver by remember { mutableStateOf("https://matrix.org") }

    fun tryLogIn(actual: suspend () -> Unit) {
        scope.launch {
            try {
                actual()
            } catch (t: Throwable) {
                state = State.Error(t.message ?: "Login failed")
                log.error(t) { "Login failed" }
            }
        }
    }

    LaunchedEffect(matrixClient) {
        state = State.Restoring
        tryLogIn {
            matrixClient.restore()
            if (matrixClient.loggedIn) {
                onSuccess()
            } else {
                state = State.Idle
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                enabled = state !is State.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = state !is State.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = homeserver,
                onValueChange = { homeserver = it },
                label = { Text("Homeserver URL") },
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
                        matrixClient.login(
                            homeserver = Url(homeserver),
                            username = username,
                            password = password
                        )
                        onSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
        }
    }
}

private sealed class State {
    object Idle : State()
    object Loading : State()
    object Restoring : State()
    data class Error(val message: String) : State()
}