package com.meetingnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.data.local.NotificationLogEntity

/**
 * ToDo 1件の通知予約(`TodoNotificationScheduler`)から起動される一回限りの Worker。
 * 発火時点でまだ未完了なら通知を出し、`notifyAt` を null に戻す(予約済み表示を消すため)。
 * ToDo が削除済み・すでに完了済みなら何もしない(静かに成功扱い)。
 */
class TodoNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val todoId = inputData.getLong(KEY_TODO_ID, -1L)
        if (todoId < 0) return Result.success()

        val app = applicationContext as? MeetingNotesApp ?: return Result.success()
        val repository = app.repository
        val todo = repository.getTodo(todoId) ?: return Result.success()
        if (todo.isDone) return Result.success()

        if (NotificationHelper.hasPermission(applicationContext)) {
            val client = repository.getClient(todo.clientId)
            val clientName = client?.name ?: "(不明なクライアント)"
            val title = "ToDoの通知: $clientName"
            NotificationHelper.notify(
                context = applicationContext,
                notificationId = ("todo-notify-$todoId").hashCode(),
                title = title,
                body = todo.task
            )
            // アプリ内の「通知」画面(通知履歴)にも残す。
            repository.logNotification(
                NotificationLogEntity(
                    meetingId = todo.meetingId ?: 0L,
                    clientId = todo.clientId,
                    title = title,
                    body = todo.task,
                    scheduledFor = "todo-notify-$todoId-${todo.notifyAt}",
                    firedAt = System.currentTimeMillis()
                )
            )
        }

        // 発火後は予約済み表示(🔔)を消すため notifyAt をクリアする。
        repository.setTodoNotifyAt(todoId, null)
        return Result.success()
    }

    companion object {
        const val KEY_TODO_ID = "todoId"
    }
}
