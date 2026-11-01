pluginManagement {
    repositories {
        google()
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

rootProject.name = "adb-remote"
includeBuild("../shared/remote-tunnel/kotlin") {
    dependencySubstitution {
        substitute(module("app.duinch:remote-tunnel-kotlin")).using(project(":"))
    }
}

