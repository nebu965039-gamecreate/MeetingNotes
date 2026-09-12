package com.meetingnotes.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ads.InterstitialAdController
import com.meetingnotes.ads.RewardedAdController
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.data.RecordingDraftStore
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientGroupEntity
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.data.model.MeetingType
import com.meetingnotes.data.remote.AnthropicClient
import com.meetingnotes.speech.AudioFileRecorder
import com.meetingnotes.speech.TranscriptPreprocessor
import com.meetingnotes.speech.TranscriptionEvent
import com.meetingnotes.speech.TranscriptionManager
import com.meetingnotes.util.DeviceIdentifier
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

enum class RecordingPhase { Countdown, Recording, Stopping, Transcribing, Editing }

/** 保存直後、要約結果画面で出す「案件フェーズ」の確認プロンプト(2026-09-11)。 */
sealed interface PostSavePrompt {
    /** クライアントにまだ案件が無い → 案件作成を促す。 */
    data class CreateProject(
        val clientId: Long,
        val clientName: String,
        val suggestedPhase: DealPhase?
    ) : PostSavePrompt

    /** 進行中の案件がちょうど1つ、かつ AI 推定フェーズと違う → フェーズ更新を促す。 */
    data class UpdatePhase(
        val projectId: Long,
        val projectName: String,
        val currentPhase: DealPhase?,
        val suggestedPhase: DealPhase
    ) : PostSavePrompt
}

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val transcriptionManager = TranscriptionManager(application)
    private val audioFileRecorder = AudioFileRecorder(application)
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
    private var audioLevelJob: Job? = null

    /** この録音の実施形態。REMOTE のときはサーバー文字起こしを使う。 */
    private var meetingType: MeetingType = MeetingType.IN_PERSON

    /** REMOTE 録音の音声ファイル。文字起こし成功後、保存完了で削除する。 */
    private var recordedAudioFile: File? = null

    val liveTranscript: StateFlow<String> = transcriptionManager.transcript

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _transcribeError = MutableStateFlow<String?>(null)
    /** リモート会議モードの文字起こしエラー(Transcribing フェーズで表示)。 */
    val transcribeError: StateFlow<String?> = _transcribeError.asStateFlow()

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

    /** 保存が完了して遷移すべきクライアントID。[postSavePrompt] が null になったら画面が遷移する。 */
    private val _savedClientId = MutableStateFlow<Long?>(null)
    val savedClientId: StateFlow<Long?> = _savedClientId.asStateFlow()

    private val _postSavePrompt = MutableStateFlow<PostSavePrompt?>(null)
    val postSavePrompt: StateFlow<PostSavePrompt?> = _postSavePrompt.asStateFlow()

    val creditBalance: StateFlow<Int> = repository.observeCredits(deviceIdHash)
        .map { it?.balance ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isRewardedAdLoaded: StateFlow<Boolean> = rewardedAdController.isLoaded

    /** TOPから直接録音した場合、保存時にクライアントを選ぶ必要がある(clientId 未割り当て)。 */
    fun isClientAssigned(): Boolean = clientId >= 0

    val clients: StateFlow<List<ClientEntity>> = repository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clientGroups: StateFlow<List<ClientGroupEntity>> = repository.observeClientGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repository.getOrInitCredits(deviceIdHash) }
        rewardedAdController.load()
        interstitialAdController.load()
    }

    fun isTranscriptionSupported(): Boolean = transcriptionManager.isSupported()

    fun currentMeetingType(): MeetingType = meetingType

    /** その月に残っているリモート会議モードの回数。 */
    suspend fun remainingRemoteTranscriptions(): Int =
        repository.remainingOnlineTranscriptions(deviceIdHash, ProAccess.isPro)

    fun isProUser(): Boolean = ProAccess.isPro

    /** 無料ユーザーがリワード広告を見てリモート会議モードを1回追加する。 */
    fun watchAdForRemoteTranscription(activity: Activity, onGranted: () -> Unit) {
        rewardedAdController.show(activity) {
            viewModelScope.launch {
                repository.grantOnlineTranscriptionBonus(deviceIdHash)
                onGranted()
            }
        }
    }

    /** 録音開始ボタン押下時に呼ぶ。3秒のカウントダウンを挟んでから実際の録音を開始する。 */
    fun beginRecordingFlow(clientId: Long, meetingType: MeetingType = MeetingType.IN_PERSON) {
        this.clientId = clientId
        this.meetingType = meetingType
        _errorMessage.value = null
        _transcribeError.value = null
        _savedClientId.value = null
        _postSavePrompt.value = null
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
        _audioLevel.value = 0f

        val remote = meetingType == MeetingType.REMOTE
        if (remote) {
            if (!audioFileRecorder.start()) {
                _errorMessage.value = "録音を開始できませんでした。マイクの許可を確認してください。"
            }
        } else {
            transcriptionManager.start()
        }

        audioLevelJob?.cancel()
        audioLevelJob = viewModelScope.launch {
            val src = if (remote) audioFileRecorder.audioLevel else transcriptionManager.audioLevel
            src.collect { _audioLevel.value = it }
        }

        elapsedTickerJob?.cancel()
        elapsedTickerJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(1000)
                _recordingElapsedMs.value = System.currentTimeMillis() - recordingStartedAt
                // 対面モードは文字起こしを下書き保存(プロセス終了時の保険)。
                if (!remote && ++tick % 5 == 0) persistDraft(endedAt = 0L)
                // リモートは長時間で自動停止(サーバー上限対策)。
                if (remote && _recordingElapsedMs.value >= AudioFileRecorder.MAX_DURATION_MS) {
                    requestStopRecording()
                    break
                }
            }
        }

        if (!remote) {
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
    }

    override fun onCleared() {
        // 万一録音中に破棄されても、ミュートしたストリームを確実に戻す。
        transcriptionManager.stop()
        audioFileRecorder.cancel()
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
        audioLevelJob?.cancel()
        audioLevelJob = null
        transcriptionManager.stop()
        audioFileRecorder.cancel()
        recordedAudioFile?.delete()
        recordedAudioFile = null
        _recordingPhase.value = RecordingPhase.Countdown
    }

    /** 停止ボタン押下時に呼ぶ。 */
    fun requestStopRecording() {
        if (_recordingPhase.value != RecordingPhase.Recording) return
        recordingEndedAt = System.currentTimeMillis()
        elapsedTickerJob?.cancel(); elapsedTickerJob = null
        audioLevelJob?.cancel(); audioLevelJob = null
        _audioLevel.value = 0f

        if (meetingType == MeetingType.REMOTE) {
            _recordingPhase.value = RecordingPhase.Transcribing
            recordedAudioFile = audioFileRecorder.stopAndGetFile()
            transcribeRecordedAudio()
        } else {
            _recordingPhase.value = RecordingPhase.Stopping
            viewModelScope.launch {
                delay(STOPPING_TRANSITION_MS)
                finalizeInPersonStop()
            }
        }
    }

    private fun finalizeInPersonStop() {
        transcriptionEventsJob?.cancel()
        transcriptionEventsJob = null
        transcriptionManager.stop()
        val preprocessed = transcriptPreprocessor.preprocess(liveTranscript.value)
        originalTranscript = preprocessed
        _editableTranscript.value = preprocessed
        _recordingPhase.value = RecordingPhase.Editing
        persistDraft(endedAt = recordingEndedAt)
    }

    /** リモート会議モード: 録音ファイルをサーバーへ送って文字起こしする。 */
    private fun transcribeRecordedAudio() {
        val file = recordedAudioFile
        if (file == null || !file.exists() || file.length() == 0L) {
            _transcribeError.value = "録音の保存に失敗しました。もう一度録音してください。"
            return
        }
        _transcribeError.value = null
        viewModelScope.launch {
            runCatching {
                val bytes = file.readBytes()
                anthropicClient.transcribeAudio(bytes, AudioFileRecorder.MIME_TYPE)
            }.onSuccess { text ->
                repository.consumeOnlineTranscription(deviceIdHash, ProAccess.isPro)
                val preprocessed = transcriptPreprocessor.preprocess(text)
                originalTranscript = preprocessed
                _editableTranscript.value = preprocessed
                _recordingPhase.value = RecordingPhase.Editing
                persistDraft(endedAt = recordingEndedAt)
            }.onFailure {
                _transcribeError.value = it.message ?: "文字起こしに失敗しました。"
            }
        }
    }

    /** Transcribing フェーズでの再試行(同じ録音ファイルをもう一度送る)。 */
    fun retryTranscription() {
        if (_recordingPhase.value != RecordingPhase.Transcribing) return
        transcribeRecordedAudio()
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
        if (text.isBlank()) return
        draftStore.save(
            RecordingDraftStore.Draft(
                clientId = clientId,
                transcript = text,
                startedAt = recordingStartedAt,
                endedAt = endedAt,
                updatedAt = System.currentTimeMillis(),
                meetingType = meetingType.wireValue,
            )
        )
    }

    fun pendingDraftForClient(clientId: Long): Boolean =
        draftStore.draft.value?.clientId == clientId

    /** クライアント一覧などから「途中の下書きを開く」で呼ぶ。編集画面の状態に復元する。 */
    fun restoreDraft(): Boolean {
        val draft = draftStore.draft.value ?: return false
        clientId = draft.clientId
        meetingType = MeetingType.fromWire(draft.meetingType) ?: MeetingType.IN_PERSON
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
        // 「破棄して新しく録音」の直後に前回分が残らないよう、編集用テキストと認識器の状態を
        // 念のためここでも明示的にリセットする(2026-09-15 report: 破棄後の新しい録音に前回の
        // 文字起こしが残っていた)。次の録音は beginRecordingFlow → transcriptionManager.start()
        // で改めてリセットされるが、start() が万一 isListening のまま呼ばれても安全なように二重に保険をかける。
        _editableTranscript.value = ""
        originalTranscript = ""
        transcriptionManager.stop()
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

            val summaryJob = async { runCatching { anthropicClient.summarizeMeeting(transcript) } }

            // 待ち時間にインタースティシャル広告を挟む。ただし要約が短時間で終わる場合
            // (結果が出た後に広告)は不快なので、少し待って まだ処理中のときだけ表示する。
            delay(AD_DELAY_MS)
            if (!summaryJob.isCompleted) interstitialAdController.tryShow(activity)

            summaryJob.await()
                .onSuccess { _summaryState.value = SummaryUiState.Success(it) }
                .onFailure {
                    repository.grantCredit(deviceIdHash)
                    _summaryState.value = SummaryUiState.Error(it.message ?: "要約に失敗しました。")
                }
        }
    }

    fun defaultMeetingTitle(): String =
        "${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}の議事録"

    /**
     * 要約結果を指定クライアントに保存する共通処理。保存できたら
     * [_savedClientId] をセットし、必要なら [_postSavePrompt] に案件フェーズの確認を積む。
     */
    private suspend fun persistMeeting(targetClientId: Long, title: String, clientName: String? = null) {
        val state = _summaryState.value
        if (state !is SummaryUiState.Success) return
        repository.saveMeeting(
            clientId = targetClientId,
            title = title.ifBlank { defaultMeetingTitle() },
            transcript = _editableTranscript.value,
            summary = state.summary,
            recordedAt = recordingStartedAt,
            endedAt = recordingEndedAt,
            meetingType = meetingType
        )
        draftStore.clear()
        recordedAudioFile?.delete()
        recordedAudioFile = null

        _postSavePrompt.value = buildPostSavePrompt(targetClientId, state.summary.dealPhase, clientName)
        _savedClientId.value = targetClientId
    }

    private suspend fun buildPostSavePrompt(
        clientId: Long,
        aiPhase: DealPhase?,
        clientName: String?
    ): PostSavePrompt? {
        val projects = repository.observeClientProjects(clientId).first()
        if (projects.isEmpty()) {
            val name = clientName ?: repository.observeClient(clientId).first()?.name ?: ""
            return PostSavePrompt.CreateProject(clientId, name, aiPhase)
        }
        val active = projects.filter { DealPhase.fromWire(it.phase)?.isActive != false }
        val target = active.singleOrNull() ?: return null
        if (aiPhase == null || DealPhase.fromWire(target.phase) == aiPhase) return null
        return PostSavePrompt.UpdatePhase(
            projectId = target.id,
            projectName = target.name,
            currentPhase = DealPhase.fromWire(target.phase),
            suggestedPhase = aiPhase
        )
    }

    /** クライアントが確定している通常フロー(クライアント画面から録音)での保存。 */
    fun saveMeeting(title: String) {
        if (clientId < 0) return
        viewModelScope.launch { persistMeeting(clientId, title) }
    }

    /** TOPから直接録音した場合に、保存時に選んだ既存クライアントへ保存する。 */
    fun saveMeetingToClient(targetClientId: Long, title: String) {
        viewModelScope.launch { persistMeeting(targetClientId, title) }
    }

    /** TOPから直接録音した場合に、保存時に新規クライアントを作成してそこへ保存する。 */
    fun saveMeetingToNewClient(name: String, groupId: Long?, title: String) {
        // 保存できる状態(要約成功)でなければクライアントを作らない(空クライアントの残留防止)。
        if (_summaryState.value !is SummaryUiState.Success) return
        viewModelScope.launch {
            val newId = repository.addClient(name.trim(), groupId)
            persistMeeting(newId, title, clientName = name.trim())
        }
    }

    // --- 保存後の「案件フェーズ」確認プロンプト ---

    /** `CreateProject` プロンプトへの応答。名前が空ならスキップ(案件は作らない)。 */
    fun resolveCreateProject(name: String, phase: DealPhase?) {
        val p = _postSavePrompt.value as? PostSavePrompt.CreateProject ?: return
        viewModelScope.launch {
            if (name.isNotBlank()) repository.addClientProject(p.clientId, name.trim(), phase)
            _postSavePrompt.value = null
        }
    }

    /** `UpdatePhase` プロンプトへの応答。選んだフェーズで案件を更新。 */
    fun resolveUpdatePhase(phase: DealPhase) {
        val p = _postSavePrompt.value as? PostSavePrompt.UpdatePhase ?: return
        viewModelScope.launch {
            repository.setProjectPhase(p.projectId, phase)
            _postSavePrompt.value = null
        }
    }

    /** プロンプトを閉じるだけ(何もしない)。 */
    fun dismissPostSavePrompt() {
        _postSavePrompt.value = null
    }

    fun resetForNewMeeting() {
        _editableTranscript.value = ""
        _summaryState.value = SummaryUiState.Idle
        _errorMessage.value = null
        _transcribeError.value = null
        _savedClientId.value = null
        _postSavePrompt.value = null
        _recordingPhase.value = RecordingPhase.Countdown
        meetingType = MeetingType.IN_PERSON
        recordedAudioFile?.delete()
        recordedAudioFile = null
    }

    companion object {
        private const val COUNTDOWN_START = 3
        private const val STOPPING_TRANSITION_MS = 700L

        /** 要約がこの時間で終わらないときだけインタースティシャル広告を出す。 */
        private const val AD_DELAY_MS = 1200L
    }
}
