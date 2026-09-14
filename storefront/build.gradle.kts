import java.nio.charset.StandardCharsets
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.apollo)
    alias(libs.plugins.detekt)
}

val selectedApplication = providers.gradleProperty("onboardingApplication").orNull
val selectedProfile = providers.gradleProperty("onboardingProfile").orNull
check((selectedApplication == null) == (selectedProfile == null)) {
    "onboardingApplication and onboardingProfile must be supplied together."
}
if (selectedApplication != null) {
    check(selectedApplication == "gurbakir" && selectedProfile in setOf("development", "staging")) {
        "Storefront proofs require an enrolled application/profile."
    }
}
fun readUtf8(path: String): Properties = Properties().apply {
    rootProject.file(path).reader(StandardCharsets.UTF_8).use(::load)
}
val selectedProjection = selectedProfile?.let { readUtf8("config/onboarding/generated/gurbakir/$it.properties") }
val selectedLocalFile = selectedProfile?.let { rootProject.file("config/local/gurbakir/$it.properties") }
val selectedLocal = selectedLocalFile?.takeIf(File::isFile)?.let {
    readUtf8(it.relativeTo(rootProject.projectDir).invariantSeparatorsPath)
}
val storefrontDomain = selectedProjection?.getProperty("shopify.storefrontDomain", "")?.trim().orEmpty()
val storefrontApiVersion = selectedProjection?.getProperty("shopify.storefrontApiVersion", "")?.trim().orEmpty()
val storefrontPublicToken = selectedLocal?.getProperty("shopify.storefrontPublicToken", "")?.trim().orEmpty()
val storefrontSchemaFile = file("src/main/graphql/com/gurbakir/storefront/schema.graphqls")
val runOwnedStorefrontProof =
    providers.gradleProperty("gurbakirRunOwnedStorefrontProof").orNull?.toBooleanStrictOrNull() ?: false
val runOwnedCartProof =
    providers.gradleProperty("gurbakirRunOwnedCartProof").orNull?.toBooleanStrictOrNull() ?: false
val runOwnedHomeReadback =
    providers.gradleProperty("onboardingRunOwnedHomeReadback").orNull?.toBooleanStrictOrNull() ?: false
check(!(runOwnedStorefrontProof || runOwnedCartProof || runOwnedHomeReadback) || selectedApplication != null) {
    "Opted-in Storefront proofs require explicit onboardingApplication and onboardingProfile."
}

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
                it.systemProperty("onboarding.runOwnedHomeReadback", runOwnedHomeReadback.toString())
                it.systemProperty("onboarding.application", selectedApplication.orEmpty())
                it.systemProperty("onboarding.profile", selectedProfile.orEmpty())
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
