package com.headsup.app

import android.content.Context
import android.graphics.Color

/** 사용자 설정 저장소 (SharedPreferences) */
object Prefs {
    const val DEFAULT_MESSAGE = "교수님을 바라보세요!"

    const val RANGE_MIN_SEC = 60    // 1분
    const val RANGE_MAX_SEC = 600   // 10분

    data class Settings(
        var minSec: Int = 120,
        var maxSec: Int = 240,
        var useDuration: Boolean = true,
        var durationMin: Int = 75,
        var message: String = DEFAULT_MESSAGE,
        var bgColor: Int = Color.WHITE,
        var textColor: Int = Color.BLACK,
        var showSec: Int = 5,
        var reserveOn: Boolean = false,
        var reserveHour: Int = 9,
        var reserveMinute: Int = 0,
    ) {
        val displayMessage: String get() = message.ifBlank { DEFAULT_MESSAGE }
    }

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val p = sp(c)
        val d = Settings()
        val min = p.getInt("minSec", d.minSec).coerceIn(RANGE_MIN_SEC, RANGE_MAX_SEC)
        val max = p.getInt("maxSec", d.maxSec).coerceIn(min, RANGE_MAX_SEC)
        return Settings(
            minSec = min,
            maxSec = max,
            useDuration = p.getBoolean("useDuration", d.useDuration),
            durationMin = p.getInt("durationMin", d.durationMin),
            message = p.getString("message", d.message) ?: d.message,
            bgColor = p.getInt("bgColor", d.bgColor),
            textColor = p.getInt("textColor", d.textColor),
            showSec = p.getInt("showSec", d.showSec),
            reserveOn = p.getBoolean("reserveOn", d.reserveOn),
            reserveHour = p.getInt("reserveHour", d.reserveHour),
            reserveMinute = p.getInt("reserveMinute", d.reserveMinute),
        )
    }

    fun save(c: Context, s: Settings) {
        sp(c).edit()
            .putInt("minSec", s.minSec)
            .putInt("maxSec", s.maxSec)
            .putBoolean("useDuration", s.useDuration)
            .putInt("durationMin", s.durationMin)
            .putString("message", s.message)
            .putInt("bgColor", s.bgColor)
            .putInt("textColor", s.textColor)
            .putInt("showSec", s.showSec)
            .putBoolean("reserveOn", s.reserveOn)
            .putInt("reserveHour", s.reserveHour)
            .putInt("reserveMinute", s.reserveMinute)
            .apply()
    }

    /** 예약된 시작 시각 (0 = 예약 없음) */
    fun reservedAt(c: Context): Long = sp(c).getLong("reservedAt", 0L)
    fun setReservedAt(c: Context, v: Long) = sp(c).edit().putLong("reservedAt", v).apply()

    fun askedNotif(c: Context): Boolean = sp(c).getBoolean("askedNotif", false)
    fun setAskedNotif(c: Context) = sp(c).edit().putBoolean("askedNotif", true).apply()
}
