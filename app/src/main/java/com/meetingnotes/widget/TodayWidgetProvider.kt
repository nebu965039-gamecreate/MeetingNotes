package com.meetingnotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.meetingnotes.MainActivity
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.R
import com.meetingnotes.data.local.OpenTodo
import com.meetingnotes.data.local.ScheduleWithClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ホーム画面ウィジェット: 「本日の予定」+「今日までのToDo」を表示する(2026-09-20 新設)。
 * **全ユーザー無料で利用可能**(Android の仕様上、ウィジェットの「追加」自体をPro限定にすることは
 * できない — ホーム画面のウィジェット一覧はOSがマニフェストから機械的に作るため、実行時の課金状態を
 * 見て一覧から除外することができない。2026-09-20 にこの制約を説明した上でユーザーが
 * 「全ユーザー無料で使えるようにする」を選択したため、`ProAccess` によるロックは行わない)。
 *
 * スクロール可能なリスト表示(`RemoteViewsService`+`RemoteViewsFactory`)は実装コストが大きいため、
 * V1では予定・ToDoともに最大3件+「+N件」の固定行数表示にしている。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    private fun refresh(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // onUpdate はメインスレッドから呼ばれるため、DB読み取りは goAsync() で非同期化する。
        val pendingResult = goAsync()
        val app = context.applicationContext as? MeetingNotesApp
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            runCatching {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val fromMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val toMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                val schedules = app.repository.getSchedulesForReminder(fromMillis, toMillis)
                    .sortedBy { it.startAtMillis }
                // 「今日までのToDo」= 期限切れ(過去)も含めた本日以前が期限の未完了ToDo。
                val todos = app.repository.getTodosDueOnOrBefore(today.toString())

                val views = buildViews(context, today, schedules, todos)
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
            pendingResult.finish()
        }
    }

    private fun buildViews(
        context: Context,
        today: LocalDate,
        schedules: List<ScheduleWithClient>,
        todos: List<OpenTodo>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_date, today.format(DATE_FORMAT))

        bindLines(
            views,
            items = schedules.map { s ->
                val timePrefix = if (s.hasTime) {
                    val dt = Instant.ofEpochMilli(s.startAtMillis).atZone(ZoneId.systemDefault())
                    "%02d:%02d ".format(dt.hour, dt.minute)
                } else {
                    ""
                }
                "$timePrefix${s.clientName}・${s.title}"
            },
            lineIds = SCHEDULE_LINE_IDS,
            moreId = R.id.widget_schedule_more,
            emptyId = R.id.widget_schedule_empty
        )

        bindLines(
            views,
            items = todos.map { t -> "${t.clientName}・${t.task}" },
            lineIds = TODO_LINE_IDS,
            moreId = R.id.widget_todo_more,
            emptyId = R.id.widget_todo_empty
        )

        // ウィジェット全体タップでアプリを開く(個々の項目への深い遷移はV1では対象外)。
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return views
    }

    /** 固定行数(lineIds)に items を割り当て、収まらない分は「+N件」、0件なら空状態文言を表示する。 */
    private fun bindLines(
        views: RemoteViews,
        items: List<String>,
        lineIds: List<Int>,
        moreId: Int,
        emptyId: Int
    ) {
        if (items.isEmpty()) {
            lineIds.forEach { views.setViewVisibility(it, View.GONE) }
            views.setViewVisibility(moreId, View.GONE)
            views.setViewVisibility(emptyId, View.VISIBLE)
            return
        }
        views.setViewVisibility(emptyId, View.GONE)
        lineIds.forEachIndexed { index, id ->
            val text = items.getOrNull(index)
            if (text != null) {
                views.setViewVisibility(id, View.VISIBLE)
                views.setTextViewText(id, text)
            } else {
                views.setViewVisibility(id, View.GONE)
            }
        }
        val remaining = items.size - lineIds.size
        if (remaining > 0) {
            views.setViewVisibility(moreId, View.VISIBLE)
            views.setTextViewText(moreId, "+${remaining}件")
        } else {
            views.setViewVisibility(moreId, View.GONE)
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日(E)", Locale.JAPAN)
        private val SCHEDULE_LINE_IDS = listOf(R.id.widget_schedule_1, R.id.widget_schedule_2, R.id.widget_schedule_3)
        private val TODO_LINE_IDS = listOf(R.id.widget_todo_1, R.id.widget_todo_2, R.id.widget_todo_3)

        /**
         * データが変わったとき(アプリのフォアグラウンド復帰時など)に明示的にウィジェットを再描画する。
         * 標準の自動更新(`updatePeriodMillis`)は最短でも30分間隔にOS側でクランプされるため、
         * それより速い反映が欲しい場面(アプリを使った直後にホーム画面へ戻る、等)向けの補助。
         * ウィジェットが1つも追加されていなければ何もしない。
         */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodayWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(context, TodayWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
