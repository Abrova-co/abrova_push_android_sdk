package ir.abrova.push.sdk

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A message with data.conversationId is shown chat-style: one notification per conversation, messages stacked. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ConversationNotificationTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var helper: NotificationHelper

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        helper = NotificationHelper(context, AbrovaPushConfig(apiKey = "ab_live_test"))
    }

    private fun message(body: String, data: Map<String, Any>?, title: String = "@kian") =
        AbrovaPushMessage(title = title, body = body, data = data)

    private fun shown(): List<Notification> {
        shadowOf(Looper.getMainLooper()).idle()
        return shadowOf(manager).allNotifications
    }

    @Test
    fun `messages of one conversation stack in one notification`() {
        val data = mapOf("conversationId" to "room-1", "senderName" to "@kian", "senderId" to "u-kian")
        helper.showNotification(message("salam", data))
        helper.showNotification(message("khoobi?", data))

        val all = shown()
        assertEquals(1, all.size)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(all[0])
        assertNotNull(style)
        assertEquals(listOf("salam", "khoobi?"), style!!.messages.map { it.text.toString() })
        assertEquals("@kian", style.messages.last().person?.name.toString())
        assertFalse(style.isGroupConversation)
        assertEquals(Notification.CATEGORY_MESSAGE, all[0].category)
    }

    @Test
    fun `two conversations get two notifications, groups carry their title`() {
        helper.showNotification(message("a", mapOf("conversationId" to "room-1", "senderName" to "@kian")))
        helper.showNotification(message("b", mapOf("conversationId" to "room-2", "senderName" to "@ali", "conversationTitle" to "Family", "conversationIsGroup" to "true")))

        val all = shown()
        assertEquals(2, all.size)
        val group = all.map { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)!! }.first { it.isGroupConversation }
        assertEquals("Family", group.conversationTitle.toString())
    }

    @Test
    fun `a message without conversationId looks as before`() {
        helper.showNotification(message("hello", mapOf("senderName" to "@kian")))
        helper.showNotification(message("again", null))

        val all = shown()
        assertEquals(2, all.size)
        all.forEach { assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)) }
    }

    @Test
    fun `parser reads FCM string values and ignores bad ones`() {
        assertNull(ConversationInfo.from(mapOf("senderName" to "x"), "t"))
        assertNull(ConversationInfo.from(mapOf("conversationId" to "  "), "t"))
        val c = ConversationInfo.from(
            mapOf("conversationId" to "r", "conversationIsGroup" to "TRUE", "senderAvatarUrl" to "javascript:x"),
            "Title",
        )!!
        assertEquals("Title", c.senderName)
        assertTrue(c.isGroup)
        assertNull(c.senderAvatarUrl)
        assertTrue(ConversationInfo.from(mapOf("conversationId" to "r", "conversationIsGroup" to true), "t")!!.isGroup)
    }

    @Test
    fun `letter avatar is drawn for any name`() {
        listOf("@kian", "علی", "", "  ").forEach {
            val b = ConversationNotification.letterAvatar(it)
            assertEquals(192, b.width)
        }
    }
}
