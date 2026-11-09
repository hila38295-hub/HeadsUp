package com.headsup.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 수업 세션을 실행하는 포그라운드 서비스.
 * - 설정한 범위 안에서 초 단위 랜덤 간격으로 배너를 띄움
 * - 수업 시간이 지나면 자동 종료 (끄면 취소할 때까지 계속)
 * - 일시정지 / 재개 / 종료
 */
class LookUpService : Service() {

    companion object {
        const val ACTION_START = "com.headsup.app.START"
        const val ACTION_PAUSE = "com.headsup.app.PAUSE"
        const val ACTION_RESUME = "com.headsup.app.RESUME"
        const val ACTION_TOGGLE = "com.headsup.app.TOGGLE"
        const val ACTION_STOP = "com.headsup.app.STOP"
        const val ACTION_TEST = "com.headsup.app.TEST"
        const val TEST_DELAY_SEC = 10

        private const val CHANNEL_ID = "session_silent"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false
            private set

        @Volatile
        var paused = false
            private set

        @Volatile
        var endAt = 0L
            private set

        @Volatile
        var alertCount = 0
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, LookUpService::class.java).setAction(ACTION_START)
            )
        }

        /** 실제 수업과 같은 경로(포그라운드 서비스)로 10초 뒤 배너를 한 번 띄운다 */
        fun test(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, LookUpService::class.java).setAction(ACTION_TEST)
            )
        }

        fun send(ctx: Context, action: String) {
            if (!running) return
            ctx.startService(Intent(ctx, LookUpService::class.java).setAction(action))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: BannerOverlay
    private val alertRunnable = Runnable { onAlert() }
    private val endRunnable = Runnable { stopSession() }
    private var testing = false
    private val testRunnable = Runnable { onTestAlert() }
    private val testEndRunnable = Runnable { finishTest() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = BannerOverlay(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginSession()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_TOGGLE -> if (paused) resume() else pause()
            ACTION_STOP -> stopSession()
            ACTION_TEST -> runTest()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------- 세션 제어 ----------

    private fun beginSession() {
        val s = Prefs.load(this)
        Prefs.setReservedAt(this, 0)
        handler.removeCallbacksAndMessages(null)
        testing = false

        running = true
        paused = false
        alertCount = 0
        endAt = if (s.useDuration) System.currentTimeMillis() + s.durationMin * 60_000L else 0L

        startForegroundCompat()
        if (endAt > 0) handler.postDelayed(endRunnable, endAt - System.currentTimeMillis())
        scheduleNext()
    }

    // ---------- 10초 테스트 ----------

    private fun runTest() {
        testing = true
        startForegroundCompat()
        handler.removeCallbacks(testRunnable)
        handler.removeCallbacks(testEndRunnable)
        handler.postDelayed(testRunnable, TEST_DELAY_SEC * 1000L)
    }

    private fun onTestAlert() {
        val s = Prefs.load(this)
        overlay.show(s)
        handler.postDelayed(testEndRunnable, s.showSec.coerceIn(1, 30) * 1000L + 500)
    }

    private fun finishTest() {
        testing = false
        if (running) {
            updateNotification()
        } else {
            overlay.hide()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun scheduleNext() {
        handler.removeCallbacks(alertRunnable)
        val s = Prefs.load(this) // 수업 중 바꾼 설정도 다음 알림부터 반영
        val delaySec = Random.nextInt(s.minSec, s.maxSec + 1)
        handler.postDelayed(alertRunnable, delaySec * 1000L)
    }

    private fun onAlert() {
        if (!running || paused) return
        if (endAt > 0 && System.currentTimeMillis() >= endAt) {
            stopSession(); return
        }
        overlay.show(Prefs.load(this))
        alertCount++
        scheduleNext()
    }

    private fun pause() {
        if (!running || paused) return
        paused = true
        handler.removeCallbacks(alertRunnable)
        overlay.hide()
        updateNotification()
    }

    private fun resume() {
        if (!running || !paused) return
        paused = false
        scheduleNext()
        updateNotification()
    }

    private fun stopSession() {
        testing = false
        running = false
        paused = false
        endAt = 0L
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        running = false
        paused = false
        super.onDestroy()
    }

    // ---------- 상태 알림 (무음, 최소 중요도) ----------

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "실행 상태", NotificationManager.IMPORTANCE_MIN).apply {
            description = "앱이 실행 중일 때만 표시돼요. 소리·진동 없음."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        if (!running && !testing) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun servicePi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, LookUpService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (testing && !running) {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("테스트 중")
                .setContentText("${TEST_DELAY_SEC}초 뒤 배너를 한 번 띄워요")
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }
        val text = if (endAt > 0) {
            val t = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(endAt))
            "$t 에 자동 종료"
        } else "종료할 때까지 계속"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (paused) "일시정지됨" else "실행 중")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, if (paused) "재개" else "일시정지", servicePi(ACTION_TOGGLE, 1))
            .addAction(0, "종료", servicePi(ACTION_STOP, 2))
            .build()
    }
}
