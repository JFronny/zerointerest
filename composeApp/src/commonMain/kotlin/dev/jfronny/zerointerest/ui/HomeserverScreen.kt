package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.Settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * List of suggested homeservers.
 * Extend this list to add more suggestions.
 */
val suggestedHomeservers = listOf(
    "https://matrix.org",
    "https://matrix.scc.kit.edu"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeserverScreen(onContinue: (homeserver: String) -> Unit) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.app_name)) }
        )
    }
) { paddingValues ->
    val settings = koinInject<Settings>()
    var homeserver by remember { mutableStateOf(Settings.FALLBACK_HOMESERVER) }

    LaunchedEffect(settings) {
        homeserver = settings.defaultHomeserver()
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.select_homeserver),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = homeserver,
            onValueChange = { homeserver = it },
            label = { Text(stringResource(Res.string.homeserver_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onContinue(homeserver) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.continue_button))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.suggested_homeservers),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn {
                items(suggestedHomeservers) { server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                homeserver = server
                                onContinue(server) 
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = server,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                    if (server != suggestedHomeservers.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val urlHandler = LocalUriHandler.current
        OutlinedButton(
            onClick = { urlHandler.openUri(SourceCodeUrl) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.source_code))
        }
    }
}
