/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.SoundCloudAvatarUrlKey
import com.metrolist.music.constants.SoundCloudClientIdKey
import com.metrolist.music.constants.SoundCloudRefreshTokenKey
import com.metrolist.music.constants.SoundCloudTokenExpiryKey
import com.metrolist.music.constants.SoundCloudUserIdKey
import com.metrolist.music.constants.SoundCloudUsernameKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.soundcloud.SoundCloud
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val SOUNDCLOUD_LOGIN_URL = "https://soundcloud.com/signin"
private const val COOKIE_POLL_INTERVAL_MS = 1000L

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SoundCloudLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    // Thread-safe holders for values captured from background WebView threads
    val capturedClientId = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    val capturedAuthToken = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    var loginHandled by remember { mutableStateOf(false) }

    // Poll for oauth_token cookie since SoundCloud is an SPA and
    // won't trigger onPageFinished after client-side login navigation
    val pollJob = remember { mutableStateOf<Job?>(null) }

    fun handleLoginDetected(oauthToken: String, clientId: String) {
        if (loginHandled) return
        loginHandled = true
        isProcessing = true
        hasError = false
        statusMessage = context.getString(R.string.soundcloud_status_connecting)

        scope.launch(Dispatchers.IO) {
            try {
                SoundCloud.accessToken = oauthToken

                withContext(Dispatchers.Main) {
                    statusMessage = context.getString(R.string.soundcloud_status_loading_profile)
                }

                val user = SoundCloud.me().getOrNull()

                context.dataStore.edit { prefs ->
                    prefs[SoundCloudClientIdKey] = clientId
                    prefs[SoundCloudAccessTokenKey] = oauthToken
                    prefs.remove(SoundCloudRefreshTokenKey)
                    prefs[SoundCloudTokenExpiryKey] = 0L
                    user?.let {
                        prefs[SoundCloudUserIdKey] = it.id.toString()
                        prefs[SoundCloudUsernameKey] = it.username
                        it.avatarUrl?.let { avatar ->
                            prefs[SoundCloudAvatarUrlKey] = avatar
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    statusMessage = context.getString(R.string.soundcloud_login_success)
                }
                delay(300)
                withContext(Dispatchers.Main) {
                    navController.navigateUp()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SoundCloud login failed")
                withContext(Dispatchers.Main) {
                    loginHandled = false
                    isProcessing = false
                    hasError = true
                    statusMessage = classifySoundCloudLoginError(context, e)
                }
            }
        }
    }

    // Start polling once the WebView is up
    DisposableEffect(Unit) {
        val job = scope.launch {
            while (isActive) {
                delay(COOKIE_POLL_INTERVAL_MS)
                if (loginHandled) continue

                val oauthToken = extractOAuthToken() ?: capturedAuthToken.get()
                val clientId = capturedClientId.get()
                if (oauthToken != null && clientId != null) {
                    Timber.d("SoundCloud login: poll detected token + client_id")
                    withContext(Dispatchers.Main) {
                        handleLoginDetected(oauthToken, clientId)
                    }
                }
            }
        }
        pollJob.value = job

        onDispose { job.cancel() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.soundcloud_login)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            actions = {
                if (!isProcessing) {
                    TextButton(
                        onClick = {
                            val oauthToken = extractOAuthToken() ?: capturedAuthToken.get()
                            val clientId = capturedClientId.get()
                            if (oauthToken != null && clientId != null) {
                                handleLoginDetected(oauthToken, clientId)
                            } else {
                                Timber.w(
                                    "SoundCloud Done pressed but missing credentials " +
                                        "(oauth_token=%s, client_id=%s)",
                                    if (oauthToken != null) "present" else "missing",
                                    if (clientId != null) "present" else "missing",
                                )
                                hasError = true
                                statusMessage = context.getString(R.string.soundcloud_login_error)
                            }
                        },
                    ) {
                        Text("Done")
                    }
                }
            },
        )

        if (isLoading || isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)

                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                captureFromRequest(request?.url, capturedClientId, capturedAuthToken)
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): android.webkit.WebResourceResponse? {
                                captureFromRequest(request?.url, capturedClientId, capturedAuthToken)
                                return null
                            }
                        }

                        loadUrl(SOUNDCLOUD_LOGIN_URL)
                    }
                },
            )

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!hasError) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(text = statusMessage.ifEmpty { stringResource(R.string.soundcloud_logging_in) })
                        if (hasError) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { navController.navigateUp() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Capture client_id and auth_token from SoundCloud request URLs.
 */
private fun captureFromRequest(
    uri: Uri?,
    clientIdRef: java.util.concurrent.atomic.AtomicReference<String?>,
    authTokenRef: java.util.concurrent.atomic.AtomicReference<String?>,
) {
    uri ?: return
    val host = uri.host ?: return
    if (!host.contains("soundcloud.com")) return

    uri.getQueryParameter("client_id")?.takeIf { it.isNotBlank() }?.let { id ->
        if (clientIdRef.compareAndSet(null, id)) {
            Timber.d("SoundCloud login: captured client_id from request")
        }
    }

    uri.getQueryParameter("auth_token")?.takeIf { it.isNotBlank() }?.let { token ->
        if (authTokenRef.compareAndSet(null, token)) {
            Timber.d("SoundCloud login: captured auth_token from request")
        }
    }
}

/**
 * Read the oauth_token cookie from SoundCloud's domain.
 */
private fun extractOAuthToken(): String? {
    val domains = listOf(
        "https://soundcloud.com",
        "https://secure.soundcloud.com",
        "https://m.soundcloud.com",
        "https://api-auth.soundcloud.com",
        ".soundcloud.com",
    )
    for (domain in domains) {
        val cookies = CookieManager.getInstance().getCookie(domain) ?: continue
        val token = cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("oauth_token=") }
            ?.substringAfter("oauth_token=")
            ?.takeIf { it.isNotBlank() }
        if (token != null) {
            Timber.d("SoundCloud login: found oauth_token on domain %s", domain)
            return token
        }
    }
    return null
}

private fun classifySoundCloudLoginError(context: android.content.Context, error: Exception): String {
    val message = error.message.orEmpty().lowercase()
    return when {
        "state" in message -> context.getString(R.string.soundcloud_login_error_state)
        "credentials" in message || "client" in message -> context.getString(R.string.soundcloud_login_error_config)
        "timeout" in message || "unable to resolve host" in message || "network" in message -> {
            context.getString(R.string.soundcloud_login_error_network)
        }
        else -> context.getString(R.string.soundcloud_login_error)
    }
}
