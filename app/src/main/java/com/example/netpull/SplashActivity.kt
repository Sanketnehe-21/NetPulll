package com.example.netpull

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) Color.parseColor("#121110") else Color.parseColor("#FBF8F2")
        val textColor = if (isDark) Color.parseColor("#FBF8F2") else Color.parseColor("#1D1B18")
        val subColor = if (isDark) Color.parseColor("#A0998E") else Color.parseColor("#7A746B")

        val dp = resources.displayMetrics.density
        fun dp(px: Int) = (px * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.ic_netpull_logo)
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110)).apply {
                gravity = Gravity.CENTER
                bottomMargin = dp(18)
            }
            alpha = 0f
            scaleX = 0.7f
            scaleY = 0.7f
        }

        val titleView = TextView(this).apply {
            text = "NetPull"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(20).toFloat()
        }

        val subtitleView = TextView(this).apply {
            text = "High-Throughput Network Engine\nZero Cache • Dual Line Bonding"
            textSize = 13f
            setTextColor(subColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            alpha = 0f
            translationY = dp(20).toFloat()
        }

        val wave = PullWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
                topMargin = dp(24)
                gravity = Gravity.CENTER
            }
            isPulling = true
            waveColor = Color.parseColor(if (isDark) "#D6BE8B" else "#CBB279")
            alpha = 0f
        }

        root.addView(logoView)
        root.addView(titleView)
        root.addView(subtitleView)
        root.addView(wave)

        setContentView(root)

        // Smooth entry animations
        logoView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        titleView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(700)
            .setStartDelay(200)
            .setInterpolator(DecelerateInterpolator())
            .start()

        subtitleView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(700)
            .setStartDelay(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        wave.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(400)
            .start()

        lifecycleScope.launch {
            delay(1700)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
