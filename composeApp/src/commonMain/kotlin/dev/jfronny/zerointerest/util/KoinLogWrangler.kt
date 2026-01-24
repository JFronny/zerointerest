package dev.jfronny.zerointerest.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import org.koin.core.logger.MESSAGE
import org.koin.core.logger.Level as OLevel
import org.koin.core.logger.Logger as OLogger

private val logger = KotlinLogging.logger {}

private val level by lazy {
    if (logger.isDebugEnabled()) OLevel.DEBUG
    else if (logger.isInfoEnabled()) OLevel.INFO
    else if (logger.isWarnEnabled()) OLevel.WARNING
    else if (logger.isErrorEnabled()) OLevel.ERROR
    else OLevel.NONE
}

object KoinLogWrangler : OLogger(level) {
    override fun display(level: OLevel, msg: MESSAGE) = logger.at(
        when (level) {
            OLevel.DEBUG -> Level.DEBUG
            OLevel.INFO -> Level.INFO
            OLevel.WARNING -> Level.WARN
            OLevel.ERROR -> Level.ERROR
            OLevel.NONE -> Level.OFF
        },
        marker = null,
    ) {
        this.message = msg
    }
}
