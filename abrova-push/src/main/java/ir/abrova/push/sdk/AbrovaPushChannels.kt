package ir.abrova.push.sdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * A notification channel defined for the project in Abrova Console. The SDK creates it on the phone; a message picks
 * it with `channelId`. Fields are nullable because Gson does not run Kotlin defaults.
 */
data class AbrovaPushChannel(
    /** The channel key messages use as `channelId`. */
    @SerializedName("id") val id: String? = null,
    /** Raised when importance, vibration or sound change: Android needs a new channel for those. */
    @SerializedName("v") val version: Int? = null,
    @SerializedName("name") val name: Map<String, String>? = null,
    @SerializedName("description") val description: Map<String, String>? = null,
    /** `high`, `default` or `low`. */
    @SerializedName("importance") val importance: String? = null,
    @SerializedName("vibrate") val vibrate: Boolean? = null,
    /** `default`, `none`, or a sound in the app's res/raw. */
    @SerializedName("sound") val sound: String? = null,
    /** The iOS interruption level; not used on Android. */
    @SerializedName("ios") val ios: String? = null,
) {
    val vibrates: Boolean get() = vibrate != false
    val soundName: String get() = sound ?: "default"

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "v" to version,
        "name" to name,
        "description" to description,
        "importance" to importance,
        "vibrate" to vibrate,
        "sound" to sound,
        "ios" to ios,
    ).filterValues { it != null }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): AbrovaPushChannel? {
            if (map == null) return null
            val id = map["id"] as? String ?: return null
            return AbrovaPushChannel(
                id = id,
                version = (map["v"] as? Number)?.toInt(),
                name = map["name"] as? Map<String, String>,
                description = map["description"] as? Map<String, String>,
                importance = map["importance"] as? String,
                vibrate = map["vibrate"] as? Boolean,
                sound = map["sound"] as? String,
                ios = map["ios"] as? String,
            )
        }
    }
}

/**
 * Creates the project's channels and picks the channel for each message.
 *
 * Android ids are `abrova_push_v7_ch_<key>_<version>`: the `abrova_push_v7_` prefix keeps NotificationHelper from
 * deleting them, and a new version (importance, vibration or sound changed in the Console) gets a new channel while the
 * old one is deleted. Only channels with this prefix are ever deleted here; the app's own channels are never touched.
 */
internal object AbrovaPushChannels {
    private const val TAG = "AbrovaPushChannels"
    private const val PREFS_NAME = "abrova_push_prefs"
    private const val KEY_CHANNELS = "notification_channels"
    internal const val PREFIX = "abrova_push_v7_ch_"
    private val KEY = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
    private val gson = Gson()
    private val lock = Any()

    fun androidIdOf(def: AbrovaPushChannel): String = "$PREFIX${def.id}_${def.version ?: 1}"

