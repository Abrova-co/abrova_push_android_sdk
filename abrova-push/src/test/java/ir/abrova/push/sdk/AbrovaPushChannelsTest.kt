package ir.abrova.push.sdk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** Notification channels defined in Abrova Console. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AbrovaPushChannelsTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    private val offers = AbrovaPushChannel(
        id = "offers", version = 1, name = mapOf("en" to "Offers", "fa" to "پیشنهادها"),
        importance = "low", vibrate = false, sound = "none",
    )
    private val orders = AbrovaPushChannel(id = "orders", version = 1, name = mapOf("en" to "Orders"), importance = "high")

    private fun msg(channelId: String?, channel: AbrovaPushChannel? = null) =
        AbrovaPushMessage(title = "t", body = "b", channelId = channelId, channel = channel)

    private fun ours() = manager.notificationChannels.map { it.id }.filter { it.startsWith(AbrovaPushChannels.PREFIX) }.sorted()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(NotificationManager::class.java)
        context.getSharedPreferences("abrova_push_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        manager.notificationChannels.forEach { manager.deleteNotificationChannel(it.id) }
        Locale.setDefault(Locale.ENGLISH)
    }

    @Test
    fun `register list creates every channel with its importance, vibration and sound`() {
        AbrovaPushChannels.sync(context, listOf(offers, orders))
        assertEquals(listOf("abrova_push_v7_ch_offers_1", "abrova_push_v7_ch_orders_1"), ours())
        val quiet = manager.getNotificationChannel("abrova_push_v7_ch_offers_1")
        assertEquals(NotificationManager.IMPORTANCE_LOW, quiet.importance)
        assertFalse(quiet.shouldVibrate())
        assertNull(quiet.sound)
        assertEquals("Offers", quiet.name)
        val loud = manager.getNotificationChannel("abrova_push_v7_ch_orders_1")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, loud.importance)
        assertTrue(loud.shouldVibrate())
        assertNotNull(loud.sound)
    }

    @Test
    fun `the phone language picks the name and English when there is no Farsi`() {
        Locale.setDefault(Locale("fa"))
        AbrovaPushChannels.sync(context, listOf(offers, orders))
        assertEquals("پیشنهادها", manager.getNotificationChannel("abrova_push_v7_ch_offers_1").name)
        assertEquals("Orders", manager.getNotificationChannel("abrova_push_v7_ch_orders_1").name)
    }

    @Test
    fun `a new version replaces the old channel, a removed channel goes, the app own channels stay`() {
        manager.createNotificationChannel(NotificationChannel("app_own", "Mine", NotificationManager.IMPORTANCE_DEFAULT))
        AbrovaPushChannels.sync(context, listOf(offers, orders))
        AbrovaPushChannels.sync(context, listOf(offers.copy(version = 2, vibrate = true)))
        assertEquals(listOf("abrova_push_v7_ch_offers_2"), ours())
        assertTrue(manager.getNotificationChannel("abrova_push_v7_ch_offers_2").shouldVibrate())
        assertNotNull(manager.getNotificationChannel("app_own"))
    }

    @Test
    fun `a message picks its channel - carried definition, saved one, the app own, else the default`() {
        // Carried in the message: created on the spot, remembered for later messages.
        assertEquals("abrova_push_v7_ch_offers_1", AbrovaPushChannels.channelFor(context, msg("offers", offers)))
        assertEquals("abrova_push_v7_ch_offers_1", AbrovaPushChannels.channelFor(context, msg("offers")))
        // A carried definition for another key is ignored (it can't rename a different channel).
        assertNull(AbrovaPushChannels.channelFor(context, msg("unknown", offers)))
        // The app's own channel, used as-is.
        manager.createNotificationChannel(NotificationChannel("app_own", "Mine", NotificationManager.IMPORTANCE_DEFAULT))
        assertEquals("app_own", AbrovaPushChannels.channelFor(context, msg("app_own")))
        // Nothing asked: the SDK's default/sound channel.
        assertNull(AbrovaPushChannels.channelFor(context, msg(null)))
    }

    @Test
    fun `the message model round-trips channelId and channel through JSON and the wrapper map`() {
        val m = msg("offers", offers)
        val back = AbrovaPushMessage.fromJson(m.toJson())!!
        assertEquals("offers", back.channelId)
        assertEquals(false, back.channel?.vibrate)
        val viaMap = AbrovaPushMessage.fromMap(m.toMap())!!
        assertEquals("offers", viaMap.channelId)
        assertEquals("none", viaMap.channel?.sound)
        // From the server's JSON (what MQTT and FCM carry).
        val server = AbrovaPushMessage.fromJson(
            """{"title":"t","body":"b","channelId":"offers","channel":{"id":"offers","v":3,"name":{"en":"Offers"},"importance":"low","vibrate":false,"sound":"none","ios":"passive"}}"""
        )!!
        assertEquals(3, server.channel?.version)
        assertEquals("abrova_push_v7_ch_offers_3", AbrovaPushChannels.channelFor(context, server))
    }
}
