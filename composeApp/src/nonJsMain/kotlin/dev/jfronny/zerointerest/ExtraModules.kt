package dev.jfronny.zerointerest

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import org.koin.dsl.bind
import org.koin.dsl.module

fun createExtraModule() = module {
    single {
        get<AbstractPlatform>()
            .zerointerestDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .addMigrations(Migration1_2, Migration2_3)
            .build()
    } bind ZeroInterestRoomDatabase::class
    single {
        RoomSummaryTrustDatabase(get())
    } bind SummaryTrustDatabase::class
    single {
        get<Platform>() as AbstractPlatform
    }
}
