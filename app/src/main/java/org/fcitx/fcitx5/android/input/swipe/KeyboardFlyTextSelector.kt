/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.swipe

import android.graphics.Rect
import android.view.MotionEvent
import kotlin.math.hypot
import timber.log.Timber

/**
 * Gesture state machine for the "keyboard fly-text" feature: physical-keyboard mode only, driven by
 * the keyboard surface's motion samples as the IME receives them on its own window
 * (`FcitxInputMethodService.installDecorMotionListener`).
 *
 * Gestures:
 *  - **Up-swipe** (vertical-dominant, upward): pick the candidate whose on-screen column the finger
 *    is over — resolved by [candidateIndexAtX] against live [candidateRectsProvider] rects.
 *  - **Down-swipe** (while [bilingualProvider]): commit the pinyin/latin preedit, or dismiss a
 *    prediction strip. Off: a no-op (no "scroll candidates" gesture).
 *  - **Left / right swipe** (horizontal-dominant): page candidates. Left = next page, right = previous.
 *  - **Two-finger left / right** (while [bilingualProvider]): switch IME. Takes over as soon as a
 *    second pointer appears, so it cannot page or pick. Devices that only ever report one pointer
 *    simply never fire this path.
 *  - **Corner-delete**: a swipe that STARTS inside the keyboard surface's top-right corner
 *    ([cornerRegionProvider]) and travels clearly leftward acts as Backspace ([onDelete]). The corner
 *    is reserved — a contact that begins there never pages or picks a candidate, so a graze near the
 *    physical backspace key cannot mangle the candidate strip. This is the gesture's primary mis-touch
 *    filter; the typing guard and the commit slop below are additional belt-and-braces.
 *  - **Cursor-move** (only when there are no candidates, [cursorModeProvider]): the four-way swipe
 *    drives the text caret — [onCursor] with the dominant [SwipeDirection]. Uses the longest commit
 *    slop ([SWIPE_CURSOR_SLOP_DP]) so a graze can't shove the caret; the corner reservation still
 *    applies, so a corner-left swipe deletes rather than moving left. Once the entry swipe fires,
 *    the gesture switches to CONTINUOUS DRAG: every [SWIPE_CURSOR_STEP_SLOP_DP] of travel from the
 *    last fire point advances the caret again (with `isDragStep=true`), live, until the finger
 *    lifts — trackpad semantics, not one flick per gesture. A bilingual down-swipe that actually
 *    commits/dismisses wins over caret-down.
 *
 * Coordinates: candidate rects are absolute screen coordinates ([android.view.View.getLocationOnScreen]),
 * so the incoming [MotionEvent] must be tested against [MotionEvent.getRawX] / [MotionEvent.getRawY]
 * (NOT [MotionEvent.getX], which is relative to the receiving view). The provider is queried at
 * gesture-classify time so rects are always fresh, not a stale snapshot.
 *
 * Robustness: some touch sources stream MOVE but never UP, which would otherwise leave the state
 * machine latched and silently swallow the next gesture. The in-flight gesture therefore self-expires
 * after [EXPIRE_MS] of silence, and any fresh DOWN hard-resets — so a missing UP can never permanently
 * lock candidate selection.
 *
 * Pure function + callbacks: this class holds no Android context beyond what the caller injects, and
 * never forwards touches anywhere (the caller consumes them).
 */
