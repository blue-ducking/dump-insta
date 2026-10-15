package com.meta.instagram.nodoomscroll.policy

import android.content.Context

/** SharedPreferences-backed store (plaintext is fine: this is a counter, not credentials). */
class PrefsBudgetStore(context: Context) : BudgetStore {
    private val prefs = context.getSharedPreferences("doomscroll_budget", Context.MODE_PRIVATE)

    override fun read(): StoredBudget? {
        if (!prefs.contains(KEY_DAY)) return null
        val locked = if (prefs.contains(KEY_LOCK)) prefs.getLong(KEY_LOCK, 0L) else null
        return StoredBudget(prefs.getInt(KEY_DAY, -1), prefs.getInt(KEY_COUNT, 0), locked)
    }

    override fun write(budget: StoredBudget) {
        prefs.edit()
            .putInt(KEY_DAY, budget.dayOfYear)
            .putInt(KEY_COUNT, budget.reelsOpened)
            .apply { budget.lockedUntilEpochMs?.let { putLong(KEY_LOCK, it) } ?: remove(KEY_LOCK) }
            .apply()
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_LOCK = "locked_until"
    }
}
