package com.meetingnotes.ui.meeting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import android.app.Activity
import com.meetingnotes.ads.RewardedAdController
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.remote.AnthropicClient
import com.meetingnotes.export.CsvExporter
import com.meetingnotes.export.ExcelExporter
import com.meetingnotes.export.IcsExporter
import com.meetingnotes.export.MarkdownExporter
import com.meetingnotes.export.MeetingExportContentBuilder
import com.meetingnotes.export.PdfExporter
import com.meetingnotes.export.PdfPasswordProtector
import com.meetingnotes.export.Watermark
import com.meetingnotes.export.WordExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * フォローアップ下書きの表示状態。
 * 通常は要約時に生成された `MeetingEntity.followupDraft` を表示するだけ。
 * 付いていない商談(旧データ / Worker 未更新の時期)は「作成する」で**1回だけ**生成して保存し、
 * 以後は保存済みを表示するのみ(再生成なし=トークン節約)。
 */
sealed interface FollowupState {
    data object Idle : FollowupState
    data object Loading : FollowupState
    data class Ready(val text: String) : FollowupState
    /** 下書き未生成。ユーザーが「作成する」で1回だけ生成できる。 */
    data object Empty : FollowupState
    data class Error(val message: String) : FollowupState
}

class MeetingDetailViewModel(
    application: Application,
    private val repository: MeetingRepository,
    meetingId: Long
) : AndroidViewModel(application) {

    private val anthropicClient = AnthropicClient()
    private val rewardedAdController = RewardedAdController(application).apply { load() }

    /** 後追い下書き生成に広告視聴が必要か(無料ユーザーのみ)。 */
    fun followupNeedsAd(): Boolean = !ProAccess.isPro && meeting.value?.followupDraft.isNullOrBlank()
    val isRewardedAdLoaded get() = rewardedAdController.isLoaded

    val meeting: StateFlow<MeetingEntity?> = repository.observeMeeting(meetingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todos: StateFlow<List<TodoEntity>> = repository.observeTodos(meetingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _clientName = MutableStateFlow<String?>(null)
    val clientName: StateFlow<String?> = _clientName.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = meeting.filterNotNull().first()
            _clientName.value = repository.getClient(loaded.clientId)?.name
        }
    }

    private val meetingId = meetingId

    fun toggleTodo(todo: TodoEntity) {
        viewModelScope.launch { repository.setTodoDone(todo.id, !todo.isDone) }
    }

    fun renameTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameMeeting(meetingId, trimmed) }
    }

    fun setPhase(phase: DealPhase) {
        viewModelScope.launch { repository.setMeetingPhaseOverride(meetingId, phase) }
    }

    /** F7: 次回打ち合わせの日付を手動設定する(dateIso = "yyyy-MM-dd")。 */
    fun setNextMeetingDate(dateIso: String) {
        viewModelScope.launch { repository.setNextMeeting(meetingId, dateIso, originalText = null) }
    }

    private val _followupState = MutableStateFlow<FollowupState>(FollowupState.Idle)
    val followupState: StateFlow<FollowupState> = _followupState.asStateFlow()

    /** 商談詳細で「フォローアップの下書き」を開いたとき。保存済みがあれば表示、無ければ作成を促す。 */
    fun openFollowup() {
        if (_followupState.value == FollowupState.Loading) return
        val stored = meeting.value?.followupDraft?.trim().orEmpty()
        _followupState.value =
            if (stored.isNotEmpty()) FollowupState.Ready(stored) else FollowupState.Empty
    }

    /** 無料ユーザー: リワード広告を見てから後追い下書きを生成する。 */
    fun watchAdThenGenerateFollowup(activity: Activity) {
        if (_followupState.value == FollowupState.Loading) return
        rewardedAdController.show(activity) { generateFollowup() }
    }

    /** 下書きが無い商談で「作成する」を押したとき。1回だけ生成して DB に保存する。 */
    fun generateFollowup() {
        if (_followupState.value == FollowupState.Loading) return
        val current = meeting.value ?: return
        // すでに保存済みなら生成しない(二重作成防止)。
        current.followupDraft?.trim()?.takeIf { it.isNotEmpty() }?.let {
            _followupState.value = FollowupState.Ready(it)
            return
        }
        val source = MeetingExportContentBuilder.buildPlainText(clientName.value, current, todos.value)
        _followupState.value = FollowupState.Loading
        viewModelScope.launch {
            runCatching { anthropicClient.generateFollowup(source) }
                .onSuccess { text ->
                    repository.setMeetingFollowupDraft(meetingId, text)
                    _followupState.value = FollowupState.Ready(text)
                }
                .onFailure {
                    _followupState.value = FollowupState.Error(it.message ?: "下書きの生成に失敗しました。")
                }
        }
    }

    fun clearFollowup() {
        if (_followupState.value != FollowupState.Loading) {
            _followupState.value = FollowupState.Idle
        }
    }

    fun deleteMeeting(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMeeting(meetingId)
            onDeleted()
        }
    }

    /**
     * [password] が非空なら Pro機能として PDF にパスワードを設定する(AES-256、後処理方式)。
     * 失敗時(暗号化ライブラリのI/Oエラー等)は [onError] を呼ぶ。
     */
    fun exportPdf(
        watermark: Watermark?,
        password: String?,
        onReady: (File) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val blocks = MeetingExportContentBuilder.build(clientName.value, current, todos.value)
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = PdfExporter.exportToFile(getApplication(), "meeting_${current.id}.pdf", blocks, watermark)
                    if (!password.isNullOrBlank()) {
                        PdfPasswordProtector.protect(getApplication(), file, password)
                    }
                    file
                }
            }.onSuccess(onReady).onFailure { onError(it.message ?: "PDFの生成に失敗しました。") }
        }
    }

    fun exportWord(onReady: (File) -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val blocks = MeetingExportContentBuilder.build(clientName.value, current, todos.value)
            val file = withContext(Dispatchers.IO) {
                WordExporter.exportToFile(getApplication(), "meeting_${current.id}.docx", blocks)
            }
            onReady(file)
        }
    }

    fun exportMarkdown(onReady: (File) -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val blocks = MeetingExportContentBuilder.build(clientName.value, current, todos.value)
            val file = withContext(Dispatchers.IO) {
                MarkdownExporter.exportToFile(getApplication(), "meeting_${current.id}.md", blocks)
            }
            onReady(file)
        }
    }

    fun exportCsv(onReady: (File) -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                CsvExporter.exportToFile(getApplication(), "meeting_${current.id}_todo.csv", todos.value)
            }
            onReady(file)
        }
    }

    fun exportExcel(onReady: (File) -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                ExcelExporter.exportToFile(getApplication(), "meeting_${current.id}_todo.xlsx", todos.value)
            }
            onReady(file)
        }
    }

    /** 「次回打ち合わせ」の日時が確定していれば .ics を生成する。未定なら onNoDate。 */
    fun exportIcs(onReady: (File) -> Unit, onNoDate: () -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                IcsExporter.exportToFile(
                    getApplication(),
                    "meeting_${current.id}.ics",
                    current.id,
                    current.title,
                    clientName.value,
                    current.nextMeetingDate,
                    current.nextMeetingOriginalText,
                )
            }
            if (file != null) onReady(file) else onNoDate()
        }
    }

    companion object {
        fun factory(application: Application, repository: MeetingRepository, meetingId: Long) = viewModelFactory {
            initializer { MeetingDetailViewModel(application, repository, meetingId) }
        }
    }
}
