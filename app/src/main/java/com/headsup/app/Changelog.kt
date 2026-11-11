package com.headsup.app

import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 업데이트 내역과 '업데이트됐어요' 팝업.
 * 새 버전을 낼 때는 ENTRIES 맨 위에 항목을 추가하고 build.gradle.kts의 versionCode를 올리면 된다.
 */
object Changelog {

    data class Entry(val version: String, val date: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry(
            "1.4", "2026.11.11",
            listOf(
                "업데이트하면 '업데이트됐어요' 팝업이 한 번 떠요",
                "더보기 → 업데이트 내역에서 버전별로 바뀐 점을 볼 수 있어요",
            )
        ),
        Entry(
            "1.3", "2026.11.09",
            listOf(
                "배너 색 선택 창: 48가지 기본 색 + 색상·채도·밝기로 직접 고르기",
                "글자와 배경이 비슷하면 잘 안 보인다고 알려 줘요",
                "처음 실행할 때 사용법 안내",
                "다크 모드 (라이트 / 다크 / 시스템)",
                "더보기: 테마, 문의, 기기 정보 복사, 사용법 다시 보기",
            )
        ),
        Entry(
            "1.1", "2026.11.09",
            listOf(
                "'10초 뒤 띄워보기'로 다른 앱 위에서 바로 테스트",
                "배너가 실제로 보였을 때만 횟수를 세고, 못 띄운 이유를 알려 줘요",
                "다른 앱 위에서 배너가 투명하게 남던 문제 수정",
                "기본 표시 시간 5초",
            )
        ),
        Entry(
            "1.0", "2026.11.09",
            listOf("첫 버전: 랜덤 간격 배너, 수업 시간 자동 종료, 일시정지, 시작 예약, 문구·색 설정")
        ),
    )

    /** 앱을 열 때 호출: 처음 설치면 조용히 기록만, 업데이트 후 첫 실행이면 팝업 */
    fun checkUpdated(activity: AppCompatActivity) {
        val current = BuildConfig.VERSION_CODE
        val last = Prefs.lastSeenVersion(activity)
        Prefs.setLastSeenVersion(activity, current)
        // 사용법 안내를 이미 본 사람 = 예전 버전을 쓰던 사람
        if (last < current && Prefs.onboarded(activity)) showUpdatedDialog(activity)
    }

    private fun showUpdatedDialog(activity: AppCompatActivity) {
        AlertDialog.Builder(activity)
            .setTitle("🎉 업데이트 완료!")
            .setMessage(
                "고개 들어!가 v${BuildConfig.VERSION_NAME}(으)로 업데이트됐어요.\n\n" +
                    "무엇이 바뀌었는지는 오른쪽 위 ⋯ 더보기 → 업데이트 내역에서 볼 수 있어요."
            )
            .setPositiveButton("확인", null)
            .setNeutralButton("업데이트 내역 보기") { _, _ -> showHistory(activity) }
            .show()
    }

    fun showHistory(activity: AppCompatActivity) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val ink = activity.getColor(R.color.ink)
        val sub = activity.getColor(R.color.ink_sub)
        val accent = activity.getColor(R.color.accent)

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }
        list.addView(TextView(activity).apply {
            text = "업데이트 내역"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ink)
        })

        ENTRIES.forEachIndexed { i, e ->
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(if (i == 0) 16 else 22), 0, dp(6))
            }
            header.addView(TextView(activity).apply {
                text = "v${e.version}"
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (i == 0) accent else ink)
            })
            header.addView(TextView(activity).apply {
                text = if (e.version == BuildConfig.VERSION_NAME) "  ${e.date} · 지금 버전" else "  ${e.date}"
                textSize = 13f
                setTextColor(sub)
            })
            list.addView(header)
            for (item in e.items) {
                list.addView(TextView(activity).apply {
                    text = "•  $item"
                    textSize = 14.5f
                    setTextColor(sub)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(dp(4), dp(3), 0, dp(3))
                })
            }
        }

        val scroll = ScrollView(activity).apply { addView(list) }
        val sheet = BottomSheetDialog(activity)
        sheet.setContentView(scroll)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
    }
}
