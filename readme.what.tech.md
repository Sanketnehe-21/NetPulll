# NetPull • Technology Stack, Libraries & Architecture Pipeline

This document explains **every technology, library, API, and architectural pipeline** used to build **NetPull**.

---

## 1. Technologies & Libraries Used

| Library / Technology | Exact Version | Purpose & What It Does |
| :--- | :--- | :--- |
| **Kotlin** | `1.9.24` | Modern, null-safe, coroutine-native Android development language. |
| **Java JDK** | `17 (LTS)` | Runtime environment and bytecode target. |
| **Android SDK / AGP** | `CompileSdk 34` / `AGP 8.5.2` | Target SDK 34 (Android 14/15) with modern Foreground Service security policies. |
| **Splash Screen & Animations** | `SplashActivity.kt` / `PullWaveView.kt` | Smooth animated scale/fade splash entry and custom 60 FPS concentric pulsing network waves (`Canvas.drawCircle` + `ValueAnimator`). |
| **Adaptive Theming** | System Configuration | Automatic Dark & Light theme matching device setting (Warm Obsidian Charcoal for Dark, Luxury Warm Cream for Light). |
| **OkHttp** | `4.12.0` (`com.squareup.okhttp3:okhttp`) | High-performance HTTP client. Used for low-level **physical network socket binding**, custom DNS resolution per interface, persistent connection pooling, and multi-stream data pulling. |
| **Kotlinx Coroutines** | `1.8.1` (`kotlinx-coroutines-android`) | Asynchronous, non-blocking concurrency framework. Powers the concurrent parallel stream workers, the 1-second speedometer loop, and UI updates without freezing the main thread. |
| **Kotlin StateFlow** | Part of Coroutines | Reactive state management (`isRunning`, `isPaused`, `totalMbps`, `totalMBps`, etc.) that allows seamless bidirectional communication between the background service and UI. |
| **Android ConnectivityManager** | Android Framework API | Requests and acquires distinct network handles for **Wi-Fi (`TRANSPORT_WIFI`)** and **Cellular (`TRANSPORT_CELLULAR`)** simultaneously. |
| **Android Foreground Service** | Android Framework API | Enables continuous background execution with `foregroundServiceType="dataSync"`, preventing Android from killing the puller when the screen is locked or apps are switched. |
| **Android Notification Manager** | Android Framework API | Renders persistent, real-time status bar notifications with live receiving speed, start timestamp, target rate, and interactive buttons (Pause/Resume, Stop). |
| **Android Jetpack Core KTX** | `1.13.1` | Idiomatic Kotlin extensions for Android system services, permissions, and notifications. |
| **Android Jetpack AppCompat** | `1.7.0` | Backward-compatible base activity, themes, and alert dialogs. |

---

## 2. The Architectural Pipeline: How NetPull Was Made

```
                    ┌───────────────────────────┐
                    │  User Interface (UI)       │
                    │  (MainActivity.kt)         │
                    └─────────────┬─────────────┘
                                  │ Taps "START PULLING"
                                  ▼
                    ┌───────────────────────────┐
                    │  Foreground Service       │
                    │  (PullService.kt)          │
                    └─────────────┬─────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │                                                 │
         ▼                                                 ▼
┌───────────────────────────────┐         ┌───────────────────────────────┐
│ Network Acquisition Pipeline  │         │ Live Telemetry & Notification │
│ (ConnectivityManager API)     │         │ (1-second Delta Loop)         │
└────────┬──────────────────────┘         └───────────────▲───────────────┘
         │                                                │
         ├──────────────────────┐                         │ Stats.add(...)
         ▼                      ▼                         │
┌─────────────────┐    ┌──────────────────┐               │
│ Wi-Fi Socket    │    │ Cellular Socket  │               │
│ OkHttpClient 1  │    │ OkHttpClient 2   │               │
└────────┬────────┘    └────────┬─────────┘               │
         │                      │                         │
         └──────────┬───────────┘                         │
                    ▼                                     │
┌───────────────────────────────────────┐                 │
│ Parallel Streaming Engine (Workers)   │─────────────────┘
│ (DownloadEngine.kt)                   │
│ • Verified Google CDN & Cachefly      │
│ • Reused 128KB in-memory buffers      │
│ • Discard immediately (0 MB Storage)  │
└───────────────────────────────────────┘
```

---

## 3. Step-by-Step Execution Pipeline

