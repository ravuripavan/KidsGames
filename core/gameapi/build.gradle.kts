plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation("androidx.compose.runtime:runtime:1.7.6")
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(21)
}
