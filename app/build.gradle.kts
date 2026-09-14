import com.android.build.api.dsl.ApplicationProductFlavor
import groovy.json.JsonSlurper
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

data class FirebaseVariantSpec(val packageName: String, val profile: String)

data class OnboardingProfileInput(
    val key: String,
    val projection: Map<String, String>,
    val local: Map<String, String>,
    val localFile: File
)

val projectionKeys =
    setOf(
        "onboarding.schemaVersion",
        "onboarding.sourceRegistrySha256",
        "onboarding.application",
        "onboarding.profile",
        "app.brandKey",
        "app.brandDisplayName",
        "app.profileDisplayName",
        "app.analyticsNamespace",
        "app.environmentId",
        "app.defaultLocale",
        "app.supportedLocales",
        "app.marketId",
        "app.marketCountryCode",
        "app.marketCurrencyCode",
        "app.supportedTerritory",
        "app.searchNormalizationLocale",
        "app.databaseName",
        "app.customerAccountMode",
        "app.customerAccountUserAgent",
        "app.customerAccountCallbackSchemeSuffix",
        "app.customerAccountCallbackHost",
        "app.customerAccountCallbackPath",
        "app.customerAccountScopes",
        "app.cartPreferences",
        "app.cartKeyAlias",
        "app.customerPreferences",
        "app.customerKeyAlias",
        "android.applicationId.debug",
        "android.applicationId.release",
        "web.collectionAppLinkOrigin",
        "web.collectionAppLinkPathPrefix",
        "web.productAppLinkOrigin",
        "web.productAppLinkPathPrefix",
        "web.orderAppLinkOrigin",
        "web.orderAppLinkPathPrefix",
        "web.legalSupportOrigin",
        "web.legalSupportPath.support",
        "web.legalSupportPath.privacy",
        "web.legalSupportPath.terms",
        "web.legalSupportPath.shipping",
        "web.legalSupportPath.returns",
        "web.legalSupportPath.legalNotice",
        "web.checkoutHostPolicy",
        "web.assetLinksMode",
        "web.manifestAutoVerify",
        "shopify.storefrontMode",
        "shopify.storefrontDomain",
        "shopify.storefrontApiVersion",
        "shopify.storefrontMediaOrigins",
        "shopify.catalogMenuHandle",
        "shopify.homeRootType",
        "shopify.homeRootHandle",
        "shopify.homeContentSchemaVersion",
        "firebase.mode",
        "firebase.ownershipKey",
        "firebase.configPath.debug",
        "firebase.configPath.release"
    )
val localKeys =
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

fun readCanonicalUtf8Properties(file: File, allowedKeys: Set<String>, allowAbsent: Boolean): Map<String, String> {
    if (!file.isFile) {
        check(allowAbsent) { "Required scoped onboarding configuration is missing." }
        return emptyMap()
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
        check(key == key.trim() && value == value.trim()) {
            "Onboarding property whitespace is not canonical."
        }
        check(key in allowedKeys) { "Unknown onboarding property key: $key" }
        check(seen.add(key.lowercase())) { "Duplicate onboarding property key: $key" }
    }
    return Properties().apply {
        file.reader(StandardCharsets.UTF_8).use(::load)
    }.entries.associate { (key, value) -> key.toString() to value.toString() }
}

fun loadProfile(key: String): OnboardingProfileInput {
    val projectionFile =
        rootProject.file("config/onboarding/generated/gurbakir/$key.properties")
    val localFile = rootProject.file("config/local/gurbakir/$key.properties")
    val projection = readCanonicalUtf8Properties(projectionFile, projectionKeys, allowAbsent = false)
    check(projection["onboarding.application"] == "gurbakir" && projection["onboarding.profile"] == key) {
        "Onboarding projection target does not match the Gürbakır application profile."
    }
    val local = readCanonicalUtf8Properties(localFile, localKeys, allowAbsent = true)
    if (local.isNotEmpty()) {
        check(local.keys == localKeys && local.values.all(String::isNotBlank)) {
            "Present scoped client configuration must contain every required non-empty client value."
        }
    }
    return OnboardingProfileInput(key, projection, local, localFile)
}

val onboardingProfiles =
    linkedMapOf(
        "development" to loadProfile("development"),
        "staging" to loadProfile("staging")
    )

