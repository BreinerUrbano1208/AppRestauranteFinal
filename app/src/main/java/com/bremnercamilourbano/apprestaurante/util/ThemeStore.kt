package com.bremnercamilourbano.apprestaurante.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Modo de la app: claro, oscuro o automático (sigue al celular). Se recuerda entre aperturas. */
object ThemeStore {
    private const val PREFS = "restapp_settings"
    private const val KEY = "theme_mode"

    const val AUTO = 0
    const val LIGHT = 1
    const val DARK = 2

    fun mode(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, AUTO)

    private fun delegateMode(mode: Int) = when (mode) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** Se llama al abrir la app. */
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(delegateMode(mode(context)))
    }

    /** Guarda el modo y lo aplica (la pantalla se redibuja sola). */
    fun set(context: Context, mode: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(delegateMode(mode))
    }
}
