package com.bremnercamilourbano.apprestaurante.data

import kotlinx.coroutines.CancellationException

/** Ejecuta una llamada de red y devuelve Result, sin romper la cancelación de corrutinas. */
internal suspend inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
