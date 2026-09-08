package com.meetingnotes.export

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/**
 * Pro機能: PDFにパスワードを設定する。
 * PdfExporter が生成した PDF を PDFBox-Android で読み込み直し、AES-256 で暗号化して上書きする後処理方式。
 * 開くのにも権限変更にも同じパスワードを要求する(所有者パスワード = ユーザーパスワード)。
 */
object PdfPasswordProtector {

    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun protect(context: Context, file: File, password: String) {
        ensureInit(context)

        val document = PDDocument.load(file)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply {
                encryptionKeyLength = 256
            }
            document.protect(policy)
            document.save(tmp)
        } finally {
            document.close()
        }

        if (file.exists() && !file.delete()) {
            tmp.delete()
            error("既存ファイルの置き換えに失敗しました。")
        }
        if (!tmp.renameTo(file)) {
            error("暗号化済みPDFの保存に失敗しました。")
        }
    }
}
