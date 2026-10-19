package ru.homefinance.data

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Новая версия, опубликованная в GitHub Releases. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/**
 * Обновления берутся из GitHub Releases. Приложение обращается только к GitHub и ничего,
 * кроме запроса «какая версия последняя», не отправляет.
 */
object Updates {
    const val REPO = "vanich79/finance-tracker"
    const val REPO_URL = "https://github.com/$REPO"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** Последний релиз или null, если релизов ещё нет. Сетевые ошибки — IOException. */
    fun fetchLatest(): UpdateInfo? {
        val connection = open(LATEST_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub ответил кодом $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Разбор ответа GitHub; релиз без APK-файла пропускается. */
    fun parseRelease(json: String): UpdateInfo? {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val assets = root.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        return UpdateInfo(
            version = root.getString("tag_name").removePrefix("v").removePrefix("V"),
            notes = root.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
            pageUrl = root.optString("html_url", REPO_URL),
        )
    }

    /** «1.10» новее «1.9»; недостающие части считаются нулями: «1.3» = «1.3.0». */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split('.', '-', ' ').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Скачивает файл целиком во временный и только потом переименовывает — недокачанный APK не останется. */
    fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Сервер ответил кодом ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("не удалось сохранить файл")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // файлы релизов отдаются через редирект на objects.githubusercontent.com
            setRequestProperty("User-Agent", "MoiFinansy-Android")
        }
}
