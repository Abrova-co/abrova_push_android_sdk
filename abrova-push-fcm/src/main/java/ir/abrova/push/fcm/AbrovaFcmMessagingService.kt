package ir.abrova.push.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Abrova Push messages over FCM. Declared in the add-on manifest.
 * If your app has its own FirebaseMessagingService, remove this one and
 * forward to [AbrovaPushFcm] instead.
 */
class AbrovaFcmMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        AbrovaPushFcm.handleMessage(applicationContext, message)
    }

    override fun onNewToken(token: String) {
        AbrovaPushFcm.onNewToken(applicationContext, token)
    }
}
