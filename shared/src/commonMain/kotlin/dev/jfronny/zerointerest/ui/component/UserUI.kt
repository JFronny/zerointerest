package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.avatarUrl
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

sealed interface UserUI {
    @Composable
    operator fun invoke(userId: UserId, iconSize: IconSize = IconSize.Regular) = component(userId)(iconSize = iconSize)

    @Composable
    fun component(userId: UserId): Component

    interface Component {
        @Composable fun Icon(size: IconSize = IconSize.Regular)

        @Composable fun Name() {
            Text(name)
        }
        val name: String

        @Composable
        operator fun invoke(iconSize: IconSize = IconSize.Regular) = Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(size = iconSize)
            Spacer(Modifier.width(8.dp))
            Name()
        }
    }

    companion object {
        @Composable
        operator fun invoke(client: MatrixClient, roomId: RoomId): UserUI {
            val flow = remember(roomId) { client.user.getAll(roomId) }
            val users by flow.collectAsState(emptyMap())
            return remember(users) { UserUIImpl(client, users) }
        }
    }
}

enum class IconSize {
    Small,
    Regular,
    Large,
}

object PreviewUserUI : UserUI {
    @Composable
    override fun component(userId: UserId): UserUI.Component = object : UserUI.Component {
        @Composable
        override fun Icon(size: IconSize) = FallbackIcon(size, userId.localpart)

        override val name: String get() = userId.localpart
    }
}

@Stable
class UserUIImpl(
    private val client: MatrixClient,
    private val users: Map<UserId, Flow<RoomUser?>>,
) : UserUI {
    @Composable
    override fun component(userId: UserId): UserUI.Component {
        val user = remember(userId, users) { users[userId] } ?: return PreviewUserUI.component(userId)
        val state by user.collectAsState(null)
        val name = remember(state) { state?.name ?: userId.full }
        return object : UserUI.Component {
            @Composable
            override fun Icon(size: IconSize) = WebImageOrFallback(size, name, state?.avatarUrl, stringResource(Res.string.avatar))

            override val name: String get() = name
        }
    }
}

@Preview
@Composable
private fun UserUiPreview() = AppTheme {
    PreviewUserUI(UserId("@alice:example.org"), IconSize.Regular)
}

@Preview
@Composable
private fun UserUiPreviewSmall() = AppTheme {
    PreviewUserUI(UserId("@alice:example.org"), IconSize.Small)
}
