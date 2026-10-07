package dev.jfronny.zerointerest.ui.component

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog to enter a monetary unit, e.g. the currency of a room.
 */
@Composable
fun MonetaryUnitDialog(
    title: String,
    current: MonetaryUnit,
    onSave: (MonetaryUnit) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(current.code) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        modifier = Modifier.width(500.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.monetary_unit_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = {
                        text = it
                        error = false
                    },
                    isError = error,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        onSave(MonetaryUnit(text.trim()))
                    } catch (e: IllegalArgumentException) {
                        error = true
                    }
                },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Preview
@Composable
private fun MonetaryUnitDialogPreview() = AppTheme {
    Box(Modifier.fillMaxSize()) {
        MonetaryUnitDialog(
            title = stringResource(Res.string.room_currency),
            current = MonetaryUnit.default,
            onSave = {},
            onClose = {},
        )
    }
}
