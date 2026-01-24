package dev.jfronny.zerointerest

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object Migration1_2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `SummaryHeadEntity` (
                `roomId` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `eventId`)
             )
            """.trimIndent()
        )
    }
}

object Migration2_3 : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `SummaryEntity` (
                `roomId` TEXT NOT NULL,
                `summaryId` TEXT NOT NULL,
                `parentId` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `summaryId`, `parentId`)
            )
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `SummaryTransactionEntity` (
                `roomId` TEXT NOT NULL,
                `summaryId` TEXT NOT NULL,
                `transactionId` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `summaryId`, `transactionId`)
            )
        """.trimIndent())
    }
}
