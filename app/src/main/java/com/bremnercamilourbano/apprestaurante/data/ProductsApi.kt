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
import retrofit2.http.HTTP
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
    // Sesión: entrada antigua con clave por rol
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // ---------- Productos ----------
    // Menú (cliente): trae los productos disponibles
    @GET("api/products")
    suspend fun getMenu(): List<ProductDto>

    // Menú (administrador): trae todos los productos, incluso los ocultos
    @GET("api/admin/products")
    suspend fun getAllProducts(@Header("Authorization") auth: String): List<ProductDto>

    // Menú (cliente): detalle de un producto
    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): ProductDto

    // Productos (administrador): crear producto
    @POST("api/products")
    suspend fun addProduct(@Header("Authorization") auth: String, @Body body: ProductRequest): ProductDto

    // Productos (administrador): editar producto
    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: ProductRequest
    ): ProductDto

    // Productos (administrador): mostrar u ocultar del menú
    @PATCH("api/products/{id}")
    suspend fun setAvailability(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: AvailabilityRequest
    ): ProductDto

    // Productos (administrador): eliminar producto
    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Header("Authorization") auth: String, @Path("id") id: Int)

    // ---------- Imágenes (administrador) ----------
    // Productos (administrador): subir la foto del producto
    @Multipart
    @POST("api/admin/uploads")
    suspend fun uploadImage(
        @Header("Authorization") auth: String,
        @Part image: MultipartBody.Part
    ): UploadResponse

    // ---------- Pedidos: cliente ----------
    // Pedido (cliente): enviar el pedido
    @POST("api/orders")
    suspend fun createOrder(
        @Header("Authorization") auth: String,
        @Body body: OrderRequest
    ): OrderCreated

    // ---------- Personal (administrador) ----------
    // Personal (administrador): lista de cuentas de cocina, caja y administradores
    @GET("api/admin/staff")
    suspend fun getStaff(@Header("Authorization") auth: String): List<StaffUserDto>

    // Personal (administrador): crear cuenta
    @POST("api/admin/staff")
    suspend fun createStaff(
        @Header("Authorization") auth: String,
        @Body body: StaffCreateRequest
    ): StaffUserDto

    // Personal (administrador): activar o desactivar cuenta
    @PATCH("api/admin/staff/{id}")
    suspend fun setStaffActive(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: StaffActiveRequest
    ): StaffUserDto

    // ---------- Cuenta del cliente ----------
    // Cuenta (cliente): crear cuenta y enviar código al correo
    @POST("api/cuentas/register")
    suspend fun registerClient(@Body body: ClientRegisterRequest): RegisterResponse

    // Cuenta (cliente): verificar el correo con el código
    @POST("api/cuentas/verificar")
    suspend fun verifyEmail(@Body body: VerifyRequest): ClientAuthResponse

    // Cuenta (cliente): reenviar el código
    @POST("api/cuentas/reenviar")
    suspend fun resendCode(@Body body: EmailRequest): OkResponse

    // Cuenta (cliente): pedir código para recuperar la contraseña
    @POST("api/cuentas/olvide")
    suspend fun forgotPassword(@Body body: EmailRequest): OkResponse

    // Cuenta (cliente): cambiar la contraseña con el código
    @POST("api/cuentas/restablecer")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): OkResponse

    // Cuenta (cliente): eliminar su propia cuenta
    @HTTP(method = "DELETE", path = "api/cuentas/me", hasBody = true)
    suspend fun deleteAccount(@Header("Authorization") auth: String, @Body body: DeleteAccountRequest): OkResponse

    // Personal (administrador): eliminar cuenta de cocina o caja
    @DELETE("api/admin/staff/{id}")
    suspend fun deleteStaff(@Header("Authorization") auth: String, @Path("id") id: Int): OkResponse

    // Sesión: iniciar sesión con correo y contraseña (todos los roles)
    @POST("api/cuentas/login")
    suspend fun loginClient(@Body body: ClientLoginRequest): ClientAuthResponse

    // Cuenta (cliente): guardar cambios del perfil
    @PATCH("api/cuentas/me")
    suspend fun updateProfile(@Header("Authorization") auth: String, @Body body: ProfileUpdateRequest): UserDto

    // Notificaciones: registrar este celular
    @POST("api/dispositivos")
    suspend fun registerDevice(@Header("Authorization") auth: String, @Body body: DeviceRequest)

    // Notificaciones: enviar una notificación de prueba
    @POST("api/dispositivos/prueba")
    suspend fun testPush(@Header("Authorization") auth: String): PushTestResponse

    // Notificaciones: quitar este celular al cerrar sesión
    @HTTP(method = "DELETE", path = "api/dispositivos", hasBody = true)
    suspend fun unregisterDevice(@Header("Authorization") auth: String, @Body body: DeviceRequest)

    // Pedido (cliente): historial de sus pedidos
    @GET("api/cuentas/me/orders")
    suspend fun getMyOrders(@Header("Authorization") auth: String): List<OrderDto>

    // Pedido (cliente): estado del pedido (seguimiento)
    @GET("api/orders/{id}/status")
    suspend fun getOrderStatus(@Path("id") id: Int): OrderStatusDto

    // ---------- Pedidos: cocina y administrador ----------
    // Pedidos (cocina y caja): pedidos en curso
    @GET("api/orders")
    suspend fun getActiveOrders(@Header("Authorization") auth: String): List<OrderDto>

    /** Cajero: entregados o cancelados de la jornada abierta. */
    // Pedidos (caja): entregados o cancelados de la jornada
    @GET("api/orders")
    suspend fun getOrdersByStatus(
        @Header("Authorization") auth: String,
        @Query("status") status: String
    ): List<OrderDto>

    // Pedidos (cocina y caja): cambiar estado (preparando, listo, entregado)
    @PATCH("api/orders/{id}/status")
    suspend fun setOrderStatus(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: StatusRequest
    ): OrderDto

    // ---------- Pedidos: solo administrador ----------
    // Pedidos (caja y administrador): cancelar pedido
    @POST("api/orders/{id}/cancel")
    suspend fun cancelOrder(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: CancelRequest
    ): OrderDto

    // Historial (administrador): todos los pedidos con filtros
    @GET("api/admin/orders/history")
    suspend fun getHistory(
        @Header("Authorization") auth: String,
        @Query("q") query: String?,
        @Query("status") status: String?,
        @Query("from") from: String?,
        @Query("to") to: String?
    ): List<OrderDto>

    // Pedidos (administrador): corregir la mesa
    @PATCH("api/orders/{id}/table")
    suspend fun changeOrderTable(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: ChangeTableRequest
    ): OrderDto

    // Pedidos (administrador): corregir el método de pago
    @PATCH("api/orders/{id}/payment")
    suspend fun changeOrderPayment(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: PaymentRequest
    ): OrderDto

    // ---------- Mesas ----------
    // Mesas (cliente): mesas activas para escoger
    @GET("api/tables")
    suspend fun getTables(): List<TableDto>

    // Mesas (caja y administrador): lista con estado libre u ocupada
    @GET("api/admin/tables")
    suspend fun getAdminTables(@Header("Authorization") auth: String): List<AdminTableDto>

    // Mesas (administrador): crear mesa
    @POST("api/admin/tables")
    suspend fun createTable(
        @Header("Authorization") auth: String,
        @Body body: TableCreateRequest
    ): AdminTableDto

    // Mesas (administrador): cambiar capacidad
    @PUT("api/admin/tables/{id}")
    suspend fun setTableCapacity(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: TableCapacityRequest
    ): AdminTableDto

    // Mesas (administrador): activar o desactivar
    @PUT("api/admin/tables/{id}")
    suspend fun setTableActive(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Body body: TableActiveRequest
    ): AdminTableDto

    // Mesas (administrador): eliminar mesa
    @DELETE("api/admin/tables/{id}")
    suspend fun deleteTable(@Header("Authorization") auth: String, @Path("id") id: Int)

    // ---------- Cuenta de mesa (cajero) ----------
    // Mesa (caja): ver la cuenta de la mesa (Mirar pedido)
    @GET("api/tables/{n}/account")
    suspend fun getTableAccount(
        @Header("Authorization") auth: String,
        @Path("n") number: Int
    ): TableAccountDto

    // Mesa (caja): cargar pedido a la mesa y enviarlo a cocina
    @POST("api/tables/{n}/orders")
    suspend fun createTableOrder(
        @Header("Authorization") auth: String,
        @Path("n") number: Int,
        @Body body: StaffOrderRequest
    ): OrderDto

    // Mesa (caja): cobrar y liberar la mesa
    @POST("api/tables/{n}/bill")
    suspend fun billTable(
        @Header("Authorization") auth: String,
        @Path("n") number: Int,
        @Body body: BillRequest
    ): BillDto

    // Jornada (administrador): pedidos de una jornada cerrada
    @GET("api/admin/jornadas/{id}/orders")
    suspend fun getJornadaOrders(
        @Header("Authorization") auth: String,
        @Path("id") id: Int
    ): List<JornadaOrderDto>

    // ---------- Jornada ----------
    // Jornada: ¿el restaurante está abierto o cerrado?
    @GET("api/jornada/status")
    suspend fun getJornadaStatus(): JornadaStatusDto

    // Jornada (caja): abrir el turno
    @POST("api/jornada/open")
    suspend fun openJornada(@Header("Authorization") auth: String): JornadaDto

    // Jornada (caja): resumen de ventas del turno
    @GET("api/jornada/summary")
    suspend fun getJornadaSummary(@Header("Authorization") auth: String): JornadaDto

    // Jornada (caja): cerrar caja
    @POST("api/jornada/close")
    suspend fun closeJornada(
        @Header("Authorization") auth: String,
        @Body body: CloseJornadaRequest
    ): JornadaDto

    // Jornada (administrador): historial de jornadas
    @GET("api/admin/jornadas")
    suspend fun getJornadas(@Header("Authorization") auth: String): List<JornadaDto>

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
                .baseUrl(BuildConfig.API_BASE_URL) // coneccion del servidor de la url en build.gradle
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
                .build()
                .create(ProductsApi::class.java)
        }

        fun create(): ProductsApi = instance
    }
}
