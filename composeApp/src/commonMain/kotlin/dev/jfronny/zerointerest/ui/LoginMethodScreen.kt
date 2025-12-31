package dev.jfronny.zerointerest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import kotlinx.coroutines.launch
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

private sealed class LoginState {
    object Loading : LoginState()
    object Restoring : LoginState()
    object Idle : LoginState()
    data class Error(val message: String) : LoginState()
}

private enum class LoginMethod {
    PASSWORD, SSO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginMethodScreen(
    homeserver: String,
    onBack: () -> Unit,
    onSuccess: suspend () -> Unit
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.login)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            }
        )
    }
) { paddingValues ->
    val scope = rememberCoroutineScope()
    val matrixClient = koinInject<MatrixClientService>()
    val settings = koinInject<Settings>()
    
    var state by remember { mutableStateOf<LoginState>(LoginState.Loading) }
    var availableLoginMethods by remember { mutableStateOf<List<LoginType>>(emptyList()) }
    var ssoProviders by remember { mutableStateOf<List<LoginType.SSO.IdentityProvider>>(emptyList()) }
    
    // Form states
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    
    // Tab state
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    // Determine available login methods
    val hasPasswordLogin = remember(availableLoginMethods) {
        availableLoginMethods.any { it is LoginType.Password }
    }
    val hasSsoLogin = remember(availableLoginMethods) {
        availableLoginMethods.any { it is LoginType.SSO }
    }
    val tabs = remember(hasPasswordLogin, hasSsoLogin) {
        buildList {
            if (hasPasswordLogin) add(LoginMethod.PASSWORD)
            if (hasSsoLogin) add(LoginMethod.SSO)
        }
    }

    fun tryAction(actual: suspend () -> Unit) {
        scope.launch {
            try {
                actual()
            } catch (t: Throwable) {
                state = LoginState.Error(t.message ?: getString(Res.string.login_failed))
                log.error(t) { "Login action failed" }
            }
        }
    }

    LaunchedEffect(matrixClient, homeserver) {
        state = LoginState.Restoring
        tryAction {
            // Fetch available login types
            val loginTypes = matrixClient.getLoginTypes(Url(homeserver))
            availableLoginMethods = loginTypes

            // Extract SSO providers from all SSO login types
            ssoProviders = loginTypes
                .filterIsInstance<LoginType.SSO>()
                .flatMap { it.identityProviders }

            state = LoginState.Idle
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
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = homeserver,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is LoginState.Restoring, is LoginState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                // Show error if any
                AnimatedVisibility(visible = state is LoginState.Error) {
                    Text(
                        text = (state as? LoginState.Error)?.message.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (tabs.size > 1) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, method ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        when (method) {
                                            LoginMethod.PASSWORD -> stringResource(Res.string.password_login)
                                            LoginMethod.SSO -> stringResource(Res.string.sso_login)
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                val currentMethod = tabs.getOrNull(selectedTabIndex) ?: LoginMethod.PASSWORD

                when (currentMethod) {
                    LoginMethod.PASSWORD -> {
                        PasswordLoginForm(
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            useToken = useToken,
                            onUseTokenChange = { useToken = it },
                            token = token,
                            onTokenChange = { token = it },
                            enabled = state !is LoginState.Loading,
                            onLogin = {
                                state = LoginState.Loading
                                tryAction {
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
                            }
                        )
                    }
                    LoginMethod.SSO -> {
                        SsoLoginForm(
                            ssoProviders = ssoProviders,
                            enabled = state !is LoginState.Loading,
                            onSsoLogin = { providerId ->
                                state = LoginState.Loading
                                tryAction {
                                    matrixClient.loginWithSso(
                                        homeserver = Url(homeserver),
                                        idpId = providerId
                                    )
                                    settings.setDefaultHomeserver(homeserver)
                                    onSuccess()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordLoginForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    useToken: Boolean,
    onUseTokenChange: (Boolean) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    enabled: Boolean,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Toggle between password and token login
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(Res.string.use_access_token), modifier = Modifier.weight(1f))
            Switch(
                checked = useToken,
                onCheckedChange = onUseTokenChange,
                enabled = enabled
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (useToken) {
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text(stringResource(Res.string.access_token)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(Res.string.username)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(Res.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Text(stringResource(Res.string.login))
        }
    }
}

@Composable
private fun SsoLoginForm(
    ssoProviders: List<LoginType.SSO.IdentityProvider>,
    enabled: Boolean,
    onSsoLogin: (providerId: String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (ssoProviders.isEmpty()) {
            // No specific providers, show a generic SSO button
            Button(
                onClick = { onSsoLogin(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(stringResource(Res.string.login_with_sso))
            }
        } else {
            Text(
                text = stringResource(Res.string.choose_sso_provider),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn {
                items(ssoProviders) { provider ->
                    OutlinedButton(
                        onClick = { onSsoLogin(provider.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        enabled = enabled
                    ) {
                        Text(provider.name)
                    }
                }
            }
        }
    }
}
