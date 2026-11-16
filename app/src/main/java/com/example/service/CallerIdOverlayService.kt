package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.OneBookApplication
import com.example.R

/**
 * Displays a compact floating Caller ID banner during incoming SIM ringing when overlay
 * permission is granted, with a heads-up notification fallback otherwise.
 */
class CallerIdOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "CRM Contact" }
        val company = intent?.getStringExtra(EXTRA_COMPANY).orEmpty()
        val stage = intent?.getStringExtra(EXTRA_STAGE).orEmpty()
        val note = intent?.getStringExtra(EXTRA_NOTE).orEmpty()
        val phone = intent?.getStringExtra(EXTRA_PHONE).orEmpty()

        if (Settings.canDrawOverlays(this)) {
            showOverlayCard(name, company, stage, note, phone)
        } else {
            showFallbackNotification(name, company, stage, phone)
        }

        handler.removeCallbacks(autoDismissRunnable)
        handler.postDelayed(autoDismissRunnable, 45_000L)
        return START_NOT_STICKY
    }

    private fun showOverlayCard(
        name: String,
        company: String,
        stage: String,
        note: String,
        phone: String
    ) {
        removeOverlay()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 18 * dp
                setStroke((1.5f * dp).toInt(), Color.parseColor("#14B8A6"))
            }
            elevation = 12 * dp
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeView = TextView(this).apply {
            text = if (stage.isNotBlank()) "ONEBOOK CRM • ${stage.uppercase()}" else "ONEBOOK CRM CALLER"
            textSize = 11f
            setTextColor(Color.parseColor("#2DD4BF"))
        }

        val nameView = TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        val subtitleView = TextView(this).apply {
            text = listOf(company, phone).filter { it.isNotBlank() }.joinToString(" • ")
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
        }

        titleBlock.addView(badgeView)
        titleBlock.addView(nameView)
        if (subtitleView.text.isNotBlank()) titleBlock.addView(subtitleView)

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#94A3B8"))
            contentDescription = "Dismiss caller ID"
            setOnClickListener { stopSelf() }
        }

        headerRow.addView(titleBlock)
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        if (note.isNotBlank()) {
            val noteView = TextView(this).apply {
                text = "Last note: $note"
                textSize = 12f
                maxLines = 2
                setTextColor(Color.parseColor("#FBBF24"))
                setPadding(0, (8 * dp).toInt(), 0, 0)
            }
            card.addView(noteView)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * dp).toInt()
            horizontalMargin = 0.04f
        }

        runCatching {
            windowManager?.addView(card, params)
            overlayView = card
        }
    }

    @SuppressLint("MissingPermission")
    private fun showFallbackNotification(
        name: String,
        company: String,
        stage: String,
        phone: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val subtitle = listOf(company, stage, phone).filter { it.isNotBlank() }.joinToString(" • ")
        val notif = NotificationCompat.Builder(this, OneBookApplication.CHANNEL_POST_CALL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming CRM Call: $name")
            .setContentText(subtitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(4099, notif)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            overlayView = null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismissRunnable)
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_COMPANY = "extra_company"
        const val EXTRA_STAGE = "extra_stage"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_PHONE = "extra_phone"
    }
}
