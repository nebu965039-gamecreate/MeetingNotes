package com.meetingnotes.export

/** PDF/Word書き出しの共通コンテンツモデル。表示側(ui/common/MeetingSummarySections.kt)と項目構成を揃える。 */
sealed interface ExportBlock {
    data class Heading(val text: String) : ExportBlock
    data class Paragraph(val text: String) : ExportBlock
    data class BulletItem(val text: String) : ExportBlock
}
