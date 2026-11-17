package com.bremnercamilourbano.apprestaurante.data

class OrderRepository(private val api: ProductsApi = ProductsApi.create()) {

    private fun bearer(token: String) = "Bearer $token"

    // ---------- Cliente ----------
    suspend fun createOrder(token: String, request: OrderRequest): Result<OrderCreated> =
        safeCall { api.createOrder(bearer(token), request) }

    suspend fun getOrderStatus(id: Int): Result<OrderStatusDto> =
        safeCall { api.getOrderStatus(id) }

    // ---------- Cocina y administrador ----------
    suspend fun getActiveOrders(token: String): Result<List<OrderDto>> =
        safeCall { api.getActiveOrders(bearer(token)) }

    suspend fun getOrdersByStatus(token: String, status: String): Result<List<OrderDto>> =
        safeCall { api.getOrdersByStatus(bearer(token), status) }

    suspend fun setStatus(token: String, id: Int, status: String): Result<OrderDto> =
        safeCall { api.setOrderStatus(bearer(token), id, StatusRequest(status)) }

    // ---------- Administrador ----------
    suspend fun cancel(token: String, id: Int, reason: String?): Result<OrderDto> =
        safeCall { api.cancelOrder(bearer(token), id, CancelRequest(reason)) }

    suspend fun getHistory(
        token: String,
        query: String?,
        status: String?,
        from: String?,
        to: String?
    ): Result<List<OrderDto>> =
        safeCall { api.getHistory(bearer(token), query, status, from, to) }

    suspend fun changeTable(token: String, id: Int, tableNumber: Int): Result<OrderDto> =
        safeCall { api.changeOrderTable(bearer(token), id, ChangeTableRequest(tableNumber)) }

    suspend fun changePayment(token: String, id: Int, method: String): Result<OrderDto> =
        safeCall { api.changeOrderPayment(bearer(token), id, PaymentRequest(method)) }
}
