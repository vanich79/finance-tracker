@file:OptIn(ExperimentalMaterial3Api::class)

package ru.homefinance.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ru.homefinance.BuildConfig
import ru.homefinance.data.UpdateInfo
import ru.homefinance.data.Updates
import java.io.File

const val SITE_URL = "https://youfirstsite.ru"
const val DONATE_URL = "https://dalink.to/vanich79"

// --- Логотип Web Workshop ----------------------------------------------------------------

// Контуры знака «YF» из шапки youfirstsite.ru (viewBox 1090 80 950 595).
private const val LOGO_STROKE = "M1099 87 H1241 L1479 385 H1337 Z"
private const val LOGO_F = "M1638 87 H2030 L1938 190 H1670 L1247 665 H1123 Z"
private const val LOGO_BAR = "M1637 300 H1884 L1804 390 H1557 Z"

/**
 * Знак Web Workshop в фирменных цветах. На сайте левая черта подрезана маской вокруг «F»;
 * в векторе Android масок нет, поэтому зазор рисуется обводкой цвета фона — фон передаётся явно.
 */
@Composable
fun WebWorkshopLogo(background: Color, dark: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val stroke = if (dark) Color.White else Color(0xFF0A0D0B)
    val accent = if (dark) Color(0xFF19D32A) else Color(0xFF0E7F1A)
    val vector = remember(stroke, accent, background) {
        ImageVector.Builder("WebWorkshop", 95.dp, 59.5.dp, 950f, 595f)
            .addGroup(translationX = -1090f, translationY = -80f)
            .addPath(addPathNodes(LOGO_STROKE), fill = SolidColor(stroke))
            .addPath(addPathNodes(LOGO_F), stroke = SolidColor(background), strokeLineWidth = 14f)
            .addPath(addPathNodes(LOGO_F), fill = SolidColor(accent))
            .addPath(addPathNodes(LOGO_BAR), fill = SolidColor(accent))
            .clearGroup()
            .build()
    }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = "Web Workshop",
        modifier = modifier
            .height(height)
            .width(height * (950f / 595f)),
    )
}

// --- Действия с системой ----------------------------------------------------------------

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Не найден браузер, чтобы открыть $url", Toast.LENGTH_LONG).show()
    }
}

/**
 * Передаёт скачанный APK установщику Android. Если установка из этого приложения ещё не
 * разрешена — открывает нужный пункт настроек и возвращает false: после разрешения пользователь
 * нажимает «Установить» ещё раз.
 */
