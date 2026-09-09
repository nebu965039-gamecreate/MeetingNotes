package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * フォローアップ用のメール文面テンプレート。
 * 本文中の `{クライアント名}` / `{日付}` は使用時に差し込まれる(`util/TemplateVars`)。
 */
@Entity(tableName = "email_templates")
data class EmailTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val createdAt: Long
)
