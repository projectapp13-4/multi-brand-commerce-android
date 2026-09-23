import groovy.json.JsonSlurper
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

data class StorefrontOnboardingTarget(
    val application: String,
    val profile: String,
    val projectionFile: File,
    val localFile: File,
    val domain: String,
    val apiVersion: String
)

fun requireMap(value: Any?, field: String): Map<*, *> {
    check(value is Map<*, *>) { "$field must be an object." }
    return value
}

fun requireList(value: Any?, field: String): List<*> {
    check(value is List<*>) { "$field must be an array." }
    return value
}

fun repositoryFile(relativePath: String): File {
    check(relativePath.isNotBlank() && '\\' !in relativePath) { "Onboarding path is not repository-relative." }
    val repositoryRoot = rootProject.projectDir.canonicalFile
    val resolved = rootProject.file(relativePath).canonicalFile
    check(resolved.toPath().startsWith(repositoryRoot.toPath())) { "Onboarding path escapes the repository root." }
    return resolved
}
val onboardingRegistryFile = rootProject.file("config/onboarding/application-registry.v1.json")
val registryDigest = MessageDigest.getInstance("SHA-256")
    .digest(onboardingRegistryFile.readBytes())
    .joinToString("") { "%02x".format(it) }
val selectedTarget = if (selectedApplication != null && selectedProfile != null) {
    check(
        selectedApplication.matches(Regex("^[a-z][a-z0-9-]{1,31}$")) &&
            selectedProfile.matches(Regex("^[a-z][a-z0-9-]{1,31}$"))
    ) {
        "Storefront proofs require a valid enrolled application/profile."
    }
    val registry = requireMap(JsonSlurper().parse(onboardingRegistryFile), "registry")
    val applications = requireList(registry["applications"], "applications")
        .mapIndexed { index, value -> requireMap(value, "applications[$index]") }
        .filter { it["key"] == selectedApplication }
    check(applications.size == 1) { "Storefront proofs require an enrolled application/profile." }
    val application = applications.single()
    check(application["role"] == "real-brand-application" && application["fixtureOnly"] == false) {
        "Storefront proofs reject synthetic or fixture-only applications."
    }
    val profiles = requireList(application["profiles"], "profiles")
        .mapIndexed { index, value -> requireMap(value, "profiles[$index]") }
        .filter { it["key"] == selectedProfile }
    check(profiles.size == 1) { "Storefront proofs require an enrolled application/profile." }
    val profile = profiles.single()
    val storefront = requireMap(profile["storefront"], "storefront")
    check(storefront["mode"] == "enabled") { "Storefront proofs require an enabled Storefront profile." }
    val domain = storefront["domain"] as? String ?: error("Storefront domain is required.")
    val apiVersion = storefront["apiVersion"] as? String ?: error("Storefront API version is required.")
    val providerContracts = requireMap(registry["providerContracts"], "providerContracts")
    val allowedVersions = requireList(providerContracts["allowedStorefrontApiVersions"], "allowedStorefrontApiVersions")
    check(apiVersion in allowedVersions) { "Storefront API version is not allowed by the registry." }
    val projectionRoot = application["configurationProjection"] as? String
        ?: error("Storefront application projection is required.")
    val localConfiguration = profile["localConfiguration"] as? String
        ?: error("Storefront local configuration is required.")
    StorefrontOnboardingTarget(
        application = selectedApplication,
        profile = selectedProfile,
        projectionFile = repositoryFile("$projectionRoot/$selectedProfile.properties"),
        localFile = repositoryFile(localConfiguration),
        domain = domain,
        apiVersion = apiVersion
    )
} else {
    null
}

