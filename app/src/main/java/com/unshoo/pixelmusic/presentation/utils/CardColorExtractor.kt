package com.unshoo.pixelmusic.presentation.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * ULTRA-OPTIMIZED Two-tier card color extractor.
 *
 * Features & Perf Enhancements:
 * - In-flight deduplication: Simultaneous requests for the same image share a single Deferred job.
 * - L1: LruCache<String, Int> 500 entries (instant UI thread hits).
 * - L2: Bounded JSON disk cache in cacheDir (500 entries) with debounced async saves.
 * - Downscaled Coil sample (24x24) + Palette maximumColorCount(4) for sub-millisecond extraction.
 * - 120ms composable debounce cancels work during fast fling/scroll.
 */
object CardColorExtractor {

    private const val DISK_CACHE_FILE = "card_color_lru.json"
    private const val MAX_DISK_ENTRIES = 500
    private const val MAX_MEMORY_ENTRIES = 500
    private const val DISK_SAVE_DEBOUNCE_MS = 2000L

    val colorCache = LruCache<String, Int>(MAX_MEMORY_ENTRIES)

    private val diskMap = LinkedHashMap<String, Int>(MAX_DISK_ENTRIES, 0.75f, true)
    private val diskMutex = Mutex()
    private val extractionSemaphore = Semaphore(3) // Up to 3 parallel light palette extractions
    private val inFlightMap = ConcurrentHashMap<String, Deferred<Int?>>()

    @Volatile
    private var isInitialized = false

    private var appContext: Context? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingSaveJob: kotlinx.coroutines.Job? = null

