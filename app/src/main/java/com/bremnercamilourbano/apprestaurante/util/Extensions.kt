package com.bremnercamilourbano.apprestaurante.util

import android.view.View
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bremnercamilourbano.apprestaurante.R
import com.bumptech.glide.Glide
import java.text.NumberFormat
import java.util.Locale

fun Double.toPrice(): String {
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 2
    return format.format(this)
}

fun ImageView.loadImage(url: String?) {
    Glide.with(this)
        .load(url?.takeIf { it.isNotBlank() })
        .placeholder(R.drawable.bg_placeholder)
        .error(R.drawable.bg_placeholder)
        .fallback(R.drawable.bg_placeholder)
        .centerCrop()
        .into(this)
}

fun View.applyStatusBarPadding() {
    val initialTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(top = initialTop + bars.top)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Carga la foto de perfil ("data:image/jpeg;base64,...") en círculo. Si es null, deja la imagen vacía. */
fun ImageView.loadAvatar(photo: String?) {
    val bytes = photo?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() }?.let {
        try { android.util.Base64.decode(it, android.util.Base64.DEFAULT) } catch (e: IllegalArgumentException) { null }
    }
    if (bytes == null) {
        Glide.with(this).clear(this)
        setImageDrawable(null)
    } else {
        Glide.with(this).load(bytes).circleCrop().into(this)
    }
}
