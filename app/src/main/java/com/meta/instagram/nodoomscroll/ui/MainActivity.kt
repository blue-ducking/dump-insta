package com.meta.instagram.nodoomscroll.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.meta.instagram.nodoomscroll.R
import com.meta.instagram.nodoomscroll.data.SessionStore
import com.meta.instagram.nodoomscroll.guard.GuardManager
import com.meta.instagram.nodoomscroll.guard.LockdownActivity
import com.meta.instagram.nodoomscroll.guard.NoScrollWebView
import com.meta.instagram.nodoomscroll.policy.PrefsBudgetStore

/**
 * Three tabs only: Home feed, Inbox (Direct), and a Reels tab that is a dead end.
 * The guarded WebView renders instagram.com; vertical swiping is impossible and
 * the infinite surfaces are unreachable. Login happens on instagram.com itself,
 * so no Meta developer credentials are needed and we never see the password.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: NoScrollWebView
    private lateinit var session: SessionStore
    private lateinit var guard: GuardManager
    private var blockedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = SessionStore(this)
        guard = GuardManager(this, PrefsBudgetStore(this))

        web = findViewById(R.id.web)
        findViewById<TextView>(R.id.tab_home).setOnClickListener { navigate("/") }
        findViewById<TextView>(R.id.tab_inbox).setOnClickListener { navigate("/direct/") }
        findViewById<TextView>(R.id.tab_reels).setOnClickListener { showReelsDeadEnd() }

        web.guardManager = guard
        web.onBlockedAttempt = { kind, _ -> onBlocked(kind, "") }
        web.webViewClient = object : NoScrollWebView.GuardWebViewClient(web) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                detectLoggedIn(url)
                updateBudgetChip()
            }
        }

        findViewById<View>(R.id.btn_back_to_inbox).setOnClickListener {
            findViewById<View>(R.id.reels_dead_end).visibility = View.GONE
            navigate("/direct/")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ensurePushAccess()

        val startPath = intent?.getStringExtra(EXTRA_START_PATH) ?: session.lastPath
        navigate(startPath)

        // Back gesture walks history but can never leave into a forbidden surface.
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") finish()
        }
    }

    private fun navigate(path: String) {
        if (guard.isLocked() && path != "/direct/") {
            launchLockdown(LockdownActivity.REASON_BUDGET)
            return
        }
        session.lastPath = path
        web.loadInstagram(path)
        updateBudgetChip()
    }

    /** The Reels tab exists to say "no". It never loads /reels. */
    private fun showReelsDeadEnd() {
        findViewById<View>(R.id.reels_dead_end).visibility = View.VISIBLE
    }

    private fun onBlocked(kind: String, url: String) {
        blockedCount++
        runOnUiThread {
            findViewById<TextView>(R.id.chip_guard).text =
                getString(R.string.guard_blocked, kind)
            when {
                kind == "nav" || kind == "pagination" ->
                    launchLockdown(LockdownActivity.REASON_TAB)
                blockedCount == BLOCKING_TOAST_THRESHOLD ->
                    Toast.makeText(this, R.string.toast_no_scrolling, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchLockdown(reason: String) {
        startActivity(
            Intent(this, LockdownActivity::class.java)
                .putExtra(LockdownActivity.EXTRA_REASON, reason)
        )
    }

    private fun updateBudgetChip() {
        findViewById<TextView>(R.id.chip_guard).text =
            getString(R.string.guard_budget, guard.remainingReels())
    }

    /**
     * Instagram sets sessionid once authenticated. We use it as the login signal
     * without ever reading or storing the value ourselves beyond the cookie jar.
     */
    private fun detectLoggedIn(url: String?) {
        if (url == null || !url.contains("instagram.com")) return
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        val loggedIn = Regex("""(^|;\s*)sessionid=[^;]+""").containsMatchIn(cookies)
        if (loggedIn && !session.isLoggedIn) {
            session.isLoggedIn = true
            Toast.makeText(this, R.string.toast_logged_in, Toast.LENGTH_LONG).show()
            navigate("/direct/") // land in DMs, not the feed
        }
    }

    private fun ensurePushAccess() {
        if (!guard.dmPushPermissionGranted()) {
            findViewById<View>(R.id.push_prompt).visibility = View.VISIBLE
            findViewById<View>(R.id.btn_enable_push).setOnClickListener {
                guard.requestDmPushPermission()
            }
        } else {
            findViewById<View>(R.id.push_prompt).visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        ensurePushAccess()
        updateBudgetChip()
    }

    companion object {
        const val EXTRA_START_PATH = "start_path"
        private const val BLOCKING_TOAST_THRESHOLD = 3
    }
}
