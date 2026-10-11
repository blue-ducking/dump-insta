package com.meta.instagram.nodoomscroll.guard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.meta.instagram.nodoomscroll.policy.BudgetStore
import com.meta.instagram.nodoomscroll.policy.DailyBudget
import com.meta.instagram.nodoomscroll.policy.PolicyConfig
import com.meta.instagram.nodoomscroll.policy.StoredBudget

/**
 * Owns the persisted state that makes the limits *enforced* rather than advisory:
 * the daily reel counter, the cooldown lock, and the "guard armed" flag.
 */
class GuardManager(
    private val context: Context,
    private val store: BudgetStore,
    private val config: PolicyConfig = PolicyConfig(),
) {

    fun currentBudget(): DailyBudget {
        val stored = store.read()
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (stored == null || !DailyBudget.sameDay(stored.dayOfYear)) {
            return DailyBudget(today, 0, null, null, config)
        }
        return DailyBudget(stored.dayOfYear, stored.reelsOpened, null, stored.lockedUntilEpochMs, config)
    }

    /** @return true if the reel may open. Enforces origin + budget atomically. */
    fun tryOpenReel(reelId: String): Boolean {
        val b = currentBudget()
        val now = System.currentTimeMillis()
        if (b.isLocked(now) || b.exhausted()) {
            lockAndPersist(b, now)
            return false
        }
        val next = b.onReelOpened(reelId)
        persist(next)
        if (next.exhausted()) lockAndPersist(next, now)
        return true
    }

    fun closeReel() {
        persist(currentBudget().onReelClosed())
    }

    fun remainingReels(): Int = currentBudget().remaining()

    fun isLocked(): Boolean = currentBudget().isLocked(System.currentTimeMillis())

    /** True once the user granted Notification Listener access (needed for DM push). */
    fun dmPushPermissionGranted(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(context, com.meta.instagram.nodoomscroll.notify.DmNotificationListenerService::class.java)
        return enabled.contains(cn.flattenToString())
    }

    fun requestDmPushPermission() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun lockAndPersist(b: DailyBudget, now: Long) {
        persist(b.engageLock(now))
    }

    private fun persist(b: DailyBudget) {
        store.write(StoredBudget(b.dayOfYear, b.reelsOpened, b.lockedUntilEpochMs))
    }

}
