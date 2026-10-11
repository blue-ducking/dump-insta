package com.meta.instagram.nodoomscroll.policy

/** Abstraction so the policy is testable without Android storage. */
interface BudgetStore {
    fun read(): StoredBudget?
    fun write(budget: StoredBudget)
}

data class StoredBudget(val dayOfYear: Int, val reelsOpened: Int, val lockedUntilEpochMs: Long?)

/** In-memory store used by tests and previews. */
class FakeBudgetStore(initial: StoredBudget? = null) : BudgetStore {
    private var value: StoredBudget? = initial
    override fun read(): StoredBudget? = value
    override fun write(budget: StoredBudget) { value = budget }
}
