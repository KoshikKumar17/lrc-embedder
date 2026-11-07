package dev.koshik.lrcembedder.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import okhttp3.ConnectionPool
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

object Http {
    val client = HttpClient(OkHttp) {
        // OkHttp uses the platform TLS stack (Conscrypt): HTTP/2, connection reuse, TLS session
        // resumption and IPv4/IPv6 fallback.
        // Ktor's CIO engine did its own TLS handshake per request.
        engine {
            config {
                connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
                retryOnConnectionFailure(true)
                // Try IPv4 first: on some networks IPv6 addresses resolve but never connect, which
                // stalls a request until it times out.
                dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
                })
            }
        }
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

/** Runs [block]; returns null on any failure except coroutine cancellation. */
suspend fun <T> safe(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Retries transient failures with exponential backoff. Never swallows coroutine cancellation. */
suspend fun <T> withRetry(
    maxAttempts: Int = 2,
    baseDelayMs: Long = 400,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempt++
            if (attempt >= maxAttempts) throw e
            kotlinx.coroutines.delay(baseDelayMs shl (attempt - 1))
        }
    }
}
