package com.bremnercamilourbano.apprestaurante.data

class TableRepository(private val api: ProductsApi = ProductsApi.create()) {

    private fun bearer(token: String) = "Bearer $token"

    // ---------- Cliente ----------
    suspend fun getTables(): Result<List<TableDto>> = safeCall { api.getTables() }

    // ---------- Administrador ----------
    suspend fun getAdminTables(token: String): Result<List<AdminTableDto>> =
        safeCall { api.getAdminTables(bearer(token)) }

    suspend fun create(token: String, number: Int, capacity: Int): Result<AdminTableDto> =
        safeCall { api.createTable(bearer(token), TableCreateRequest(number, capacity)) }

    suspend fun setCapacity(token: String, id: Int, capacity: Int): Result<AdminTableDto> =
        safeCall { api.setTableCapacity(bearer(token), id, TableCapacityRequest(capacity)) }

    suspend fun setActive(token: String, id: Int, active: Boolean): Result<AdminTableDto> =
        safeCall { api.setTableActive(bearer(token), id, TableActiveRequest(active)) }

    // ---------- Cajero: cuenta de la mesa ----------
    suspend fun account(token: String, number: Int): Result<TableAccountDto> =
        safeCall { api.getTableAccount(bearer(token), number) }

    suspend fun addOrder(token: String, number: Int, items: List<OrderItemRequest>): Result<OrderDto> =
        safeCall { api.createTableOrder(bearer(token), number, StaffOrderRequest(items, "")) }

    suspend fun bill(token: String, number: Int, method: String): Result<BillDto> =
        safeCall { api.billTable(bearer(token), number, BillRequest(method)) }

    suspend fun delete(token: String, id: Int): Result<Unit> =
        safeCall { api.deleteTable(bearer(token), id) }
}
