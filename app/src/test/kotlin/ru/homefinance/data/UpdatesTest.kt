package ru.homefinance.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdatesTest {

    @Test
    fun `сравнение версий по числам, а не по строкам`() {
        assertTrue(Updates.isNewer("1.4", "1.3"))
        assertTrue(Updates.isNewer("1.10", "1.9"))
        assertTrue(Updates.isNewer("2.0", "1.99"))
        assertTrue(Updates.isNewer("1.3.1", "1.3"))
        assertFalse(Updates.isNewer("1.3", "1.3"))
        assertFalse(Updates.isNewer("1.3.0", "1.3"))
        assertFalse(Updates.isNewer("1.2", "1.3"))
    }

    @Test
    fun `из ответа GitHub берётся версия, описание и APK`() {
        val info = Updates.parseRelease(
            """{"tag_name":"v1.4","html_url":"https://github.com/vanich79/finance-tracker/releases/tag/v1.4",
               "draft":false,"prerelease":false,"body":"Что нового:\n- графики",
               "assets":[
                 {"name":"checksums.txt","browser_download_url":"https://example/checksums.txt","size":10},
                 {"name":"MoiFinansy-1.4.apk","browser_download_url":"https://example/MoiFinansy-1.4.apk","size":1400000}
               ]}""",
        )!!
        assertEquals("1.4", info.version)
        assertEquals("https://example/MoiFinansy-1.4.apk", info.apkUrl)
        assertEquals(1_400_000L, info.apkSize)
        assertEquals("Что нового:\n- графики", info.notes)
    }

    @Test
    fun `релиз без APK или черновик не предлагается`() {
        assertNull(Updates.parseRelease("""{"tag_name":"v9.0","assets":[]}"""))
        assertNull(
            Updates.parseRelease(
                """{"tag_name":"v9.0","prerelease":true,
                   "assets":[{"name":"a.apk","browser_download_url":"https://example/a.apk"}]}""",
            ),
        )
    }
}
