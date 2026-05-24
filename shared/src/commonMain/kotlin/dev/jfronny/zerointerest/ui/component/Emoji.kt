package dev.jfronny.zerointerest.ui.component

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.kodein.emoji.Emoji
import org.kodein.emoji.EmojiFinder
import org.kodein.emoji.EmojiTemplateCatalog
import org.kodein.emoji.codePoints
import org.kodein.emoji.findEmoji
import org.kodein.emoji.list
import kotlin.jvm.JvmInline

/**
 * Based on the compose support in https://github.com/kosi-libs/Emoji.kt
 *
 * Adapted here to support the kotlin JS target and to use [WebImage]
 */

/**
 * Displays a `String` containing Emoji characters.
 * Replaces all emojis with [NotoImageEmoji].
 *
 * @see androidx.compose.material3.Text
 */
@Composable
fun TextWithEmoji(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    WithNotoImageEmoji(
        text = text,
    ) { emojiAnnotatedString, emojiInlineContent ->
        Text(
            text = emojiAnnotatedString,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = emojiInlineContent,
            onTextLayout = onTextLayout,
            style = style
        )
    }
}

/**
 * Displays an `AnnotatedString` containing Emoji characters.
 * Replaces all emojis with [NotoImageEmoji].
 *
 * @see androidx.compose.material3.Text
 */
@Composable
fun TextWithEmoji(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    WithNotoImageEmoji(
        text = text,
    ) { emojiAnnotatedString, emojiInlineContent ->
        Text(
            text = emojiAnnotatedString,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = inlineContent + emojiInlineContent,
            onTextLayout = onTextLayout,
            style = style
        )
    }
}

/**
 * Creates an annotated String and a `InlineTextContent` map from a text containing Emoji characters.
 * Replaces all emojis with [NotoImageEmoji].
 *
 * @param text The text to with Emoji UTF characters.
 * @param content A lambda that receives the `AnnotatedString` and its corresponding `InlineTextContent` map
 *                These should be used to display: `{ astr, map -> Text(astr, inlineContent = map) }`.
 */
@Composable
fun WithNotoImageEmoji(
    text: CharSequence,
    content: @Composable (AnnotatedString, Map<String, InlineTextContent>) -> Unit
) {
    WithNotoEmoji(
        text = text,
        ratio = {
            details.notoImageRatio.takeIf { it > 0f }
                ?: 1f
        },
        content = content
    )
}

/**
 * Replaces all shortcodes (i.e. :emoji: or :emoji~skintone:) with their actual corresponding emojis.
 */
@Composable
fun String.withEmoji(): String {
    val service = EmojiService.get() ?: return ""
    return remember(this) { service.catalog.replace(this) }
}

@JvmInline
value class EmojiUrl private constructor(val url: String) {
    companion object {
        const val notoBaseUrl: String = "https://fonts.gstatic.com/s/e/notoemoji/latest"
        fun from(emoji: Emoji): EmojiUrl {
            val code = emoji.details.codePoints().joinToString("_") { it.toString(radix = 16) }
            return EmojiUrl("$notoBaseUrl/$code/emoji.svg")
        }
    }
    val code: String get() = url.split('/').let { it[it.lastIndex - 1] }
}

@Composable
private fun WithNotoEmoji(
    text: CharSequence,
    ratio: Emoji.() -> Float,
    content: @Composable (AnnotatedString, Map<String, InlineTextContent>) -> Unit,
) {
    val service = EmojiService.get() ?: return

    val all = remember(text) { service.finder.findEmoji(text).toList() }

    val inlineContent = HashMap<String, InlineTextContent>()
    val annotatedString = buildAnnotatedString {
        var start = 0
        all.forEach { found ->
            if (text is AnnotatedString)
                append(text.subSequence(start, found.start))
            else
                append(text.substring(start, found.start))
            val inlineContentID = "emoji:${found.emoji}"
            inlineContent[inlineContentID] = InlineTextContent(Placeholder(found.emoji.ratio().em, 1.em, PlaceholderVerticalAlign.Center)) {
                WebImage(EmojiUrl.from(found.emoji).url, "${found.emoji.details.description} emoji", Modifier)
            }
            appendInlineContent(inlineContentID)
            start = found.end
        }
        if (text is AnnotatedString)
            append(text.subSequence(start, text.length))
        else
            append(text.substring(start, text.length))
    }

    content(annotatedString, inlineContent)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun <T> Deferred<T>.consumeAsState(initialValue: T) =
    produceState(
        initialValue = if (isCompleted) getCompleted() else initialValue,
        producer = { value = await() }
    )

/**
 * Centralized Emoji [catalog] and [finder] services.
 */
class EmojiService private constructor(
    val catalog: EmojiTemplateCatalog,
    val finder: EmojiFinder,
) {
    companion object {
        private lateinit var deferred: Deferred<EmojiService>

        /**
         * Before the catalog is initialized (or accessed, as the first access initializes it), this can be assigned a lambda that will configure the catalog.
         */
        var catalogBuilder: EmojiTemplateCatalog.Builder.() -> Unit = {}
            set(value) {
                if (::deferred.isInitialized) error("Cannot set catalogBuilder after Service has been initialized or accessed.")
                field = value
            }

        /**
         * Initializes the Emoji services in the background.
         * This function does not block.
         */
        fun initialize() {
            if (!::deferred.isInitialized) {
                @OptIn(DelicateCoroutinesApi::class)
                deferred = GlobalScope.async {
                    val catalog = async(Dispatchers.Default) { EmojiTemplateCatalog(Emoji.list(), catalogBuilder) }
                    val finder = async(Dispatchers.Default) { EmojiFinder() }
                    EmojiService(catalog.await(), finder.await())
                }
            }
        }

        /**
         * Get the emoji service as a Composable state.
         * @return null if the emoji service is currently initializing.
         */
        @Composable
        fun get(): EmojiService? {
            initialize()
            val service: EmojiService? by deferred.consumeAsState(null)
            return service
        }

        /**
         * Awaits for the Emoji service to be initialized.
         */
        suspend fun await(): EmojiService {
            initialize()
            return deferred.await()
        }
    }
}
