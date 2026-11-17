package com.bremnercamilourbano.apprestaurante.data

object OrderStatus {
    const val RECIBIDO = "RECIBIDO"
    const val EN_PREPARACION = "EN_PREPARACION"
    const val LISTO = "LISTO"
    const val ENTREGADO = "ENTREGADO"
    const val CANCELADO = "CANCELADO"

    val ACTIVOS = listOf(RECIBIDO, EN_PREPARACION, LISTO)
    val TERMINADOS = listOf(ENTREGADO, CANCELADO)

    /** Siguiente estado: RECIBIDO -> EN_PREPARACION -> LISTO -> ENTREGADO. */
    fun next(status: String): String? = when (status) {
        RECIBIDO -> EN_PREPARACION
        EN_PREPARACION -> LISTO
        LISTO -> ENTREGADO
        else -> null
    }
}

object OrderType {
    const val MESA = "mesa"
    const val RECOGER = "recoger"
    const val DOMICILIO = "domicilio"
}

object PaymentMethod {
    const val EFECTIVO = "EFECTIVO"
    const val TARJETA = "TARJETA"
    const val NEQUI = "NEQUI"
    const val DAVIPLATA = "DAVIPLATA"

    /** Solo se registra cómo pagó el cliente; la app no procesa pagos. */
    val ALL = listOf(EFECTIVO, TARJETA, NEQUI, DAVIPLATA)
}

data class OrderItemRequest(val productId: Int, val quantity: Int)

data class OrderRequest(
    val customerName: String,
    val phone: String,
    val type: String,
    val tableNumber: Int?,
    val address: String?,
    val neighborhood: String?,
    val note: String,
    val paymentMethod: String,
    val items: List<OrderItemRequest>
)

data class OrderCreated(val id: Int, val total: Double, val status: String)

data class OrderStatusDto(
    val id: Int,
    val status: String,
    val cancelReason: String?,
    val type: String?,
    val tableNumber: Int?,
    val total: Double,
    val items: List<OrderLineDto>?
)

data class OrderLineDto(
    val productId: Int?,
    val name: String,
    val price: Double,
    val quantity: Int
)

data class OrderDto(
    val id: Int,
    val customerName: String,
    val phone: String,
    val type: String,
    val address: String?,
    val neighborhood: String?,
    val tableNumber: Int?,
    val note: String,
    val paymentMethod: String,
    val status: String,
    val total: Double,
    val cancelReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<OrderLineDto>
)

data class StatusRequest(val status: String)
data class CancelRequest(val reason: String?)

data class ChangeTableRequest(val tableNumber: Int)
data class PaymentRequest(val paymentMethod: String)
