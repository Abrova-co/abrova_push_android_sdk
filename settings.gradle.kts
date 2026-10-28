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
        // Abrova packages (ir.abrova:pn-protocol) are published on Abrova Center.
        maven { url = uri("https://center.abrova.ir/api/packages/abrova/maven") }
        // Local libs folder for fallback
        flatDir {
            dirs("libs")
        }
    }
}

rootProject.name = "abrova-push-android-sdk"

// PN Protocol: releases use the published ir.abrova:pn-protocol from Abrova Center.
// For local protocol work, build with -PuseLocalProtocol=true to use ../protocol/pn-protocol-android.
if (providers.gradleProperty("useLocalProtocol").orNull == "true") {
    include(":pn-protocol")
    project(":pn-protocol").projectDir = file("../protocol/pn-protocol-android/pn-protocol")
}

// SDK modules
include(":abrova-push")
include(":abrova-push-fcm")
include(":abrova-push-voip")
project(":abrova-push-voip").projectDir = file("../abrova-push-voip-android-sdk")
include(":example")
