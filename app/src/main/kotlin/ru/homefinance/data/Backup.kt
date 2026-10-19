package ru.homefinance.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Полная копия данных приложения. В файле — JSON: его можно открыть и прочитать глазами,
 * даты записаны как 2026-10-19, суммы и тарифы — в копейках.
 */
data class Backup(
    val exportedAt: LocalDate?,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val readings: List<MeterReading>,
    val tariffs: Map<MeterType, Long>,
    val settings: Map<String, String> = emptyMap(),
) {
    fun toJson(): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", (exportedAt ?: LocalDate.now()).toString())
        put("categories", JSONArray(categories.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("type", c.type.name)
        }))
        put("transactions", JSONArray(transactions.map { t ->
            JSONObject()
                .put("type", t.type.name)
                .put("amount", t.amount)
                .put("categoryId", t.categoryId ?: JSONObject.NULL)
                .put("note", t.note)
                .put("date", t.date.toString())
        }))
        put("readings", JSONArray(readings.map { r ->
            JSONObject().put("meter", r.meter.name).put("value", r.value).put("date", r.date.toString())
        }))
        put("tariffs", JSONArray(tariffs.map { (meter, price) ->
            JSONObject().put("meter", meter.name).put("price", price)
        }))
        put("settings", JSONObject(settings))
    }.toString(2)

    companion object {
        const val FORMAT = "homefinance-backup"
        // 2: счётчики света «день/ночь» и настройки. Копии версии 1 читаются как есть;
        // старое приложение копию версии 2 не примет и попросит обновиться.
        const val VERSION = 2

        /** Разбирает файл целиком; при любой ошибке бросает [BackupFormatException] и ничего не меняет. */
        fun parse(text: String): Backup {
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw BackupFormatException("Файл не похож на резервную копию: это не JSON")
            }
            if (root.optString("format") != FORMAT) {
                throw BackupFormatException("Файл не похож на резервную копию «Моих финансов»")
            }
            if (root.optInt("version") > VERSION) {
                throw BackupFormatException("Копия сделана более новой версией приложения — обновите приложение")
            }
            try {
                val categories = root.objects("categories").map { o ->
                    Category(o.getLong("id"), o.getString("name"), TxType.valueOf(o.getString("type")))
                }
                val categoryIds = categories.map { it.id }.toSet()
                val transactions = root.objects("transactions").map { o ->
                    val amount = o.getLong("amount")
                    require(amount > 0) { "сумма должна быть больше нуля" }
                    // Ссылка на статью, которой нет в файле, превращается в «без статьи».
                    val categoryId = if (o.isNull("categoryId")) null else o.getLong("categoryId")
                    Transaction(
                        id = 0,
                        type = TxType.valueOf(o.getString("type")),
                        amount = amount,
                        categoryId = categoryId?.takeIf { it in categoryIds },
                        categoryName = null,
                        note = o.optString("note"),
                        date = LocalDate.parse(o.getString("date")),
                    )
                }
                val readings = root.objects("readings").map { o ->
                    MeterReading(0, MeterType.valueOf(o.getString("meter")), o.getDouble("value"), LocalDate.parse(o.getString("date")))
                }
                val tariffs = root.objects("tariffs").associate { o ->
                    MeterType.valueOf(o.getString("meter")) to o.getLong("price")
                }
                val settings = root.optJSONObject("settings")?.let { o ->
                    o.keys().asSequence().associateWith { o.getString(it) }
                }.orEmpty()
                return Backup(
                    exportedAt = root.optString("exportedAt").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                    categories = categories,
                    transactions = transactions,
                    readings = readings,
                    tariffs = tariffs,
                    settings = settings,
                )
            } catch (e: Exception) {
                throw BackupFormatException("Файл копии повреждён: ${e.message}")
            }
        }

        private fun JSONObject.objects(name: String): List<JSONObject> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
    }
}

class BackupFormatException(message: String) : Exception(message)
