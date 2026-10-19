package com.bremnercamilourbano.apprestaurante.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.ProductRepository
import com.bremnercamilourbano.apprestaurante.model.CartItem
import com.bremnercamilourbano.apprestaurante.model.Product
import com.google.gson.JsonParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface LoadState {
    data object Loading : LoadState
    data object Loaded : LoadState
    data class Error(val kind: ErrorKind) : LoadState
}

enum class ErrorKind { NETWORK, SERVER, UNKNOWN }

data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())

class RestaurantViewModel : ViewModel() {

    private val repository = ProductRepository()
    private val serverProducts = MutableStateFlow<List<Product>>(emptyList())
    private var loadJob: Job? = null

    val products: StateFlow<List<Product>> = serverProducts.asStateFlow()

    val menu: StateFlow<List<Product>> = serverProducts
        .map { list -> list.filter { it.available } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()


    fun loadProducts(silent: Boolean = false) {
        if (silent) loadJob?.cancel() else if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            if (!silent) _loadState.value = LoadState.Loading
            repository.getMenu(adminToken.value)
                .onSuccess { list ->
                    serverProducts.value = list
                    _loadState.value = LoadState.Loaded
                }
                .onFailure { error ->
                    if (error.isUnauthorized()) {
                        expireSession()
                    } else if (serverProducts.value.isEmpty()) {
                        _loadState.value = LoadState.Error(error.toErrorKind())
                    } else {
                        _loadState.value = LoadState.Loaded
                        post(R.string.msg_menu_refresh_failed)
                    }
                }
        }
    }

    fun loadProductDetail(id: Int) {
        viewModelScope.launch {
            repository.getProduct(id).onSuccess { fresh ->
                serverProducts.update { list -> list.map { if (it.id == fresh.id) fresh else it } }
            }
        }
    }

    private val cart = MutableStateFlow<Map<Int, Int>>(emptyMap())

