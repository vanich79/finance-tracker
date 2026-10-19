@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.homefinance.data.Category
import ru.homefinance.data.Transaction
import ru.homefinance.data.TxType
import java.time.LocalDate
import java.time.YearMonth

/** Операция в процессе редактирования. id = 0 — новая. */
data class TxDraft(
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val amount: String = "",
    val categoryId: Long? = null,
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
)

fun Transaction.toDraft() = TxDraft(id, type, moneyInput(amount), categoryId, note, date)

@Composable
fun OperationsScreen(state: UiState, onMonth: (YearMonth) -> Unit, onOpen: (TxDraft) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<TxType?>(null) }
    val income = state.transactions.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = state.transactions.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val shown = state.transactions.filter { filter == null || it.type == filter }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item { MonthSwitcher(state.month, onMonth) }
            item { SummaryCard(income, expense) }
            item {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Все", TxType.EXPENSE to "Расходы", TxType.INCOME to "Доходы").forEach { (t, label) ->
                        FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(label) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "За этот месяц записей нет. Нажмите «+», чтобы добавить доход или расход.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            shown.groupBy { it.date }.forEach { (date, list) ->
                item(key = "day-$date") {
                    Text(
                        formatDay(date),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { tx ->
                    TransactionRow(tx, onClick = { onOpen(tx.toDraft()) })
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpen(TxDraft(date = defaultDate(state.month))) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить операцию")
        }
    }
}

@Composable
private fun SummaryCard(income: Long, expense: Long) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryCell("Доходы", formatMoney(income), incomeColor(), Modifier.weight(1f))
            SummaryCell("Расходы", formatMoney(expense), expenseColor(), Modifier.weight(1f))
            val balance = income - expense
            SummaryCell(
                "Остаток",
                formatMoney(balance, withSign = true),
                if (balance >= 0) incomeColor() else expenseColor(),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(tx.categoryName ?: "Без статьи", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (tx.note.isNotBlank()) {
            { Text(tx.note, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else null,
        trailingContent = {
            val sign = if (tx.type == TxType.INCOME) "+" else "−"
            Text(
                sign + formatMoney(tx.amount),
                color = amountColor(tx.type),
                fontWeight = FontWeight.SemiBold,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun TransactionDialog(
    initial: TxDraft,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Long) -> Unit,
    onAddCategory: (String, TxType, (Long) -> Unit) -> Unit,
) {
    var d by remember { mutableStateOf(initial) }
    var tried by remember { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = parseMoney(d.amount)
    val amountError = when {
        amount == null -> "Введите сумму, например 1250 или 99,90"
        amount <= 0 -> "Сумма должна быть больше нуля"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (d.id == 0L) "Новая запись" else "Изменить запись") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TypeSwitch(
                    d.type,
                    onChange = { t ->
                        val keep = categories.any { it.id == d.categoryId && it.type == t }
                        d = d.copy(type = t, categoryId = if (keep) d.categoryId else null)
                    },
                    expenseLabel = "Расход",
                    incomeLabel = "Доход",
                )
                OutlinedTextField(
                    value = d.amount,
                    onValueChange = { d = d.copy(amount = it) },
                    label = { Text("Сумма, ₽") },
                    singleLine = true,
                    isError = tried && amountError != null,
                    supportingText = if (tried && amountError != null) ({ Text(amountError) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(
                    categories = categories.filter { it.type == d.type },
                    selectedId = d.categoryId,
                    onSelect = { d = d.copy(categoryId = it) },
                    onNew = { addingCategory = true },
                )
                OutlinedTextField(
                    value = d.note,
                    onValueChange = { d = d.copy(note = it) },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(d.date, { d = d.copy(date = it) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (amount == null || amountError != null) {
                    tried = true
                } else {
                    onSave(Transaction(d.id, d.type, amount, d.categoryId, null, d.note.trim(), d.date))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (d.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )

    if (addingCategory) {
        TextInputDialog(
            title = if (d.type == TxType.EXPENSE) "Новая статья расходов" else "Новая статья доходов",
            label = "Название",
            initial = "",
            confirmText = "Добавить",
            onDismiss = { addingCategory = false },
            onConfirm = { name ->
                addingCategory = false
                onAddCategory(name, d.type) { id -> d = d.copy(categoryId = id) }
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить запись?",
            text = "${formatMoney(amount ?: 0)} от ${formatDate(d.date)} будет удалена без возможности восстановления.",
            confirmText = "Удалить",
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete(d.id) },
        )
    }
}

@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = categories.firstOrNull { it.id == selectedId }?.name ?: "Без статьи"
    Box {
        OutlinedTextField(
            value = name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Статья") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Поле только для чтения не ловит нажатия — перекрываем его прозрачной областью.
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = {
                    onSelect(c.id)
                    expanded = false
                })
            }
            DropdownMenuItem(text = { Text("Без статьи") }, onClick = {
                onSelect(null)
                expanded = false
            })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Новая статья…") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onNew()
                },
            )
        }
    }
}
