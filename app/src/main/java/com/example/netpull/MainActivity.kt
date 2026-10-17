package com.example.netpull

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── State ──
    private var isWifiOn = true
    private var isSimOn = false
    private var currentPill = "max" // "max", "25", "50", "100", "custom"
    private var customCapValue = 0.0

    // ── Design Theme Colors (Exact match to Figma/HTML spec) ──
    private var isDark = false
    private var cBg = 0
    private var cCard = 0
    private var cBorder = 0
    private var cText = 0
    private var cMuted = 0
    private var cGold = 0
    private var cGoldText = 0
    private var cGoldTint = 0
    private var cGoldLine = 0
    private var cOnGold = 0
    private var cGreen = 0
    private var cGreenTint = 0
    private var cTrack = 0
    private var cDivider = 0

    // ── UI Views ──
    private lateinit var statusBadgeDot: View
    private lateinit var statusBadgeText: TextView
    private lateinit var statusBadgeContainer: LinearLayout

    private lateinit var speedNumberText: TextView
    private lateinit var speedMbsText: TextView
    private lateinit var peakValueText: TextView
    private lateinit var totalPulledValueText: TextView
    private lateinit var timeValueText: TextView
    private lateinit var pullWaveView: PullWaveView

    // Wi-Fi Card Views
    private lateinit var wifiCard: LinearLayout
    private lateinit var wifiSwitchTrack: FrameLayout
    private lateinit var wifiSwitchKnob: View
    private lateinit var wifiSpeedNumber: TextView
    private lateinit var wifiBadgePill: LinearLayout
    private lateinit var wifiBadgeDot: View
    private lateinit var wifiBadgeText: TextView

    // SIM Card Views
    private lateinit var simCard: LinearLayout
    private lateinit var simSwitchTrack: FrameLayout
    private lateinit var simSwitchKnob: View
    private lateinit var simSpeedNumber: TextView
    private lateinit var simBadgePill: LinearLayout
    private lateinit var simBadgeDot: View
    private lateinit var simBadgeText: TextView
    private lateinit var simSubtitleText: TextView

    // Speed Target Views
    private val pillViews = mutableMapOf<String, TextView>()
    private lateinit var customLimitRow: LinearLayout
    private lateinit var customLimitInput: EditText
    private lateinit var explainBannerText: TextView

    // Action Buttons
    private lateinit var mainActionButton: Button
    private lateinit var pauseResumeButton: Button
    private lateinit var testNetworkLink: TextView

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notification permission needed for live status bar speed", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initThemePalette()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        buildScreen()
        observeServiceState()
    }

    private fun initThemePalette() {
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            cBg = Color.parseColor("#121110")
            cCard = Color.parseColor("#1C1A17")
            cBorder = Color.parseColor("#292623")
            cText = Color.parseColor("#F3EEE4")
            cMuted = Color.parseColor("#A39C8F")
            cGold = Color.parseColor("#D6BE8B")
            cGoldText = Color.parseColor("#D6BE8B")
            cGoldTint = Color.parseColor("#2E281E")
            cGoldLine = Color.parseColor("#574C37")
            cOnGold = Color.parseColor("#1D1B18")
            cGreen = Color.parseColor("#5FD08C")
            cGreenTint = Color.parseColor("#1B3324")
            cTrack = Color.parseColor("#3A3631")
            cDivider = Color.parseColor("#262320")
        } else {
            cBg = Color.parseColor("#FBF8F2")
            cCard = Color.parseColor("#FFFFFF")
            cBorder = Color.parseColor("#E9E3D8")
            cText = Color.parseColor("#1D1B18")
            cMuted = Color.parseColor("#6B665E")
            cGold = Color.parseColor("#CBB279")
            cGoldText = Color.parseColor("#8C7330")
            cGoldTint = Color.parseColor("#F4EDDE")
            cGoldLine = Color.parseColor("#E3D5B5")
            cOnGold = Color.parseColor("#1D1B18")
            cGreen = Color.parseColor("#1F7A46")
            cGreenTint = Color.parseColor("#E5F2EB")
            cTrack = Color.parseColor("#DDD6C8")
            cDivider = Color.parseColor("#ECE6DB")
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun shape(bg: Int, stroke: Int = 0, strokeW: Int = 0, radiusDp: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bg)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeW > 0) setStroke(dp(strokeW), stroke)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // ══════════════════════════════════════════════
        // 1 • HEADER (Logo, NetPull title, Status Badge)
        // ══════════════════════════════════════════════
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { bottomMargin = dp(12) }
        }

        // Logo Container (Squircle)
        val logoSquircle = FrameLayout(this).apply {
            background = shape(if (isDark) Color.parseColor("#1C1A17") else Color.parseColor("#FBF8F2"), 0, 0, 11)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }
        }
        val logoCircle = ImageView(this).apply {
            setImageResource(R.drawable.ic_netpull_logo)
            layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
        }
        logoSquircle.addView(logoCircle)

        // Title and Subtitle
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleText = TextView(this).apply {
            text = "NetPull"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
            letterSpacing = -0.02f
        }
        val subText = TextView(this).apply {
            text = "Dual-Network Engine"
            textSize = 12f
            setTextColor(cMuted)
        }
        titleBox.addView(titleText)
        titleBox.addView(subText)

        // Top Right Status Badge Pill
        statusBadgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cDivider, 0, 0, 14)
            setPadding(dp(11), 0, dp(11), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28)
            )
        }
        statusBadgeDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9A948A"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(6) }
        }
        statusBadgeText = TextView(this).apply {
            text = "Idle"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
        }
        statusBadgeContainer.addView(statusBadgeDot)
        statusBadgeContainer.addView(statusBadgeText)

        header.addView(logoSquircle)
        header.addView(titleBox)
        header.addView(statusBadgeContainer)
        root.addView(header)

        // ══════════════════════════════════════════════
        // 2 • HERO SPEEDOMETER CARD (Concentric Waves + Stats)
        // ══════════════════════════════════════════════
        val heroCard = FrameLayout(this).apply {
            background = shape(cCard, cBorder, 1, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        // Concentric expanding ripple waves
        pullWaveView = PullWaveView(this).apply {
            waveColor = cGold
            isPulling = false
            layoutParams = FrameLayout.LayoutParams(dp(300), dp(180), Gravity.CENTER)
        }
        heroCard.addView(pullWaveView)

        val heroContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val speedTopLabel = TextView(this).apply {
            text = "DOWNLOAD SPEED"
            textSize = 11f
            letterSpacing = 0.10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
            gravity = Gravity.CENTER
        }

        // Main Speed Text Row
        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }

        speedNumberText = TextView(this).apply {
            text = "0.0"
            textSize = 38f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
            letterSpacing = -0.03f
        }
        val mbpsUnit = TextView(this).apply {
            text = " Mbps "
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
        }
        speedMbsText = TextView(this).apply {
            text = "(0.0 MB/s)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cGoldText)
        }
        speedRow.addView(speedNumberText)
        speedRow.addView(mbpsUnit)
        speedRow.addView(speedMbsText)

        // Stats Row (Peak Speed | Total Pulled | Time)
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun statCol(label: String, initVal: String): Pair<LinearLayout, TextView> {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val lbl = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(cMuted)
            }
            val valView = TextView(this).apply {
                text = initVal
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(cText)
            }
            col.addView(lbl)
            col.addView(valView)
            return Pair(col, valView)
        }

        val peakCol = statCol("Peak Speed", "0.0 Mbps")
        val totalCol = statCol("Total Pulled", "0 MB")
        val timeCol = statCol("Time", "00:00")

        peakValueText = peakCol.second
        totalPulledValueText = totalCol.second
        timeValueText = timeCol.second

        statsRow.addView(peakCol.first)
        statsRow.addView(totalCol.first)
        statsRow.addView(timeCol.first)

        heroContent.addView(speedTopLabel)
        heroContent.addView(speedRow)
        heroContent.addView(statsRow)
        heroCard.addView(heroContent)
        root.addView(heroCard)

        // ══════════════════════════════════════════════
        // 3 • NETWORK CARDS (Wi-Fi Line & SIM Data)
        // ══════════════════════════════════════════════
        val netCardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        // ── Wi-Fi Card ──
        wifiCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(cCard, cBorder, 1, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { toggleWifi() }
        }

        val wifiTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        }
        val wifiIconCircle = FrameLayout(this).apply {
            background = shape(cGoldTint, 0, 0, 17)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        val wifiIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_wifi)
            setColorFilter(cGoldText)
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        }
        wifiIconCircle.addView(wifiIcon)

        // Switch Toggle (46x28dp with 22x22dp knob)
        wifiSwitchTrack = FrameLayout(this).apply {
            background = shape(cGold, 0, 0, 14)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(28))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        wifiSwitchKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.END or Gravity.CENTER_VERTICAL)
        }
        wifiSwitchTrack.addView(wifiSwitchKnob)

        val wifiSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        wifiTop.addView(wifiIconCircle)
        wifiTop.addView(wifiSpacer)
        wifiTop.addView(wifiSwitchTrack)

        val wifiTitle = TextView(this).apply {
            text = "Wi-Fi Line"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
            setPadding(0, dp(8), 0, 0)
        }
        val wifiSubtitle = TextView(this).apply {
            text = "Primary Broadband"
            textSize = 12f
            setTextColor(cMuted)
        }

        val wifiSpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, dp(6))
        }
        wifiSpeedNumber = TextView(this).apply {
            text = "0.0"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
        }
        val wifiSpeedUnit = TextView(this).apply {
            text = " Mbps"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
            setPadding(0, 0, 0, dp(2))
        }
        wifiSpeedRow.addView(wifiSpeedNumber)
        wifiSpeedRow.addView(wifiSpeedUnit)

        wifiBadgePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cDivider, 0, 0, 11)
            setPadding(dp(9), 0, dp(9), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(22)
            )
        }
        wifiBadgeDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cMuted)
            }
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(5) }
        }
        wifiBadgeText = TextView(this).apply {
            text = "Ready"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
        }
        wifiBadgePill.addView(wifiBadgeDot)
        wifiBadgePill.addView(wifiBadgeText)

        wifiCard.addView(wifiTop)
        wifiCard.addView(wifiTitle)
        wifiCard.addView(wifiSubtitle)
        wifiCard.addView(wifiSpeedRow)
        wifiCard.addView(wifiBadgePill)

        // ── SIM Card ──
        simCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(cCard, cBorder, 1, 24)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener { toggleSim() }
        }

        val simTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        }
        val simIconCircle = FrameLayout(this).apply {
            background = shape(cGoldTint, 0, 0, 17)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        val simIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_cellular)
            setColorFilter(cGoldText)
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        }
        simIconCircle.addView(simIcon)

        simSwitchTrack = FrameLayout(this).apply {
            background = shape(cTrack, 0, 0, 14)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(28))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        simSwitchKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.START or Gravity.CENTER_VERTICAL)
        }
        simSwitchTrack.addView(simSwitchKnob)

        val simSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        simTop.addView(simIconCircle)
        simTop.addView(simSpacer)
        simTop.addView(simSwitchTrack)

        val simTitle = TextView(this).apply {
            text = "SIM Data"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
            setPadding(0, dp(8), 0, 0)
        }
        simSubtitleText = TextView(this).apply {
            text = "Dual Pull Off"
            textSize = 12f
            setTextColor(cMuted)
        }

        val simSpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, dp(6))
        }
        simSpeedNumber = TextView(this).apply {
            text = "0.0"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
        }
        val simSpeedUnit = TextView(this).apply {
            text = " Mbps"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
            setPadding(0, 0, 0, dp(2))
        }
        simSpeedRow.addView(simSpeedNumber)
        simSpeedRow.addView(simSpeedUnit)

        val simBottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        simBadgePill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cDivider, 0, 0, 11)
            setPadding(dp(9), 0, dp(9), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(22)
            )
        }
        simBadgeDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cMuted)
            }
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(5) }
        }
        simBadgeText = TextView(this).apply {
            text = "Off"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
        }
        simBadgePill.addView(simBadgeDot)
        simBadgePill.addView(simBadgeText)

        val simDataInfo = TextView(this).apply {
            text = "ⓘ Uses your data plan"
            textSize = 10f
            setTextColor(cMuted)
            setPadding(0, dp(4), 0, 0)
        }
        simBottomRow.addView(simBadgePill)
        simBottomRow.addView(simDataInfo)

        simCard.addView(simTop)
        simCard.addView(simTitle)
        simCard.addView(simSubtitleText)
        simCard.addView(simSpeedRow)
        simCard.addView(simBottomRow)

        netCardsRow.addView(wifiCard)
        netCardsRow.addView(simCard)
        root.addView(netCardsRow)

        // ══════════════════════════════════════════════
        // 4 • SPEED TARGET (Pills, Custom Input, Explainer)
        // ══════════════════════════════════════════════
        val targetLabel = TextView(this).apply {
            text = "SPEED TARGET"
            textSize = 11f
            letterSpacing = 0.10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cMuted)
            setPadding(dp(2), 0, 0, dp(6))
        }
        root.addView(targetLabel)

        // 5 Pills Row: [Max] [25M] [50M] [100M] [Custom]
        val pillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            ).apply { bottomMargin = dp(8) }
        }

        fun pillBtn(id: String, textLabel: String): TextView {
            return TextView(this).apply {
                text = textLabel
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(cText)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = if (id == "custom") 0 else dp(6)
                }
                setOnClickListener { selectPill(id) }
                pillViews[id] = this
            }
        }

        pillsRow.addView(pillBtn("max", "Max"))
        pillsRow.addView(pillBtn("25", "25M"))
        pillsRow.addView(pillBtn("50", "50M"))
        pillsRow.addView(pillBtn("100", "100M"))
        pillsRow.addView(pillBtn("custom", "Custom"))
        root.addView(pillsRow)

        // Custom Limit Input Box
        customLimitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(cCard, cGold, 2, 14)
            setPadding(dp(14), 0, dp(14), 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply { bottomMargin = dp(8) }
        }
        val customPrefix = TextView(this).apply {
            text = "Custom limit"
            textSize = 12f
            setTextColor(cMuted)
        }
        customLimitInput = EditText(this).apply {
            hint = "0"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cText)
            setHintTextColor(cMuted)
            background = null
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val v = s?.toString()?.toDoubleOrNull() ?: 0.0
                    customCapValue = v
                    PullService.limiter.setMbps(v)
                    updateExplainerText()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            })
        }
        val customSuffix = TextView(this).apply {
            text = " Mbps"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cGoldText)
            setPadding(dp(4), 0, 0, 0)
        }
        customLimitRow.addView(customPrefix)
        customLimitRow.addView(customLimitInput)
        customLimitRow.addView(customSuffix)
        root.addView(customLimitRow)

        // Explainer Callout Banner
        val calloutBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = shape(cGoldTint, cGoldLine, 1, 16)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        val infoIcon = TextView(this).apply {
            text = "ⓘ "
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cGoldText)
        }
        explainBannerText = TextView(this).apply {
            text = "Pulling the full bandwidth available across active networks."
            textSize = 13f
            setTextColor(cText)
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        calloutBox.addView(infoIcon)
        calloutBox.addView(explainBannerText)
        root.addView(calloutBox)

        // ══════════════════════════════════════════════
        // 5 • ACTION BUTTONS (Start/Stop, Pause, Test Network)
        // ══════════════════════════════════════════════
        val buttonsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        // Primary Action Button (START PULLING / STOP PULLING)
        mainActionButton = Button(this).apply {
            text = "▶   START PULLING"
            textSize = 14f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            background = shape(cGold, 0, 0, 18)
            setTextColor(cOnGold)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { handleRunToggle() }
        }

        // Secondary Button (PAUSE / RESUME)
        pauseResumeButton = Button(this).apply {
            text = "❚❚   PAUSE"
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            background = shape(cDivider, 0, 0, 16)
            setTextColor(cMuted)
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { handlePauseToggle() }
        }

        // Test Network Link
        testNetworkLink = TextView(this).apply {
            text = "Test Network"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(cGoldText)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { checkNetworks() }
        }

        buttonsCol.addView(mainActionButton)
        buttonsCol.addView(pauseResumeButton)
        buttonsCol.addView(testNetworkLink)
        root.addView(buttonsCol)

        // ══════════════════════════════════════════════
        // 6 • FOOTER GUARANTEE
        // ══════════════════════════════════════════════
        val footerText = TextView(this).apply {
            text = "✓ Zero Cache • Runs in Background"
            textSize = 11f
            setTextColor(cMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(footerText)

        setContentView(ScrollView(this).apply { addView(root) })

        // Initialize state styling
        updateSwitchesUI()
        selectPill("max")
    }

    private fun toggleWifi() {
        isWifiOn = !isWifiOn
        updateSwitchesUI()
    }

    private fun toggleSim() {
        isSimOn = !isSimOn
        updateSwitchesUI()
    }

    private fun updateSwitchesUI() {
        // Wi-Fi Switch
        wifiSwitchTrack.background = shape(if (isWifiOn) cGold else cTrack, 0, 0, 14)
        (wifiSwitchKnob.layoutParams as FrameLayout.LayoutParams).gravity =
            (if (isWifiOn) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        wifiSwitchKnob.requestLayout()

        // SIM Switch
        simSwitchTrack.background = shape(if (isSimOn) cGold else cTrack, 0, 0, 14)
        (simSwitchKnob.layoutParams as FrameLayout.LayoutParams).gravity =
            (if (isSimOn) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        simSwitchKnob.requestLayout()
        simSubtitleText.text = if (isSimOn) "Dual Pull Active" else "Dual Pull Off"

        updateBadges()
    }

    private fun updateBadges() {
        val running = PullService.isRunning.value
        val paused = PullService.isPaused.value

        // Helper
        fun setPill(
            pill: LinearLayout, dot: View, textV: TextView,
            enabled: Boolean
        ) {
            if (!enabled) {
                pill.background = shape(cDivider, 0, 0, 11)
                (dot.background as GradientDrawable).setColor(cMuted)
                textV.setTextColor(cMuted)
                textV.text = "Off"
            } else if (!running) {
                pill.background = shape(cDivider, 0, 0, 11)
                (dot.background as GradientDrawable).setColor(cMuted)
                textV.setTextColor(cMuted)
                textV.text = "Ready"
            } else if (paused) {
                pill.background = shape(cGoldTint, 0, 0, 11)
                (dot.background as GradientDrawable).setColor(cGold)
                textV.setTextColor(cGoldText)
                textV.text = "Paused"
            } else {
                pill.background = shape(cGreenTint, 0, 0, 11)
                (dot.background as GradientDrawable).setColor(cGreen)
                textV.setTextColor(cGreen)
                textV.text = "Active"
            }
        }

        setPill(wifiBadgePill, wifiBadgeDot, wifiBadgeText, isWifiOn)
        setPill(simBadgePill, simBadgeDot, simBadgeText, isSimOn)
    }

    private fun selectPill(id: String) {
        currentPill = id

        pillViews.forEach { (key, tv) ->
            if (key == id) {
                tv.background = shape(cGoldTint, cGold, 2, 12)
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextColor(cText)
            } else {
                tv.background = shape(cCard, cBorder, 1, 12)
                tv.setTypeface(null, Typeface.NORMAL)
                tv.setTextColor(cMuted)
            }
        }

        customLimitRow.visibility = if (id == "custom") View.VISIBLE else View.GONE

        val cap = when (id) {
            "max" -> 0.0
            "25" -> 25.0
            "50" -> 50.0
            "100" -> 100.0
            "custom" -> customCapValue
            else -> 0.0
        }
        PullService.limiter.setMbps(cap)
        updateExplainerText()
    }

    private fun updateExplainerText() {
        explainBannerText.text = when (currentPill) {
            "max" -> "Pulling the full bandwidth available across active networks."
            "custom" -> if (customCapValue > 0) "Capped at ${String.format(Locale.US, "%.0f", customCapValue)} Mbps. You can change this while downloading."
                        else "Enter a limit in Mbps to cap the pull. Until then, Max applies."
            else -> "Capped at $currentPill Mbps. You can change this while downloading."
        }
    }

    private fun handleRunToggle() {
        if (PullService.isRunning.value) {
            showEndSummaryDialog()
            PullService.stop(this)
        } else {
            if (!isWifiOn && !isSimOn) {
                Toast.makeText(this, "Select Wi-Fi, SIM data, or both", Toast.LENGTH_SHORT).show()
                return
            }
            val cap = when (currentPill) {
                "max" -> 0.0
                "25" -> 25.0
                "50" -> 50.0
                "100" -> 100.0
                "custom" -> customCapValue
                else -> 0.0
            }
            PullService.limiter.setMbps(cap)
            PullService.start(this, isWifiOn, isSimOn, cap)
        }
    }

    private fun handlePauseToggle() {
        if (PullService.isRunning.value) {
            PullService.togglePause(this)
        }
    }

    private fun checkNetworks() {
        statusBadgeText.text = "Testing..."
        statusBadgeText.setTextColor(cGoldText)
        (statusBadgeDot.background as GradientDrawable).setColor(cGold)
        lifecycleScope.launch {
            val engine = DownloadEngine(applicationContext, PullService.limiter, PullService.stats)
            val result = withContext(Dispatchers.IO) { engine.checkInternet() }
            statusBadgeText.text = "Ready"
            statusBadgeText.setTextColor(cGreen)
            (statusBadgeDot.background as GradientDrawable).setColor(cGreen)

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Network Connectivity Check")
                .setMessage(result.summary + "\n\nNetworks are ready for dual-carrier pulling.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showEndSummaryDialog() {
        val peak = PullService.peakMbps.value
        val totalBytes = PullService.totalBytesPulled.value
        val totalMB = totalBytes / (1024.0 * 1024.0)
        val durSec = PullService.durationSeconds.value
        val mins = durSec / 60
        val secs = durSec % 60
        val timeStr = String.format(Locale.US, "%02dm %02ds", mins, secs)

        AlertDialog.Builder(this)
            .setTitle("Pull Session Summary")
            .setMessage(
                "✓ Session Completed\n\n" +
                "• Duration: $timeStr\n" +
                "• Total Pulled: ${String.format(Locale.US, "%.2f MB", totalMB)}\n" +
                "• Peak Speed: ${String.format(Locale.US, "%.1f Mbps (%.1f MB/s)", peak, peak / 8.0)}\n" +
                "• Disk Storage Used: 0 MB (Zero Cache)\n\n" +
                "Network interfaces released back to system."
            )
            .setPositiveButton("Done", null)
            .show()
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            PullService.isRunning.collect { running ->
                val paused = PullService.isPaused.value
                pullWaveView.isPulling = running && !paused

                if (running) {
                    mainActionButton.text = "■   STOP PULLING"
                    mainActionButton.background = shape(Color.parseColor("#D32F2F"), 0, 0, 18)
                    mainActionButton.setTextColor(Color.WHITE)

                    pauseResumeButton.isEnabled = true
                    pauseResumeButton.background = shape(cGold, 0, 0, 16)
                    pauseResumeButton.setTextColor(cOnGold)
                } else {
                    mainActionButton.text = "▶   START PULLING"
                    mainActionButton.background = shape(cGold, 0, 0, 18)
                    mainActionButton.setTextColor(cOnGold)

                    pauseResumeButton.isEnabled = false
                    pauseResumeButton.background = shape(cDivider, 0, 0, 16)
                    pauseResumeButton.setTextColor(cMuted)
                    pauseResumeButton.text = "❚❚   PAUSE"

                    statusBadgeContainer.background = shape(cDivider, 0, 0, 14)
                    (statusBadgeDot.background as GradientDrawable).setColor(Color.parseColor("#9A948A"))
                    statusBadgeText.text = "Idle"
                    statusBadgeText.setTextColor(cMuted)
                }
                updateBadges()
            }
        }

        lifecycleScope.launch {
            PullService.isPaused.collect { paused ->
                val running = PullService.isRunning.value
                pullWaveView.isPulling = running && !paused

                if (running) {
                    if (paused) {
                        pauseResumeButton.text = "▶   RESUME"
                        statusBadgeContainer.background = shape(cGoldTint, 0, 0, 14)
                        (statusBadgeDot.background as GradientDrawable).setColor(cGold)
                        statusBadgeText.text = "Paused"
                        statusBadgeText.setTextColor(cGoldText)
                    } else {
                        pauseResumeButton.text = "❚❚   PAUSE"
                        statusBadgeContainer.background = shape(cGoldTint, 0, 0, 14)
                        (statusBadgeDot.background as GradientDrawable).setColor(cGreen)
                        statusBadgeText.text = "Pulling Active"
                        statusBadgeText.setTextColor(cText)
                    }
                }
                updateBadges()
            }
        }

        lifecycleScope.launch {
            PullService.totalMbps.collect { mbps ->
                speedNumberText.text = String.format(Locale.US, "%.1f", mbps)
            }
        }

        lifecycleScope.launch {
            PullService.totalMBps.collect { mbPerSec ->
                speedMbsText.text = String.format(Locale.US, "(%.1f MB/s)", mbPerSec)
            }
        }

        lifecycleScope.launch {
            PullService.peakMbps.collect { peak ->
                peakValueText.text = String.format(Locale.US, "%.1f Mbps", peak)
            }
        }

        lifecycleScope.launch {
            PullService.totalBytesPulled.collect { bytes ->
                val mb = bytes / (1024.0 * 1024.0)
                totalPulledValueText.text = if (mb >= 1024) {
                    String.format(Locale.US, "%.2f GB", mb / 1024.0)
                } else {
                    String.format(Locale.US, "%.0f MB", mb)
                }
            }
        }

        lifecycleScope.launch {
            PullService.durationSeconds.collect { sec ->
                val m = sec / 60
                val s = sec % 60
                timeValueText.text = String.format(Locale.US, "%02d:%02d", m, s)
            }
        }

        lifecycleScope.launch {
            PullService.wifiMbps.collect { mbps ->
                wifiSpeedNumber.text = String.format(Locale.US, "%.1f", mbps)
            }
        }

        lifecycleScope.launch {
            PullService.cellMbps.collect { mbps ->
                simSpeedNumber.text = String.format(Locale.US, "%.1f", mbps)
            }
        }
    }
}
