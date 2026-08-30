package com.meetingnotes.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface TranscriptionEvent {
    data object Unsupported : TranscriptionEvent
    data class Error(val message: String) : TranscriptionEvent
}

/**
 * オンデバイス音声認識のラッパー。
 *
 * `SpeechRecognizer` は1回の発話ごとにセッションが終了するため、録音中は
 * `onResults` / `onError` のたびに `startListening` を呼び直して継続させる必要がある。
 * ただし以下を守らないと `ERROR_CLIENT(5)` / `ERROR_RECOGNIZER_BUSY(8)` が発生する:
 *  - コールバック内から同期的に `startListening` を呼ばない(必ずメインHandler経由で遅延実行)
 *  - 前のセッションが終わる前に次を開始しない(`sessionActive` でガード)
 *  - `onEndOfSpeech` では再開しない(直後に必ず `onResults` か `onError` が来るため二重開始になる)
 */
class TranscriptionManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** ユーザーが録音中とみなしている間 true。stop() で false。 */
    private var isListening = false

    /** startListening 実行〜セッション終了(onResults/onError)までの多重起動防止フラグ。 */
    private var sessionActive = false

    /** 次回開始時に認識器を作り直すか(実エラーからの復帰時に true)。 */
    private var recreateOnNextStart = false

    /** 復帰可能な実エラーの連続回数。onReadyForSpeech / 有効な onResults でリセット。 */
    private var consecutiveErrors = 0

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _events = MutableStateFlow<TranscriptionEvent?>(null)
    val events: StateFlow<TranscriptionEvent?> = _events.asStateFlow()

    /** onRmsChangedで報告される音声レベル(dB相当)。録音中の可視化表示に使う。 */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val committedSegments = mutableListOf<String>()

    fun isSupported(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        if (!isSupported()) {
            _events.value = TranscriptionEvent.Unsupported
            return
        }
        if (isListening) return

        _transcript.value = ""
        _audioLevel.value = 0f
        _events.value = null
        committedSegments.clear()
        consecutiveErrors = 0
        recreateOnNextStart = false
        sessionActive = false
        isListening = true

        createRecognizer()
        scheduleSessionStart()
    }

    fun stop() {
        isListening = false
        sessionActive = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        _audioLevel.value = 0f
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

    /** 直前のセッションが確実に終了してから startListening するため、必ずこの経路を通す。 */
    private fun scheduleSessionStart() {
        if (!isListening) return
        mainHandler.removeCallbacks(sessionStartRunnable)
        mainHandler.postDelayed(sessionStartRunnable, RESTART_DELAY_MS)
    }

    private val sessionStartRunnable = Runnable {
        if (!isListening || sessionActive) return@Runnable
        if (recreateOnNextStart) {
            createRecognizer()
            recreateOnNextStart = false
        }
        val r = recognizer ?: return@Runnable
        sessionActive = true
        try {
            r.startListening(recognizerIntent())
        } catch (e: Exception) {
            sessionActive = false
            handleRetryableFailure()
        }
    }

    /** ERROR_CLIENT 等の復帰可能な失敗。認識器を作り直して再開し、連続しすぎたら諦める。 */
    private fun handleRetryableFailure() {
        if (!isListening) return
        consecutiveErrors++
        if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
            fail("音声認識を再開できませんでした。もう一度お試しください。")
            return
        }
        recreateOnNextStart = true
        scheduleSessionStart()
    }

    private fun fail(message: String) {
        isListening = false
        sessionActive = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        _events.value = TranscriptionEvent.Error(message)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            _audioLevel.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        // onResults か onError が必ず続くため、ここでは再開しない(二重開始で ERROR_CLIENT になる)
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            sessionActive = false
            if (!isListening) return

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("マイクの権限が許可されていません。設定から許可してください。")

                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    fail("この端末では日本語のオンデバイス音声認識を利用できません。")

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_NO_MATCH -> {
                    // 発話の切れ目・無音。エラー扱いせずそのまま次のセッションへ。
                    scheduleSessionStart()
                }

                // ERROR_CLIENT(5) / ERROR_RECOGNIZER_BUSY(8) / ネットワーク系など。
                // 端末側の内部状態が乱れていることが多いので認識器を作り直して再開する。
                else -> handleRetryableFailure()
            }
        }

        override fun onResults(results: Bundle?) {
            sessionActive = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                committedSegments.add(text)
                _transcript.value = committedSegments.joinToString(separator = "")
                consecutiveErrors = 0
            }
            scheduleSessionStart()
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        /** セッション間の待機。前セッションのteardownと重ならないようにする。 */
        const val RESTART_DELAY_MS = 80L

        /** 実エラーがこの回数を超えて連続したら録音を打ち切る。 */
        const val MAX_CONSECUTIVE_ERRORS = 5
    }
}
