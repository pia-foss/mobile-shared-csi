plugins {
    id("com.android.library").version("7.3.1").apply(false)
    kotlin("multiplatform").version("1.9.20").apply(false)
    kotlin("plugin.serialization").version("1.9.20").apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
