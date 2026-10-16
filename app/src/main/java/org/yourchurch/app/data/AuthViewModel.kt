package org.yourchurch.app.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.yourchurch.app.R

/** Sign-in problems. The screen turns these into text in the person's own language. */
enum class AuthError(@StringRes val messageRes: Int) {
    EnterEmail(R.string.err_enter_email),
    NoConnection(R.string.err_no_connection),
    TooManyTries(R.string.err_too_many),
    BadCredentials(R.string.err_bad_credentials),
    InvalidEmail(R.string.err_invalid_email),
    EmailInUse(R.string.err_email_in_use),
    WeakPassword(R.string.err_weak_password),
    Generic(R.string.err_generic)
}

data class AuthState(
    val user: FirebaseUser? = null,
    /** True for leaders and for owners: the leader tools are shown. */
    val isAdmin: Boolean = false,
    /** True only for the church's owners (the accounts added in the Firebase console): they can give leader access. */
    val isOwner: Boolean = false,
    /** Set when the profile or leader status could not be loaded from the church server (the reason, short). */
    val loadError: String? = null,
    /** Name from the person's profile (users/{uid}). Empty until they add one. */
    val displayName: String = "",
    /** Phone number from the profile, like +4791234567. Empty if they haven't added one. */
    val phone: String = "",
    /** True once we know for sure whether a profile name exists (false if we couldn't check). */
    val profileLoaded: Boolean = false,
    /** False until we know whether someone is signed in (and, if so, their role and profile). */
    val ready: Boolean = false,
    val working: Boolean = false,
    val error: AuthError? = null,
    val resetSent: Boolean = false,
    val deleting: Boolean = false,
    val deleteError: AuthError? = null,
    /** Has the person clicked the link in the verification email? */
    val emailVerified: Boolean = false,
    val verificationSent: Boolean = false,
    val verifyWorking: Boolean = false,
    /** True after "I've verified" was tapped but Firebase still says not verified. */
    val verifyNotYet: Boolean = false,
    val verifyError: AuthError? = null
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** The name and phone typed on the "create account" form, saved as soon as the account exists. */
    private var pendingName: String? = null
    private var pendingPhone: String? = null

    private val listener = FirebaseAuth.AuthStateListener { fa ->
        val user = fa.currentUser
        if (user == null) {
            AvatarStore.clear()
            _state.update {
                it.copy(
                    user = null, isAdmin = false, isOwner = false, displayName = "", phone = "", profileLoaded = false,
                    emailVerified = false, verificationSent = false, verifyNotYet = false,
                    verifyError = null, loadError = null, ready = true
                )
            }
        } else {
            _state.update { it.copy(user = user, emailVerified = user.isEmailVerified) }
            loadProfile(user)
        }
    }

    private fun describe(e: Throwable): String =
        (e as? FirebaseFirestoreException)?.code?.name ?: (e.message ?: e.javaClass.simpleName).take(60)

    /**
     * Loads this person's name and whether they are a leader. What was known last time is shown at once, and if the
     * church server cannot be reached the app keeps it and tries again a few times, instead of quietly treating the
     * person as "not a leader, no name".
     */
    private fun loadProfile(user: FirebaseUser) {
        viewModelScope.launch {
            val prefs = FirebaseApp.getInstance().applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            var admin = prefs.getBoolean("auth_admin_${user.uid}", false)
            var owner = prefs.getBoolean("auth_owner_${user.uid}", false)
            var name = prefs.getString("auth_name_${user.uid}", "").orEmpty()
            var phone = prefs.getString("auth_phone_${user.uid}", "").orEmpty()

            val newName = pendingName
            val newPhone = pendingPhone
            pendingName = null
            pendingPhone = null
            if (newName != null) {
                // A new account: save the profile the person just typed.
                runCatching { writeProfile(user.uid, newName, user.email.orEmpty(), newPhone.orEmpty()) }
                name = newName
                phone = newPhone.orEmpty()
            }
            _state.update { it.copy(isAdmin = admin, isOwner = owner, displayName = name, phone = phone) }

            var attempt = 0
            while (true) {
                var problem: String? = null
                runCatching { db.collection("admins").document(user.uid).get().await().exists() }
                    .onSuccess { owner = it }
                    .onFailure { problem = describe(it) }
                // Leader access, given by an owner inside the app (it can have an end date).
                var leader = false
                runCatching { db.collection("leaders").document(user.uid).get().await() }
                    .onSuccess { snap ->
                        leader = snap.exists() && (snap.getTimestamp("until")?.toDate()?.after(Date()) ?: true)
                    }
                    .onFailure { if (problem == null) problem = describe(it) }
                admin = owner || leader

                var profileLoaded = newName != null
                if (newName == null) {
                    runCatching { db.collection("users").document(user.uid).get().await() }
                        .onSuccess { snap ->
                            name = snap.getString("displayName") ?: ""
                            phone = snap.getString("phone") ?: ""
                            profileLoaded = true
                            // Older profiles have no email yet. Add it so admins can recognise people.
                            if (name.isNotBlank() && snap.getString("email") != user.email) {
                                runCatching { writeProfile(user.uid, name, user.email.orEmpty(), phone) }
                            }
                        }
                        .onFailure { if (problem == null) problem = describe(it) }
                }
                if (problem == null) {
                    prefs.edit()
                        .putBoolean("auth_admin_${user.uid}", admin)
                        .putBoolean("auth_owner_${user.uid}", owner)
                        .putString("auth_name_${user.uid}", name)
                        .putString("auth_phone_${user.uid}", phone)
                        .apply()
                }
                _state.update {
                    it.copy(isAdmin = admin, isOwner = owner, displayName = name, phone = phone, profileLoaded = profileLoaded, loadError = problem, ready = true)
                }
                attempt++
                if (problem == null || attempt >= 4) break
                delay(attempt * 4000L)
            }
        }
    }

    /** Try loading the profile and leader status again (the "Retry" button). */
    fun retryProfile() {
        auth.currentUser?.let { loadProfile(it) }
    }

    init {
        auth.addAuthStateListener(listener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
    }

    private suspend fun writeProfile(uid: String, name: String, email: String, phone: String) {
        val data = hashMapOf<String, Any>(
            "displayName" to name,
            "email" to email,
            "phone" to phone,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        withTimeout(15_000) {
            db.collection("users").document(uid).set(data, SetOptions.merge()).await()
        }
    }

    fun submit(email: String, password: String, creating: Boolean, name: String = "", phone: String = "") {
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, resetSent = false) }
            try {
                if (creating) {
                    pendingName = name.trim().take(60)
                    pendingPhone = normalizePhone(phone).orEmpty()
                    val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                    // A failure to send must not undo the sign-up; the person can resend from the next screen.
                    val sent = runCatching { result.user?.sendEmailVerification()?.await() }.isSuccess
                    _state.update { it.copy(working = false, verificationSent = sent) }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password).await()
                    _state.update { it.copy(working = false) }
                }
            } catch (e: Exception) {
                pendingName = null
                pendingPhone = null
                _state.update { it.copy(working = false, error = classify(e)) }
            }
        }
    }

    /**
     * Saves the person's name and phone number. [phone] may be blank (no number). A number that
     * doesn't look valid is ignored and nothing is saved.
     */
    fun saveProfile(name: String, phone: String) {
        val user = auth.currentUser ?: return
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) return
        val cleanPhone = if (phone.isBlank()) "" else (normalizePhone(phone) ?: return)
        viewModelScope.launch {
            runCatching { writeProfile(user.uid, cleanName, user.email.orEmpty(), cleanPhone) }.onSuccess {
                _state.update { it.copy(displayName = cleanName, phone = cleanPhone, profileLoaded = true) }
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(error = AuthError.EnterEmail, resetSent = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, resetSent = false) }
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                _state.update { it.copy(working = false, resetSent = true) }
            } catch (e: Exception) {
                _state.update { it.copy(working = false, error = classify(e)) }
            }
        }
    }

    /** Sends (or re-sends) the verification email to the signed-in person. */
    fun sendVerification() {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _state.update { it.copy(verifyWorking = true, verifyError = null, verifyNotYet = false) }
            try {
                user.sendEmailVerification().await()
                _state.update { it.copy(verifyWorking = false, verificationSent = true) }
            } catch (e: Exception) {
                _state.update { it.copy(verifyWorking = false, verifyError = classify(e)) }
            }
        }
    }

    /**
     * Asks Firebase whether the link has been clicked yet.
     * [manual] is true when the person tapped the button, false for the automatic check on return to the app.
     */
    fun checkVerified(manual: Boolean) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            if (manual) _state.update { it.copy(verifyWorking = true, verifyNotYet = false, verifyError = null) }
            try {
                user.reload().await()
                val verified = auth.currentUser?.isEmailVerified == true
                _state.update {
                    it.copy(verifyWorking = false, emailVerified = verified, verifyNotYet = manual && !verified)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(verifyWorking = false, verifyError = if (manual) classify(e) else null)
                }
            }
        }
    }

    /**
     * Permanently deletes the signed-in person's account and profile.
     * Asks for the password again first, which Firebase requires for this action.
     */
    fun deleteAccount(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, deleteError = null) }
            try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                deleteMyData(user.uid)
                runCatching { PushTokens.unregister(user.uid) }
                runCatching { db.collection("fcmTokens").document(user.uid).delete().await() }
                runCatching { db.collection("users").document(user.uid).delete().await() }
                runCatching { db.collection("avatars").document(user.uid).delete().await() }
                user.delete().await()
                _state.update { it.copy(deleting = false) }
            } catch (e: Exception) {
                _state.update { it.copy(deleting = false, deleteError = classify(e)) }
            }
        }
    }

    /**
     * Removes what belongs to this person and is theirs to delete: their sermon notes, the people they blocked, the
     * messages they scheduled, and their ticks in the reading plans. (Their profile, picture and notification address
     * are removed separately.)
     */
    private suspend fun deleteMyData(uid: String) {
        runCatching {
            db.collection("users").document(uid).collection("sermonNotes").get().await().documents.forEach { it.reference.delete().await() }
        }
        runCatching {
            db.collection("users").document(uid).collection("blocked").get().await().documents.forEach { it.reference.delete().await() }
        }
        runCatching {
            db.collection("scheduledMessages").whereEqualTo("senderId", uid).get().await().documents.forEach { it.reference.delete().await() }
        }
        runCatching {
            db.collection("readingPlans").get().await().documents.forEach {
                runCatching { it.reference.collection("progress").document(uid).delete().await() }
            }
        }
    }

    fun clearDeleteError() = _state.update { it.copy(deleteError = null) }

    fun clearMessages() = _state.update { it.copy(error = null, resetSent = false) }

    fun signOut() {
        val uid = auth.currentUser?.uid
        viewModelScope.launch {
            // Stop notifications on this phone for this person before signing out.
            if (uid != null) runCatching { PushTokens.unregister(uid) }
            auth.signOut()
        }
    }

    private fun classify(e: Exception): AuthError = when (e) {
        is FirebaseNetworkException -> AuthError.NoConnection
        is FirebaseTooManyRequestsException -> AuthError.TooManyTries
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> AuthError.BadCredentials
            "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
            "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailInUse
            "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
            else -> AuthError.Generic
        }
        else -> AuthError.Generic
    }
}
