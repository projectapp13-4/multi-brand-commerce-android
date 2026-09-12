plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.example.gate2synthetic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gate2synthetic"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-gate2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "shop.0.gate2synthetic"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        managedDevices {
            localDevices {
                create("ciApi30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
                create("ciApi23") {
                    device = "Pixel 2"
                    apiLevel = 23
                    systemImageSource = "aosp"
                    require64Bit = true
                }
            }
        }
    }

    lint {
        lintConfig = rootProject.file("config/lint.xml")
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        sarifReport = true
        xmlReport = true
    }

    packaging {
        resources.excludes +=
            setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md"
            )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(projects.mobileCore)
    implementation(projects.foundation)
    implementation(projects.storefront)
    implementation(projects.account)
    implementation(projects.checkout)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coil.compose)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
