pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google\\.android.*")
        includeGroupByRegex("com\\.google\\.gms.*")
        includeGroupByRegex("com\\.google\\.firebase.*")
        includeGroupByRegex("com\\.google\\.services.*")
        includeGroupByRegex("com\\.google\\.code.*")
        includeGroupByRegex("com\\.google\\.testing.*")
        includeGroupByRegex("androidx.*")
      }
    }
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
    mavenCentral()
  }
}

rootProject.name = "Dhanam AI"

include(":app")
