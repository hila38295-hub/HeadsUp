package com.headsup.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 다른 앱 위에 화면 상단 배너를 띄운다.
 * 시스템 알림이 아니라 오버레이 창이라 소리·진동이 전혀 없고, 색상을 자유롭게 바꿀 수 있다.
 * 배너를 탭하면 바로 사라진다.
 */
class BannerOverlay(context: Context) {

    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private var view: View? = null

    fun show(s: Prefs.Settings): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        hide()

        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val tv = TextView(ctx).apply {
            text = s.displayMessage
            setTextColor(s.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(260)
            maxWidth = dp(560)
            setPadding(dp(28), dp(18), dp(28), dp(18))
            background = GradientDrawable().apply {
                setColor(s.bgColor)
                cornerRadius = 18f * d
                setStroke(dp(1), 0x26000000)
            }
            elevation = 10f * d
            setOnClickListener { hide() }
        }

        val container = FrameLayout(ctx).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            clipToPadding = false
            addView(
                tv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(20)
        }

        try {
            wm.addView(container, lp)
        } catch (e: Exception) {
            return false
        }
        view = container

        tv.alpha = 0f
        tv.translationY = -40f * d
        tv.animate().alpha(1f).translationY(0f).setDuration(220).start()

        handler.postDelayed(hideRunnable, s.showSec.coerceIn(1, 30) * 1000L)
        return true
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        view?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        view = null
    }
}
