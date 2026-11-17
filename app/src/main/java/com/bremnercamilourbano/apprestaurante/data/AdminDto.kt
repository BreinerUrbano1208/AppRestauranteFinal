package com.bremnercamilourbano.apprestaurante.data

/** Mesa que el cliente puede elegir al pedir. */
data class TableDto(val id: Int, val number: Int, val capacity: Int)

/** Mesa vista por el administrador. [occupied] = tiene pedidos activos. */
data class AdminTableDto(
    val id: Int,
    val number: Int,
    val capacity: Int,
    val active: Boolean,
    val occupied: Boolean,
    val activeOrders: Int,
    val orderIds: List<Int>
)

data class TableCreateRequest(val number: Int, val capacity: Int)
data class TableCapacityRequest(val capacity: Int)
data class TableActiveRequest(val active: Boolean)

data class MethodSalesDto(val method: String, val orders: Int, val total: Double)

/** Estado público del restaurante: los clientes solo pueden pedir con la jornada abierta. */
data class JornadaStatusDto(val open: Boolean, val openedAt: Long?)

/** Jornada con su resumen. Si no hay jornada abierta, el servidor responde solo { "open": false }. */
data class JornadaDto(
    val id: Int,
    val open: Boolean,
    val openedAt: Long,
    val closedAt: Long?,
    val openedBy: String?,
    val closedBy: String?,
    val total: Double,
    val orders: Int,
    val cancelled: Int,
    val active: Int,
    val averageTicket: Double,
    val byMethod: List<MethodSalesDto>?
)

data class CloseJornadaRequest(val password: String)

/** Cuenta abierta de una mesa: pedidos todavía sin facturar. */
data class TableAccountDto(val number: Int, val orders: List<OrderDto>, val total: Double)

/** Pedido que el cajero carga a una mesa. */
data class StaffOrderRequest(val items: List<OrderItemRequest>, val note: String)

data class BillRequest(val paymentMethod: String)

data class BillDto(val billed: Int, val total: Double, val paymentMethod: String)

/** Pedido de una jornada (evidencia para el administrador). */
data class JornadaOrderDto(
    val id: Int,
    val customerName: String?,
    val type: String?,
    val tableNumber: Int?,
    val status: String,
    val paymentMethod: String?,
    val total: Double,
    val createdAt: Long
)

/** Cuenta del personal que gestiona el administrador. */
data class StaffUserDto(
    val id: Int,
    val name: String,
    val email: String,
    val role: String?,
    val active: Boolean
)

data class StaffCreateRequest(val name: String, val email: String, val password: String, val role: String)

data class StaffActiveRequest(val active: Boolean)
