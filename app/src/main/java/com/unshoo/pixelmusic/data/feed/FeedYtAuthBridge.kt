package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class YtConnection(
    val isConnected: Boolean = false,
    val accountName: String = "",
    val cookies: Map<String, String> = emptyMap(),
    val channelHandle: String? = null,
    val photoUrl: String? = null,
) {
    companion object {
        val DISCONNECTED = YtConnection()
    }
}

@Singleton
class FeedYtAuthBridge @Inject constructor(
    private val datastoreRepository: DatastoreRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connection = MutableStateFlow(YtConnection.DISCONNECTED)
    val connection: StateFlow<YtConnection> = _connection.asStateFlow()

    init {
        scope.launch {
            combine(
                datastoreRepository.cookies,
                datastoreRepository.ytUsername,
                datastoreRepository.ytHandle,
                datastoreRepository.ytAvatarUrl
            ) { cookiesObj, name, handle, avatarUrl ->
                val rawCookie = cookiesObj.toRawCookie()
                val parsed = parseCookieHeader(rawCookie)
                val isConnected = parsed.isNotEmpty() && ("SAPISID" in parsed || "__Secure-3PAPISID" in parsed)
                if (isConnected) {
                    YtConnection(
                        isConnected = true,
                        accountName = name.ifBlank { "YouTube Account" },
                        cookies = parsed,
                        channelHandle = handle.takeIf { it.isNotBlank() },
                        photoUrl = avatarUrl.takeIf { it.isNotBlank() }
                    )
                } else {
                    YtConnection.DISCONNECTED
                }
            }.collect { conn ->
                _connection.value = conn
            }
        }
    }

    fun sapisid(account: YtConnection = connection.value): String? =
        account.cookies["__Secure-3PAPISID"]
            ?: account.cookies["SAPISID"]
            ?: account.cookies["APISID"]

    fun cookieHeaderValue(account: YtConnection = connection.value): String? {
        val cookies = account.cookies
        if (cookies.isEmpty()) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    suspend fun awaitLoadedConnection(): YtConnection {
        val raw = datastoreRepository.cookies.first().toRawCookie()
        val parsed = parseCookieHeader(raw)
        val isConnected = parsed.isNotEmpty() && ("SAPISID" in parsed || "__Secure-3PAPISID" in parsed)
        val name = datastoreRepository.ytUsername.first()
        val handle = datastoreRepository.ytHandle.first()
        val avatarUrl = datastoreRepository.ytAvatarUrl.first()
        val conn = if (isConnected) {
            YtConnection(
                isConnected = true,
                accountName = name.ifBlank { "YouTube Account" },
                cookies = parsed,
                channelHandle = handle.takeIf { it.isNotBlank() },
                photoUrl = avatarUrl.takeIf { it.isNotBlank() }
            )
        } else {
            YtConnection.DISCONNECTED
        }
        _connection.value = conn
        return conn
    }

    fun authorizationHeaderValue(origin: String = "https://music.youtube.com", account: YtConnection = connection.value): String? {
        val sapisid = sapisid(account) ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    private fun parseCookieHeader(raw: String): Map<String, String> =
        raw.split(';')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else {
                    val name = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (name.isBlank() || value.isBlank()) null else name to value
                }
            }
            .toMap()
}
