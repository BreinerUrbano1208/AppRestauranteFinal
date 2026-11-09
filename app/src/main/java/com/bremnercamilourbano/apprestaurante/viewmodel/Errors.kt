package com.bremnercamilourbano.apprestaurante.viewmodel

import com.bremnercamilourbano.apprestaurante.R
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

/** 401: no hay sesión válida (nunca entró o ya venció). */
fun Throwable.isUnauthorized() = this is HttpException && code() == 401

/** 403: hay sesión, pero el rol no tiene permiso para esa acción. */
fun Throwable.isForbidden() = this is HttpException && code() == 403

private fun HttpException.serverMessage(): String? = try {
    response()?.errorBody()?.string()
        ?.let { JsonParser.parseString(it).asJsonObject.get("error")?.asString }
} catch (e: Exception) {
    null
}

fun Throwable.toErrorKind(): ErrorKind = when (this) {
    is IOException -> ErrorKind.NETWORK
    is HttpException -> ErrorKind.SERVER
    else -> ErrorKind.UNKNOWN
}

/** Mensaje para mostrar al usuario según el tipo de fallo. */
fun Throwable.toUiMessage(): UiMessage = when {
    this is HttpException -> {
        val text = serverMessage()
        if (text != null) UiMessage(R.string.msg_server_error, listOf(text)) else UiMessage(R.string.error_server)
    }
    this is IOException -> UiMessage(R.string.msg_no_connection)
    else -> UiMessage(R.string.msg_unknown_error)
}
