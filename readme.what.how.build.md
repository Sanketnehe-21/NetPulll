# NetPull • Complete Technical Guide (What • How • Build)

## 1. WHAT is this app?

**NetPull** is an Android utility designed to pull maximum internet throughput by combining **Wi-Fi and SIM Mobile Data simultaneously** while running 100% in the background, consuming **0 MB disk storage** and **0 MB cache memory**.

### Core Stack & Libraries Used
- **Language**: Kotlin 1.9.24 (Java 17 target)
- **Framework**: Android SDK (MinSdk 26 [Android 8.0] to CompileSdk 34 [Android 14/15])
- **Networking**: `com.squareup.okhttp3:okhttp:4.12.0` (HTTP/1.1 socket factory and custom DNS binding)
- **Concurrency**: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1` (Coroutines, StateFlow, CoroutineScope)
- **Android Architecture**:
  - `androidx.core:core-ktx:1.13.1`
  - `androidx.appcompat:appcompat:1.7.0`
  - `androidx.lifecycle:lifecycle-runtime-ktx:2.8.4`
- **Visuals**: Vector drawables designed in **White & Cream** (`#FBF8F2`, `#FFFFFF`, `#CBB279`).

---

## 2. HOW does it work under the hood?

### A. Dual-Network Bonding (Wi-Fi + SIM simultaneously)
Normal Android devices prioritize Wi-Fi and completely disable mobile data routing when Wi-Fi connects. NetPull bypasses this using low-level Android Network APIs:
1. It creates two distinct `NetworkRequest` objects:
   - `NetworkCapabilities.TRANSPORT_WIFI`
   - `NetworkCapabilities.TRANSPORT_CELLULAR`
2. It requests both through `ConnectivityManager.requestNetwork(...)`.
3. For each network returned by Android, NetPull creates a dedicated `OkHttpClient` instance:
   - Sockets are bound to the specific physical interface: `clientBuilder.socketFactory(network.socketFactory)`.
   - DNS resolution is forced through that network interface: `clientBuilder.dns { network.getAllByName(it).toList() }`.
4. As a result, requests sent on the Wi-Fi client travel purely through Wi-Fi, and requests on the SIM client travel purely through cellular towers at the exact same moment.

### B. Zero-Cache & Zero-Storage Architecture
Traditional download apps save data to files (`FileOutputStream`, cache partitions, or Temp directories). 
NetPull is designed as an internet puller:
- A single reusable `ByteArray(128 * 1024)` (128 KB) buffer is recycled in memory.
- Packets arriving over TCP are read into this buffer from verified high-speed production CDN endpoints (Google CDN `dl.google.com` & CacheFly).
- The byte count is immediately added to atomic statistics (`Stats`).
- **No file or database is written to disk**. The buffer is overwritten in the next iteration. Storage consumed is **0 bytes**.

### C. True Speed Measurement (Mbps vs MB/s)
Many speedometers report inaccurate speeds due to timer jitter. NetPull uses strict timestamp delta calculations:
$$\Delta t = \frac{\text{System.currentTimeMillis()} - \text{prevTimestamp}}{1000.0}$$
$$\text{Speed (Mbps)} = \frac{\Delta \text{Bytes} \times 8}{\Delta t \times 1,000,000}$$
$$\text{Speed (MB/s)} = \frac{\Delta \text{Bytes}}{\Delta t \times 1,048,576}$$

Both **Mbps (Megabits per second)** and **MB/s (Megabytes per second)** are displayed to eliminate any ambiguity.

### D. Background Foreground Service
- Runs as an Android `ForegroundService` with `android:foregroundServiceType="dataSync"`.
- Uses a persistent notification channel with `IMPORTANCE_LOW` (updates speed smoothly without beeping).
- Displays start timestamp, target rate, receiving speed, and interactive **Pause/Resume** and **Stop** buttons directly in the notification drawer.
- Operates uninterrupted when the screen is locked or while other apps are active.

---

## 3. BUILD & INSTALL Instructions

### Prerequisites
- JDK 17 (verified with OpenJDK 17.0.16)
- Android SDK Platform 34 and Build-Tools 34.0.0 (`ANDROID_HOME` configured)
- Android device with USB debugging enabled

### Build APK via Terminal
From the project root:
```bash
./gradlew assembleDebug
```
The resulting APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install & Launch via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.netpull/.MainActivity
```

---

## 4. Testing & Verification Checklist
- [x] App compiles with 0 errors via Gradle 8.7 / AGP 8.5.2.
- [x] White & Cream theme with custom vector logo.
- [x] Pre-flight network connectivity check (Wi-Fi + SIM ping).
- [x] Start confirmation dialog and End session summary popup.
- [x] Live notification with start time, target rate, receiving speed, and Pause/Stop actions.
- [x] Zero storage/cache usage verified.
