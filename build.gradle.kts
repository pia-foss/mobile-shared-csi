plugins {
    id("com.android.library").version("8.9.0").apply(false)
    kotlin("multiplatform").version("2.1.0").apply(false)
    kotlin("plugin.serialization").version("2.1.0").apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
