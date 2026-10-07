package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WebImageOrFallback(size: IconSize, name: String, url: String?, contentDescription: String) {
    if (url == null) {
        FallbackIcon(size, name)
        return
    }
    IconWrapper(size) {
        WebImage(url, contentDescription)
    }
}

@Composable
fun FallbackIcon(size: IconSize, name: String) {
    val initials = name
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { name.take(2).uppercase() }
    IconWrapper(size) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun IconWrapper(size: IconSize, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(
            when (size) {
                IconSize.Small -> 24.dp
                IconSize.Regular -> 40.dp
                IconSize.Large -> 48.dp
            },
        ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
