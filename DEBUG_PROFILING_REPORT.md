# Deep Performance & Profiling Report: Debug Build (`com.unshoo.pixelmusic.debug`)

- **Device Under Test**: `ZD222NS47Y` (Android OS / ARM64)
- **Target Package**: `com.unshoo.pixelmusic.debug`
- **Profiling Duration**: 5 Minutes (300 seconds continuous sampling)
- **Log Location**: `d:\Downloads\PixelMusic\DeletedDev\profiling_logs\debug\`

---

## 1. Executive Summary & Jank Scorecard

| Metric | Measured Value | Threshold / Target | Status |
| :--- | :--- | :--- | :--- |
| **Total Frames Rendered** | **19,932** frames | - | Baseline |
| **Janky Frames (Deadline Missed)** | **4,782 (23.99%)** | < 5% | ❌ **Severe Jank** |
| **Legacy Janky Frames** | **6,128 (30.74%)** | < 5% | ❌ **High Stutter** |
| **50th Percentile (Median Frame Time)** | **14 ms** | < 16.6ms (60Hz) / < 8.3ms (120Hz) | ⚠️ Borderline |
| **90th Percentile Frame Time** | **61 ms** | < 16.6 ms | ❌ **Major Stutter** |
| **95th Percentile Frame Time** | **105 ms** | < 25 ms | ❌ **Severe Freeze** |
| **99th Percentile Frame Time** | **250 ms** | < 50 ms | ❌ **Quarter-Second Hitch** |
| **Missed Vsync Count** | **3,331** | < 100 | ❌ Render Pipeline Starvation |
| **Slow UI Thread Count** | **4,097** | < 100 | ❌ Main Thread Blocking |
| **Slow Draw Commands** | **4,617** | < 100 | ❌ Canvas/Compose Overdraw |
| **High Input Latency Events** | **34,187** | < 500 | ❌ Touch Event Backlog |

---

## 2. Memory & Allocation Breakdown

```
Applications Memory Usage (in Kilobytes):
** MEMINFO in pid 15279 [com.unshoo.pixelmusic.debug] **
                   Pss(KB)       Rss(KB)      Heap Size    Heap Alloc     Heap Free
  Native Heap     125,890       127,716        268,220        96,764       167,631
  Dalvik Heap      85,459        86,996        142,450        44,146        98,304
  Dalvik Other     45,118        70,612
  Code / APK      136,140       334,132
  Graphics dev     13,896        13,900
  Stack            11,520        11,524
  TOTAL PSS       468,808 KB    627,496 KB (RSS)
```

### Key Memory Findings:
1. **Dalvik Heap GC Churn**:
   - Total Dalvik heap allocated is ~44MB out of 142MB heap footprint.
   - Large amount of short-lived objects created during scrolling lists (unstable lambdas in Compose causing frequent recompositions and GC allocations).
2. **GPU & Skia Texture Caches**:
   - Total GPU memory: **132.32 MB** (`36.44 MB` texture cache, `50.59 MB` RenderTargets, `38.31 MB` scratch textures).
   - GraphicBufferAllocator allocated: **54.24 MB** across BLAST Consumer buffers.
3. **WebViews & Media Load**:
   - Found **1 Active WebView** retained in memory alongside 12 Views / 1 ViewRootImpl.

---

## 3. Threading, Background Processes & CPU Hotspots

Top active threads recorded under `top -H`:
1. **`DefaultDispatcher-worker-*`**: 12–16 worker threads running concurrently.
   - High concurrency in Kotlin Coroutines dispatcher during initial image loading, metadata scanning, and lyric fetch tasks.
2. **`p57 Dispatcher` & `p57 TaskRunner` (Cronet/OkHttp/Network)**:
   - Multiple background socket pools communicating with YouTube Music endpoints (`music.youtube.com`, `yt3.ggpht.com`, `googleapis.com`).
3. **`ExoPlayer:MediaCodec` & `AudioTrack`**:
   - Audio decoding and playback buffer processing running smoothly with priority `-16`, consuming negligible CPU (< 1.5%).
4. **`Chrome_InProcGpuThread` & `VizWebView`**:
   - Embedded WebView rendering pipeline running in-process, causing occasional background CPU bursts.

---

## 4. Database & Room Diagnostics

- **Database**: `/data/user/0/com.unshoo.pixelmusic.debug/databases/pixelmusic_database`
- **WAL Journaling**: Enabled (`journalMode=WAL`, `syncMode=NORMAL`)
- **Connection Pool**: 4 max connections.
- **Total Statements Executed**: 51,612 queries in 5 minutes (avg 1ms per query).
- **Triggers**: Heavy room invalidation triggers on `telegram_channels`, `gdrive_folders`, and `favorite_songs` tables firing during background sync cycles.
- **Long operations exceeding 2000ms**: 0 detected.

---

## 5. Thermal & Battery Impact

- **Battery Temp**: 40.0°C
- **CPU Core Temperatures**:
  - `CPU0-CPU3` (Efficiency cores): 63.5°C – 65.6°C
  - `CPU4-CPU6` (Performance cores): 67.3°C – 68.0°C
  - `CPU7` (Prime core): 62.8°C
  - `SoC Temp`: 90.0°C (`mStatus=3` - High Thermal State)
- **Root Cause of Heating in Debug**:
  - Unoptimized Kotlin bytecode + lack of R8 method inlining + Compose runtime layout inspection forces CPU performance cores to sustain higher clocks during list scrolling and artwork decoding.

---

## 6. Recommendations for Debug Build

1. **Enable Compose Compiler Metrics** to detect unskippable composables and unstable parameters.
2. **Apply `remember` & Immutable data structures** on all list items in `LazyColumn` to prevent 30%+ frame drops.
3. **Consolidate Coroutine Dispatchers** to avoid spawning 16+ simultaneous worker threads during simultaneous search & thumbnail loading.
