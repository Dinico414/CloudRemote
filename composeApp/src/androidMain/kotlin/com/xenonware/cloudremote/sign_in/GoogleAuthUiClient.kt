@file:Suppress("DEPRECATION")

package com.xenonware.cloudremote.sign_in

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInResult
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.UnsupportedApiCallException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.xenonware.cloudremote.R
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class GoogleAuthUiClient(
    private val context: Context,
    private val oneTapClient: SignInClient
) {
    private val auth = Firebase.auth

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    suspend fun signIn(): BeginSignInResult? {
        Log.d("GoogleAuthUiClient", "Attempting OneTap signIn()")
        return try {
            val request = buildSignInRequest()
            val result = oneTapClient.beginSignIn(request).await()
            Log.d("GoogleAuthUiClient", "OneTap signIn() success")
            result
        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "OneTap signIn() failed", e)
            if (e is UnsupportedApiCallException || e is ApiException) {
                return null
            }
            throw e
        }
    }

    fun getTraditionalSignInIntent(): Intent {
        Log.d("GoogleAuthUiClient", "Getting traditional sign-in intent")
        return googleSignInClient.signInIntent
    }

    suspend fun signInWithIntent(intent: Intent): SignInResult {
        Log.d("GoogleAuthUiClient", "Parsing OneTap signInWithIntent result")
        val credential = oneTapClient.getSignInCredentialFromIntent(intent)
        val googleIdToken = credential.googleIdToken
        return firebaseAuthWithGoogle(googleIdToken)
    }

    suspend fun signInWithTraditionalIntent(intent: Intent): SignInResult {
        Log.d("GoogleAuthUiClient", "Parsing Traditional signInWithIntent result")
        val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
        val account = task.await()
        val idToken = account.idToken
        return firebaseAuthWithGoogle(idToken)
    }

    private suspend fun firebaseAuthWithGoogle(googleIdToken: String?): SignInResult {
        Log.d("GoogleAuthUiClient", "Authenticating with Firebase using Google ID Token")
        val googleCredentials = GoogleAuthProvider.getCredential(googleIdToken, null)
        return try {
            val user = auth.signInWithCredential(googleCredentials).await().user
            Log.d("GoogleAuthUiClient", "Firebase Auth successful for user: ${user?.uid}")
            SignInResult(
                data = user?.run {
                    UserData(
                        userId = uid,
                        username = displayName,
                        profilePictureUrl = photoUrl?.toString(),
                        email = email.toString()
                    )
                },
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "Firebase Auth failed", e)
            if (e is CancellationException) throw e
            SignInResult(
                data = null,
                errorMessage = e.message
            )
        }
    }

    suspend fun signOut() {
        Log.d("GoogleAuthUiClient", "Attempting to signOut")
        try {
            oneTapClient.signOut().await()
            googleSignInClient.signOut().await()
            auth.signOut()
            Log.d("GoogleAuthUiClient", "signOut successful")
        } catch (e: Exception) {
            Log.e("GoogleAuthUiClient", "signOut failed", e)
            if (e is CancellationException) throw e
        }
    }

    fun getSignedInUser(): UserData? = try {
        auth.currentUser?.let { user ->
            Log.d("GoogleAuthUiClient", "Retrieved signed in user: ${user.uid}")
            UserData(
                userId = user.uid,
                username = user.displayName,
                profilePictureUrl = user.photoUrl?.toString(),
                email = user.email ?: ""
            )
        } ?: run {
            Log.d("GoogleAuthUiClient", "No signed in user found")
            null
        }
    } catch (e: Exception) {
        Log.e("GoogleAuthUiClient", "Error fetching signed in user", e)
        if (e is UnsupportedApiCallException ||
            e.message?.contains("auth_api_credentials_begin_sign_in") == true) {
            null
        } else {
            throw e
        }
    }

    private fun buildSignInRequest(): BeginSignInRequest {
        return BeginSignInRequest.Builder().setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder().setSupported(true)
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id)).build()
        ).setAutoSelectEnabled(false).build()

    }
}
