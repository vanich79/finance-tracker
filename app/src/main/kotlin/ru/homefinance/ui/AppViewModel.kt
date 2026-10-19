package ru.homefinance.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.homefinance.data.Backup
import ru.homefinance.data.BackupFormatException
import ru.homefinance.data.Category
import ru.homefinance.data.MeterReading
import ru.homefinance.data.MeterType
import ru.homefinance.data.MonthTotals
import ru.homefinance.data.Repository
import ru.homefinance.data.SETTING_TWO_TARIFF
import ru.homefinance.data.Transaction
import ru.homefinance.data.TxType
import java.time.YearMonth

data class UiState(
    val month: YearMonth = YearMonth.now(),
    val categories: List<Category> = emptyList(),
    /** Операции выбранного месяца. */
    val transactions: List<Transaction> = emptyList(),
    val readings: List<MeterReading> = emptyList(),
    val tariffs: Map<MeterType, Long> = emptyMap(),
    val twoTariffElectricity: Boolean = false,
    /** Последний месяц на графиках; показываются [CHART_MONTHS] месяцев до него включительно. */
    val chartEnd: YearMonth = YearMonth.now(),
    val monthly: Map<YearMonth, MonthTotals> = emptyMap(),
) {
    val meters: List<MeterType> get() = MeterType.visible(twoTariffElectricity)
}

const val CHART_MONTHS = 12

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        change {}
    }

    fun setMonth(month: YearMonth) {
        _state.update { it.copy(month = month) }
        change {}
    }

    fun setChartEnd(month: YearMonth) {
        _state.update { it.copy(chartEnd = month) }
        change {}
    }

    fun setTwoTariffElectricity(enabled: Boolean) = change { setSetting(SETTING_TWO_TARIFF, if (enabled) "1" else "0") }

    fun saveTransaction(t: Transaction) = change { saveTransaction(t) }
    fun deleteTransaction(id: Long) = change { deleteTransaction(id) }

    fun addCategory(name: String, type: TxType, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = mutex.withLock {
                val id = withContext(Dispatchers.IO) { repo.addCategory(name.trim(), type) }
                reload()
                id
            }
            onAdded(id)
        }
    }

    fun renameCategory(id: Long, name: String) = change { renameCategory(id, name.trim()) }
    fun deleteCategory(id: Long) = change { deleteCategory(id) }

    fun saveReadings(readings: List<MeterReading>) = change { readings.forEach { saveReading(it) } }
    fun deleteReading(id: Long) = change { deleteReading(id) }
    fun setTariff(meter: MeterType, price: Long) = change { setTariff(meter, price) }

    // --- Резервная копия ---

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Короткие сообщения для всплывающей строки внизу экрана. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Копия, прочитанная из файла и ждущая подтверждения перед восстановлением. */
    private val _pendingRestore = MutableStateFlow<Backup?>(null)
    val pendingRestore: StateFlow<Backup?> = _pendingRestore.asStateFlow()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                mutex.withLock {
                    withContext(Dispatchers.IO) {
                        val backup = repo.backup()
                        val stream = resolver.openOutputStream(uri, "w") ?: error("файл недоступен для записи")
                        stream.use { it.write(backup.toJson().toByteArray(Charsets.UTF_8)) }
                        backup
                    }
                }
            }
            _messages.send(
                result.fold(
                    onSuccess = { "Копия сохранена: ${it.transactions.size} записей, ${it.readings.size} показаний" },
                    onFailure = { "Не удалось сохранить копию: ${it.message}" },
                ),
            )
        }
    }

    /** Читает файл и показывает, что в нём; данные меняются только после [confirmRestore]. */
    fun openBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = resolver.openInputStream(uri) ?: error("файл недоступен для чтения")
                    Backup.parse(stream.use { it.readBytes().toString(Charsets.UTF_8) })
                }
            }.onSuccess { _pendingRestore.value = it }
                .onFailure { _messages.send(if (it is BackupFormatException) it.message.orEmpty() else "Не удалось прочитать файл: ${it.message}") }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    fun confirmRestore() {
        val backup = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            val result = runCatching {
                mutex.withLock {
                    withContext(Dispatchers.IO) { repo.restore(backup) }
                    reload()
                }
            }
            _messages.send(
                result.fold(
                    onSuccess = { "Данные восстановлены из копии" },
                    onFailure = { "Восстановление не удалось, данные не изменены: ${it.message}" },
                ),
            )
        }
    }

    private val resolver get() = getApplication<Application>().contentResolver

    /** Изменения идут по одному, после каждого состояние перечитывается из базы. */
    private fun change(block: Repository.() -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                withContext(Dispatchers.IO) { repo.block() }
                reload()
            }
        }
    }

    private suspend fun reload() {
        val month = _state.value.month
        val chartEnd = _state.value.chartEnd
        val loaded = withContext(Dispatchers.IO) {
            UiState(
                month = month,
                categories = repo.categories(),
                transactions = repo.transactions(month.atDay(1), month.atEndOfMonth()),
                readings = repo.readings(),
                tariffs = repo.tariffs(),
                twoTariffElectricity = repo.settings()[SETTING_TWO_TARIFF] == "1",
                chartEnd = chartEnd,
                monthly = repo.monthlyTotals(chartEnd.minusMonths(CHART_MONTHS - 1L), chartEnd),
            )
        }
        // Если месяц успели переключить, данные старого месяца не показываем:
        // следующая перезагрузка уже стоит в очереди.
        _state.update { cur ->
            loaded.copy(
                month = cur.month,
                transactions = if (cur.month == month) loaded.transactions else cur.transactions,
                chartEnd = cur.chartEnd,
                monthly = if (cur.chartEnd == chartEnd) loaded.monthly else cur.monthly,
            )
        }
    }
}
