package dev.achmad.finbox.features.extension

import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.features.extension.list.groupExtensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionGroupingTest {

    private fun installed(pkg: String, versionCode: Int = 1) = InstalledExtension(
        pkg = pkg,
        name = pkg.substringAfterLast('.'),
        versionCode = versionCode,
        versionName = "1.0",
        libVersion = "1.0",
        country = "ID",
        extensionIds = listOf("dev.achmad.finbox.extension.test"),
        enabled = true,
    )

    private fun available(pkg: String, versionCode: Int = 1) = AvailableExtension(
        name = pkg.substringAfterLast('.'),
        pkg = pkg,
        versionCode = versionCode,
        versionName = "$versionCode.0",
        libVersion = 1.0,
        apkUrl = "https://example.com/$pkg.apk",
        sha256 = "",
        country = "ID",
        iconUrl = null,
    )

    @Test
    fun `a newer index entry moves an installed extension into updates`() {
        val state = groupExtensions(
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
    fun `an installed extension is never offered as available`() {
        val state = groupExtensions(
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
        val state = groupExtensions(
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
