package com.felipe.conversornuvem

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class Pal(val night: Boolean) {
    private fun c(s: String) = Color.parseColor(s)
    val bg = c(if (night) "#0F1720" else "#EEF2F6")
    val card = c(if (night) "#18232E" else "#FFFFFF")
    val ink = c(if (night) "#E8EEF3" else "#15202B")
    val muted = c(if (night) "#93A1AE" else "#5B6875")
    val line = c(if (night) "#2A3947" else "#D3DBE3")
    val accent = c(if (night) "#7C94FF" else "#2446D8")
    val onAccent = c(if (night) "#0C1320" else "#FFFFFF")
    val ok = c(if (night) "#4CC08A" else "#1B7F52")
    val err = c(if (night) "#F07A72" else "#B83A33")
    val soft = Color.argb(if (night) 41 else 36, Color.red(accent), Color.green(accent), Color.blue(accent))
}

class Ui(val ctx: Context, val p: Pal) {
    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun shape(fill: Int, radius: Int, stroke: Int = 0, sw: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (sw > 0) setStroke(dp(sw), stroke)
        }

    fun ripple(d: GradientDrawable) = RippleDrawable(ColorStateList.valueOf(Color.argb(45, 128, 128, 128)), d, null)

    fun lp(top: Int = 0, w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    fun txt(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun label(t: String) = txt(t.uppercase(), 12f, p.muted, true).apply { letterSpacing = 0.08f }

    fun card(top: Int = 14) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(p.card, 20, p.line, 1)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = lp(top)
    }

    fun btn(texto: String, primary: Boolean, small: Boolean = false, top: Int = 12, click: () -> Unit) = TextView(ctx).apply {
        text = texto
        gravity = Gravity.CENTER
        textSize = if (small) 14f else 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(if (primary) p.onAccent else p.accent)
        background = ripple(if (primary) shape(p.accent, 14) else shape(Color.TRANSPARENT, 14, p.accent, 2))
        minHeight = dp(if (small) 44 else 52)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        isClickable = true
        setOnClickListener { click() }
        layoutParams = lp(top)
    }

    fun field(hint: String, value: String, password: Boolean = false, top: Int = 8) = EditText(ctx).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        textSize = 16f
        setTextColor(p.ink)
        setHintTextColor(p.muted)
        background = shape(p.bg, 12, p.line, 1)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        layoutParams = lp(top)
    }

    fun chip(texto: String, selected: Boolean, click: () -> Unit) = TextView(ctx).apply {
        text = texto
        gravity = Gravity.CENTER
        textSize = 15f
        minHeight = dp(48)
        setPadding(dp(10), dp(12), dp(10), dp(12))
        isClickable = true
        setOnClickListener { click() }
        mark(this, selected)
    }

    fun mark(v: TextView, selected: Boolean) {
        if (selected) {
            v.background = ripple(shape(p.soft, 12, p.accent, 2))
            v.setTextColor(p.accent)
            v.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        } else {
            v.background = ripple(shape(p.bg, 12, p.line, 1))
            v.setTextColor(p.ink)
            v.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    fun row(vararg views: View, gap: Int = 8, top: Int = 8) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = lp(top)
        for ((i, v) in views.withIndex()) {
            addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(gap) })
        }
    }

    fun screen(content: LinearLayout): ScrollView = ScrollView(ctx).apply {
        setBackgroundColor(p.bg)
        isFillViewport = true
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(18), dp(24), dp(18), dp(24))
        addView(content)
    }
}

fun EditText.onChange(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { block(s?.toString() ?: "") }
    })
}

/** Miniatura do ícone: mostra a imagem escolhida ou a inicial do nome. */
class TileView(ctx: Context, private val ui: Ui, private val sizeDp: Int, private val radiusDp: Int) : FrameLayout(ctx) {
    private val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val letra = ui.txt("M", sizeDp * 0.42f, ui.p.accent, true).apply { gravity = Gravity.CENTER }

    init {
        background = ui.shape(ui.p.soft, radiusDp, ui.p.line, 1)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, ui.dp(radiusDp).toFloat()) }
        }
        addView(img, LayoutParams(-1, -1))
        addView(letra, LayoutParams(-1, -1))
        layoutParams = LinearLayout.LayoutParams(ui.dp(sizeDp), ui.dp(sizeDp))
    }

    fun set(png: ByteArray?, nome: String) {
        if (png != null) {
            img.setImageBitmap(BitmapFactory.decodeByteArray(png, 0, png.size))
            img.visibility = View.VISIBLE; letra.visibility = View.GONE
        } else {
            img.setImageDrawable(null); img.visibility = View.GONE; letra.visibility = View.VISIBLE
            letra.text = (nome.trim().firstOrNull() ?: 'M').uppercaseChar().toString()
        }
    }
}

