@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.homefinance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.homefinance.data.MeterType
import ru.homefinance.data.cost
import ru.homefinance.data.monthlyUsage
import java.math.BigDecimal
import java.time.YearMonth

/** Значок вкладки: в базовом наборе Material-иконок столбчатой диаграммы нет. */
val BarChartIcon: ImageVector = ImageVector.Builder("BarChart", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        pathData = addPathNodes("M5,9.2h3V19H5V9.2z M10.6,5h2.8v14h-2.8V5z M16.2,13H19v6h-2.8V13z"),
        fill = SolidColor(Color.Black),
    )
    .build()

private val shortMonths = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun shortMonth(m: YearMonth) = shortMonths[m.monthValue - 1]

/** «1,2 млн», «150 тыс», «950» — подписи к оси, где полная сумма не помещается. */
private fun compactNumber(value: Double): String = when {
    value >= 1_000_000 -> formatReading(Math.round(value / 100_000) / 10.0) + " млн"
    value >= 10_000 -> formatReading(Math.round(value / 1000).toDouble()) + " тыс"
    else -> formatReading(Math.round(value).toDouble())
}

@Composable
fun ChartsScreen(state: UiState, onChartEnd: (YearMonth) -> Unit) {
    val months = (CHART_MONTHS - 1 downTo 0).map { state.chartEnd.minusMonths(it.toLong()) }
    // При сдвиге периода выбор возвращается на последний месяц.
    var selected by rememberSaveable(state.chartEnd) { mutableIntStateOf(months.lastIndex) }
    val month = months[selected]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChartEnd(state.chartEnd.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Раньше")
            }
            Text(
                "${shortMonth(months.first())} ${months.first().year} — ${shortMonth(months.last())} ${months.last().year}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChartEnd(state.chartEnd.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Позже")
            }
        }

        MoneyChartCard(state, months, selected, onSelect = { selected = it })
        UsageChartCard(state, months, selected, onSelect = { selected = it })

        Text(
            "Нажмите на столбец, чтобы увидеть цифры за месяц. Сейчас выбран: ${formatMonth(month)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoneyChartCard(state: UiState, months: List<YearMonth>, selected: Int, onSelect: (Int) -> Unit) {
    val income = incomeColor()
    val expense = expenseColor()
    val totals = months.map { state.monthly[it] }
    val current = totals[selected]
    val withData = totals.filterNotNull()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Доходы и расходы", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Доходы", income)
                Legend("Расходы", expense)
            }
            BarChart(
                labels = months.map(::shortMonth),
                series = listOf(
                    totals.map { (it?.income ?: 0) / 100.0 },
                    totals.map { (it?.expense ?: 0) / 100.0 },
                ),
                colors = listOf(income, expense),
                selected = selected,
                onSelect = onSelect,
                axisLabel = { compactNumber(it) + " ₽" },
            )
            Text(formatMonth(months[selected]), fontWeight = FontWeight.SemiBold)
            if (current == null) {
                Text("Записей нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ValueRow("Доходы", formatMoney(current.income), income)
                ValueRow("Расходы", formatMoney(current.expense), expense)
                val balance = current.income - current.expense
                ValueRow("Остаток", formatMoney(balance, withSign = true), if (balance >= 0) income else expense)
            }
            if (withData.size > 1) {
                Text(
                    "В среднем за месяц (${withData.size} мес. с записями): доходы " +
                        "${formatMoney(withData.sumOf { it.income } / withData.size)}, расходы " +
                        formatMoney(withData.sumOf { it.expense } / withData.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageChartCard(state: UiState, months: List<YearMonth>, selected: Int, onSelect: (Int) -> Unit) {
    // Счётчики, которые сейчас на экране, плюс те, по которым есть старые показания.
    val withReadings = state.readings.map { it.meter }.toSet()
    val meters = MeterType.entries.filter { it in state.meters || it in withReadings }
    var chosen by rememberSaveable { mutableStateOf(MeterType.HOT_WATER) }
    val meter = chosen.takeIf { it in meters } ?: meters.first()
    val usage = monthlyUsage(state.readings, meter)
    val values = months.map { usage[it] }
    val color = MaterialTheme.colorScheme.tertiary

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Расход по счётчикам", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meters.forEach { m ->
                    FilterChip(selected = m == meter, onClick = { chosen = m }, label = { Text(m.short) })
                }
            }
            BarChart(
                labels = months.map(::shortMonth),
                series = listOf(values.map { it?.toDouble()?.coerceAtLeast(0.0) ?: 0.0 }),
                colors = listOf(color),
                selected = selected,
                onSelect = onSelect,
                axisLabel = { compactNumber(it) },
            )
            Text(formatMonth(months[selected]), fontWeight = FontWeight.SemiBold)
            val use: BigDecimal? = values[selected]
            val tariff = state.tariffs[meter] ?: 0L
            when {
                use == null -> Text(
                    "Нет двух показаний подряд, чтобы посчитать расход",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                use.signum() < 0 -> Text(
                    "Расход отрицательный — проверьте показания в истории",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val money = if (tariff > 0) " ≈ ${formatMoney(cost(use, tariff))} по текущему тарифу" else ""
                    Text("${formatReading(use.toDouble())} ${meter.unit}$money")
                }
            }
            Text(
                "Расход месяца — разница между его показаниями и предыдущими.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ValueRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Столбчатая диаграмма: по столбцу на каждую серию в каждом месяце.
 * series[серия][месяц]; отрицательные значения рисуются как ноль.
 */
@Composable
private fun BarChart(
    labels: List<String>,
    series: List<List<Double>>,
    colors: List<Color>,
    selected: Int,
    onSelect: (Int) -> Unit,
    axisLabel: (Double) -> String,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val count = labels.size

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(count) {
                detectTapGestures { offset ->
                    onSelect((offset.x / (size.width / count.toFloat())).toInt().coerceIn(0, count - 1))
                }
            },
    ) {
        val bottomLabels = 18.dp.toPx()
        val top = 14.dp.toPx()
        val base = size.height - bottomLabels
        val plotHeight = base - top
        val slot = size.width / count
        val max = series.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1.0

        drawRoundRect(
            color = highlight,
            topLeft = Offset(slot * selected, 0f),
            size = Size(slot, size.height),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )

        // Две линии сетки: максимум и половина, с подписями над линией слева.
        listOf(1.0, 0.5).forEach { f ->
            val y = base - (plotHeight * f).toFloat()
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(axisLabel(max * f), labelStyle)
            drawText(text, topLeft = Offset(2.dp.toPx(), y - text.size.height))
        }
        drawLine(gridColor, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())

        val groupWidth = slot * 0.7f
        val barWidth = groupWidth / series.size
        val gap = if (series.size > 1) 1.dp.toPx() else 0f
        for (i in 0 until count) {
            val x0 = slot * i + (slot - groupWidth) / 2
            series.forEachIndexed { s, values ->
                val h = (values[i].coerceAtLeast(0.0) / max * plotHeight).toFloat()
                if (h > 0f) {
                    drawRoundRect(
                        color = colors[s],
                        topLeft = Offset(x0 + s * barWidth + gap / 2, base - h),
                        size = Size(barWidth - gap, h),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            val text = measurer.measure(labels[i], if (i == selected) selectedStyle else labelStyle)
            drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, base + 3.dp.toPx()))
        }
    }
}
