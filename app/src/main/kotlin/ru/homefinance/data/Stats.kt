package ru.homefinance.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

data class MonthTotals(val income: Long = 0, val expense: Long = 0)

/** Расход между двумя показаниями, округлённый до тысячных, чтобы не тащить хвосты Double. */
fun usage(last: MeterReading, prev: MeterReading): BigDecimal =
    BigDecimal.valueOf(last.value).subtract(BigDecimal.valueOf(prev.value)).setScale(3, RoundingMode.HALF_UP)

/** Стоимость расхода в копейках при тарифе в копейках за единицу. */
fun cost(usage: BigDecimal, tariff: Long): Long =
    usage.multiply(BigDecimal.valueOf(tariff)).setScale(0, RoundingMode.HALF_UP).toLong()

/**
 * Расход ресурса по месяцам. Разница между соседними показаниями относится к месяцу более
 * позднего: показания, снятые 20 октября, дают расход «за октябрь». Если в месяце несколько
 * показаний, разницы складываются.
 */
fun monthlyUsage(readings: List<MeterReading>, meter: MeterType): Map<YearMonth, BigDecimal> {
    val sorted = readings.filter { it.meter == meter }.sortedWith(compareBy({ it.date }, { it.id }))
    val result = mutableMapOf<YearMonth, BigDecimal>()
    sorted.zipWithNext { prev, cur ->
        val month = YearMonth.from(cur.date)
        result[month] = (result[month] ?: BigDecimal.ZERO).add(usage(cur, prev))
    }
    return result
}
