package com.ateliersourcedeck.toolbox

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

object C {
    val BG = Color.parseColor("#0A0C0E")
    val PANEL = Color.parseColor("#15181B")
    val BORDER = Color.parseColor("#2B3034")
    val INK = Color.parseColor("#ECEFF1")
    val MUTED = Color.parseColor("#8B93A0")
    val GREEN = Color.parseColor("#39FF6B")
    val GREEN_INK = Color.parseColor("#07130A")
    val GREEN_DIM = Color.parseColor("#2239FF6B")
    val AMBER = Color.parseColor("#FFD166")
    val RED = Color.parseColor("#FF5F56")
    val CONSOLE = Color.parseColor("#000000")
    val DIM = Color.parseColor("#55605A")
    val GLASS = Color.parseColor("#D9121618")
    val GLASS_EDGE = Color.parseColor("#33FFFFFF")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.shape(fill: Int, stroke: Int = 0, radiusDp: Int = 10): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

fun Context.label(
    text: String,
    size: Float = 14f,
    color: Int = C.INK,
    bold: Boolean = false,
    mono: Boolean = false
): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = size
    t.setTextColor(color)
    if (mono) t.typeface = Typeface.MONOSPACE
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.button(text: String, primary: Boolean = false, onClick: () -> Unit): TextView {
    val b = label(text, 13f, if (primary) C.GREEN_INK else C.INK, bold = primary)
    b.gravity = Gravity.CENTER
    b.setPadding(dp(14), dp(10), dp(14), dp(10))
    b.background = shape(if (primary) C.GREEN else C.PANEL, if (primary) C.GREEN else C.BORDER, 8)
    b.isClickable = true
    b.setOnClickListener { onClick() }
    return b
}

fun Context.card(): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = shape(C.PANEL, C.BORDER, 10)
    c.setPadding(dp(14), dp(14), dp(14), dp(14))
    return c
}

fun LinearLayout.addSpaced(v: View, bottomDp: Int = 10) {
    val lp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    lp.bottomMargin = context.dp(bottomDp)
    addView(v, lp)
}

/** 橫排多個等寬子元件。 */
fun Context.row(vararg views: View): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    views.forEachIndexed { i, v ->
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (i > 0) lp.leftMargin = dp(8)
        r.addView(v, lp)
    }
    return r
}
