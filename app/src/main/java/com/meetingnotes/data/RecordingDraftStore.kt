package com.meetingnotes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 録音〜要約前の下書きを1件だけ端末に保持する。
 * アプリがプロセスごと終了しても文字起こしテキストが失われないようにするための保険。
 * 正式な商談は Room DB に保存され、こちらは「まだ保存に至っていない作業中の1件」だけを持つ。
 *
 * `MeetingNotesApp` でシングルトンとして生成し、[draft] を各 ViewModel が購読する。
 */
class RecordingDraftStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Draft(
        val clientId: Long,
        val transcript: String,
        val startedAt: Long,
        /** 0 = 録音中に中断 / >0 = 停止後(編集中)に中断。 */
        val endedAt: Long,
        val updatedAt: Long,
    )

    private val _draft = MutableStateFlow(readFromPrefs())
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    fun save(draft: Draft) {
        if (draft.transcript.isBlank()) return
        prefs.edit()
            .putLong(KEY_CLIENT_ID, draft.clientId)
            .putString(KEY_TRANSCRIPT, draft.transcript)
            .putLong(KEY_STARTED_AT, draft.startedAt)
            .putLong(KEY_ENDED_AT, draft.endedAt)
            .putLong(KEY_UPDATED_AT, draft.updatedAt)
            .apply()
        _draft.value = draft
    }

    fun clear() {
        prefs.edit().clear().apply()
        _draft.value = null
    }

    private fun readFromPrefs(): Draft? {
        val transcript = prefs.getString(KEY_TRANSCRIPT, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return Draft(
            clientId = prefs.getLong(KEY_CLIENT_ID, -1L),
            transcript = transcript,
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L),
            endedAt = prefs.getLong(KEY_ENDED_AT, 0L),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    private companion object {
        const val PREFS_NAME = "recording_draft"
        const val KEY_CLIENT_ID = "clientId"
        const val KEY_TRANSCRIPT = "transcript"
        const val KEY_STARTED_AT = "startedAt"
        const val KEY_ENDED_AT = "endedAt"
        const val KEY_UPDATED_AT = "updatedAt"
    }
}
