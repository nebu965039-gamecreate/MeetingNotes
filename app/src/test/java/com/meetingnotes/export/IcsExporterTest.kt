package com.meetingnotes.export

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExporterTest {

    @Test
    fun `date-only next meeting becomes an all-day event`() {
        val ics = IcsExporter.buildIcs(
            meetingId = 1,
            meetingTitle = "契約の打ち合わせ",
            clientName = "テスト株式会社",
            nextMeetingDate = "2026-09-10",
            nextMeetingOriginalText = "来週の木曜"
        )
        assertNotNull(ics)
        ics!!
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260910"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260911"))
        assertTrue(ics.contains("SUMMARY:テスト株式会社 との打ち合わせ"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun `date-time next meeting becomes a one-hour timed event`() {
        val ics = IcsExporter.buildIcs(
            meetingId = 2,
            meetingTitle = "見積レビュー",
            clientName = null,
            nextMeetingDate = "2026-09-10T14:30",
            nextMeetingOriginalText = null
        )
        assertNotNull(ics)
        ics!!
        assertTrue(ics.contains("DTSTART:20260910T143000"))
        assertTrue(ics.contains("DTEND:20260910T153000"))
        assertTrue(ics.contains("SUMMARY:次回打ち合わせ(見積レビュー)"))
    }

    @Test
    fun `undetermined next meeting yields null`() {
        assertNull(
            IcsExporter.buildIcs(3, "件名", null, null, "また来週あたり")
        )
        assertNull(
            IcsExporter.buildIcs(3, "件名", null, "来週の水曜くらい", null)
        )
    }

    @Test
    fun `hasUsableDate matches buildIcs nullability`() {
        assertTrue(IcsExporter.hasUsableDate("2026-09-10"))
        assertTrue(IcsExporter.hasUsableDate("2026-09-10T14:00"))
        assertTrue(!IcsExporter.hasUsableDate(null))
        assertTrue(!IcsExporter.hasUsableDate(""))
        assertTrue(!IcsExporter.hasUsableDate("来週あたり"))
    }

    @Test
    fun `special characters in text values are escaped`() {
        val ics = IcsExporter.buildIcs(
            meetingId = 4,
            meetingTitle = "A;B,C",
            clientName = null,
            nextMeetingDate = "2026-09-10",
            nextMeetingOriginalText = null
        )!!
        assertTrue(ics.contains("SUMMARY:次回打ち合わせ(A\\;B\\,C)"))
    }
}
