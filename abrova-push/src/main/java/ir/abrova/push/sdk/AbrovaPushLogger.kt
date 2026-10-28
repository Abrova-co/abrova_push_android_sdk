package ir.abrova.push.sdk

/**
 * Log levels for the Abrova Push SDK.
 * Controls verbosity of logging output.
 */
enum class AbrovaPushLogLevel {
    /** No logging at all (for production) */
    NONE,
    /** Only errors */
    ERROR,
    /** Errors and warnings */
    WARNING,
    /** Errors, warnings, and info messages */
    INFO,
    /** All messages including debug output (default for development) */
    DEBUG,
    /** Everything including very detailed traces */
    VERBOSE;

    companion object {
        fun fromString(level: String): AbrovaPushLogLevel {
            return when (level.lowercase()) {
                "none" -> NONE
                "error" -> ERROR
                "warning" -> WARNING
                "info" -> INFO
                "debug" -> DEBUG
                "verbose" -> VERBOSE
                else -> DEBUG
            }
        }
    }
}

/**
 * Logger for Abrova Push SDK with configurable log levels.
 */
object AbrovaPushLogger {
    var logLevel: AbrovaPushLogLevel = AbrovaPushLogLevel.DEBUG

    fun setLogLevelFromString(level: String) {
        logLevel = AbrovaPushLogLevel.fromString(level)
    }

    fun v(tag: String, message: String) {
        if (logLevel >= AbrovaPushLogLevel.VERBOSE) {
            android.util.Log.v("AbrovaPush.$tag", message)
        }
    }

    fun d(tag: String, message: String) {
        if (logLevel >= AbrovaPushLogLevel.DEBUG) {
            android.util.Log.d("AbrovaPush.$tag", message)
        }
    }

    fun i(tag: String, message: String) {
        if (logLevel >= AbrovaPushLogLevel.INFO) {
            android.util.Log.i("AbrovaPush.$tag", message)
        }
    }

    fun w(tag: String, message: String) {
        if (logLevel >= AbrovaPushLogLevel.WARNING) {
            android.util.Log.w("AbrovaPush.$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (logLevel >= AbrovaPushLogLevel.ERROR) {
            if (throwable != null) {
                android.util.Log.e("AbrovaPush.$tag", message, throwable)
            } else {
                android.util.Log.e("AbrovaPush.$tag", message)
            }
        }
    }
}

/**
 * Convenience object for logging with automatic tag prefixing.
 * Use: Log.d(TAG, "message") instead of AbrovaPushLogger.d(TAG, "message")
 */
object Log {
    fun v(tag: String, message: String) = AbrovaPushLogger.v(tag, message)
    fun d(tag: String, message: String) = AbrovaPushLogger.d(tag, message)
    fun i(tag: String, message: String) = AbrovaPushLogger.i(tag, message)
    fun w(tag: String, message: String) = AbrovaPushLogger.w(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = AbrovaPushLogger.e(tag, message, throwable)
}
