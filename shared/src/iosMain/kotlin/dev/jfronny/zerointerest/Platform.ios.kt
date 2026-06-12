package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room3.Room as Room3
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import okio.Path.Companion.toPath
import org.koin.core.scope.Scope
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSinceDate
import platform.UIKit.UIDevice
import platform.posix.atexit

class IOSPlatform : AbstractPlatform(documentDirectory().toPath()) {
    override val name: String = UIDevice.currentDevice.systemName()
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder<TrixnityRoomDatabase>("${documentDirectory()}/$TRIXNITY_NAME")
    override fun zerointerestDatabaseBuilder() = Room3.databaseBuilder<ZeroInterestRoomDatabase>("${documentDirectory()}/$ZEROINTEREST_NAME")

    @OptIn(ExperimentalForeignApi::class)
    override fun createDataStore(): DataStore<Preferences> = createDataStore {
        val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        (requireNotNull(documentDirectory).path + "/$DATASTORE_NAME").toPath()
    }

    override fun getHttpClientEngine() = Darwin.create {}
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
actual fun Scope.getPlatform(): Platform = IOSPlatform()

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null
private val shutdownHooks = mutableListOf<() -> Unit>()

private fun onExit() {
    shutdownHooks.forEach { it() }
}

@OptIn(ExperimentalForeignApi::class)
actual fun addShutdownHook(block: () -> Unit) {
    shutdownHooks.add(block)
    if (shutdownHooks.size == 1) {
        atexit(staticCFunction(::onExit))
    }
}

@Composable
actual fun Instant.formatLocalized(style: TimestampStyle): String {
    val epochMillis = toEpochMilliseconds()
    val now = NSDate()
    val date = NSDate.dateWithTimeIntervalSince1970(
        epochMillis / 1000.0,
    )

    val diffSeconds =
        kotlin.math.abs(now.timeIntervalSinceDate(date))

    // < 1 week -> relative
    if (diffSeconds < 7 * 24 * 60 * 60) {
        val relativeFormatter = NSRelativeDateTimeFormatter()

        return relativeFormatter.localizedStringForDate(
            date,
            relativeToDate = now,
        )
    }

    val formatter = NSDateFormatter()

    val calendar = NSCalendar.currentCalendar
    val currentYear =
        calendar.component(NSCalendarUnitYear, fromDate = now)

    val targetYear =
        calendar.component(NSCalendarUnitYear, fromDate = date)

    formatter.dateStyle =
        if (currentYear == targetYear) {
            NSDateFormatterMediumStyle
        } else {
            NSDateFormatterLongStyle
        }

    formatter.timeStyle =
        NSDateFormatterNoStyle

    return formatter.stringFromDate(date)
}
