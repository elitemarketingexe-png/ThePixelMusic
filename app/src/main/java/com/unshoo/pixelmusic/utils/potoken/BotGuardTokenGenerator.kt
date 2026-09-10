package com.unshoo.pixelmusic.utils.potoken

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generates cryptographically valid PoTokens by running YouTube's BotGuard
 * challenge inside a headless [WebView].
 *
 * ## Lifecycle
 *
 * 1. [initialize] — called once from `Application.onCreate()` with the app context.
 * 2. [preWarm] — optional, boots the WebView at startup to avoid first-call delay.
 * 3. [mintToken] — suspend function, called per video. Reuses the engine until
 *    it expires (~50 min) or the session changes.
 * 4. [onAppBackgrounded] — releases the WebView to free ~50 MB of memory.
 *
 * ## Optimizations
 *
 * - **Suspend API** — [mintToken] is a suspend function, never blocks the calling thread.
 * - **Pre-warm** — [preWarm] bootstraps the engine at app startup so the first
 *   real mint is instant.
 * - **Player token cache** — Caches per-video tokens (LRU, max 200) to avoid
 *   redundant mints when the same video is played multiple times.
 * - **Session token reuse** — The session token is minted once per engine and
 *   reused for all videos until the engine expires or the session changes.
 * - **Background cleanup** — [onAppBackgrounded] releases the WebView to free memory.
 *   The engine is recreated on the next [mintToken] call.
 */
object BotGuardTokenGenerator {

    private const val TAG = "BotGuardTokenGen"
    private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
    private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val WV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
    private const val JS_BRIDGE = "BotGuardBridge"

    /** Timeout for first call (cold start: WebView boot + BotGuard bootstrap). */
    private const val COLD_START_TIMEOUT_MS = 15_000L

    /** Timeout for subsequent calls (warm: just mint a token). */
    private const val WARM_TIMEOUT_MS = 5_000L

    /** Maximum number of cached player tokens. */
    private const val PLAYER_TOKEN_CACHE_SIZE = 200

    private val httpClient = OkHttpClient()

    // ── state ────────────────────────────────────────────────────────
    private var appContext: Context? = null
    private var permanentlyBroken = false

    private val mutex = Mutex()
    private var engine: BotGuardEngine? = null
    private var engineSessionId: String? = null
    private var cachedSessionToken: String? = null
    private var engineReady = false

