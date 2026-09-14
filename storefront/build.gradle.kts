import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
fun readCanonicalUtf8(path: String, allowedKeys: Set<String>, allowAbsent: Boolean = false): Properties {
    val file = rootProject.file(path)
    if (!file.isFile) {
        check(allowAbsent) { "Required onboarding configuration is missing." }
        return Properties()
    }
    val bytes = file.readBytes()
    check(!(bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte())) {
        "Onboarding properties must be UTF-8 without a BOM."
    }
    val text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    check('\r' !in text) { "Onboarding properties must use LF line endings." }
    val seen = mutableSetOf<String>()
    text.split('\n').dropLastWhile(String::isEmpty).forEachIndexed { index, line ->
        check(line.isNotEmpty() && !line.startsWith("#") && '\\' !in line) {
            "Malformed onboarding property line ${index + 1}."
        }
        val separator = line.indexOf('=')
        check(separator > 0) { "Malformed onboarding property line ${index + 1}." }
        val key = line.substring(0, separator)
        val value = line.substring(separator + 1)
        check(key == key.trim() && value == value.trim() && key in allowedKeys) {
            "Noncanonical onboarding property line ${index + 1}."
        }
        check(seen.add(key.lowercase())) { "Duplicate onboarding property key: $key" }
    }
    return Properties().apply { file.reader(StandardCharsets.UTF_8).use(::load) }
}
val projectionKeyInventory = rootProject.file("config/onboarding/generated/gurbakir/development.properties")
    .readLines(StandardCharsets.UTF_8)
    .map { it.substringBefore('=') }
    .toSet()
val selectedProjection = selectedProfile?.let {
    readCanonicalUtf8("config/onboarding/generated/gurbakir/$it.properties", projectionKeyInventory)
}
val selectedLocalFile = selectedProfile?.let { rootProject.file("config/local/gurbakir/$it.properties") }
val selectedLocal = selectedLocalFile?.takeIf(File::isFile)?.let {
    readCanonicalUtf8(
        it.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
        setOf(
            "shopify.storefrontPublicToken",
            "shopify.customerAccountClientId",
            "shopify.customerAccountIssuer",
            "shopify.customerAccountAuthorizationEndpoint",
            "shopify.customerAccountTokenEndpoint",
            "shopify.customerAccountLogoutEndpoint",
            "shopify.customerAccountGraphqlEndpoint",
            "shopify.customerAccountRedirectUri"
        )
    )
}
val storefrontDomain = selectedProjection?.getProperty("shopify.storefrontDomain", "")?.trim().orEmpty()
val storefrontApiVersion = selectedProjection?.getProperty("shopify.storefrontApiVersion", "")?.trim().orEmpty()
val storefrontPublicToken = selectedLocal?.getProperty("shopify.storefrontPublicToken", "")?.trim().orEmpty()
if (selectedProjection != null) {
    val registryDigest = MessageDigest.getInstance("SHA-256")
        .digest(rootProject.file("config/onboarding/application-registry.v1.json").readBytes())
        .joinToString("") { "%02x".format(it) }
    check(
        selectedProjection.getProperty("onboarding.schemaVersion") == "1" &&
            selectedProjection.getProperty("onboarding.sourceRegistrySha256") == registryDigest &&
            selectedProjection.getProperty("onboarding.application") == "gurbakir" &&
            selectedProjection.getProperty("onboarding.profile") == selectedProfile &&
            storefrontDomain == "gurbakir.com" && storefrontApiVersion == "2026-07"
    ) {
        "Storefront proof projection does not match the enrolled Gürbakır profile."
    }
}
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

val validateOnboardingProjections by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates Gate 8 projections before Storefront proof or introspection tasks."
    workingDir(rootProject.projectDir)
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        rootProject.file("scripts/Invoke-MultiBrandOnboarding.ps1").absolutePath,
        "-Command",
        "Validate",
        "-ProjectionOnly"
    )
    environment = System.getenv().filterKeys { key ->
        !key.matches(Regex("^MB_[A-Z0-9_]+_(SHOPIFY_ADMIN_TOKEN|FIREBASE_ACCESS_TOKEN)$"))
    }
}

tasks.configureEach {
    if (name != validateOnboardingProjections.name &&
        (name.startsWith("test") || name.contains("ApolloSchema", ignoreCase = true))
    ) {
        dependsOn(validateOnboardingProjections)
    }
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
