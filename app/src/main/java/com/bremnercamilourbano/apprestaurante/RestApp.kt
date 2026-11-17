package com.bremnercamilourbano.apprestaurante

import android.app.Application
import com.bremnercamilourbano.apprestaurante.util.ThemeStore

class RestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeStore.apply(this)
    }
}