val selectedApplication = providers.gradleProperty("onboardingApplication").orNull
val selectedProfile = providers.gradleProperty("onboardingProfile").orNull
val requireConfiguredProfile =
    providers.gradleProperty("requireConfiguredProfile").orNull?.toBooleanStrictOrNull() ?: false
check((selectedApplication == null) == (selectedProfile == null)) {
    "onboardingApplication and onboardingProfile must be supplied together."
}
if (selectedApplication != null) {
    check(selectedApplication == "gurbakir") { "The :app module can only consume the enrolled gurbakir application." }
    check(selectedProfile in onboardingProfiles) { "Unknown onboarding profile for :app." }
}
if (requireConfiguredProfile) {
    check(selectedApplication != null && selectedProfile != null) {
        "requireConfiguredProfile requires an explicit application and profile."
    }
    val selected = onboardingProfiles.getValue(selectedProfile)
    check(selected.localFile.isFile && selected.local.keys == localKeys) {
        "The explicitly selected onboarding profile is UNCONFIGURED."
    }
    val otherProfile = onboardingProfiles.keys.single { it != selectedProfile }
    check(
        gradle.startParameter.taskNames.none {
            it.startsWith(":app:", ignoreCase = true) && it.contains(otherProfile, ignoreCase = true)
        }
    ) {
        "Configured app tasks must belong to the explicitly selected onboarding profile."
    }
}

fun OnboardingProfileInput.projectionValue(key: String): String =
    checkNotNull(projection[key]) { "Missing tracked onboarding projection value: $key" }

fun OnboardingProfileInput.localValue(key: String): String = local[key].orEmpty()

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun OnboardingProfileInput.redirectScheme(): String =
    runCatching { URI(localValue("shopify.customerAccountRedirectUri")).scheme }
        .getOrNull()
        ?.takeIf { it.startsWith("shop.") }
        ?: "shop.unconfigured.gurbakir"

fun OnboardingProfileInput.originHost(key: String): String =
    checkNotNull(URI(projectionValue(key)).host) { "Projected web origin has no host." }

