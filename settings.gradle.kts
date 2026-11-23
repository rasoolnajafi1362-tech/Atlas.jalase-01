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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    if (System.getenv("GITHUB_ACTIONS") == "true") {
      // Maven Central rate limits this large test dependency on hosted runners.
      exclusiveContent {
        forRepository {
          maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        }
        filter {
          includeModule("org.robolectric", "nativeruntime-dist-compat")
        }
      }
    }
    mavenCentral()
  }
}

rootProject.name = "اطلس جلسه ۰۱"

include(":app")
