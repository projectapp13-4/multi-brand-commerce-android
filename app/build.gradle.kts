import groovy.json.JsonSlurper
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

data class FirebaseVariantSpec(val packageName: String, val environment: String)

val firebaseConfigurationFiles =
    linkedMapOf(
        "src/developmentDebug/google-services.json" to
            FirebaseVariantSpec("com.gurbakir.mobile.dev.debug", "development"),
        "src/developmentRelease/google-services.json" to
            FirebaseVariantSpec("com.gurbakir.mobile.dev", "development"),
        "src/stagingDebug/google-services.json" to
            FirebaseVariantSpec("com.gurbakir.mobile.staging.debug", "staging"),
        "src/stagingRelease/google-services.json" to
            FirebaseVariantSpec("com.gurbakir.mobile.staging", "staging")
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
        projectIdsByEnvironment.getOrPut(spec.environment) { mutableSetOf() }.add(projectId)
    }
    check(projectIdsByEnvironment.values.all { it.size == 1 }) {
        "Firebase debug and release registrations must share one project per environment."
    }
    check(projectIdsByEnvironment.values.map { it.single() }.toSet().size == 2) {
        "Firebase development and staging configurations must use separate projects."
    }
    apply(plugin = "com.google.gms.google-services")
}

val defaultsFile = rootProject.layout.projectDirectory.file("config/local.defaults.properties")
val localFile = rootProject.layout.projectDirectory.file("config/local.properties")
val environmentProperties = Properties().apply {
    defaultsFile.asFile.inputStream().use(::load)
    if (localFile.asFile.isFile) {
        localFile.asFile.inputStream().use(::load)
    }
}

fun configValue(key: String): String = environmentProperties.getProperty(key, "").trim()

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val customerAccountRedirectScheme =
    runCatching { URI(configValue("shopify.customerAccountRedirectUri")).scheme }
        .getOrNull()
        ?.takeIf { it.startsWith("shop.") }
        ?: "shop.unconfigured.gurbakir"
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

        buildConfigField("String", "STOREFRONT_DOMAIN", configValue("shopify.storefrontDomain").asBuildConfigString())
        buildConfigField(
            "String",
            "STOREFRONT_API_VERSION",
            configValue("shopify.storefrontApiVersion").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "STOREFRONT_PUBLIC_TOKEN",
            configValue("shopify.storefrontPublicToken").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CATALOG_MENU_HANDLE",
            configValue("shopify.catalogMenuHandle").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "HOME_CONTENT_ROOT_HANDLE",
            configValue("shopify.homeContentRootHandle").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_CLIENT_ID",
            configValue("shopify.customerAccountClientId").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_ISSUER",
            configValue("shopify.customerAccountIssuer").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_AUTH_ENDPOINT",
            configValue("shopify.customerAccountAuthorizationEndpoint").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_TOKEN_ENDPOINT",
            configValue("shopify.customerAccountTokenEndpoint").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT",
            configValue("shopify.customerAccountLogoutEndpoint").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT",
            configValue("shopify.customerAccountGraphqlEndpoint").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_REDIRECT_URI",
            configValue("shopify.customerAccountRedirectUri").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "CUSTOMER_ACCOUNT_SCOPES",
            configValue("shopify.customerAccountScopes").asBuildConfigString()
        )
        manifestPlaceholders["appAuthRedirectScheme"] = customerAccountRedirectScheme
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseClientConfigured.toString())
    }

    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            applicationId = "com.gurbakir.mobile.dev"
            buildConfigField("String", "ENVIRONMENT_ID", "\"development\"")
            resValue("string", "app_name", "Gürbakır Geliştirme")
        }
        create("staging") {
            dimension = "environment"
            applicationId = "com.gurbakir.mobile.staging"
            buildConfigField("String", "ENVIRONMENT_ID", "\"staging\"")
            resValue("string", "app_name", "Gürbakır Hazırlık")
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
