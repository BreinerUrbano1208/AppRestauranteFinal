package com.bremnercamilourbano.apprestaurante.data

import com.bremnercamilourbano.apprestaurante.model.CartItem
import com.bremnercamilourbano.apprestaurante.model.Product
import kotlinx.coroutines.CancellationException

class ProductRepository(private val api: ProductsApi = ProductsApi.create()) {

    private fun bearer(token: String) = "Bearer $token"

    suspend fun login(username: String, password: String): Result<String> = safeCall {
        api.login(LoginRequest(username, password)).token
    }

    /** Menú público (solo disponibles) o, con token de administrador, el catálogo completo. */
    suspend fun getMenu(adminToken: String? = null): Result<List<Product>> = safeCall {
        val list = if (adminToken != null) api.getAllProducts(bearer(adminToken)) else api.getMenu()
        list.mapNotNull { it.toDomain() }
    }

    suspend fun getProduct(id: Int): Result<Product> = safeCall {
        api.getProduct(id).toDomain() ?: throw IllegalStateException("Producto sin id")
    }

    suspend fun createProduct(token: String, product: Product): Result<Unit> = safeCall {
        api.addProduct(bearer(token), product.toRequest())
        Unit
    }

    suspend fun updateProduct(token: String, product: Product): Result<Unit> = safeCall {
        api.updateProduct(bearer(token), product.id, product.toRequest())
        Unit
    }

    suspend fun setAvailability(token: String, id: Int, available: Boolean): Result<Unit> = safeCall {
        api.setAvailability(bearer(token), id, AvailabilityRequest(available))
        Unit
    }

    suspend fun deleteProduct(token: String, id: Int): Result<Unit> = safeCall {
        api.deleteProduct(bearer(token), id)
    }

    suspend fun createOrder(customerName: String, note: String, items: List<CartItem>): Result<OrderResponse> =
        safeCall {
            api.createOrder(
                OrderRequest(
                    customerName = customerName,
                    note = note,
                    items = items.map { OrderItemRequest(it.product.id, it.quantity) }
                )
            )
        }

    private suspend inline fun <T> safeCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
