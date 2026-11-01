package ir.abrova.push.sdk

import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        reset()
    }

    @After
    fun tearDown() = reset()

    private fun reset() {
        AbrovaPushService.config = null
        AbrovaPushService.subscriptionId = null
        context.getSharedPreferences("abrova_push_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun saved(subscriptionId: String?) {
        context.getSharedPreferences("abrova_push_prefs", Context.MODE_PRIVATE).edit()
            .putString("apiKey", "ab_live_test")
            .putString("device_id", "device-1")
            .putString("appId", "project-1")
            .apply { if (subscriptionId != null) putString("subscription_id", subscriptionId) }
            .commit()
    }

    private fun boot() = BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

    @Test
    fun `boot restores the saved subscriptionId for the service`() {
        saved("sub-1")
        boot()
        assertEquals("sub-1", AbrovaPushService.subscriptionId)
    }

    @Test
    fun `boot before the first registration leaves it empty`() {
        saved(null)
        boot()
        assertNull(AbrovaPushService.subscriptionId)
    }
}
