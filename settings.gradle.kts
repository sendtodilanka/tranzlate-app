pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "tranzlate"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Ring 1 — reusable AAR libraries (zero project deps)
include(":lib:subscription")
include(":lib:ads")
include(":lib:consent")

// Ring 2 — pure-JVM contracts
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:config")
include(":core:testing")

// Ring 3 — Android infra + brains
include(":core:designsystem")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:translate")
include(":core:access")
include(":core:usage")
include(":core:ads")
include(":core:translate-fake")

// Ring 4 — features + shell
include(":feature:text")
include(":feature:languagepicker")
include(":feature:camera")
include(":feature:history")
include(":feature:settings")
include(":feature:paywall")
include(":app")
