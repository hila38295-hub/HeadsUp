package com.headsup.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/** 더보기 시트와 첫 실행 안내 */
object MoreAndGuide {

    const val CONTACT_TEXT = "앱이 이상하면 정연이한테 말해 주세요 🙌"

    private data class Page(val emoji: String, val title: String, val body: String)

    private val pages = listOf(
        Page(
            "👀", "고개 들어!",
            "수업 중에 태블릿만 보고 있으면 의심받기 쉬워요.\n" +
                "정해 둔 간격 안에서 랜덤한 순간에 배너로\n교수님을 볼 타이밍을 알려 드려요."
        ),
        Page(
            "⏱️", "원하는 대로 설정",
            "알림 간격(1~10분), 수업 시간, 시작 예약,\n배너 문구와 색까지 바꿀 수 있어요.\n" +
                "소리·진동은 없고, 배너를 탭하면 바로 닫혀요."
        ),
        Page(
            "🔓", "권한 하나만 켜 주세요",
            "다른 앱을 쓰는 중에도 배너가 뜨려면\n'다른 앱 위에 표시'를 허용해야 해요.\n" +
                "잠겨 있으면 앱 정보 → ⋮ → 제한된 설정 허용.\n배터리는 '제한 없음'을 추천해요."
        ),
        Page(
            "✅", "바로 확인해 보기",
            "'10초 뒤 띄워보기'를 누르고 다른 앱으로 가 보세요.\n" +
                "테마 변경과 문의는 오른쪽 위 ⋯ 더보기에 있어요."
        ),
    )

    fun showGuide(activity: AppCompatActivity) {
        val v = LayoutInflater.from(activity).inflate(R.layout.dialog_onboarding, null)
        val emoji = v.findViewById<TextView>(R.id.obEmoji)
        val title = v.findViewById<TextView>(R.id.obTitle)
        val body = v.findViewById<TextView>(R.id.obBody)
        val dots = v.findViewById<TextView>(R.id.obDots)
        val skip = v.findViewById<MaterialButton>(R.id.obSkip)
        val next = v.findViewById<MaterialButton>(R.id.obNext)

        val dialog = AlertDialog.Builder(activity).setView(v).setCancelable(false).create()
        var index = 0

        fun render() {
            val p = pages[index]
            emoji.text = p.emoji
            title.text = p.title
            body.text = p.body
            dots.text = pages.indices.joinToString(" ") { if (it == index) "●" else "○" }
            next.text = if (index == pages.lastIndex) "시작하기" else "다음"
            skip.visibility = if (index == pages.lastIndex) android.view.View.INVISIBLE else android.view.View.VISIBLE
        }

        fun finish() {
            Prefs.setOnboarded(activity)
            dialog.dismiss()
        }

        skip.setOnClickListener { finish() }
        next.setOnClickListener {
            if (index == pages.lastIndex) finish() else { index++; render() }
        }
        render()
        dialog.show()
    }

    fun showMore(activity: AppCompatActivity) {
        val sheet = BottomSheetDialog(activity)
        val v = LayoutInflater.from(activity).inflate(R.layout.sheet_more, null)
        sheet.setContentView(v)

        // 테마
        val group = v.findViewById<MaterialButtonToggleGroup>(R.id.themeGroup)
        group.check(
            when (Prefs.themeMode(activity)) {
                App.THEME_DARK -> R.id.themeDark
                App.THEME_SYSTEM -> R.id.themeSystem
                else -> R.id.themeLight
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.themeDark -> App.THEME_DARK
                R.id.themeSystem -> App.THEME_SYSTEM
                else -> App.THEME_LIGHT
            }
            if (mode == Prefs.themeMode(activity)) return@addOnButtonCheckedListener
            Prefs.setThemeMode(activity, mode)
            sheet.dismiss()
            App.applyTheme(mode) // 화면이 새 테마로 다시 그려진다
        }

        // 문의
        v.findViewById<TextView>(R.id.tvContact).text = CONTACT_TEXT
        v.findViewById<MaterialButton>(R.id.btnCopyInfo).setOnClickListener {
            val cm = activity.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("기기 정보", deviceInfo(activity)))
            Toast.makeText(activity, "복사했어요. 카톡에 붙여넣어 보내 주세요", Toast.LENGTH_SHORT).show()
        }

        // 사용법
        v.findViewById<MaterialButton>(R.id.btnGuide).setOnClickListener {
            sheet.dismiss()
            showGuide(activity)
        }

        v.findViewById<TextView>(R.id.tvVersion).text = "고개 들어! v${versionName(activity)}"
        sheet.show()
    }

    private fun versionName(activity: AppCompatActivity): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun deviceInfo(activity: AppCompatActivity): String {
        val s = Prefs.load(activity)
        return buildString {
            appendLine("[고개 들어! 버그 문의]")
            appendLine("앱 버전: ${versionName(activity)}")
            appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("안드로이드: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("다른 앱 위에 표시: ${if (android.provider.Settings.canDrawOverlays(activity)) "허용" else "꺼짐"}")
            appendLine("간격: ${s.minSec}~${s.maxSec}초, 표시 ${s.showSec}초")
            appendLine("실행 중: ${LookUpService.running}, 표시 ${LookUpService.alertCount}회, 실패 ${LookUpService.missedCount}회")
            LookUpService.lastMissReason?.let { appendLine("마지막 실패 이유: $it") }
            append("증상: ")
        }
    }
}
