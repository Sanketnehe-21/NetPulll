package com.example.netpull

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PullService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var pullJob: Job? = null
    private var meterJob: Job? = null
    private lateinit var engine: DownloadEngine
    private lateinit var notifManager: NotificationManager

    companion object {
        const val CHANNEL_ID = "netpull_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.netpull.ACTION_START"
        const val ACTION_STOP = "com.example.netpull.ACTION_STOP"
        const val ACTION_PAUSE_RESUME = "com.example.netpull.ACTION_PAUSE_RESUME"

        const val EXTRA_USE_WIFI = "EXTRA_USE_WIFI"
        const val EXTRA_USE_CELL = "EXTRA_USE_CELL"
        const val EXTRA_CAP_MBPS = "EXTRA_CAP_MBPS"

        val stats = Stats()
        val limiter = RateLimiter()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _statusText = MutableStateFlow("Idle")
        val statusText: StateFlow<String> = _statusText.asStateFlow()

        // Precise live metrics
        private val _totalMbps = MutableStateFlow(0.0)
        val totalMbps: StateFlow<Double> = _totalMbps.asStateFlow()

        private val _totalMBps = MutableStateFlow(0.0)
        val totalMBps: StateFlow<Double> = _totalMBps.asStateFlow()

        private val _wifiMbps = MutableStateFlow(0.0)
        val wifiMbps: StateFlow<Double> = _wifiMbps.asStateFlow()

        private val _cellMbps = MutableStateFlow(0.0)
        val cellMbps: StateFlow<Double> = _cellMbps.asStateFlow()

        private val _peakMbps = MutableStateFlow(0.0)
        val peakMbps: StateFlow<Double> = _peakMbps.asStateFlow()

        private val _totalBytesPulled = MutableStateFlow(0L)
        val totalBytesPulled: StateFlow<Long> = _totalBytesPulled.asStateFlow()

        private val _startTimeStr = MutableStateFlow("")
        val startTimeStr: StateFlow<String> = _startTimeStr.asStateFlow()

        private val _targetCapStr = MutableStateFlow("Max Speed (Unlimited)")
        val targetCapStr: StateFlow<String> = _targetCapStr.asStateFlow()

        private val _durationSeconds = MutableStateFlow(0L)
        val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

        fun start(context: Context, useWifi: Boolean, useCell: Boolean, capMbps: Double) {
            val intent = Intent(context, PullService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_USE_WIFI, useWifi)
                putExtra(EXTRA_USE_CELL, useCell)
                putExtra(EXTRA_CAP_MBPS, capMbps)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PullService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun togglePause(context: Context) {
            val intent = Intent(context, PullService::class.java).apply {
                action = ACTION_PAUSE_RESUME
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NotificationManager::class.java)
        engine = DownloadEngine(applicationContext, limiter, stats)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPulling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE_RESUME -> {
                _isPaused.value = !_isPaused.value
                val status = if (_isPaused.value) "Paused" else "Resumed pulling"
                _statusText.value = status
                updateNotification()
            }
            ACTION_START -> {
                val useWifi = intent.getBooleanExtra(EXTRA_USE_WIFI, true)
                val useCell = intent.getBooleanExtra(EXTRA_USE_CELL, false)
                val capMbps = intent.getDoubleExtra(EXTRA_CAP_MBPS, 0.0)
                limiter.setMbps(capMbps)

                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                _startTimeStr.value = timeFmt.format(Date())
                _targetCapStr.value = if (capMbps <= 0.0) "Max Speed (Unlimited)" else "${String.format(Locale.US, "%.1f", capMbps)} Mbps"

                _isPaused.value = false
                startForeground(NOTIFICATION_ID, buildNotification())
                startPulling(useWifi, useCell)
            }
        }
        return START_NOT_STICKY
    }

    private fun startPulling(useWifi: Boolean, useCell: Boolean) {
        stopJobs()
        _isRunning.value = true
        _isPaused.value = false
        _peakMbps.value = 0.0
        _durationSeconds.value = 0L
        stats.reset()

        val startTimestamp = System.currentTimeMillis()

        // Precision speedometer loop with exact dt calculation
        meterJob = serviceScope.launch {
            var prevBytes = LongArray(3)
            var prevTimestamp = System.currentTimeMillis()

            while (isActive) {
                delay(1000)
                val nowTimestamp = System.currentTimeMillis()
                val dt = (nowTimestamp - prevTimestamp) / 1000.0
                if (dt <= 0.0) continue

                val nowBytes = LongArray(3) { stats.bytes.get(it) }

                fun mbps(slot: Int): Double {
                    val diff = (nowBytes[slot] - prevBytes[slot]).coerceAtLeast(0L)
                    return (diff * 8.0) / (dt * 1_000_000.0)
                }

                fun mbPerSec(slot: Int): Double {
                    val diff = (nowBytes[slot] - prevBytes[slot]).coerceAtLeast(0L)
                    return diff / (dt * 1024.0 * 1024.0)
                }

                val w = mbps(SLOT_WIFI)
                val c = mbps(SLOT_CELL)
                val d = mbps(SLOT_DEFAULT)
                val totalMbit = w + c + d
                val totalMByte = mbPerSec(SLOT_WIFI) + mbPerSec(SLOT_CELL) + mbPerSec(SLOT_DEFAULT)

                prevBytes = nowBytes
                prevTimestamp = nowTimestamp

                _totalMbps.value = totalMbit
                _totalMBps.value = totalMByte
                _wifiMbps.value = w
                _cellMbps.value = c
                _totalBytesPulled.value = stats.totalEver.get()
                _durationSeconds.value = (nowTimestamp - startTimestamp) / 1000L

                if (totalMbit > _peakMbps.value) {
                    _peakMbps.value = totalMbit
                }

                updateNotification()
            }
        }

        // Streaming job
        pullJob = serviceScope.launch {
            try {
                _statusText.value = "Checking network links..."
                val check = engine.checkInternet()
                _statusText.value = check.summary

                delay(400)
                engine.startPull(useWifi, useCell, { _isPaused.value }) { msg ->
                    _statusText.value = msg
                }
            } catch (e: CancellationException) {
                _statusText.value = "Stopped"
            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    private fun updateNotification() {
        if (!_isRunning.value) return
        try {
            notifManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun stopJobs() {
        pullJob?.cancel()
        pullJob = null
        meterJob?.cancel()
        meterJob = null
    }

    private fun stopPulling() {
        stopJobs()
        _isRunning.value = false
        _isPaused.value = false
        _totalMbps.value = 0.0
        _totalMBps.value = 0.0
        _wifiMbps.value = 0.0
        _cellMbps.value = 0.0
        _statusText.value = "Stopped"
    }

    override fun onDestroy() {
        stopPulling()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NetPull Active Notification",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live pull speed, target rate, and controls"
                setShowBadge(false)
            }
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun getLogoBitmap(): Bitmap? {
        return try {
            val drawable: Drawable = ContextCompat.getDrawable(this, R.drawable.ic_netpull_logo) ?: return null
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PullService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PullService::class.java).apply { action = ACTION_PAUSE_RESUME },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val paused = _isPaused.value
        val totalMb = _totalMbps.value
        val totalMB = _totalMBps.value
        val wifiMb = _wifiMbps.value
        val cellMb = _cellMbps.value
        val target = _targetCapStr.value
        val startedAt = _startTimeStr.value
        val totalPulledMB = _totalBytesPulled.value / (1024.0 * 1024.0)

        val title = if (paused) "NetPull • PAUSED" else "NetPull • PULLING MAX INTERNET"

        val line1 = if (paused) "Session paused • Tap Resume" else String.format(
            Locale.US,
            "Receiving: %.1f Mbps (%.1f MB/s)",
            totalMb, totalMB
        )

        val line2 = String.format(
            Locale.US,
            "Wi-Fi: %.1f Mbps | SIM: %.1f Mbps • Target: %s",
            wifiMb, cellMb, target
        )

        val line3 = String.format(
            Locale.US,
            "Started: %s • Pulled: %.1f MB (0 disk used)",
            startedAt, totalPulledMB
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .addLine(line1)
            .addLine(line2)
            .addLine(line3)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(line1)
            .setStyle(inboxStyle)
            .setSmallIcon(R.drawable.ic_stat_netpull)
            .setColor(Color.parseColor("#CBB279"))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        getLogoBitmap()?.let { builder.setLargeIcon(it) }

        return builder.build()
    }
}
