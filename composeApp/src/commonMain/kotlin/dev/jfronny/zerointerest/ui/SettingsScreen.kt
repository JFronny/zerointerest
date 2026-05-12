package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import dev.jfronny.zerointerest.data.money.MonetaryUnit
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

            val debugHints by settings.debugHints.collectAsState(initial = false)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.debug_hints)) },
                supportingContent = { Text(stringResource(Res.string.debug_hints_description)) },
                trailingContent = {
                    Switch(
                        checked = debugHints,
                        onCheckedChange = {
                            scope.launch { settings.setDebugHints(it) }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { settings.setDebugHints(!debugHints) }
                }
            )

            val requestFullKeyboard by settings.requestFullKeyboard.collectAsState(initial = false)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.request_full_keyboard)) },
                supportingContent = { Text(stringResource(Res.string.request_full_keyboard_description)) },
                trailingContent = {
                    Switch(
                        checked = requestFullKeyboard,
                        onCheckedChange = {
                            scope.launch { settings.setRequestFullKeyboard(it) }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { settings.setRequestFullKeyboard(!requestFullKeyboard) }
                }
            )

            val monetaryUnit by settings.monetaryUnit.collectAsState(initial = MonetaryUnit.default)
            var showMonetaryUnitDialog by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(Res.string.monetary_unit)) },
                supportingContent = { Text(stringResource(Res.string.monetary_unit_description)) },
                trailingContent = { Text(monetaryUnit.code, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.clickable { showMonetaryUnitDialog = true }
            )

            if (showMonetaryUnitDialog) {
                var text by remember { mutableStateOf(monetaryUnit.code) }
                var error by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showMonetaryUnitDialog = false },
                    title = { Text(stringResource(Res.string.monetary_unit)) },
                    modifier = Modifier.width(500.dp),
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.monetary_unit_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = text,
                                onValueChange = {
                                    text = it
                                    error = false
                                },
                                isError = error,
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                try {
                                    val unit = MonetaryUnit(text.trim())
                                    scope.launch { settings.setMonetaryUnit(unit) }
                                    showMonetaryUnitDialog = false
                                } catch (e: IllegalArgumentException) {
                                    error = true
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMonetaryUnitDialog = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                    }
                )
            }
            
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
