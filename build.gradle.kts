plugins {
    alias(libs.plugins.android.library) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
