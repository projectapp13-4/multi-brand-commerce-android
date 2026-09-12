plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.gurbakir.firebase"
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
    constraints {
        implementation("androidx.annotation:annotation:1.3.0") {
            because("avoid the unverified legacy annotation jar selected by the release-only Firebase graph")
        }
        implementation("com.google.firebase:firebase-measurement-connector:19.0.0") {
            because("retain the already verified connector version when the debug-only messaging graph is absent")
        }
    }
    api(projects.foundation)
    implementation(platform(libs.firebase.bom))
    debugImplementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.firebase.installations)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
}
