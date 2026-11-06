package com.echoflow.data

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Where every OkHttp client starts. Clients still set their own timeouts, but they share one
 * connection pool, so a provider reached from several services reuses warm connections instead
 * of each service opening its own.
 *
 * The dispatcher is deliberately not shared: its per-host limit (5) would then cap long-lived
 * streams and sockets across the whole app.
 */
object HttpClients {
    private val pool = ConnectionPool()

    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder().connectionPool(pool)
}
