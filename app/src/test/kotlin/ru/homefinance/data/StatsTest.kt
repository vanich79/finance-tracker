package ru.homefinance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StatsTest {

    private fun reading(id: Long, meter: MeterType, value: Double, date: String) =
        MeterReading(id, meter, value, LocalDate.parse(date))

    @Test
    fun `расход по месяцам считается от предыдущих показаний`() {
        val readings = listOf(
            reading(1, MeterType.HOT_WATER, 100.0, "2026-08-20"),
            reading(2, MeterType.HOT_WATER, 103.2, "2026-09-20"),
            reading(3, MeterType.HOT_WATER, 106.0, "2026-10-05"),
            reading(4, MeterType.HOT_WATER, 107.1, "2026-10-25"), // два показания в октябре складываются
            reading(5, MeterType.COLD_WATER, 50.0, "2026-09-20"), // чужой счётчик не мешает
        ).shuffled()

        val usage = monthlyUsage(readings, MeterType.HOT_WATER)

        assertEquals(
            mapOf(
                YearMonth.of(2026, 9) to BigDecimal("3.200"),
                YearMonth.of(2026, 10) to BigDecimal("3.900"),
            ),
            usage,
        )
        assertEquals(emptyMap<YearMonth, BigDecimal>(), monthlyUsage(readings, MeterType.ELECTRICITY_NIGHT))
    }

    @Test
    fun `стоимость округляется до копейки`() {
        // 3,2 м³ × 268,50 ₽ = 859,20 ₽
        assertEquals(85_920L, cost(BigDecimal("3.200"), 26_850))
        // 123,456 кВт·ч × 7,04 ₽ = 869,13 ₽ (869,13024)
        assertEquals(86_913L, cost(BigDecimal("123.456"), 704))
    }

    @Test
    fun `суммы по месяцам из базы`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("finance.db")
        val repo = Repository(context)
        fun add(type: TxType, amount: Long, date: String) =
            repo.saveTransaction(Transaction(0, type, amount, null, null, "", LocalDate.parse(date)))
        add(TxType.INCOME, 10_000_00, "2026-09-05")
        add(TxType.EXPENSE, 1_500_00, "2026-09-05")
        add(TxType.EXPENSE, 500_00, "2026-09-30")
        add(TxType.EXPENSE, 200_00, "2026-10-01")
        add(TxType.EXPENSE, 999_00, "2026-07-31") // вне периода

        val totals = repo.monthlyTotals(YearMonth.of(2026, 8), YearMonth.of(2026, 10))

        assertEquals(
            mapOf(
                YearMonth.of(2026, 9) to MonthTotals(income = 10_000_00, expense = 2_000_00),
                YearMonth.of(2026, 10) to MonthTotals(expense = 200_00),
            ),
            totals,
        )
    }
}
