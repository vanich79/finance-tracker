package ru.homefinance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupTest {

    private lateinit var context: Context
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("finance.db")
        repo = Repository(context)
    }

    @Test
    fun `копия переносит все данные и заменяет текущие`() {
        val salaryId = repo.addCategory("Премия", TxType.INCOME)
        val food = repo.categories().first { it.name == "Продукты" }
        val day = LocalDate.of(2026, 10, 5)
        repo.saveTransaction(Transaction(0, TxType.INCOME, 15_000_000, salaryId, null, "Октябрь", day))
        repo.saveTransaction(Transaction(0, TxType.EXPENSE, 123_456, food.id, null, "Пятёрочка «у дома»", day))
        repo.saveTransaction(Transaction(0, TxType.EXPENSE, 99, null, null, "", day.plusDays(1)))
        repo.saveReading(MeterReading(0, MeterType.HOT_WATER, 123.456, day))
        repo.saveReading(MeterReading(0, MeterType.ELECTRICITY, 4521.0, day))
        repo.saveReading(MeterReading(0, MeterType.ELECTRICITY_DAY, 3010.5, day))
        repo.saveReading(MeterReading(0, MeterType.ELECTRICITY_NIGHT, 1511.25, day))
        repo.setTariff(MeterType.HOT_WATER, 26_850)
        repo.setTariff(MeterType.ELECTRICITY_NIGHT, 352)
        repo.setSetting(SETTING_TWO_TARIFF, "1")

        val json = repo.backup().toJson()
        val before = snapshot(repo)

        // Портим данные: удаляем статью, добавляем лишнее, меняем тариф.
        repo.deleteCategory(food.id)
        repo.saveTransaction(Transaction(0, TxType.EXPENSE, 1, null, null, "лишняя", day))
        repo.setTariff(MeterType.HOT_WATER, 1)
        repo.setSetting(SETTING_TWO_TARIFF, "0")

        repo.restore(Backup.parse(json))
        assertEquals(before, snapshot(repo))

        // Восстановленная база продолжает работать: новые записи получают свежие id.
        val newId = repo.addCategory("Новая", TxType.EXPENSE)
        assertEquals(true, newId > repo.categories().filter { it.name != "Новая" }.maxOf { it.id })

        // Копия читается и через новый экземпляр — то есть данные записаны на диск.
        assertEquals(before.second, snapshot(Repository(context)).second)
    }

    @Test
    fun `чужой или битый файл отклоняется, данные не трогаются`() {
        val count = repo.categories().size
        assertThrows(BackupFormatException::class.java) { Backup.parse("привет") }
        assertThrows(BackupFormatException::class.java) { Backup.parse("""{"a":1}""") }
        assertThrows(BackupFormatException::class.java) {
            Backup.parse("""{"format":"homefinance-backup","version":1,"transactions":[{"type":"EXPENSE"}]}""")
        }
        assertEquals(count, repo.categories().size)
    }

    @Test
    fun `ссылка на отсутствующую статью превращается в без статьи`() {
        val backup = Backup.parse(
            """{"format":"homefinance-backup","version":1,"categories":[],
               "transactions":[{"type":"EXPENSE","amount":500,"categoryId":42,"note":"","date":"2026-10-01"}]}""",
        )
        repo.restore(backup)
        val tx = repo.transactions(LocalDate.MIN, LocalDate.MAX).single()
        assertNull(tx.categoryId)
        assertEquals(500L, tx.amount)
    }

    @Test
    fun `копия версии 1 из приложения 1_1 восстанавливается`() {
        val backup = Backup.parse(
            """{"format":"homefinance-backup","version":1,"exportedAt":"2026-10-19",
               "categories":[{"id":4,"name":"ЖКХ","type":"EXPENSE"}],
               "transactions":[{"type":"EXPENSE","amount":512050,"categoryId":4,"note":"","date":"2026-10-19"}],
               "readings":[{"meter":"ELECTRICITY","value":4521.5,"date":"2026-10-19"}],
               "tariffs":[{"meter":"ELECTRICITY","price":704}]}""",
        )
        repo.restore(backup)
        assertEquals("ЖКХ", repo.transactions(LocalDate.MIN, LocalDate.MAX).single().categoryName)
        assertEquals(MeterType.ELECTRICITY, repo.readings().single().meter)
        assertEquals(emptyMap<String, String>(), repo.settings())
    }

    @Test
    fun `копия от более новой версии приложения отклоняется с понятной причиной`() {
        val e = assertThrows(BackupFormatException::class.java) {
            Backup.parse("""{"format":"homefinance-backup","version":99}""")
        }
        assertEquals(true, e.message!!.contains("обновите"))
    }

    /** Данные без внутренних id операций и показаний — они после восстановления новые. */
    private fun snapshot(r: Repository) = Triple(
        r.categories(),
        r.transactions(LocalDate.MIN, LocalDate.MAX).map { it.copy(id = 0) } to
            r.readings().map { it.copy(id = 0) },
        r.tariffs() to r.settings(),
    )
}
