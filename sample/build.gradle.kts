plugins {
    alias(libs.plugins.android.application)
}

// Not published. It exists so the adapters and the element reader are driven against a real app on a real
// device - `volition -p dev.mkonic.volition.sample ui`.
android {
    namespace = "dev.mkonic.volition.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.mkonic.volition.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.navigation.fragment)

    debugImplementation(project(":library"))
}
