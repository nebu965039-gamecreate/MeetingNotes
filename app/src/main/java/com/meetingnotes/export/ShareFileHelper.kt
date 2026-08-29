package com.meetingnotes.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareFileHelper {

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startChooser(context, intent, chooserTitle)
    }

    fun sharePlainText(context: Context, text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startChooser(context, intent, chooserTitle)
    }

    private fun startChooser(context: Context, intent: Intent, chooserTitle: String) {
        val chooser = Intent.createChooser(intent, chooserTitle)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
