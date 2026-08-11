package dev.achmad.finbox.core.gmail

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import dev.achmad.finbox.core.util.injectLazy
import dev.achmad.finbox.core.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the OAuth redirect (`dev.achmad.finbox:/oauth2callback`),
 * completes token exchange and closes. Account rows are created by
 * [GmailAuthManager.handleCallback].
 */
class AuthCallbackActivity : Activity() {

    private val authManager: GmailAuthManager by injectLazy()
    private val toastHelper: ToastHelper by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = intent?.data?.host

        if (host != "oauth2callback") {
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val account = authManager.handleCallback(intent)
                toastHelper.show("Connected ${account.email}")
            } catch (e: Exception) {
                toastHelper.show(
                    message = "Auth failed: ${e.message}",
                    duration = Toast.LENGTH_LONG
                )
            }
            finish()
        }
    }
}
