package ir.abrova.push.sdk

import android.content.Context
import org.robolectric.RuntimeEnvironment
import ir.abrova.push.sdk.inapp.InAppMessageManager
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SDK triggers "App open" / "Session start" by itself (autoInAppTriggers) and apps may still call them: a
 * second trigger within a few seconds is the same moment (one session, no second fetch-and-show). And the
 * config's defaults keep today's behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class InAppAutoTriggerTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(200).setBody("[]")) }
        AbrovaPushConfig.SERVER_URL = server.url("/").toString().removeSuffix("/")
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("abrova_push_inapp", Context.MODE_PRIVATE).edit().clear().commit()
        resetManager()
    }

    @After
    fun tearDown() {
        server.shutdown()
        AbrovaPushConfig.resetDefaults()
        resetManager()
    }

    private fun resetManager() {
        val field = InAppMessageManager::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun manager() = InAppMessageManager.getInstance(
        context, ApiClient(AbrovaPushConfig(apiKey = "test_api_key")), "app", "device-1"
    )

    private fun sessionCount() =
        context.getSharedPreferences("abrova_push_inapp", Context.MODE_PRIVATE).getInt("session_count", 0)

    @Test
    fun `two session starts at once count one session`() {
        val m = manager()
        m.triggerOnSessionStart()
        m.triggerOnSessionStart()
        assertEquals(1, sessionCount())
    }

    @Test
    fun `two app opens at once fetch once`() {
        val m = manager()
        m.triggerOnAppOpen()
        Thread.sleep(300)
        val afterFirst = server.requestCount
        m.triggerOnAppOpen()
        Thread.sleep(300)
        assertEquals(afterFirst, server.requestCount)
    }

    @Test
    fun `defaults keep today's behaviour`() {
        val cfg = AbrovaPushConfig(apiKey = "k")
        assertTrue(cfg.askNotificationPermission)
        assertTrue(cfg.autoInAppTriggers)
        assertFalse(AbrovaPushConfig(apiKey = "k", askNotificationPermission = false).askNotificationPermission)
    }
}
