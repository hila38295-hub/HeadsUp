package com.headsup.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.util.Locale

/** 배너 배경색·글자색을 고르는 시트: 48색 팔레트 + 색상/채도/밝기 슬라이더 */
object ColorPicker {

    val PALETTE = intArrayOf(
        // 무채색
        0xFFFFFFFF, 0xFFF5F5F5, 0xFFE0E0E0, 0xFFBDBDBD, 0xFF9E9E9E, 0xFF616161, 0xFF303030, 0xFF000000,
        // 파스텔
        0xFFFFCDD2, 0xFFF8BBD0, 0xFFE1BEE7, 0xFFD1C4E9, 0xFFC5CAE9, 0xFFBBDEFB, 0xFFB2EBF2, 0xFFB2DFDB,
        0xFFC8E6C9, 0xFFDCEDC8, 0xFFF0F4C3, 0xFFFFF9C4, 0xFFFFECB3, 0xFFFFE0B2, 0xFFFFCCBC, 0xFFD7CCC8,
        // 선명한 색
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
        0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722,
        // 진한 색
        0xFFB71C1C, 0xFF880E4F, 0xFF4A148C, 0xFF1A237E, 0xFF0D47A1, 0xFF004D40, 0xFF1B5E20, 0xFF3E2723,
    ).map { it.toInt() }.toIntArray()

    fun hex(c: Int) = String.format(Locale.US, "#%06X", 0xFFFFFF and c)

    /** 대비가 3:1보다 낮으면 읽기 어렵다 (WCAG 큰 글씨 기준) */
    fun lowContrast(text: Int, bg: Int): Boolean =
        ColorUtils.calculateContrast(text or 0xFF000000.toInt(), bg or 0xFF000000.toInt()) < 3.0

    /**
     * @param editBg true면 배경색, false면 글자색을 고른다
     * @param onChange 색이 바뀔 때마다 호출 (s는 이미 갱신된 상태)
     */
    fun show(
        activity: AppCompatActivity,
        s: Prefs.Settings,
        editBg: Boolean,
        onChange: () -> Unit
    ) {
        val sheet = BottomSheetDialog(activity)
        val v = LayoutInflater.from(activity).inflate(R.layout.sheet_color, null)
        sheet.setContentView(v)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        val d = activity.resources.displayMetrics.density
        val title = v.findViewById<TextView>(R.id.cpTitle)
        val preview = v.findViewById<TextView>(R.id.cpPreview)
        val warn = v.findViewById<TextView>(R.id.cpWarn)
        val grid = v.findViewById<GridLayout>(R.id.cpGrid)
        val hexView = v.findViewById<TextView>(R.id.cpHex)
        val hue = v.findViewById<Slider>(R.id.cpHue)
        val sat = v.findViewById<Slider>(R.id.cpSat)
        val value = v.findViewById<Slider>(R.id.cpVal)

        title.text = if (editBg) "배경색" else "글자색"

        fun current() = if (editBg) s.bgColor else s.textColor

        val accent = activity.getColor(R.color.accent)
        val plain = activity.getColor(R.color.swatch_stroke)
        val swatches = ArrayList<Pair<View, Int>>()

        fun paintSwatch(view: View, c: Int) {
            val selected = (c or 0xFF000000.toInt()) == (current() or 0xFF000000.toInt())
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                if (selected) setStroke((4 * d).toInt(), accent) else setStroke((1 * d).toInt(), plain)
            }
        }

        var updating = false

        fun refresh(updateSliders: Boolean) {
            val c = current()
            preview.text = s.displayMessage
            preview.setTextColor(s.textColor)
            preview.background = GradientDrawable().apply {
                setColor(s.bgColor)
                cornerRadius = 16f * d
                setStroke((1 * d).toInt(), 0x26000000)
            }
            warn.visibility = if (lowContrast(s.textColor, s.bgColor)) View.VISIBLE else View.GONE
            hexView.text = hex(c)
            for ((view, col) in swatches) paintSwatch(view, col)
            if (updateSliders) {
                updating = true
                val hsv = FloatArray(3)
                Color.colorToHSV(c, hsv)
                hue.value = hsv[0].coerceIn(0f, 360f)
                sat.value = (hsv[1] * 100f).coerceIn(0f, 100f)
                value.value = (hsv[2] * 100f).coerceIn(0f, 100f)
                updating = false
            }
        }

        fun set(c: Int, updateSliders: Boolean) {
            if (editBg) s.bgColor = c else s.textColor = c
            refresh(updateSliders)
            onChange()
        }

        // 팔레트
        val size = (34 * d).toInt()
        val m = (3 * d).toInt()
        for (c in PALETTE) {
            val sw = View(activity)
            sw.contentDescription = hex(c)
            sw.setOnClickListener { set(c, true) }
            val lp = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(m, m, m, m)
            }
            grid.addView(sw, lp)
            swatches.add(sw to c)
        }

        // 슬라이더 (색상·채도·밝기)
        val sliderListener = Slider.OnChangeListener { _, _, fromUser ->
            if (updating || !fromUser) return@OnChangeListener
            val c = Color.HSVToColor(floatArrayOf(hue.value, sat.value / 100f, value.value / 100f))
            set(c, false)
        }
        hue.addOnChangeListener(sliderListener)
        sat.addOnChangeListener(sliderListener)
        value.addOnChangeListener(sliderListener)
        hue.setLabelFormatter { "${it.toInt()}°" }
        sat.setLabelFormatter { "${it.toInt()}%" }
        value.setLabelFormatter { "${it.toInt()}%" }

        v.findViewById<MaterialButton>(R.id.cpDone).setOnClickListener { sheet.dismiss() }

        refresh(true)
        sheet.show()
    }
}
