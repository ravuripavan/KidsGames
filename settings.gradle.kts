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

rootProject.name = "KidsGames"

include(":app")
include(":core:gameapi")
include(":core:designkit")
include(":core:vocab")
include(":core:shell")

include(":games:popballoons")
include(":games:whatisit")
include(":games:talktime")
include(":games:matchshapes")
include(":games:countanimals")
include(":games:colorsort")
include(":games:tracelines")
include(":games:patterns")
include(":games:puzzleboard")
include(":games:memorypairs")
include(":games:musicpad")
include(":games:carrace")
include(":games:cardesign")
include(":games:carwash")
