package com.animevostbr.playback

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.media3.ui.PlayerView

/**
  * PlayerView with YouTube-style gestures:
  *  - a double tap on the left/right third seeks back/forward by [SEEK_STEP_MS];
  *  - each further tap on the same side within [SERIES_TIMEOUT_MS] adds another step;
  *  - a single tap still toggles the controller (delayed until a double tap is ruled out);
  *  - a two-finger pinch reports zoom in/out once per gesture (fill the screen / original size).
  *
  * Only touches outside controller buttons get here: buttons are child views and consume their own.
  */
@SuppressLint("ViewConstructor")
class GesturePlayerView(
    context: Context,
    /** Called on every seek step with the direction (-1/+1) and the total seeked in this tap series. */
    private val onSeekStep: (direction: Int, totalMs: Long) -> Unit,
    /** Called once per pinch gesture: true when the fingers spread apart. */
    private val onPinch: (zoomIn: Boolean) -> Unit
) : PlayerView(context) {

    private var seriesDirection = 0
    private var seriesTotalMs = 0L
    private var lastSeekTapAt = 0L
    /** UP of the second tap of a double tap: the seek already happened in onDoubleTap. */
    private var doubleTapUpPending = false
    /** The current gesture became a pinch: ignore it for taps until all fingers are up. */
    private var pinching = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var total = 1f
        private var reported = false

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            total = 1f
            reported = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            total *= detector.scaleFactor
            // switch as soon as the intent is clear, like YouTube, instead of waiting for the fingers to lift
            if (!reported && (total > ZOOM_IN_SCALE || total < ZOOM_OUT_SCALE)) {
                reported = true
                onPinch(total > 1f)
            }
            return true
        }
    })

    private fun inSeries(eventTime: Long) =
        seriesDirection != 0 && eventTime - lastSeekTapAt < SERIES_TIMEOUT_MS

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // A tap that continued a seek series must not toggle the controller
            if (e.eventTime - lastSeekTapAt < SERIES_TIMEOUT_MS || lastSeekTapAt > e.eventTime) return true
            performClick() // default PlayerView behaviour: toggle the controller
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val dir = zoneOf(e.x)
            if (dir == 0) {
                performClick()
                return true
            }
            if (!(inSeries(e.eventTime) && dir == seriesDirection)) {
                seriesTotalMs = 0
            }
            doubleTapUpPending = true
            seek(dir)
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (player == null) return false
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
        if (!pinching && (event.pointerCount > 1 || scaleDetector.isInProgress)) {
            pinching = true
            // drop the pending tap of the first finger
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            detector.onTouchEvent(cancel)
            cancel.recycle()
        }
        if (pinching) return true
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (doubleTapUpPending) {
                doubleTapUpPending = false
            } else if (inSeries(event.eventTime) && zoneOf(event.x) == seriesDirection) {
                // Series continuation: after a double tap every tap on the same side is another step
                seek(seriesDirection)
            }
        }
        detector.onTouchEvent(event)
        return true
    }

    /** Remote (←/→) seek step; consecutive steps in one direction accumulate like taps. */
    fun seekStep(dir: Int) {
        if (!(inSeries(android.os.SystemClock.uptimeMillis()) && dir == seriesDirection)) seriesTotalMs = 0
        seek(dir)
    }

    private fun seek(dir: Int) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + dir * SEEK_STEP_MS).coerceIn(0, duration))
        seriesDirection = dir
        seriesTotalMs += SEEK_STEP_MS
        lastSeekTapAt = android.os.SystemClock.uptimeMillis()
        onSeekStep(dir, seriesTotalMs)
    }

    /** -1 left third, +1 right third, 0 middle. */
    private fun zoneOf(x: Float): Int = when {
        x < width / 3f -> -1
        x > width * 2f / 3f -> 1
        else -> 0
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
        const val SERIES_TIMEOUT_MS = 800L
        private const val ZOOM_IN_SCALE = 1.15f
        private const val ZOOM_OUT_SCALE = 0.87f
    }
}
