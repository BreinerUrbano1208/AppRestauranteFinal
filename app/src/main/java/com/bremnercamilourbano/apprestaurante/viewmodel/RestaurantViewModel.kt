package com.bremnercamilourbano.apprestaurante.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderItemRequest
import com.bremnercamilourbano.apprestaurante.data.OrderRepository
import com.bremnercamilourbano.apprestaurante.data.OrderRequest
import com.bremnercamilourbano.apprestaurante.data.ProductRepository
import com.bremnercamilourbano.apprestaurante.data.Session
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.model.CartItem
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.matchesQuery
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

/** Menú, carrito, envío del pedido, inicio de sesión del personal y gestión de productos. */
class RestaurantViewModel : ViewModel() {

    private val repository = ProductRepository()
    private val orders = OrderRepository()
    private val serverProducts = MutableStateFlow<List<Product>>(emptyList())
    private var loadJob: Job? = null

    val products: StateFlow<List<Product>> = serverProducts.asStateFlow()

    // ---------- Buscador ----------
    private val _menuQuery = MutableStateFlow("")
    val menuQuery: StateFlow<String> = _menuQuery.asStateFlow()
    fun setMenuQuery(query: String) {
        _menuQuery.value = query
    }

    private val _adminQuery = MutableStateFlow("")
    val adminQuery: StateFlow<String> = _adminQuery.asStateFlow()
    fun setAdminQuery(query: String) {
        _adminQuery.value = query
    }

    private val _menuCategory = MutableStateFlow<String?>(null)
    val menuCategory: StateFlow<String?> = _menuCategory.asStateFlow()
    fun setMenuCategory(category: String?) {
        _menuCategory.value = category
    }

    /** Categorías de los productos disponibles, sin repetir ("platos" y "Platos" cuentan como una). */
    val categories: StateFlow<List<String>> = serverProducts.map { list ->
        list.filter { it.available }
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Menú del cliente: solo productos disponibles, filtrados por búsqueda y categoría. */
    val menu: StateFlow<List<Product>> = combine(serverProducts, _menuQuery, _menuCategory) { list, query, category ->
        list.filter {
            it.available && it.matchesQuery(query) &&
                (category == null || it.category.trim().equals(category, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Catálogo del administrador (incluye no disponibles) filtrado por la búsqueda. */
    val adminProducts: StateFlow<List<Product>> = combine(serverProducts, _adminQuery) { list, query ->
        list.filter { it.matchesQuery(query) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Solo el administrador ve el catálogo completo; cocina y clientes usan el menú público. */
    private fun adminToken(): String? = SessionStore.session.value?.takeIf { it.isAdmin }?.token

    fun loadProducts(silent: Boolean = false) {
        if (silent) loadJob?.cancel() else if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            if (!silent) _loadState.value = LoadState.Loading
            repository.getMenu(adminToken())
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

    // ---------- Carrito ----------
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

    // ---------- Envío del pedido ----------
    private val _orderBusy = MutableStateFlow(false)
    val orderBusy: StateFlow<Boolean> = _orderBusy.asStateFlow()

    /** Emite el número del pedido recién creado. */
    private val _orderPlaced = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val orderPlaced: SharedFlow<Int> = _orderPlaced.asSharedFlow()

    fun submitOrder(customerName: String, phone: String, type: String, tableNumber: Int?, note: String) {
        val items = cartItems.value
        if (items.isEmpty() || _orderBusy.value) return
        _orderBusy.value = true
        viewModelScope.launch {
            val request = OrderRequest(
                customerName = customerName.trim(),
                phone = phone.trim(),
                type = type,
                tableNumber = tableNumber,
                note = note.trim(),
                items = items.map { OrderItemRequest(it.product.id, it.quantity) }
            )
            orders.createOrder(request)
                .onSuccess { order ->
                    cart.value = emptyMap()
                    post(R.string.msg_order_sent, order.id)
                    _orderPlaced.tryEmit(order.id)
                }
                .onFailure { error ->
                    postError(error)
                    // Puede que el administrador haya cambiado el menú: se refresca en silencio.
                    if (error is HttpException) loadProducts(silent = true)
                }
            _orderBusy.value = false
        }
    }

    // ---------- Sesión del personal ----------
    val session: StateFlow<Session?> = SessionStore.session

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<UiMessage?>(null)
    val loginError: StateFlow<UiMessage?> = _loginError.asStateFlow()

    fun login(password: String) {
        if (_loginBusy.value) return
        _loginBusy.value = true
        _loginError.value = null
        viewModelScope.launch {
            repository.login(password)
                .onSuccess { response ->
                    SessionStore.start(response.token, response.role)
                    loadProducts(silent = true)
                }
                .onFailure { error ->
                    _loginError.value = when {
                        error is HttpException && error.code() == 401 -> UiMessage(R.string.staff_login_error)
                        error is HttpException && error.code() == 429 -> UiMessage(R.string.admin_login_blocked)
                        error is IOException -> UiMessage(R.string.msg_no_connection)
                        else -> UiMessage(R.string.msg_unknown_error)
                    }
                }
            _loginBusy.value = false
        }
    }

    fun logout() {
        SessionStore.clear()
        _loginError.value = null
        loadProducts(silent = true)
    }

    private fun expireSession() {
        SessionStore.clear()
        post(R.string.msg_session_expired)
        loadProducts(silent = true)
    }

    // ---------- Gestión de productos (administrador) ----------
    private val _saveBusy = MutableStateFlow(false)
    val saveBusy: StateFlow<Boolean> = _saveBusy.asStateFlow()

    /** Si [imageBytes] no es null, primero sube la foto y usa la URL que devuelve el servidor. */
    fun saveProduct(
        id: Int?,
        title: String,
        description: String,
        category: String,
        price: Double,
        imageUrl: String?,
        imageBytes: ByteArray? = null
    ) {
        val token = adminToken() ?: return
        viewModelScope.launch {
            _saveBusy.value = true
            try {
                var finalUrl = imageUrl
                if (imageBytes != null) {
                    val upload = repository.uploadImage(token, imageBytes)
                    val failure = upload.exceptionOrNull()
                    if (failure != null) {
                        handleAdminFailure(failure)
                        return@launch
                    }
                    finalUrl = upload.getOrNull()
                }

                val result = if (id == null) {
                    repository.createProduct(
                        token,
                        Product(0, title, description, category, price, finalUrl, available = true)
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
                            imageUrl = finalUrl
                        )
                    )
                }
                result
                    .onSuccess {
                        post(if (id == null) R.string.msg_product_created else R.string.msg_product_updated)
                        loadProducts(silent = true)
                    }
                    .onFailure { handleAdminFailure(it) }
            } finally {
                _saveBusy.value = false
            }
        }
    }

    fun toggleAvailability(productId: Int) {
        val token = adminToken() ?: return
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
        when {
            error.isUnauthorized() -> expireSession()
            error.isForbidden() -> post(R.string.msg_forbidden)
            else -> postError(error)
        }
    }

    /** Para que las pantallas muestren un aviso (Snackbar) sin lógica de red. */
    fun showMessage(@StringRes res: Int) = post(res)

    private fun post(@StringRes res: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(res, args.toList()))
    }

    private fun postError(error: Throwable) {
        _messages.tryEmit(error.toUiMessage())
    }
}
