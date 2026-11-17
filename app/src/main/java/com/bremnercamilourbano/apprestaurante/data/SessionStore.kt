package com.bremnercamilourbano.apprestaurante.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Roles {
    const val ADMIN = "admin"
    const val COCINA = "cocina"
    const val CAJERO = "cajero"
    const val CLIENTE = "cliente"
}

data class Session(val token: String, val role: String) {
    val isAdmin: Boolean get() = role == Roles.ADMIN
    val isCajero: Boolean get() = role == Roles.CAJERO

    /** Cajero y administrador pueden cancelar, entregar y gestionar mesas. */
    val canManage: Boolean get() = isAdmin || isCajero
}

/** Sesión del personal (cocina, cajero o administrador). Se guarda para no pedir la clave en cada apertura. */
object SessionStore {
    private var prefs: android.content.SharedPreferences? = null
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("restapp_staff", Context.MODE_PRIVATE)
        prefs = p
        val token = p.getString("token", null)
        val role = p.getString("role", null)
        if (token != null && role != null) _session.value = Session(token, role)
    }

    fun start(token: String, role: String) {
        prefs?.edit()?.putString("token", token)?.putString("role", role)?.apply()
        _session.value = Session(token, role)
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _session.value = null
    }
}
