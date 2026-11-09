package com.bremnercamilourbano.apprestaurante.util

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val LOCALE_CO: Locale = Locale.forLanguageTag("es-CO")

/** Fecha y hora en la zona horaria del teléfono. Ej: 09/11 10:32 */
fun Long.toShortDateTime(): String =
    SimpleDateFormat("dd/MM HH:mm", LOCALE_CO).format(Date(this))

/** Fecha que devuelve el selector de rango (medianoche UTC) como AAAA-MM-DD, para el servidor. */
fun Long.toApiDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(this))

/** La misma fecha, para mostrarla en pantalla. Ej: 09/11/2026 */
fun Long.toDisplayDate(): String =
    SimpleDateFormat("dd/MM/yyyy", LOCALE_CO).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(this))

@StringRes
fun orderStatusLabel(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> R.string.status_recibido
    OrderStatus.EN_PREPARACION -> R.string.status_en_preparacion
    OrderStatus.FINALIZADO -> R.string.status_finalizado
    OrderStatus.CANCELADO -> R.string.status_cancelado
    else -> R.string.status_consultando
}

@StringRes
fun orderStatusHelp(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> R.string.status_help_recibido
    OrderStatus.EN_PREPARACION -> R.string.status_help_en_preparacion
    OrderStatus.FINALIZADO -> R.string.status_help_finalizado
    OrderStatus.CANCELADO -> R.string.status_help_cancelado
    else -> R.string.status_help_consultando
}

@ColorRes
fun orderStatusColor(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> R.color.status_recibido
    OrderStatus.EN_PREPARACION -> R.color.status_en_preparacion
    OrderStatus.FINALIZADO -> R.color.status_finalizado
    OrderStatus.CANCELADO -> R.color.status_cancelado
    else -> R.color.status_recibido
}

/** Avance (0 a 100) de la barra de "Mi pedido". */
fun orderProgress(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> 33
    OrderStatus.EN_PREPARACION -> 66
    OrderStatus.FINALIZADO -> 100
    else -> 0
}
