package com.bremnercamilourbano.apprestaurante.data

import com.bremnercamilourbano.apprestaurante.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ProductsApi {

    // ---------- Sesión ----------
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // ---------- Productos ----------
    @GET("api/products")
    suspend fun getMenu(): List<ProductDto>

    @GET("api/admin/products")
    suspend fun getAllProducts(@Header("Authorization") auth: String): List<ProductDto>

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): ProductDto

    @POST("api/products")
    suspend fun addProduct(@Header("Authorization") auth: String, @Body body: ProductRequest): ProductDto

    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: ProductRequest
    ): ProductDto

    @PATCH("api/products/{id}")
    suspend fun setAvailability(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: AvailabilityRequest
    ): ProductDto

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Header("Authorization") auth: String, @Path("id") id: Int)

    // ---------- Imágenes (administrador) ----------
    @Multipart
    @POST("api/admin/uploads")
    suspend fun uploadImage(
        @Header("Authorization") auth: String,
        @Part image: MultipartBody.Part
    ): UploadResponse

    // ---------- Pedidos: cliente ----------
    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderRequest): OrderCreated

    @GET("api/orders/{id}/status")
    suspend fun getOrderStatus(@Path("id") id: Int): OrderStatusDto

    // ---------- Pedidos: cocina y administrador ----------
    @GET("api/orders")
    suspend fun getActiveOrders(@Header("Authorization") auth: String): List<OrderDto>

    @PATCH("api/orders/{id}/status")
    suspend fun setOrderStatus(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: StatusRequest
    ): OrderDto

    // ---------- Pedidos: solo administrador ----------
    @POST("api/orders/{id}/cancel")
    suspend fun cancelOrder(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: CancelRequest
    ): OrderDto

    @GET("api/admin/orders/history")
    suspend fun getHistory(
        @Header("Authorization") auth: String,
        @Query("q") query: String?,
        @Query("status") status: String?,
        @Query("from") from: String?,
        @Query("to") to: String?
    ): List<OrderDto>

    companion object {
        // Render (plan gratis) se duerme sin uso y la primera petición puede tardar ~50 s:
        // por eso los tiempos de espera son de 60 s.
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        private val instance: ProductsApi by lazy {
            Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
                .build()
                .create(ProductsApi::class.java)
        }

        fun create(): ProductsApi = instance
    }
}
