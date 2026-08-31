package com.antigravity.remotecontrol.nativebridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Lets the page keep the native side informed about whether a downward drag on it should
 * be allowed to become a pull-to-refresh.
 *
 * Several things make this necessary, and none of them are visible from native code:
 *
 *  1. The WebView's own scrollY stays at 0 on app-like pages that scroll inside
 *     `overflow: auto` containers rather than the document, so `canScrollVertically(-1)`
 *     always claims we are at the top.
 *  2. The Antigravity UI renders inside an **iframe**. `evaluateJavascript` and
 *     `addJavascriptInterface` only reach the main frame, so a main-frame-only detector
 *     sees no scroll events and no scrolled elements at all — it reports "at top"
 *     forever, which is what made pull-to-refresh fire during a scroll-up.
 *  3. Not every downward drag is a scroll. Dragging a bottom sheet down to dismiss it is
 *     a gesture the *page* owns; natively it is indistinguishable from a pull on a page
 *     that happens to be at the top.
 *
 * The detection script is therefore installed with `addDocumentStartJavaScript`, which
 * runs it in *every* frame, and reports back through a `WebMessageListener`, which is
 * reachable from subframes. [MainActivity] combines the per-frame answers.
 *
 * Reports are a *latch*, not a request/response. The page cannot answer a question inside
 * the lifetime of a touch gesture — its touch listeners are passive and coalesced, and an
 * `evaluateJavascript` round-trip on a mid-range device costs ~300ms, longer than the
 * whole decision window for a pull. What it can do is push state the moment it changes,
 * so the native side always has a recent answer on hand when a finger lands. Signals that
 * can only be known once the finger is already down (the page calling `preventDefault`)
 * still arrive in time to *cancel* a pull in flight.
 */
