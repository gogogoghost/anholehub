pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.jetbrains\\.kotlin.*")
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

rootProject.name = "anlobehub"
include(":app")
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":feature:auth")
include(":feature:chat")
include(":feature:agents")
include(":feature:models")
include(":feature:settings")
