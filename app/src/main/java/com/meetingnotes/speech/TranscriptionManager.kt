package com.meetingnotes.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

sealed interface TranscriptionEvent {
    data object Unsupported : TranscriptionEvent
    data class Error(val message: String) : TranscriptionEvent
}

/** 自前 AudioRecord のサンプリングレート(オンデバイス認識は 16kHz で十分)。 */
private const val SAMPLE_RATE = 16_000

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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 認識セッションの開始/終了音を消しているあいだ true。 */
    private var beepsMuted = false

    /** ユーザーが録音中とみなしている間 true。stop() で false。 */
    private var isListening = false

    /** startListening 実行〜セッション終了(onResults/onError)までの多重起動防止フラグ。 */
    private var sessionActive = false

    /** 次回開始時に認識器を作り直すか(実エラーからの復帰時に true)。 */
    private var recreateOnNextStart = false

    /** 復帰可能な実エラーの連続回数。onReadyForSpeech / 有効な onResults でリセット。 */
    private var consecutiveErrors = 0

    /** 一度でも認識結果が返ってきたか(音声フィーダーのフォールバック判定に使う)。 */
    private var everSucceeded = false

    /** フィーダー経路で認識結果ゼロのまま終わったセッション数。 */
    private var emptyFeederSessions = 0

    /**
     * 自前の AudioRecord → パイプ → 認識器 経路を使っているあいだ true。
     * これにより認識エンジン標準の近接音声向け処理(遠い声を消す)を回避し、
     * テレビ会議でスピーカーから出る相手の声も拾いやすくする。開始に失敗したら通常経路へ戻す。
     */
    private var audioFeederEnabled = false

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _events = MutableStateFlow<TranscriptionEvent?>(null)
    val events: StateFlow<TranscriptionEvent?> = _events.asStateFlow()

    /** 音声レベル(録音中の可視化表示用)。フィーダー使用時は自前の振幅、それ以外は onRmsChanged。 */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val micAudioFeeder = MicAudioFeeder { level ->
        if (audioFeederEnabled) _audioLevel.value = level
    }

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
        everSucceeded = false
        emptyFeederSessions = 0
        recreateOnNextStart = false
        sessionActive = false
        isListening = true

        // SpeechRecognizer は発話の区切りごとにセッションを開始/終了する。多くの端末
        // (特に OPPO/ColorOS)はそのたびに効果音を鳴らすため、録音中は該当ストリームをミュートする。
        muteBeeps()
        // 自前でマイクを開き、認識器へはパイプで渡す。開けなければ通常経路(認識器がマイクを開く)。
        audioFeederEnabled = micAudioFeeder.start()
        createRecognizer()
        scheduleSessionStart()
    }

    fun stop() {
        isListening = false
        sessionActive = false
        audioFeederEnabled = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        micAudioFeeder.stop()
        _audioLevel.value = 0f
        unmuteBeeps()
    }

    private fun muteBeeps() {
        if (beepsMuted) return
        beepsMuted = true
        BEEP_STREAMS.forEach { stream ->
            runCatching { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0) }
        }
    }

    private fun unmuteBeeps() {
        if (!beepsMuted) return
        beepsMuted = false
        BEEP_STREAMS.forEach { stream ->
            runCatching { audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0) }
        }
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
    }

    /** [audioSource] が非 null なら、認識器はマイクではなくそのパイプから音声を読む。 */
    private fun recognizerIntent(audioSource: ParcelFileDescriptor?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPAN.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            if (audioSource != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            }
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

        // フィーダー使用時はこのセッション用のパイプを用意する。作れなければ通常経路へフォールバック。
        val audioSource = if (audioFeederEnabled) {
            micAudioFeeder.newSessionSource().also { if (it == null) disableAudioFeeder() }
        } else {
            null
        }

        sessionActive = true
        try {
            r.startListening(recognizerIntent(audioSource))
        } catch (e: Exception) {
            sessionActive = false
            runCatching { audioSource?.close() }
            handleRetryableFailure()
        }
    }

    /** 自前マイク経路をやめて、認識器が直接マイクを開く通常経路に戻す。 */
    private fun disableAudioFeeder() {
        if (!audioFeederEnabled) return
        audioFeederEnabled = false
        micAudioFeeder.stop()
        recreateOnNextStart = true
    }

    /** ERROR_CLIENT 等の復帰可能な失敗。認識器を作り直して再開し、連続しすぎたら諦める。 */
    private fun handleRetryableFailure() {
        if (!isListening) return
        consecutiveErrors++
        // 自前マイク経路がまだ一度も成功しておらず連続で失敗するなら、その経路が原因の可能性が高い。
        // 通常経路(認識器がマイクを開く)へ切り替えてやり直す。
        if (audioFeederEnabled && !everSucceeded && consecutiveErrors >= 2) {
            disableAudioFeeder()
            consecutiveErrors = 0
            scheduleSessionStart()
            return
        }
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
        audioFeederEnabled = false
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        micAudioFeeder.stop()
        unmuteBeeps()
        _events.value = TranscriptionEvent.Error(message)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
            everSucceeded = true
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // フィーダー使用時は AudioRecord 側で算出した振幅を使う(認識器からの値は来ないことがある)。
            if (!audioFeederEnabled) _audioLevel.value = rmsdB
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
                    // ただしフィーダー経路で一度も認識できず無音ばかりなら、その経路が
                    // 認識器に無視されている可能性が高いので通常経路へ切り替える。
                    if (audioFeederEnabled && !everSucceeded && ++emptyFeederSessions >= 3) {
                        disableAudioFeeder()
                    }
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
                everSucceeded = true
                emptyFeederSessions = 0
            }
            scheduleSessionStart()
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * 録音中ずっとマイクを開いたままにし、認識セッションごとに新しいパイプへ差し替えて
     * `SpeechRecognizer` へ音声を供給する。認識エンジンが自分でマイクを開くと近接音声向けの
     * 処理が入り、離れた相手の声(テレビ会議のスピーカー越しなど)が削られやすい。
     */
    private class MicAudioFeeder(private val onLevel: (Float) -> Unit) {
        @Volatile private var running = false
        private var record: AudioRecord? = null
        private var thread: Thread? = null
        private val sinkLock = Any()
        private var sink: ParcelFileDescriptor.AutoCloseOutputStream? = null

        /** AudioRecord を開始。マイク権限が無い・初期化失敗なら false(通常経路にフォールバック)。 */
        @SuppressLint("MissingPermission")
        fun start(): Boolean {
            if (running) return true
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return false
            val bufSize = maxOf(minBuf, SAMPLE_RATE) // ≒0.5秒
            val rec = buildRecord(bufSize) ?: return false
            record = rec
            running = true
            return try {
                rec.startRecording()
                thread = Thread { readLoop(rec, bufSize) }.apply { isDaemon = true; start() }
                true
            } catch (e: IllegalStateException) {
                running = false
                runCatching { rec.release() }
                record = null
                false
            }
        }

        // 録音フロー(RecordingScreen)が RECORD_AUDIO 許可を確認してからでないと start() は呼ばれない。
        // 万一許可が無くても runCatching で SecurityException を握りつぶし、通常経路にフォールバックする。
        @SuppressLint("MissingPermission")
        private fun buildRecord(bufSize: Int): AudioRecord? {
            // UNPROCESSED(無加工)→ MIC の順。VOICE_RECOGNITION は近接向け処理が入るため使わない。
            val sources = intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
            for (src in sources) {
                val r = runCatching {
                    AudioRecord(
                        src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufSize
                    )
                }.getOrNull()
                if (r != null && r.state == AudioRecord.STATE_INITIALIZED) return r
                r?.release()
            }
            return null
        }

        private fun readLoop(rec: AudioRecord, bufSize: Int) {
            val buf = ByteArray(bufSize)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                emitLevel(buf, n)
                val s = synchronized(sinkLock) { sink } ?: continue
                try {
                    s.write(buf, 0, n)
                } catch (e: IOException) {
                    // 読み手(認識器)がまだこのパイプを開いていない/閉じた。次のセッションのパイプを待つ。
                    synchronized(sinkLock) { if (sink === s) sink = null }
                }
            }
        }

        private fun emitLevel(buf: ByteArray, n: Int) {
            var sum = 0L
            var i = 0
            while (i + 1 < n) {
                val sample = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)
                sum += abs(sample.toShort().toInt())
                i += 2
            }
            val avg = if (n >= 2) sum.toDouble() / (n / 2) else 0.0
            onLevel((avg / 800.0).toFloat().coerceIn(0f, 12f))
        }

        /** 新しい認識セッション用に、書き込み先を張り替えて read 端を返す。失敗時 null。 */
        fun newSessionSource(): ParcelFileDescriptor? {
            if (!running) return null
            return try {
                val pipe = ParcelFileDescriptor.createPipe()
                synchronized(sinkLock) {
                    sink?.let { runCatching { it.close() } }
                    sink = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                }
                pipe[0]
            } catch (e: IOException) {
                null
            }
        }

        fun stop() {
            running = false
            thread?.let { runCatching { it.join(500) } }
            thread = null
            synchronized(sinkLock) {
                sink?.let { runCatching { it.close() } }
                sink = null
            }
            record?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            record = null
        }
    }

    private companion object {
        /** セッション間の待機。前セッションのteardownと重ならないようにする。 */
        const val RESTART_DELAY_MS = 80L

        /** 実エラーがこの回数を超えて連続したら録音を打ち切る。 */
        const val MAX_CONSECUTIVE_ERRORS = 5

        /** 認識セッションの開始/終了音が乗りやすいストリーム。録音中だけミュートする。 */
        val BEEP_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION
        )
    }
}
