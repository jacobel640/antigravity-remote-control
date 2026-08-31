package com.antigravity.remotecontrol.web

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.util.Log
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

/**
 * A WebView that implements NestedScrollingChild3 to coordinate scrolling with parents
 * like SwipeRefreshLayout or CoordinatorLayout.
 *
 * Pull-to-refresh is decided once per gesture, at the moment the finger lands, from state
 * that is already known. A gesture may hand its pull-down to the parent only if:
 *  - the WebView's own scroll position is at the top;
 *  - the page's last reported scroll state was "at top" (see [UIBridge]);
 *  - nothing has scrolled since the finger landed, and the finger has not moved upward;
 *  - the finger has travelled downward past a deliberate-pull threshold.
 *
 * Note what this deliberately does NOT do: ask the page anything during the gesture. The
 * page's answer arrives ~300ms late, long after the pull has been decided, so a gate that
 * waits for one simply never opens.
 */
class NestedScrollingWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild3 {

    private val childHelper = NestedScrollingChildHelper(this)
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)

    // Kept separate from the touch-path arrays: cancelling runs outside the touch stream.
    private val cancelOffset = IntArray(2)
    private val cancelConsumed = IntArray(2)
    private var lastY = 0
    private var nestedYOffset = 0
    private var velocityTracker: VelocityTracker? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // --- Per-gesture state ---------------------------------------------------

    /** Raw Y of the finger when the current gesture began. */
    private var startY = 0f

    /** True between ACTION_DOWN and ACTION_UP/ACTION_CANCEL. */
    private var isGestureActive = false

    /** Set once this gesture can no longer become a refresh, for any reason. */
    private var gestureDisqualified = false

    /** WebView scrollY at ACTION_DOWN, to detect native scrolling during the gesture. */
    private var gestureStartScrollY = 0

    // --- Cross-gesture state -------------------------------------------------

    /**
     * The page's most recent verdict on whether its content is scrolled to the top.
     * Defaults to true so that a page which never reports (no JS, blocked script) still
     * gets working pull-to-refresh from the native checks alone.
     */
    private var pageAtTop = true

    /** Last time the WebView's own scroll position was non-zero. */
    private var lastNonZeroScrollYTime = 0L

    /** Wall clock of the most recent touch, used to gate page-issued commands. */
    private var lastTouchTime = 0L

    init {
        // CRITICAL: Disable native nested scrolling to prevent super.onTouchEvent
        // from automatically triggering the parent SwipeRefreshLayout.
        isNestedScrollingEnabled = false
        // We re-enable it for our own helper but keep the WebView's internal one OFF.
        childHelper.isNestedScrollingEnabled = true
    }

    /**
     * Receives the page's latched scroll state.
     *
     * A "not at top" report also kills any gesture already in flight, so a container that
     * starts scrolling mid-pull cannot finish as a refresh.
     */
    fun updateBridgeConfirmation(atTop: Boolean, timestamp: Long) {
        pageAtTop = atTop
        if (!atTop && isGestureActive && !gestureDisqualified) {
            gestureDisqualified = true
            // This report can arrive mid-pull — the page only learns that it owns the
            // gesture once its own handler has run. Retract whatever the parent has
            // already accumulated so a spinner that started does not complete.
            cancelParentPull()
        }
    }

    /**
     * Unwinds any pull SwipeRefreshLayout has accumulated for the current gesture.
     *
     * A large positive dy pre-scroll is exactly what scrolling back up sends, so the
     * parent retracts its spinner through its own normal path rather than being poked.
     */
    private fun cancelParentPull() {
        if (!hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)) return
        cancelConsumed[0] = 0
        cancelConsumed[1] = 0
        dispatchNestedPreScroll(0, CANCEL_PULL_DY, cancelConsumed, cancelOffset, ViewCompat.TYPE_TOUCH)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t > 0) {
            lastNonZeroScrollYTime = System.currentTimeMillis()
        }
        // The WebView itself moved during this gesture: the content consumed the drag,
        // so this gesture is a scroll and not a pull-to-refresh.
        if (isGestureActive && t != gestureStartScrollY) {
            gestureDisqualified = true
        }
    }

    /**
     * Whether the user touched the WebView within [withinMs].
     *
     * The page can ask the app to open its settings, and the frame that hosts the button
     * is a different origin from the one the user configured, so origin cannot be the
     * test. A real finger can be: a frame cannot fake one, which is enough to stop a
     * hostile frame from popping the dialog on its own.
     */
    fun hasRecentUserGesture(withinMs: Long): Boolean {
        return lastTouchTime > 0L && (System.currentTimeMillis() - lastTouchTime) < withinMs
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastTouchTime = System.currentTimeMillis()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginGesture(ev)
            MotionEvent.ACTION_POINTER_DOWN -> gestureDisqualified = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun beginGesture(ev: MotionEvent) {
        isGestureActive = true
        gestureStartScrollY = scrollY
        startY = ev.y
        lastY = ev.y.toInt()

        // The decision is taken here, from what we already know. If the page was scrolled
        // when the finger landed, this whole gesture is a scroll — even if the content
        // reaches the top part-way through, which is exactly the reversal case that used
        // to fire a spurious refresh.
        gestureDisqualified = !pageAtTop

        if (gestureDisqualified) {
            Log.d(TAG, "Gesture starts disqualified: page reports content scrolled")
        }
    }

    private fun endGesture() {
        isGestureActive = false
        gestureDisqualified = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var returnValue = false

        val eventCopy = MotionEvent.obtain(event)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedYOffset = 0
        }

        eventCopy.offsetLocation(0f, nestedYOffset.toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                returnValue = super.onTouchEvent(event)
                lastY = event.y.toInt()
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                initVelocityTracker()
                velocityTracker?.addMovement(eventCopy)
                if (!returnValue) {
                    returnValue = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y.toInt()
                var deltaY = lastY - y

                // A drag that ever moves upward past slop is a scroll-down, and the rest
                // of that gesture must not be able to turn into a refresh.
                if (!gestureDisqualified && (startY - event.y) > touchSlop) {
                    gestureDisqualified = true
                }

                // If we are at the top and pulling down, we give the parent a chance to consume FIRST
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    deltaY -= scrollConsumed[1]
                    eventCopy.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedYOffset += scrollOffset[1]
                }

                lastY = y - scrollOffset[1]

                val oldScrollY = scrollY
                // Since we disabled nested scrolling on the WebView, this won't trigger the parent.
                super.onTouchEvent(eventCopy)
                val scrolledDeltaY = scrollY - oldScrollY
                var unconsumedY = deltaY - scrolledDeltaY

                // THE GATE: a leftover pull-down only reaches the parent when this whole
                // gesture qualifies as a deliberate pull from a resting top position.
                if (unconsumedY < 0 && !shouldAllowRefresh()) {
                    unconsumedY = 0
                }

                if (dispatchNestedScroll(0, scrolledDeltaY, 0, unconsumedY, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    lastY -= scrollOffset[1]
                    eventCopy.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedYOffset += scrollOffset[1]
                }

                velocityTracker?.addMovement(eventCopy)
                returnValue = true
            }
            MotionEvent.ACTION_UP -> {
                returnValue = super.onTouchEvent(event)
                if (returnValue) {
                    performClick()
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                recycleVelocityTracker()
            }
            MotionEvent.ACTION_CANCEL -> {
                returnValue = super.onTouchEvent(event)
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                recycleVelocityTracker()
            }
        }
        eventCopy.recycle()
        return returnValue
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    /**
     * Whether the current gesture may hand a pull-down to SwipeRefreshLayout.
     *
     * Every unknown resolves to "no refresh": a missed refresh costs the user a second
     * pull, a false one reloads the page under them.
     */
    private fun shouldAllowRefresh(): Boolean {
        // 1. The WebView's own scroll position must be at the very top.
        if (super.canScrollVertically(-1)) return false

        // 2. Something already ruled this gesture out: the page was scrolled when the
        //    finger landed, the content moved, the drag went upward, or a second finger.
        if (gestureDisqualified) return false

        // 3. No gesture in flight means nothing to authorise. This also keeps the
        //    canScrollVertically(-1) answer stable between gestures.
        if (!isGestureActive) return false

        // 4. The page must have settled at the top; a fling that just landed there does
        //    not count as a resting position.
        val now = System.currentTimeMillis()
        if (lastNonZeroScrollYTime > 0 && (now - lastNonZeroScrollYTime) < SETTLE_TIME_MS) {
            return false
        }

        // 5. The finger must have travelled DOWNWARD past the deliberate-pull threshold.
        //    An earlier version used abs() here, so a scroll-up satisfied it just as well
        //    as a pull-down.
        return (lastY - startY) > PULL_THRESHOLD_PX
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (direction < 0) {
            // SwipeRefreshLayout asks this to decide whether it may take the drag.
            // Reporting "I can scroll up" blocks it, which is the safe default.
            return !shouldAllowRefresh()
        }
        return super.canScrollVertically(direction)
    }

    // NestedScrollingChild implementation
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        // We handle this internally via childHelper to ensure native WV nested scroll stays OFF
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled
    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)
    override fun stopNestedScroll(type: Int) = childHelper.stopNestedScroll(type)
    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)
    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int, consumed: IntArray) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }
    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }
    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    private fun initVelocityTracker() {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        private const val TAG = "NestedScrollingWebView"

        /** How long the page must rest at scrollY == 0 before a pull counts. */
        private const val SETTLE_TIME_MS = 300L

        /** Downward finger travel required before a pull is treated as deliberate. */
        private const val PULL_THRESHOLD_PX = 60

        /** Larger than any spinner offset, so one pre-scroll drains the parent fully. */
        private const val CANCEL_PULL_DY = 100_000
    }
}
