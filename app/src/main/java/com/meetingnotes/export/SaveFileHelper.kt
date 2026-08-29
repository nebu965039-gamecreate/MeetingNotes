package com.meetingnotes.export

import android.content.Context
import android.net.Uri
import java.io.File

object SaveFileHelper {

    /** SAFの保存先ピッカーで選ばれたUriへ、生成済みファイルの内容をコピーする。 */
    fun copyToUri(context: Context, sourceFile: File, destinationUri: Uri) {
        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }
}
