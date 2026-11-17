package ir.abrova.push.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import ir.abrova.push.sdk.Log
import ir.abrova.push.sdk.AbrovaPushErrorCode
import okhttp3.Interceptor

/**
 * SDK self-reports. The SDK counts its own errors by the public
 * [AbrovaPushErrorCode] number every Abrova Push SDK shares, and sends the counts with its next ordinary API
 * request in one header:
 *
 *     X-Abrova-SDK-Report: 1000=3 1400=1 9100=1
 *
 * Counts only — never a message, stack trace, key, user or device detail. A count is cleared only after the
 * server answered 2xx, so nothing is lost while offline and nothing is counted twice.
 *
 * Next to it, why: the same counts broken down by a reason from a fixed list ([SdkErrorReason]), with the port and
 * the network type when known, in a second header on the same request, kept and cleared the same way:
 *
 *     X-Abrova-SDK-Report-Reasons: 1000:timeout.8883.wifi=3 1007:mqtt32110.8883.cellular=5 1304:http503=1 Switched off with
 * [ir.abrova.push.sdk.AbrovaPushConfig.sendDiagnostics] = false: then nothing is counted, kept or sent.
 *
 * [CRASH] (report-only): the app crashed in Abrova Push code (see [isOwnCrash]). Seen by a chained default
 * uncaught-exception handler that counts it and always hands the crash on to the handler that was there before
 * (the app's crash reporter or Android's), the way crash reporters chain.
 */
internal object SdkDiagnostics {
    private const val TAG = "SdkDiagnostics"
    const val HEADER = "X-Abrova-SDK-Report"
    const val REASONS_HEADER = "X-Abrova-SDK-Report-Reasons"

    /** Report-only code: an uncaught exception thrown by Abrova Push code. */
    const val CRASH = 9100

    /** Most codes per report and highest count per code (the server accepts no more). */
    const val MAX_CODES = 16
    const val MAX_COUNT = 9999

    private const val PREFS = "abrova_push_diagnostics"
    private const val KEY_PREFIX = "c_"
    private const val REASON_PREFIX = "r_"
    /** Longest reasons header the server accepts. */
    private const val MAX_REASONS_HEADER = 400
    /** `<code>:<reason>[.<port>][.<net>]`, the only entries ever kept (see [SdkErrorReason.key]). */
    private val REASON_ENTRY = Regex("^\\d{4}:[a-z][a-z0-9_]{0,19}(\\.\\d{1,5})?(\\.(wifi|cellular|ethernet|vpn|none|other))?$")
    private val OWN_PACKAGES = listOf("ir.abrova.push.", "ir.abrova.protocol.")

    private val lock = Any()
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var enabled = true
    @Volatile private var crashHandlerInstalled = false

    /** Counted before [attach] (e.g. while the service starts); written once storage is there. */
    private val early = mutableMapOf<Int, Int>()
    private val earlyReasons = mutableMapOf<String, Int>()

    /** Call from init() and the service. Safe to call more than once; the last `enabled` wins. */
    fun attach(context: Context, enabled: Boolean) {
        try {
            synchronized(lock) {
                this.enabled = enabled
                val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs = p
                if (!enabled) {
                    early.clear()
                    earlyReasons.clear()
                    p.edit().clear().apply()
                    return
                }
                if (early.isNotEmpty() || earlyReasons.isNotEmpty()) {
                    val edit = p.edit()
                    for ((code, n) in early) edit.putInt(KEY_PREFIX + code, add(p.getInt(KEY_PREFIX + code, 0), n))
                    for ((entry, n) in earlyReasons) edit.putInt(REASON_PREFIX + entry, add(p.getInt(REASON_PREFIX + entry, 0), n))
                    edit.apply()
                    early.clear()
                    earlyReasons.clear()
                }
            }
            installCrashHandler()
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostics not attached: ${e.message}")
        }
    }

    fun count(code: AbrovaPushErrorCode, reason: String? = null) = count(code.code, reason)