class KeyboardFlyTextSelector(
    private val density: Float,
    private val candidateRectsProvider: () -> List<Pair<Int, Rect>>,
    private val onSelect: (Int) -> Unit,
    private val onPage: (Int) -> Unit,
    /**
     * Returns the top-right corner of the keyboard surface as a display-space [Rect] (the zone a
     * left swipe must START in to be read as Backspace), or null when the surface geometry is
     * unavailable on this device — in which case corner-delete is silently disabled and a left
     * swipe from anywhere just pages. Computed fresh per gesture so it tracks config/rotation.
     */
    private val cornerRegionProvider: () -> Rect?,
    /**
     * True while the corner-delete gesture is enabled (AppPrefs `keyboardFlyTextCornerDelete`). When
     * false, a left swipe from the corner is a normal page swipe — the corner is reserved only while
     * the user has opted into the delete gesture.
     */
    private val cornerDeleteEnabled: () -> Boolean,
    /** Fired once when a corner-started, clearly leftward swipe clears the commit slop. */
    private val onDelete: () -> Unit,
    /**
     * True while hardware keys are being hit (the caller compares the age of the last key event
     * against its configured guard window, AppPrefs `keyboardFlyTextGuardMs`). A surface contact
     * during typing is a graze between keystrokes, not a gesture — the single most reliable
     * mis-touch filter there is, because it uses a signal the graze cannot fake.
     */
    private val typingGuard: () -> Boolean = { false },
    /** Fly-text sensitivity in percent (50–150, from AppPrefs); 100 = untouched thresholds. */
    private val sensitivityProvider: () -> Int = { 100 },
    /**
     * True while the selector should drive the caret instead of paging/selecting: there are no
     * candidates on screen (and no open panel), so the four-way swipe moves the text cursor. The
     * caller arms this on the same master pref as the other gestures.
     */
    private val cursorModeProvider: () -> Boolean = { false },
    /**
     * Fired when the four-way cursor swipe clears the longer cursor commit slop
     * ([SWIPE_CURSOR_SLOP_DP]) — and then once per [SWIPE_CURSOR_STEP_SLOP_DP] of travel while the
     * finger stays down (continuous drag). The second parameter is true for drag steps (the caller
     * plays no click for those: the moving caret is the feedback, and a click per step at drag
     * rate is noise).
     */
    private val onCursor: (SwipeDirection, Boolean) -> Unit = { _, _ -> },
    /**
     * True while pinyin/English bilingual gestures are on (AppPrefs `keyboardFlyTextBilingual`).
     * Down-swipe then commits latin / dismisses prediction, and a two-finger horizontal swipe
     * switches IME. Off: down-swipe stays a no-op, two-finger is ignored so one-finger paging
     * still works if the surface ever reports two pointers.
     */
    private val bilingualProvider: () -> Boolean = { false },
    /**
     * Fired on a classified down-swipe while bilingual is on. Return true if the swipe was
     * consumed (latin committed or prediction dismissed); false leaves cursor-move free to run.
     */
    private val onDown: () -> Boolean = { false },
    /**
     * Fired on a classified two-finger horizontal swipe. [forward] is true for right (next IME),
     * false for left (previous IME).
     */
    private val onTwoFingerHorizontal: (forward: Boolean) -> Unit = {}
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    /** Once true the gesture is classified/consumed; further MOVEs are ignored until reset. */
    private var classified = false
    /**
     * Direction locked the instant the travel clears the base slop with a clearly dominant axis (via
     * [swipeAxis]). Held until reset so a wobble later in the stroke cannot reclassify an up into a
     * page or vice versa; the gesture only *fires* once [swipeDirection] also clears the higher
     * per-direction commit slop (the hysteresis that separates a deliberate swipe from a graze).
     */
    private var pendingDir: SwipeDirection? = null
    /**
     * Continuous cursor drag: once the entry swipe fired (travel ≥ [SWIPE_CURSOR_SLOP_DP] from the
     * DOWN point), the gesture bypasses the one-shot classification machine and tracks the finger
     * — every [SWIPE_CURSOR_STEP_SLOP_DP] of travel from ([cursorOriginX], [cursorOriginY]) fires
     * [onCursor] again with the dominant axis, re-originating at the fire point, until UP/CANCEL.
     * No direction lock and no reversal guard in drag: a trackpad follows the finger, including
     * back. Cleared by [reset].
     */
    private var cursorDragging = false
    private var cursorOriginX = 0f
    private var cursorOriginY = 0f
    /**
     * Set on a fresh DOWN whose coordinates fall inside the top-right corner zone (and only while
     * [cornerDeleteEnabled]). While true the in-flight gesture is reserved for Backspace: only a
     * clearly leftward swipe fires it, and up/down/right from the corner are no-ops. Reset on every
     * fresh DOWN / UP / CANCEL so the reservation never leaks across gestures.
     */
    private var cornerDeleteArmed = false
    /**
     * Second finger landed before this gesture classified. While true, MOVE uses the two-pointer
     * centroid and only [onTwoFingerHorizontal] can fire — one-finger page/select/cursor cannot.
     */
    private var twoFingerArmed = false
    private var lastEventTime = 0L

    /**
     * True while a finger is down on the keyboard surface (between DOWN and UP/CANCEL).
     *
     * The caller uses this to keep feeding onTouchEvent even after fly-text is disarmed (e.g. the
     * candidates vanished mid-swipe): a gesture already in flight must still see its UP/CANCEL, or
     * the latched state would misread the next gesture. A gesture that was already classified (or
     * was reset) counts as NOT active.
     */
    val gestureActive: Boolean get() = downTime != 0L

    /**
     * If a touch source only streams MOVE (no UP), the latched [classified] flag would otherwise
     * survive forever. Expire it after this quiet window so a fresh gesture starts clean.
     */
    private val EXPIRE_MS = 900L

    fun onTouchEvent(event: MotionEvent) {
        val t = event.eventTime
        // A long silence between events means the previous gesture was abandoned (no UP seen).
        // Cursor drags latch too (dragging holds state between MOVEs), so they expire the same way:
        // a finger resting still past the quiet window ends the drag, and the next move needs a
        // fresh DOWN.
        if ((classified || cursorDragging) && (t - lastEventTime) > EXPIRE_MS) reset()
        lastEventTime = t

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Typing guard: keys are still being hit, so this contact is a graze between
                // keystrokes — never arm a gesture from it.
                if (typingGuard()) {
                    Timber.d("FlyText: DOWN suppressed (typing)")
                    reset()
                    return
                }
                reset()
                downX = event.rawX
                downY = event.rawY
                downTime = t
                // Corner-delete is reserved for contacts that START in the surface's top-right
                // corner: a left swipe from there is Backspace, and nothing else fires from a corner
                // contact (so a stray brush near the backspace key can't page or pick a candidate).
                // The corner is the gesture's main mis-touch filter; the typing guard + commit slop
                // below are belt-and-braces. Disabled when the feature is off or the surface geometry
                // is unknown, in which case a left swipe from the corner just pages like anywhere else.
                cornerDeleteArmed = cornerDeleteEnabled() &&
                    cornerRegionProvider()?.contains(event.rawX.toInt(), event.rawY.toInt()) == true
                if (cornerDeleteEnabled()) {
                    val region = cornerRegionProvider()
                    Timber.i(
                        "FlyText: DOWN rawX=${event.rawX} rawY=${event.rawY} " +
                            "cornerDelete=${cornerDeleteArmed} region=${region}"
                    )
                }
                // Some surfaces deliver the second finger as part of the same DOWN. Treat it as
                // two-finger immediately so a two-thumb rest cannot page/select as one finger.
                if (bilingualProvider() && event.pointerCount >= 2) armTwoFinger(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (typingGuard()) {
                    Timber.d("FlyText: POINTER_DOWN suppressed (typing)")
                    reset()
                    return
                }
                if (!bilingualProvider() || classified || cursorDragging || downTime == 0L) return
                if (event.pointerCount < 2) return
                armTwoFinger(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (classified || downTime == 0L) return
                // Typing guard: a key press mid-gesture (or within the guard window of this MOVE)
                // means the finger is typing, not gesturing — drop the in-flight gesture entirely.
                if (typingGuard()) {
                    Timber.d("FlyText: gesture cancelled (typing)")
                    reset()
                    return
                }
                // Late two-finger: some drivers skip POINTER_DOWN and only raise pointerCount on
                // MOVE. Arm before one-finger classification so a second thumb cannot page/select.
                if (!twoFingerArmed && bilingualProvider() && event.pointerCount >= 2 && !cursorDragging) {
                    armTwoFinger(event)
                }
                if (twoFingerArmed) {
                    handleTwoFingerMove(event)
                    return
                }
                // Continuous cursor drag: handled BEFORE the one-shot classification machinery —
                // no direction lock, no reversal guard, no classified latch. The caret follows the
                // finger per step, re-originating at every fire, until the finger lifts.
                if (cursorDragging) {
                    if (!cursorModeProvider()) {
                        // Candidates appeared mid-drag: the caret is no longer this gesture's
                        // target. End the drag silently; the gesture still sees its own UP/CANCEL
                        // via gestureActive, so nothing latches.
                        cursorDragging = false
                        return
                    }
                    val dragD = density * flyTextSensitivityScale(sensitivityProvider())
                    val dragDx = event.rawX - cursorOriginX
                    val dragDy = event.rawY - cursorOriginY
                    val stepPx = SWIPE_CURSOR_STEP_SLOP_DP * dragD
                    if (kotlin.math.abs(dragDx) >= stepPx || kotlin.math.abs(dragDy) >= stepPx) {
                        // Dominant axis wins the step; ties lean horizontal (matches the entry
                        // swipe's left/right emphasis for caret movement).
                        val dir = if (kotlin.math.abs(dragDx) >= kotlin.math.abs(dragDy)) {
                            if (dragDx < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
                        } else {
                            if (dragDy < 0f) SwipeDirection.UP else SwipeDirection.DOWN
                        }
                        Timber.d("FlyText: cursor step dir=$dir (rawX=${event.rawX} rawY=${event.rawY})")
                        onCursor(dir, true)
                        cursorOriginX = event.rawX
                        cursorOriginY = event.rawY
                    }
                    return
                }
                // Sensitivity scales every slop uniformly: all thresholds are linear in density,
                // so scaling density scales base/up/page together (single mapping in SwipeGeometry).
                val d = density * flyTextSensitivityScale(sensitivityProvider())
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                // Reversal guard: the finger returned to the start (travel back under the base slop)
                // — this was a brush, not a swipe. Drop the locked direction so it cannot fire later.
                if (hypot(dx, dy) < SWIPE_BASE_SLOP_DP * d) {
                    pendingDir = null
                    return
                }
                // Lock the direction as soon as the travel is clearly one axis (below the higher
                // commit slop). From here the direction is fixed; a wobble cannot reclassify it.
                if (pendingDir == null) pendingDir = swipeAxis(dx, dy, d)
                val dir = pendingDir ?: return
                // Corner-delete path: a contact that began in the top-right corner is reserved for
                // Backspace. Only a clearly leftward swipe fires it; up/down/right from the corner are
                // no-ops (locked, but never page/select — protecting candidates from a corner graze).
                // The commit slop is the same horizontal one the page gestures use, scaled by the
                // fly-text sensitivity, so the delete threshold tracks the same mis-touch tuning.
                if (cornerDeleteArmed) {
                    if (swipeDirection(dx, dy, d) == SwipeDirection.LEFT) {
                        Timber.i("FlyText: corner-delete (rawX=${event.rawX})")
                        onDelete()
                        classified = true
                    }
                    return
                }
                // Cursor-move mode: no candidates on screen → the four-way swipe drives the caret
                // (UP/DOWN/LEFT/RIGHT). Uses the LONGEST commit slop ([SWIPE_CURSOR_SLOP_DP]) so a
                // graze across the bare keyboard surface can't shove the caret — the only mis-touch
                // guard this mode has, since it has no corner reservation and no select target.
                // Clearing it is also the DRAG-ENTRY gate: the first fire starts continuous
                // tracking, and the gesture stops being one-shot from here.
                if (cursorModeProvider()) {
                    val cursorSlopPx = SWIPE_CURSOR_SLOP_DP * d
                    val travel = when (dir) {
                        SwipeDirection.UP, SwipeDirection.DOWN -> kotlin.math.abs(dy)
                        SwipeDirection.LEFT, SwipeDirection.RIGHT -> kotlin.math.abs(dx)
                    }
                    if (travel >= cursorSlopPx) {
                        // Bilingual down-swipe (commit latin / dismiss) wins over caret-down when
                        // there is actually composing text or a prediction strip to act on.
                        if (dir == SwipeDirection.DOWN && bilingualProvider() && onDown()) {
                            Timber.i("FlyText: down-swipe (bilingual) in cursor mode")
                            classified = true
                            return
                        }
                        Timber.i("FlyText: cursor drag start dir=$dir (rawX=${event.rawX} rawY=${event.rawY})")
                        onCursor(dir, false)
                        // Enter drag: per-step advances from this point (handled at the top of the
                        // MOVE branch). classified stays false — the one-shot machine is bypassed
                        // for the rest of the gesture; reset() on UP/CANCEL clears the drag state.
                        cursorDragging = true
                        cursorOriginX = event.rawX
                        cursorOriginY = event.rawY
                    }
                    return
                }
                // Fire only once the travel also clears the *commit* slop for this direction — the
                // hysteresis that separates a deliberate swipe from a stray touch that merely grazed
                // the base slop. swipeDirection re-applies the same axis ratio + per-direction slop
                // the Lab page reports, so the read-out still matches what happens here.
                if (swipeDirection(dx, dy, d) != dir) return
                when (dir) {
                    SwipeDirection.UP -> {
                        val rects = candidateRectsProvider()
                        val idx = candidateIndexAtX(event.rawX, rects)
                        Timber.i("FlyText: up-swipe over ${rects.size} rects → idx=$idx (rawY=${event.rawY})")
                        if (idx >= 0) {
                            onSelect(idx)
                        }
                    }
                    SwipeDirection.DOWN -> {
                        if (bilingualProvider() && onDown()) {
                            Timber.i("FlyText: down-swipe (bilingual)")
                        } else {
                            Timber.i("FlyText: down-swipe (ignored)")
                        }
                    }
                    // Left swipe (dx < 0) = next page, right = previous (the swap pref is applied by
                    // the service).
                    SwipeDirection.LEFT -> {
                        Timber.i("FlyText: page dir=1 (rawX=${event.rawX})")
                        onPage(1)
                    }
                    SwipeDirection.RIGHT -> {
                        Timber.i("FlyText: page dir=-1 (rawX=${event.rawX})")
                        onPage(-1)
                    }
                }
                classified = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    /** Clear in-flight gesture state. Safe to call any time (e.g. on input finish). */
    fun reset() {
        downX = 0f
        downY = 0f
        downTime = 0L
        classified = false
        pendingDir = null
        cornerDeleteArmed = false
        twoFingerArmed = false
        cursorDragging = false
        cursorOriginX = 0f
        cursorOriginY = 0f
    }

    /**
     * Switch this in-flight gesture to two-finger IME-switch. Re-origins at the current centroid
     * so the first finger's travel does not count as a leftover one-finger page/select.
     */
    private fun armTwoFinger(event: MotionEvent) {
        twoFingerArmed = true
        cornerDeleteArmed = false
        pendingDir = null
        val (cx, cy) = centroidRaw(event)
        downX = cx
        downY = cy
        Timber.i("FlyText: two-finger armed pointers=${event.pointerCount}")
    }

    private fun handleTwoFingerMove(event: MotionEvent) {
        if (classified) return
        if (event.pointerCount < 2) return
        val d = density * flyTextSensitivityScale(sensitivityProvider())
        val (cx, cy) = centroidRaw(event)
        val dx = cx - downX
        val dy = cy - downY
        if (hypot(dx, dy) < SWIPE_BASE_SLOP_DP * d) {
            pendingDir = null
            return
        }
        if (pendingDir == null) pendingDir = swipeAxis(dx, dy, d)
        val dir = pendingDir ?: return
        if (swipeDirection(dx, dy, d) != dir) return
        when (dir) {
            SwipeDirection.LEFT, SwipeDirection.RIGHT -> {
                val forward = dir == SwipeDirection.RIGHT
                Timber.i("FlyText: two-finger ${if (forward) "right" else "left"} (IME)")
                onTwoFingerHorizontal(forward)
            }
            else -> Timber.i("FlyText: two-finger vertical (ignored)")
        }
        classified = true
    }

    /**
     * Display-space centroid of every pointer. Pointer 0 uses [MotionEvent.getRawX] /
     * [MotionEvent.getRawY]; further pointers are offset by the same view-to-raw delta so this
     * works below API 29 (no raw-coordinate overload taking a pointer index).
     */
    private fun centroidRaw(event: MotionEvent): Pair<Float, Float> {
        val n = event.pointerCount
        if (n <= 1) return event.rawX to event.rawY
        val dx = event.rawX - event.x
        val dy = event.rawY - event.y
        var x = 0f
        var y = 0f
        for (i in 0 until n) {
            x += event.getX(i) + dx
            y += event.getY(i) + dy
        }
        return x / n to y / n
    }
}
