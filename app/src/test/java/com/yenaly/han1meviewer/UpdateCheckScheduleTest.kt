package com.yenaly.han1meviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckScheduleTest {

    @Test
    fun firstCheckIsDue() {
        assertTrue(
            isUpdateCheckIntervalElapsed(
                nowEpochSeconds = 1_000_000,
                lastCheckEpochSeconds = 0,
                intervalDays = 7,
            )
        )
    }

    @Test
    fun recentSuccessfulCheckIsThrottled() {
        val day = 86_400L
        assertFalse(
            isUpdateCheckIntervalElapsed(
                nowEpochSeconds = 10 * day,
                lastCheckEpochSeconds = 9 * day,
                intervalDays = 7,
            )
        )
    }

    @Test
    fun expiredIntervalIsDueAgain() {
        val day = 86_400L
        assertTrue(
            isUpdateCheckIntervalElapsed(
                nowEpochSeconds = 17 * day,
                lastCheckEpochSeconds = 9 * day,
                intervalDays = 7,
            )
        )
    }

    @Test
    fun zeroIntervalAllowsEveryLaunchCheck() {
        assertTrue(
            isUpdateCheckIntervalElapsed(
                nowEpochSeconds = 1_000_000,
                lastCheckEpochSeconds = 999_999,
                intervalDays = 0,
            )
        )
    }
}
