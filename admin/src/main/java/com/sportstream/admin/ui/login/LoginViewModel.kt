package com.sportstream.admin.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sportstream.admin.SportStreamAdminApp
import com.sportstream.admin.data.AdminApi
import com.sportstream.admin.data.LoginRequest
import kotlinx.coroutines.launch

/**
 * Phase 8 \u00b7 Step 8.13 \u2014 Login state machine.
 *
 * The admin authentication flow is intentionally minimal: a single
 * `POST /api/admin/auth/login` call to [AdminApi] tells us YES/NO. Real
 * RBAC, refresh-tokens, and Sentry-tagged crash breadcrumbs land in Step 8.16.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        data class Success(val token: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    private val _state = MutableLiveData<LoginState>(LoginState.Idle)
    val state: LiveData<LoginState> = _state

    private val api: AdminApi by lazy {
        val ctx = getApplication<Application>().applicationContext
        val app = ctx as SportStreamAdminApp
        AdminApi(baseUrl = SportStreamAdminApp.ADMIN_API_BASE_URL, httpClient = app.httpClient)
    }

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _state.value = LoginState.Error("Email and password are required")
            return
        }

        _state.value = LoginState.Loading
        viewModelScope.launch {
            val result = api.login(LoginRequest(email, password))
            _state.value = when (result) {
                is AdminApi.LoginResult.Success ->
                    LoginState.Success(result.token)
                is AdminApi.LoginResult.Failure ->
                    LoginState.Error(result.message)
            }
        }
    }
}
