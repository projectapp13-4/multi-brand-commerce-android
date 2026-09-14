import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.apollo)
    alias(libs.plugins.detekt)
}

val localConfiguration = Properties().apply {
    val localFile = rootProject.file("config/local.properties")
    if (localFile.isFile) {
        localFile.inputStream().use(::load)
    }
}
val storefrontDomain = localConfiguration.getProperty("shopify.storefrontDomain", "").trim()
val storefrontApiVersion = localConfiguration.getProperty("shopify.storefrontApiVersion", "").trim()
val storefrontPublicToken = localConfiguration.getProperty("shopify.storefrontPublicToken", "").trim()
val storefrontSchemaFile = file("src/main/graphql/com/gurbakir/storefront/schema.graphqls")
val runOwnedStorefrontProof =
    providers.gradleProperty("gurbakirRunOwnedStorefrontProof").orNull?.toBooleanStrictOrNull() ?: false
val runOwnedCartProof =
    providers.gradleProperty("gurbakirRunOwnedCartProof").orNull?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.gurbakir.storefront"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
                it.systemProperty("gurbakir.repoRoot", rootProject.projectDir.absolutePath)
                it.systemProperty("gurbakir.runOwnedStorefrontProof", runOwnedStorefrontProof.toString())
                it.systemProperty("gurbakir.runOwnedCartProof", runOwnedCartProof.toString())
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
    service("storefront") {
        srcDir("src/main/graphql")
        packageName.set("com.gurbakir.storefront.graphql")
        schemaFile.set(storefrontSchemaFile)
        mapScalar("URL", "kotlin.String")
        mapScalar("Decimal", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("DateTime", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")

        if (storefrontDomain.isNotBlank() && storefrontApiVersion.isNotBlank() && storefrontPublicToken.isNotBlank()) {
            introspection {
                endpointUrl.set("https://$storefrontDomain/api/$storefrontApiVersion/graphql.json")
                headers.put("X-Shopify-Storefront-Access-Token", storefrontPublicToken)
                schemaFile.set(storefrontSchemaFile)
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(projects.foundation)
    implementation(libs.apollo.runtime)
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.apollo.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.core)
}
