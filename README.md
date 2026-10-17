# NetPull • Dual-Network High-Throughput Engine

<p align="center">
  <img src="Main screen light and dark.png" alt="NetPull UI Preview (Light and Dark Mode)" width="750" />
</p>

<p align="center">
  <b>A native Android network utility engineered to pull maximum bandwidth simultaneously across Wi-Fi and Cellular SIM lines with 0 MB disk storage and 0 MB cache usage.</b>
</p>

---

## ✨ Features

- ⚡ **Simultaneous Dual-Network Bonding**: Binds independent TCP sockets directly to hardware physical interfaces (`Wi-Fi` and `Cellular 4G/5G`) using low-level Android `ConnectivityManager` APIs.
- 🛡️ **Zero Disk & Zero Cache Storage**: Streams incoming packets into recycled 128 KB memory buffers and discards them immediately. 0 MB files written to phone storage or cache.
- 📱 **Real-Time Speedometer**: Accurate millisecond-delta speed tracking displaying throughput in both **Mbps** and **MB/s**, along with **Peak Speed**, **Total Data Pulled**, and **Session Time**.
- 🌊 **Smooth Wave Animation**: Custom 60 FPS concentric pulsing ripple wave animation in champagne gold behind the hero speed numbers.
- 🔄 **Bandwidth Throttling / Speed Cap**: Quick preset selectors (`Max / Unlimited`, `25M`, `50M`, `100M`, `Custom`) with dynamic real-time throttling.
- 🌓 **Adaptive Theming**: Automatically adapts to device theme (Warm Luxury Cream for Light Mode; Deep Obsidian Charcoal for Dark Mode).
- 🔔 **Background Foreground Service**: Operates uninterrupted when the screen is locked or while multitasking, with a persistent status bar notification featuring live speed updates, start time, target rate, and Pause/Stop buttons.
- 🔍 **Pre-Flight Network Diagnostic**: Checks connectivity status across Wi-Fi and SIM data before initiating pull streams.

---

## 🛠️ Architecture & Pipeline

```
                 ┌─────────────────────────────────┐
                 │       MainActivity (UI)         │
                 └────────────────┬────────────────┘
                                  │ Intent (ACTION_START)
                                  ▼
                 ┌─────────────────────────────────┐
                 │    PullService (Foreground)     │
                 │   type="dataSync" + Notif       │
                 └────────────────┬────────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         │                                                 │
         ▼                                                 ▼
┌───────────────────────────────┐         ┌───────────────────────────────┐
│ Network Acquisition Pipeline  │         │ Live Telemetry & Notification │
│ (ConnectivityManager API)     │         │ (1-second Delta Loop)         │
└────────┬──────────────────────┘         └───────────────▲───────────────┘
         │                                                │
         ├──────────────────────┐                         │ stats.add(...)
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
│ • Verified Google CDN & Cachefly      │
│ • Reused 128KB in-memory buffers      │
│ • Discard immediately (0 MB Storage)  │
└───────────────────────────────────────┘
```

For an in-depth technical breakdown of every library, class, and socket implementation, see [readme.what.tech.md](readme.what.tech.md) and [readme.what.how.build.md](readme.what.how.build.md).

---

## 🚀 Building & Running

### Requirements
- Android SDK Platform 34 (Android 14)
- Build-Tools 34.0.0
- JDK 17 (Java 17 LTS)
- Android device running Android 8.0+ (API 26+)

### Build APK via Gradle
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.netpull/.SplashActivity
```

---

## 🔒 Privacy & Safety Guarantee

- **Safe Networking**: NetPull only accesses network interfaces that the user legitimately connects to (Wi-Fi and user's cellular SIM plan).
- **Zero Interception**: NetPull does not intercept, modify, or proxy other applications' traffic.
- **Zero Cache**: Data pulled from public CDN endpoints is discarded immediately in volatile memory buffers.

---

## 📄 License
This project is open-source and available under the standard MIT License.
