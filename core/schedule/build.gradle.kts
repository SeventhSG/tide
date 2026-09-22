plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin, no Android. Recurrence is date arithmetic, and keeping the
// framework out means these tests run in milliseconds without Robolectric,
// the same reason the progression engine is pure.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