    /** Must be called once from Application.onCreate */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            appContext = context.applicationContext
        }
        appScope.launch {
            loadDiskCacheInternal()
        }
    }

    private suspend fun loadDiskCacheInternal() {
        try {
            val ctx = appContext ?: return
            val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
            if (!file.exists()) return
            val jsonText = withContext(Dispatchers.IO) { file.readText() }
            if (jsonText.isBlank()) return
            val obj = JSONObject(jsonText)
            val keys = obj.keys()
            val loaded = mutableListOf<Pair<String, Int>>()
            while (keys.hasNext()) {
                val k = keys.next()
                try {
                    val v = obj.getInt(k)
                    loaded.add(k to v)
                } catch (_: Exception) {
                }
            }
            diskMutex.withLock {
                diskMap.clear()
                loaded.takeLast(MAX_DISK_ENTRIES).forEach { (k, v) ->
                    diskMap[k] = v
                    colorCache.put(k, v)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "CardColorExtractor: failed to load disk cache")
        }
    }

    private fun scheduleDiskSave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = appScope.launch {
            delay(DISK_SAVE_DEBOUNCE_MS)
            saveDiskCacheInternal()
        }
    }

    private suspend fun saveDiskCacheInternal() {
        try {
            val ctx = appContext ?: return
            val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
            val snapshot: Map<String, Int>
            diskMutex.withLock {
                while (diskMap.size > MAX_DISK_ENTRIES) {
                    val eldest = diskMap.entries.iterator().next()
                    diskMap.remove(eldest.key)
                }
                snapshot = LinkedHashMap(diskMap)
            }
            if (snapshot.isEmpty()) return
            val json = JSONObject()
            snapshot.forEach { (k, v) ->
                val safeKey = if (k.length > 300) k.take(300) else k
                json.put(safeKey, v)
            }
            withContext(Dispatchers.IO) {
                file.writeText(json.toString())
            }
        } catch (e: Exception) {
            Timber.w(e, "CardColorExtractor: failed to save disk cache")
        }
    }

    /**
     * Returns ARGB or null. Hits L1 -> L2 -> Deduplicated Palette Extraction.
     */
    suspend fun extractColorArgb(context: Context, imageUrl: String?): Int? {
        if (imageUrl.isNullOrBlank()) return null

        // 1. L1 Memory Cache (Instant hit)
        colorCache.get(imageUrl)?.let { return it }

        // 2. L2 In-memory Disk Map
        diskMutex.withLock {
            diskMap[imageUrl]?.let { cached ->
                colorCache.put(imageUrl, cached)
                return cached
            }
        }

        // 3. Deduplication: Join existing in-flight deferred job if already running
        val existing = inFlightMap[imageUrl]
        if (existing != null) {
            return existing.await()
        }

        val deferred = appScope.async(Dispatchers.IO) {
            extractionSemaphore.withPermit {
                try {
                    val ctx = context.applicationContext
                    val loader = ctx.imageLoader
                    val request = ImageRequest.Builder(ctx)
                        .data(imageUrl)
                        .allowHardware(false)
                        .size(32) // 32x32 sample size for lightning-fast color extraction without frame drops
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null && !bmp.isRecycled) {
                            val rgb = Palette.from(bmp)
                                .maximumColorCount(4) // fast swatch extraction
                                .resizeBitmapArea(32 * 32)
                                .generate()
                                .let { palette ->
                                    val swatches = listOfNotNull(
                                        palette.vibrantSwatch,
                                        palette.darkVibrantSwatch,
                                        palette.lightVibrantSwatch,
                                        palette.dominantSwatch,
                                        palette.mutedSwatch,
                                        palette.lightMutedSwatch,
                                        palette.darkMutedSwatch
                                    )
                                    // Pick swatch with highest saturation & population score
                                    swatches.maxByOrNull { swatch ->
                                        val hsl = swatch.hsl
                                        val saturation = hsl[1]
                                        val lightness = hsl[2]
                                        // Bonus for vibrant, non-extreme (not too black/white) colors
                                        val vibranceBonus = if (lightness in 0.15f..0.85f) 1.5f else 0.8f
                                        (saturation * 2.0f + swatch.population / 1000f) * vibranceBonus
                                    }?.rgb ?: palette.dominantSwatch?.rgb
                                }
                            if (rgb != null) {
                                colorCache.put(imageUrl, rgb)
                                diskMutex.withLock {
                                    diskMap[imageUrl] = rgb
                                }
                                scheduleDiskSave()
                                return@withPermit rgb
                            }
                        }
                    }
                    null
                } catch (e: Exception) {
                    Timber.d(e, "extractColor failed for $imageUrl")
                    null
                } finally {
                    inFlightMap.remove(imageUrl)
                }
            }
        }

        inFlightMap[imageUrl] = deferred
        return deferred.await()
    }

    /** Invalidate a single entry */
    fun invalidate(imageUrl: String) {
        colorCache.remove(imageUrl)
        inFlightMap.remove(imageUrl)
        appScope.launch {
            diskMutex.withLock { diskMap.remove(imageUrl) }
            scheduleDiskSave()
        }
    }

    /** Clear all cache entries */
    fun clearAll() {
        colorCache.evictAll()
        inFlightMap.clear()
        appScope.launch {
            diskMutex.withLock { diskMap.clear() }
            try {
                appContext?.let { ctx ->
                    val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Composable helper that returns dominant card color.
 */
@Composable
fun rememberDominantCardColor(
    imageUrl: String?,
    baseColor: Color,
    isDarkTheme: Boolean,
    darkBlendFraction: Float = 0.32f,
    lightBlendFraction: Float = 0.48f
): Color {
    val context = LocalContext.current
    val blendFraction = if (isDarkTheme) darkBlendFraction else lightBlendFraction

    val initialArgb = remember(imageUrl) {
        if (!imageUrl.isNullOrBlank()) CardColorExtractor.colorCache.get(imageUrl) else null
    }

    val whiteSolid = Color.White
    val whiteBlendFraction = if (isDarkTheme) 0.14f else 0.22f

    var targetColor by remember(imageUrl, baseColor, isDarkTheme) {
        mutableStateOf(
            if (initialArgb != null) {
                val extracted = lerp(baseColor, Color(initialArgb), blendFraction)
                lerp(extracted, whiteSolid, whiteBlendFraction)
            } else baseColor
        )
    }

    if (initialArgb == null && !imageUrl.isNullOrBlank()) {
        LaunchedEffect(imageUrl, baseColor, isDarkTheme) {
            // Calm 160ms debounce to prevent thrashing during fast tab switching/scrolling
            delay(160)
            val argb = CardColorExtractor.extractColorArgb(context, imageUrl)
            if (argb != null) {
                val extracted = lerp(baseColor, Color(argb), blendFraction)
                targetColor = lerp(extracted, whiteSolid, whiteBlendFraction)
            }
        }
    }

    // Fast static return if cached — bypasses animation work to eliminate scroll jank
    if (initialArgb != null) {
        return targetColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "card_dominant_color"
    )
    return animatedColor
}
