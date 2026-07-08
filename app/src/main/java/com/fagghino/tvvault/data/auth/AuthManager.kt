package com.fagghino.tvvault.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun signIn(): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(context)
            
            // L'ID del client web viene generato automaticamente dal plugin google-services
            // Si trova nelle string resources: R.string.default_web_client_id
            val serverClientId = context.getString(
                context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            )

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                
                // Scambia l'ID token di Google con le credenziali di Firebase Auth
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                
                val user = authResult.user
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Autenticazione Firebase fallita: utente nullo"))
                }
            } else {
                Result.failure(Exception("Tipo di credenziale non supportato"))
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Errore durante il sign-in", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }
}