    /**
     * LRU cache for per-video player tokens.
     * Key: videoId, Value: token string.
     * Avoids redundant mints when the same video is played multiple times.
     */
    private val playerTokenCache: LinkedHashMap<String, String> =
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
                return size > PLAYER_TOKEN_CACHE_SIZE
            }
        }

    private val preWarmStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    // ── public API ───────────────────────────────────────────────────

    /** Call once from `Application.onCreate()`. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        Timber.tag(TAG).d("Initialized")
    }

    /**
     * Pre-warm the BotGuard engine at app startup.
     * This bootstraps the WebView and generates the session token so that
     * the first real [mintToken] call is instant.
     *
     * Safe to call from a background coroutine. No-op if already warmed.
     */
    suspend fun preWarm(sessionId: String) {
        val ctx = appContext ?: return
        if (permanentlyBroken) return
        if (engineReady) return
        if (!preWarmStarted.compareAndSet(false, true)) return

        val startTime = android.os.SystemClock.elapsedRealtime()
        try {
            withTimeout(COLD_START_TIMEOUT_MS) {
                ensureEngineReady(ctx, sessionId)
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
            Timber.tag(TAG).i("Pre-warm complete in ${elapsed}ms")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Pre-warm failed (non-fatal) after ${android.os.SystemClock.elapsedRealtime() - startTime}ms")
            preWarmStarted.set(false)
        }
    }

    /**
     * Mint a PoToken pair for the given [videoId] and [sessionId].
     *
     * Returns `null` when:
     * - [initialize] was never called
     * - The system has no usable WebView
     * - BotGuard bootstrap timed out
     * - The WebView is permanently broken
     *
     * This is a **suspend function** — never blocks the calling thread.
     * Call from a coroutine context (e.g. `Dispatchers.IO`).
     */
    suspend fun mintToken(videoId: String, sessionId: String): PoTokenResult? {
        val ctx = appContext ?: run {
            Timber.tag(TAG).w("initialize() not called")
            return null
        }
        if (permanentlyBroken) return null

        // Check player token cache first
        mutex.withLock {
            val cachedPlayer = playerTokenCache[videoId]
            if (cachedPlayer != null && cachedSessionToken != null && engineReady) {
                Timber.tag(TAG).d("Cache hit for $videoId")
                return PoTokenResult(playerToken = cachedPlayer, sessionToken = cachedSessionToken!!)
            }
        }

        val startTime = android.os.SystemClock.elapsedRealtime()
        val isFirstCall = !engineReady
        val timeout = if (isFirstCall) COLD_START_TIMEOUT_MS else WARM_TIMEOUT_MS

        return try {
            withTimeout(timeout) {
                val result = mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = false)
                // Cache the player token
                mutex.withLock {
                    playerTokenCache[videoId] = result.playerToken
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
                Timber.tag(TAG).i("Minted token for $videoId in ${elapsed}ms (firstCall=$isFirstCall)")
                result
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Timber.tag(TAG).w("Timed out after ${timeout}ms — proceeding without PoToken")
            destroyEngine()
            null
        } catch (e: BrokenWebViewException) {
            Timber.tag(TAG).e(e, "Permanently broken WebView")
            permanentlyBroken = true
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "mintToken failed: ${e.message}")
            null
        }
    }

    /**
     * Release the WebView to free memory (~50 MB).
     * Call from `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` or similar.
     * The engine is recreated on the next [mintToken] call.
     */
    suspend fun onAppBackgrounded() {
        mutex.withLock {
            if (engine != null) {
                Timber.tag(TAG).d("Releasing engine (app backgrounded)")
                destroyEngineLocked()
            }
        }
    }

    /**
     * Invalidate the player token cache for a specific video.
     * Useful when the user's auth state changes.
     */
    suspend fun invalidatePlayerToken(videoId: String) {
        mutex.withLock {
            playerTokenCache.remove(videoId)
        }
    }

    /**
     * Invalidate all cached tokens.
     * Call when the user logs out or auth state changes.
     */
    suspend fun invalidateAll() {
        mutex.withLock {
            playerTokenCache.clear()
            destroyEngineLocked()
        }
    }

    // ── internal ─────────────────────────────────────────────────────

    private suspend fun ensureEngineReady(ctx: Context, sessionId: String): PoTokenResult {
        return mintTokenInternal(ctx, "__warmup__", sessionId, forceNewEngine = false)
    }

    private suspend fun mintTokenInternal(
        ctx: Context,
        videoId: String,
        sessionId: String,
        forceNewEngine: Boolean,
    ): PoTokenResult {
        val (eng, sessionTok, wasNew) = mutex.withLock {
            val needsNew = forceNewEngine
                || engine == null
                || engine!!.isExpired

            if (needsNew) {
                destroyEngineLocked()
                val newEngine = BotGuardEngine.create(ctx)
                val newSessionToken = newEngine.mint(sessionId)
                engine = newEngine
                engineSessionId = sessionId
                cachedSessionToken = newSessionToken
                engineReady = true
                Triple(newEngine, newSessionToken, true)
            } else {
                val currentEngine = engine!!
                val token = if (engineSessionId != sessionId || cachedSessionToken == null) {
                    val minted = currentEngine.mint(sessionId)
                    engineSessionId = sessionId
                    cachedSessionToken = minted
                    minted
                } else {
                    cachedSessionToken!!
                }
                Triple(currentEngine, token, false)
            }
        }

        val playerTok = try {
            eng.mint(videoId)
        } catch (e: Throwable) {
            if (wasNew) throw e
            Timber.tag(TAG).w(e, "mint failed, retrying with fresh engine")
            return mintTokenInternal(ctx, videoId, sessionId, forceNewEngine = true)
        }

        return PoTokenResult(playerToken = playerTok, sessionToken = sessionTok)
    }

    private suspend fun destroyEngine() {
        mutex.withLock {
            destroyEngineLocked()
        }
    }

    private suspend fun destroyEngineLocked() {
        val eng = engine
        engine = null
        engineSessionId = null
        cachedSessionToken = null
        engineReady = false
        if (eng != null) {
            withContext(Dispatchers.Main) {
                eng.close()
            }
        }
    }

    // ── WebView wrapper ──────────────────────────────────────────────

    private class BotGuardEngine private constructor(
        private val webView: WebView,
    ) {
        private val scope = MainScope()
        private val readyDeferred = CompletableDeferred<Unit>()
        private val pendingMints = Collections.synchronizedMap(
            ArrayMap<String, CompletableDeferred<String>>()
        )
        private val activeCalls = Collections.synchronizedSet(mutableSetOf<okhttp3.Call>())
        private lateinit var expiry: Instant

        val isExpired: Boolean get() = Instant.now().isAfter(expiry)

        suspend fun awaitReady() {
            readyDeferred.await()
        }

        fun startBootstrap() {
            scope.launch(exceptionHandler) {
                val html = withContext(Dispatchers.IO) {
                    webView.context.assets.open("po_token.html")
                        .bufferedReader().use { it.readText() }
                }
                val patched = html.replaceFirst("</script>", "\n$JS_BRIDGE.onPageLoaded()</script>")
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com", patched, "text/html", "utf-8", null
                )
            }
        }

        @JavascriptInterface
        fun onPageLoaded() {
            Timber.tag(TAG).d("Page loaded — requesting challenge from Create")
            postToBotGuard(CREATE_URL, "[ \"$REQUEST_KEY\" ]") { body ->
                val challengeJson = parseCreateChallenge(body)
                webView.evaluateJavascript(
                    """
                    try {
                        var data = $challengeJson;
                        runBotGuard(data).then(function(r) {
                            this.webPoSignalOutput = r.webPoSignalOutput;
                            $JS_BRIDGE.onBotGuardReady(r.botguardResponse);
                        }, function(e) {
                            $JS_BRIDGE.onFatalError(e + "\n" + e.stack);
                        });
                    } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                    """.trimIndent(),
                    null
                )
            }
        }

        @JavascriptInterface
        fun onBotGuardReady(botguardResponse: String) {
            Timber.tag(TAG).d("BotGuard executed — requesting integrity token from GenerateIT")
            postToBotGuard(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { body ->
                try {
                    val (tokenU8, lifetimeSec) = parseIntegrityToken(body)
                    expiry = Instant.now().plusSeconds(lifetimeSec).minus(10, ChronoUnit.MINUTES)

                    webView.evaluateJavascript(
                        """
                        try {
                            this.integrityToken = $tokenU8;
                            createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                                $JS_BRIDGE.onMinterReady();
                            }).catch(function(e) {
                                $JS_BRIDGE.onFatalError(e + "\n" + (e.stack || ''));
                            });
                        } catch(e) { $JS_BRIDGE.onFatalError(e + "\n" + e.stack); }
                        """.trimIndent(),
                        null
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "parseIntegrityToken failed")
                    signalError(PoTokenException("GenerateIT parse failed: ${e.message}"))
                }
            }
        }

        private fun signalReady() {
            readyDeferred.complete(Unit)
        }

        fun signalError(error: Throwable) {
            readyDeferred.completeExceptionally(error)
        }

        @JavascriptInterface
        fun onMinterReady() {
            Timber.tag(TAG).d("Minter ready")
            signalReady()
        }

        @JavascriptInterface
        fun onFatalError(error: String) {
            Timber.tag(TAG).e("Fatal JS error: $error")
            signalError(classifyJsError(error))
        }

        suspend fun mint(identifier: String): String {
            val deferred = CompletableDeferred<String>()
            pendingMints[identifier] = deferred
            try {
                withContext(Dispatchers.Main) {
                    val u8Arg = stringToJsUint8Array(identifier)
                    webView.evaluateJavascript(
                        """
                        try {
                            obtainPoToken($u8Arg).then(function(u8) {
                                $JS_BRIDGE.onMintOk("${identifier}", u8.join(","));
                            }).catch(function(e) {
                                $JS_BRIDGE.onMintErr("${identifier}", e + "\n" + (e.stack || ''));
                            });
                        } catch(e) { $JS_BRIDGE.onMintErr("${identifier}", e + "\n" + e.stack); }
                        """.trimIndent(),
                        null
                    )
                }
                return deferred.await()
            } finally {
                pendingMints.remove(identifier)
            }
        }

        @JavascriptInterface
        fun onMintOk(identifier: String, csvBytes: String) {
            val base64 = commaSeparatedBytesToBase64(csvBytes)
            Timber.tag(TAG).d("Minted token for $identifier (${base64.length} chars)")
            val deferred = pendingMints.remove(identifier)
            deferred?.complete(base64)
        }

        @JavascriptInterface
        fun onMintErr(identifier: String, error: String) {
            Timber.tag(TAG).e("Mint failed for $identifier: $error")
            val deferred = pendingMints.remove(identifier)
            deferred?.completeExceptionally(classifyJsError(error))
        }

        private val exceptionHandler = CoroutineExceptionHandler { _, t ->
            Timber.tag(TAG).w(t, "Coroutines exception in BotGuardEngine")
            signalError(t)
        }

        private fun postToBotGuard(
            url: String,
            jsonBody: String,
            onSuccess: (String) -> Unit,
        ) {
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody())
                .headers(
                    mapOf(
                        "User-Agent" to WV_USER_AGENT,
                        "Accept" to "application/json",
                        "Content-Type" to "application/json+protobuf",
                        "x-goog-api-key" to API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1",
                    ).toHeaders()
                )
                .build()

            val call = httpClient.newCall(request)
            activeCalls.add(call)

            scope.launch(exceptionHandler) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        call.execute()
                    }
                    response.use { resp ->
                        if (resp.code != 200) {
                            signalError(PoTokenException("BotGuard HTTP ${resp.code} from $url"))
                        } else {
                            val body = resp.body.string()
                            onSuccess(body)
                        }
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.tag(TAG).w(e, "postToBotGuard failed for $url")
                    signalError(e)
                } finally {
                    activeCalls.remove(call)
                }
            }
        }

        @MainThread
        fun close() {
            readyDeferred.completeExceptionally(PoTokenException("Engine closed"))
            scope.cancel()

            val calls = synchronized(activeCalls) {
                val copy = ArrayList(activeCalls)
                activeCalls.clear()
                copy
            }
            calls.forEach { runCatching { it.cancel() } }

            val pending = synchronized(pendingMints) {
                val copy = ArrayList(pendingMints.values)
                pendingMints.clear()
                copy
            }
            pending.forEach { it.completeExceptionally(PoTokenException("Engine closed")) }

            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }

        companion object {
            suspend fun create(context: Context): BotGuardEngine {
                val engine = withContext(Dispatchers.Main) {
                    var engineRef: BotGuardEngine? = null
                    val wv = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.userAgentString = WV_USER_AGENT
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                if (m.message().contains("Uncaught")) {
                                    val err = "\"${m.message()}\", ${m.sourceId()} (${m.lineNumber()})"
                                    engineRef?.signalError(BrokenWebViewException(err))
                                }
                                return super.onConsoleMessage(m)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: android.webkit.RenderProcessGoneDetail
                            ): Boolean {
                                Timber.tag(TAG).w("WebView renderer gone (crashed=${detail.didCrash()})")
                                engineRef?.signalError(PoTokenException("WebView renderer process gone"))
                                return true
                            }
                        }
                    }
                    val created = BotGuardEngine(wv)
                    engineRef = created
                    wv.addJavascriptInterface(created, JS_BRIDGE)
                    created.startBootstrap()
                    created
                }
                try {
                    engine.awaitReady()
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        engine.close()
                    }
                    throw e
                }
                return engine
            }
        }
    }
}
