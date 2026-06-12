package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room3.Room as Room3
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import dev.jfronny.zerointerest.shared.generated.resources.*
import io.ktor.client.engine.java.Java
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.stringResource
import org.koin.core.scope.Scope

class JVMPlatform : AbstractPlatform(OS.stateDir.toOkioPath()) {
    override val name: String = "Desktop (${OS.type.displayName})"
    override fun getHttpClientEngine() = Java.create {}
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder<TrixnityRoomDatabase>(stateDir.resolve(TRIXNITY_NAME).toNioPath().absolutePathString())
    override fun zerointerestDatabaseBuilder() = Room3.databaseBuilder<ZeroInterestRoomDatabase>(stateDir.resolve(ZEROINTEREST_NAME).toNioPath().absolutePathString())
    override fun createDataStore(): DataStore<Preferences> = createDataStore {
        stateDir.resolve(DATASTORE_NAME)
    }
}

actual fun Scope.getPlatform(): Platform = JVMPlatform()

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null
actual fun addShutdownHook(block: () -> Unit) = Runtime.getRuntime().addShutdownHook(Thread(block))

@Composable
actual fun Instant.formatLocalized(style: TimestampStyle): String {
    val locale = Locale.getDefault()

    val now = ZonedDateTime.now()
    val time = toJavaInstant().atZone(ZoneId.systemDefault())

    val diff = Duration.between(time, now).abs()

    return when {
        diff < Duration.ofMinutes(1) -> stringResource(Res.string.timestamp_just_now)

        diff < Duration.ofHours(1) -> stringResource(Res.string.timestamp_minutes_ago, diff.toMinutes())

        diff < Duration.ofDays(1) -> stringResource(Res.string.timestamp_hours_ago, diff.toHours())

        diff < Duration.ofDays(7) -> stringResource(Res.string.timestamp_days_ago, diff.toDays())

        time.year == now.year -> {
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .format(time)
        }

        else -> {
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .format(time)
        }
    }
}
