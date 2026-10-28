package ir.abrova.push.example

import android.app.Application
import android.util.Log
import ir.abrova.push.sdk.AbrovaPush
import ir.abrova.push.sdk.AbrovaPushConfig
import ir.abrova.push.sdk.AbrovaPushLogLevel
// Uncomment if using VoIP module: implementation("ir.abrova:abrova-push-voip-android:0.1.0")
// import ir.abrova.push.voip.AbrovaPushVoip
// import ir.abrova.push.voip.VoipConfig

/**
 * Example Application class demonstrating Abrova Push SDK initialization.
 *
 * This is the first entry point where you MUST initialize the Abrova Push SDK.
 * The SDK should be initialized in Application.onCreate() before any activities start.
 */
class ExampleApplication : Application() {

    companion object {
        private const val TAG = "AbrovaPushExample"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Application onCreate - Initializing Abrova Push SDK")

        // Initialize Abrova Push SDK
        initializeAbrovaPush()

        // Initialize VoIP (optional - only if your app handles calls)
        // initializeVoip()
    }

    private fun initializeAbrovaPush() {
        // API key is read from local.properties (gitignored).
        // Copy local.properties.example → local.properties and fill in your values.
        val apiKey = BuildConfig.ABROVA_PUSH_API_KEY

        // Create configuration - only apiKey is required
        val config = AbrovaPushConfig(
            apiKey = apiKey,
            notificationIcon = "ic_notification",
            showServiceNotification = false  // Hide foreground service notification
        )

        // Initialize SDK
        AbrovaPush.init(this, config)

        // Set log level (DEBUG for development, ERROR or NONE for production)
        AbrovaPush.setLogLevel(AbrovaPushLogLevel.DEBUG)

        Log.i(TAG, "Abrova Push SDK initialized (debug=${BuildConfig.DEBUG})")
    }

    // Uncomment if using VoIP module
    // private fun initializeVoip() {
    //     val voipConfig = VoipConfig(
    //         appName = getString(R.string.app_name),
    //         ringtoneUri = null,
    //         timeoutSeconds = 30,
    //         showMissedCallNotification = true,
    //         callerNameKey = "callerName",
    //         callerIdKey = "callerId",
    //         callerAvatarKey = "callerAvatar",
    //         callTypeKey = "callType"
    //     )
    //     AbrovaPushVoip.initialize(this, voipConfig)
    //     Log.i(TAG, "Abrova Push VoIP initialized")
    // }
}
