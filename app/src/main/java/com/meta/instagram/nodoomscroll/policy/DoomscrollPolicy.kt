package com.meta.instagram.nodoomscroll.policy

/**
 * The brain of the app. Everything that could turn into a doomscroll loop is
 * decided here, in pure Kotlin, so it can be unit tested without a device.
 *
 * Rules (locked with the product owner):
 *  1. Vertical swipe / paging is HARD-disabled. The Reels tab and Explore feed
 *     are unreachable surfaces - we never mount them at all.
 *  2. A Reel may be opened ONLY from inside a DM thread, and it plays as a
 *     single inline video that STOPS at the end. No auto-advance to "next".
 *  3. Autoplay is off everywhere, and infinite feed refresh is off.
 */
class DoomscrollPolicy(private val config: PolicyConfig = PolicyConfig()) {

    /** True if the URL/tab must never be rendered inside the WebView. */
    fun isForbiddenSurface(url: String): Boolean {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return FORBIDDEN_PATHS.any { p ->
            path.endsWith(p) || path.contains("$p/")
        }
    }

    /**
     * Decide what happens when the user taps a link.
     * Only reels reached *from a DM thread* are allowed, and only one at a time.
     */
    fun decideReelOpen(origin: OpenOrigin, budget: DailyBudget): ReelDecision {
        if (origin != OpenOrigin.DM_THREAD) {
            return ReelDecision.Blocked(Reason.NOT_FROM_A_DM)
        }
        if (!config.reelsFromDmsAllowed) {
            return ReelDecision.Blocked(Reason.DISABLED_BY_POLICY)
        }
        if (budget.exhausted()) {
            return ReelDecision.Blocked(Reason.DAILY_LIMIT_REACHED)
        }
        if (budget.activeViewer != null) {
            // One reel viewer at a time: prevents "stacking" reels back to back.
            return ReelDecision.Blocked(Reason.ALREADY_WATCHING)
        }
        return ReelDecision.Allowed(autoplay = false, autoAdvanceToNext = false)
    }

    /** Any request that would page the feed further down. Always refused. */
    fun shouldLoadMoreFeed(): Boolean = false

    /** Feed refresh is allowed only as an explicit, single pull-to-refresh by the user. */
    fun shouldAutoRefreshFeed(): Boolean = false

    fun shouldAutoplayVideo(): Boolean = false

    companion object {
        /** Surfaces that constitute the infinite scroll. Never reachable. */
        val FORBIDDEN_PATHS = listOf(
            "/reels/", "/reels", "/explore/", "/explore",
            "/accounts/reeldetached", "/direct_v2/inbox", // IG's own reel inbox shortcut
        )
    }
}

data class PolicyConfig(
    val reelsFromDmsAllowed: Boolean = true,
    val maxReelsPerDay: Int = 10,
    val cooldownAfterLimitMinutes: Int = 60,
)

enum class OpenOrigin { HOME_FEED, INBOX_LIST, EXPLICIT_SEARCH, DM_THREAD }

sealed class ReelDecision {
    data class Allowed(val autoplay: Boolean, val autoAdvanceToNext: Boolean) : ReelDecision()
    data class Blocked(val reason: Reason) : ReelDecision()
}

enum class Reason { NOT_FROM_A_DM, DISABLED_BY_POLICY, DAILY_LIMIT_REACHED, ALREADY_WATCHING }
