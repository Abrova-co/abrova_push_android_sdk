package ir.abrova.push.sdk.internal

/** Keeps secrets out of logs: JSON values of keys such as token, password, secret are replaced by `***`. */
internal object LogMask {
    private val SECRET_VALUE = Regex("(\"[A-Za-z_]*(?:[Tt]oken|[Pp]assword|[Ss]ecret)\"\\s*:\\s*)\"[^\"]*\"")

    fun secrets(json: String?): String = json?.replace(SECRET_VALUE, "$1\"***\"") ?: ""
}
