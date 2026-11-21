package ir.abrova.push.sdk

import android.os.Looper
import com.google.gson.Gson
import ir.abrova.push.sdk.inapp.InAppMessage
import ir.abrova.push.sdk.inapp.InAppMessageContent
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * notificationsEnabled on register (with an older server that refuses the field), the in-app fetch's
 * ETag/304, and the banner position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NotificationsAndInAppSignalTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient
    private val gson = Gson()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        AbrovaPushConfig.SERVER_URL = server.url("/").toString().removeSuffix("/")
        api = ApiClient(AbrovaPushConfig(apiKey = "test_api_key"))
    }

    @After
    fun tearDown() {
        server.shutdown()
        AbrovaPushConfig.resetDefaults()
    }

    private fun await(latch: CountDownLatch) {
        val deadline = System.currentTimeMillis() + 5000
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(30)
        }
        assertEquals(0L, latch.count)
    }

    private fun register(extra: Map<String, Any?>?): Pair<Boolean, String?> {
        val latch = CountDownLatch(1)
        var ok = false
        var error: String? = null
        api.registerDevice(
            deviceId = "d1",
            extraFields = extra,
            callback = object : ApiClient.ApiCallback<ApiClient.RegisterResponse> {
                override fun onSuccess(response: ApiClient.RegisterResponse) { ok = true; latch.countDown() }
                override fun onError(message: String) { error = message; latch.countDown() }
            }
        )
        await(latch)
        return ok to error
    }

    @Test
    fun `register sends notificationsEnabled`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"deviceId\":\"d1\"}"))
        val (ok, _) = register(mapOf("notificationsEnabled" to false))
        assertTrue(ok)
        val body = gson.fromJson(server.takeRequest().body.readUtf8(), Map::class.java)
        assertEquals(false, body["notificationsEnabled"])
    }

    @Test
    fun `an older server that refuses the field gets the register again without it`() {
        var refused = 0
        api.onNotificationsFieldRefused = { refused++ }
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("{\"statusCode\":400,\"message\":[\"property notificationsEnabled should not exist\"]}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"deviceId\":\"d1\"}"))

        val (ok, _) = register(mapOf("notificationsEnabled" to true, "fcmToken" to "t"))

        assertTrue(ok)
        assertEquals(1, refused)
        val first = gson.fromJson(server.takeRequest().body.readUtf8(), Map::class.java)
        val second = gson.fromJson(server.takeRequest().body.readUtf8(), Map::class.java)
        assertEquals(true, first["notificationsEnabled"])
        assertFalse(second.containsKey("notificationsEnabled"))
        assertEquals("t", second["fcmToken"]) // the other fields stay
    }

    @Test
    fun `another 400 is an error, not a retry`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"message\":[\"deviceId must be a string\"]}"))
        val (ok, error) = register(mapOf("notificationsEnabled" to true))
        assertFalse(ok)
        assertEquals("Registration failed: 400", error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `in-app fetch sends the etag and reads 304 as unchanged`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "W/\"abc\"").setBody("{\"messages\":[]}"))
        server.enqueue(MockResponse().setResponseCode(304))

        val first = api.fetchInAppMessages(mapOf("deviceId" to "d1"), null)!!
        assertFalse(first.notModified)
        assertEquals("W/\"abc\"", first.etag)
        assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))

        val second = api.fetchInAppMessages(mapOf("deviceId" to "d1"), first.etag)!!
        assertTrue(second.notModified)
        assertNull(second.body)
        assertEquals("W/\"abc\"", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
    }

    @Test
    fun `banner position is read, kept for translations and handed to wrappers`() {
        val json = JSONObject(
            """{"id":"m1","name":"n","type":"banner",
               "content":{"title":"Hi","body":"b","position":"bottom"},
               "localizations":[{"locale":"fa","content":{"title":"سلام","body":"ب"}}]}"""
        )
        val m = InAppMessage.fromJson(json)
        assertEquals("bottom", m.content.position)
        assertEquals("bottom", m.getLocalizedContent("fa-IR").position)
        assertEquals("bottom", m.content.toMap()["position"])
        assertNull(InAppMessageContent.fromJson(JSONObject("""{"title":"a","position":"middle"}""")).position)
    }
}
