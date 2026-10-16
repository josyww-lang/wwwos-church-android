package org.yourchurch.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Each phone has a "push token", the address Firebase uses to send it notifications. We keep the tokens of
 * the signed-in person in fcmTokens/{uid}, so the notification server knows where to send their messages.
 * The token is removed when they sign out or switch notifications off.
 */
object PushTokens {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun register(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        withTimeout(15_000) {
            db.collection("fcmTokens").document(uid)
                .set(mapOf("tokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
        }
    }

    /** Is this phone's address saved for this person? Used by the notification check. */
    suspend fun isRegistered(uid: String): Boolean {
        val token = FirebaseMessaging.getInstance().token.await()
        val snapshot = withTimeout(15_000) { db.collection("fcmTokens").document(uid).get().await() }
        return (snapshot.get("tokens") as? List<*>)?.contains(token) == true
    }

    suspend fun unregister(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        withTimeout(15_000) {
            db.collection("fcmTokens").document(uid)
                .set(mapOf("tokens" to FieldValue.arrayRemove(token)), SetOptions.merge()).await()
        }
    }
}
