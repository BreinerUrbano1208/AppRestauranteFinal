package com.bremnercamilourbano.apprestaurante.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.bremnercamilourbano.apprestaurante.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/** Avisos emergentes con estilo: cápsula oscura con ícono de color (verde = listo, rojo = error, azul = información). */
object Toasts {

    enum class Kind(@DrawableRes val icon: Int) {
        SUCCESS(R.drawable.ic_toast_success),
        ERROR(R.drawable.ic_toast_error),
        INFO(R.drawable.ic_toast_info)
    }

    private val ERROR_WORDS = listOf("error", "no_connection", "unknown", "expired", "required", "invalid", "fail")
    private val SUCCESS_WORDS = listOf("welcome", "updated", "sent", "done", "created", "deleted", "saved", "added", "ready")

    /** Decide el color según el nombre del texto (por ejemplo login_error_credentials -> rojo). */
    fun kindOf(view: View, @StringRes res: Int): Kind {
        val name = try { view.resources.getResourceEntryName(res) } catch (e: Exception) { "" }
        return when {
            ERROR_WORDS.any { name.contains(it) } -> Kind.ERROR
            SUCCESS_WORDS.any { name.contains(it) } -> Kind.SUCCESS
            else -> Kind.INFO
        }
    }

    fun show(root: View, text: String, kind: Kind, anchor: View? = null) {
        val snackbar = Snackbar.make(root, text, if (kind == Kind.ERROR) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_SLIDE
        if (anchor != null && anchor.isVisible) snackbar.setAnchorView(anchor)

        val density = root.resources.displayMetrics.density
        snackbar.view.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1F1F23"))
            cornerRadius = 22f * density
        }
        snackbar.view.elevation = 10f * density

        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 4
            gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(kind.icon, 0, 0, 0)
            compoundDrawablePadding = (12 * density).toInt()
        }
        snackbar.show()
    }

    fun show(root: View, @StringRes res: Int, args: List<Any> = emptyList(), anchor: View? = null) {
        show(root, root.context.getString(res, *args.toTypedArray()), kindOf(root, res), anchor)
    }

    fun error(root: View, @StringRes res: Int) = show(root, root.context.getString(res), Kind.ERROR)
}
