package dev.jfronny.zerointerest

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.dsl.bind
import org.koin.dsl.module

fun createExtraModule() = module {
    single { BundledSQLiteDriver() } bind SQLiteDriver::class
    single { get<Platform>() as AbstractPlatform }
}
