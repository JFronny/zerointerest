package dev.jfronny.zerointerest

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.impl.StaticLoggerBinder
import org.slf4j.impl.StaticMDCBinder
import org.slf4j.impl.StaticMarkerBinder
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

class AndroidServiceProvider : SLF4JServiceProvider {
    override fun getLoggerFactory(): ILoggerFactory? = StaticLoggerBinder.getSingleton().loggerFactory
    override fun getMarkerFactory(): IMarkerFactory? = StaticMarkerBinder.getSingleton().markerFactory
    override fun getMDCAdapter(): MDCAdapter? = StaticMDCBinder.getSingleton().mdca
    override fun getRequestedApiVersion(): String? = "2.0"
    override fun initialize() {}
}