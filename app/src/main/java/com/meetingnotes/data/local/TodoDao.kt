package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Insert
    suspend fun insertAll(todos: List<TodoEntity>)

    @Query("SELECT * FROM todos WHERE meetingId = :meetingId ORDER BY id ASC")
    fun observeByMeeting(meetingId: Long): Flow<List<TodoEntity>>

    @Query(
        "SELECT t.* FROM todos t INNER JOIN meetings m ON t.meetingId = m.id " +
            "WHERE m.clientId = :clientId ORDER BY t.id ASC"
    )
    fun observeByClient(clientId: Long): Flow<List<TodoEntity>>

    @Query("UPDATE todos SET isDone = :isDone WHERE id = :todoId")
    suspend fun setDone(todoId: Long, isDone: Boolean)

    /** 期限が解決できている未完了 ToDo(ホーム「期限のあるToDo」・カレンダー・リマインド用)。 */
    @Query(
        """
        SELECT t.id AS todoId, t.meetingId AS meetingId, t.task AS task, t.assignee AS assignee,
               t.deadline AS deadline, t.dueDate AS dueDate, t.isDone AS isDone,
               m.clientId AS clientId, c.name AS clientName
        FROM todos t
        INNER JOIN meetings m ON t.meetingId = m.id
        INNER JOIN clients c ON c.id = m.clientId
        WHERE t.isDone = 0 AND t.dueDate IS NOT NULL
        ORDER BY t.dueDate ASC
        """
    )
    fun observeOpenTodosWithDueDate(): Flow<List<OpenTodo>>

    /** クライアントごとの未完了 ToDo 件数(ホームのフォローボードのバッジ)。 */
    @Query(
        """
        SELECT m.clientId AS clientId, COUNT(*) AS count
        FROM todos t INNER JOIN meetings m ON t.meetingId = m.id
        WHERE t.isDone = 0
        GROUP BY m.clientId
        """
    )
    fun observeOpenTodoCountByClient(): Flow<List<ClientTodoCount>>

    /** 指定日が期限の未完了 ToDo(リマインド Worker 用、Flow でなく suspend)。 */
    @Query(
        """
        SELECT t.id AS todoId, t.meetingId AS meetingId, t.task AS task, t.assignee AS assignee,
               t.deadline AS deadline, t.dueDate AS dueDate, t.isDone AS isDone,
               m.clientId AS clientId, c.name AS clientName
        FROM todos t
        INNER JOIN meetings m ON t.meetingId = m.id
        INNER JOIN clients c ON c.id = m.clientId
        WHERE t.isDone = 0 AND t.dueDate = :date
        """
    )
    suspend fun getTodosDueOn(date: String): List<OpenTodo>
}
