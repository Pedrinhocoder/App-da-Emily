package br.com.pedrinho.amorcarrega

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Coração decorativo com brilho suave que pulsa de leve (dois batimentos
 * curtos e uma pausa). Só anima enquanto está visível e respeita o ajuste
 * "remover animações" do sistema.
 */
class HeartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val heartPath = Path()
    private val heartBounds = RectF()
    private val shineRect = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }

    /** 0 = repouso, 1 = pico do batimento. */
    private var beat = 0f
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (84 * density).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // Curva paramétrica clássica do coração, normalizada para caber na view.
        heartPath.reset()
        val steps = 180
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val x = 16.0 * sin(t).pow(3)
            val y = -(13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t))
            if (i == 0) heartPath.moveTo(x.toFloat(), y.toFloat())
            else heartPath.lineTo(x.toFloat(), y.toFloat())
        }
        heartPath.close()
        heartPath.computeBounds(heartBounds, true)

        val scale = size * 0.58f / heartBounds.width()
        val m = Matrix().apply {
            postTranslate(-heartBounds.centerX(), -heartBounds.centerY())
            postScale(scale, scale)
            postTranslate(cx, cy)
        }
        heartPath.transform(m)
        heartPath.computeBounds(heartBounds, true)

        fillPaint.shader = LinearGradient(
            heartBounds.left, heartBounds.top, heartBounds.right, heartBounds.bottom,
            intArrayOf(0xFFFF7A9C.toInt(), 0xFFD81B60.toInt(), 0xFF8E1450.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        haloPaint.shader = RadialGradient(
            cx, cy, size * 0.5f,
            intArrayOf(0x66FF5C8A, 0x00FF5C8A),
            null,
            Shader.TileMode.CLAMP
        )

        // Brilho no lóbulo esquerdo.
        val bw = heartBounds.width()
        val bh = heartBounds.height()
        shineRect.set(
            heartBounds.left + bw * 0.17f, heartBounds.top + bh * 0.14f,
            heartBounds.left + bw * 0.35f, heartBounds.top + bh * 0.26f
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = 1f + 0.09f * beat

        haloPaint.alpha = (255 * (0.55f + 0.45f * beat)).toInt()
        canvas.drawCircle(cx, cy, min(width, height) * 0.5f, haloPaint)

        val save = canvas.save()
        canvas.scale(s, s, cx, cy)
        canvas.drawPath(heartPath, fillPaint)
        canvas.rotate(-30f, shineRect.centerX(), shineRect.centerY())
        canvas.drawOval(shineRect, shinePaint)
        canvas.restoreToCount(save)
    }

    /** Pico suave (cos²) de largura [width] centrado em [center]. */
    private fun bump(t: Float, center: Float, width: Float): Float {
        val d = abs(t - center)
        if (d >= width) return 0f
        val c = cos(d / width * (PI / 2)).toFloat()
        return c * c
    }

    private fun updateAnimation() {
        val shouldRun = isAttachedToWindow &&
                windowVisibility == VISIBLE &&
                ValueAnimator.areAnimatorsEnabled()

        if (shouldRun) {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    beat = maxOf(bump(t, 0.14f, 0.14f), 0.55f * bump(t, 0.36f, 0.12f))
                    invalidate()
                }
                start()
            }
        } else {
            animator?.cancel()
            animator = null
            if (beat != 0f) {
                beat = 0f
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
