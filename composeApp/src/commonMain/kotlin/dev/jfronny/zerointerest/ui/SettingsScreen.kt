package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.component.BackButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val settings = koinInject<Settings>()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    BackButton(onBack = onBack)
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val flipBalances by settings.flipBalances.collectAsState(initial = true)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.flip_balances)) },
                supportingContent = { Text(stringResource(Res.string.flip_balances_description)) },
                trailingContent = {
                    Switch(
                        checked = flipBalances,
                        onCheckedChange = { 
                            scope.launch { settings.setFlipBalances(it) } 
                        }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { settings.setFlipBalances(!flipBalances) }
                }
            )
            
            ListItem(
                headlineContent = { Text(stringResource(Res.string.source_code)) },
                supportingContent = { Text(SourceCodeUrl) },
                leadingContent = { Icon(Icons.Default.ForkRight, null) },
                modifier = Modifier.clickable { uriHandler.openUri(SourceCodeUrl) }
            )
            
            ListItem(
                headlineContent = { Text(stringResource(Res.string.logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                modifier = Modifier.clickable { onLogout() }
            )
        }
    }
}
