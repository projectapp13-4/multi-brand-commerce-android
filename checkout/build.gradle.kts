plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.gurbakir.checkout"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.unitTests.all {
        it.useJUnitPlatform()
    }

    lint {
        lintConfig = rootProject.file("config/lint.xml")
        abortOnError = true
        warningsAsErrors = true
        sarifReport = true
        xmlReport = true
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.checkout.kit)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
}
