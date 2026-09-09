package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Insert
    suspend fun insertAll(todos: List<TodoEntity>)

    @Insert
    suspend fun insert(todo: TodoEntity): Long

    @Query("SELECT * FROM todos WHERE meetingId = :meetingId ORDER BY id ASC")
    fun observeByMeeting(meetingId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE clientId = :clientId ORDER BY id ASC")
    fun observeByClient(clientId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET isDone = :isDone WHERE id = :todoId")
    suspend fun setDone(todoId: Long, isDone: Boolean)

    /** ToDo 一件のスヌーズ再表示日時(null 解除)。 */
    @Query("UPDATE todos SET snoozedUntil = :until WHERE id = :todoId")
    suspend fun setSnooze(todoId: Long, until: Long?)

    /** 手動 ToDo の本文・期限の編集。 */
    @Query("UPDATE todos SET task = :task, deadline = :deadline, dueDate = :dueDate WHERE id = :todoId")
    suspend fun updateContent(todoId: Long, task: String, deadline: String, dueDate: String?)

    @Query("DELETE FROM todos WHERE id = :todoId")
    suspend fun deleteById(todoId: Long)

    @Query("SELECT * FROM todos WHERE id = :todoId")
    suspend fun getById(todoId: Long): TodoEntity?

    /** この商談の「フォローアップメール」ToDo の id(完了・未完了は問わない。なければ null)。F1 ボードの完了/取消の導線用。 */
    @Query("SELECT id FROM todos WHERE meetingId = :meetingId AND isFollowupEmail = 1 ORDER BY id ASC LIMIT 1")
    suspend fun followupEmailTodoId(meetingId: Long): Long?

    /**
     * 期限が解決できている未完了 ToDo(ホーム「期限のあるToDo」・カレンダー・リマインド用)。
     * スヌーズ中(snoozedUntil が [nowMillis] より未来)の ToDo は除外する。
     */
    @Query(
        """
        SELECT t.id AS todoId, t.meetingId AS meetingId, t.task AS task, t.assignee AS assignee,
               t.deadline AS deadline, t.dueDate AS dueDate, t.isDone AS isDone,
               t.clientId AS clientId, c.name AS clientName
        FROM todos t
        INNER JOIN clients c ON c.id = t.clientId
        WHERE t.isDone = 0 AND t.dueDate IS NOT NULL
              AND (t.snoozedUntil IS NULL OR t.snoozedUntil <= :nowMillis)
        ORDER BY t.dueDate ASC
        """
    )
    fun observeOpenTodosWithDueDate(nowMillis: Long): Flow<List<OpenTodo>>

    /** 未完了 ToDo の総数(下部ナビの ToDo バッジ・ホームのダッシュボード)。スヌーズ中は除外。 */
    @Query(
        "SELECT COUNT(*) FROM todos WHERE isDone = 0 " +
            "AND (snoozedUntil IS NULL OR snoozedUntil <= :nowMillis)"
    )
    fun observeOpenTodoTotal(nowMillis: Long): Flow<Int>

    /** クライアントごとの未完了 ToDo 件数(ホームのフォローボードのバッジ)。スヌーズ中は除外。 */
    @Query(
        """
        SELECT t.clientId AS clientId, COUNT(*) AS count
        FROM todos t
        WHERE t.isDone = 0 AND (t.snoozedUntil IS NULL OR t.snoozedUntil <= :nowMillis)
        GROUP BY t.clientId
        """
    )
    fun observeOpenTodoCountByClient(nowMillis: Long): Flow<List<ClientTodoCount>>

    /** 指定日が期限の未完了 ToDo(リマインド Worker 用、Flow でなく suspend)。スヌーズ中は除外。 */
    @Query(
        """
        SELECT t.id AS todoId, t.meetingId AS meetingId, t.task AS task, t.assignee AS assignee,
               t.deadline AS deadline, t.dueDate AS dueDate, t.isDone AS isDone,
               t.clientId AS clientId, c.name AS clientName
        FROM todos t
        INNER JOIN clients c ON c.id = t.clientId
        WHERE t.isDone = 0 AND t.dueDate = :date
              AND (t.snoozedUntil IS NULL OR t.snoozedUntil <= :nowMillis)
        """
    )
    suspend fun getTodosDueOn(date: String, nowMillis: Long): List<OpenTodo>
}
