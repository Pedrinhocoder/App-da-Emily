package br.com.pedrinho.amorcarrega

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Anel de bateria: arco proporcional ao nível, porcentagem no centro,
 * cor que muda conforme o nível e um raio pulsando enquanto carrega.
 */
class BatteryRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val COLOR_GREEN = 0xFF4CAF50.toInt()
        private const val COLOR_YELLOW = 0xFFFFB300.toInt()
        private const val COLOR_RED = 0xFFF44336.toInt()

        /** >= 50% verde, 20–49% amarelo, < 20% vermelho. */
        fun colorFor(level: Int): Int = when {
            level >= 50 -> COLOR_GREEN
            level >= 20 -> COLOR_YELLOW
            else -> COLOR_RED
        }
    }

    private val density = resources.displayMetrics.density

    private var initialized = false
    private var targetLevel = 0
    private var shownLevel = 0f
    private var ringColor = COLOR_RED
    private var charging = false
    private var full = false
    private var pulse = 0f

    private val rect = RectF()
    private val boltPath = Path()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.ring_track)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD)
        color = context.getColor(R.color.wine_text)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = context.getColor(R.color.muted_text)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var levelAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    /** Atualiza o anel. Chame sempre que a bateria mudar. */
    fun setBattery(level: Int, isCharging: Boolean, isFull: Boolean = false) {
        val lv = level.coerceIn(0, 100)
        val newColor = colorFor(lv)

        if (!initialized) {
            // Primeira leitura: o arco "enche" a partir de zero.
            initialized = true
            ringColor = newColor
            animateLevel(0f, lv.toFloat())
        } else {
            if (lv != targetLevel) animateLevel(shownLevel, lv.toFloat())
            if (newColor != colorFor(targetLevel)) animateColor(ringColor, newColor)
        }

        targetLevel = lv
        charging = isCharging
        full = isFull
        updatePulse()

        val estado = when {
            full -> "carregada"
            charging -> "carregando"
            else -> "descarregando"
        }
        contentDescription = "Bateria em $lv por cento, $estado"
        invalidate()
    }

    private fun animateLevel(from: Float, to: Float) {
        levelAnimator?.cancel()
        levelAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shownLevel = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateColor(from: Int, to: Int) {
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 500
            addUpdateListener {
                ringColor = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    /** O raio só pulsa enquanto está carregando e a view está na tela. */
    private fun updatePulse() {
        if (charging && isAttachedToWindow) {
            if (pulseAnimator?.isRunning == true) return
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            pulse = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        levelAnimator?.cancel()
        colorAnimator?.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (240 * density).toInt()
        val s = min(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * 0.075f
        val r = (size - stroke) / 2f

        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        arcPaint.color = ringColor
        rect.set(cx - r, cy - r, cx + r, cy + r)

        // Trilho + arco do nível (começa no topo, sentido horário).
        canvas.drawCircle(cx, cy, r, trackPaint)
        val sweep = 360f * shownLevel / 100f
        if (sweep > 0f) canvas.drawArc(rect, -90f, sweep, false, arcPaint)

        // Porcentagem no centro.
        textPaint.textSize = size * 0.25f
        val fm = textPaint.fontMetrics
        val baseline = cy + size * 0.03f - (fm.ascent + fm.descent) / 2f
        canvas.drawText("${shownLevel.roundToInt()}%", cx, baseline, textPaint)

        // Estado logo abaixo da porcentagem.
        labelPaint.textSize = size * 0.07f
        val label = when {
            full -> "Carregada"
            charging -> "Carregando"
            else -> "Na bateria"
        }
        canvas.drawText(label, cx, cy + size * 0.27f, labelPaint)

        // Raio animado acima da porcentagem.
        if (charging) drawBolt(canvas, cx, cy - size * 0.25f, size * 0.17f)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, bh: Float) {
        val bw = bh * 0.7f
        boltPath.reset()
        boltPath.moveTo(0.65f * bw, 0f)
        boltPath.lineTo(0.05f * bw, 0.55f * bh)
        boltPath.lineTo(0.45f * bw, 0.55f * bh)
        boltPath.lineTo(0.35f * bw, bh)
        boltPath.lineTo(0.95f * bw, 0.40f * bh)
        boltPath.lineTo(0.55f * bw, 0.40f * bh)
        boltPath.close()

        boltPaint.color = ringColor
        boltPaint.alpha = (255 * (0.6f + 0.4f * pulse)).toInt()

        val scale = 1f + 0.14f * pulse
        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-bw / 2f, -bh / 2f)
        canvas.drawPath(boltPath, boltPaint)
        canvas.restoreToCount(save)
    }
}
