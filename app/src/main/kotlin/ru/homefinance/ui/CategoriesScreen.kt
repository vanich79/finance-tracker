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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.homefinance.data.Category
import ru.homefinance.data.TxType
import java.time.YearMonth

/** Статьи доходов и расходов: суммы за месяц и управление списком. */
@Composable
fun CategoriesScreen(
    state: UiState,
    onMonth: (YearMonth) -> Unit,
    onAdd: (String, TxType) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    val txs = state.transactions.filter { it.type == type }
    val sums = txs.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val total = txs.sumOf { it.amount }
    // Сначала статьи с движением за месяц, остальные — по алфавиту (сортировка устойчивая).
    val rows = state.categories.filter { it.type == type }
        .map { it to (sums[it.id] ?: 0L) }
        .sortedByDescending { it.second }
    val withoutCategory = sums[null] ?: 0L

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item { MonthSwitcher(state.month, onMonth) }
            item { TypeSwitch(type, { type = it }) }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (type == TxType.EXPENSE) "Всего расходов" else "Всего доходов")
                    Text(formatMoney(total), fontWeight = FontWeight.SemiBold, color = amountColor(type))
                }
                HorizontalDivider()
            }
            items(rows, key = { it.first.id }) { (category, sum) ->
                CategoryRow(category.name, sum, total, type, Modifier.clickable { editing = category })
            }
            if (withoutCategory > 0) {
                item { CategoryRow("Без статьи", withoutCategory, total, type, Modifier) }
            }
            item {
                Text(
                    "Нажмите на статью, чтобы переименовать или удалить её.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Статья") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (adding) {
        TextInputDialog(
            title = if (type == TxType.EXPENSE) "Новая статья расходов" else "Новая статья доходов",
            label = "Название",
            initial = "",
            confirmText = "Добавить",
            onDismiss = { adding = false },
            onConfirm = {
                onAdd(it, type)
                adding = false
            },
        )
    }
    editing?.let { category ->
        TextInputDialog(
            title = "Статья",
            label = "Название",
            initial = category.name,
            onDismiss = { editing = null },
            onConfirm = {
                onRename(category.id, it)
                editing = null
            },
            extraButton = {
                TextButton(onClick = {
                    editing = null
                    deleting = category
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "Удалить статью «${category.name}»?",
            text = "Записи с этой статьёй сохранятся и будут показаны как «Без статьи».",
            confirmText = "Удалить",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(category.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun CategoryRow(name: String, sum: Long, total: Long, type: TxType, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, modifier = Modifier.weight(1f))
            Text(
                if (sum > 0) formatMoney(sum) else "—",
                color = if (sum > 0) amountColor(type) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sum > 0 && total > 0) {
            val share = sum.toFloat() / total
            LinearProgressIndicator(
                progress = { share },
                color = amountColor(type),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            Text(
                "${(share * 100).toInt()} %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
