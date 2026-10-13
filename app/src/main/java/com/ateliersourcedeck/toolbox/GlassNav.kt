package com.ateliersourcedeck.toolbox

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/**
 * 玻璃效果底部導覽列。
 * Android 12 以上：擷取導覽列後方的內容縮圖並模糊，疊上一層霧白漸層與細邊框（真實的毛玻璃）。
 * Android 11 以下：沒有模糊，改用較不透明的霧面底色。
 * 支援點擊、以及手指在導覽列上左右拖動來切換頁面。
 */
class GlassNav(ctx: Context, names: List<String>) : FrameLayout(ctx) {
    var source: View? = null
    var onTap: ((Int) -> Unit)? = null
    var onDrag: ((Float) -> Unit)? = null
    var onDragEnd: ((Float) -> Unit)? = null

    private val count = names.size
    private val blur = ImageView(ctx)
    private val tint = View(ctx)
    private val highlight = View(ctx)
    private val indicator = View(ctx)
    private val row = LinearLayout(ctx)
    private val labels = ArrayList<TextView>()
    private val argb = ArgbEvaluator()
    private val canBlur = Build.VERSION.SDK_INT >= 31
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var position = 0f
    private var snap: Bitmap? = null
    private var pending = false
    private var downX = 0f
    private var startPos = 0f
    private var dragging = false

    private val tabW: Float get() = if (width > 0) width.toFloat() / count else 1f

    init {
        val r = context.dp(26).toFloat()
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        elevation = context.dp(12).toFloat()

        blur.scaleType = ImageView.ScaleType.FIT_XY
        blur.visibility = if (canBlur) View.VISIBLE else View.GONE
        addView(blur, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (canBlur) applyBlur()

        val tintColors = if (canBlur)
            intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#12FFFFFF"))
        else
            intArrayOf(Color.parseColor("#F01E2428"), Color.parseColor("#F0131719"))
        tint.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, tintColors)
        addView(tint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        highlight.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#80FFFFFF"), Color.TRANSPARENT)
        )
        val hl = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1))
        hl.setMargins(context.dp(22), context.dp(1), context.dp(22), 0)
        addView(highlight, hl)

        val ind = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#4D39FF6B"), Color.parseColor("#2039FF6B"))
        )
        ind.cornerRadius = context.dp(20).toFloat()
        ind.setStroke(context.dp(1), Color.parseColor("#6639FF6B"))
        indicator.background = ind
        val il = LayoutParams(context.dp(60), LayoutParams.MATCH_PARENT)
        il.setMargins(0, context.dp(6), 0, context.dp(6))
        addView(indicator, il)

        row.orientation = LinearLayout.HORIZONTAL
        names.forEach { n ->
            val t = TextView(context)
            t.text = n
            t.textSize = 12f
            t.gravity = Gravity.CENTER
            t.setTextColor(C.MUTED)
            t.setLineSpacing(0f, 0.95f)
            labels.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val border = GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.cornerRadius = r
        border.setStroke(context.dp(1), Color.parseColor("#40FFFFFF"))
        foreground = border
    }

    private fun applyBlur() {
        try {
            blur.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP))
        } catch (e: Throwable) {
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val lp = indicator.layoutParams
        lp.width = (tabW - context.dp(8)).toInt().coerceAtLeast(1)
        indicator.layoutParams = lp
        updatePosition(position)
        refreshBlur()
    }

    fun updatePosition(p: Float) {
        position = p
        indicator.translationX = p * tabW + context.dp(4)
        labels.forEachIndexed { i, t ->
            val w = max(0f, 1f - abs(i - p))
            t.setTextColor(argb.evaluate(w, C.MUTED, C.GREEN) as Int)
        }
    }

    /** 重新擷取並模糊導覽列後方的畫面（合併同一幀內的多次呼叫）。 */
    fun refreshBlur() {
        if (!canBlur || pending) return
        val src = source ?: return
        pending = true
        postOnAnimation {
            pending = false
            try {
                val w = width
                val h = height
                if (w > 0 && h > 0) {
                    val s = 0.25f
                    val bw = (w * s).toInt().coerceAtLeast(1)
                    val bh = (h * s).toInt().coerceAtLeast(1)
                    val old = snap
                    val b: Bitmap
                    if (old == null || old.width != bw || old.height != bh) {
                        b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                        snap = b
                        blur.setImageBitmap(b)
                    } else {
                        b = old
                    }
                    val c = Canvas(b)
                    c.drawColor(C.BG)
                    c.scale(s, s)
                    c.translate(-left.toFloat(), -top.toFloat())
                    src.draw(c)
                    blur.invalidate()
                }
            } catch (e: Throwable) {
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                startPos = position
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.x - downX) > slop) dragging = true
                if (dragging) {
                    val p = (startPos + (e.x - downX) / tabW).coerceIn(0f, (count - 1).toFloat())
                    onDrag?.invoke(p)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onDragEnd?.invoke(position)
                } else {
                    val idx = (e.x / tabW).toInt().coerceIn(0, count - 1)
                    onTap?.invoke(idx)
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd?.invoke(position)
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
