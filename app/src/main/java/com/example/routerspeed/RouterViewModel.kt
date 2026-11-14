package com.example.routerspeed

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class RouterUiState(
    val loggedIn: Boolean = false,
    val connecting: Boolean = false,
    val refreshing: Boolean = false,
    val devices: List<ActiveDevice> = emptyList(),
    val histories: Map<String, List<SpeedPoint>> = emptyMap(),
    val error: String? = null,
)

class RouterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RouterRepository()
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var uiState by mutableStateOf(RouterUiState())
        private set

    val initialAddress = preferences.getString(KEY_ADDRESS, null) ?: DEFAULT_ADDRESS
    val initialRememberPassword = preferences.getBoolean(KEY_REMEMBER, false)
    val initialPassword = when {
        !preferences.contains(KEY_REMEMBER) -> DEFAULT_PASSWORD
        initialRememberPassword -> preferences.getString(KEY_PASSWORD, null).orEmpty()
        else -> ""
    }

    private val activityTracker = DeviceActivityTracker()
    private val speedHistory = SpeedHistory()
    private var requestJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var savedAddress: String? = null
    private var savedPassword: String? = null

    init {
        if (initialRememberPassword && initialPassword.isNotEmpty()) {
            login(initialAddress, initialPassword, rememberPassword = true)
        }
    }

    fun login(address: String, password: String, rememberPassword: Boolean) {
        if (requestJob?.isActive == true) return
        when {
            address.isBlank() -> {
                uiState = uiState.copy(error = "请输入路由器地址")
                return
            }
            password.isEmpty() -> {
                uiState = uiState.copy(error = "请输入路由器密码")
                return
            }
        }

        uiState = uiState.copy(connecting = true, error = null)
        requestJob = viewModelScope.launch {
            try {
                repository.login(address, password)
                savedAddress = address
                savedPassword = password
                val snapshot = fetchSnapshot()
                saveCredentials(address, password, rememberPassword)
                uiState = RouterUiState(
                    loggedIn = true,
                    devices = snapshot.devices,
                    histories = snapshot.histories,
                )
                startAutoRefresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                activityTracker.resetTiming()
                repository.logout()
                val message = if (error is ReLoginFailedException) {
                    "自动重新登录失败：${error.cause.userMessage()}"
                } else {
                    error.userMessage()
                }
                uiState = RouterUiState(error = message)
            }
        }
    }

    fun refresh(manual: Boolean = true) {
        if (!uiState.loggedIn || requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            if (manual) uiState = uiState.copy(refreshing = true)
            try {
                val snapshot = fetchSnapshot()
                uiState = uiState.copy(
                    devices = snapshot.devices,
                    histories = snapshot.histories,
                    error = null,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                activityTracker.resetTiming()
                if (error is ReLoginFailedException) {
                    autoRefreshJob?.cancel()
                    repository.logout()
                    uiState = RouterUiState(
                        error = "自动重新登录失败：${error.cause.userMessage()}",
                    )
                } else {
                    uiState = uiState.copy(error = error.userMessage())
                }
            } finally {
                uiState = uiState.copy(refreshing = false)
            }
        }
    }

    private fun saveCredentials(address: String, password: String, rememberPassword: Boolean) {
        preferences.edit()
            .putString(KEY_ADDRESS, address)
            .putBoolean(KEY_REMEMBER, rememberPassword)
            .apply {
                if (rememberPassword) putString(KEY_PASSWORD, password) else remove(KEY_PASSWORD)
            }
            .apply()
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    fun logout() {
        autoRefreshJob?.cancel()
        requestJob?.cancel()
        repository.logout()
        activityTracker.reset()
        speedHistory.reset()
        savedAddress = null
        savedPassword = null
        uiState = RouterUiState()
    }

    private suspend fun fetchSnapshot(): RouterSnapshot {
        val devices = fetchDevicesWithReLogin()
        val now = SystemClock.elapsedRealtime()
        return RouterSnapshot(
            devices = activityTracker.update(devices, now),
            histories = speedHistory.record(devices, now),
        )
    }

    private suspend fun fetchDevicesWithReLogin(): List<Device> {
        try {
            return repository.fetchDevices()
        } catch (error: RouterException) {
            if (!error.isSessionExpired) throw error
        }

        activityTracker.resetTiming()
        val address = savedAddress ?: throw ReLoginFailedException(RouterException("登录信息不存在"))
        val password = savedPassword ?: throw ReLoginFailedException(RouterException("登录信息不存在"))
        try {
            repository.login(address, password)
            return repository.fetchDevices()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw ReLoginFailedException(error)
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                refresh(manual = false)
            }
        }
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        repository.logout()
        savedPassword = null
    }

    private fun Throwable.userMessage(): String = when (this) {
        is RouterException -> message ?: "路由器请求失败"
        is UnknownHostException -> "无法解析路由器地址，请检查地址"
        is SocketTimeoutException -> "连接路由器超时，请确认手机已连接路由器 Wi-Fi"
        is ConnectException -> "无法连接路由器，请检查地址及网络"
        is IOException -> "网络请求失败，请检查手机与路由器连接"
        else -> message ?: "操作失败，请稍后重试"
    }

    private data class RouterSnapshot(
        val devices: List<ActiveDevice>,
        val histories: Map<String, List<SpeedPoint>>,
    )

    private class ReLoginFailedException(override val cause: Throwable) : Exception(cause)

    companion object {
        private const val PREFS_NAME = "login_preferences"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER = "remember_password"
        private const val DEFAULT_ADDRESS = "192.168.3.1"
        private const val DEFAULT_PASSWORD = ""
    }
}
