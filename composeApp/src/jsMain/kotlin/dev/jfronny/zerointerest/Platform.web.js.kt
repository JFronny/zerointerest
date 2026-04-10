package dev.jfronny.zerointerest

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

actual fun createSqlJsWorker() =
    WebWorkerSQLiteDriver(Worker(js("""new URL("sqlite-web-worker/worker.js", import.meta.url)""")))