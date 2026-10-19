package ru.homefinance.data

import java.time.LocalDate

enum class TxType { INCOME, EXPENSE }

/** Статья дохода или расхода. */
data class Category(val id: Long, val name: String, val type: TxType)

data class Transaction(
    val id: Long,
    val type: TxType,
    /** Сумма в копейках — без ошибок округления, которые дал бы Double. */
    val amount: Long,
    val categoryId: Long?,
    /** null, если статья не выбрана или удалена. */
    val categoryName: String?,
    val note: String,
    val date: LocalDate,
)

// Имена констант записаны в базе и в резервных копиях — не переименовывать.
enum class MeterType(val title: String, val short: String, val unit: String) {
    HOT_WATER("Горячая вода", "ГВС", "м³"),
    COLD_WATER("Холодная вода", "ХВС", "м³"),
    ELECTRICITY("Электроэнергия", "Свет", "кВт·ч"),
    ELECTRICITY_DAY("Электроэнергия, день (Т1)", "Свет день", "кВт·ч"),
    ELECTRICITY_NIGHT("Электроэнергия, ночь (Т2)", "Свет ночь", "кВт·ч"),
    ;

    companion object {
        /** Счётчики на экране: однотарифный свет или пара «день/ночь». Старые показания остаются в истории. */
        fun visible(twoTariffElectricity: Boolean): List<MeterType> =
            if (twoTariffElectricity) listOf(HOT_WATER, COLD_WATER, ELECTRICITY_DAY, ELECTRICITY_NIGHT)
            else listOf(HOT_WATER, COLD_WATER, ELECTRICITY)
    }
}

data class MeterReading(val id: Long, val meter: MeterType, val value: Double, val date: LocalDate)
