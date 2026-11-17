package com.bremnercamilourbano.apprestaurante.util

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.PaymentMethod
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
    OrderStatus.LISTO -> R.string.status_listo
    OrderStatus.ENTREGADO -> R.string.status_entregado
    OrderStatus.CANCELADO -> R.string.status_cancelado
    else -> R.string.status_consultando
}

@StringRes
fun orderStatusHelp(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> R.string.status_help_recibido
    OrderStatus.EN_PREPARACION -> R.string.status_help_en_preparacion
    OrderStatus.LISTO -> R.string.status_help_listo
    OrderStatus.ENTREGADO -> R.string.status_help_entregado
    OrderStatus.CANCELADO -> R.string.status_help_cancelado
    else -> R.string.status_help_consultando
}

@ColorRes
fun orderStatusColor(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> R.color.status_recibido
    OrderStatus.EN_PREPARACION -> R.color.status_en_preparacion
    OrderStatus.LISTO -> R.color.status_listo
    OrderStatus.ENTREGADO -> R.color.status_finalizado
    OrderStatus.CANCELADO -> R.color.status_cancelado
    else -> R.color.status_recibido
}

/** Avance (0 a 100) de la barra de "Mi pedido". */
fun orderProgress(status: String?): Int = when (status) {
    OrderStatus.RECIBIDO -> 25
    OrderStatus.EN_PREPARACION -> 50
    OrderStatus.LISTO -> 75
    OrderStatus.ENTREGADO -> 100
    else -> 0
}

@StringRes
fun paymentLabel(method: String?): Int = when (method) {
    PaymentMethod.TARJETA -> R.string.pay_tarjeta
    PaymentMethod.NEQUI -> R.string.pay_nequi
    PaymentMethod.DAVIPLATA -> R.string.pay_daviplata
    else -> R.string.pay_efectivo
}

/**
 * Celular colombiano: 10 números que empiezan por 3 (acepta +57 o 57 al inicio).
 * Devuelve los 10 números, o null si no es válido.
 */
fun normalizeColombianPhone(text: String): String? {
    var n = text.filter { it.isDigit() || it == '+' }.removePrefix("+")
    if (n.length == 12 && n.startsWith("57")) n = n.substring(2)
    return if (n.length == 10 && n[0] == '3' && n.all { it.isDigit() }) n else null
}
