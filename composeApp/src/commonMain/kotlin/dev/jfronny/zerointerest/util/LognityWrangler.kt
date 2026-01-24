package dev.jfronny.zerointerest.util

import de.connect2x.lognity.api.ansi.AnsiScope
import de.connect2x.lognity.api.appender.Appender
import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.api.config.Config
import de.connect2x.lognity.api.config.ConfigSpec
import de.connect2x.lognity.api.context.Context
import de.connect2x.lognity.api.context.ContextSpec
import de.connect2x.lognity.api.format.Formatter
import de.connect2x.lognity.api.logger.Level as OLevel
import de.connect2x.lognity.api.logger.Logger as LLogger
import de.connect2x.lognity.api.marker.Marker
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KMarkerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

private val xlogger = KotlinLogging.logger {}

@OptIn(ExperimentalAtomicApi::class)
object LognityWrangler : Backend {
    override val name: String get() = "ktor"
    override val defaultLevel: OLevel = if (xlogger.isDebugEnabled()) OLevel.DEBUG
    else if (xlogger.isInfoEnabled()) OLevel.INFO
    else if (xlogger.isWarnEnabled()) OLevel.WARN
    else if (xlogger.isErrorEnabled()) OLevel.ERROR
    else OLevel.TRACE

    override val defaultFormatter: Formatter get() = Formatter.identity
    private val _configSpec: AtomicReference<ConfigSpec> = AtomicReference {
        appender(OAppender)
    }
    override var configSpec: ConfigSpec
        get() = _configSpec.load()
        set(value) {
            _configSpec.store(value)
        }
    private val _contextSpec: AtomicReference<ContextSpec> = AtomicReference {}
    override var contextSpec: ContextSpec
        get() = _contextSpec.load()
        set(value) {
            _contextSpec.store(value)
        }

    private val isCoroutineScopeProviderSet: AtomicBoolean = AtomicBoolean(false)
    private val coroutineScopeProvider: AtomicReference<() -> CoroutineScope> = AtomicReference {
        val supervisorJob = SupervisorJob()
        addShutdownHook(supervisorJob::cancel)
        CoroutineScope(Dispatchers.Default + supervisorJob + CoroutineName("Lognity"))
    }

    override val coroutineScope: CoroutineScope by lazy(LazyThreadSafetyMode.SYNCHRONIZED, coroutineScopeProvider.load())

    override fun setCoroutineScopeProvider(provider: () -> CoroutineScope) {
        check(!isCoroutineScopeProviderSet.compareAndExchange(expectedValue = false, newValue = true)) {
            "CoroutineScope provider was already set for logging backend"
        }
        coroutineScopeProvider.store(provider)
    }

    override fun addShutdownHook(hook: () -> Unit) = dev.jfronny.zerointerest.addShutdownHook(hook)

    override fun createMarker(
        key: String,
        name: String,
        isEnabled: Boolean
    ): Marker = OMarker(isEnabled, key, name)

    override fun createLogger(
        name: String?,
        contextSpec: ContextSpec
    ): LLogger = OLogger(KotlinLogging.logger(name ?: ""),
        Config(configSpec),
        Context(contextSpec),
        defaultLevel,
        true
    )

    class OMarker(
        override var isEnabled: Boolean,
        override val key: String,
        override val name: String
    ) : Marker

    class OLogger(val logger: KLogger,
                  override val config: Config,
                  override val context: Context,
                  override var level: OLevel,
                  override var isEnabled: Boolean
    ) : LLogger {
        override fun log(level: OLevel, message: AnsiScope.() -> Any) = log(null, level, message)
        override fun log(
            marker: Marker?,
            level: OLevel,
            message: AnsiScope.() -> Any
        ) {
            if (level < this.level) return
            val actualMarker = marker ?: context[LLogger.DefaultMarker]?.marker
            if (actualMarker?.isEnabled == false) return
            val messageContent = message(AnsiScope)
            val timestamp = Clock.System.now()
            for (appender in config.appenders) {
                appender.append(
                    this,
                    level,
                    appender.formatter(this, level, messageContent, marker, timestamp, appender.pattern),
                    marker
                )
            }

        }
    }

    object OAppender : Appender {
        override val name: String = "OAppender"
        override val formatter: Formatter get() = Formatter { logger, level, content, marker, timestamp, s -> content.toString() }
        override val pattern: String get() = "{{message}}"

        override fun append(
            logger: LLogger,
            level: OLevel,
            message: String,
            marker: Marker?
        ) {
            val level = when (level) {
                OLevel.TRACE -> Level.TRACE
                OLevel.DEBUG -> Level.DEBUG
                OLevel.INFO -> Level.INFO
                OLevel.WARN -> Level.WARN
                OLevel.ERROR -> Level.ERROR
                OLevel.FATAL -> Level.ERROR
            }
            val marker = marker?.let { marker ->
                if (!marker.isEnabled) return
                KMarkerFactory.getMarker(marker.name)
            }
            if (logger !is OLogger) {
                xlogger.warn { "Non-OLogger using OAppender: $logger" }
                return
            }
            logger.logger.at(level, marker) {
                this.message = message
            }
        }
    }
}