class UIBridge(private val onScrollStateReported: (Boolean, Long) -> Unit) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Legacy main-frame path, used only when WebMessageListener is unavailable. */
    @JavascriptInterface
    fun setSwipeRefreshEnabled(atTop: Boolean, timestamp: Long) {
        mainHandler.post {
            onScrollStateReported(atTop, timestamp)
        }
    }

    companion object {
        /** Name of the legacy `addJavascriptInterface` object (main frame only). */
        const val JAVASCRIPT_OBJ_NAME = "AndroidUIBridge"

        /** Name of the `WebMessageListener` object, which subframes can also reach. */
        const val MESSAGE_OBJ_NAME = "AndroidScrollBridge"

        /** Payload meaning "a pull here is allowed to refresh". */
        const val MSG_AT_TOP = "1"

        /** Payload asking the app to open its settings dialog. */
        const val MSG_OPEN_SETTINGS = "S"

        /** Prefix for "did this frame manage to place the settings button" reports. */
        const val MSG_BUTTON_PREFIX = "B:"

        fun getScrollDetectionScript(): String {
            return """
                (function() {
                    if (window._antigravityUIDetectorInjected) return;
                    window._antigravityUIDetectorInjected = true;

                    // The element that most recently scrolled. This is the whole trick:
                    // the original version kept a bare boolean that any unrelated scroll
                    // event could reset, so a stray event from an unscrolled element wiped
                    // out the knowledge that the real container was scrolled down.
                    var scroller = null;

                    // True while the page itself is driving the current gesture (it called
                    // preventDefault on a touchmove). Dragging a bottom sheet down looks
                    // exactly like a pull otherwise.
                    var pageOwnsGesture = false;

                    var modalCache = false;
                    var modalCacheAt = 0;

                    var lastSent = null;
                    var lastSentAt = 0;

                    // Looked up per call rather than captured: at document-start time the
                    // injected objects may not be attached yet.
                    function post(text) {
                        var msg = window.${'$'}{MSG_OBJ};
                        if (msg && msg.postMessage) {
                            try { msg.postMessage(text); return true; } catch (err) {}
                        }
                        return false;
                    }

                    function send(atTop) {
                        if (post(atTop ? '1' : '0')) return;
                        var legacy = window.${'$'}{JS_OBJ};
                        if (legacy && legacy.setSwipeRefreshEnabled) {
                            try { legacy.setSwipeRefreshEnabled(atTop, Date.now()); } catch (err) {}
                        }
                    }

                    function isScrollable(el) {
                        if (!el || el.nodeType !== 1) return false;
                        if ((el.scrollHeight - el.clientHeight) <= 1) return false;
                        var style;
                        try { style = window.getComputedStyle(el); } catch (err) { return false; }
                        if (!style) return false;
                        var oy = style.overflowY;
                        return oy === 'auto' || oy === 'scroll' || oy === 'overlay';
                    }

                    function documentScrollTop() {
                        var t = window.pageYOffset || 0;
                        if (document.scrollingElement) {
                            t = Math.max(t, document.scrollingElement.scrollTop || 0);
                        }
                        if (document.documentElement) {
                            t = Math.max(t, document.documentElement.scrollTop || 0);
                        }
                        if (document.body) {
                            t = Math.max(t, document.body.scrollTop || 0);
                        }
                        return t;
                    }

                    // A visible modal or bottom sheet means the content behind it is inert,
                    // and a downward drag belongs to the sheet, not to pull-to-refresh.
                    // Polled rather than computed on touch, because native has to know the
                    // answer at the instant the finger lands.
                    function modalOpen() {
                        var now = Date.now();
                        if (now - modalCacheAt < 200) return modalCache;
                        modalCacheAt = now;
                        modalCache = false;
                        try {
                            var nodes = document.querySelectorAll(
                                'dialog[open],[role=dialog],[role=alertdialog],[aria-modal=true]');
                            for (var i = 0; i < nodes.length; i++) {
                                if (nodes[i].getClientRects().length > 0) { modalCache = true; break; }
                            }
                        } catch (err) {}
                        return modalCache;
                    }

                    function isAtTop() {
                        if (pageOwnsGesture) return false;
                        if (modalOpen()) return false;
                        if (documentScrollTop() > 0) return false;
                        if (scroller) {
                            // A detached node keeps its last scrollTop forever; drop it.
                            if (scroller.isConnected === false) {
                                scroller = null;
                            } else if ((scroller.scrollTop || 0) > 0) {
                                return false;
                            }
                        }
                        return true;
                    }

                    function report(force) {
                        var value = isAtTop();
                        var now = Date.now();
                        if (!force && value === lastSent && (now - lastSentAt) < 400) return;
                        lastSent = value;
                        lastSentAt = now;
                        send(value);
                    }

                    // Scroll events do not bubble, but capture-phase listeners on window
                    // still see them from every element, including nested containers and
                    // elements inside shadow roots. This is the one page signal that
                    // arrives promptly and reliably, so the latch is built on it.
                    window.addEventListener('scroll', function(e) {
                        var t = e.target;
                        scroller = (t && t.nodeType === 1) ? t : null;
                        report(false);
                    }, true);

                    // Best effort refinement: if the finger came down over a scrollable
                    // container, that container is the one that matters for the next pull.
                    // composedPath() reaches through shadow roots; elementsFromPoint does not.
                    window.addEventListener('touchstart', function(e) {
                        pageOwnsGesture = false;
                        try {
                            var path = e.composedPath ? e.composedPath() : null;
                            if (path) {
                                for (var i = 0; i < path.length; i++) {
                                    if (isScrollable(path[i])) { scroller = path[i]; break; }
                                }
                            }
                        } catch (err) {}
                        report(true);
                    }, {passive: true, capture: true});

                    // Bubble phase, so the page's own handlers have already run. A passive
                    // listener may not call preventDefault, but it may still observe that
                    // someone else did — which is the page saying "this drag is mine".
                    window.addEventListener('touchmove', function(e) {
                        if (!pageOwnsGesture && e.defaultPrevented) {
                            pageOwnsGesture = true;
                            report(true);
                        }
                    }, {passive: true, capture: false});

                    function endGesture() {
                        if (pageOwnsGesture) {
                            pageOwnsGesture = false;
                            report(true);
                        }
                    }

                    window.addEventListener('touchend', endGesture, {passive: true, capture: true});
                    window.addEventListener('touchcancel', endGesture, {passive: true, capture: true});

                    // ---- Settings button -------------------------------------------
                    // The app's SSL/URL settings used to hang off a native floating button
                    // that covered the site's own logo. Injecting a real button into the
                    // site's top bar is nicer, but Antigravity is a third party: its markup
                    // can change on any deploy. So the bar is located by *shape* rather than
                    // by class names or structure, and if nothing plausible is found this
                    // frame says so and the native button comes back instead.

                    try {
                    var BTN_ID = '_antigravitySettingsBtn';
                    // Width kept clear at the top right for the parent frame's account widget.
                    var CORNER_RESERVE_PX = 60;
                    var placed = false;
                    var lastPlacementSent = null;

                    // '1' placed, '0' could not place, '2' deliberately not shown here.
                    // '0' and '2' differ in what the app does about it: only a failure is
                    // worth falling back to the native button for.
                    function reportPlacement(state) {
                        var payload = 'B:' + state;
                        if (lastPlacementSent === payload) return;
                        lastPlacementSent = payload;
                        post(payload);
                    }

                    // The conversation view has a crowded bar and does not need this button.
                    // Its route is the app's own /c/<id> segment; if that ever changes the
                    // button simply keeps showing, which is the harmless direction.
                    function isConversationView() {
                        try {
                            return /(^|\/)c\/[^/]+/.test(location.pathname);
                        } catch (err) { return false; }
                    }

                    // Deliberately loose. The instance-picker screen is an Angular app
                    // whose bar icons are neither <button> nor <a>, so a strict test for
                    // real controls rejected a perfectly good top bar.
                    function interactiveCount(el) {
                        try {
                            return el.querySelectorAll(
                                'button,a,[role=button],[role=link],input,select,[tabindex],' +
                                'svg,img,mat-icon,[class*=icon],[class*=btn]').length;
                        } catch (err) { return 0; }
                    }

                    // A frame that is mostly filled by a child frame is a shell: the bar the
                    // user sees belongs to the inner document, so let that frame place the
                    // button and stay out of its way. Otherwise both would place one.
                    function isShellFrame() {
                        try {
                            var frames = document.getElementsByTagName('iframe');
                            for (var i = 0; i < frames.length; i++) {
                                var r = frames[i].getBoundingClientRect();
                                if (r.width >= window.innerWidth * 0.7 &&
                                    r.height >= window.innerHeight * 0.7) {
                                    return true;
                                }
                            }
                        } catch (err) {}
                        return false;
                    }

                    // A top bar is an element pinned to the top of the viewport, spanning
                    // most of its width, short, and holding several controls. That shape is
                    // far more stable than any selector into someone else's component tree.
                    function findBar() {
                        var best = null;
                        var bestScore = -1;
                        var all;
                        try { all = document.body.querySelectorAll('*'); } catch (err) { return null; }
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            var r;
                            try { r = el.getBoundingClientRect(); } catch (err) { continue; }
                            if (r.top > 12 || r.top < -4) continue;
                            if (r.height < 32 || r.height > 96) continue;
                            if (r.width < window.innerWidth * 0.7) continue;
                            // One is enough: on the instance-picker screen the bar holds
                            // only the brand, because the account controls live in a
                            // separate Google widget that floats above it.
                            var n = interactiveCount(el);
                            if (n < 1) continue;
                            // Strongly prefer a horizontal flex row. Appending to a block
                            // container puts the button on its own line underneath the bar
                            // instead of alongside the existing controls.
                            var style;
                            try { style = window.getComputedStyle(el); } catch (err) { continue; }
                            var isRow = style.display.indexOf('flex') >= 0 &&
                                style.flexDirection.indexOf('row') === 0;
                            // Prefer the innermost qualifying element: that is the row of
                            // controls itself rather than an outer wrapper.
                            var score = (isRow ? 1000000 : 0) + n * 1000 - r.height;
                            if (el.contains(best)) score -= 500000;
                            if (score > bestScore) { bestScore = score; best = el; }
                        }
                        return best;
                    }

                    function parseRgb(value) {
                        var m = value && value.match(/rgba?\(([^)]+)\)/);
                        if (!m) return null;
                        var parts = m[1].split(',');
                        var alpha = parts.length > 3 ? parseFloat(parts[3]) : 1;
                        if (!(alpha > 0.1)) return null;
                        return [parseFloat(parts[0]), parseFloat(parts[1]), parseFloat(parts[2])];
                    }

                    // Picks ink that is actually visible against whatever the bar sits on,
                    // so the icon survives the app's light and dark themes alike.
                    function iconColor(bar) {
                        var el = bar;
                        var rgb = null;
                        while (el && !rgb) {
                            try { rgb = parseRgb(window.getComputedStyle(el).backgroundColor); } catch (err) {}
                            el = el.parentElement;
                        }
                        if (!rgb) rgb = [255, 255, 255];
                        var lum = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
                        return lum > 140 ? '#444746' : '#c4c7c5';
                    }

                    // Google's floating account widget lives in the *parent* frame and hangs
                    // over the top-right corner, so a button appended to the end of the bar
                    // renders underneath it and is invisible. The overlap cannot be detected
                    // from in here (elementFromPoint does not cross frames), so instead the
                    // button is walked left until it clears that reserved corner.
                    function fitButton(bar, btn) {
                        for (var guard = 0; guard < 8; guard++) {
                            var r;
                            try { r = btn.getBoundingClientRect(); } catch (err) { return; }
                            if (r.width === 0) return;
                            if (r.right <= window.innerWidth - CORNER_RESERVE_PX) return;
                            var prev = btn.previousElementSibling;
                            if (!prev) return;
                            try { bar.insertBefore(btn, prev); } catch (err) { return; }
                        }
                    }

                    function buildButton() {
                        var b = document.createElement('button');
                        b.id = BTN_ID;
                        b.type = 'button';
                        b.setAttribute('aria-label', 'App settings');
                        b.setAttribute('title', 'App settings');
                        b.style.cssText = 'all:unset;display:inline-flex;align-items:center;' +
                            'justify-content:center;width:32px;height:32px;flex:0 0 auto;' +
                            'cursor:pointer;border-radius:50%;color:inherit;opacity:0.75;' +
                            'align-self:center;margin:0 6px;-webkit-tap-highlight-color:transparent;';
                        // Built through DOM calls rather than innerHTML: the page enforces
                        // Trusted Types, which rejects raw HTML string assignment outright.
                        // currentColor keeps the icon in step with the site's own theme.
                        var NS = 'http://www.w3.org/2000/svg';
                        // Not currentColor: inside the app frame the inherited colour is not
                        // a visible ink colour, and the icon came out invisible.

                        var svg = document.createElementNS(NS, 'svg');
                        svg.setAttribute('viewBox', '0 0 24 24');
                        svg.setAttribute('width', '18');
                        svg.setAttribute('height', '18');
                        svg.setAttribute('fill', 'currentColor');
                        svg.setAttribute('aria-hidden', 'true');
                        svg.setAttribute('focusable', 'false');
                        var path = document.createElementNS(NS, 'path');
                        path.setAttribute('d', 'M12 1 3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 ' +
                            '9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z');
                        svg.appendChild(path);
                        b.appendChild(svg);
                        b.addEventListener('click', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            post('S');
                        }, true);
                        // The bar may sit inside a horizontal scroller; keep our own drags
                        // from being read as page gestures.
                        b.addEventListener('touchstart', function(e) { e.stopPropagation(); }, true);
                        return b;
                    }

                    function placeButton() {
                        var existing = document.getElementById(BTN_ID);
                        // Re-checked even when already placed: this frame may only become a
                        // shell later, once the app swaps in the frame that owns the real UI.
                        if (isShellFrame()) {
                            if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
                            placed = false;
                            reportPlacement('0');
                            return;
                        }
                        if (isConversationView()) {
                            if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
                            placed = false;
                            reportPlacement('2');
                            return;
                        }
                        if (existing && existing.isConnected) {
                            var svgNow = existing.firstChild;
                            if (svgNow && svgNow.setAttribute && existing.parentElement) {
                                svgNow.setAttribute('fill', iconColor(existing.parentElement));
                                fitButton(existing.parentElement, existing);
                            }
                            reportPlacement('1');
                            return;
                        }
                        var bar;
                        try { bar = findBar(); } catch (err) { bar = null; }
                        if (!bar) { placed = false; reportPlacement('0'); return; }
                        try {
                            var btn = existing && !existing.isConnected ? existing : buildButton();
                            bar.appendChild(btn);
                            if (btn.firstChild && btn.firstChild.setAttribute) {
                                btn.firstChild.setAttribute('fill', iconColor(bar));
                            }
                            fitButton(bar, btn);
                            placed = true;
                            reportPlacement('1');
                        } catch (err) {
                            placed = false;
                            reportPlacement('0');
                        }
                    }

                    // SPA re-renders drop foreign children, so re-place on DOM churn.
                    var placeQueued = false;
                    function queuePlace() {
                        if (placeQueued) return;
                        placeQueued = true;
                        setTimeout(function() { placeQueued = false; placeButton(); }, 300);
                    }

                    try {
                        new MutationObserver(queuePlace).observe(
                            document.documentElement, {childList: true, subtree: true});
                    } catch (err) {}
                    setInterval(placeButton, 2000);
                    queuePlace();
                    } catch (err) {
                        // Never let a failure here take the scroll detection down with it.
                    }



                    // Keeps the latch fresh when a frame scrolls or opens a sheet without
                    // firing an event we can see.
                    setInterval(function() { report(false); }, 250);
                    report(true);
                })();
            """.trimIndent()
                .replace("\${MSG_OBJ}", MESSAGE_OBJ_NAME)
                .replace("\${JS_OBJ}", JAVASCRIPT_OBJ_NAME)
        }
    }
}
