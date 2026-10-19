package ru.homefinance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Ключ настройки: "1" — счётчик света двухтарифный (день/ночь). */
const val SETTING_TWO_TARIFF = "electricity_two_tariff"

// Версия 2: таблица настроек (двухтарифный счётчик света). Существующие таблицы не менялись.
private class FinanceDb(context: Context) : SQLiteOpenHelper(context, "finance.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL)",
        )
        // Даты хранятся как номер дня от 1970-01-01 (LocalDate.toEpochDay), суммы — в копейках.
        db.execSQL(
            "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, category_id INTEGER, note TEXT NOT NULL DEFAULT '', date INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX transactions_date ON transactions(date)")
        db.execSQL(
            "CREATE TABLE meter_readings (id INTEGER PRIMARY KEY AUTOINCREMENT, meter TEXT NOT NULL, " +
                "value REAL NOT NULL, date INTEGER NOT NULL)",
        )
        // Цена единицы (м³ или кВт·ч) в копейках.
        db.execSQL("CREATE TABLE tariffs (meter TEXT PRIMARY KEY, price INTEGER NOT NULL)")
        db.execSQL(CREATE_SETTINGS)

        DEFAULT_EXPENSES.forEach { db.insert("categories", null, categoryValues(it, TxType.EXPENSE)) }
        DEFAULT_INCOMES.forEach { db.insert("categories", null, categoryValues(it, TxType.INCOME)) }
    }

    // Только добавление таблиц: у пользователя на телефоне уже живые данные.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL(CREATE_SETTINGS)
    }

    companion object {
        const val CREATE_SETTINGS = "CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)"

        val DEFAULT_EXPENSES = listOf(
            "Продукты", "Кафе и рестораны", "Транспорт", "ЖКХ", "Связь и интернет", "Здоровье",
            "Одежда", "Дом", "Развлечения", "Подарки", "Прочее",
        )
        val DEFAULT_INCOMES = listOf("Зарплата", "Подработка", "Проценты по вкладам", "Подарки", "Прочее")
    }
}

private fun categoryValues(name: String, type: TxType) = ContentValues().apply {
    put("name", name)
    put("type", type.name)
}

/** Все методы блокирующие — вызывать вне главного потока. */
class Repository(context: Context) {

    private val db: SQLiteDatabase by lazy { FinanceDb(context.applicationContext).writableDatabase }
    private val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU"))

    // --- Статьи ---

