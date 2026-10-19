package ru.homefinance.ui

import android.app.Application
import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homefinance.BuildConfig
import ru.homefinance.data.UpdateInfo
import ru.homefinance.data.Updates
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val updatesDir = File(app.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _auto = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val auto: StateFlow<Boolean> = _auto.asStateFlow()

    /** Новая версия, найденная автоматической проверкой: о ней спрашиваем один раз за запуск. */
    private val _offer = MutableStateFlow<UpdateInfo?>(null)
    val offer: StateFlow<UpdateInfo?> = _offer.asStateFlow()

    init {
        // Файл прошлого обновления уже установлен (или не нужен) — место не занимаем.
        viewModelScope.launch(Dispatchers.IO) { updatesDir.listFiles()?.forEach { it.delete() } }
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (_auto.value && System.currentTimeMillis() - last > DAY_MS) check(silent = true)
    }

    fun setAuto(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _auto.value = enabled
    }

    fun checkNow() = check(silent = false)

    fun dismissOffer() {
        _offer.value = null
    }

    /** Закрыть окно «скачано» без установки: файл остаётся, кнопка в настройках снова доступна. */
    fun postpone() {
        val s = _state.value
        if (s is UpdateState.Ready) _state.value = UpdateState.Available(s.info)
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _offer.value = null
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(info, 0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = File(updatesDir, "MoiFinansy-${info.version}.apk")
                    if (!file.exists()) {
                        Updates.download(info.apkUrl, file) { _state.value = UpdateState.Downloading(info, it) }
                    }
                    verify(file)
                    file
                }
            }
            _state.value = result.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed("Не удалось скачать обновление: ${it.message ?: "ошибка сети"}") },
            )
        }
    }

    private fun check(silent: Boolean) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { withContext(Dispatchers.IO) { Updates.fetchLatest() } }
            if (result.isSuccess) prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val info = result.getOrNull()
            _state.value = when {
                result.isFailure ->
                    if (silent) UpdateState.Idle
                    else UpdateState.Failed("Не удалось проверить обновления. Проверьте интернет и попробуйте ещё раз.")
                info != null && Updates.isNewer(info.version, BuildConfig.VERSION_NAME) -> {
                    if (silent) _offer.value = info
                    UpdateState.Available(info)
                }
                silent -> UpdateState.Idle
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Ставим только своё приложение и только более новую версию — иначе понятная ошибка вместо отказа установщика. */
    private fun verify(file: File) {
        val app = getApplication<Application>()
        val info = app.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null) {
            file.delete()
            throw IOException("файл повреждён, попробуйте ещё раз")
        }
        if (info.packageName != app.packageName) {
            file.delete()
            throw IOException("в релизе файл другого приложения")
        }
        if (PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            file.delete()
            throw IOException("в релизе нет более новой версии")
        }
    }

    private companion object {
        const val KEY_AUTO = "auto"
        const val KEY_LAST_CHECK = "last_check"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