### Stage 1: UI & User Configuration (`MainActivity.kt`)
1. User chooses which networks to activate: **Wi-Fi**, **SIM Mobile Data**, or **Both**.
2. User specifies an optional **Speed Cap** (0 = Unlimited / Max Throughput, or sets a limit in Mbps).
3. Clicking **"START PULLING"** triggers a confirmation dialog that shows selected networks and target rate.
4. An explicit intent with `ACTION_START` is sent to `PullService`.

### Stage 2: Foreground Service Initialization (`PullService.kt`)
1. In Android 14/15, background execution requires a declared foreground service type. NetPull starts with `foregroundServiceType="dataSync"`.
2. A low-importance notification channel is registered (`IMPORTANCE_LOW` ensures notifications update smoothly every second without beeping).
3. A persistent notification is posted with the app's custom White & Cream logo, start timestamp, target speed, and interactive action buttons: **Pause/Resume** and **Stop**.

### Stage 3: Dual-Network Binding Pipeline (`ConnectivityManager`)
Normal Android devices route 100% of traffic through Wi-Fi whenever Wi-Fi is connected, putting the mobile data interface to sleep. NetPull circumvents this:
1. `ConnectivityManager.requestNetwork(...)` is called with:
   - `NetworkCapabilities.TRANSPORT_WIFI`
   - `NetworkCapabilities.TRANSPORT_CELLULAR`
2. Android OS allocates dedicated `Network` handles for each physical interface.
3. For each network, a separate `OkHttpClient` is constructed:
   ```kotlin
   clientBuilder.socketFactory(network.socketFactory)
   clientBuilder.dns { hostname -> network.getAllByName(hostname).toList() }
   ```
4. This binds TCP sockets directly to the hardware modem/chipset:
   - Wi-Fi packets travel exclusively over the Wi-Fi radio.
   - Cellular packets travel exclusively over the SIM radio.

### Stage 4: Zero-Storage & Zero-Cache Continuous Streaming (`DownloadEngine.kt`)
To pull maximum internet without filling up storage or cache memory:
1. **6 parallel streaming workers** are spawned per active network lane.
2. Each worker pulls data from a pool of high-capacity production CDN endpoints:
   - `https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip` (146 MB)
   - `https://cachefly.cachefly.net/100mb.test` (100 MB)
   - `https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip` (146 MB)
   - `https://speedtest.serverius.net/files/100mb.bin` (100 MB)
   - `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip` (146 MB)
3. Data is read into a reusable `ByteArray(128 * 1024)` (128 KB buffer).
4. Bytes are added to atomic counters (`stats.add(lane.slot, n)`).
5. **No file channel or disk write occurs**. The buffer is immediately overwritten in memory. Disk and cache usage is strictly **0 MB**.
6. When a file stream reaches EOF, workers immediately rotate to the next Google CDN URL with keep-alive connections, preventing stalls.

### Stage 5: Real-Time Speed & Telemetry Pipeline
1. Every second, `meterJob` measures the exact millisecond time delta:
   $$\Delta t = \frac{\text{nowTimestamp} - \text{prevTimestamp}}{1000.0}$$
2. Exact speed is computed:
   - **MegaBits/sec (Mbps)**: `(bytesDiff * 8.0) / (dt * 1_000_000.0)`
   - **MegaBytes/sec (MB/s)**: `bytesDiff / (dt * 1024.0 * 1024.0)`
3. Metrics are updated via Kotlin `StateFlow` and reflected across:
   - In-app Speedometer (Total Mbps, MB/s, Peak speed, Duration, Total MB/GB pulled, Wi-Fi vs SIM).
   - Status bar notification drawer.

### Stage 6: Pause, Resume & Teardown Pipeline
- **Pause**: When user taps Pause (in app or notification), workers hold without disconnecting sockets, dropping throughput to 0 Mbps while keeping the service alive.
- **Stop**:
  1. Workers are cancelled and OkHttp dispatchers cancel all in-flight calls (`dispatcher.cancelAll()`).
  2. Network callbacks are unregistered via `ConnectivityManager.unregisterNetworkCallback(...)`, returning routing control back to Android OS.
  3. The foreground service removes its ongoing notification and stops itself.
  4. An **End Session Summary Dialog** opens on screen, displaying:
     - Total duration.
     - Total data pulled (MB / GB).
     - Peak receiving speed achieved.
     - Confirmation of 0 MB disk storage used.

---

## 4. Build & Deployment Pipeline

```bash
# 1. Compile debug APK using Gradle 8.7 & AGP 8.5.2
./gradlew assembleDebug

# 2. Deploy APK to physical device over ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Launch MainActivity
adb shell am start -n com.example.netpull/.MainActivity
```
