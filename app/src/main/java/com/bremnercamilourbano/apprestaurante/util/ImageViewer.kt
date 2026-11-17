package com.bremnercamilourbano.apprestaurante.util

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bremnercamilourbano.apprestaurante.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target

/**
 * Muestra la foto en pantalla completa y en su tamaño original (sin recortar).
 * Se puede acercar con dos dedos, mover cuando está acercada y tocar una vez para cerrar.
 */
@SuppressLint("ClickableViewAccessibility")
fun showImageViewer(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

    val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = context.getString(R.string.detail_image_zoom)
    }
    Glide.with(context)
        .load(url)
        .override(Target.SIZE_ORIGINAL)
        .fitCenter()
        .placeholder(R.drawable.bg_placeholder)
        .error(R.drawable.bg_placeholder)
        .into(image)

    val root = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(
            image,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    var scale = 1f
    val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale = (scale * detector.scaleFactor).coerceIn(1f, 5f)
            image.scaleX = scale
            image.scaleY = scale
            if (scale == 1f) {
                image.translationX = 0f
                image.translationY = 0f
            }
            return true
        }
    })
    val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            dialog.dismiss()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale > 1f && !scaleDetector.isInProgress) {
                image.translationX -= dx
                image.translationY -= dy
            }
            return true
        }
    })
    root.setOnTouchListener { _, event ->
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        true
    }

    dialog.setContentView(root)
    dialog.show()
}
