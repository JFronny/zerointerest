package dev.jfronny.zerointerest.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.jfronny.zerointerest.composeapp.generated.resources.Res
import dev.jfronny.zerointerest.composeapp.generated.resources.ic_client_placeholder
import org.jetbrains.compose.resources.painterResource

@Composable
fun WebImage(url: String?, contentDescription: String? = null, modifier: Modifier = Modifier.clip(RoundedCornerShape(6.dp)), contentScale: ContentScale = ContentScale.Fit) {
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) {
        Image(
            painter = painterResource(Res.drawable.ic_client_placeholder),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
        )
    } else {
        if (state is AsyncImagePainter.State.Loading) {
            LoadingAnimation(MaterialTheme.colorScheme.onBackground)
        }

        val transition by animateFloatAsState(if (state is AsyncImagePainter.State.Success) 1f else 0f)
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.alpha(transition)
        )
    }
}

@Composable
fun LoadingAnimation(color: Color) {
    val animation = rememberInfiniteTransition()
    val progress by animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart,
        )
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .scale(progress)
            .alpha(1f - progress)
            .border(
                5.dp,
                color = color,
                shape = CircleShape
            )
    )
}
