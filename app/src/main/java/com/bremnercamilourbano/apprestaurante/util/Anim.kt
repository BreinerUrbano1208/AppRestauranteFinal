package com.bremnercamilourbano.apprestaurante.util

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

private fun View.dp(value: Float) = value * resources.displayMetrics.density

/** Aparece subiendo suavemente (fundido + desplazamiento). */
fun View.enterUp(delay: Long = 0L, distanceDp: Float = 22f, duration: Long = 420L) {
    alpha = 0f
    translationY = dp(distanceDp)
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(DecelerateInterpolator(1.6f))
        .start()
}

/** Aparece creciendo con un pequeño rebote. */
fun View.popIn(delay: Long = 0L, duration: Long = 520L) {
    alpha = 0f
    scaleX = 0.4f
    scaleY = 0.4f
    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(OvershootInterpolator(2.2f))
        .start()
}

/** Pulso corto: sirve para confirmar un toque (agregar al carrito, cambio en la insignia, etc.). */
fun View.bounce() {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate()
        .scaleX(1.25f)
        .scaleY(1.25f)
        .setDuration(110)
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(3f))
                .start()
        }
        .start()
}

/** Anima varias vistas una detrás de otra. */
fun staggerEnter(vararg views: View, start: Long = 0L, step: Long = 80L) {
    views.forEachIndexed { index, view -> view.enterUp(delay = start + index * step) }
}
