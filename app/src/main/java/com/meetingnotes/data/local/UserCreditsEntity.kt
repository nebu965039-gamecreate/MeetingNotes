package com.meetingnotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_credits")
data class UserCreditsEntity(
    @PrimaryKey val deviceIdHash: String,
    val balance: Int,
    val lastResetYearMonth: String,
    /** 今月このデバイスで実行したリモート会議モードの文字起こし回数。 */
    val onlineTranscriptionsUsed: Int = 0,
    /** 今月リワード広告で追加解放したリモート会議モードの回数(無料ユーザー用)。 */
    val onlineTranscriptionsBonus: Int = 0,
    /** 今日このデバイスで消費したAI要約のトークン数(2026-09-21〜、`CreditPolicy.DAILY_TOKEN_CAP` 参照)。 */
    val tokensUsedToday: Long = 0,
    /** `tokensUsedToday` を最後にリセットした日付(yyyy-MM-dd)。 */
    val tokensResetDate: String = ""
)
