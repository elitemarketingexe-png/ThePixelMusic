# Deep Performance & Profiling Report: Release / Main Build (`com.unshoo.pixelmusic`)

- **Device Under Test**: `ZD222NS47Y` (Android OS / ARM64)
- **Target Package**: `com.unshoo.pixelmusic`
- **Profiling Duration**: 5 Minutes (300 seconds continuous sampling)
- **Log Location**: `d:\Downloads\PixelMusic\DeletedDev\profiling_logs\release\`

---

## 1. Executive Summary & Jank Scorecard

| Metric | Measured Value | Threshold / Target | Status | Comparison vs Debug |
| :--- | :--- | :--- | :--- | :--- |
| **Total Frames Rendered** | **27,275** frames | - | Baseline | **+36.8% More Frames Processed** |
| **Janky Frames (Deadline Missed)** | **4,452 (16.32%)** | < 5% | ⚠️ **Moderate Jank** | **32% Lower Jank Rate than Debug** |
| **50th Percentile (Median Frame Time)** | **15 ms** | < 16.6 ms | ✅ **Pass (60Hz Smooth)** | 14ms vs 15ms |
| **90th Percentile Frame Time** | **31 ms** | < 16.6 ms | ⚠️ **Noticeable Stutter** | **49% Faster than Debug (31ms vs 61ms)** |
| **95th Percentile Frame Time** | **38 ms** | < 25 ms | ⚠️ **Mild Stutter** | **63.8% Faster than Debug (38ms vs 105ms)** |
| **99th Percentile Frame Time** | **89 ms** | < 50 ms | ❌ **Heavy Hitches** | **64.4% Faster than Debug (89ms vs 250ms)** |
| **Missed Vsync Count** | **1,978** | < 100 | ❌ Vsync Slips | **40.6% Fewer missed vsyncs** |
| **Slow UI Thread Count** | **3,722** | < 100 | ❌ Main Thread Bottleneck | 3,722 vs 4,097 |
| **Slow Draw Commands** | **4,393** | < 100 | ❌ Render Pipeline Load | 4,393 vs 4,617 |
| **Slow Bitmap Uploads** | **86** | < 10 | ⚠️ Texture Upload Overhead | Higher due to image/cache loads |

---

## 2. Memory & Allocation Breakdown

```
Applications Memory Usage (in Kilobytes):
** MEMINFO in pid 21228 [com.unshoo.pixelmusic] **
                   Pss(KB)       Rss(KB)      Heap Size    Heap Alloc     Heap Free
  Native Heap     210,647       212,460        406,584       220,663       180,555
  Dalvik Heap     115,315       116,892        158,889        60,585        98,304
  Dalvik Other     20,420        32,988
  Code / APK       46,024       230,328
  Graphics dev     58,540        58,544
  Stack             8,676         8,680
  TOTAL PSS       517,727 KB    673,156 KB (RSS)
```

### Key Differences & Memory Analysis:
1. **Code Size & Optimization**:
   - **Code PSS dropped from 136 MB (Debug) down to 46 MB (Release)** thanks to R8 shrinking and dead-code stripping.
2. **Native Heap & Image Caches**:
   - Native Heap is higher (210 MB) due to aggressive Coil image caching in memory (bitmaps kept in hardware buffers for fast scrolling).
3. **GPU & Texture Memory**:
   - Total GPU Memory: **247.97 MB** (`108.73 MB` image textures, `76.80 MB` RenderTargets, `35.25 MB` scratch textures).
   - GraphicBufferAllocator allocated: **54.24 MB** across BLAST Consumer buffers.
   - **Purgeable GPU memory: 244.97 MB** (System can reclaim this smoothly under low memory pressure without crashing).

---

## 3. Threading, Background Processes & Heating Root Causes

1. **Main Thread Blocking (3,722 Slow UI Thread instances)**:
   - Synchronous JSON parsing or state mapper transformations when feeding updated UI states from `PlayerViewModel` / `SongRepository` to Compose.
   - `rememberDerivedStateOf` or missing `key()` in `LazyColumn` items causing items to re-measure and re-layout repeatedly during scrolling.
2. **Coil Image Loading & Bitmap Uploads (86 Slow Bitmap Uploads)**:
   - High resolution album arts uploaded to the GPU directly on the render thread during rapid scrolling instead of being resized/downsampled to list item dimensions beforehand.
3. **Continuous Background Coroutines**:
   - Active Dispatcher threads (`DefaultDispatcher-worker-1` through `16`) continuously polling and invalidating Room table triggers.

---

## 4. Database & Room Diagnostics

- **Database**: `/data/user/0/com.unshoo.pixelmusic/databases/pixelmusic_database`
- **Total Statements Executed**: 51,612 operations recorded.
- **Average Time per Query**: **1 ms** (Fast query execution; the database itself is well-indexed and running WAL mode efficiently).
- **Bottleneck**: Database query times are not causing the UI lag; rather, the frequent room modification triggers invalidate Kotlin `Flow` observers which triggers full Compose UI recompositions.

---

## 5. Side-by-Side Comparison: Debug vs Main Release

```
===================================================================================
PROFILING METRIC                   DEBUG BUILD              RELEASE (MAIN) BUILD
===================================================================================
Jank Rate                          23.99% (4,782 frames)    16.32% (4,452 frames)  [-32%]
90th Percentile Frame Latency      61 ms                    31 ms                  [-49%]
95th Percentile Frame Latency      105 ms                   38 ms                  [-64%]
99th Percentile Frame Latency      250 ms                   89 ms                  [-64%]
Missed Vsyncs                      3,331                    1,978                  [-41%]
Code Memory Footprint (PSS)        136 MB                   46 MB                  [-66%]
Total PSS Memory                   468 MB                   517 MB (Higher cache)
===================================================================================
```

---

## 6. Actionable Optimization Plan to Eliminate the Remaining 16% Jank & Device Heating

1. **Downsample Artwork in Coil Image Loaders**:
   - Set `.size(Size.ORIGINAL)` only on full player screen; for song lists and carousels, clamp image request size to `128.dp` x `128.dp` to prevent GPU texture upload spikes and eliminate the 86 slow bitmap upload hitches.
2. **Add Stable Keys & Content Types to LazyLists**:
   - Ensure every `items()` call in `LazyColumn` has `key = { it.id }` and `contentType = { "song_item" }`.
3. **Debounce Flow Invalidation in ViewModels**:
   - Throttle/debounce Room database observation updates by 150-200ms during active playback/sync so that background inserts don't cause 60fps re-renders of list screens.
4. **Use `derivedStateOf` for Player Progress Updates**:
   - Avoid emitting new whole-screen UI state objects on every 100ms playback progress tick; isolate progress bar rendering to a dedicated sub-composable.