val projectionKeyInventory = setOf(
    "onboarding.schemaVersion", "onboarding.sourceRegistrySha256", "onboarding.application", "onboarding.profile",
    "app.brandKey", "app.brandDisplayName", "app.profileDisplayName", "app.analyticsNamespace", "app.environmentId",
    "app.defaultLocale", "app.supportedLocales", "app.marketId", "app.marketCountryCode", "app.marketCurrencyCode",
    "app.supportedTerritory", "app.searchNormalizationLocale", "app.databaseName", "app.customerAccountMode",
    "app.customerAccountUserAgent", "app.customerAccountCallbackSchemeSuffix", "app.customerAccountCallbackHost",
    "app.customerAccountCallbackPath", "app.customerAccountScopes", "app.cartPreferences", "app.cartKeyAlias",
    "app.customerPreferences", "app.customerKeyAlias", "android.applicationId.debug", "android.applicationId.release",
    "web.collectionAppLinkOrigin", "web.collectionAppLinkPathPrefix", "web.productAppLinkOrigin",
    "web.productAppLinkPathPrefix", "web.orderAppLinkOrigin", "web.orderAppLinkPathPrefix", "web.legalSupportOrigin",
    "web.legalSupportPath.support", "web.legalSupportPath.privacy", "web.legalSupportPath.terms",
    "web.legalSupportPath.shipping", "web.legalSupportPath.returns", "web.legalSupportPath.legalNotice",
    "web.checkoutHostPolicy", "web.assetLinksMode", "web.manifestAutoVerify", "shopify.storefrontMode",
    "shopify.storefrontDomain", "shopify.storefrontApiVersion", "shopify.storefrontMediaOrigins",
    "shopify.catalogMenuHandle", "shopify.homeRootType", "shopify.homeRootHandle", "shopify.homeContentSchemaVersion",
    "shopify.homeDefinitionContract",
    "firebase.mode", "firebase.ownershipKey", "firebase.configPath.debug", "firebase.configPath.release"
)
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
val selectedProjection = selectedTarget?.let {
    readCanonicalUtf8(
        it.projectionFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
        projectionKeyInventory
    )
}
val selectedLocalFile = selectedTarget?.localFile
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
    val homeContractTuple =
        Triple(
            selectedProjection.getProperty("shopify.homeRootType"),
            selectedProjection.getProperty("shopify.homeContentSchemaVersion"),
            selectedProjection.getProperty("shopify.homeDefinitionContract")
        )
    check(
        homeContractTuple in
            setOf(
                Triple("mobile_home", "1", "gate7-v1"),
                Triple("mobile_home_v2", "2", "pilot-media-v2")
            )
    ) {
        "HOME_CONTRACT_MISMATCH"
    }
    check(
        selectedProjection.getProperty("onboarding.schemaVersion") == "1" &&
            selectedProjection.getProperty("onboarding.sourceRegistrySha256") == registryDigest &&
            selectedProjection.getProperty("onboarding.application") == selectedTarget?.application &&
            selectedProjection.getProperty("onboarding.profile") == selectedTarget?.profile &&
            storefrontDomain == selectedTarget?.domain && storefrontApiVersion == selectedTarget?.apiVersion
    ) {
        "Storefront proof projection does not match the enrolled application/profile."
    }
}
val storefrontSchemaFile = file("src/main/graphql/com/gurbakir/storefront/schema.graphqls")
val runOwnedStorefrontProof =
    providers.gradleProperty("gurbakirRunOwnedStorefrontProof").orNull?.toBooleanStrictOrNull() ?: false
val requestedOwnedCartProof =
    providers.gradleProperty("onboardingRunOwnedStorefrontCartProof").orNull?.toBooleanStrictOrNull()
val requestedLegacyOwnedCartProof =
    providers.gradleProperty("gurbakirRunOwnedCartProof").orNull?.toBooleanStrictOrNull()
check(
    requestedOwnedCartProof == null ||
        requestedLegacyOwnedCartProof == null ||
        requestedOwnedCartProof == requestedLegacyOwnedCartProof
) {
    "Conflicting owned Storefront cart proof switches."
}
val runOwnedCartProof = requestedOwnedCartProof ?: requestedLegacyOwnedCartProof ?: false
val runOwnedHomeReadback =
    providers.gradleProperty("onboardingRunOwnedHomeReadback").orNull?.toBooleanStrictOrNull() ?: false
val runOwnedHomeV2Readback =
    providers.gradleProperty("onboardingRunOwnedHomeV2Readback").orNull?.toBooleanStrictOrNull() ?: false
check(
    !(runOwnedStorefrontProof || runOwnedCartProof || runOwnedHomeReadback || runOwnedHomeV2Readback) ||
        selectedApplication != null
) {
    "Opted-in Storefront proofs require explicit onboardingApplication and onboardingProfile."
}
if (runOwnedHomeV2Readback) {
    check(
        selectedProjection?.getProperty("shopify.homeRootType") == "mobile_home_v2" &&
            selectedProjection.getProperty("shopify.homeContentSchemaVersion") == "2" &&
            selectedProjection.getProperty("shopify.homeDefinitionContract") == "pilot-media-v2"
    ) {
        "Owned Home v2 proof requires the exact pilot-media-v2 contract."
    }
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
                it.systemProperty("onboarding.repoRoot", rootProject.projectDir.absolutePath)
                it.systemProperty(
                    "onboarding.projectionPath",
                    selectedTarget?.projectionFile?.relativeTo(
                        rootProject.projectDir
                    )?.invariantSeparatorsPath.orEmpty()
                )
                it.systemProperty(
                    "onboarding.localConfigurationPath",
                    selectedTarget?.localFile?.relativeTo(rootProject.projectDir)?.invariantSeparatorsPath.orEmpty()
                )
                it.systemProperty("onboarding.registrySha256", registryDigest)
                it.systemProperty("gurbakir.runOwnedStorefrontProof", runOwnedStorefrontProof.toString())
                it.systemProperty("gurbakir.runOwnedCartProof", runOwnedCartProof.toString())
                it.systemProperty("onboarding.runOwnedStorefrontCartProof", runOwnedCartProof.toString())
                it.systemProperty("onboarding.runOwnedHomeReadback", runOwnedHomeReadback.toString())
                it.systemProperty("onboarding.runOwnedHomeV2Readback", runOwnedHomeV2Readback.toString())
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
    testImplementation(libs.serialization.json)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.apollo.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.core)
}
