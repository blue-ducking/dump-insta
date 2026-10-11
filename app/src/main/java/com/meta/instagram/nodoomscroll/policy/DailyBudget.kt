package com.meta.instagram.nodoomscroll.policy

import java.util.Calendar

/**
 * Enforced daily counter for reels opened from DMs. Persisted via [BudgetStore].
 * `activeViewer` guarantees only one reel player can exist at a time, which is
 * what makes "watch one more" impossible without an explicit new tap.
 */
data class DailyBudget(
    val dayOfYear: Int,
    val reelsOpened: Int,
    val activeViewer: String?,
    val lockedUntilEpochMs: Long?,
    private val config: PolicyConfig,
) {
    fun exhausted(): Boolean = reelsOpened >= config.maxReelsPerDay

    fun remaining(): Int = (config.maxReelsPerDay - reelsOpened).coerceAtLeast(0)

    fun onReelOpened(reelId: String): DailyBudget =
        copy(reelsOpened = reelsOpened + 1, activeViewer = reelId)

    fun onReelClosed(): DailyBudget = copy(activeViewer = null)

    /** Called when the budget runs out; starts the cooldown lock. */
    fun engageLock(nowMs: Long): DailyBudget =
        copy(lockedUntilEpochMs = nowMs + config.cooldownAfterLimitMinutes * 60_000L)

    fun isLocked(nowMs: Long): Boolean =
        lockedUntilEpochMs?.let { it > nowMs } == true

    companion object {
        fun today(config: PolicyConfig = PolicyConfig()): DailyBudget {
            val cal = Calendar.getInstance()
            return DailyBudget(cal.get(Calendar.DAY_OF_YEAR), 0, null, null, config)
        }

        fun sameDay(storedDay: Int): Boolean =
            Calendar.getInstance().get(Calendar.DAY_OF_YEAR) == storedDay
    }
}
