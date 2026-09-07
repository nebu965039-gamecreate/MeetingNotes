package com.meetingnotes.util

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 端末内の軽量診断ログ。外部送信はせず、ユーザーが「診断情報を共有」で明示的に出すときだけ使う。
 * 目的: クローズドテストで「録音が不安定」などの報告が来たときに、何が起きたかを再現なしで把握する。
 *
 * 記録するもの:
 *  - 録音セッションの結果(長さ・文字数・エラー種別と回数・正常終了か打ち切りか)
 *  - キャッチされなかった例外のスタックトレース(その後、既定ハンドラに委譲してクラッシュはそのまま)
 *
 * ファイルは 1 本のみ・上限 [MAX_BYTES]。超えたら古い行から捨てる。
 */
object DiagnosticsLog {

    private const val FILE_NAME = "diagnostics.log"
    private const val MAX_BYTES = 64 * 1024

    private val timestampFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    @Volatile private var file: File? = null
    private val lock = Any()

    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, FILE_NAME)
        installCrashHandler()
    }

    fun append(line: String) {
        val f = file ?: return
        val stamped = "${timestampFormatter.format(Instant.now())}  $line\n"
        synchronized(lock) {
            runCatching {
                f.appendText(stamped)
                if (f.length() > MAX_BYTES) trim(f)
            }
        }
    }

    fun readAll(): String {
        val f = file ?: return "(診断ログはまだありません)"
        return synchronized(lock) {
            runCatching { f.readText() }.getOrDefault("").ifBlank { "(診断ログはまだありません)" }
        }
    }

    fun clear() {
        val f = file ?: return
        synchronized(lock) { runCatching { f.writeText("") } }
    }

    private fun trim(f: File) {
        runCatching {
            val kept = f.readText().takeLast(MAX_BYTES / 2).substringAfter('\n', "")
            f.writeText(kept)
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                append("CRASH on ${thread.name}: ${throwable.stackTraceToString().take(4000)}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
