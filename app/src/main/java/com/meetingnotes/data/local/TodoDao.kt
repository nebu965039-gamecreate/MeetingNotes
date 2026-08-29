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

    @Query("UPDATE todos SET isDone = :isDone WHERE id = :todoId")
    suspend fun setDone(todoId: Long, isDone: Boolean)
}
