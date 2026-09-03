package com.meetingnotes.ui.meeting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.remote.AnthropicClient
import com.meetingnotes.export.CsvExporter
import com.meetingnotes.export.ExcelExporter
import com.meetingnotes.export.IcsExporter
import com.meetingnotes.export.MarkdownExporter
import com.meetingnotes.export.MeetingExportContentBuilder
import com.meetingnotes.export.PdfExporter
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

sealed interface FollowupState {
    data object Idle : FollowupState
    data object Loading : FollowupState
    data class Ready(val text: String, val casual: Boolean) : FollowupState
    data class Error(val message: String) : FollowupState
}

class MeetingDetailViewModel(
    application: Application,
    private val repository: MeetingRepository,
    meetingId: Long
) : AndroidViewModel(application) {

    private val anthropicClient = AnthropicClient()

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

    private val _followupState = MutableStateFlow<FollowupState>(FollowupState.Idle)
    val followupState: StateFlow<FollowupState> = _followupState.asStateFlow()

    /** F5: 商談要約からフォローアップ文面の下書きを生成する。 */
    fun generateFollowup(casual: Boolean) {
        val source = buildPlainTextSummary() ?: return
        _followupState.value = FollowupState.Loading
        viewModelScope.launch {
            runCatching { anthropicClient.generateFollowup(source, casual) }
                .onSuccess { _followupState.value = FollowupState.Ready(it, casual) }
                .onFailure {
                    _followupState.value = FollowupState.Error(it.message ?: "下書きの生成に失敗しました。")
                }
        }
    }

    fun clearFollowup() {
        _followupState.value = FollowupState.Idle
    }

    fun deleteMeeting(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMeeting(meetingId)
            onDeleted()
        }
    }

    fun exportPdf(watermark: Watermark?, onReady: (File) -> Unit) {
        val current = meeting.value ?: return
        viewModelScope.launch {
            val blocks = MeetingExportContentBuilder.build(clientName.value, current, todos.value)
            val file = withContext(Dispatchers.IO) {
                PdfExporter.exportToFile(getApplication(), "meeting_${current.id}.pdf", blocks, watermark)
            }
            onReady(file)
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

    fun buildPlainTextSummary(): String? {
        val current = meeting.value ?: return null
        return MeetingExportContentBuilder.buildPlainText(clientName.value, current, todos.value)
    }

    companion object {
        fun factory(application: Application, repository: MeetingRepository, meetingId: Long) = viewModelFactory {
            initializer { MeetingDetailViewModel(application, repository, meetingId) }
        }
    }
}
