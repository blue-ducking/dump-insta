package com.meta.instagram.nodoomscroll.guard

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import com.meta.instagram.nodoomscroll.R

/**
 * The "enforced" part of the product. Shown when:
 *  - the daily reel budget is exhausted (cooldown), or
 *  - the user tries to leave the guarded WebView for Reels/Explore, or
 *  - the official Instagram app is launched while the guard is armed.
 *
 * It is deliberately inescapable: back is swallowed, and it keeps itself on top.
 */
class LockdownActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots of the lock screen being bypassed via recents preview.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContentView(R.layout.activity_lockdown)
        findViewById<android.view.View>(R.id.btn_wait)?.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this) { /* swallow: no escape */ }
    }

    override fun onResume() {
        super.onResume()
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_BUDGET
        findViewById<android.widget.TextView>(R.id.lock_title)?.text =
            getString(R.string.lock_title, reasonMessage(reason))
    }

    private fun reasonMessage(reason: String): String = when (reason) {
        REASON_SWIPE -> getString(R.string.lock_reason_swipe)
        REASON_TAB -> getString(R.string.lock_reason_tab)
        else -> getString(R.string.lock_reason_budget)
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val REASON_BUDGET = "budget"
        const val REASON_SWIPE = "swipe"
        const val REASON_TAB = "tab"
    }
}
