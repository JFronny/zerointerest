package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val settings = koinInject<Settings>()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val flipBalances by settings.flipBalances.collectAsState(initial = true)
    val debugHints by settings.debugHints.collectAsState(initial = false)
    val requestFullKeyboard by settings.requestFullKeyboard.collectAsState(initial = false)

    val monetaryUnit by settings.monetaryUnit.collectAsState(initial = MonetaryUnit.default)

    SettingsContent(
        onBack = onBack,
        onLogout = onLogout,
        onViewSourceCode = { uriHandler.openUri(SourceCodeUrl) },
        flipBalances = flipBalances,
        setFlipBalances = { scope.launch { settings.setFlipBalances(it) } },
        debugHints = debugHints,
        setDebugHints = { scope.launch { settings.setDebugHints(it) } },
        requestFullKeyboard = requestFullKeyboard,
        setRequestFullKeyboard = { scope.launch { settings.setRequestFullKeyboard(it) } },
        monetaryUnit = monetaryUnit,
        setMonetaryUnit = { scope.launch { settings.setMonetaryUnit(it) } },
    )
}

@Composable
private fun SettingsContent(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onViewSourceCode: () -> Unit,

    flipBalances: Boolean,
    setFlipBalances: (Boolean) -> Unit,
    debugHints: Boolean,
    setDebugHints: (Boolean) -> Unit,
    requestFullKeyboard: Boolean,
    setRequestFullKeyboard: (Boolean) -> Unit,
    monetaryUnit: MonetaryUnit,
    setMonetaryUnit: (MonetaryUnit) -> Unit,
) {

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
            ListItem(
                headlineContent = { Text(stringResource(Res.string.flip_balances)) },
                supportingContent = { Text(stringResource(Res.string.flip_balances_description)) },
                trailingContent = {
                    Switch(
                        checked = flipBalances,
                        onCheckedChange = setFlipBalances
                    )
                },
                modifier = Modifier.clickable { setFlipBalances(!flipBalances) }
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.debug_hints)) },
                supportingContent = { Text(stringResource(Res.string.debug_hints_description)) },
                trailingContent = {
                    Switch(
                        checked = debugHints,
                        onCheckedChange = setDebugHints
                    )
                },
                modifier = Modifier.clickable { setDebugHints(!debugHints) }
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.request_full_keyboard)) },
                supportingContent = { Text(stringResource(Res.string.request_full_keyboard_description)) },
                trailingContent = {
                    Switch(
                        checked = requestFullKeyboard,
                        onCheckedChange = setRequestFullKeyboard
                    )
                },
                modifier = Modifier.clickable { setRequestFullKeyboard(!requestFullKeyboard) }
            )

            var showMonetaryUnitDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(Res.string.monetary_unit)) },
                supportingContent = { Text(stringResource(Res.string.monetary_unit_description)) },
                trailingContent = { Text(monetaryUnit.code, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.clickable { showMonetaryUnitDialog = true }
            )

            if (showMonetaryUnitDialog) {
                MonetaryUnitDialog(
                    monetaryUnit = monetaryUnit,
                    setMonetaryUnit = setMonetaryUnit,
                    onClose = { showMonetaryUnitDialog = false }
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(Res.string.source_code)) },
                supportingContent = { Text(SourceCodeUrl) },
                leadingContent = { Icon(Icons.Default.ForkRight, null) },
                modifier = Modifier.clickable { onViewSourceCode() }
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                modifier = Modifier.clickable { onLogout() }
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() = AppTheme {
    SettingsContent(
        onBack = {},
        onLogout = {},
        onViewSourceCode = {},
        flipBalances = true,
        setFlipBalances = {},
        debugHints = true,
        setDebugHints = {},
        requestFullKeyboard = true,
        setRequestFullKeyboard = {},
        monetaryUnit = MonetaryUnit.default,
        setMonetaryUnit = {},
    )
}

@Composable
private fun MonetaryUnitDialog(
    monetaryUnit: MonetaryUnit,
    setMonetaryUnit: (MonetaryUnit) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(monetaryUnit.code) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
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
                        setMonetaryUnit(unit)
                        onClose()
                    } catch (e: IllegalArgumentException) {
                        error = true
                    }
                }
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Preview
@Composable
private fun MonetaryUnitDialogPreview() = AppTheme {
    Box(Modifier.fillMaxSize()) {
        MonetaryUnitDialog(
            monetaryUnit = MonetaryUnit.default,
            setMonetaryUnit = {},
            onClose = {}
        )
    }
}
