# Abrova Push Android SDK

Native Android SDK for real-time push notifications. No Firebase dependency - full control over your push infrastructure.

## Features

- Real-time push
- Survives app close and device restart
- Rich notifications with images, actions, and deep links
- In-app messaging (modal, banner, fullscreen, card)
- Message inbox with persistent storage
- Topic subscription for targeted messaging
- A/B testing support
- User segmentation
- Analytics and delivery tracking
- No Google/Firebase dependency

## Installation

The SDK is published on [Abrova Center](https://center.abrova.ir). Add its repository in `settings.gradle.kts` (or `settings.gradle`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://center.abrova.ir/api/packages/abrova/maven") }
    }
}
```

Then add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ir.abrova:abrova-push-android:0.1.20")
}
```

### Permissions

The SDK requires these permissions (automatically merged via manifest):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

## Quick Start

### 1. Initialize in Application class

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = AbrovaPushConfig(
            apiKey = "your_api_key_here"
        )
        AbrovaPush.init(this, config)
    }
}
```

### 2. Register and listen for messages

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Set up callbacks
        AbrovaPush.setCallback(object : AbrovaPushCallbackAdapter() {
            override fun onMessageReceived(message: AbrovaPushMessage) {
                Log.d("Push", "Title: ${message.title}, Body: ${message.body}")
            }

            override fun onConnectionStateChanged(connected: Boolean) {
                Log.d("Push", "Connected: $connected")
            }

            override fun onRegistered(deviceId: String) {
                Log.d("Push", "Registered: $deviceId")
            }

            override fun onError(error: String) {
                Log.e("Push", "Error: $error")
            }
        })

        // Register on every launch: it keeps the device's token, app version
        // and last-seen time current on the server.
        AbrovaPush.register(
            userId = "user_123",  // optional
            metadata = mapOf(     // optional
                "plan" to "premium",
                "language" to "en"
            )
        )
    }
}
```

### 3. Handle notification taps

```kotlin
val initialMessage = AbrovaPush.getInitialMessage()
if (initialMessage != null) {
    Log.d("Push", "Opened from notification: ${initialMessage.title}")
    AbrovaPush.clearInitialMessage()
}
```

Taps are picked up by the SDK on its own. If your activity is `singleTop` (or otherwise already in front when a
notification is tapped), the tap arrives in `onNewIntent` — pass it on there:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
}
```

## Device Management

### User ID

```kotlin
// Set user ID (after login)
AbrovaPush.setUserId("user_123")

// Clear user ID (after logout) — also detaches the user on the server,
// so this device stops receiving the previous user's notifications
AbrovaPush.clearUserId()
```

### Signed user tokens

Optional. The API key in your app is public, so a user ID alone does not prove
who the user is. Give the SDK a `tokenProvider` that asks **your server** for a
Abrova user token - the same token, and the same function, you use with Abrova Chat:

```kotlin
val config = AbrovaPushConfig(
    apiKey = "ab_live_your_api_key",
    tokenProvider = { myBackend.getAbrovaToken() }  // suspend; null when signed out
)
AbrovaPush.init(this, config)

// Revoked or invalid token - send the user to login.
AbrovaPush.setAuthErrorListener { error -> Log.w("Push", error.code) }
```

The SDK caches the token, refreshes it before it expires and retries once if the
server reports it expired. If the provider fails, requests are sent without a
token, as before. You can also set the provider later with
`AbrovaPush.setTokenProvider(...)` (Java: `setBlockingTokenProvider`), or pass a
token you fetched yourself with `AbrovaPush.setUserToken(token)`.

### Topics

```kotlin
// Subscribe
AbrovaPush.subscribeTopic("news")

// Unsubscribe
AbrovaPush.unsubscribeTopic("news")
```

## Rich Notifications

| Field | Type | Description |
|-------|------|-------------|
| `imageUrl` | string | Large image in the notification |
| `actions` | array | Action buttons (max 3) |
| `sound` | string | Custom sound or "default" |
| `deepLink` | string | Deep link URI |
| `priority` | string | "high" or "normal" |
| `ttl` | number | Time-to-live in seconds |

## In-App Messages

```kotlin
AbrovaPush.setInAppMessageCallback(object : InAppMessageCallback {
    override fun onMessageReady(message: InAppMessage) { }
    override fun onButtonClick(message: InAppMessage, button: InAppButton) { }
    override fun onMessageDismissed(message: InAppMessage) { }
})

// Trigger messages
AbrovaPush.triggerInAppOnAppOpen()
AbrovaPush.triggerInAppEvent("viewed_product")
```

## Message Inbox

```kotlin
// Fetch messages
AbrovaPush.getInboxMessages(
    filter = InboxFilter(limit = 50),
    onSuccess = { response ->
        val messages = response.messages
        val unread = response.unreadCount
    },
    onError = { error -> Log.e("Inbox", error) }
)

