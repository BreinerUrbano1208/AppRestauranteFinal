package com.bremnercamilourbano.apprestaurante.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderRepository
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** El pedido que el cliente está siguiendo. [status] es null mientras se consulta por primera vez. */
data class MyOrder(
    val id: Int,
    val status: String? = null,
    val cancelReason: String? = null,
    val offline: Boolean = false
)

data class HistoryFilter(
    val query: String = "",
    val status: String? = null, // null = finalizados y cancelados
    val from: String? = null,   // AAAA-MM-DD
    val to: String? = null
)

/** Seguimiento del cliente, pantalla de cocina e historial del administrador. */
class OrdersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OrderRepository()
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    // ---------- Cliente: "Mi pedido" ----------
    private val _myOrder = MutableStateFlow<MyOrder?>(null)
    val myOrder: StateFlow<MyOrder?> = _myOrder.asStateFlow()

    init {
        val saved = prefs.getInt(KEY_LAST_ORDER, 0)
        if (saved > 0) _myOrder.value = MyOrder(saved)

        // Al cerrar sesión se borran los datos de clientes que estaban en memoria.
        viewModelScope.launch {
            SessionStore.session.collect { session ->
                if (session == null) {
                    _active.value = emptyList()
                    _history.value = emptyList()
                    _statusFilter.value = null
                    _tableFilter.value = null
                    _historyFilter.value = HistoryFilter()
                }
            }
        }
    }

    /** Guarda el pedido recién enviado para poder seguirlo aunque se cierre la app. */
    fun trackOrder(id: Int) {
        prefs.edit().putInt(KEY_LAST_ORDER, id).apply()
        _myOrder.value = MyOrder(id, OrderStatus.RECIBIDO)
    }

    fun clearMyOrder() {
        prefs.edit().remove(KEY_LAST_ORDER).apply()
        _myOrder.value = null
    }

    /** Consulta el estado al servidor. La pantalla la llama cada pocos segundos mientras está visible. */
    suspend fun refreshMyOrder() {
        val current = _myOrder.value ?: return
        if (current.status?.let { it in OrderStatus.TERMINADOS } == true) return
        repository.getOrderStatus(current.id)
            .onSuccess { s -> _myOrder.value = MyOrder(s.id, s.status, s.cancelReason) }
            .onFailure { e ->
                if (e is HttpException && e.code() == 404) clearMyOrder()
                else _myOrder.value = current.copy(offline = true)
            }
    }

    // ---------- Cocina ----------
    private val _active = MutableStateFlow<List<OrderDto>>(emptyList())
    private val _activeState = MutableStateFlow<LoadState>(LoadState.Loading)
    val activeState: StateFlow<LoadState> = _activeState.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null) // null = todos los activos
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _tableFilter = MutableStateFlow<Int?>(null)
    val tableFilter: StateFlow<Int?> = _tableFilter.asStateFlow()

    fun setStatusFilter(status: String?) {
        _statusFilter.value = status
    }

    fun setTableFilter(table: Int?) {
        _tableFilter.value = table
    }

    /** Pedidos activos ya filtrados por estado y por mesa. */
    val kitchenOrders: StateFlow<List<OrderDto>> =
        combine(_active, _statusFilter, _tableFilter) { list, status, table ->
            list.filter { (status == null || it.status == status) && (table == null || it.tableNumber == table) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun refreshActive() {
        val token = SessionStore.session.value?.token ?: return
        repository.getActiveOrders(token)
            .onSuccess { list ->
                _active.value = list
                _activeState.value = LoadState.Loaded
            }
            .onFailure { error ->
                when {
                    error.isUnauthorized() || error.isForbidden() -> handleFailure(error)
                    _active.value.isEmpty() -> _activeState.value = LoadState.Error(error.toErrorKind())
                    // Si ya había pedidos en pantalla se conservan y se reintenta en el próximo ciclo.
                }
            }
    }

    private val inFlight = mutableSetOf<Int>()

    /** RECIBIDO -> EN_PREPARACION -> FINALIZADO */
    fun advance(order: OrderDto) {
        val next = OrderStatus.next(order.status) ?: return
        val token = SessionStore.session.value?.token ?: return
        if (!inFlight.add(order.id)) return
        viewModelScope.launch {
            try {
                repository.setStatus(token, order.id, next).onFailure { handleFailure(it) }
                refreshActive()
            } finally {
                inFlight.remove(order.id)
            }
        }
    }

    /** Solo administrador. El servidor rechaza cancelar un pedido ya finalizado. */
    fun cancel(order: OrderDto, reason: String) {
        val token = SessionStore.session.value?.token ?: return
        if (!inFlight.add(order.id)) return
        viewModelScope.launch {
            try {
                repository.cancel(token, order.id, reason.trim().ifBlank { null })
                    .onSuccess { post(R.string.msg_order_cancelled, order.id) }
                    .onFailure { handleFailure(it) }
                refreshActive()
            } finally {
                inFlight.remove(order.id)
            }
        }
    }

    // ---------- Historial (administrador) ----------
    private val _history = MutableStateFlow<List<OrderDto>>(emptyList())
    val history: StateFlow<List<OrderDto>> = _history.asStateFlow()

    private val _historyState = MutableStateFlow<LoadState>(LoadState.Loading)
    val historyState: StateFlow<LoadState> = _historyState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter())
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private var historyJob: Job? = null

    fun updateHistoryFilter(debounce: Boolean = false, change: (HistoryFilter) -> HistoryFilter) {
        _historyFilter.value = change(_historyFilter.value)
        loadHistory(debounce)
    }

    fun loadHistory(debounce: Boolean = false) {
        val token = SessionStore.session.value?.takeIf { it.isAdmin }?.token ?: return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS) // espera a que termine de escribir
            if (_history.value.isEmpty()) _historyState.value = LoadState.Loading
            val f = _historyFilter.value
            repository.getHistory(token, f.query.trim().ifBlank { null }, f.status, f.from, f.to)
                .onSuccess {
                    _history.value = it
                    _historyState.value = LoadState.Loaded
                }
                .onFailure { error ->
                    if (error.isUnauthorized() || error.isForbidden()) {
                        handleFailure(error)
                    } else {
                        _historyState.value = LoadState.Error(error.toErrorKind())
                    }
                }
        }
    }

    // ---------- Comunes ----------
    private fun handleFailure(error: Throwable) {
        when {
            error.isUnauthorized() -> {
                SessionStore.clear()
                post(R.string.msg_session_expired)
            }
            error.isForbidden() -> post(R.string.msg_forbidden)
            else -> _messages.tryEmit(error.toUiMessage())
        }
    }

    private fun post(res: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(res, args.toList()))
    }

    private companion object {
        const val PREFS = "restapp"
        const val KEY_LAST_ORDER = "last_order_id"
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
