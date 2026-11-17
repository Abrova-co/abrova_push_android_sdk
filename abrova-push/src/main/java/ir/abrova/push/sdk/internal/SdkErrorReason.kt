package ir.abrova.push.sdk.internal

import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

/**
 * Why an error happened, as one word from a fixed list, for the SDK's own error report
 * (`X-Abrova-SDK-Report-Reasons`, see [SdkDiagnostics]).
 *
 * Built only from the error's type and numeric codes (MQTT reason code, HTTP status, errno): never from a message,
 * so it can never carry an address, a host, a user or any other app data.
 *
 * Words: `mqtt<N>`, `http<NNN>`, `cert`, `tls_handshake`, `tls`, `timeout`, `dns`, `refused`, `noroute`, `offline`,
 * `reset`, `eof`, `socket`, `parse`, `state`, `arg`, `security`, `oom`, `cancelled`, `other`. Every word that matches
 * anywhere in the cause chain is collected and the most telling one wins (in that order).
 */
internal object SdkErrorReason {
    const val OTHER = "other"

    private const val MAX_DEPTH = 8
    private const val MQTT_EXCEPTION = "org.eclipse.paho.client.mqttv3.MqttException"

    /** Lower index = more telling. Numeric words (mqtt / http) always win. */
    private val PRIORITY = listOf(
        "cert", "tls_handshake", "tls", "timeout", "dns", "refused", "noroute", "offline", "reset", "eof", "socket",
        "parse", "state", "arg", "security", "oom", "cancelled",
    )

    private val NETWORKS = setOf("wifi", "cellular", "ethernet", "vpn", "none", "other")

    // Linux errno values (Android's), read from ErrnoException.errno.
    private const val ENETDOWN = 100
    private const val ENETUNREACH = 101
    private const val ECONNABORTED = 103
    private const val ECONNRESET = 104
    private const val ETIMEDOUT = 110
    private const val ECONNREFUSED = 111
    private const val EHOSTUNREACH = 113
    private const val EPIPE = 32

    /** The reason for [error] (its whole cause chain), [OTHER] when nothing matches or there is no error. */
    fun of(error: Throwable?): String {
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        // An MQTT error that wraps a cause (Paho's "unable to connect", "connection lost") is described by that cause;
        // its own code is used only when nothing under it says more.
        var mqttFallback: String? = null
        var t = error
        var depth = 0
        while (t != null && depth < MAX_DEPTH) {
            if (t.javaClass.name == MQTT_EXCEPTION && t.cause != null && t.cause !== t) {
                if (mqttFallback == null) mqttFallback = mqttReasonCode(t)?.takeIf { it > 0 }?.let { "mqtt$it" }
                t = t.cause
                depth++
                continue
            }
            val word = direct(t)
            if (word != null) {
                val rank = if (word.startsWith("mqtt") || word.startsWith("http")) -1 else PRIORITY.indexOf(word)
                if (rank < bestRank) {
                    best = word
                    bestRank = rank
                }
            }
            val next = t.cause
            if (next === t) break
            t = next
            depth++
        }
        return best ?: mqttFallback ?: OTHER
    }

    /** An API answer with HTTP status [status] (>= 400). */
    fun http(status: Int): String = if (status in 100..999) "http$status" else OTHER

    /**
     * The entry key `<reason>[.<port>][.<net>]`: [port] only when 1–65535, [network] only when it is one of
     * wifi, cellular, ethernet, vpn, none, other.
     */
    fun key(reason: String, port: Int? = null, network: String? = null): String = buildString {
        append(reason)
        if (port != null && port in 1..65535) append('.').append(port)
        if (network != null && network in NETWORKS) append('.').append(network)
    }

    private fun direct(t: Throwable): String? = when {
        t.javaClass.name == MQTT_EXCEPTION -> mqttReasonCode(t)?.takeIf { it > 0 }?.let { "mqtt$it" }
        t is CertificateException || t is CertPathValidatorException -> "cert"
        t is SSLHandshakeException -> "tls_handshake"
        t is SSLException -> "tls"
        t is SocketTimeoutException -> "timeout"
        t is UnknownHostException -> "dns"
        t is ConnectException -> "refused"
        t is NoRouteToHostException -> "noroute"
        t is EOFException -> "eof"
        t is SocketException -> "socket"
        t is InterruptedIOException -> "timeout" // OkHttp's call timeout
        isErrno(t) -> errnoWord(t)
        t is com.google.gson.JsonParseException || t.javaClass.name == "org.json.JSONException" -> "parse"
        t is IllegalStateException -> "state"
        t is IllegalArgumentException -> "arg"
        t is SecurityException -> "security"
        t is OutOfMemoryError -> "oom"
        t is CancellationException || t is InterruptedException -> "cancelled"
        else -> null
    }

    private fun mqttReasonCode(t: Throwable): Int? = try {
        t.javaClass.getMethod("getReasonCode").invoke(t) as? Int
    } catch (_: Exception) {
        null
    }

    private fun isErrno(t: Throwable): Boolean = t.javaClass.name == "android.system.ErrnoException"

    private fun errnoWord(t: Throwable): String? {
        val errno = try {
            t.javaClass.getField("errno").getInt(t)
        } catch (_: Exception) {
            return null
        }
        return when (errno) {
            ETIMEDOUT -> "timeout"
            ECONNREFUSED -> "refused"
            ENETUNREACH, EHOSTUNREACH, ENETDOWN -> "noroute"
            ECONNRESET, ECONNABORTED, EPIPE -> "reset"
            else -> null
        }
    }
}
