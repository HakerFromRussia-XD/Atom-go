pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "atom-go"

include(":backend:app")

val backendOnly = providers.gradleProperty("backendOnly").orNull == "true"
if (!backendOnly) {
    include(":mobile:shared")
    include(":mobile:androidApp")
}
