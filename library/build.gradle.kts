import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    alias(libs.plugins.android.library)
}

// scripts/version.sh is the only version source - releasing is tagging. The artifact drops the tag's
// leading v, so tag v1.2.3 publishes volition 1.2.3.
fun versionOutput(kind: String): String = providers.exec {
    commandLine("bash", rootProject.file("scripts/version.sh").path, kind)
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("")

val versionName: String = versionOutput("name").ifEmpty { "unknown" }
val versionCode: Int = versionOutput("code").toIntOrNull() ?: 0
val artifactVersion: String = versionName.removePrefix("v")

android {
    namespace = "dev.mkonic.volition"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        buildConfigField("int", "VERSION_CODE", "$versionCode")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)

    // The adapter in dev.mkonic.volition.voyager is only loaded by an app that already has Voyager,
    // so it travels in the same artifact without being a dependency of it.
    compileOnly(libs.voyager.navigator)
}

/**
 * The ivy descriptor a consumer resolves the release AAR through - a GitHub release is not a Maven
 * repository, so the dependencies travel beside the AAR. Lists this build's direct runtime
 * dependencies at the versions it resolved.
 */
val writeIvyDescriptor = tasks.register("writeIvyDescriptor") {
    val runtime = configurations.named("releaseRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    val descriptor = layout.buildDirectory.file("outputs/ivy/ivy-$artifactVersion.xml")
    inputs.property("version", artifactVersion)
    outputs.file(descriptor)
    doLast {
        val dependencies = runtime.get().dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .filterNot { dependency ->
                // A BOM shapes versions but ships no artifact.
                val attributes = dependency.resolvedVariant.attributes
                val category = attributes.keySet().firstOrNull { it.name == "org.gradle.category" }
                attributes.getAttribute(category ?: return@filterNot false).toString().endsWith("platform")
            }
            .mapNotNull { it.selected.moduleVersion }
            .distinctBy { "${it.group}:${it.name}" }
            .sortedBy { "${it.group}:${it.name}" }
            .joinToString("\n") {
                """        <dependency org="${it.group}" name="${it.name}" rev="${it.version}" conf="default->default"/>"""
            }
        descriptor.get().asFile.apply { parentFile.mkdirs() }.writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<ivy-module version="2.0">
            |    <info organisation="dev.mkonic" module="volition" revision="$artifactVersion"/>
            |    <configurations>
            |        <conf name="default"/>
            |    </configurations>
            |    <publications>
            |        <artifact name="volition" type="aar" ext="aar" conf="default"/>
            |    </publications>
            |    <dependencies>
            |$dependencies
            |    </dependencies>
            |</ivy-module>
            |""".trimMargin()
        )
    }
}