fun ApplicationProductFlavor.configureOnboarding(profile: OnboardingProfileInput) {
    val releaseApplicationId = profile.projectionValue("android.applicationId.release")
    check(profile.projectionValue("android.applicationId.debug") == "$releaseApplicationId.debug") {
        "The current Gürbakır debug application ID must be the release ID plus .debug."
    }
    applicationId = releaseApplicationId
    fun field(name: String, value: String) = buildConfigField("String", name, value.asBuildConfigString())
    field("ENVIRONMENT_ID", profile.projectionValue("app.environmentId"))
    field("BRAND_KEY", profile.projectionValue("app.brandKey"))
    field("BRAND_DISPLAY_NAME", profile.projectionValue("app.brandDisplayName"))
    field("ANALYTICS_NAMESPACE", profile.projectionValue("app.analyticsNamespace"))
    field("DEFAULT_LOCALE", profile.projectionValue("app.defaultLocale"))
    field("SUPPORTED_LOCALES", profile.projectionValue("app.supportedLocales"))
    field("MARKET_ID", profile.projectionValue("app.marketId"))
    field("MARKET_COUNTRY_CODE", profile.projectionValue("app.marketCountryCode"))
    field("MARKET_CURRENCY_CODE", profile.projectionValue("app.marketCurrencyCode"))
    field("SUPPORTED_TERRITORY", profile.projectionValue("app.supportedTerritory"))
    field("SEARCH_NORMALIZATION_LOCALE", profile.projectionValue("app.searchNormalizationLocale"))
    field("DATABASE_NAME", profile.projectionValue("app.databaseName"))
    field("CART_PREFERENCES", profile.projectionValue("app.cartPreferences"))
    field("CART_KEY_ALIAS", profile.projectionValue("app.cartKeyAlias"))
    field("CUSTOMER_PREFERENCES", profile.projectionValue("app.customerPreferences"))
    field("CUSTOMER_KEY_ALIAS", profile.projectionValue("app.customerKeyAlias"))
    field("STOREFRONT_DOMAIN", profile.projectionValue("shopify.storefrontDomain"))
    field("STOREFRONT_API_VERSION", profile.projectionValue("shopify.storefrontApiVersion"))
    field("STOREFRONT_PUBLIC_TOKEN", profile.localValue("shopify.storefrontPublicToken"))
    field("CATALOG_MENU_HANDLE", profile.projectionValue("shopify.catalogMenuHandle"))
    field("HOME_CONTENT_ROOT_HANDLE", profile.projectionValue("shopify.homeRootHandle"))
    field("CUSTOMER_ACCOUNT_CLIENT_ID", profile.localValue("shopify.customerAccountClientId"))
    field("CUSTOMER_ACCOUNT_ISSUER", profile.localValue("shopify.customerAccountIssuer"))
    field("CUSTOMER_ACCOUNT_AUTH_ENDPOINT", profile.localValue("shopify.customerAccountAuthorizationEndpoint"))
    field("CUSTOMER_ACCOUNT_TOKEN_ENDPOINT", profile.localValue("shopify.customerAccountTokenEndpoint"))
    field("CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT", profile.localValue("shopify.customerAccountLogoutEndpoint"))
    field("CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT", profile.localValue("shopify.customerAccountGraphqlEndpoint"))
    field("CUSTOMER_ACCOUNT_REDIRECT_URI", profile.localValue("shopify.customerAccountRedirectUri"))
    field("CUSTOMER_ACCOUNT_USER_AGENT", profile.projectionValue("app.customerAccountUserAgent"))
    field("CUSTOMER_ACCOUNT_SCOPES", profile.projectionValue("app.customerAccountScopes").replace(',', ' '))
    field("COLLECTION_APP_LINK_ORIGIN", profile.projectionValue("web.collectionAppLinkOrigin"))
    field("COLLECTION_APP_LINK_PATH_PREFIX", profile.projectionValue("web.collectionAppLinkPathPrefix"))
    field("PRODUCT_APP_LINK_ORIGIN", profile.projectionValue("web.productAppLinkOrigin"))
    field("PRODUCT_APP_LINK_PATH_PREFIX", profile.projectionValue("web.productAppLinkPathPrefix"))
    field("ORDER_APP_LINK_ORIGIN", profile.projectionValue("web.orderAppLinkOrigin"))
    field("ORDER_APP_LINK_PATH_PREFIX", profile.projectionValue("web.orderAppLinkPathPrefix"))
    field("LEGAL_SUPPORT_ORIGIN", profile.projectionValue("web.legalSupportOrigin"))
    field("LEGAL_SUPPORT_PATH_SUPPORT", profile.projectionValue("web.legalSupportPath.support"))
    field("LEGAL_SUPPORT_PATH_PRIVACY", profile.projectionValue("web.legalSupportPath.privacy"))
    field("LEGAL_SUPPORT_PATH_TERMS", profile.projectionValue("web.legalSupportPath.terms"))
    field("LEGAL_SUPPORT_PATH_SHIPPING", profile.projectionValue("web.legalSupportPath.shipping"))
    field("LEGAL_SUPPORT_PATH_RETURNS", profile.projectionValue("web.legalSupportPath.returns"))
    field("LEGAL_SUPPORT_PATH_LEGAL_NOTICE", profile.projectionValue("web.legalSupportPath.legalNotice"))
    manifestPlaceholders["appAuthRedirectScheme"] = profile.redirectScheme()
    manifestPlaceholders["collectionAppLinkHost"] = profile.originHost("web.collectionAppLinkOrigin")
    manifestPlaceholders["collectionAppLinkPathPrefix"] = profile.projectionValue("web.collectionAppLinkPathPrefix")
    manifestPlaceholders["productAppLinkHost"] = profile.originHost("web.productAppLinkOrigin")
    manifestPlaceholders["productAppLinkPathPrefix"] = profile.projectionValue("web.productAppLinkPathPrefix")
    manifestPlaceholders["orderAppLinkHost"] = profile.originHost("web.orderAppLinkOrigin")
    manifestPlaceholders["orderAppLinkPathPrefix"] = profile.projectionValue("web.orderAppLinkPathPrefix")
    resValue("string", "app_name", profile.projectionValue("app.profileDisplayName"))
}