    fun categories(): List<Category> =
        db.rawQuery("SELECT id, name, type FROM categories", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Category(c.getLong(0), c.getString(1), TxType.valueOf(c.getString(2))))
            }
        }.sortedWith(compareBy(collator) { it.name })

    fun addCategory(name: String, type: TxType): Long = db.insert("categories", null, categoryValues(name, type))

    fun renameCategory(id: Long, name: String) {
        db.update("categories", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(id.toString()))
    }

    /** Операции удалённой статьи остаются и показываются «без статьи». */
    fun deleteCategory(id: Long) {
        db.beginTransaction()
        try {
            db.update(
                "transactions",
                ContentValues().apply { putNull("category_id") },
                "category_id = ?",
                arrayOf(id.toString()),
            )
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- Операции ---

    fun transactions(from: LocalDate, to: LocalDate): List<Transaction> =
        db.rawQuery(
            "SELECT t.id, t.type, t.amount, t.category_id, c.name, t.note, t.date " +
                "FROM transactions t LEFT JOIN categories c ON c.id = t.category_id " +
                "WHERE t.date BETWEEN ? AND ? ORDER BY t.date DESC, t.id DESC",
            arrayOf(from.toEpochDay().toString(), to.toEpochDay().toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Transaction(
                            id = c.getLong(0),
                            type = TxType.valueOf(c.getString(1)),
                            amount = c.getLong(2),
                            categoryId = if (c.isNull(3)) null else c.getLong(3),
                            categoryName = if (c.isNull(4)) null else c.getString(4),
                            note = c.getString(5),
                            date = LocalDate.ofEpochDay(c.getLong(6)),
                        ),
                    )
                }
            }
        }

    fun saveTransaction(t: Transaction) {
        val values = ContentValues().apply {
            put("type", t.type.name)
            put("amount", t.amount)
            if (t.categoryId == null) putNull("category_id") else put("category_id", t.categoryId)
            put("note", t.note)
            put("date", t.date.toEpochDay())
        }
        if (t.id == 0L) {
            db.insert("transactions", null, values)
        } else {
            db.update("transactions", values, "id = ?", arrayOf(t.id.toString()))
        }
    }

    fun deleteTransaction(id: Long) {
        db.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    // --- Счётчики ---

    /** Все показания, новые сверху. */
    fun readings(): List<MeterReading> =
        db.rawQuery("SELECT id, meter, value, date FROM meter_readings ORDER BY date DESC, id DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        MeterReading(
                            id = c.getLong(0),
                            meter = MeterType.valueOf(c.getString(1)),
                            value = c.getDouble(2),
                            date = LocalDate.ofEpochDay(c.getLong(3)),
                        ),
                    )
                }
            }
        }

    fun saveReading(r: MeterReading) {
        val values = ContentValues().apply {
            put("meter", r.meter.name)
            put("value", r.value)
            put("date", r.date.toEpochDay())
        }
        if (r.id == 0L) {
            db.insert("meter_readings", null, values)
        } else {
            db.update("meter_readings", values, "id = ?", arrayOf(r.id.toString()))
        }
    }

    fun deleteReading(id: Long) {
        db.delete("meter_readings", "id = ?", arrayOf(id.toString()))
    }

    fun tariffs(): Map<MeterType, Long> =
        db.rawQuery("SELECT meter, price FROM tariffs", null).use { c ->
            buildMap {
                while (c.moveToNext()) put(MeterType.valueOf(c.getString(0)), c.getLong(1))
            }
        }

    fun setTariff(meter: MeterType, price: Long) {
        val values = ContentValues().apply {
            put("meter", meter.name)
            put("price", price)
        }
        db.insertWithOnConflict("tariffs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Статистика ---

    /** Суммы доходов и расходов по месяцам; месяцы без записей в ответ не попадают. */
    fun monthlyTotals(from: YearMonth, to: YearMonth): Map<YearMonth, MonthTotals> {
        val result = mutableMapOf<YearMonth, MonthTotals>()
        db.rawQuery(
            "SELECT type, date, SUM(amount) FROM transactions WHERE date BETWEEN ? AND ? GROUP BY type, date",
            arrayOf(from.atDay(1).toEpochDay().toString(), to.atEndOfMonth().toEpochDay().toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val month = YearMonth.from(LocalDate.ofEpochDay(c.getLong(1)))
                val sum = c.getLong(2)
                val totals = result[month] ?: MonthTotals()
                result[month] = if (TxType.valueOf(c.getString(0)) == TxType.INCOME) {
                    totals.copy(income = totals.income + sum)
                } else {
                    totals.copy(expense = totals.expense + sum)
                }
            }
        }
        return result
    }

    // --- Настройки ---

    fun settings(): Map<String, String> =
        db.rawQuery("SELECT key, value FROM settings", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    fun setSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- Резервная копия ---

    fun backup(): Backup = Backup(
        exportedAt = LocalDate.now(),
        categories = categories(),
        transactions = transactions(LocalDate.MIN, LocalDate.MAX).reversed(),
        readings = readings().reversed(),
        tariffs = tariffs(),
        settings = settings(),
    )

    /** Заменяет все данные содержимым копии. Либо всё целиком, либо ничего. */
    fun restore(backup: Backup) {
        db.beginTransaction()
        try {
            listOf("transactions", "categories", "meter_readings", "tariffs", "settings")
                .forEach { db.delete(it, null, null) }
            // Статьи вставляются со своими id, чтобы операции из копии ссылались на них же.
            backup.categories.forEach { c ->
                db.insert("categories", null, categoryValues(c.name, c.type).apply { put("id", c.id) })
            }
            backup.transactions.forEach { saveTransaction(it.copy(id = 0)) }
            backup.readings.forEach { saveReading(it.copy(id = 0)) }
            backup.tariffs.forEach { (meter, price) -> setTariff(meter, price) }
            backup.settings.forEach { (key, value) -> setSetting(key, value) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
