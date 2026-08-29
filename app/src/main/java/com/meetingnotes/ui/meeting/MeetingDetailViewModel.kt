package com.meetingnotes.ui.meeting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
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

class MeetingDetailViewModel(
    application: Application,
    private val repository: MeetingRepository,
    meetingId: Long
) : AndroidViewModel(application) {

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
