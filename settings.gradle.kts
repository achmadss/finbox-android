pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Finbox"
include(":app")
include(":data")
include(":source:core")

// Every directory under source/lib/<country>/<bank> is a source module.
// Included by walking the tree rather than listed, for the same reason the
// registry is generated rather than written: a source you forgot to add here
// would be a directory that quietly is not built.
file("source/lib").eachDir { country ->
    country.eachDir { bank ->
        include(":source:lib:${country.name}:${bank.name}")
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()
        ?.filter { it.isDirectory && it.name != "build" && !it.name.startsWith(".") }
        ?.sortedBy { it.name }
        ?.forEach(block)
}
