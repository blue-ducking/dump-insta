package com.meta.instagram.nodoomscroll.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBudgetTest {

    private val config = PolicyConfig(maxReelsPerDay = 3, cooldownAfterLimitMinutes = 15)

    @Test fun openingConsumesBudgetAndRegistersASingleViewer() {
        val b = DailyBudget.today(config).onReelOpened("r1")
        assertEquals(1, b.reelsOpened)
        assertEquals("r1", b.activeViewer)
        assertEquals(2, b.remaining())
        assertNull(b.onReelClosed().activeViewer)
    }

    @Test fun budgetResetsOnANewDay() {
        val yesterday = (DailyBudget.today(config).dayOfYear + 1) % 366
        assertFalse(DailyBudget.sameDay(yesterday))
    }

    @Test fun lockExpiresAfterCooldown() {
        val now = 1_700_000_000_000L
        val locked = DailyBudget.today(config).engageLock(now)
        assertTrue(locked.isLocked(now))
        assertFalse(locked.isLocked(now + 16 * 60_000L))
    }

    @Test fun storedBudgetSurvivesARoundTrip() {
        val store: BudgetStore = FakeBudgetStore()
        store.write(StoredBudget(dayOfYear = 100, reelsOpened = 2, lockedUntilEpochMs = 5L))
        val read = requireNotNull(store.read())
        assertEquals(StoredBudget(100, 2, 5L), read)
    }
}
