package com.bremnercamilourbano.apprestaurante.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserDto(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val role: String?,
    val lastName: String? = null,
    val docType: String? = null,
    val docNumber: String? = null,
    val birthDate: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    /** Foto en formato "data:image/jpeg;base64,...". Null si no tiene. */
    val photo: String? = null,
    val acceptedData: Boolean = false
)

/** Respuesta de entrar o crear cuenta. [role] dice si es cliente, cajero, cocina o administrador. */
data class ClientAuthResponse(val token: String, val role: String?, val user: UserDto)

data class ClientRegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    /** Autorización de tratamiento de datos personales (Ley 1581 de 2012). */
    val acceptData: Boolean
)

/** Registro: con correo configurado en el servidor llega [needsVerification] (sin sesión) y hay que escribir el código. */
data class RegisterResponse(
    val token: String? = null,
    val role: String? = null,
    val user: UserDto? = null,
    val needsVerification: Boolean = false,
    val email: String? = null
)

data class VerifyRequest(val email: String, val code: String)
data class EmailRequest(val email: String)
data class ResetPasswordRequest(val email: String, val code: String, val password: String)
data class DeleteAccountRequest(val password: String)
data class OkResponse(val ok: Boolean = true)

data class ClientLoginRequest(val email: String, val password: String)

data class DeviceRequest(val token: String)

data class PushTestResponse(val devices: Int? = null, val dispositivos: Int = 0, val activo: Boolean = false, val enviados: Int = 0, val fallidos: Int = 0, val error: String? = null)

/** El perfil completo: lo que va vacío (null) se borra en el servidor. */
data class ProfileUpdateRequest(
    val name: String,
    val lastName: String?,
    val phone: String,
    val docType: String?,
    val docNumber: String?,
    val birthDate: String?,
    val address: String?,
    val neighborhood: String?,
    val city: String?,
    val photo: String?,
    val acceptData: Boolean
)

data class ClientSession(
    val token: String,
    val name: String,
    val email: String,
    val phone: String,
    val lastName: String? = null,
    val docType: String? = null,
    val docNumber: String? = null,
    val birthDate: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val photo: String? = null,
    val acceptedData: Boolean = false
)

/**
 * Sesión del cliente (cuenta). Se guarda en el teléfono para no pedir la clave cada vez que se abre la app.
 * Es distinta de [SessionStore], que es la del personal.
 */
object ClientStore {
    private var prefs: android.content.SharedPreferences? = null
    private val _session = MutableStateFlow<ClientSession?>(null)
    val session: StateFlow<ClientSession?> = _session.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("restapp_client", Context.MODE_PRIVATE)
        prefs = p
        val token = p.getString("token", null)
        if (token != null) {
            _session.value = ClientSession(
                token = token,
                name = p.getString("name", "").orEmpty(),
                email = p.getString("email", "").orEmpty(),
                phone = p.getString("phone", "").orEmpty(),
                lastName = p.getString("lastName", null),
                docType = p.getString("docType", null),
                docNumber = p.getString("docNumber", null),
                birthDate = p.getString("birthDate", null),
                address = p.getString("address", null),
                neighborhood = p.getString("neighborhood", null),
                city = p.getString("city", null),
                photo = p.getString("photo", null),
                acceptedData = p.getBoolean("acceptedData", false)
            )
        }
    }

    private fun save(token: String, user: UserDto) {
        prefs?.edit()
            ?.putString("token", token)
            ?.putString("name", user.name)
            ?.putString("email", user.email)
            ?.putString("phone", user.phone)
            ?.putString("lastName", user.lastName)
            ?.putString("docType", user.docType)
            ?.putString("docNumber", user.docNumber)
            ?.putString("birthDate", user.birthDate)
            ?.putString("address", user.address)
            ?.putString("neighborhood", user.neighborhood)
            ?.putString("city", user.city)
            ?.putString("photo", user.photo)
            ?.putBoolean("acceptedData", user.acceptedData)
            ?.apply()
        _session.value = ClientSession(
            token, user.name, user.email, user.phone, user.lastName, user.docType, user.docNumber,
            user.birthDate, user.address, user.neighborhood, user.city, user.photo, user.acceptedData
        )
    }

    fun start(token: String, user: UserDto) = save(token, user)

    /** Guarda los datos nuevos del perfil sin cerrar la sesión. */
    fun updateProfile(user: UserDto) {
        val current = _session.value ?: return
        save(current.token, user)
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _session.value = null
    }
}
