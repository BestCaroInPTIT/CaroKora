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

rootProject.name = "CaroGameClient"

gradle.beforeProject {
    val dirName = if (path == ":") "root" else path.replace(":", "_")
    val tmpBase = System.getProperty("java.io.tmpdir").replace("\\", "/")
    layout.buildDirectory.set(file("$tmpBase/CaroGameClient-build/$dirName"))
}

include(":app")
