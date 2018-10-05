package com.github.aysnc.sql.db.postgresql

import com.github.jasync.sql.db.Configuration
import com.github.jasync.sql.db.Connection
import com.github.jasync.sql.db.QueryResult
import com.github.jasync.sql.db.SSLConfiguration
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import io.netty.handler.timeout.TimeoutException
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


interface DatabaseTestHelper {


    fun databaseName() = "netty_driver_test"

    fun timeTestDatabase() = "netty_driver_time_test"

    fun databasePort() = 5432

    fun defaultConfiguration() = Configuration(
            port = databasePort(),
            username = "postgres",
            database = databaseName())

    fun timeTestConfiguration() = Configuration(
            port = databasePort(),
            username = "postgres",
            database = timeTestDatabase())

    fun <T> withHandler(fn: (PostgreSQLConnection) -> T): T {
        return withHandler(this.defaultConfiguration(), fn)
    }

    fun <T> withTimeHandler(fn: (PostgreSQLConnection) -> T): T {
        return withHandler(this.timeTestConfiguration(), fn)
    }

    fun <T> withSSLHandler(mode: SSLConfiguration.Mode, host: String = "localhost", rootCert: File? = File("script/server.crt"), fn: (PostgreSQLConnection) -> T): T {
        val config = Configuration(
                host = host,
                port = databasePort(),
                username = "postgres",
                database = databaseName(),
                ssl = SSLConfiguration(mode = mode, rootCert = rootCert))
        return withHandler(config, fn)
    }

    fun <T> withHandler(configuration: Configuration, fn: (PostgreSQLConnection) -> T): T {

        val handler = PostgreSQLConnection(configuration)

        try {
            handler.connect().get(5, TimeUnit.SECONDS)
            return fn(handler)
        } finally {
            handleTimeout(handler) { handler.disconnect() }
        }

    }

    fun executeDdl(handler: Connection, data: String, count: Int = 0): Long {
        val rows = handleTimeout(handler) {
            handler.sendQuery(data).get(5, TimeUnit.SECONDS).rowsAffected
        }

        if (rows.toInt() != count) {
            throw IllegalStateException("We expected %s rows but there were %s".format(count, rows))
        }

        return rows
    }

    private fun <R> handleTimeout(handler: Connection, fn: () -> R): R {
        try {
            return fn()
        } catch (e: TimeoutException) {

            throw IllegalStateException("Timeout executing call from handler -> %s".format(handler))

        }
    }

    fun executeQuery(handler: Connection, data: String): QueryResult {
        return handleTimeout(handler) {
            handler.sendQuery(data).get(5, TimeUnit.SECONDS)
        }
    }

    fun executePreparedStatement(
            handler: Connection,
            statement: String,
            values: List<Any?> = emptyList()) {
        handleTimeout(handler) {
            handler.sendPreparedStatement(statement, values).get(5, TimeUnit.SECONDS)
        }
    }

    fun <T> await(future: CompletableFuture<T>): T {
        return future.get(5, TimeUnit.SECONDS)
    }


}
