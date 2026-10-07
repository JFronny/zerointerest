package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ForkRight
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
import androidx.compose.ui.tooling.preview.Preview
import dev.jfronny.zerointerest.SourceCodeUrl
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.component.BackButton
import dev.jfronny.zerointerest.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val settings = koinInject<Settings>()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val flipBalances by settings.flipBalances.collectAsState(initial = true)
    val debugHints by settings.debugHints.collectAsState(initial = false)
    val requestFullKeyboard by settings.requestFullKeyboard.collectAsState(initial = false)

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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.flip_balances)) },
                supportingContent = { Text(stringResource(Res.string.flip_balances_description)) },
                trailingContent = {
                    Switch(
                        checked = flipBalances,
                        onCheckedChange = setFlipBalances,
                    )
                },
                modifier = Modifier.clickable { setFlipBalances(!flipBalances) },
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.debug_hints)) },
                supportingContent = { Text(stringResource(Res.string.debug_hints_description)) },
                trailingContent = {
                    Switch(
                        checked = debugHints,
                        onCheckedChange = setDebugHints,
                    )
                },
                modifier = Modifier.clickable { setDebugHints(!debugHints) },
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.request_full_keyboard)) },
                supportingContent = { Text(stringResource(Res.string.request_full_keyboard_description)) },
                trailingContent = {
                    Switch(
                        checked = requestFullKeyboard,
                        onCheckedChange = setRequestFullKeyboard,
                    )
                },
                modifier = Modifier.clickable { setRequestFullKeyboard(!requestFullKeyboard) },
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.source_code)) },
                supportingContent = { Text(SourceCodeUrl) },
                leadingContent = { Icon(Icons.Default.ForkRight, null) },
                modifier = Modifier.clickable { onViewSourceCode() },
            )

            ListItem(
                headlineContent = { Text(stringResource(Res.string.logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                modifier = Modifier.clickable { onLogout() },
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
    )
}