val firebaseConfigurationFiles =
    linkedMapOf(
        onboardingProfiles.getValue("development").projectionValue("firebase.configPath.debug").removePrefix("app/") to
            FirebaseVariantSpec(
                onboardingProfiles.getValue("development").projectionValue("android.applicationId.debug"),
                "development"
            ),
        onboardingProfiles.getValue(
            "development"
        ).projectionValue("firebase.configPath.release").removePrefix("app/") to
            FirebaseVariantSpec(
                onboardingProfiles.getValue("development").projectionValue("android.applicationId.release"),
                "development"
            ),
        onboardingProfiles.getValue("staging").projectionValue("firebase.configPath.debug").removePrefix("app/") to
            FirebaseVariantSpec(
                onboardingProfiles.getValue("staging").projectionValue("android.applicationId.debug"),
                "staging"
            ),
        onboardingProfiles.getValue("staging").projectionValue("firebase.configPath.release").removePrefix("app/") to
            FirebaseVariantSpec(
                onboardingProfiles.getValue("staging").projectionValue("android.applicationId.release"),
                "staging"
            )
    )
val presentFirebaseConfigurations =
    firebaseConfigurationFiles.keys.filter { project.layout.projectDirectory.file(it).asFile.isFile }
check(
    presentFirebaseConfigurations.isEmpty() || presentFirebaseConfigurations.size == firebaseConfigurationFiles.size
) {
    "Firebase configuration must be absent or complete for all four non-production variants."
}
val firebaseClientConfigured = presentFirebaseConfigurations.size == firebaseConfigurationFiles.size
if (firebaseClientConfigured) {
    val projectIdsByEnvironment = mutableMapOf<String, MutableSet<String>>()
    firebaseConfigurationFiles.forEach { (relativePath, spec) ->
        val parsed =
            JsonSlurper().parse(project.layout.projectDirectory.file(relativePath).asFile) as Map<*, *>
        val projectInfo = parsed["project_info"] as? Map<*, *>
        val projectId = projectInfo?.get("project_id") as? String
        check(!projectId.isNullOrBlank()) { "Firebase project identity is missing for $relativePath." }
        val clients = parsed["client"] as? List<*> ?: emptyList<Any>()
        val matchingClients = clients.filter { rawClient ->
            val client = rawClient as? Map<*, *> ?: return@filter false
            val clientInfo = client["client_info"] as? Map<*, *> ?: return@filter false
            val androidInfo = clientInfo["android_client_info"] as? Map<*, *> ?: return@filter false
            androidInfo["package_name"] == spec.packageName
        }
        check(matchingClients.size == 1) {
            "Firebase configuration must contain one exact client for ${spec.packageName}."
        }
        projectIdsByEnvironment.getOrPut(spec.profile) { mutableSetOf() }.add(projectId)
    }
    check(projectIdsByEnvironment.values.all { it.size == 1 }) {
        "Firebase debug and release registrations must share one project per environment."
    }
    check(projectIdsByEnvironment.values.map { it.single() }.toSet().size == 2) {
        "Firebase development and staging configurations must use separate projects."
    }
    apply(plugin = "com.google.gms.google-services")
}

val validateOnboardingProjections by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates tracked Gate 8 projections without credentials, network, repair, or Gradle recursion."
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
    environment =
        System.getenv().filterKeys { key ->
            !key.matches(Regex("^MB_[A-Z0-9_]+_(SHOPIFY_ADMIN_TOKEN|FIREBASE_ACCESS_TOKEN)$"))
        }
}
android {
    namespace = "com.gurbakir.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gurbakir.mobile.unconfigured"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseClientConfigured.toString())
    }

    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            configureOnboarding(onboardingProfiles.getValue("development"))
        }
        create("staging") {
            dimension = "environment"
            configureOnboarding(onboardingProfiles.getValue("staging"))
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isPseudoLocalesEnabled = true
            buildConfigField("boolean", "IS_RELEASE_BUILD", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "IS_RELEASE_BUILD", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
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
        resources.excludes += setOf(
            "/META-INF/AL2.0",
            "/META-INF/LGPL2.1",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md"
        )
    }
}

tasks.configureEach {
    val projectionBackedPrefixes =
        listOf(
            "assemble",
            "bundle",
            "compile",
            "generate",
            "hilt",
            "ksp",
            "lint",
            "merge",
            "package",
            "process",
            "test"
        )
    if (name != validateOnboardingProjections.name && projectionBackedPrefixes.any(name::startsWith)) {
        dependsOn(validateOnboardingProjections)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(projects.mobileCore)
    implementation(projects.foundation)
    implementation(projects.storefront)
    implementation(projects.account)
    implementation(projects.checkout)
    implementation(projects.firebase)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(platform(libs.firebase.bom))
    ksp(libs.hilt.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
