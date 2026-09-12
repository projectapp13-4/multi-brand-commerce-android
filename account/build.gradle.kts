plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.apollo)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.gurbakir.account"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.gurbakir.account.test"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
        managedDevices {
            localDevices {
                create("ciApi30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    lint {
        lintConfig = rootProject.file("config/lint.xml")
        abortOnError = true
        warningsAsErrors = true
        sarifReport = true
        xmlReport = true
    }
}

apollo {
    service("customerAccount") {
        srcDir("src/main/graphql")
        packageName.set("com.gurbakir.account.graphql")
        schemaFile.set(file("src/main/graphql/com/gurbakir/account/schema.graphqls"))
        mapScalarToKotlinString("CurrencyCode")
        mapScalarToKotlinString("DateTime")
        mapScalarToKotlinString("Decimal")
        mapScalarToKotlinString("URL")
    }
}

dependencies {
    api(projects.foundation)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.apollo.runtime)
    implementation(libs.appauth)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.core)
}