    val cartItems: StateFlow<List<CartItem>> = combine(products, cart) { all, quantities ->
        quantities.mapNotNull { (id, quantity) ->
            all.firstOrNull { it.id == id && it.available }?.let { CartItem(it, quantity) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cartCount: StateFlow<Int> = cartItems
        .map { items -> items.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val cartTotal: StateFlow<Double> = cartItems
        .map { items -> items.sumOf { it.subtotal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun addToCart(product: Product, quantity: Int = 1) {
        if (!product.available) {
            post(R.string.msg_unavailable)
            return
        }
        cart.update { current ->
            val updated = LinkedHashMap(current)
            updated[product.id] = (updated[product.id] ?: 0) + quantity
            updated
        }
        post(R.string.msg_added_to_cart, product.title)
    }

    fun changeQuantity(productId: Int, delta: Int) {
        cart.update { current ->
            val updated = LinkedHashMap(current)
            val newQuantity = (updated[productId] ?: return@update current) + delta
            if (newQuantity <= 0) updated.remove(productId) else updated[productId] = newQuantity
            updated
        }
    }

    fun removeFromCart(productId: Int) {
        cart.update { current -> LinkedHashMap(current).apply { remove(productId) } }
    }

    fun clearCart() {
        cart.value = emptyMap()
        post(R.string.msg_cart_cleared)
    }


    private val _orderBusy = MutableStateFlow(false)
    val orderBusy: StateFlow<Boolean> = _orderBusy.asStateFlow()

    private val _orderPlaced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val orderPlaced: SharedFlow<Unit> = _orderPlaced.asSharedFlow()

    fun submitOrder(customerName: String, note: String) {
        val items = cartItems.value
        if (items.isEmpty() || _orderBusy.value) return
        _orderBusy.value = true
        viewModelScope.launch {
            repository.createOrder(customerName.trim(), note.trim(), items)
                .onSuccess { order ->
                    cart.value = emptyMap()
                    post(R.string.msg_order_sent, order.id)
                    _orderPlaced.tryEmit(Unit)
                }
                .onFailure { error ->
                    postError(error)
                    // Puede que el administrador haya cambiado el menú: se refresca en silencio.
                    if (error is HttpException) loadProducts(silent = true)
                }
            _orderBusy.value = false
        }
    }


    private val adminToken = MutableStateFlow<String?>(null)

    val adminLogged: StateFlow<Boolean> = adminToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<UiMessage?>(null)
    val loginError: StateFlow<UiMessage?> = _loginError.asStateFlow()

    fun loginAdmin(password: String) {
        if (_loginBusy.value) return
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch {
            repository.login(ADMIN_USER, password)
                .onSuccess { token ->
                    adminToken.value = token
                    loadProducts(silent = true)
                }
                .onFailure { error ->
                    _loginError.value = when {
                        error is HttpException && error.code() == 401 -> UiMessage(R.string.admin_login_error)
                        error is HttpException && error.code() == 429 -> UiMessage(R.string.admin_login_blocked)
                        error is IOException -> UiMessage(R.string.msg_no_connection)
                        else -> UiMessage(R.string.msg_unknown_error)
                    }
                }
            _loginBusy.value = false
        }
    }

    fun logoutAdmin() {
        adminToken.value = null
        _loginError.value = null
        loadProducts(silent = true)
    }

    private fun expireSession() {
        adminToken.value = null
        post(R.string.msg_session_expired)
        loadProducts(silent = true)
    }

    fun saveProduct(
        id: Int?,
        title: String,
        description: String,
        category: String,
        price: Double,
        imageUrl: String?
    ) {
        val token = adminToken.value ?: return
        viewModelScope.launch {
            val result = if (id == null) {
                repository.createProduct(
                    token,
                    Product(0, title, description, category, price, imageUrl, available = true)
                )
            } else {
                val current = serverProducts.value.firstOrNull { it.id == id } ?: return@launch
                repository.updateProduct(
                    token,
                    current.copy(
                        title = title,
                        description = description,
                        category = category,
                        price = price,
                        imageUrl = imageUrl
                    )
                )
            }
            result
                .onSuccess {
                    post(if (id == null) R.string.msg_product_created else R.string.msg_product_updated)
                    loadProducts(silent = true)
                }
                .onFailure { handleAdminFailure(it) }
        }
    }

    fun toggleAvailability(productId: Int) {
        val token = adminToken.value ?: return
        val current = serverProducts.value.firstOrNull { it.id == productId } ?: return
        val newValue = !current.available
        viewModelScope.launch {
            repository.setAvailability(token, productId, newValue)
                .onSuccess {
                    if (newValue) post(R.string.msg_availability_on, current.title)
                    else post(R.string.msg_availability_off, current.title)
                    loadProducts(silent = true)
                }
                .onFailure {
                    handleAdminFailure(it)
                    loadProducts(silent = true) // deja el interruptor igual que en el servidor
                }
        }
    }

    init {
        // Al crear el ViewModel se consulta el menú a la API.
        loadProducts()
    }

    private fun handleAdminFailure(error: Throwable) {
        if (error.isUnauthorized()) expireSession() else postError(error)
    }

    private fun post(@StringRes res: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(res, args.toList()))
    }

    private fun postError(error: Throwable) {
        when {
            error is HttpException -> {
                val text = error.serverMessage()
                if (text != null) post(R.string.msg_server_error, text) else post(R.string.error_server)
            }
            error is IOException -> post(R.string.msg_no_connection)
            else -> post(R.string.msg_unknown_error)
        }
    }

    private fun Throwable.isUnauthorized() = this is HttpException && code() == 401

    private fun HttpException.serverMessage(): String? = try {
        response()?.errorBody()?.string()
            ?.let { JsonParser.parseString(it).asJsonObject.get("error")?.asString }
    } catch (e: Exception) {
        null
    }

    private fun Throwable.toErrorKind(): ErrorKind = when (this) {
        is IOException -> ErrorKind.NETWORK
        is HttpException -> ErrorKind.SERVER
        else -> ErrorKind.UNKNOWN
    }

    companion object {
        const val ADMIN_USER = "admin"
    }
}
