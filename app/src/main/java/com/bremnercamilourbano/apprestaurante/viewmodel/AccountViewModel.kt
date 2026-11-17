package com.bremnercamilourbano.apprestaurante.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.AccountRepository
import com.bremnercamilourbano.apprestaurante.data.ClientLoginRequest
import com.bremnercamilourbano.apprestaurante.data.ClientRegisterRequest
import com.bremnercamilourbano.apprestaurante.data.ClientAuthResponse
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.data.ProfileUpdateRequest
import com.bremnercamilourbano.apprestaurante.data.Roles
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Cuenta del cliente: crear cuenta, entrar, salir y ver "mis pedidos". */
class AccountViewModel : ViewModel() {

    private val repository = AccountRepository()

    val client = ClientStore.session

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** La contraseña (o el correo) no coincide: la pantalla de entrada marca el campo. */
    private val _loginFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val loginFailed: SharedFlow<Unit> = _loginFailed.asSharedFlow()

    private val _orders = MutableStateFlow<List<OrderDto>>(emptyList())
    val orders: StateFlow<List<OrderDto>> = _orders.asStateFlow()

    /** El correo falta por verificar: la pantalla abre el diálogo para escribir el código. */
    private val _verifyRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val verifyRequests: SharedFlow<String> = _verifyRequests.asSharedFlow()

    fun register(name: String, email: String, phone: String, password: String, acceptData: Boolean) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.register(ClientRegisterRequest(name.trim(), email.trim(), phone, password, acceptData))
                .onSuccess {
                    val token = it.token
                    val user = it.user
                    if (it.needsVerification) {
                        _messages.tryEmit(UiMessage(R.string.verify_code_sent))
                        _verifyRequests.tryEmit(it.email ?: email.trim())
                    } else if (token != null && user != null) {
                        enter(ClientAuthResponse(token, it.role, user))
                    }
                }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    fun login(email: String, password: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.login(ClientLoginRequest(email.trim(), password))
                .onSuccess { enter(it) }
                .onFailure {
                    val pending = it.verificationEmailOrNull()
                    if (pending != null) {
                        _messages.tryEmit(UiMessage(R.string.verify_code_sent))
                        _verifyRequests.tryEmit(pending.ifEmpty { email.trim() })
                    } else if (it.isUnauthorized()) {
                        _loginFailed.tryEmit(Unit)
                        _messages.tryEmit(UiMessage(R.string.login_error_credentials))
                    } else {
                        _messages.tryEmit(it.toUiMessage())
                    }
                }
            _busy.value = false
        }
    }

    /** Verificar el correo con el código de 6 dígitos; si es correcto, entra con sesión. */
    fun verify(email: String, code: String, onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.verify(email, code.trim())
                .onSuccess { enter(it); onDone() }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    fun resend(email: String) {
        viewModelScope.launch {
            repository.resend(email)
                .onSuccess { _messages.tryEmit(UiMessage(R.string.verify_code_sent)) }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
        }
    }

    /** Olvidé mi contraseña, paso 1: el servidor manda un código al correo. */
    fun forgot(email: String, onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.forgot(email.trim())
                .onSuccess { _messages.tryEmit(UiMessage(R.string.recover_code_sent)); onDone() }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    /** Paso 2: código + contraseña nueva. */
    fun reset(email: String, code: String, password: String, onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.reset(email.trim(), code.trim(), password)
                .onSuccess { _messages.tryEmit(UiMessage(R.string.recover_done)); onDone() }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    /** Eliminar mi cuenta y mis datos (Ley 1581). */
    fun deleteAccount(password: String, onDone: () -> Unit) {
        val token = ClientStore.session.value?.token ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.deleteAccount(token, password)
                .onSuccess {
                    onDone()
                    logout()
                    _messages.tryEmit(UiMessage(R.string.delete_account_done))
                }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    /** El correo decide el rol: cliente va a la tienda; cocina, cajero y administrador, a su trabajo. */
    private fun enter(response: ClientAuthResponse) {
        val role = response.role ?: response.user.role ?: Roles.CLIENTE
        if (role == Roles.CLIENTE) {
            SessionStore.clear()
            ClientStore.start(response.token, response.user)
        } else {
            ClientStore.clear()
            SessionStore.start(response.token, role)
        }
        _messages.tryEmit(UiMessage(R.string.profile_welcome, listOf(response.user.name)))
    }

    /** Editar perfil: datos personales y foto. */
    fun updateProfile(request: ProfileUpdateRequest, onDone: () -> Unit = {}) {
        val token = ClientStore.session.value?.token ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repository.updateProfile(token, request)
                .onSuccess {
                    ClientStore.updateProfile(it)
                    _messages.tryEmit(UiMessage(R.string.profile_updated))
                    onDone()
                }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    fun logout() {
        ClientStore.clear()
        _orders.value = emptyList()
    }

    suspend fun refreshOrders() {
        val token = ClientStore.session.value?.token ?: return
        repository.myOrders(token)
            .onSuccess { _orders.value = it }
            .onFailure { error ->
                if (error.isUnauthorized()) {
                    ClientStore.clear()
                    _messages.tryEmit(UiMessage(R.string.msg_client_session_expired))
                }
            }
    }
}
