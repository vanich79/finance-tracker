@file:OptIn(ExperimentalMaterial3Api::class)

package ru.homefinance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.homefinance.data.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val RU: Locale = Locale.forLanguageTag("ru-RU")
private const val DAY_MS = 24L * 60 * 60 * 1000

// --- Числа и даты -----------------------------------------------------------------------

fun formatMoney(kopecks: Long, withSign: Boolean = false): String {
    val nf = NumberFormat.getNumberInstance(RU).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val sign = when {
        kopecks < 0 -> "−"
        withSign && kopecks > 0 -> "+"
        else -> ""
    }
    return sign + nf.format(BigDecimal.valueOf(abs(kopecks), 2)) + " ₽"
}

/** Сумма для поля ввода: «1250» или «1250,50». */
fun moneyInput(kopecks: Long): String =
    if (kopecks % 100 == 0L) (kopecks / 100).toString()
    else BigDecimal.valueOf(kopecks, 2).toPlainString().replace('.', ',')

fun parseDecimal(text: String): BigDecimal? =
    text.replace(" ", "").replace(" ", "").replace(',', '.').toBigDecimalOrNull()

/** Копейки из введённого текста; null, если это не число. */
fun parseMoney(text: String): Long? = runCatching {
    parseDecimal(text)?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()
}.getOrNull()

fun formatReading(value: Double): String =
    NumberFormat.getNumberInstance(RU).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
    }.format(value)

fun readingInput(value: Double): String = formatReading(value).replace(" ", "").replace(" ", "")

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dayFormat = DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)
private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun formatDay(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(dayFormat)
    }
}

fun formatMonth(month: YearMonth): String = month.format(monthFormat).replaceFirstChar { it.titlecase(RU) }

/** Дата по умолчанию для новой записи в выбранном месяце. */
fun defaultDate(month: YearMonth): LocalDate {
    val today = LocalDate.now()
    return if (YearMonth.from(today) == month) today else month.atEndOfMonth()
}

// --- Цвета ------------------------------------------------------------------------------

@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
fun expenseColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun amountColor(type: TxType): Color = if (type == TxType.INCOME) incomeColor() else expenseColor()

// --- Общие элементы ---------------------------------------------------------------------

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
        }
        Text(
            formatMonth(month),
            style = MaterialTheme.typography.titleMedium,
            // Нажатие на название возвращает к текущему месяцу.
            modifier = Modifier
                .clickable { onChange(YearMonth.now()) }
                .padding(8.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
        }
    }
}

@Composable
fun TypeSwitch(
    type: TxType,
    onChange: (TxType) -> Unit,
    modifier: Modifier = Modifier,
    expenseLabel: String = "Расходы",
    incomeLabel: String = "Доходы",
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        listOf(TxType.EXPENSE to expenseLabel, TxType.INCOME to incomeLabel).forEachIndexed { i, (t, label) ->
            SegmentedButton(
                selected = type == t,
                onClick = { onChange(t) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
            ) { Text(label) }
        }
    }
}

@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Default.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(formatDate(date))
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / DAY_MS)) }
                    open = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Диалог с одним полем: название статьи, тариф и т. п. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmText: String = "Сохранить",
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> String? = { if (it.isBlank()) "Введите значение" else null },
    extraButton: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    var tried by remember { mutableStateOf(false) }
    val error = validate(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    isError = tried && error != null,
                    supportingText = if (tried && error != null) ({ Text(error) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (error == null) onConfirm(text.trim()) else tried = true }) { Text(confirmText) }
        },
        dismissButton = {
            Row {
                extraButton?.invoke()
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
