package com.dddheroes.heroesofddd.astrologers.write

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MonthWeekTest {

    @Test
    fun `week 1 is valid`() {
        val monthWeek = MonthWeek(month = 1, week = 1)
        assertEquals(1, monthWeek.week)
    }

    @Test
    fun `week 4 is valid`() {
        val monthWeek = MonthWeek(month = 1, week = 4)
        assertEquals(4, monthWeek.week)
    }

    @Test
    fun `week 0 is invalid`() {
        assertThrows<IllegalArgumentException> {
            MonthWeek(month = 1, week = 0)
        }
    }

    @Test
    fun `week 5 is invalid`() {
        assertThrows<IllegalArgumentException> {
            MonthWeek(month = 1, week = 5)
        }
    }

    @Test
    fun `negative week is invalid`() {
        assertThrows<IllegalArgumentException> {
            MonthWeek(month = 1, week = -1)
        }
    }

    @Test
    fun `month 0 is invalid`() {
        assertThrows<IllegalArgumentException> {
            MonthWeek(month = 0, week = 1)
        }
    }

    @Test
    fun `negative month is invalid`() {
        assertThrows<IllegalArgumentException> {
            MonthWeek(month = -1, week = 1)
        }
    }

    @Test
    fun `weekNumber for month 1 week 1 is 1`() {
        assertEquals(1, MonthWeek(month = 1, week = 1).weekNumber)
    }

    @Test
    fun `weekNumber for month 1 week 4 is 4`() {
        assertEquals(4, MonthWeek(month = 1, week = 4).weekNumber)
    }

    @Test
    fun `weekNumber for month 2 week 1 is 5`() {
        assertEquals(5, MonthWeek(month = 2, week = 1).weekNumber)
    }

    @Test
    fun `weekNumber for month 3 week 3 is 11`() {
        assertEquals(11, MonthWeek(month = 3, week = 3).weekNumber)
    }

}
