package com.meetingnotes.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.ln

/**
 * リモート会議モード用の録音。端末内の音声認識ではなく、音声をファイルに録って
 * あとでサーバー(Whisper)に送る。認識用マイク処理を避けるため、なるべく
 * 前処理の少ない `AudioSource` を選ぶ。
 *
 * 出力は Opus/Ogg(24kbps mono, 16kHz)。1時間で約10MBに収まる。
 */
class AudioFileRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var levelJob: Runnable? = null
    private var startedAtMs = 0L

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** 0f〜約12f。RecordingScreen の可視化バーが期待するスケールに合わせている。 */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    val isRecording: Boolean get() = recorder != null

    /** 録音開始。マイク権限が無い・初期化失敗なら false。 */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recorder != null) return true
        val file = File(context.cacheDir, "remote_meeting_${System.currentTimeMillis()}.ogg")
        val rec = buildRecorder() ?: return false
        return try {
            rec.setOutputFile(file.absolutePath)
            rec.setAudioSamplingRate(SAMPLE_RATE)
            rec.setAudioEncodingBitRate(BIT_RATE)
            rec.setAudioChannels(1)
            rec.prepare()
            rec.start()
            recorder = rec
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            _elapsedMs.value = 0L
            startLevelPolling()
            true
        } catch (e: Exception) {
            runCatching { rec.release() }
            file.delete()
            false
        }
    }

    private fun buildRecorder(): MediaRecorder? {
        val rec = MediaRecorder(context) // minSdk 33: Context 引数のコンストラクタが常に使える
        // 遠くの声を消しにくいソースを優先(UNPROCESSED → CAMCORDER → MIC)。
        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val ok = runCatching {
                rec.reset()
                rec.setAudioSource(src)
                rec.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            }.isSuccess
            if (ok) return rec
        }
        runCatching { rec.release() }
        return null
    }

    private fun startLevelPolling() {
        levelJob = object : Runnable {
            override fun run() {
                val r = recorder ?: return
                _elapsedMs.value = System.currentTimeMillis() - startedAtMs
                val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
                // maxAmplitude(0〜32767) を可視化用のおおまかなスケールへ。
                _audioLevel.value = if (amp <= 0) 0f
                else (ln(amp.toDouble()) / ln(32767.0) * 12.0).toFloat().coerceIn(0f, 12f)
                mainHandler.postDelayed(this, 120)
            }
        }
        mainHandler.post(levelJob!!)
    }

    /** 録音を停止し、音声ファイルを返す。失敗・空なら null。 */
    fun stopAndGetFile(): File? {
        levelJob?.let { mainHandler.removeCallbacks(it) }
        levelJob = null
        _audioLevel.value = 0f
        val rec = recorder ?: return outputFile?.takeIf { it.exists() && it.length() > 0 }
        recorder = null
        val file = outputFile
        val stopped = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        return if (stopped && file != null && file.exists() && file.length() > 0) file else {
            file?.delete()
            null
        }
    }

    /** 中止(ファイルは破棄)。 */
    fun cancel() {
        levelJob?.let { mainHandler.removeCallbacks(it) }
        levelJob = null
        _audioLevel.value = 0f
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 24_000
        const val MIME_TYPE = "audio/ogg"

        /** これを超えたら録音を促し停止させる目安(サーバー側の上限 12MB に対する安全余裕)。 */
        const val MAX_DURATION_MS = 45 * 60 * 1000L
    }
}
