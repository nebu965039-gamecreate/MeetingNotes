package com.meetingnotes.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TodoDueDateTest {

    // 2026-09-08 は火曜日
    private val today = LocalDate.of(2026, 9, 8)

    @Test fun iso() = assertEquals("2026-09-15", TodoDueDate.parse("2026-09-15", today))

    @Test fun slash() = assertEquals("2026-09-15", TodoDueDate.parse("9/15までに送付", today))

    @Test fun jpMonthDay() = assertEquals("2026-09-20", TodoDueDate.parse("9月20日", today))

    @Test fun slashPastRollsToNextYear() =
        assertEquals("2027-01-05", TodoDueDate.parse("1/5", today))

    @Test fun today_() = assertEquals("2026-09-08", TodoDueDate.parse("本日中に", today))

    @Test fun tomorrow() = assertEquals("2026-09-09", TodoDueDate.parse("明日まで", today))

    @Test fun dayAfterTomorrow() = assertEquals("2026-09-10", TodoDueDate.parse("明後日", today))

    @Test fun thisWeekFriday() = assertEquals("2026-09-11", TodoDueDate.parse("金曜日まで", today))

    @Test fun nextWeekMonday() = assertEquals("2026-09-14", TodoDueDate.parse("来週月曜", today))

    @Test fun undecidedIsNull() {
        assertNull(TodoDueDate.parse("未定", today))
        assertNull(TodoDueDate.parse("なるべく早く", today))
        assertNull(TodoDueDate.parse("来週", today)) // 曜日なしは特定不可
        assertNull(TodoDueDate.parse("", today))
        assertNull(TodoDueDate.parse(null, today))
    }
}
