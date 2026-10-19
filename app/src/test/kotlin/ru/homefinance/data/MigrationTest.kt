package ru.homefinance.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** База версии 1 — как у пользователя на телефоне с приложением 1.0/1.1. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {

    @Test
    fun `база версии 1 открывается без потери данных и получает настройки`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("finance.db")
        val file = context.getDatabasePath("finance.db").apply { parentFile?.mkdirs() }
        val day = LocalDate.of(2026, 10, 20).toEpochDay()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Схема ровно как в onCreate версии 1.
            db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, " +
                    "amount INTEGER NOT NULL, category_id INTEGER, note TEXT NOT NULL DEFAULT '', date INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX transactions_date ON transactions(date)")
            db.execSQL(
                "CREATE TABLE meter_readings (id INTEGER PRIMARY KEY AUTOINCREMENT, meter TEXT NOT NULL, " +
                    "value REAL NOT NULL, date INTEGER NOT NULL)",
            )
            db.execSQL("CREATE TABLE tariffs (meter TEXT PRIMARY KEY, price INTEGER NOT NULL)")
            db.execSQL("INSERT INTO categories (id, name, type) VALUES (4, 'ЖКХ', 'EXPENSE')")
            db.execSQL("INSERT INTO transactions (type, amount, category_id, note, date) VALUES ('EXPENSE', 512050, 4, 'квартплата', $day)")
            db.execSQL("INSERT INTO meter_readings (meter, value, date) VALUES ('ELECTRICITY', 4521.5, $day)")
            db.execSQL("INSERT INTO tariffs (meter, price) VALUES ('ELECTRICITY', 704)")
            db.version = 1
        }

        val repo = Repository(context)
        val tx = repo.transactions(LocalDate.MIN, LocalDate.MAX).single()
        assertEquals(512050L, tx.amount)
        assertEquals("ЖКХ", tx.categoryName)
        assertEquals("квартплата", tx.note)
        assertEquals(MeterReading(1, MeterType.ELECTRICITY, 4521.5, LocalDate.ofEpochDay(day)), repo.readings().single())
        assertEquals(mapOf(MeterType.ELECTRICITY to 704L), repo.tariffs())

        // Новая таблица работает, показания «день/ночь» сохраняются рядом со старыми.
        assertEquals(emptyMap<String, String>(), repo.settings())
        repo.setSetting(SETTING_TWO_TARIFF, "1")
        repo.saveReading(MeterReading(0, MeterType.ELECTRICITY_DAY, 3000.0, LocalDate.ofEpochDay(day)))
        assertEquals(mapOf(SETTING_TWO_TARIFF to "1"), Repository(context).settings())
        assertEquals(2, repo.readings().size)
    }
}
