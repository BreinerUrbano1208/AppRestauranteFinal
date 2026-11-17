package com.bremnercamilourbano.apprestaurante.data

class AccountRepository(private val api: ProductsApi = ProductsApi.create()) {

    suspend fun register(request: ClientRegisterRequest): Result<RegisterResponse> =
        safeCall { api.registerClient(request) }

    suspend fun login(request: ClientLoginRequest): Result<ClientAuthResponse> =
        safeCall { api.loginClient(request) }

    suspend fun verify(email: String, code: String): Result<ClientAuthResponse> =
        safeCall { api.verifyEmail(VerifyRequest(email, code)) }

    suspend fun resend(email: String): Result<OkResponse> = safeCall { api.resendCode(EmailRequest(email)) }

    suspend fun forgot(email: String): Result<OkResponse> = safeCall { api.forgotPassword(EmailRequest(email)) }

    suspend fun reset(email: String, code: String, password: String): Result<OkResponse> =
        safeCall { api.resetPassword(ResetPasswordRequest(email, code, password)) }

    suspend fun deleteAccount(token: String, password: String): Result<OkResponse> =
        safeCall { api.deleteAccount("Bearer $token", DeleteAccountRequest(password)) }

    suspend fun deleteStaff(token: String, id: Int): Result<OkResponse> =
        safeCall { api.deleteStaff("Bearer $token", id) }

    suspend fun updateProfile(token: String, request: ProfileUpdateRequest): Result<UserDto> =
        safeCall { api.updateProfile("Bearer $token", request) }

    suspend fun staff(token: String): Result<List<StaffUserDto>> =
        safeCall { api.getStaff("Bearer $token") }

    suspend fun createStaff(token: String, request: StaffCreateRequest): Result<StaffUserDto> =
        safeCall { api.createStaff("Bearer $token", request) }

    suspend fun setStaffActive(token: String, id: Int, active: Boolean): Result<StaffUserDto> =
        safeCall { api.setStaffActive("Bearer $token", id, StaffActiveRequest(active)) }

    suspend fun myOrders(token: String): Result<List<OrderDto>> =
        safeCall { api.getMyOrders("Bearer $token") }
}
