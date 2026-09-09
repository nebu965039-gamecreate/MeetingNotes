package com.meetingnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailTemplateDao {

    @Query("SELECT * FROM email_templates ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<EmailTemplateEntity>>

    @Insert
    suspend fun insert(template: EmailTemplateEntity): Long

    @Query("UPDATE email_templates SET name = :name, body = :body WHERE id = :id")
    suspend fun updateContent(id: Long, name: String, body: String)

    @Query("DELETE FROM email_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
