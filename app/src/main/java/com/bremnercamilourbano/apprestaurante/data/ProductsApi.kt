package com.bremnercamilourbano.apprestaurante.data

import com.bremnercamilourbano.apprestaurante.BuildConfig
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

interface ProductsApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

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

    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderRequest): OrderResponse

    companion object {
        fun create(): ProductsApi = Retrofit.Builder()
            .baseUrl("http://192.168.18.10:3000/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(ProductsApi::class.java)
    }
}
