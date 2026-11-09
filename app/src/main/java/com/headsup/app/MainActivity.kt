package com.headsup.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var s: Prefs.Settings
    private lateinit var overlay: BannerOverlay
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            ui.postDelayed(this, 1000)
        }
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var btnPrimary: MaterialButton
    private lateinit var btnSecondary: MaterialButton
    private lateinit var tvRange: TextView
    private lateinit var rangeSlider: RangeSlider
    private lateinit var swDuration: MaterialSwitch
    private lateinit var tvDuration: TextView
    private lateinit var sliderDuration: Slider
    private lateinit var swReserve: MaterialSwitch
    private lateinit var btnReserveTime: MaterialButton
    private lateinit var etMessage: EditText
    private lateinit var pickBgHex: TextView
    private lateinit var pickBgDot: View
    private lateinit var pickTextHex: TextView
    private lateinit var pickTextDot: View
    private lateinit var tvShowSec: TextView
    private lateinit var sliderShow: Slider
    private lateinit var tvPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        overlay = BannerOverlay(this)
        s = Prefs.load(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvStatusSub = findViewById(R.id.tvStatusSub)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnSecondary = findViewById(R.id.btnSecondary)
        tvRange = findViewById(R.id.tvRange)
        rangeSlider = findViewById(R.id.rangeSlider)
        swDuration = findViewById(R.id.swDuration)
        tvDuration = findViewById(R.id.tvDuration)
        sliderDuration = findViewById(R.id.sliderDuration)
        swReserve = findViewById(R.id.swReserve)
        btnReserveTime = findViewById(R.id.btnReserveTime)
        etMessage = findViewById(R.id.etMessage)
        pickBgHex = findViewById(R.id.pickBgHex)
        pickBgDot = findViewById(R.id.pickBgDot)
        pickTextHex = findViewById(R.id.pickTextHex)
        pickTextDot = findViewById(R.id.pickTextDot)
        tvShowSec = findViewById(R.id.tvShowSec)
        sliderShow = findViewById(R.id.sliderShow)
        tvPreview = findViewById(R.id.tvPreview)

        setupInterval()
        setupDuration()
        setupReserve()
        setupBanner()

        btnPrimary.setOnClickListener { onPrimary() }
        btnSecondary.setOnClickListener { onSecondary() }
        findViewById<View>(R.id.btnMore).setOnClickListener { MoreAndGuide.showMore(this) }

        if (savedInstanceState == null && !Prefs.onboarded(this)) {
            MoreAndGuide.showGuide(this)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    private fun save() = Prefs.save(this, s)

    // ---------- 알림 간격 ----------

    private fun setupInterval() {
        rangeSlider.setValues(s.minSec.toFloat(), s.maxSec.toFloat())
        rangeSlider.setLabelFormatter { fmtSec(it.toInt()) }
        rangeSlider.addOnChangeListener { slider, _, _ ->
            val v = slider.values
            s.minSec = v[0].toInt()
            s.maxSec = v[1].toInt()
            updateRangeLabel()
            save()
        }
        updateRangeLabel()
    }

    private fun updateRangeLabel() {
        tvRange.text = if (s.minSec == s.maxSec) {
            "${fmtSec(s.minSec)}마다 (고정)"
        } else {
            "${fmtSec(s.minSec)} ~ ${fmtSec(s.maxSec)} 사이 랜덤 (초 단위)"
        }
    }

    // ---------- 수업 시간 ----------

    private fun setupDuration() {
        swDuration.isChecked = s.useDuration
        sliderDuration.value = snap(s.durationMin.toFloat(), 10f, 240f, 5f)
        sliderDuration.setLabelFormatter { "${it.toInt()}분" }
        swDuration.setOnCheckedChangeListener { _, checked ->
            s.useDuration = checked
            save()
            updateDurationLabel()
        }
        sliderDuration.addOnChangeListener { _, value, _ ->
            s.durationMin = value.toInt()
            save()
            updateDurationLabel()
        }
        updateDurationLabel()
    }

    private fun updateDurationLabel() {
        sliderDuration.visibility = if (s.useDuration) View.VISIBLE else View.GONE
        tvDuration.text = if (s.useDuration) {
            "시작하고 ${fmtMin(s.durationMin)} 뒤 자동으로 꺼져요"
        } else {
            "종료를 누를 때까지 계속 알려드려요"
        }
    }

    // ---------- 예약 ----------

    private fun setupReserve() {
        swReserve.isChecked = s.reserveOn
        swReserve.setOnCheckedChangeListener { _, checked ->
            s.reserveOn = checked
            save()
            updateReserveLabel()
            refreshStatus()
        }
        btnReserveTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                s.reserveHour = h
                s.reserveMinute = m
                save()
                updateReserveLabel()
                refreshStatus()
            }, s.reserveHour, s.reserveMinute, false).show()
        }
        updateReserveLabel()
    }

    private fun updateReserveLabel() {
        btnReserveTime.visibility = if (s.reserveOn) View.VISIBLE else View.GONE
        btnReserveTime.text = fmtTime(nextReserveMillis())
    }

    private fun nextReserveMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.reserveHour)
            set(Calendar.MINUTE, s.reserveMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // ---------- 배너 ----------

    private fun setupBanner() {
        etMessage.setText(s.message)
        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                s.message = e?.toString() ?: ""
                save()
                updatePreview()
            }
        })

        val onColorChange = { save(); updatePreview() }
        findViewById<View>(R.id.pickBgRow).setOnClickListener {
            ColorPicker.show(this, s, editBg = true, onChange = onColorChange)
        }
        findViewById<View>(R.id.pickTextRow).setOnClickListener {
            ColorPicker.show(this, s, editBg = false, onChange = onColorChange)
        }

        sliderShow.value = snap(s.showSec.toFloat(), 1f, 10f, 1f)
        sliderShow.setLabelFormatter { "${it.toInt()}초" }
        sliderShow.addOnChangeListener { _, value, _ ->
            s.showSec = value.toInt()
            save()
            updateShowLabel()
        }
        updateShowLabel()

        findViewById<MaterialButton>(R.id.btnPreview).setOnClickListener {
            if (ensureOverlayPermission()) overlay.show(s)
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            if (!ensureOverlayPermission()) return@setOnClickListener
            save()
            LookUpService.test(this)
            Toast.makeText(
                this,
                "${LookUpService.TEST_DELAY_SEC}초 안에 다른 앱으로 이동해 보세요",
                Toast.LENGTH_LONG
            ).show()
        }
        updatePreview()
    }

    private fun updateShowLabel() {
        tvShowSec.text = "표시 시간 · ${s.showSec}초"
    }

    private fun updatePreview() {
        val d = resources.displayMetrics.density
        tvPreview.text = s.displayMessage
        tvPreview.setTextColor(s.textColor)
        tvPreview.background = GradientDrawable().apply {
            setColor(s.bgColor)
            cornerRadius = 16f * d
            setStroke((1 * d).toInt(), 0x26000000)
        }
        val stroke = getColor(R.color.swatch_stroke)
        fun dot(view: View, c: Int) {
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                setStroke((1 * d).toInt(), stroke)
            }
        }
        dot(pickBgDot, s.bgColor)
        dot(pickTextDot, s.textColor)
        pickBgHex.text = ColorPicker.hex(s.bgColor)
        pickTextHex.text = ColorPicker.hex(s.textColor)
    }

    // ---------- 시작 / 일시정지 / 종료 / 예약 ----------

    private fun onPrimary() {
        val reservedAt = Prefs.reservedAt(this)
        when {
            LookUpService.running -> LookUpService.send(
                this,
                if (LookUpService.paused) LookUpService.ACTION_RESUME else LookUpService.ACTION_PAUSE
            )
            reservedAt > 0 -> { // "지금 바로 시작"
                if (!ensureOverlayPermission()) return
                Reserver.cancel(this)
                startNow()
            }
            s.reserveOn -> reserve()
            else -> startNow()
        }
        ui.postDelayed({ refreshStatus() }, 250)
    }

    private fun onSecondary() {
        when {
            LookUpService.running -> LookUpService.send(this, LookUpService.ACTION_STOP)
            Prefs.reservedAt(this) > 0 -> {
                Reserver.cancel(this)
                Toast.makeText(this, "예약을 취소했어요", Toast.LENGTH_SHORT).show()
            }
        }
        ui.postDelayed({ refreshStatus() }, 250)
    }

    private fun startNow() {
        if (!ensureOverlayPermission()) return
        askNotificationOnce()
        save()
        LookUpService.start(this)
    }

    private fun reserve() {
        if (!ensureOverlayPermission()) return
        if (!Reserver.canExact(this)) {
            AlertDialog.Builder(this)
                .setTitle("예약 권한이 필요해요")
                .setMessage("정해진 시각에 정확히 시작하려면 '알람 및 리마인더' 권한을 켜 주세요.")
                .setPositiveButton("설정 열기") { _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }
                .setNegativeButton("취소", null)
                .show()
            return
        }
        askNotificationOnce()
        save()
        val at = nextReserveMillis()
        if (Reserver.schedule(this, at)) {
            Toast.makeText(this, "${fmtDateTime(at)}에 시작하도록 예약했어요", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "예약에 실패했어요. 권한을 확인해 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        AlertDialog.Builder(this)
            .setTitle("'다른 앱 위에 표시' 권한이 필요해요")
            .setMessage(
                "다른 앱을 보고 있을 때도 배너가 뜨려면 이 권한이 있어야 해요.\n\n" +
                    "설정에서 '고개 들어!'를 찾아 허용으로 바꾼 뒤 돌아와 주세요."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .setNegativeButton("취소", null)
            .show()
        return false
    }

    /** 상태 알림의 일시정지/종료 버튼용. 거절해도 앱은 정상 동작한다. */
    private fun askNotificationOnce() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.askedNotif(this)) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Prefs.setAskedNotif(this)
    }

    // ---------- 상태 표시 ----------

    private var shownTestResult: String? = null

    private fun refreshStatus() {
        val test = LookUpService.lastTestResult
        if (test != null && test != shownTestResult) {
            shownTestResult = test
            LookUpService.lastTestResult = null
            AlertDialog.Builder(this).setTitle("10초 테스트 결과").setMessage(test)
                .setPositiveButton("확인", null).show()
        }

        val now = System.currentTimeMillis()
        var reservedAt = Prefs.reservedAt(this)
        if (reservedAt in 1 until now - 60_000) { // 지나간 예약 정리
            Prefs.setReservedAt(this, 0)
            reservedAt = 0
        }

        when {
            LookUpService.running -> {
                val paused = LookUpService.paused
                tvStatus.text = if (paused) "일시정지됨" else "실행 중"
                val endAt = LookUpService.endAt
                val line1 = if (endAt > 0) {
                    val left = ((endAt - now).coerceAtLeast(0)) / 1000
                    "남은 수업 시간 ${fmtClock(left)} · ${fmtTime(endAt)} 종료"
                } else "종료를 누를 때까지 계속 알려드려요"
                var line2 = "지금까지 ${LookUpService.alertCount}번 표시됨"
                if (LookUpService.missedCount > 0) {
                    line2 += "\n${LookUpService.missedCount}번은 표시되지 못함: ${LookUpService.lastMissReason ?: "알 수 없음"}"
                }
                tvStatusSub.text = "$line1\n$line2"
                btnPrimary.text = if (paused) "재개" else "일시정지"
                btnSecondary.text = "종료"
                btnSecondary.visibility = View.VISIBLE
            }
            reservedAt > 0 -> {
                tvStatus.text = "예약됨"
                tvStatusSub.text = "${fmtDateTime(reservedAt)}에 자동으로 시작해요"
                btnPrimary.text = "지금 바로 시작"
                btnSecondary.text = "예약 취소"
                btnSecondary.visibility = View.VISIBLE
            }
            else -> {
                tvStatus.text = "대기 중"
                tvStatusSub.text = if (s.reserveOn) {
                    "${fmtDateTime(nextReserveMillis())}에 시작하도록 예약할 수 있어요"
                } else "시작하면 ${fmtSec(s.minSec)}~${fmtSec(s.maxSec)} 뒤 첫 배너가 떠요"
                btnPrimary.text = if (s.reserveOn) "예약하기" else "시작"
                btnSecondary.visibility = View.GONE
            }
        }
    }

    // ---------- 포맷 ----------

    private fun snap(v: Float, min: Float, max: Float, step: Float): Float {
        val c = v.coerceIn(min, max)
        return min + Math.round((c - min) / step) * step
    }

    private fun fmtSec(sec: Int): String {
        val m = sec / 60
        val r = sec % 60
        return if (r == 0) "${m}분" else "${m}분 ${r}초"
    }

    private fun fmtMin(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "${m}분"
            m == 0 -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }

    private fun fmtClock(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (h > 0) String.format(Locale.KOREA, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.KOREA, "%d:%02d", m, sec)
    }

    private fun fmtTime(millis: Long): String =
        SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(millis))

    private fun fmtDateTime(millis: Long): String =
        SimpleDateFormat("M/d(E) a h:mm", Locale.KOREA).format(Date(millis))
}
