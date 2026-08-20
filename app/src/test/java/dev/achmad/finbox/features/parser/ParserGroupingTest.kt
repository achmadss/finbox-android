package dev.achmad.finbox.features.parser

import dev.achmad.data.model.InstalledParser
import dev.achmad.finbox.core.parser.AvailableParser
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.features.parser.list.groupParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserGroupingTest {

    private fun installed(pkg: String, versionCode: Int = 1) = InstalledParser(
        pkg = pkg,
        provider = "Bank",
        name = pkg.substringAfterLast('.'),
        file = "/exts/$pkg-1.0.apk",
        versionCode = versionCode,
        versionName = "1.0",
        libVersion = "1.0",
        sha256 = "",
        sourceIds = listOf(1),
        enabled = true,
    )

    private fun available(pkg: String, versionCode: Int = 1) = AvailableParser(
        name = pkg.substringAfterLast('.'),
        provider = "Bank",
        pkg = pkg,
        versionCode = versionCode,
        versionName = "$versionCode.0",
        libVersion = 1.0,
        apkUrl = "https://example.com/$pkg.apk",
        sha256 = "",
        iconUrl = null,
    )

    @Test
    fun `a newer index entry moves an installed parser into updates`() {
        val state = groupParsers(
            installed = listOf(installed("dev.bri", versionCode = 1)),
            available = listOf(available("dev.bri", versionCode = 2)),
            errors = emptyMap(),
            downloads = emptyMap(),
        )

        assertEquals(listOf("dev.bri"), state.updates.map { it.pkg })
        assertTrue(state.installed.isEmpty())
        assertTrue(state.available.isEmpty())
    }

    @Test
    fun `an installed parser is never offered as available`() {
        val state = groupParsers(
            installed = listOf(installed("dev.bri")),
            available = listOf(available("dev.bri"), available("dev.jago")),
            errors = emptyMap(),
            downloads = emptyMap(),
        )

        assertEquals(listOf("dev.bri"), state.installed.map { it.pkg })
        assertEquals(listOf("dev.jago"), state.available.map { it.pkg })
    }

    @Test
    fun `an install in flight carries its step onto the row`() {
        val state = groupParsers(
            installed = emptyList(),
            available = listOf(available("dev.bri"), available("dev.jago")),
            errors = emptyMap(),
            downloads = mapOf("dev.jago" to InstallStep.Downloading),
        )

        assertEquals(
            listOf(InstallStep.Idle, InstallStep.Downloading),
            state.available.map { it.installStep },
        )
        assertEquals(listOf(false, true), state.available.map { it.isRunning })
    }
}
