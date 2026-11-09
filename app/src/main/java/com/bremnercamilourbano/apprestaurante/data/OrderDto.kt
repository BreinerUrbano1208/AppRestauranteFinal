package com.bremnercamilourbano.apprestaurante.data

object OrderStatus {
    const val RECIBIDO = "RECIBIDO"
    const val EN_PREPARACION = "EN_PREPARACION"
    const val FINALIZADO = "FINALIZADO"
    const val CANCELADO = "CANCELADO"

    val ACTIVOS = listOf(RECIBIDO, EN_PREPARACION)
    val TERMINADOS = listOf(FINALIZADO, CANCELADO)

    /**   Siguiente estado en la cocina (RECIBIDO -> EN_PREPARACION -> FINALIZADO). */
    fun next(status: String): String? = when (status) {
        RECIBIDO -> EN_PREPARACION
        EN_PREPARACION -> FINALIZADO
        else -> null
    }
}

object OrderType {
    const val MESA = "mesa"
    const val RECOGER = "recoger"
}

data class OrderItemRequest(val productId: Int, val quantity: Int)

data class OrderRequest(
    val customerName: String,
    val phone: String,
    val type: String,
    val tableNumber: Int?,
    val note: String,
    val items: List<OrderItemRequest>
)

data class OrderCreated(val id: Int, val total: Double, val status: String)

data class OrderStatusDto(val id: Int, val status: String, val cancelReason: String?)

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
    val tableNumber: Int?,
    val note: String,
    val status: String,
    val total: Double,
    val cancelReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<OrderLineDto>
)

data class StatusRequest(val status: String)
data class CancelRequest(val reason: String?)