/** Barra de progresso com porcentagem animada ou modo indeterminado. */
class BarView(ctx: Context, private val track: Int, private val fill: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var shown = 0f
    private var phase = 0f
    private var move: ValueAnimator? = null
    var indeterminate = false
        set(v) {
            field = v
            if (v) {
                if (move == null) {
                    move = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 1200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                        start()
                    }
                }
            } else { move?.cancel(); move = null }
            invalidate()
        }

    fun setValue(v: Float) {
        val alvo = v.coerceIn(0f, 100f)
        ValueAnimator.ofFloat(shown, alvo).apply {
            duration = 350
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
        paint.color = track; rect.set(0f, 0f, w, h); c.drawRoundRect(rect, r, r, paint)
        paint.color = fill
        if (indeterminate) {
            val seg = w * 0.4f; val x = -seg + (w + seg) * phase
            rect.set(maxOf(0f, x), 0f, minOf(w, x + seg), h)
        } else rect.set(0f, 0f, w * shown / 100f, h)
        if (rect.width() > 0f) c.drawRoundRect(rect, r, r, paint)
    }

    override fun onDetachedFromWindow() { move?.cancel(); move = null; super.onDetachedFromWindow() }
}

/** Terminal com letras "digitadas" subindo, como numa compilação. */
class TerminalView(ctx: Context, private val ui: Ui) : FrameLayout(ctx) {
    private val bgc = Color.parseColor("#0B1118")
    private val scroll = ScrollView(ctx)
    private val box = LinearLayout(ctx)
    private val queue = ArrayDeque<Pair<String, Int>>()
    private var busy = false
    private val h = Handler(Looper.getMainLooper())
    private val rnd = java.util.Random()
    private val viewport = ui.dp(156)

    init {
        background = ui.shape(bgc, 12, ui.p.line, 1)
        layoutParams = ui.lp(14, -1, viewport)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(10))
        scroll.isVerticalScrollBarEnabled = false
        scroll.setOnTouchListener { _, _ -> true }
        scroll.addView(box)
        addView(scroll, LayoutParams(-1, -1))
        val fade = View(ctx)
        fade.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgc, Color.TRANSPARENT))
        addView(fade, LayoutParams(-1, ui.dp(44), Gravity.TOP))
        clipChildren = true
        resetBox()
    }

    private fun resetBox() {
        box.removeAllViews()
        box.addView(View(context), LinearLayout.LayoutParams(1, viewport))   // espaço: as linhas nascem embaixo
    }

    private fun color(cls: Int) = Color.parseColor(
        when (cls) {
            BuildState.C_OK -> "#4CC08A"; BuildState.C_ERR -> "#F07A72"
            BuildState.C_DIM -> "#6F8094"; BuildState.C_CMD -> "#8FA4FF"; else -> "#B7C4D1"
        }
    )

    private fun lineView(t: String, cls: Int) = TextView(context).apply {
        text = t; textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(color(cls))
    }

    private fun down() { scroll.post { scroll.smoothScrollTo(0, box.height) } }

    fun clear() { queue.clear(); busy = false; h.removeCallbacksAndMessages(null); resetBox() }

    fun restore(lines: List<Pair<String, Int>>) {
        clear()
        for (l in lines.takeLast(40)) box.addView(lineView(l.first, l.second))
        down()
    }

    fun add(text: String, cls: Int) { queue.add(Pair(text, cls)); if (!busy) pump() }

    private fun pump() {
        val item = queue.removeFirstOrNull()
        if (item == null) { busy = false; return }
        busy = true
        val tv = lineView("", item.second)
        box.addView(tv)
        while (box.childCount > 42) box.removeViewAt(1)
        val text = item.first
        val fast = queue.size > 5
        var i = 0
        val step = object : Runnable {
            override fun run() {
                i = minOf(text.length, i + if (fast) text.length else 2 + rnd.nextInt(3))
                tv.text = text.substring(0, i)
                down()
                if (i < text.length) h.postDelayed(this, 18)
                else h.postDelayed({ pump() }, if (fast) 0L else 120L + rnd.nextInt(160))
            }
        }
        h.post(step)
    }
}
