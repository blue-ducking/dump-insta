package com.meta.instagram.nodoomscroll.guard;

/**
 * Single source of truth for the client-side anti-doomscroll injection.
 *
 * Why JS and not only Kotlin? Instagram for web is a React SPA: tapping the
 * Reels tab does NOT trigger a page load, so shouldOverrideUrlLoading never
 * fires. We must police the DOM from the inside.
 *
 * Enforced rules:
 *  1. Vertical swipe/paging is HARD-disabled (overflow:hidden + touchmove
 *     preventDefault), so the feed cannot be scrolled at all.
 *  2. Reels/Explore entry points are rewritten to /direct and their labels are
 *     hidden -> the Reels tab is unreachable.
 *  3. autoplay/loop attributes are stripped and programmatic play() is paused;
 *     'ended' resets instead of advancing -> no auto-advance to next reel.
 *  4. pushState/replaceState into /reels or /explore is bounced back to /direct.
 *  5. Feed pagination XHR/fetch (?max_id=) is refused -> no infinite refresh.
 *  6. Every violation is reported to Android via window.NoScrollBridge.
 */
public final class GuardJs {

    public static String build() {
        return "(function(){"
            + "if (window.__noDoomscrollInstalled) { return; }"
            + "window.__noDoomscrollInstalled = true;"
            + "var FORBIDDEN = ['/reels', '/explore', '/popular', '/accounts/reeldetached'];"
            + "var DIRECT = '/direct';"
            + "function notify(kind, url) {"
            + "  try { if (window.NoScrollBridge) { window.NoScrollBridge.onBlocked(kind, url || location.href); } } catch (e) {}"
            + "}"
            + "function forbidden(u) {"
            + "  u = String(u || '').toLowerCase();"
            + "  for (var i = 0; i < FORBIDDEN.length; i++) {"
            + "    var f = FORBIDDEN[i];"
            + "    if (u.indexOf(f) === 0 || u.indexOf(f + '/') !== -1) { return true; }"
            + "  }"
            + "  return false;"
            + "}"
            // 1. Hard-disable scrolling.
            + "function freezeScrolling() {"
            + "  var css = 'html,body{overflow:hidden !important;height:100% !important;"
            + "overscroll-behavior:none !important}'"
            + "    + '*[role=\"feed\"]{overflow:hidden !important;touch-action:none !important}';"
            + "  if (!document.getElementById('nd-css')) {"
            + "    var s = document.createElement('style'); s.id = 'nd-css'; s.textContent = css;"
            + "    (document.head || document.documentElement).appendChild(s);"
            + "  }"
            + "  if (document.documentElement) { document.documentElement.style.overflow = 'hidden'; }"
            + "  if (document.body) { document.body.style.overflow = 'hidden'; }"
            + "}"
            // 2. Remove Reels/Explore entry points.
            + "function killTabs() {"
            + "  var links = document.querySelectorAll('a[href]');"
            + "  for (var i = 0; i < links.length; i++) {"
            + "    var a = links[i], href = a.getAttribute('href') || '';"
            + "    if (forbidden(href)) {"
            + "      a.setAttribute('href', DIRECT + '/');"
            + "      a.setAttribute('aria-disabled', 'true');"
            + "      a.dataset.ndBlocked = '1';"
            + "    }"
            + "  }"
            + "  var labels = ['Reels', 'Explore', 'For you'];"
            + "  var nodes = document.querySelectorAll('[aria-label], span');"
            + "  for (var j = 0; j < nodes.length; j++) {"
            + "    var t = (nodes[j].getAttribute('aria-label') || nodes[j].textContent || '').trim();"
            + "    for (var k = 0; k < labels.length; k++) {"
            + "      if (t === labels[k]) { nodes[j].style.display = 'none'; break; }"
            + "    }"
            + "  }"
            + "}"
            // 3. Kill autoplay + prevent auto-advance.
            + "function tameVideos() {"
            + "  var vids = document.getElementsByTagName('video');"
            + "  for (var i = 0; i < vids.length; i++) {"
            + "    var v = vids[i];"
            + "    if (!v.dataset.ndTamed) {"
            + "      v.dataset.ndTamed = '1';"
            + "      v.autoplay = false;"
            + "      v.removeAttribute('autoplay');"
            + "      v.removeAttribute('loop');"
            + "      v.setAttribute('controls', 'controls');"
            + "      v.addEventListener('play', function () {"
            + "        if (!v.dataset.ndUserStarted) { v.pause(); notify('autoplay', location.href); }"
            + "      }, true);"
            + "      v.addEventListener('ended', function () {"
            + "        v.currentTime = 0; v.pause(); notify('ended', location.href);"
            + "      }, true);"
            + "    }"
            + "    if (!v.dataset.ndUserStarted && !v.paused) { v.pause(); }"
            + "  }"
            + "}"
            // A real tap on the video is the ONLY way playback may start.
            + "document.addEventListener('click', function (e) {"
            + "  var v = e.target && e.target.closest ? e.target.closest('video') : null;"
            + "  if (v) { v.dataset.ndUserStarted = '1'; }"
            + "}, true);"
            // 4. Block SPA navigation into Reels/Explore.
            + "function bounce(msg) {"
            + "  notify('nav', msg);"
            + "  if (history.replaceState) { history.replaceState({}, '', DIRECT + '/'); }"
            + "  freezeScrolling(); tameVideos(); killTabs();"
            + "}"
            + "var ps = history.pushState, rs = history.replaceState;"
            + "history.pushState = function (state, title, url) {"
            + "  if (forbidden(url)) { bounce(String(url)); return; }"
            + "  return ps.apply(history, arguments);"
            + "};"
            + "history.replaceState = function (state, title, url) {"
            + "  if (forbidden(url)) { bounce(String(url)); return; }"
            + "  return rs.apply(history, arguments);"
            + "};"
            // 5. Refuse feed pagination requests (infinite refresh).
            + "function paginating(u) {"
            + "  u = String(u || '').toLowerCase();"
            + "  return u.indexOf('max_id=') !== -1 || u.indexOf('p_ig_max_id=') !== -1;"
            + "}"
            + "var XO = XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open = function (m, u) {"
            + "  if (paginating(u)) { notify('pagination', String(u)); return; }"
            + "  return XO.apply(this, arguments);"
            + "};"
            + "if (window.fetch) {"
            + "  var origFetch = window.fetch;"
            + "  window.fetch = function (input) {"
            + "    var u = typeof input === 'string' ? input : (input && input.url);"
            + "    if (paginating(u)) { notify('pagination', String(u)); return Promise.reject(new Error('blocked')); }"
            + "    return origFetch.apply(window, arguments);"
            + "  };"
            + "}"
            // 6. Neutralise wheel/touch paging (composer stays usable for typing DMs).
            + "document.addEventListener('wheel', function (e) { e.preventDefault(); }, { passive: false });"
            + "document.addEventListener('touchmove', function (e) {"
            + "  var el = e.target;"
            + "  var inComposer = el && el.closest ? !!el.closest('textarea,[contenteditable=\"true\"],input') : false;"
            + "  if (!inComposer) { e.preventDefault(); }"
            + "}, { passive: false });"
            // 7. Watcher loop: Instagram re-renders constantly, so re-apply.
            + "function tick() {"
            + "  freezeScrolling(); killTabs(); tameVideos();"
            + "  if (forbidden(location.pathname)) { bounce(location.pathname); }"
            + "}"
            + "setInterval(tick, 400);"
            + "tick();"
            + "if (window.MutationObserver) {"
            + "  new MutationObserver(function () { tick(); })"
            + "    .observe(document.documentElement, { childList: true, subtree: true, attributes: true });"
            + "}"
            + "})();";
    }

    private GuardJs() {}
}
