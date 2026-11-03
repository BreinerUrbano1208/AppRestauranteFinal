package com.bremnercamilourbano.apprestaurante.data


import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// API de productos
interface ProductsApi {

    // Login
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // Obtener menú
    @GET("api/products")
    suspend fun getMenu(): List<ProductDto>

    // Obtener todos los productos (admin)
    @GET("api/admin/products")
    suspend fun getAllProducts(@Header("Authorization") auth: String): List<ProductDto>

    // Obtener producto por ID
    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): ProductDto

    // Agregar producto
    @POST("api/products")
    suspend fun addProduct(@Header("Authorization") auth: String, @Body body: ProductRequest): ProductDto

    // Actualizar producto
    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: ProductRequest
    ): ProductDto

    // Cambiar disponibilidad
    @PATCH("api/products/{id}")
    suspend fun setAvailability(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: AvailabilityRequest
    ): ProductDto

    // Eliminar producto
    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Header("Authorization") auth: String, @Path("id") id: Int)

    // Crear pedido
    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderRequest): OrderResponse

    // Instancia de Retrofit
    companion object {
        fun create(): ProductsApi = Retrofit.Builder()
            /* url para emulador*/ .baseUrl("http://10.0.2.2:3000/")
            /*url para celular .baseUrl("http://192.168.18.10:3000/")*/
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(ProductsApi::class.java)
    }
}