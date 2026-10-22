package br.com.pedrinho.amorcarrega

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val BRAZIL: Locale = Locale.forLanguageTag("pt-BR")
val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", BRAZIL)
fun LocalDate.friendly(): String = format(DAY_FORMAT)
class CareUi(val activity: Activity) {
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    fun color(id: Int) = ContextCompat.getColor(activity, id)
    val ink get() = color(R.color.care_text)
    val muted get() = color(R.color.care_muted)
    val accent get() = color(R.color.care_accent)
    val action get() = color(R.color.care_action)
    val soft get() = color(R.color.care_soft)
    val border get() = color(R.color.care_border)
    val good get() = color(R.color.care_good)
    fun shape(fill: Int, radius: Int = 20, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    fun column(padding: Int = 0) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun text(value: String, size: Float = 14f, tone: Int = ink, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(tone)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun space(height: Int = 12) = Space(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    fun card(): LinearLayout = column(18).apply {
        background = shape(color(R.color.care_surface), 22, border)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    fun button(label: String, filled: Boolean = true, action: () -> Unit) = Button(activity).apply {
        text = label; isAllCaps = false; textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) Color.WHITE else accent)
        background = shape(if (filled) this@CareUi.action else soft, 14)
        minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(14), dp(8), dp(14), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { action() }
    }
    fun badge(label: String, tone: Int = accent, fill: Int = soft) = text(label, 11f, tone, true).apply {
        background = shape(fill, 9); setPadding(dp(9), dp(5), dp(9), dp(5))
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }
    fun section(parent: LinearLayout, title: String, subtitle: String? = null) {
        parent.addView(space(10)); parent.addView(text(title, 21f, bold = true))
        if (subtitle != null) parent.addView(text(subtitle, 13f, muted))
        parent.addView(space(14))
    }
    fun field(parent: LinearLayout, label: String, value: String = "", multiline: Boolean = false,
              type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val title = text(label, 12f, muted, true)
        parent.addView(title)
        val input = EditText(activity).apply {
            id = View.generateViewId(); inputType = type or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            setText(value); textSize = 16f; setTextColor(ink); setHintTextColor(muted)
            background = shape(soft, 12, border); minHeight = dp(52)
            setPadding(dp(12), dp(12), dp(12), dp(12)); setSingleLine(!multiline)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(16) }
        }
        title.labelFor = input.id
        parent.addView(input)
        return input
    }
    fun empty(parent: LinearLayout, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
        parent.addView(card().apply {
            addView(text(title, 18f, bold = true)); addView(space(6)); addView(text(detail, 14f, muted))
            if (action != null) addView(button(action, action = onAction))
        })
    }
}
