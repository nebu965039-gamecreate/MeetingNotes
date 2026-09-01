package com.meetingnotes.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ads.InterstitialAdController
import com.meetingnotes.ads.RewardedAdController
import com.meetingnotes.data.RecordingDraftStore
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.data.remote.AnthropicClient
import com.meetingnotes.speech.TranscriptPreprocessor
import com.meetingnotes.speech.TranscriptionEvent
import com.meetingnotes.speech.TranscriptionManager
import com.meetingnotes.util.DeviceIdentifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface SummaryUiState {
    data object Idle : SummaryUiState
    data object Loading : SummaryUiState
    data class Success(val summary: MeetingSummary) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

enum class RecordingPhase { Countdown, Recording, Stopping, Editing }

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriptionManager = TranscriptionManager(application)
    private val transcriptPreprocessor = TranscriptPreprocessor()
    private val anthropicClient = AnthropicClient(
        integrityProvider = (application as MeetingNotesApp).integrityTokenProvider
    )
    private val repository = (application as MeetingNotesApp).repository
    private val draftStore = (application as MeetingNotesApp).recordingDraftStore

    private val deviceIdHash = DeviceIdentifier.getHashedId(application)
    private val rewardedAdController = RewardedAdController(application)
    private val interstitialAdController = InterstitialAdController(application)

    private var clientId: Long = -1
    private var originalTranscript: String = ""
    private var recordingStartedAt: Long = 0L
    private var recordingEndedAt: Long = 0L
    private var countdownJob: Job? = null
    private var transcriptionEventsJob: Job? = null
    private var elapsedTickerJob: Job? = null

    val liveTranscript: StateFlow<String> = transcriptionManager.transcript
    val audioLevel: StateFlow<Float> = transcriptionManager.audioLevel

    private val _recordingPhase = MutableStateFlow(RecordingPhase.Countdown)
    val recordingPhase: StateFlow<RecordingPhase> = _recordingPhase.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(COUNTDOWN_START)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _editableTranscript = MutableStateFlow("")
    val editableTranscript: StateFlow<String> = _editableTranscript.asStateFlow()

    /** 録音経過時間(ミリ秒)。長時間録音時に画面側で目安の警告を出すために公開している。 */
    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _summaryState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val summaryState: StateFlow<SummaryUiState> = _summaryState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val creditBalance: StateFlow<Int> = repository.observeCredits(deviceIdHash)
        .map { it?.balance ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isRewardedAdLoaded: StateFlow<Boolean> = rewardedAdController.isLoaded

    init {
        viewModelScope.launch { repository.getOrInitCredits(deviceIdHash) }
        rewardedAdController.load()
        interstitialAdController.load()
    }

    fun isTranscriptionSupported(): Boolean = transcriptionManager.isSupported()

    /** 録音開始ボタン押下時に呼ぶ。3秒のカウントダウンを挟んでから実際の録音を開始する。 */
    fun beginRecordingFlow(clientId: Long) {
        this.clientId = clientId
        _errorMessage.value = null
        _recordingPhase.value = RecordingPhase.Countdown
        _countdownSeconds.value = COUNTDOWN_START

        countdownJob = viewModelScope.launch {
            for (remaining in COUNTDOWN_START downTo 1) {
                _countdownSeconds.value = remaining
                delay(1000)
            }
            beginActualRecording()
        }
    }

    private fun beginActualRecording() {
        _recordingPhase.value = RecordingPhase.Recording
        recordingStartedAt = System.currentTimeMillis()
        _errorMessage.value = null
        _recordingElapsedMs.value = 0L
        transcriptionManager.start()

        elapsedTickerJob?.cancel()
        elapsedTickerJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(1000)
                _recordingElapsedMs.value = System.currentTimeMillis() - recordingStartedAt
                // 数秒おきに文字起こしを下書き保存(プロセス終了時の保険)。
                if (++tick % 5 == 0) persistDraft(endedAt = 0L)
            }
        }

        transcriptionEventsJob?.cancel()
        transcriptionEventsJob = viewModelScope.launch {
            transcriptionManager.events.collect { event ->
                when (event) {
                    is TranscriptionEvent.Unsupported ->
                        _errorMessage.value = "この端末はオンデバイス音声認識に対応していません。"
                    is TranscriptionEvent.Error ->
                        _errorMessage.value = event.message
                    null -> Unit
                }
            }
        }
    }

    /** カウントダウン中・録音中を問わず、画面を離脱する際に呼ぶ。進行中の処理を安全に後始末する。 */
    fun cancelRecordingFlow() {
        // 中断前に最新の文字起こしを下書き保存(一覧から再開できるようにする)。
        persistDraft(endedAt = if (_recordingPhase.value == RecordingPhase.Editing) recordingEndedAt else 0L)
        countdownJob?.cancel()
        countdownJob = null
        transcriptionEventsJob?.cancel()
        transcriptionEventsJob = null
        elapsedTickerJob?.cancel()
        elapsedTickerJob = null
        transcriptionManager.stop()
        _recordingPhase.value = RecordingPhase.Countdown
    }

    /** 停止ボタン押下時に呼ぶ。「停止中...」を一瞬挟んでから編集画面用の状態にする。 */
    fun requestStopRecording() {
        if (_recordingPhase.value != RecordingPhase.Recording) return
        _recordingPhase.value = RecordingPhase.Stopping
        viewModelScope.launch {
            delay(STOPPING_TRANSITION_MS)
            finalizeStop()
        }
    }

    private fun finalizeStop() {
        recordingEndedAt = System.currentTimeMillis()
        transcriptionEventsJob?.cancel()
        transcriptionEventsJob = null
        elapsedTickerJob?.cancel()
        elapsedTickerJob = null
        transcriptionManager.stop()
        val preprocessed = transcriptPreprocessor.preprocess(liveTranscript.value)
        originalTranscript = preprocessed
        _editableTranscript.value = preprocessed
        _recordingPhase.value = RecordingPhase.Editing
        persistDraft(endedAt = recordingEndedAt)
    }

    fun updateEditableTranscript(text: String) {
        _editableTranscript.value = text
        persistDraft(endedAt = recordingEndedAt)
    }

    /** 現在の文字起こしを下書きとして端末に保存する(要約前の作業を失わないための保険)。 */
    private fun persistDraft(endedAt: Long) {
        val text = if (_recordingPhase.value == RecordingPhase.Editing) {
            _editableTranscript.value
        } else {
            liveTranscript.value
        }
        if (text.isBlank() || clientId < 0) return
        draftStore.save(
            RecordingDraftStore.Draft(
                clientId = clientId,
                transcript = text,
                startedAt = recordingStartedAt,
                endedAt = endedAt,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun pendingDraftForClient(clientId: Long): Boolean =
        draftStore.draft.value?.clientId == clientId

    /** クライアント一覧などから「途中の下書きを開く」で呼ぶ。編集画面の状態に復元する。 */
    fun restoreDraft(): Boolean {
        val draft = draftStore.draft.value ?: return false
        clientId = draft.clientId
        recordingStartedAt = draft.startedAt
        recordingEndedAt = if (draft.endedAt > 0) draft.endedAt else System.currentTimeMillis()
        originalTranscript = draft.transcript
        _editableTranscript.value = draft.transcript
        _summaryState.value = SummaryUiState.Idle
        _errorMessage.value = null
        _recordingPhase.value = RecordingPhase.Editing
        return true
    }

    fun discardDraft() {
        draftStore.clear()
    }

    /** 手編集を、文字起こし直後(前処理済み)のテキストに戻す。 */
    fun revertToOriginalTranscript() {
        _editableTranscript.value = originalTranscript
    }

    /** リワード広告を視聴してクレジットを獲得する(仕様書7.1)。 */
    fun watchRewardedAd(activity: Activity) {
        rewardedAdController.show(activity) {
            viewModelScope.launch { repository.grantCredit(deviceIdHash) }
        }
    }

    fun submitForSummary(activity: Activity) {
        val transcript = _editableTranscript.value
        if (transcript.isBlank()) {
            _summaryState.value = SummaryUiState.Error("文字起こしテキストが空です。")
            return
        }
        if (transcript.length > AnthropicClient.MAX_TRANSCRIPT_CHARS) {
            _summaryState.value = SummaryUiState.Error(AnthropicClient.TRANSCRIPT_TOO_LONG_MESSAGE)
            return
        }

        _summaryState.value = SummaryUiState.Loading
        viewModelScope.launch {
            if (!repository.consumeCredit(deviceIdHash)) {
                _summaryState.value = SummaryUiState.Error("クレジットが残っていません。広告を見て獲得してください。")
                return@launch
            }

            // 要約の待ち時間にインタースティシャル広告を挟む(ロード済みかつ頻度キャップ内のときのみ)。
            // 広告表示中に裏で要約が進み、閉じたときには結果が出ている、という流れを狙う。
            interstitialAdController.tryShow(activity)

            runCatching { anthropicClient.summarizeMeeting(transcript) }
                .onSuccess { _summaryState.value = SummaryUiState.Success(it) }
                .onFailure {
                    repository.grantCredit(deviceIdHash)
                    _summaryState.value = SummaryUiState.Error(it.message ?: "要約に失敗しました。")
                }
        }
    }

    fun defaultMeetingTitle(): String =
        "${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}の議事録"

    fun saveMeeting(title: String, onSaved: () -> Unit) {
        val state = _summaryState.value
        if (state !is SummaryUiState.Success) return
        val transcript = _editableTranscript.value
        val finalTitle = title.ifBlank { defaultMeetingTitle() }
        viewModelScope.launch {
            repository.saveMeeting(
                clientId = clientId,
                title = finalTitle,
                transcript = transcript,
                summary = state.summary,
                recordedAt = recordingStartedAt,
                endedAt = recordingEndedAt
            )
            draftStore.clear()
            onSaved()
        }
    }

    fun resetForNewMeeting() {
        _editableTranscript.value = ""
        _summaryState.value = SummaryUiState.Idle
        _errorMessage.value = null
        _recordingPhase.value = RecordingPhase.Countdown
    }

    companion object {
        private const val COUNTDOWN_START = 3
        private const val STOPPING_TRANSITION_MS = 700L
    }
}
