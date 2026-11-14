package ir.abrova.push.sdk

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Chat-style notifications: the sender's round photo, the messages of one conversation stacked in one notification,
 * and (Android 11+) the conversation in the phone's "Conversations" section.
 *
 * A message is shown this way when its `data` has a `conversationId`. Every other message looks as before. Keys:
 *  - `conversationId` (required): one notification per id; a new message is added to it.
 *  - `senderName`: who wrote it (else the title).
 *  - `senderId`: tells two senders with the same name apart.
 *  - `senderAvatarUrl`: an https image; without one a round letter is drawn.
 *  - `conversationTitle`: the group's name (shown for groups).
 *  - `conversationIsGroup`: `true` for a group.
 * The message body is the text shown; `imageUrl` is ignored in this style.
 */
internal data class ConversationInfo(
    val id: String,
    val senderName: String,
    val senderId: String?,
    val senderAvatarUrl: String?,
    val title: String?,
    val isGroup: Boolean,
) {
    companion object {
        private const val MAX_ID = 200

        /** The conversation of a message, or null when its data has no `conversationId`. */
        fun from(data: Map<String, Any?>?, fallbackSender: String): ConversationInfo? {
            if (data == null) return null
            fun text(key: String) = data[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val id = text("conversationId")?.take(MAX_ID) ?: return null
            val avatar = text("senderAvatarUrl")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val group = data["conversationIsGroup"].let { it == true || it?.toString()?.equals("true", ignoreCase = true) == true }
            return ConversationInfo(
                id = id,
                senderName = text("senderName") ?: fallbackSender.ifEmpty { "?" },
                senderId = text("senderId"),
                senderAvatarUrl = avatar,
                title = text("conversationTitle"),
                isGroup = group,
            )
        }
    }

    /** One notification per conversation (tag + fixed id). */
    val tag: String get() = "abrova_conversation:$id"
    val shortcutId: String get() = "abrova_conversation_$id"
}

internal object ConversationNotification {
    const val NOTIFICATION_ID = 7_301
    private const val AVATAR_SIZE = 192

    /**
     * Turns [builder] into a conversation notification: MessagingStyle with the earlier messages still on screen,
     * the sender as a Person, and a long-lived shortcut so Android 11+ files it under Conversations.
     */
    fun apply(
        context: Context,
        builder: NotificationCompat.Builder,
        conversation: ConversationInfo,
        body: String,
        avatar: Bitmap?,
        launchIntent: Intent?,
    ) {
        val icon = IconCompat.createWithBitmap(circle(avatar) ?: letterAvatar(conversation.senderName))
        val sender = Person.Builder()
            .setName(conversation.senderName)
            .setKey(conversation.senderId ?: conversation.senderName)
            .setIcon(icon)
            .build()
        val me = Person.Builder().setName("Me").setKey("abrova_me").build()

        val style = previousStyle(context, conversation) ?: NotificationCompat.MessagingStyle(me)
        style.addMessage(NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender))
        style.setGroupConversation(conversation.isGroup)
        if (conversation.isGroup) style.setConversationTitle(conversation.title)
        // Only the last few are kept on screen; Android keeps 25 at most anyway.
        while (style.messages.size > 10) style.messages.removeAt(0)

        builder.setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setLargeIcon(null as Bitmap?)

        val shortcutLabel = if (conversation.isGroup) conversation.title ?: conversation.senderName else conversation.senderName
        if (launchIntent != null && pushShortcut(context, conversation, shortcutLabel, sender, icon, launchIntent)) {
            builder.setShortcutId(conversation.shortcutId)
        }
    }

    /** The conversation's notification still on screen, as a MessagingStyle to add to (null = none, or Android < 7). */
    private fun previousStyle(context: Context, conversation: ConversationInfo): NotificationCompat.MessagingStyle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return null
            val active = manager.activeNotifications.firstOrNull { it.tag == conversation.tag && it.id == NOTIFICATION_ID }
                ?: return null
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(active.notification)
        }.getOrNull()
    }

    private fun pushShortcut(
        context: Context,
        conversation: ConversationInfo,
        label: String,
        sender: Person,
        icon: IconCompat,
        launchIntent: Intent,
    ): Boolean = runCatching {
        // A shortcut's intent needs an action; the launcher intent has ACTION_MAIN.
        val intent = Intent(launchIntent).apply { if (action == null) action = Intent.ACTION_MAIN }
        val shortcut = ShortcutInfoCompat.Builder(context, conversation.shortcutId)
            .setShortLabel(label.take(40).ifEmpty { "?" })
            .setLongLived(true)
            .setIcon(icon)
            .setIntent(intent)
            .apply { if (!conversation.isGroup) setPerson(sender) }
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        true
    }.getOrElse {
        Log.w("Notification", "Conversation shortcut not added: ${it.message}")
        false
    }

    /** A round crop of [bitmap] (null stays null). */
    private fun circle(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val side = minOf(bitmap.width, bitmap.height)
        if (side <= 0) return null
        val out = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        val scale = AVATAR_SIZE.toFloat() / side
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            val m = android.graphics.Matrix()
            m.setScale(scale, scale)
            m.postTranslate(-(bitmap.width - side) / 2f * scale, -(bitmap.height - side) / 2f * scale)
            setLocalMatrix(m)
        }
        Canvas(out).drawCircle(AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        return out
    }

    private val COLORS = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(), 0xFFAB47BC.toInt(),
        0xFF42A5F5.toInt(), 0xFFEC407A.toInt(), 0xFF7E57C2.toInt(), 0xFF66BB6A.toInt(),
    )

    /** A colored circle with the name's first letter (the color is fixed per name). */
    fun letterAvatar(name: String): Bitmap {
        val out = Bitmap.createBitmap(AVATAR_SIZE, AVATAR_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val color = COLORS[Math.floorMod(name.hashCode(), COLORS.size)]
        canvas.drawCircle(AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, AVATAR_SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val letter = name.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "?"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = AVATAR_SIZE * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val y = AVATAR_SIZE / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, AVATAR_SIZE / 2f, y, paint)
        return out
    }
}
