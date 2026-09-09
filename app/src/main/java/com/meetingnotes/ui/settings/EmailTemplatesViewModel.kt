package com.meetingnotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.EmailTemplateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmailTemplatesViewModel(private val repository: MeetingRepository) : ViewModel() {

    val templates: StateFlow<List<EmailTemplateEntity>> =
        repository.observeEmailTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, body: String) {
        viewModelScope.launch { repository.addEmailTemplate(name, body) }
    }

    fun update(id: Long, name: String, body: String) {
        viewModelScope.launch { repository.updateEmailTemplate(id, name, body) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteEmailTemplate(id) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { EmailTemplatesViewModel(repository) }
        }
    }
}
