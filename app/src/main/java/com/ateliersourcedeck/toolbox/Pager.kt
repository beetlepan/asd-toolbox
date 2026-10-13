package com.ateliersourcedeck.toolbox

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** 自製的橫向分頁容器：可手指拖動、也可程式指定頁面。position 是浮點數（1.5 = 在第 1、2 頁中間）。 */
class Pager(ctx: Context) : FrameLayout(ctx) {
    var position = 0f
        private set
    var onPosition: ((Float) -> Unit)? = null
    var onSettled: ((Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var startPage = 0
    private var dragging = false
    private var tracker: VelocityTracker? = null
    private var anim: ValueAnimator? = null

    fun setPages(views: List<View>) {
        removeAllViews()
        views.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        position = position.coerceIn(0f, (childCount - 1).coerceAtLeast(0).toFloat())
        layoutPages()
    }

    fun cancelAnim() {
        anim?.cancel()
        anim = null
    }

    fun setPosition(p: Float) {
        position = p.coerceIn(0f, (childCount - 1).coerceAtLeast(0).toFloat())
        layoutPages()
        onPosition?.invoke(position)
    }

    fun animateTo(target: Int) {
        cancelAnim()
        val t = target.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val a = ValueAnimator.ofFloat(position, t.toFloat())
        a.duration = 300
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener { setPosition(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onSettled?.invoke(t)
            }
        })
        anim = a
        a.start()
    }

    private fun layoutPages() {
        val w = width.toFloat()
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val d = i - position
            v.translationX = d * w
            v.visibility = if (abs(d) < 1f) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutPages()
    }

    private fun tryStartDrag(e: MotionEvent): Boolean {
        val dx = e.x - downX
        val dy = e.y - downY
        if (abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.5f) {
            dragging = true
            lastX = e.x
            startPage = position.roundToInt()
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return false
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                dragging = false
                cancelAnim()
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                if (!dragging && tryStartDrag(e)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.recycle()
                tracker = null
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    tryStartDrag(e)
                } else if (width > 0) {
                    val dx = e.x - lastX
                    lastX = e.x
                    setPosition(position - dx / width)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    var target = position.roundToInt()
                    val t = tracker
                    if (t != null) {
                        t.computeCurrentVelocity(1000)
                        val vx = t.xVelocity
                        if (abs(vx) > 900f) target = if (vx < 0) startPage + 1 else startPage - 1
                    }
                    animateTo(target)
                }
                dragging = false
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }
}
