package dev.achmad.finbox.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies a source's launcher icon into a drawable only that source answers to.
 *
 * A source ships its icon the ordinary way, at
 * `src/main/res/mipmap-<density>/ic_launcher.png`, which is also the layout an
 * extension in Tachiyomi's extensions-source uses, so a set copies in
 * unchanged. It cannot be *read* under that name: every source's resources
 * merge into one app, `ic_launcher` is what the app's own launcher icon already
 * answers to, and an app resource beats a library one — so all four sources
 * would render finbox's icon.
 *
 * This writes `drawable-<density>/<id>_icon.png`, which the generated
 * `SourceEntry` points at. The `mipmap` original stays where a contributor put
 * it and is simply never read.
 */
abstract class GenerateSourceIcon : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val res: DirectoryProperty

    @get:Input
    abstract val sourceId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val from = res.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        val densities = from.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("mipmap-") }
            .mapNotNull { bucket ->
                bucket.resolve("ic_launcher.png").takeIf { it.isFile }?.let { bucket.name to it }
            }

        // A source with no icon would build and then draw a blank row, which
        // reads as a UI bug a long way from the missing file.
        if (densities.isEmpty()) {
            throw GradleException(
                "${sourceId.get()} has no icon. Add one at " +
                    "src/main/res/mipmap-<density>/ic_launcher.png.",
            )
        }

        densities.forEach { (bucket, icon) ->
            val target = out.resolve("drawable-${bucket.removePrefix("mipmap-")}")
            target.mkdirs()
            icon.copyTo(target.resolve("${sourceId.get()}_icon.png"), overwrite = true)
        }
    }
}
