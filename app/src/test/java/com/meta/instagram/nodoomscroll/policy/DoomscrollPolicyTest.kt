package com.meta.instagram.nodoomscroll.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomscrollPolicyTest {

    private val policy = DoomscrollPolicy()

    @Test fun reelsTabIsForbiddenSurface() {
        assertTrue(policy.isForbiddenSurface("https://www.instagram.com/reels/"))
        assertTrue(policy.isForbiddenSurface("https://www.instagram.com/reels/ABC123/"))
        assertTrue(policy.isForbiddenSurface("https://www.instagram.com/explore/"))
        assertTrue(policy.isForbiddenSurface("https://www.instagram.com/explore/tags/cat/?hl=en"))
    }

    @Test fun dmAndPostSurfacesStayReachable() {
        assertFalse(policy.isForbiddenSurface("https://www.instagram.com/direct/inbox/"))
        assertFalse(policy.isForbiddenSurface("https://www.instagram.com/p/Cxyz123/"))
        assertFalse(policy.isForbiddenSurface("https://www.instagram.com/"))
    }

    @Test fun reelFromFeedIsBlocked() {
        val d = policy.decideReelOpen(OpenOrigin.HOME_FEED, DailyBudget.today())
        assertEquals(ReelDecision.Blocked(Reason.NOT_FROM_A_DM), d)
    }

    @Test fun reelFromDmIsAllowedWithoutAutoplayOrAutoAdvance() {
        val d = policy.decideReelOpen(OpenOrigin.DM_THREAD, DailyBudget.today())
        assertTrue(d is ReelDecision.Allowed)
        d as ReelDecision.Allowed
        assertFalse("autoplay must be off", d.autoplay)
        assertFalse("auto-advance must be off", d.autoAdvanceToNext)
    }

    @Test fun onlyOneReelViewerAtATime() {
        val budget = DailyBudget.today().onReelOpened("reel-1")
        val d = policy.decideReelOpen(OpenOrigin.DM_THREAD, budget)
        assertEquals(ReelDecision.Blocked(Reason.ALREADY_WATCHING), d)
    }

    @Test fun dailyLimitIsEnforced() {
        val config = PolicyConfig(maxReelsPerDay = 2)
        val p = DoomscrollPolicy(config)
        var b = DailyBudget.today(config).onReelOpened("a").onReelClosed()
            .onReelOpened("b").onReelClosed()
        assertTrue(b.exhausted())
        assertEquals(ReelDecision.Blocked(Reason.DAILY_LIMIT_REACHED), p.decideReelOpen(OpenOrigin.DM_THREAD, b))
        assertEquals(0, b.remaining())
    }

    @Test fun feedNeverInfiniteScrolls() {
        assertFalse(policy.shouldLoadMoreFeed())
        assertFalse(policy.shouldAutoRefreshFeed())
        assertFalse(policy.shouldAutoplayVideo())
    }

    @Test fun cooldownLockEngagesAfterLimit() {
        val config = PolicyConfig(maxReelsPerDay = 1, cooldownAfterLimitMinutes = 30)
        val b = DailyBudget.today(config).onReelOpened("x")
        val now = 1_700_000_000_000L
        val locked = b.engageLock(now)
        assertTrue(locked.isLocked(now + 60_000))
        assertFalse(locked.isLocked(now + 31 * 60_000))
    }

    @Test fun budgetStoreRoundTrip() {
        val store: BudgetStore = FakeBudgetStore(StoredBudget(dayOfYear = 285, reelsOpened = 4, lockedUntilEpochMs = null))
        val read = store.read()!!
        assertEquals(4, read.reelsOpened)
        assertEquals(285, read.dayOfYear)
    }
}