fun installApk(context: Context, file: File): Boolean {
    if (!context.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(context, "Разрешите установку из «Мои финансы» и вернитесь назад", Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return true
}

private fun sizeText(bytes: Long) = if (bytes > 0) " · %.1f МБ".format(RU, bytes / 1_048_576.0) else ""

// --- Экран настроек ---------------------------------------------------------------------

@Composable
fun SettingsScreen(
    update: UpdateState,
    autoUpdate: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Мои финансы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Учёт доходов, расходов и показаний счётчиков. Данные хранятся только на этом телефоне.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Section("Обновления") {
                ListItem(
                    headlineContent = { Text("Проверять автоматически") },
                    supportingContent = { Text("Раз в сутки при запуске спрашивать GitHub о новой версии") },
                    trailingContent = { Switch(checked = autoUpdate, onCheckedChange = onAutoUpdate) },
                    modifier = Modifier.clickable { onAutoUpdate(!autoUpdate) },
                )
                UpdateBlock(update, onCheck, onDownload, onInstall = { installApk(context, it) })
            }

            Section("Резервная копия") {
                ListItem(
                    headlineContent = { Text("Сохранить резервную копию") },
                    supportingContent = { Text("Все записи, статьи, показания и тарифы в один файл") },
                    modifier = Modifier.clickable(onClick = onExport),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Восстановить из копии") },
                    supportingContent = { Text("Заменит текущие данные содержимым файла") },
                    modifier = Modifier.clickable(onClick = onImport),
                )
            }

            Section("Разработчик") {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Home, contentDescription = null) },
                    headlineContent = { Text("Сайт разработчика") },
                    supportingContent = { Text("youfirstsite.ru — создание сайтов и приложений") },
                    modifier = Modifier.clickable { openUrl(context, SITE_URL) },
                )
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE53935)) },
                    headlineContent = { Text("Помочь разработчику") },
                    supportingContent = { Text("Приложение бесплатное — поддержать можно донатом") },
                    modifier = Modifier.clickable { openUrl(context, DONATE_URL) },
                )
                HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                    headlineContent = { Text("Исходный код и инструкция") },
                    supportingContent = { Text("github.com/${Updates.REPO}") },
                    modifier = Modifier.clickable { openUrl(context, Updates.REPO_URL) },
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(context, SITE_URL) }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WebWorkshopLogo(
                    background = MaterialTheme.colorScheme.background,
                    dark = isSystemInDarkTheme(),
                    height = 40.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text("Сделано в Web Workshop", fontWeight = FontWeight.SemiBold)
                Text(
                    "Приложение выходит в интернет только для проверки обновлений на GitHub. " +
                        "Ваши записи никуда не отправляются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Card(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun UpdateBlock(
    update: UpdateState,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (update) {
            UpdateState.Idle -> Text("Установлена версия ${BuildConfig.VERSION_NAME}")
            UpdateState.Checking -> {
                Text("Проверяю…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            UpdateState.UpToDate -> Text("У вас последняя версия — ${BuildConfig.VERSION_NAME}")
            is UpdateState.Available -> {
                Text("Доступна версия ${update.info.version}${sizeText(update.info.apkSize)}", fontWeight = FontWeight.SemiBold)
                ReleaseNotes(update.info.notes)
                Button(onClick = { onDownload(update.info) }) { Text("Скачать и установить") }
            }
            is UpdateState.Downloading -> {
                Text("Скачиваю версию ${update.info.version}…")
                LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Text("Версия ${update.info.version} скачана", fontWeight = FontWeight.SemiBold)
                Button(onClick = { onInstall(update.file) }) { Text("Установить") }
            }
            is UpdateState.Failed -> Text(update.message, color = MaterialTheme.colorScheme.error)
        }
        if (update !is UpdateState.Checking && update !is UpdateState.Downloading && update !is UpdateState.Ready) {
            OutlinedButton(onClick = onCheck) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Проверить сейчас")
            }
        }
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isNotBlank()) {
        Text(
            notes.take(600),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Окна обновления поверх любого экрана -----------------------------------------------

/** Предложение после автопроверки, ход скачивания и кнопка установки. */
@Composable
fun UpdateDialogs(
    offer: UpdateInfo?,
    update: UpdateState,
    onDismissOffer: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onPostpone: () -> Unit,
) {
    val context = LocalContext.current
    when {
        offer != null -> AlertDialog(
            onDismissRequest = onDismissOffer,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("Доступна версия ${offer.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("У вас установлена ${BuildConfig.VERSION_NAME}. Данные при обновлении сохранятся.")
                    if (offer.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        ReleaseNotes(offer.notes)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDownload(offer) }) { Text("Обновить") } },
            dismissButton = { TextButton(onClick = onDismissOffer) { Text("Позже") } },
        )
        update is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Скачиваю версию ${update.info.version}") },
            text = { LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        update is UpdateState.Ready -> AlertDialog(
            onDismissRequest = onPostpone,
            title = { Text("Версия ${update.info.version} скачана") },
            text = {
                Text(
                    "Нажмите «Установить» и подтвердите установку. Если Android попросит разрешение " +
                        "для «Мои финансы» — включите его, вернитесь и нажмите «Установить» ещё раз.",
                )
            },
            confirmButton = { TextButton(onClick = { installApk(context, update.file) }) { Text("Установить") } },
            dismissButton = { TextButton(onClick = onPostpone) { Text("Позже") } },
        )
    }
}

