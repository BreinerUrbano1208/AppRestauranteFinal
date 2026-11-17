package com.bremnercamilourbano.apprestaurante.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.AdminTableDto
import com.bremnercamilourbano.apprestaurante.data.AccountRepository
import com.bremnercamilourbano.apprestaurante.data.JornadaDto
import com.bremnercamilourbano.apprestaurante.data.StaffCreateRequest
import com.bremnercamilourbano.apprestaurante.data.StaffUserDto
import com.bremnercamilourbano.apprestaurante.data.JornadaOrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderItemRequest
import com.bremnercamilourbano.apprestaurante.data.TableAccountDto
import com.bremnercamilourbano.apprestaurante.data.JornadaRepository
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.data.TableRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.JsonParser
import retrofit2.HttpException

/** Mesas y resumen de ventas (solo administrador). */
class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private fun context() = getApplication<Application>()

    private fun HttpException.serverText(): String? = try {
        response()?.errorBody()?.string()
            ?.let { JsonParser.parseString(it).asJsonObject.get("error")?.asString }
    } catch (e: Exception) {
        null
    }

    private val repository = TableRepository()
    private val tableRepo get() = repository

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Las mesas las gestionan el cajero y el administrador. */
    private fun token(): String? = SessionStore.session.value?.takeIf { it.canManage }?.token

    // ---------- Mesas ----------
    private val _tables = MutableStateFlow<List<AdminTableDto>>(emptyList())
    val tables: StateFlow<List<AdminTableDto>> = _tables.asStateFlow()

    private val _tablesState = MutableStateFlow<LoadState>(LoadState.Loading)
    val tablesState: StateFlow<LoadState> = _tablesState.asStateFlow()

    // ---------- Jornada ----------
    private val jornadaRepo = JornadaRepository()

    /** Resumen en vivo de la jornada abierta; open = false si el restaurante está cerrado. */
    private val _jornada = MutableStateFlow<JornadaDto?>(null)
    val jornada: StateFlow<JornadaDto?> = _jornada.asStateFlow()

    private val _jornadaState = MutableStateFlow<LoadState>(LoadState.Loading)
    val jornadaState: StateFlow<LoadState> = _jornadaState.asStateFlow()

    /** Jornadas cerradas (solo administrador). */
    private val _jornadas = MutableStateFlow<List<JornadaDto>>(emptyList())
    val jornadas: StateFlow<List<JornadaDto>> = _jornadas.asStateFlow()

    private val _jornadaBusy = MutableStateFlow(false)
    val jornadaBusy: StateFlow<Boolean> = _jornadaBusy.asStateFlow()

    /** Mensaje de error del diálogo de cierre (contraseña incorrecta, pedidos sin terminar…). */
    private val _closeError = MutableStateFlow<String?>(null)
    val closeError: StateFlow<String?> = _closeError.asStateFlow()

    /** Se emite cuando la jornada se cerró bien (para cerrar el diálogo). */
    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    init {
        // Al cerrar sesión se borran los datos que estaban en memoria.
        viewModelScope.launch {
            SessionStore.session.collect { session ->
                if (session == null) {
                    _tables.value = emptyList()
                    _tablesState.value = LoadState.Loading
                    _jornada.value = null
                    _jornadaState.value = LoadState.Loading
                    _jornadas.value = emptyList()
                }
            }
        }
    }

    suspend fun refreshTables() {
        val token = token() ?: return
        repository.getAdminTables(token)
            .onSuccess {
                _tables.value = it
                _tablesState.value = LoadState.Loaded
            }
            .onFailure { error ->
                if (error.isUnauthorized() || error.isForbidden()) handleFailure(error)
                else if (_tables.value.isEmpty()) _tablesState.value = LoadState.Error(error.toErrorKind())
            }
    }

    fun createTable(number: Int, capacity: Int) = launchAction(R.string.msg_table_created) { token ->
        repository.create(token, number, capacity).map { }
    }

    fun setCapacity(table: AdminTableDto, capacity: Int) = launchAction(R.string.msg_table_updated) { token ->
        repository.setCapacity(token, table.id, capacity).map { }
    }

    fun toggleActive(table: AdminTableDto) = launchAction(R.string.msg_table_updated) { token ->
        repository.setActive(token, table.id, !table.active).map { }
    }

    fun deleteTable(table: AdminTableDto) = launchAction(R.string.msg_table_deleted) { token ->
        repository.delete(token, table.id)
    }

    private fun launchAction(successMessage: Int, action: suspend (String) -> Result<Unit>) {
        val token = token() ?: return
        viewModelScope.launch {
            action(token)
                .onSuccess { post(successMessage) }
                .onFailure { handleFailure(it) }
            refreshTables()
        }
    }

    // ---------- Jornada ----------
    suspend fun refreshJornada() {
        val session = SessionStore.session.value?.takeIf { it.canManage } ?: return
        jornadaRepo.summary(session.token)
            .onSuccess {
                _jornada.value = it
                _jornadaState.value = LoadState.Loaded
            }
            .onFailure { error ->
                if (error.isUnauthorized() || error.isForbidden()) handleFailure(error)
                else if (_jornada.value == null) _jornadaState.value = LoadState.Error(error.toErrorKind())
            }
        if (session.isAdmin) {
            jornadaRepo.history(session.token).onSuccess { _jornadas.value = it }
        }
    }

    /** Cajero: abre el sistema (empieza una jornada nueva). */
    fun openJornada() {
        val token = SessionStore.session.value?.takeIf { it.canManage }?.token ?: return
        if (_jornadaBusy.value) return
        _jornadaBusy.value = true
        viewModelScope.launch {
            jornadaRepo.open(token)
                .onSuccess { post(R.string.jornada_opened) }
                .onFailure { handleFailure(it) }
            refreshJornada()
            _jornadaBusy.value = false
        }
    }

    /** Cierra la jornada. Pide la contraseña de quien cierra; si es incorrecta, la jornada sigue abierta. */
    fun closeJornada(password: String) {
        val token = SessionStore.session.value?.takeIf { it.canManage }?.token ?: return
        if (_jornadaBusy.value) return
        _jornadaBusy.value = true
        _closeError.value = null
        viewModelScope.launch {
            jornadaRepo.close(token, password)
                .onSuccess {
                    post(R.string.jornada_closed)
                    _closed.tryEmit(Unit)
                }
                .onFailure { error ->
                    // Un 401 aquí es "contraseña incorrecta", no sesión vencida.
                    _closeError.value = when {
                        error is HttpException && error.code() == 401 -> context().getString(R.string.jornada_wrong_password)
                        error is HttpException -> error.serverText() ?: context().getString(R.string.error_server)
                        else -> context().getString(R.string.msg_no_connection)
                    }
                }
            refreshJornada()
            _jornadaBusy.value = false
        }
    }

    fun clearCloseError() {
        _closeError.value = null
    }

    // ---------- Personal (administrador) ----------
    private val accountRepo = AccountRepository()

    private val _staff = MutableStateFlow<List<StaffUserDto>>(emptyList())
    val staff: StateFlow<List<StaffUserDto>> = _staff.asStateFlow()

    private fun adminToken(): String? = SessionStore.session.value?.takeIf { it.isAdmin }?.token

    suspend fun refreshStaff() {
        val token = adminToken() ?: return
        accountRepo.staff(token).onSuccess { _staff.value = it }.onFailure { handleFailure(it) }
    }

    /** Crea una cuenta de cajero, cocina o administrador. Devuelve true si el servidor la aceptó. */
    suspend fun createStaff(name: String, email: String, password: String, role: String): Boolean {
        val token = adminToken() ?: return false
        val result = accountRepo.createStaff(token, StaffCreateRequest(name, email, password, role))
        result.onSuccess { post(R.string.staff_users_created) }.onFailure { serverMessage(it) }
        refreshStaff()
        return result.isSuccess
    }

    suspend fun setStaffActive(user: StaffUserDto, active: Boolean) {
        val token = adminToken() ?: return
        accountRepo.setStaffActive(token, user.id, active).onFailure { serverMessage(it) }
        refreshStaff()
    }

    /** Elimina una cuenta de cocina o caja. */
    suspend fun deleteStaff(user: StaffUserDto) {
        val token = adminToken() ?: return
        accountRepo.deleteStaff(token, user.id)
            .onSuccess { post(R.string.staff_users_deleted) }
            .onFailure { serverMessage(it) }
        refreshStaff()
    }

    // ---------- Cuenta de la mesa (cajero) ----------
    private fun serverMessage(error: Throwable) {
        val text = (error as? HttpException)?.takeIf { it.code() == 409 || it.code() == 400 }?.serverText()
        if (text != null) _messages.tryEmit(UiMessage(R.string.msg_server_error, listOf(text))) else handleFailure(error)
    }

    suspend fun fetchAccount(number: Int): TableAccountDto? {
        val token = token() ?: return null
        return tableRepo.account(token, number).onFailure { handleFailure(it) }.getOrNull()
    }

    /** Carga un pedido a la mesa. Devuelve true si el servidor lo aceptó. */
    suspend fun addTableOrder(number: Int, items: List<OrderItemRequest>): Boolean {
        val token = token() ?: return false
        val result = tableRepo.addOrder(token, number, items)
        result.onSuccess { post(R.string.tables_order_sent) }.onFailure { serverMessage(it) }
        refreshTables()
        return result.isSuccess
    }

    /** Factura la mesa: los pedidos pasan a entregados y la mesa queda libre. */
    suspend fun billTable(number: Int, method: String): Boolean {
        val token = token() ?: return false
        val result = tableRepo.bill(token, number, method)
        result.onSuccess { post(R.string.tables_billed) }.onFailure { serverMessage(it) }
        refreshTables()
        return result.isSuccess
    }

    /** Administrador: pedidos de una jornada cerrada. */
    suspend fun fetchJornadaOrders(id: Int): List<JornadaOrderDto>? {
        val token = SessionStore.session.value?.takeIf { it.isAdmin }?.token ?: return null
        return jornadaRepo.orders(token, id).onFailure { handleFailure(it) }.getOrNull()
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

    private fun post(res: Int) {
        _messages.tryEmit(UiMessage(res))
    }
}
