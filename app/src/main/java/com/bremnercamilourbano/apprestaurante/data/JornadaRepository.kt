package com.bremnercamilourbano.apprestaurante.data

class JornadaRepository(private val api: ProductsApi = ProductsApi.create()) {

    private fun bearer(token: String) = "Bearer $token"

    suspend fun status(): Result<JornadaStatusDto> = safeCall { api.getJornadaStatus() }

    suspend fun open(token: String): Result<JornadaDto> = safeCall { api.openJornada(bearer(token)) }

    suspend fun summary(token: String): Result<JornadaDto> = safeCall { api.getJornadaSummary(bearer(token)) }

    suspend fun close(token: String, password: String): Result<JornadaDto> =
        safeCall { api.closeJornada(bearer(token), CloseJornadaRequest(password)) }

    suspend fun orders(token: String, id: Int): Result<List<JornadaOrderDto>> =
        safeCall { api.getJornadaOrders(bearer(token), id) }

    suspend fun history(token: String): Result<List<JornadaDto>> = safeCall { api.getJornadas(bearer(token)) }
}
