package ru.homefinance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.homefinance.data.MeterReading
import ru.homefinance.data.MeterType
import ru.homefinance.data.TxType
import ru.homefinance.data.cost
import ru.homefinance.data.usage
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun MetersScreen(
    state: UiState,
    onSaveReadings: (List<MeterReading>) -> Unit,
    onDeleteReading: (Long) -> Unit,
    onTariff: (MeterType, Long) -> Unit,
    onToExpense: (TxDraft) -> Unit,
    onTwoTariff: (Boolean) -> Unit,
) {
    var entering by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MeterReading?>(null) }
    var tariffFor by remember { mutableStateOf<MeterType?>(null) }

    // Показания уже идут от новых к старым; для каждого находим предыдущее того же счётчика.
    val byMeter = MeterType.entries.associateWith { m -> state.readings.filter { it.meter == m } }
    val previous = buildMap {
        byMeter.values.forEach { list -> list.zipWithNext { newer, older -> put(newer.id, older) } }
    }
    val housingCategory = state.categories.firstOrNull { it.type == TxType.EXPENSE && it.name == "ЖКХ" }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.meters) { meter ->
                val list = byMeter.getValue(meter)
                MeterCard(
                    meter = meter,
                    last = list.getOrNull(0),
                    prev = list.getOrNull(1),
                    tariff = state.tariffs[meter] ?: 0L,
                    onTariff = { tariffFor = meter },
                    onToExpense = { last, use, sum ->
                        onToExpense(
                            TxDraft(
                                type = TxType.EXPENSE,
                                amount = moneyInput(sum),
                                categoryId = housingCategory?.id,
                                note = "${meter.short}: ${formatReading(use.toDouble())} ${meter.unit} × " +
                                    "${formatMoney(state.tariffs[meter] ?: 0L)}",
                                date = last.date,
                            ),
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Двухтарифный счётчик света") },
                    supportingContent = {
                        Text(
                            if (state.twoTariffElectricity) {
                                "Показания и тарифы отдельно для дня (Т1) и ночи (Т2)"
                            } else {
                                "Включите, если счётчик считает день и ночь отдельно"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(checked = state.twoTariffElectricity, onCheckedChange = onTwoTariff)
                    },
                    modifier = Modifier.clickable { onTwoTariff(!state.twoTariffElectricity) },
                )
            }
            item {
                Text(
                    "История показаний",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (state.readings.isEmpty()) {
                item {
                    Text(
                        "Показаний пока нет. Нажмите «Внести показания».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.readings, key = { it.id }) { r ->
                val prev = previous[r.id]
                ListItem(
                    headlineContent = { Text("${r.meter.short}: ${formatReading(r.value)} ${r.meter.unit}") },
                    supportingContent = { Text(formatDate(r.date)) },
                    trailingContent = {
                        if (prev != null) {
                            val use = usage(r, prev)
                            Text(
                                (if (use.signum() > 0) "+" else "") + formatReading(use.toDouble()),
                                color = if (use.signum() < 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickable { editing = r },
                )
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = { entering = true },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            text = { Text("Внести показания") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (entering) {
        ReadingsEntryDialog(
            last = state.meters.associateWith { byMeter.getValue(it).firstOrNull() },
            onDismiss = { entering = false },
            onSave = {
                onSaveReadings(it)
                entering = false
            },
        )
    }
    editing?.let { r ->
        ReadingEditDialog(
            reading = r,
            onDismiss = { editing = null },
            onSave = {
                onSaveReadings(listOf(it))
                editing = null
            },
            onDelete = {
                onDeleteReading(r.id)
                editing = null
            },
        )
    }
    tariffFor?.let { meter ->
        val current = state.tariffs[meter] ?: 0L
        TextInputDialog(
            title = "Тариф: ${meter.title}",
            label = "Цена за 1 ${meter.unit}, ₽",
            initial = if (current > 0) moneyInput(current) else "",
            keyboardType = KeyboardType.Decimal,
            validate = { text ->
                val v = parseMoney(text)
                if (v == null || v < 0) "Введите цену, например 54,81" else null
            },
            onDismiss = { tariffFor = null },
            onConfirm = {
                onTariff(meter, parseMoney(it) ?: 0L)
                tariffFor = null
            },
        )
    }
}

@Composable
private fun MeterCard(
    meter: MeterType,
    last: MeterReading?,
    prev: MeterReading?,
    tariff: Long,
    onTariff: () -> Unit,
    onToExpense: (last: MeterReading, usage: BigDecimal, cost: Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${meter.short} · ${meter.title}", style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text("Показаний ещё нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Текущие: ${formatReading(last.value)} ${meter.unit} от ${formatDate(last.date)}")
            }
            val use = if (last != null && prev != null) usage(last, prev) else null
            val sum = if (use != null && use.signum() >= 0) cost(use, tariff) else 0L
            if (last != null && prev != null && use != null) {
                Text(
                    "Предыдущие: ${formatReading(prev.value)} от ${formatDate(prev.date)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (use.signum() < 0) {
                    Text(
                        "Текущие показания меньше предыдущих — проверьте ввод",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val money = if (tariff > 0) " · ${formatMoney(sum)}" else ""
                    Text(
                        "Расход: ${formatReading(use.toDouble())} ${meter.unit}$money",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTariff) {
                    Text(if (tariff > 0) "Тариф: ${formatMoney(tariff)}/${meter.unit}" else "Указать тариф")
                }
                if (last != null && use != null && sum > 0) {
                    TextButton(onClick = { onToExpense(last, use, sum) }) { Text("В расходы") }
                }
            }
        }
    }
}

/** Ввод показаний сразу по всем счётчикам на одну дату; пустые поля пропускаются. */
@Composable
private fun ReadingsEntryDialog(
    last: Map<MeterType, MeterReading?>,
    onDismiss: () -> Unit,
    onSave: (List<MeterReading>) -> Unit,
) {
    val values = remember { mutableStateMapOf<MeterType, String>() }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var tried by remember { mutableStateOf(false) }
    val meters = last.keys.toList()
    val parsed = meters.associateWith { m -> values[m]?.takeIf { it.isNotBlank() }?.let { parseDecimal(it) } }
    val bad = meters.filter { m -> !values[m].isNullOrBlank() && parsed[m] == null }
    val filled = parsed.filterValues { it != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Показания счётчиков") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                meters.forEach { m ->
                    val prev = last[m]
                    val entered = parsed[m]
                    val lower = entered != null && prev != null && entered.toDouble() < prev.value
                    OutlinedTextField(
                        value = values[m] ?: "",
                        onValueChange = { values[m] = it },
                        label = { Text("${m.short}, ${m.unit}") },
                        singleLine = true,
                        isError = (tried && m in bad) || lower,
                        supportingText = {
                            when {
                                tried && m in bad -> Text("Это не число")
                                lower -> Text("Меньше предыдущих (${formatReading(prev!!.value)})")
                                prev != null -> Text("Предыдущие: ${formatReading(prev.value)}")
                                else -> Text("Первое показание")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DateField(date, { date = it }, Modifier.fillMaxWidth())
                if (tried && filled.isEmpty() && bad.isEmpty()) {
                    Text("Заполните хотя бы один счётчик", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (bad.isNotEmpty() || filled.isEmpty()) {
                    tried = true
                } else {
                    onSave(filled.map { (m, v) -> MeterReading(0, m, v!!.toDouble(), date) })
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ReadingEditDialog(
    reading: MeterReading,
    onDismiss: () -> Unit,
    onSave: (MeterReading) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(readingInput(reading.value)) }
    var date by remember { mutableStateOf(reading.date) }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseDecimal(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(reading.meter.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Показания, ${reading.meter.unit}") },
                    singleLine = true,
                    isError = value == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(date, { date = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let { onSave(reading.copy(value = it.toDouble(), date = date)) } },
                enabled = value != null,
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить показания?",
            text = "${reading.meter.short}: ${formatReading(reading.value)} ${reading.meter.unit} от ${formatDate(reading.date)}",
            confirmText = "Удалить",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}
