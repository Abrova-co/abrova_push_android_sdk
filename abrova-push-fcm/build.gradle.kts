plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

// SDK Version - MAJOR.MINOR.PATCH
// Requires a abrova-push-android release that includes AbrovaPushTransports.
val sdkVersion = "0.1.0"

android {
    namespace = "ir.abrova.push.fcm"
    compileSdk = 34

    defaultConfig {
        // Same as abrova-push; Firebase BoM 33.x still supports API 21
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=ir.abrova.push.sdk.AbrovaPushInternalApi"
    }
}

// Publish to Maven Central
mavenPublishing {
    publishToMavenCentral(
        com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL,
        automaticRelease = true
    )
    signAllPublications()

    coordinates("ir.abrova", "abrova-push-fcm", sdkVersion)

    pom {
        name.set("Abrova Push FCM Add-on")
        description.set("Optional Firebase Cloud Messaging transport for the Abrova Push Android SDK.")
        inceptionYear.set("2026")
        url.set("https://abrova.ir/cloud/abrova-push")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("abrova")
                name.set("Abrova")
                email.set("founder@abrova.ir")
                url.set("https://abrova.ir")
            }
        }

        scm {
            url.set("https://github.com/Abrova-co/abrova_push_android_sdk")
            connection.set("scm:git:git://github.com/Abrova-co/abrova_push_android_sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/Abrova-co/abrova_push_android_sdk.git")
        }
    }
}

dependencies {
    // Abrova Push core SDK
    api(project(":abrova-push"))

    // Firebase Cloud Messaging (BoM 33.x keeps minSdk 21)
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Auto-initialisation without app code
    implementation("androidx.startup:startup-runtime:1.1.1")

    // ==================== Testing ====================
    testImplementation("junit:junit:4.13.2")
}