    /** The definitions from the last register answer (or messages), kept for when the app starts offline. */
    fun saved(context: Context): List<AbrovaPushChannel> = synchronized(lock) {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CHANNELS, null)
            ?: return emptyList()
        runCatching {
            gson.fromJson<List<AbrovaPushChannel>>(json, object : TypeToken<List<AbrovaPushChannel>>() {}.type)
                ?.filter { valid(it) }
        }.getOrNull() ?: emptyList()
    }

    private fun save(context: Context, list: List<AbrovaPushChannel>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CHANNELS, gson.toJson(list)).apply()
    }

    private fun valid(def: AbrovaPushChannel?): Boolean = def?.id != null && KEY.matches(def.id)

    /**
     * The project's list from a register answer: every channel is created (so it shows in the phone's settings before
     * the first message), and channels the Console no longer has are deleted.
     */
    fun sync(context: Context, list: List<AbrovaPushChannel>?) {
        if (list == null) return
        val defs = list.filter { valid(it) }
        synchronized(lock) { save(context, defs) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            defs.forEach { ensure(context, manager, it) }
            val keep = defs.map { androidIdOf(it) }.toSet()
            manager.notificationChannels
                .filter { it.id.startsWith(PREFIX) && it.id !in keep }
                .forEach { runCatching { manager.deleteNotificationChannel(it.id) } }
        } catch (e: Exception) {
            AbrovaPushLogger.w(TAG, "Channel sync failed: ${e.message}")
        }
    }

    /**
     * The Android channel for a message, or null for the SDK's own default/sound channel:
     *  1. the definition carried in the message (the server adds it for a Console channel);
     *  2. a saved definition with that key;
     *  3. an Android channel the app created itself with exactly that id;
     *  4. otherwise null, the default channel, as FCM does.
     */
    fun channelFor(context: Context, message: AbrovaPushMessage): String? {
        val requested = message.channelId?.takeIf { it.isNotEmpty() } ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return null
            val def = definitionFor(context, message)
            when {
                def != null -> ensure(context, manager, def)
                manager.getNotificationChannel(requested) != null -> requested
                else -> null
            }
        } catch (e: Exception) {
            AbrovaPushLogger.w(TAG, "Channel '$requested' unavailable, using the default: ${e.message}")
            null
        }
    }

    /** The Console definition behind a message's channelId: the one it carries (remembered), else a saved one. */
    fun definitionFor(context: Context, message: AbrovaPushMessage): AbrovaPushChannel? {
        val requested = message.channelId ?: return null
        val carried = message.channel?.takeIf { valid(it) && it.id == requested }
        if (carried != null) {
            synchronized(lock) {
                val list = saved(context)
                val old = list.firstOrNull { it.id == carried.id }
                if (old == null || (old.version ?: 1) <= (carried.version ?: 1)) {
                    save(context, list.filter { it.id != carried.id } + carried)
                }
            }
            return carried
        }
        return saved(context).firstOrNull { it.id == requested }
    }

    /** Creates the channel (or renames it: Android lets an app change only the name and description). */
    private fun ensure(context: Context, manager: NotificationManager, def: AbrovaPushChannel): String {
        val androidId = androidIdOf(def)
        // Older versions of this channel: their settings can no longer change, the new id replaces them.
        val prefix = "$PREFIX${def.id}_"
        manager.notificationChannels
            .filter { it.id.startsWith(prefix) && it.id != androidId && it.id.removePrefix(prefix).all(Char::isDigit) }
            .forEach { runCatching { manager.deleteNotificationChannel(it.id) } }

        val importance = when (def.importance) {
            "high" -> NotificationManager.IMPORTANCE_HIGH
            "low" -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        val channel = NotificationChannel(androidId, localized(def.name) ?: def.id, importance).apply {
            localized(def.description)?.let { description = it }
            enableVibration(def.vibrates)
            if (!def.vibrates) vibrationPattern = null
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            when (val uri = soundUri(context, def.soundName)) {
                null -> setSound(null, null)
                else -> setSound(uri, attributes)
            }
        }
        manager.createNotificationChannel(channel)
        return androidId
    }

    /** `none` = no sound; `default` or a sound that isn't in res/raw = the phone's notification sound. */
    internal fun soundUri(context: Context, sound: String): Uri? {
        if (sound == "none") return null
        if (sound != "default") {
            val res = context.resources.getIdentifier(sound.substringBeforeLast('.'), "raw", context.packageName)
            if (res != 0) return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/${sound.substringBeforeLast('.')}")
            AbrovaPushLogger.w(TAG, "Sound '$sound' not found in res/raw/, using the default sound")
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /** The phone's language (Farsi or English), English when the name has no Farsi. */
    private fun localized(texts: Map<String, String>?): String? {
        if (texts.isNullOrEmpty()) return null
        val lang = Locale.getDefault().language
        return texts[lang]?.takeIf { it.isNotBlank() } ?: texts["en"]?.takeIf { it.isNotBlank() } ?: texts.values.firstOrNull()
    }
}
