package dev.achmad.finbox.gmail

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Receives the OAuth redirect (`dev.achmad.finbox:/oauth2callback`),
 * completes token exchange and closes. Account rows are created by
 * [GmailAuthManager.handleCallback].
 */
class AuthCallbackActivity : Activity() {

    private val authManager: GmailAuthManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.data?.host == "oauth2callback") {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val account = authManager.handleCallback(intent)
                    Toast.makeText(this@AuthCallbackActivity, "Connected ${account.email}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthCallbackActivity, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        } else {
            finish()
        }
    }
}
