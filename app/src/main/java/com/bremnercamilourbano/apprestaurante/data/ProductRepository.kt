package com.bremnercamilourbano.apprestaurante.data

import com.bremnercamilourbano.apprestaurante.model.Product
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProductRepository(private val api: ProductsApi = ProductsApi.create()) {

    private fun bearer(token: String) = "Bearer $token"

    /** Inicia sesión: el servidor decide el rol (admin o cocina) según la contraseña. */
    suspend fun login(password: String): Result<LoginResponse> = safeCall {
        api.login(LoginRequest(LOGIN_USER, password))
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

    /** Sube la foto (ya comprimida, JPEG) y devuelve su URL pública. */
    suspend fun uploadImage(token: String, jpegBytes: ByteArray): Result<String> = safeCall {
        val body = jpegBytes.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("image", "foto.jpg", body)
        api.uploadImage(bearer(token), part).imageUrl
    }

    private companion object {
        // El servidor decide el rol por la contraseña; este nombre se ignora.
        const val LOGIN_USER = "personal"
    }
}
