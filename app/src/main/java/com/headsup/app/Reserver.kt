package com.headsup.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** 정해진 시각에 세션을 시작하는 예약 기능 */
object Reserver {

    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 100, Intent(ctx, ReserveReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    /** 안드로이드 12 이상에선 '알람 및 리마인더' 권한이 있어야 정확한 시각에 시작할 수 있음 */
    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    fun schedule(ctx: Context, atMillis: Long): Boolean {
        return try {
            am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(ctx))
            Prefs.setReservedAt(ctx, atMillis)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(ctx: Context) {
        am(ctx).cancel(pi(ctx))
        Prefs.setReservedAt(ctx, 0)
    }
}

class ReserveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.setReservedAt(context, 0)
        LookUpService.start(context)
    }
}
