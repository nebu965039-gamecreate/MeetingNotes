package com.meetingnotes.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

class TranscriptionManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

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
        committedSegments.clear()

        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        isListening = true
        startListeningInternal()
    }

    fun stop() {
        isListening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        _audioLevel.value = 0f
    }

    private fun startListeningInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            _audioLevel.value = rmsdB
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (isListening) startListeningInternal()
        }

        override fun onError(error: Int) {
            if (!isListening) return

            when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                    startListeningInternal()
                }
                else -> {
                    // 権限不足・言語未対応・端末非対応などは再試行しても解消しないため停止する
                    isListening = false
                    _events.value = TranscriptionEvent.Error("音声認識エラー(code=$error)")
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                committedSegments.add(text)
                _transcript.value = committedSegments.joinToString(separator = "")
            }
            if (isListening) startListeningInternal()
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
