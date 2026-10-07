package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.data.ZiConfigStateEvent
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Data passed into the room info sheet for display and actions.
 */
@Immutable
data class RoomInfoSheetData(
    val roomId: RoomId,
    val name: String,
    val avatarUrl: String?,
    val topic: String,
    val isFavorite: Boolean,
    val roomConfig: ZiConfigStateEvent,
    val debugHints: Boolean,
)

@Composable
fun rememberRoomInfoSheetData(roomId: RoomId, room: Room?, config: ZiConfigStateEvent, topic: String): RoomInfoSheetData {
    val settings = koinInject<Settings>()
    val debugHints by settings.debugHints.collectAsState(initial = false)
    val favoriteRooms by settings.favoriteRooms.collectAsState(initial = emptyList())
    val defaultRoomName = stringResource(Res.string.room)
    return remember(roomId, room?.name?.explicitName, room?.avatarUrl, config, topic, debugHints, favoriteRooms, defaultRoomName) {
        RoomInfoSheetData(
            roomId = roomId,
            name = room?.name?.explicitName ?: defaultRoomName,
            avatarUrl = room?.avatarUrl,
            topic = topic,
            isFavorite = favoriteRooms.any { it == roomId },
            roomConfig = config,
            debugHints = debugHints,
        )
    }
}

/**
 * Modal bottom sheet showing room information and quick actions.
 *
 * @param data The room data to display.
 * @param onDismiss Request to dismiss the sheet.
 * @param onToggleFavorite Callback to toggle the favorite status.
 * @param onSettleUp Callback to navigate to the settle-up screen.
 * @param onSetCurrency Callback to open the currency selection dialog.
 * @param onUpgradeRoom Callback to open the room upgrade dialog.
 * @param onDebugNewSummary Callback to trigger a debug summary creation.
 * @param onDebugResetTrust Callback to reset trust for this room.
 * @param onOpenSettings Callback to open the app settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomInfoSheet(
    data: RoomInfoSheetData,
    onDismiss: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onSetTopic: (String) -> Unit,
    onSettleUp: () -> Unit,
    onSetCurrency: () -> Unit,
    onUpgradeRoom: () -> Unit,
    onDebugNewSummary: () -> Unit,
    onDebugResetTrust: () -> Unit,
    onOpenSettings: () -> Unit,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
) {
    val scope = rememberCoroutineScope()

    ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).padding(end = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WebImageOrFallback(IconSize.Large, data.name, data.avatarUrl, stringResource(Res.string.avatar))
                }

                Text(
                    text = data.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    16.dp,
                    Alignment.CenterHorizontally,
                ),
            ) {
                QuickActionItem(
                    icon = if (data.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    label = stringResource(
                        if (data.isFavorite) Res.string.unfavorite_room else Res.string.favorite_room,
                    ),
                    onClick = {
                        scope.launch { onToggleFavorite(!data.isFavorite) }
                    },
                )

                QuickActionItem(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = stringResource(Res.string.settle_up),
                    onClick = {
                        onDismiss()
                        onSettleUp()
                    },
                )
            }

            TopicSection(currentTopic = data.topic, onSave = onSetTopic)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            when (data.roomConfig) {
                is ZiConfigStateEvent.V1 -> {
                    ActionItem(
                        icon = Icons.Default.Money,
                        label = stringResource(Res.string.room_currency),
                        onClick = {
                            onDismiss()
                            onSetCurrency()
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                is ZiConfigStateEvent.V0 -> {
                    ActionItem(
                        icon = Icons.Default.Upgrade,
                        label = stringResource(Res.string.upgrade_room),
                        onClick = {
                            onDismiss()
                            onUpgradeRoom()
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                is ZiConfigStateEvent.Newer, ZiConfigStateEvent.Unknown -> Unit
            }

            if (data.debugHints) {
                ActionItem(
                    icon = Icons.Default.BugReport,
                    label = stringResource(Res.string.debug_new_summary),
                    onClick = {
                        onDismiss()
                        onDebugNewSummary()
                    },
                )
                ActionItem(
                    icon = Icons.Default.BugReport,
                    label = stringResource(Res.string.debug_reset_trust),
                    onClick = {
                        onDismiss()
                        onDebugResetTrust()
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            ActionItem(
                icon = Icons.Default.Settings,
                label = stringResource(Res.string.settings),
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RoomInfoSheetPreview() = AppTheme {
    Box(Modifier.fillMaxSize()) {
        RoomInfoSheet(
            data = RoomInfoSheetData(
                roomId = RoomId("!room1:example.com"),
                name = "Demo Room",
                topic = "",
                avatarUrl = null,
                isFavorite = true,
                roomConfig = ZiConfigStateEvent.V0(),
                debugHints = true,
            ),
            onDismiss = {},
            onToggleFavorite = {},
            onSetTopic = {},
            onSettleUp = {},
            onSetCurrency = {},
            onUpgradeRoom = {},
            onDebugNewSummary = {},
            onDebugResetTrust = {},
            onOpenSettings = {},
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RoomInfoSheetPreviewAlt() = AppTheme {
    Box(Modifier.fillMaxSize()) {
        RoomInfoSheet(
            data = RoomInfoSheetData(
                roomId = RoomId("!room1:example.com"),
                name = "Demo Room",
                topic = "",
                avatarUrl = null,
                isFavorite = false,
                roomConfig = ZiConfigStateEvent.V1(),
                debugHints = false,
            ),
            onDismiss = {},
            onToggleFavorite = {},
            onSetTopic = {},
            onSettleUp = {},
            onSetCurrency = {},
            onUpgradeRoom = {},
            onDebugNewSummary = {},
            onDebugResetTrust = {},
            onOpenSettings = {},
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded),
        )
    }
}

@Composable
private fun TopicSection(
    currentTopic: String,
    onSave: (String) -> Unit,
) {
    var editText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.room_topic),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (isEditing) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default,
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = {
                    isEditing = false
                    editText = ""
                }) {
                    Text(stringResource(Res.string.cancel_topic))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    isEditing = false
                    onSave(editText)
                    editText = ""
                }) {
                    Text(stringResource(Res.string.save_topic))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editText = currentTopic
                        isEditing = true
                    }
                    .padding(12.dp),
            ) {
                Text(
                    text = currentTopic.ifBlank { stringResource(Res.string.add_topic) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentTopic.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
