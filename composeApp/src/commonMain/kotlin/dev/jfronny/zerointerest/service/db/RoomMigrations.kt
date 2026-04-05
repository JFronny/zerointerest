package dev.jfronny.zerointerest.service.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.executeSQL

object Migration1_2 : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL(
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
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL("""
            CREATE TABLE IF NOT EXISTS `SummaryEntity` (
                `roomId` TEXT NOT NULL,
                `summaryId` TEXT NOT NULL,
                `parentId` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `summaryId`, `parentId`)
            )
        """.trimIndent())
        connection.executeSQL("""
            CREATE TABLE IF NOT EXISTS `SummaryTransactionEntity` (
                `roomId` TEXT NOT NULL,
                `summaryId` TEXT NOT NULL,
                `transactionId` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `summaryId`, `transactionId`)
            )
        """.trimIndent())
    }
}

object Migration3_4 : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL("""
            CREATE TABLE IF NOT EXISTS `TransactionTemplateEntity` (
                `roomId` TEXT NOT NULL,
                `id` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `sender` TEXT NOT NULL,
                `receivers` TEXT NOT NULL,
                PRIMARY KEY(`roomId`, `id`)
            )
        """.trimIndent())
    }
}