// Real-time updates
AbrovaPush.getInboxManager().setCallback(object : InboxCallback {
    override fun onMessageReceived(message: InboxMessage) {
        // New inbox message arrived
    }
    override fun onMessageStatusChanged(messageId: String, status: InboxMessageStatus) { }
})

// Get unread count
val count = AbrovaPush.getInboxManager().getUnreadCount()
```

## Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `apiKey` | String | **required** | Your API key from the dashboard |
| `notificationIcon` | String? | null | Notification icon resource name |
| `showServiceNotification` | Boolean | true | Show the persistent foreground-service notification |
| `showNotificationInForeground` | Boolean | true | Show notifications when app is in foreground |
| `autoRefresh` | Boolean | true | On `init()`, silently refresh a previously registered device every 24h or when app version/build, SDK version or userId changed |
| `wrapperSdkName` | String? | null | **Official wrappers only** (e.g. `"flutter"`, `"react-native"`). Reported instead of `android` |
| `wrapperSdkVersion` | String? | null | **Official wrappers only.** Version reported with `wrapperSdkName` |
| `tokenProvider` | suspend () -> String? | null | Returns a signed user token from your server. See [Signed user tokens](#signed-user-tokens) |
| `sendDiagnostics` | Boolean | true | Send counts of the SDK's own errors (by error code, no personal data) with its requests |

The SDK reports its identity to the server (`sdkName`/`sdkVersion` on register and an `X-Abrova-SDK: android/<version>` header on every request). The native version is available as `AbrovaPush.SDK_VERSION`. Wrapper values must be at most 32 characters of `A-Za-z0-9._+-` and are ignored unless both are set.

On register the SDK sends `installId`, a hash of the device's `ANDROID_ID` and the
package name. It lets the server retire the record left by a previous install, so
a reinstall does not leave a stale device behind. The raw `ANDROID_ID` never
leaves the device.

## Delivery Tracking

Every notification the device receives is confirmed to Abrova Push, so the
**Delivery** page in the Abrova Console shows it as *delivered*, not just *sent*.
Confirmations are sent once per message (redeliveries are skipped) and retried
a few times on transient network errors. No setup is needed.

The Console also shows, per device, the SDK and app version it runs and when it
was last seen.

## FCM Add-on (Optional)

The core SDK has no Firebase dependency. Apps that use Firebase can add a second
delivery path, which wakes the app when Android has closed the Abrova connection:

```kotlin
dependencies {
    implementation("ir.abrova:abrova-push-fcm:0.1.0")
}
```

Messages then arrive over both paths and are shown once. Without Firebase or Google
Play services the add-on does nothing. Setup: [abrova-push-fcm/README.md](abrova-push-fcm/README.md).

## VoIP Calls (Optional)

For incoming call UI, add the [VoIP SDK](https://github.com/Abrova-co/abrova_push_voip_android_sdk):

```kotlin
dependencies {
    implementation("ir.abrova:abrova-push-android:0.1.20")
    implementation("ir.abrova:abrova-push-voip:0.1.1")  // Optional
}
```

```kotlin
import ir.abrova.push.voip.AbrovaPushVoip
import ir.abrova.push.voip.VoipConfig
import ir.abrova.push.voip.VoipCallback

// Initialize
AbrovaPushVoip.initialize(this, VoipConfig(appName = "MyApp"))

// Handle call events
AbrovaPushVoip.setCallback(object : VoipCallback {
    override fun onCallAccepted(callData: CallData) {
        // Connect to your calling service (Jitsi, WebRTC)
    }
    override fun onCallDeclined(callData: CallData) { }
    override fun onCallTimeout(callData: CallData) { }
})
```

Send push with `{"type": "voip_call", "callerName": "John"}` to trigger the incoming call screen automatically.

The Push SDK works independently without VoIP. VoIP is only needed for apps with real calling features.

## Example App

Open the **repository root** in Android Studio (not the `example/` folder on its
own) and run the `example` configuration. The app reads your API key from the
`ABROVA_PUSH_API_KEY` Gradle property; set it in `~/.gradle/gradle.properties`
so it never lands in the repo:

```properties
ABROVA_PUSH_API_KEY=ab_live_your_api_key
```

The example includes:
- Push notification receiving
- In-app message triggers
- Inbox management
- A/B test variant assignment
- VoIP calling (simulate and receive)
- Settings and debugging tools

## Links

- [Abrova Push](https://abrova.ir/cloud/abrova-push) - Learn more about Abrova Push
- [Documentation](https://abrova.ir/cloud/abrova-push/docs/quick-start) - Full documentation and guides
- [Abrova Console](https://console.abrova.ir) - Manage your push notifications

## License

MIT License - see [LICENSE](LICENSE) for details.
