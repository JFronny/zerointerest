package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.jfronny.zerointerest.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MoreOptionsButton(
    openSettings: () -> Unit,
    extraOptions: @Composable (ColumnScope.(close: () -> Unit) -> Unit) = {}
) = Box {
    var expanded by remember { mutableStateOf(false) }

    SimpleIconButton(
        icon = Icons.Default.MoreVert,
        description = stringResource(Res.string.more_options),
        onClick = { expanded = true }
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.settings)) },
            onClick = {
                expanded = false
                openSettings()
            }
        )
        extraOptions({ expanded = false })
    }
}

@Composable
fun BackButton(onBack: () -> Unit) = SimpleIconButton(
    icon = Icons.AutoMirrored.Default.ArrowBack,
    description = stringResource(Res.string.back),
    onClick = onBack
)

@Composable
fun SimpleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { Text(description) },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    }
}