    /**
     * Adds one to [code] and, when given, to its [reason] (a [SdkErrorReason.key]: `timeout.8883.wifi`). Never throws;
     * a 17th distinct code, or a 17th distinct reason, is not kept (the code is still counted).
     */
    fun count(code: Int, reason: String? = null) {
        if (!enabled) return
        try {
            synchronized(lock) {
                val p = prefs
                val entry = reason?.let { "$code:$it" }?.takeIf { REASON_ENTRY.matches(it) }
                if (p == null) {
                    if (!early.containsKey(code) && early.size >= MAX_CODES) return
                    early[code] = add(early[code] ?: 0, 1)
                    if (entry != null && (earlyReasons.containsKey(entry) || earlyReasons.size < MAX_CODES)) earlyReasons[entry] = add(earlyReasons[entry] ?: 0, 1)
                    return
                }
                val edit = p.edit()
                val key = KEY_PREFIX + code
                if (!p.contains(key) && pendingLocked().size >= MAX_CODES) return
                edit.putInt(key, add(p.getInt(key, 0), 1))
                if (entry != null) {
                    val reasonKey = REASON_PREFIX + entry
                    if (p.contains(reasonKey) || pendingReasonsLocked().size < MAX_CODES) edit.putInt(reasonKey, add(p.getInt(reasonKey, 0), 1))
                }
                edit.apply()
            }
        } catch (_: Exception) {
            // Counting never matters more than the SDK's work.
        }
    }

    /** What would be sent now: code → count. */
    fun pending(): Map<Int, Int> = synchronized(lock) { if (enabled) pendingLocked() else emptyMap() }

    /** What would be sent now: `<code>:<reason…>` → count. */
    fun pendingReasons(): Map<String, Int> = synchronized(lock) { if (enabled) pendingReasonsLocked() else emptyMap() }

