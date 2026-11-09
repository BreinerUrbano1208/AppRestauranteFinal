package com.bremnercamilourbano.apprestaurante.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Roles {
    const val ADMIN = "admin"
    const val COCINA = "cocina"
}

data class Session(val token: String, val role: String) {
    val isAdmin: Boolean get() = role == Roles.ADMIN
}

/** Sesión del personal (cocina o administrador). Vive mientras la app esté abierta. */
object SessionStore {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun start(token: String, role: String) {
        _session.value = Session(token, role)
    }

    fun clear() {
        _session.value = null
    }
}
