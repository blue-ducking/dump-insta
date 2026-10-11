package com.meta.instagram.nodoomscroll.guard

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.meta.instagram.nodoomscroll.policy.DoomscrollPolicy

/**
 * A WebView that physically cannot doomscroll. Defence in depth, three layers:
 *
 *  1. [onTouchEvent] - vertical swipes are consumed here and never reach the
 *     page. This is the HARD disable: even if all JS failed, you cannot scroll.
 *  2. [GuardJs] - injected at document-start (and re-injected on page finish),
 *     freezes overflow, removes Reels/Explore tabs, kills autoplay/auto-advance,
 *     blocks feed pagination requests.
 *  3. [GuardWebViewClient] - cancels navigations into forbidden surfaces before
 *     they render, and counts a DM-originated reel open through [GuardManager].
 */
class NoScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyleAttr) {

    private var downX = 0f
    private var downY = 0f
    private var swallowedGesture = false

    /** Blocked-attempt feedback for the UI (kind: swipe | nav | autoplay | pagination | ended). */
    var onBlockedAttempt: ((kind: String, url: String) -> Unit)? = null

    val policy = DoomscrollPolicy()
    var guardManager: GuardManager? = null

    init {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Engine-level autoplay kill switch (API 17+; NOT a WebSettings constant).
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
        }
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addJavascriptInterface(Bridge(), "NoScrollBridge")
        installDocumentStartScript()
    }

    private fun installDocumentStartScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            WebSettingsCompat.setDocumentStartScript(settings, setOf(GuardJs.build()))
        } catch (_: RuntimeException) {
            // Feature reported but unsupported by this WebView APK; page-finish injection covers us.
        }
    }

    /** Layer 1: no vertical gesture ever reaches the page. Taps still work. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; swallowedGesture = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = Math.abs(event.y - downY)
                val dx = Math.abs(event.x - downX)
                if (dy > SCROLL_SLOP && dy > dx) {
                    swallowedGesture = true
                    post { onBlockedAttempt?.invoke("swipe", url ?: "") }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swallowedGesture) {
                    swallowedGesture = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun loadInstagram(path: String) = loadUrl(INSTAGRAM_WEB + path)

    @SuppressLint("JavascriptInterface")
    private inner class Bridge {
        @JavascriptInterface
        fun onBlocked(kind: String, url: String) {
            post { onBlockedAttempt?.invoke(kind, url) }
        }
    }

    /** Layer 3 client: URL-level enforcement + reel-open accounting. */
    inner class GuardWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val u = request?.url?.toString() ?: return false
            if (policy.isForbiddenSurface(u)) {
                post { onBlockedAttempt?.invoke("nav", u) }
                return true
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val u = request?.url?.toString()
            if (u != null && policy.isForbiddenSurface(u) && !request.isForMainFrame) {
                // Sub-resource loads of the reels/explore data endpoints are refused too.
                post { onBlockedAttempt?.invoke("subresource", u) }
                return EMPTY
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            evaluateJavascript(GuardJs.build(), null)
            if (url != null && REEL_PATH.containsMatchIn(url)) {
                // Reached via a DM tap (the only way to get here); count it against the budget.
                guardManager?.tryOpenReel(url)
            } else {
                guardManager?.closeReel()
            }
        }
    }

    companion object {
        const val INSTAGRAM_WEB = "https://www.instagram.com"
        private const val SCROLL_SLOP = 18f
        private val REEL_PATH = Regex("/reel[s]?/[^/?#]+")
        private val EMPTY = WebResourceResponse("text/plain", "utf-8", null)
    }
}