    /**
     * `1000:timeout.8883.wifi=3 1304:http503=1`, or null when there is nothing to send. Sorted; at most 16 entries and
     * 400 characters (entries that do not fit wait for a later request).
     */
    fun reasonsHeaderValue(snapshot: Map<String, Int>): String? {
        if (snapshot.isEmpty()) return null
        val parts = mutableListOf<String>()
        var length = 0
        for ((entry, n) in snapshot.entries.sortedBy { it.key }) {
            if (parts.size >= MAX_CODES) break
            val part = "$entry=${n.coerceIn(1, MAX_COUNT)}"
            val next = length + part.length + if (parts.isEmpty()) 0 else 1
            if (next > MAX_REASONS_HEADER) break
            parts += part
            length = next
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** The part of [snapshot] that [reasonsHeaderValue] puts in the header (what the server will take). */
    private fun sentReasons(snapshot: Map<String, Int>): Map<String, Int> {
        val value = reasonsHeaderValue(snapshot) ?: return emptyMap()
        return value.split(' ').associate { part ->
            val eq = part.lastIndexOf('=')
            part.substring(0, eq) to part.substring(eq + 1).toInt()
        }
    }

    /** `1000=3 1400=1`, or null when there is nothing to send. Codes in ascending order. */
    fun headerValue(snapshot: Map<Int, Int>): String? =
        if (snapshot.isEmpty()) null
        else snapshot.entries.sortedBy { it.key }.take(MAX_CODES).joinToString(" ") { "${it.key}=${it.value.coerceIn(1, MAX_COUNT)}" }

    /** The server took [snapshot] (and [reasons]): subtract it (counts added meanwhile stay for the next request). */
    fun acknowledge(snapshot: Map<Int, Int>, reasons: Map<String, Int> = emptyMap()) {
        try {
            synchronized(lock) {
                val p = prefs ?: return
                val edit = p.edit()
                for ((code, sent) in snapshot) {
                    val left = p.getInt(KEY_PREFIX + code, 0) - sent
                    if (left > 0) edit.putInt(KEY_PREFIX + code, left) else edit.remove(KEY_PREFIX + code)
                }
                for ((entry, sent) in reasons) {
                    val left = p.getInt(REASON_PREFIX + entry, 0) - sent
                    if (left > 0) edit.putInt(REASON_PREFIX + entry, left) else edit.remove(REASON_PREFIX + entry)
                }
                edit.apply()
            }
        } catch (_: Exception) {
            // Sent again next time; a count a little high is better than a crash.
        }
    }

    /** OkHttp interceptor: attaches the report and its reasons, and clears what a 2xx answer took. */
    fun interceptor(): Interceptor = Interceptor { chain ->
        val snapshot = pending()
        val value = headerValue(snapshot)
        val reasons = sentReasons(pendingReasons())
        val reasonsValue = reasonsHeaderValue(reasons)
        if (value == null && reasonsValue == null) {
            chain.proceed(chain.request())
        } else {
            val builder = chain.request().newBuilder()
            if (value != null) builder.header(HEADER, value)
            if (reasonsValue != null) builder.header(REASONS_HEADER, reasonsValue)
            val response = chain.proceed(builder.build())
            if (response.isSuccessful) acknowledge(snapshot, reasons)
            response
        }
    }

    /**
     * True when the code that threw is Abrova Push code: in the root cause, the first frame that is not platform or
     * library code belongs to Abrova Push. An app callback that throws while the SDK calls it is the app's crash,
     * not the SDK's (the SDK is only further down the stack).
     */
    fun isOwnCrash(error: Throwable): Boolean {
        var root = error
        var depth = 0
        while (depth < 8) {
            val cause = root.cause ?: break
            if (cause === root) break
            root = cause
            depth++
        }
        val thrower = root.stackTrace.firstOrNull { frame -> PLATFORM_PACKAGES.none { frame.className.startsWith(it) } } ?: return false
        return OWN_PACKAGES.any { thrower.className.startsWith(it) }
    }

    private val PLATFORM_PACKAGES = listOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "com.android.", "dalvik.", "libcore.",
        "sun.", "jdk.", "okhttp3.", "okio.", "com.google.gson.",
    )

    private fun installCrashHandler() {
        synchronized(lock) {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (enabled && isOwnCrash(error)) {
                    synchronized(lock) {
                        val p = prefs
                        // commit(), not apply(): the process is about to die.
                        if (p != null) {
                            val reason = REASON_PREFIX + CRASH + ":" + SdkErrorReason.of(error)
                            p.edit()
                                .putInt(KEY_PREFIX + CRASH, add(p.getInt(KEY_PREFIX + CRASH, 0), 1))
                                .putInt(reason, add(p.getInt(reason, 0), 1))
                                .commit()
                        }
                    }
                }
            } catch (_: Throwable) {
                // Never stand between the app and its crash handler.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun pendingLocked(): Map<Int, Int> {
        val p = prefs ?: return early.toMap()
        return p.all.mapNotNull { (key, value) ->
            val code = key.removePrefix(KEY_PREFIX).toIntOrNull()
            if (!key.startsWith(KEY_PREFIX) || code == null || value !is Int || value <= 0) null else code to value
        }.toMap()
    }

    private fun pendingReasonsLocked(): Map<String, Int> {
        val p = prefs ?: return earlyReasons.toMap()
        return p.all.mapNotNull { (key, value) ->
            if (!key.startsWith(REASON_PREFIX) || value !is Int || value <= 0) null else key.removePrefix(REASON_PREFIX) to value
        }.toMap()
    }

    private fun add(a: Int, b: Int): Int = (a.toLong() + b).coerceAtMost(MAX_COUNT.toLong()).toInt()

    /** Tests only. */
    internal fun resetForTests() {
        synchronized(lock) {
            prefs?.edit()?.clear()?.commit()
            prefs = null
            enabled = true
            early.clear()
            earlyReasons.clear()
        }
    }
}
