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
import dev.jfronny.zerointerest.MatrixClientService
import io.ktor.http.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import zerointerest.composeapp.generated.resources.Res
import zerointerest.composeapp.generated.resources.app_icon

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val matrixClient = koinInject<MatrixClientService>()
    var state by remember { mutableStateOf<State>(State.Loading) }

    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var homeserver by remember { mutableStateOf("https://matrix.org") }

    fun tryLogIn(actual: suspend () -> Unit) {
        state = State.Loading
        scope.launch {
            try {
                actual()
                onSuccess()
            } catch (t: Throwable) {
                state = State.Error(t.message ?: "Login failed")
                log.error(t) { "Login failed" }
            }
        }
    }

    LaunchedEffect(matrixClient) {
        tryLogIn {
            matrixClient.restore()
        }
        if (matrixClient.loggedIn) {
            onSuccess()
        } else {
            state = State.Idle
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

        Spacer(modifier = Modifier.height(12.dp))

        val isLoading = state is State.Loading

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = homeserver,
            onValueChange = { homeserver = it },
            label = { Text("Homeserver URL") },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = state is State.Error) {
            Text(
                text = (state as? State.Error)?.message.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    tryLogIn {
                        matrixClient.login(
                            homeserver = Url(homeserver),
                            username = username,
                            password = password
                        )
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
    data class Error(val message: String) : State()
}