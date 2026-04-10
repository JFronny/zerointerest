package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.AndroidUiModes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.theme.AppTheme
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val log = KotlinLogging.logger {}

@Composable
fun LoadingScreen(onSuccess: suspend () -> Unit, onError: suspend () -> Unit) {
    val settings = koinInject<Settings>()
    val matrixClient = koinInject<MatrixClientService>()

    LaunchedEffect(matrixClient) {
        val homeserver = settings.defaultHomeserver()
        try {
            log.info { "Trying to restore session with homeserver $homeserver" }
            matrixClient.restore()
            if (matrixClient.loggedIn) {
                settings.setDefaultHomeserver(homeserver)
                log.info { "Successfully restored session with homeserver $homeserver" }
                onSuccess()
            } else {
                log.error { "Failed to restore session with homeserver $homeserver" }
                onError()
            }
        } catch (e: Throwable) {
            log.error(e) { "Failed to restore session with homeserver $homeserver" }
            onError()
        }
    }

    LoadingScreenUi()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingScreenUi() = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.login)) },
        )
    }
) { paddingValues ->
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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(Modifier.size(128.dp))
        }
    }
}

@Preview
@Composable
private fun LoadingScreenPreview() = AppTheme {
    LoadingScreenUi()
}

@Preview(uiMode = AndroidUiModes.UI_MODE_NIGHT_YES)
@Composable
private fun LoadingScreenPreviewDark() = AppTheme {
    LoadingScreenUi()
}
