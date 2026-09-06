package com.meetingnotes.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JapaneseHolidaysTest {

    @Test
    fun fixedHolidays() {
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 1, 1)))   // 元日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 5, 5)))   // こどもの日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 11, 3)))  // 文化の日
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.of(2026, 6, 15))) // 平日
    }

    @Test
    fun happyMonday() {
        // 2026年の成人の日 = 1月第2月曜 = 1/12
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 1, 12)))
        // 2026年のスポーツの日 = 10月第2月曜 = 10/12
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun equinoxes() {
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 3, 20)))  // 春分の日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 9, 23)))  // 秋分の日
    }

    @Test
    fun substituteHoliday() {
        // 2026/5/3(憲法記念日)は日曜 → 5/6 が振替休日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 5, 6)))
    }

    @Test
    fun nationalHolidayBetween() {
        // 2026/9/21(敬老の日・月)と 9/23(秋分の日・水)に挟まれた 9/22(火)は国民の休日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2026, 9, 22)))
    }
}
