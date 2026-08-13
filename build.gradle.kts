// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("org.cyclonedx.bom") version "3.2.4"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

subprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        when (project.path) {
            ":app" -> {
                // The release SBOM must describe only dependencies shipped with the app.
                includeConfigs.set(listOf("releaseRuntimeClasspath"))
            }

            ":benchmark" -> enabled = false
        }
    }
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
