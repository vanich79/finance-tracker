@file:OptIn(ExperimentalMaterial3Api::class)

package ru.homefinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.homefinance.ui.AppViewModel
import ru.homefinance.ui.BarChartIcon
import ru.homefinance.ui.CategoriesScreen
import ru.homefinance.ui.ChartsScreen
import ru.homefinance.ui.ConfirmDialog
import ru.homefinance.ui.FinanceTheme
import ru.homefinance.ui.MetersScreen
import ru.homefinance.ui.OperationsScreen
import ru.homefinance.ui.SettingsScreen
import ru.homefinance.ui.TransactionDialog
import ru.homefinance.ui.TxDraft
import ru.homefinance.ui.UpdateDialogs
import ru.homefinance.ui.UpdateViewModel
import ru.homefinance.ui.formatDate
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTheme { FinanceApp() }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Операции", Icons.AutoMirrored.Filled.List),
    Tab("Статьи", Icons.Default.ShoppingCart),
    Tab("Графики", BarChartIcon),
    Tab("Счётчики", Icons.Default.Home),
)

@Composable
private fun FinanceApp(vm: AppViewModel = viewModel(), updates: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val updateOffer by updates.offer.collectAsStateWithLifecycle()
    val autoUpdate by updates.auto.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Диалог операции общий: его открывают и список операций, и счётчики («В расходы»).
    var draft by remember { mutableStateOf<TxDraft?>(null) }
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }
    // Тип не ограничиваем: пересланный через мессенджер файл часто приходит как octet-stream.
    // Чужой файл всё равно не пройдёт проверку при разборе.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::openBackup) }
    val exportBackup = { exportLauncher.launch("finance-backup-${LocalDate.now()}.json") }
    val importBackup = { importLauncher.launch(arrayOf("*/*")) }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            update = update,
            autoUpdate = autoUpdate,
            snackbar = snackbar,
            onBack = { showSettings = false },
            onAutoUpdate = updates::setAuto,
            onCheck = updates::checkNow,
            onDownload = updates::download,
            onExport = exportBackup,
            onImport = importBackup,
        )
    } else Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[tab].title) },
                actions = {
                    TopMenu(onExport = exportBackup, onImport = importBackup, onSettings = { showSettings = true })
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                0 -> OperationsScreen(state, onMonth = vm::setMonth, onOpen = { draft = it })
                1 -> CategoriesScreen(
                    state,
                    onMonth = vm::setMonth,
                    onAdd = { name, type -> vm.addCategory(name, type) },
                    onRename = vm::renameCategory,
                    onDelete = vm::deleteCategory,
                )
                2 -> ChartsScreen(state, onChartEnd = vm::setChartEnd)
                else -> MetersScreen(
                    state,
                    onSaveReadings = vm::saveReadings,
                    onDeleteReading = vm::deleteReading,
                    onTariff = vm::setTariff,
                    onToExpense = { draft = it },
                    onTwoTariff = vm::setTwoTariffElectricity,
                )
            }
        }
    }

    draft?.let { d ->
        key(d) {
            TransactionDialog(
                initial = d,
                categories = state.categories,
                onDismiss = { draft = null },
                onSave = {
                    vm.saveTransaction(it)
                    draft = null
                },
                onDelete = {
                    vm.deleteTransaction(it)
                    draft = null
                },
                onAddCategory = { name, type, onAdded -> vm.addCategory(name, type, onAdded) },
            )
        }
    }

    pendingRestore?.let { backup ->
        val date = backup.exportedAt?.let { " от ${formatDate(it)}" }.orEmpty()
        ConfirmDialog(
            title = "Восстановить из копии$date?",
            text = "В файле: ${backup.transactions.size} записей доходов и расходов, " +
                "${backup.categories.size} статей, ${backup.readings.size} показаний счётчиков.\n\n" +
                "Все текущие данные в приложении будут заменены содержимым файла. " +
                "Если сомневаетесь, сначала сохраните резервную копию текущих данных.",
            confirmText = "Заменить",
            onDismiss = vm::cancelRestore,
            onConfirm = vm::confirmRestore,
        )
    }

    UpdateDialogs(
        offer = updateOffer,
        update = update,
        onDismissOffer = updates::dismissOffer,
        onDownload = updates::download,
        onPostpone = updates::postpone,
    )
}

@Composable
private fun TopMenu(onExport: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "Сохранить резервную копию" to onExport,
                "Восстановить из копии" to onImport,
                "Настройки и о приложении" to onSettings,
            ).forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}
