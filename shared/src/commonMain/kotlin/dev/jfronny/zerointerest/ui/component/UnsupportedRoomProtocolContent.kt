package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Error content for rooms that use a zerointerest protocol version newer than this client supports.
 * No room information is shown and no writes are allowed for such rooms.
 */
@Composable
fun UnsupportedRoomProtocolContent(
    version: Int?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    Res.string.room_protocol_too_new,
                    version?.toString() ?: stringResource(Res.string.version_unknown),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview
@Composable
private fun UnsupportedRoomProtocolContentPreview() = AppTheme {
    Box(Modifier.size(300.dp)) {
        UnsupportedRoomProtocolContent(version = 2)
    }
}
