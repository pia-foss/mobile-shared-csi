import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("maven-publish")
}

publishing {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/pia-foss/mobile-shared-csi/")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

android {
    namespace = "com.kape.csi"

    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    group = "com.kape.android"
    version = "1.3.4"

    jvmToolchain(17)

    // Enable the default target hierarchy.
    // It's a template for all possible targets and their shared source sets hardcoded in the
    // Kotlin Gradle plugin.
    applyDefaultHierarchyTemplate()

    // Android
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS
    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        tvosX64(),
        tvosSimulatorArm64(),
        tvosArm64()
    ).forEach {
        val main by it.compilations.getting {
            val fixUndefinedSymbols by cinterops.creating {
                defFile(project.file("src/iosMain/kotlin/com/privateinternetaccess/csi/internals/cinterop/fix_undefined_symbols.def"))
            }
        }
        it.binaries.framework {
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.bouncycastle:bcprov-jdk18on:1.80")
                implementation("io.ktor:ktor-client-okhttp:3.1.1")
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.1.0")
            }
        }
        val tvosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.1.0")
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}