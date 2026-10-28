package ir.abrova.push.sdk

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AbrovaPushAnalytics.
 * Tests analytics event tracking, enable/disable functionality, and callback handling.
 */
class AbrovaPushAnalyticsTest {

    private var receivedEvents: MutableList<Pair<AbrovaPushAnalyticsEvent, Map<String, Any?>>> = mutableListOf()

    @Before
    fun setUp() {
        // Mock the Log object to avoid Android runtime dependency
        mockkObject(Log)
        every { Log.d(any(), any()) } returns Unit
        every { Log.v(any(), any()) } returns Unit
        every { Log.e(any(), any(), any()) } returns Unit

        // Reset analytics state before each test
        AbrovaPushAnalytics.disable()
        AbrovaPushAnalytics.setHandler(null)
        receivedEvents.clear()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        AbrovaPushAnalytics.disable()
        AbrovaPushAnalytics.setHandler(null)
        unmockkObject(Log)
    }

    // ==================== Enable/Disable Tests ====================

    @Test
    fun `analytics is disabled by default`() {
        assertFalse(AbrovaPushAnalytics.isEnabled())
    }

    @Test
    fun `enable enables analytics tracking`() {
        // When
        AbrovaPushAnalytics.enable()

        // Then
        assertTrue(AbrovaPushAnalytics.isEnabled())
    }

    @Test
    fun `disable disables analytics tracking`() {
        // Given
        AbrovaPushAnalytics.enable()

        // When
        AbrovaPushAnalytics.disable()

        // Then
        assertFalse(AbrovaPushAnalytics.isEnabled())
    }

    @Test
    fun `setHandler automatically enables analytics`() {
        // When
        AbrovaPushAnalytics.setHandler { _, _ -> }

        // Then
        assertTrue(AbrovaPushAnalytics.isEnabled())
    }

    @Test
    fun `setHandler with null disables analytics`() {
        // Given
        AbrovaPushAnalytics.setHandler { _, _ -> }
        assertTrue(AbrovaPushAnalytics.isEnabled())

        // When
        AbrovaPushAnalytics.setHandler(null)

        // Then
        assertFalse(AbrovaPushAnalytics.isEnabled())
    }

    // ==================== Event Tracking Tests ====================

    @Test
    fun `track does not call handler when disabled`() {
        // Given
        var handlerCalled = false
        AbrovaPushAnalytics.setHandler { _, _ -> handlerCalled = true }
        AbrovaPushAnalytics.disable()

        // When
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.SDK_INITIALIZED)

        // Then
        assertFalse(handlerCalled)
    }

    @Test
    fun `track calls handler when enabled`() {
        // Given
        AbrovaPushAnalytics.setHandler { event, properties ->
            receivedEvents.add(Pair(event, properties))
        }

        // When
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.SDK_INITIALIZED)

        // Then
        assertEquals(1, receivedEvents.size)
        assertEquals(AbrovaPushAnalyticsEvent.SDK_INITIALIZED, receivedEvents[0].first)
    }

    @Test
    fun `track passes properties correctly`() {
        // Given
        AbrovaPushAnalytics.setHandler { event, properties ->
            receivedEvents.add(Pair(event, properties))
        }

        // When
        AbrovaPushAnalytics.track(
            AbrovaPushAnalyticsEvent.MESSAGE_RECEIVED,
            mapOf("title" to "Test Message", "silent" to false)
        )

        // Then
        assertEquals(1, receivedEvents.size)
        val props = receivedEvents[0].second
        assertEquals("Test Message", props["title"])
        assertEquals(false, props["silent"])
    }

    @Test
    fun `track with single property works correctly`() {
        // Given
        AbrovaPushAnalytics.setHandler { event, properties ->
            receivedEvents.add(Pair(event, properties))
        }

        // When
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.DEVICE_REGISTERED, "device_id", "abc123")

        // Then
        assertEquals(1, receivedEvents.size)
        assertEquals("abc123", receivedEvents[0].second["device_id"])
    }

    @Test
    fun `track with empty properties passes empty map`() {
        // Given
        AbrovaPushAnalytics.setHandler { event, properties ->
            receivedEvents.add(Pair(event, properties))
        }

        // When
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.CONNECTED)

        // Then
        assertEquals(1, receivedEvents.size)
        assertTrue(receivedEvents[0].second.isEmpty())
    }

    // ==================== Multiple Events Test ====================

    @Test
    fun `multiple events are tracked in order`() {
        // Given
        AbrovaPushAnalytics.setHandler { event, properties ->
            receivedEvents.add(Pair(event, properties))
        }

        // When
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.SDK_INITIALIZED)
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.DEVICE_REGISTERED, "device_id", "123")
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.CONNECTED)
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.TOPIC_SUBSCRIBED, "topic", "news")

        // Then
        assertEquals(4, receivedEvents.size)
        assertEquals(AbrovaPushAnalyticsEvent.SDK_INITIALIZED, receivedEvents[0].first)
        assertEquals(AbrovaPushAnalyticsEvent.DEVICE_REGISTERED, receivedEvents[1].first)
        assertEquals(AbrovaPushAnalyticsEvent.CONNECTED, receivedEvents[2].first)
        assertEquals(AbrovaPushAnalyticsEvent.TOPIC_SUBSCRIBED, receivedEvents[3].first)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `track handles callback exception gracefully`() {
        // Given
        AbrovaPushAnalytics.setHandler { _, _ ->
            throw RuntimeException("Test exception")
        }

        // When/Then - should not throw
        AbrovaPushAnalytics.track(AbrovaPushAnalyticsEvent.SDK_INITIALIZED)
    }

    // ==================== Event Types Test ====================

    @Test
    fun `all analytics event types are defined`() {
        // Verify we have all expected event types
        val events = AbrovaPushAnalyticsEvent.values()

        assertTrue(events.contains(AbrovaPushAnalyticsEvent.SDK_INITIALIZED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.DEVICE_REGISTERED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.DEVICE_UNREGISTERED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.MESSAGE_RECEIVED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.MESSAGE_DISPLAYED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.NOTIFICATION_CLICKED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.ACTION_CLICKED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.CONNECTED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.DISCONNECTED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.CONNECTION_ERROR))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.RETRY_STARTED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.TOPIC_SUBSCRIBED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.TOPIC_UNSUBSCRIBED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.NETWORK_STATE_CHANGED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.APP_STATE_CHANGED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.PERMISSION_REQUESTED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.PERMISSION_GRANTED))
        assertTrue(events.contains(AbrovaPushAnalyticsEvent.PERMISSION_DENIED))
    }
}
