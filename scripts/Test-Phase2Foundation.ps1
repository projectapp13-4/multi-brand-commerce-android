[CmdletBinding()]
param(
    [switch]$RequireCleanWorktree
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [bool]$Passed,
        [Parameter(Mandatory)] [string]$Evidence
    )

    $checks.Add([pscustomobject]@{
        name = $Name
        passed = $Passed
        evidence = $Evidence
    })
}

function Read-RequiredText {
    param([Parameter(Mandatory)] [string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }
    return Get-Content -LiteralPath $path -Raw
}

Push-Location $repoRoot
try {
    $requiredFiles = @(
        'settings.gradle.kts',
        'build.gradle.kts',
        'gradle.properties',
        'gradle/libs.versions.toml',
        'gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties',
        '.github/workflows/android-foundation.yml',
        '.gitleaks.toml',
        'docs/decisions/ADR-0003-ANDROID-SDK-BASELINE.md',
        'docs/phase2/PHASE-2-FOUNDATION-REPORT.md',
        'docs/phase2/GATE-STATUS.md',
        'docs/phase2/BUILD-AND-BOOTSTRAP.md',
        'docs/phase2/DEPENDENCY-AND-VERSION-DECISIONS.md',
        'docs/phase2/CONFIGURATION-AND-SECRETS.md',
        'docs/phase2/THREAT-MODEL.md',
        'docs/phase2/STOREFRONT-INTEGRATION-PROOF.md',
        'docs/phase2/CUSTOMER-ACCOUNT-OAUTH-PROOF.md',
        'docs/phase2/CHECKOUT-KIT-PROOF.md',
        'docs/phase2/FIREBASE-PROOF.md',
        'docs/phase2/DEVICE-TEST-EVIDENCE.md',
        'docs/phase2/CANDIDATE-FEATURE-ACCEPTANCE-PACK.md',
        'docs/phase2/candidate-feature-acceptance-pack.csv',
        'docs/phase2/gate-results.json'
        'scripts/Test-FirebaseConfiguration.ps1'
        'scripts/Provision-CustomerAccountDiscovery.ps1'
        'foundation/src/main/kotlin/com/gurbakir/foundation/config/BrandConfiguration.kt'
        'foundation/src/main/kotlin/com/gurbakir/foundation/ui/CommerceTheme.kt'
        'app/src/main/kotlin/com/gurbakir/mobile/brand/GurbakirBrand.kt'
        'app/src/main/res/values/strings.xml'
        'app/src/main/res/values-en/strings.xml'
        'app/src/androidTest/kotlin/com/gurbakir/mobile/FoundationScreenTest.kt'
        'storefront/src/main/graphql/com/gurbakir/storefront/schema.graphqls'
        'storefront/src/main/graphql/com/gurbakir/storefront/ShopSummary.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CatalogPage.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartCreate.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartById.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartLinesAdd.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartLinesUpdate.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartLinesRemove.graphql'
        'storefront/src/main/graphql/com/gurbakir/storefront/CartBuyerIdentityUpdate.graphql'
        'storefront/src/main/kotlin/com/gurbakir/storefront/ApolloStorefrontGateway.kt'
        'storefront/src/main/kotlin/com/gurbakir/storefront/CartCoordinator.kt'
        'storefront/src/main/kotlin/com/gurbakir/storefront/AndroidKeystoreCartSessionStore.kt'
        'storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontReadProofTest.kt'
        'storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontCartProofTest.kt'
        'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorization.kt'
        'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorizationBrowser.kt'
        'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountDiscovery.kt'
        'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenClient.kt'
        'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountLogoutClient.kt'
        'account/src/main/kotlin/com/gurbakir/account/session/CustomerAccountSessionCoordinator.kt'
        'account/src/main/kotlin/com/gurbakir/account/CustomerAccountGateway.kt'
        'app/src/main/kotlin/com/gurbakir/mobile/CustomerAccountProofController.kt'
        'app/src/main/kotlin/com/gurbakir/mobile/CustomerAccountProofViewModel.kt'
        'app/src/main/kotlin/com/gurbakir/mobile/CommerceProofController.kt'
        'app/src/main/kotlin/com/gurbakir/mobile/CommerceProofViewModel.kt'
        'checkout/src/main/kotlin/com/gurbakir/checkout/OfficialCheckoutKitClient.kt'
        'checkout/src/test/kotlin/com/gurbakir/checkout/CheckoutUrlPolicyTest.kt'
    )
    $missingFiles = @($requiredFiles | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Leaf)
    })
    $requiredFilesEvidence = if ($missingFiles.Count -eq 0) {
        "all $($requiredFiles.Count) required artifacts present"
    } else {
        "missing: $($missingFiles -join ', ')"
    }
    Add-Check 'required Phase 2 artifacts exist' ($missingFiles.Count -eq 0) $requiredFilesEvidence

    $settings = Read-RequiredText 'settings.gradle.kts'
    $expectedModules = @('app', 'foundation', 'storefront', 'account', 'checkout', 'firebase')
    $missingModules = @($expectedModules | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot "$_/build.gradle.kts") -PathType Leaf) -or
        $null -eq $settings -or
        $settings -notmatch [regex]::Escape(":$_")
    })
    $modulesEvidence = if ($missingModules.Count -eq 0) {
        $expectedModules -join ', '
    } else {
        "missing: $($missingModules -join ', ')"
    }
    Add-Check 'six approved Gradle modules are included' ($missingModules.Count -eq 0) $modulesEvidence

    $androidBuildFiles = @($expectedModules | ForEach-Object {
        Read-RequiredText "$_/build.gradle.kts"
    })
    $sdkMismatch = @($androidBuildFiles | Where-Object {
        $null -eq $_ -or $_ -notmatch 'compileSdk\s*=\s*36' -or $_ -notmatch 'minSdk\s*=\s*23'
    })
    $appBuild = Read-RequiredText 'app/build.gradle.kts'
    $appBaselineCorrect = $null -ne $appBuild -and
        $appBuild -match 'targetSdk\s*=\s*36' -and
        $appBuild -match 'namespace\s*=\s*"com\.gurbakir\.mobile"' -and
        $appBuild -match 'applicationId\s*=\s*"com\.gurbakir\.mobile\.unconfigured"' -and
        $appBuild -match 'applicationId\s*=\s*"com\.gurbakir\.mobile\.dev"' -and
        $appBuild -match 'applicationId\s*=\s*"com\.gurbakir\.mobile\.staging"' -and
        $appBuild -notmatch 'create\("production"\)'
    Add-Check 'SDK, namespace, and provisional application IDs match ADR-0003' (($sdkMismatch.Count -eq 0) -and $appBaselineCorrect) 'compile/target 36, min 23, neutral namespace, development/staging only'

    $wrapperProperties = Read-RequiredText 'gradle/wrapper/gradle-wrapper.properties'
    $wrapperPropertyCorrect = $null -ne $wrapperProperties -and
        $wrapperProperties -match 'gradle-9\.4\.1-bin\.zip' -and
        $wrapperProperties -match 'distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
    $wrapperJar = Join-Path $repoRoot 'gradle/wrapper/gradle-wrapper.jar'
    $wrapperJarHash = if (Test-Path -LiteralPath $wrapperJar) { (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash } else { '' }
    $wrapperJarCorrect = $wrapperJarHash -eq '55243EF57851F12B070AD14F7F5BB8302DACEEEBC5BCE5ECE5FA6EDB23E1145C'
    Add-Check 'Gradle wrapper distribution and bootstrap jar are pinned' ($wrapperPropertyCorrect -and $wrapperJarCorrect) "Gradle 9.4.1; wrapper jar SHA256 $wrapperJarHash"

    $verificationPath = Join-Path $repoRoot 'gradle/verification-metadata.xml'
    $verificationOk = (Test-Path -LiteralPath $verificationPath -PathType Leaf) -and (Get-Item -LiteralPath $verificationPath).Length -gt 1000
    $lockPaths = @(
        Join-Path $repoRoot 'settings-gradle.lockfile'
        $expectedModules | ForEach-Object { Join-Path $repoRoot "$_/gradle.lockfile" }
    )
    $missingLocks = @($lockPaths | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
    $lockEvidence = if ($missingLocks.Count -eq 0) {
        'verification metadata plus settings and six module locks present'
    } else {
        "missing locks: $($missingLocks -join ', ')"
    }
    Add-Check 'dependency verification metadata and lockfiles exist' ($verificationOk -and $missingLocks.Count -eq 0) $lockEvidence

    $manifest = Read-RequiredText 'app/src/main/AndroidManifest.xml'
    $manifestSafe = $null -ne $manifest -and
        $manifest -match 'usesCleartextTraffic="false"' -and
        $manifest -match 'allowBackup="false"'
    Add-Check 'application manifest denies cleartext and backup' $manifestSafe 'usesCleartextTraffic=false; allowBackup=false'

    $defaults = Read-RequiredText 'config/local.defaults.properties'
    $safeDefaults = $null -ne $defaults -and
        $defaults -match '(?m)^firebase\.enabled=false\s*$' -and
        $defaults -match '(?m)^telemetry\.analyticsEnabled=false\s*$' -and
        $defaults -match '(?m)^telemetry\.crashlyticsEnabled=false\s*$' -and
        $defaults -match '(?m)^shopify\.storefrontPublicToken=\s*$'
    Add-Check 'tracked integration defaults fail closed' $safeDefaults 'Firebase/analytics/crashlytics false; Storefront token empty'

    $firebaseManifestSafe = $null -ne $manifest -and
        $manifest -match 'firebase_analytics_collection_enabled' -and
        $manifest -match 'firebase_crashlytics_collection_enabled' -and
        $manifest -match 'firebase_messaging_auto_init_enabled' -and
        $manifest -match 'firebase_messaging_installation_id_enabled' -and
        $manifest -match 'delivery_metrics_exported_to_big_query_enabled' -and
        $manifest -match 'google_analytics_adid_collection_enabled'
    $firebaseBuildGuarded = $null -ne $appBuild -and
        $appBuild -match 'firebaseClientConfigured' -and
        $appBuild -match 'firebase\.enabled must match'
    Add-Check 'Firebase provisioning and collection fail closed' ($firebaseManifestSafe -and $firebaseBuildGuarded) 'four-file variant guard; current Installation ID enabled; analytics/crash/FCM auto-init/delivery metrics/ad-id false'

    $firebaseModuleBuild = Read-RequiredText 'firebase/build.gradle.kts'
    $firebaseContracts = Read-RequiredText 'firebase/src/main/kotlin/com/gurbakir/firebase/FirebaseContracts.kt'
    $consentSensitiveFirebaseSdkBoundaryCorrect = $null -ne $firebaseModuleBuild -and
        $firebaseModuleBuild -match 'implementation\(libs\.firebase\.messaging\)' -and
        $firebaseModuleBuild -match 'implementation\(libs\.firebase\.config\)' -and
        $firebaseModuleBuild -notmatch 'libs\.firebase\.analytics' -and
        $firebaseModuleBuild -notmatch 'libs\.firebase\.crashlytics' -and
        $null -ne $firebaseContracts -and
        $firebaseContracts -match 'class DisabledAnalyticsReporter'
    Add-Check 'consent-sensitive telemetry SDKs remain outside the runtime graph' $consentSensitiveFirebaseSdkBoundaryCorrect 'Messaging/Remote Config linked; Analytics/Crashlytics absent pending approved consent policy; disabled typed reporter retained'

    $firebaseMessagingService = Read-RequiredText 'app/src/main/kotlin/com/gurbakir/mobile/GurbakirFirebaseMessagingService.kt'
    $firebaseConsentAndPayloadBoundaryCorrect = $null -ne $firebaseContracts -and
        $firebaseContracts -match 'MAINTENANCE_MESSAGE_ENABLED\("maintenance_message_enabled"\)' -and
        $firebaseContracts -match 'CHECKOUT_PRELOAD_ENABLED\("checkout_preload_enabled"\)' -and
        $firebaseContracts -match 'fetchAndActivate' -and
        $firebaseContracts -match 'messaging\.register\(\)' -and
        $firebaseContracts -match 'installations\.id' -and
        $firebaseContracts -match 'messaging\.unregister\(\)' -and
        $firebaseContracts -match 'it\.keys == setOf\(ROUTE_KEY\)' -and
        $firebaseContracts -notmatch '\.getToken\(' -and
        $null -ne $firebaseMessagingService -and
        $firebaseMessagingService -match 'class GurbakirFirebaseMessagingService' -and
        $firebaseMessagingService -match 'override fun onNewToken\(' -and
        $firebaseMessagingService -match 'override fun onRegistered\(' -and
        $firebaseMessagingService -match 'override fun onUnregistered\(' -and
        $firebaseMessagingService -match 'PendingIntent\.FLAG_IMMUTABLE' -and
        $firebaseMessagingService -match 'getString\(R\.string\.firebase_notification_title\)' -and
        $manifest -match '(?s)<service\s+android:name="\.GurbakirFirebaseMessagingService"\s+android:exported="false"'
    Add-Check 'Firebase proof uses explicit consent current Installation ID and fail-closed payload routing' $firebaseConsentAndPayloadBoundaryCorrect 'two boolean flags; manual register/unregister; token/FID callbacks observe nothing sensitive; current Installation ID; exact one-key route; fixed local content; non-exported service'

    $brandConfiguration = Read-RequiredText 'foundation/src/main/kotlin/com/gurbakir/foundation/config/BrandConfiguration.kt'
    $brandSelection = Read-RequiredText 'app/src/main/kotlin/com/gurbakir/mobile/brand/GurbakirBrand.kt'
    $foundationUi = Read-RequiredText 'app/src/main/kotlin/com/gurbakir/mobile/GurbakirApp.kt'
    $turkishStrings = Read-RequiredText 'app/src/main/res/values/strings.xml'
    $englishStrings = Read-RequiredText 'app/src/main/res/values-en/strings.xml'
    $brandBoundaryTyped = $null -ne $brandConfiguration -and
        $brandConfiguration -match 'data class BrandConfiguration' -and
        $brandConfiguration -match 'data class BrandDesignTokens' -and
        $brandConfiguration -match 'data class BrandLegalLinks' -and
        (Read-RequiredText 'foundation/src/main/kotlin/com/gurbakir/foundation/config/ApplicationComposition.kt') -match 'enum class ApplicationCapability' -and
        $null -ne $brandSelection -and
        $brandSelection -match 'defaultLocaleTag = "tr"' -and
        $brandSelection -match 'supportedLocaleTags = setOf\("tr", "en"\)' -and
        $null -ne $foundationUi -and
        $foundationUi -match 'stringResource\(' -and
        $null -ne $turkishStrings -and
        $turkishStrings -match 'Geliştirme' -and
        $null -ne $englishStrings -and
        $englishStrings -match 'Development'
    Add-Check 'brand design and Turkish localization boundaries are typed and resource safe' $brandBoundaryTyped 'app-owned Gürbakır selection; neutral design/content schema; tr baseline plus en resources'

    $storefrontBuild = Read-RequiredText 'storefront/build.gradle.kts'
    $storefrontGateway = Read-RequiredText 'storefront/src/main/kotlin/com/gurbakir/storefront/ApolloStorefrontGateway.kt'
    $storefrontCartPager = Read-RequiredText 'storefront/src/main/kotlin/com/gurbakir/storefront/StorefrontCartPager.kt'
    $cartCoordinator = Read-RequiredText 'storefront/src/main/kotlin/com/gurbakir/storefront/CartCoordinator.kt'
    $cartStore = Read-RequiredText 'storefront/src/main/kotlin/com/gurbakir/storefront/AndroidKeystoreCartSessionStore.kt'
    $ownedReadProof = Read-RequiredText 'storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontReadProofTest.kt'
    $ownedCartProof = Read-RequiredText 'storefront/src/test/kotlin/com/gurbakir/storefront/OwnedStorefrontCartProofTest.kt'
    $storefrontGenerated = $null -ne $storefrontBuild -and
        $storefrontBuild -match 'packageName\.set\("com\.gurbakir\.storefront\.graphql"\)' -and
        $null -ne $storefrontGateway -and
        $storefrontGateway -match 'ShopSummaryQuery' -and
        $storefrontGateway -match 'CatalogPageQuery' -and
        $storefrontGateway -match 'CartCreateMutation' -and
        $null -ne $storefrontCartPager -and
        $storefrontCartPager -match 'CartByIdQuery' -and
        $storefrontCartPager -match 'CartLinesPageQuery' -and
        $storefrontGateway -match 'CartLinesAddMutation' -and
        $storefrontGateway -match 'CartLinesUpdateMutation' -and
        $storefrontGateway -match 'CartLinesRemoveMutation' -and
        $storefrontGateway -match 'CartBuyerIdentityUpdateMutation' -and
        $null -ne $cartCoordinator -and
        $cartCoordinator -match 'class CartCoordinator' -and
        $null -ne $cartStore -and
        $cartStore -match 'AndroidKeyStore' -and
        $null -ne $ownedReadProof -and
        $ownedReadProof -match 'gurbakir\.runOwnedStorefrontProof' -and
        $storefrontBuild -match 'gurbakirRunOwnedStorefrontProof' -and
        $null -ne $ownedCartProof -and
        $ownedCartProof -match 'gurbakir\.runOwnedCartProof' -and
        $storefrontBuild -match 'gurbakirRunOwnedCartProof'
    Add-Check 'Storefront uses generated Apollo cart lifecycle and explicit owned proofs' $storefrontGenerated 'shop/catalog plus cart create/read/add/update/remove/buyer operations; encrypted persistence; opt-in owned proofs'

    $accountBuild = Read-RequiredText 'account/build.gradle.kts'
    $accountConfiguration = Read-RequiredText 'foundation/src/main/kotlin/com/gurbakir/foundation/config/AppConfiguration.kt'
    $authorization = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorization.kt'
    $authorizationBrowser = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountAuthorizationBrowser.kt'
    $accountProofController = Read-RequiredText 'app/src/main/kotlin/com/gurbakir/mobile/CustomerAccountProofController.kt'
    $oauthGuarded = $null -ne $accountBuild -and
        $accountBuild -match 'implementation\(libs\.appauth\)' -and
        $null -ne $accountConfiguration -and
        $accountConfiguration -match 'customer-account-api:full' -and
        $accountConfiguration -match 'isValidShopifyMobileRedirectUri' -and
        $null -ne $authorization -and
        $authorization -match 'setCodeVerifier' -and
        $authorization -match 'setNonce' -and
        $authorization -match 'RejectedRoute' -and
        $null -ne $authorizationBrowser -and
        $authorizationBrowser -match 'getAuthorizationRequestIntent' -and
        $null -ne $accountProofController -and
        $accountProofController -match 'consumeCallback'
    Add-Check 'Customer Account public-mobile OAuth launch and callback foundation is fail closed' $oauthGuarded 'AppAuth system browser; S256/state/nonce; exact callback route; exchange/identity coordinator'

    $accountDiscovery = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountDiscovery.kt'
    $accountTokenClient = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountTokenClient.kt'
    $accountLogoutClient = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/oauth/CustomerAccountLogoutClient.kt'
    $accountSession = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/session/CustomerAccountSessionCoordinator.kt'
    $accountGateway = Read-RequiredText 'account/src/main/kotlin/com/gurbakir/account/CustomerAccountGateway.kt'
    $accountLifecycleComplete = $null -ne $accountDiscovery -and
        $accountDiscovery -match 'followRedirects\(false\)' -and
        $null -ne $accountTokenClient -and
        $accountTokenClient -match 'ShopifyCustomerAccountTokenClient' -and
        $accountTokenClient -match 'override suspend fun exchange' -and
        $accountTokenClient -match 'override suspend fun refresh' -and
        $accountTokenClient -match 'followSslRedirects\(false\)' -and
        $null -ne $accountLogoutClient -and
        $accountLogoutClient -match 'followSslRedirects\(false\)' -and
        $null -ne $accountSession -and
        $accountSession -match 'CustomerAccountSessionCoordinator' -and
        $accountSession -match 'CustomerIdTokenValidator' -and
        $null -ne $accountGateway -and
        $accountGateway -match 'CustomerIdentityQuery' -and
        $accountGateway -match 'addHttpHeader\("Authorization",\s*token\)' -and
        $accountGateway -notmatch 'Authorization.*Bearer'
    Add-Check 'Customer Account discovery token session logout and typed GraphQL boundaries exist' $accountLifecycleComplete 'bounded discovery; exact no-redirect exchange/refresh; fail-closed session; no-redirect logout; Shopify raw-token typed query'

    $commerceController = Read-RequiredText 'app/src/main/kotlin/com/gurbakir/mobile/CommerceProofController.kt'
    $checkoutClient = Read-RequiredText 'checkout/src/main/kotlin/com/gurbakir/checkout/OfficialCheckoutKitClient.kt'
    $checkoutFoundationReady = $null -ne $commerceController -and
        $commerceController -match 'preloadCheckout' -and
        $commerceController -match 'presentCheckout' -and
        $commerceController -match 'checkoutAdapter\.invalidate' -and
        $null -ne $checkoutClient -and
        $checkoutClient -match 'ShopifyCheckoutSheetKit\.preload' -and
        $checkoutClient -match 'ShopifyCheckoutSheetKit\.present' -and
        $checkoutClient -match 'onWebPixelEvent\(event: PixelEvent\) = Unit'
    Add-Check 'Checkout Kit foundation consumes fresh owned cart URLs with consent-safe callbacks' $checkoutFoundationReady 'preload/present/invalidate coordinator; completion/cancel/failure boundary; web-pixel forwarding disabled'

    $ci = Read-RequiredText '.github/workflows/android-foundation.yml'
    $androidTest = Read-RequiredText 'app/src/androidTest/kotlin/com/gurbakir/mobile/FoundationScreenTest.kt'
    $ciAndInstrumentationReady = $null -ne $ci -and
        $ci -match '--no-configuration-cache spotlessCheck detekt lint' -and
        $ci -match ':app:testStagingDebugUnitTest' -and
        $ci -match ':app:assembleStagingRelease' -and
        $ci -match ':app:assembleDevelopmentDebugAndroidTest' -and
        $ci -match ':app:assembleStagingDebugAndroidTest' -and
        $null -ne $androidTest -and
        $androidTest -match 'createComposeRule' -and
        $androidTest -match 'FoundationTestTags'
    Add-Check 'CI and deterministic Compose instrumentation foundations cover both environments' $ciAndInstrumentationReady 'development/staging tests, packages, and Android-test APKs; device execution remains separate'

    $gateStatus = Read-RequiredText 'docs/phase2/GATE-STATUS.md'
    $threatModel = Read-RequiredText 'docs/phase2/THREAT-MODEL.md'
    $environmentClassificationCorrect = $null -ne $gateStatus -and
        $gateStatus -match 'paid Basic plan is not an environment classifier' -and
        $null -ne $threatModel -and
        $threatModel -match 'paid-but-unlaunched.*authorized non-production'
    Add-Check 'paid plan is not misclassified as production' $environmentClassificationCorrect 'owner-designated unlaunched Shopify store is non-production test environment'

    $trackedFiles = @(git ls-files)
    $forbiddenTracked = @($trackedFiles | Where-Object {
        $_ -match '(^|/)(local\.properties|google-services\.json|key\.properties)$' -or
        $_ -match '\.(jks|keystore|p12|pem|key)$'
    })
    $trackedSecretEvidence = if ($forbiddenTracked.Count -eq 0) {
        'no forbidden tracked filenames'
    } else {
        $forbiddenTracked -join ', '
    }
    Add-Check 'no local configuration, Firebase file, or signing material is tracked' ($forbiddenTracked.Count -eq 0) $trackedSecretEvidence

    $sourceRoots = @('app/src', 'foundation/src', 'storefront/src', 'account/src', 'checkout/src', 'firebase/src')
    $sourceFiles = @($sourceRoots | ForEach-Object {
        if (Test-Path -LiteralPath (Join-Path $repoRoot $_)) {
            Get-ChildItem -LiteralPath (Join-Path $repoRoot $_) -Recurse -File -Include *.kt,*.xml
        }
    })
    $restrictedBrandPattern = 'Kristal' + ' Kutu|Bagu' + 'ette'
    $restrictedBrandHits = @($sourceFiles | Select-String -Pattern $restrictedBrandPattern)
    $brandEvidence = if ($restrictedBrandHits.Count -eq 0) {
        'zero source hits'
    } else {
        "$($restrictedBrandHits.Count) source hits"
    }
    Add-Check 'implementation source contains no prohibited reference or legacy branding' ($restrictedBrandHits.Count -eq 0) $brandEvidence

    $csvPath = Join-Path $repoRoot 'docs/phase2/candidate-feature-acceptance-pack.csv'
    $candidateRows = if (Test-Path -LiteralPath $csvPath) { @(Import-Csv -LiteralPath $csvPath) } else { @() }
    $ids = @($candidateRows | ForEach-Object { [int]$_.id })
    $expectedIds = 1..24
    $idsCorrect = $candidateRows.Count -eq 24 -and @(Compare-Object $expectedIds $ids).Count -eq 0
    $recommendationCounts = @{}
    $candidateRows | Group-Object phase2_recommendation | ForEach-Object { $recommendationCounts[$_.Name] = $_.Count }
    $recommendationsCorrect =
        $recommendationCounts['ACCEPT'] -eq 11 -and
        $recommendationCounts['INTENTIONALLY CHANGE'] -eq 7 -and
        $recommendationCounts['NEEDS PRODUCT DECISION'] -eq 6 -and
        -not $recommendationCounts.ContainsKey('REJECT')
    $requiredCsvFields = @('reference_behavior', 'evidence_location', 'approved_requirement_mention', 'phase2_recommendation', 'platform_dependencies', 'data_privacy_security', 'acceptance_criteria', 'failure_empty_offline_lifecycle_states', 'test_approach', 'irreducible_unknowns')
    $emptyRequiredCells = @($candidateRows | ForEach-Object {
        $row = $_
        $requiredCsvFields | Where-Object { [string]::IsNullOrWhiteSpace($row.$_) }
    })
    Add-Check 'candidate acceptance pack has all 24 complete rows' ($idsCorrect -and $recommendationsCorrect -and $emptyRequiredCells.Count -eq 0) 'IDs 1-24; ACCEPT=11; INTENTIONALLY CHANGE=7; NEEDS PRODUCT DECISION=6; no empty required cells'

    $gatePath = Join-Path $repoRoot 'docs/phase2/gate-results.json'
    $gateData = if (Test-Path -LiteralPath $gatePath) { Get-Content -LiteralPath $gatePath -Raw | ConvertFrom-Json } else { $null }
    $allowedStatuses = @('PASS', 'PARTIAL', 'EXTERNALLY BLOCKED', 'NOT STARTED')
    $gates = if ($null -ne $gateData) { @($gateData.gates) } else { @() }
    $gateIds = @($gates | ForEach-Object { [int]$_.id })
    $gateSchemaOk = $null -ne $gateData -and
        $gateData.schemaVersion -eq 'gurbakir.phase2-gates.v1' -and
        @(Compare-Object $allowedStatuses @($gateData.statusVocabulary)).Count -eq 0 -and
        $gates.Count -eq 8 -and
        @(Compare-Object (1..8) $gateIds).Count -eq 0 -and
        @($gates | Where-Object { $_.status -notin $allowedStatuses }).Count -eq 0 -and
        @($gates | Where-Object { $_.status -eq 'EXTERNALLY BLOCKED' -and [string]::IsNullOrWhiteSpace($_.blocker) }).Count -eq 0
    Add-Check 'machine-readable gate results use the honest status vocabulary' $gateSchemaOk 'eight ordered gates; external blockers are non-empty'

    $foundationGatesComplete = $null -ne $gateData -and
        $gateData.overallStatus -eq 'PASS' -and
        @($gates | Where-Object { $_.status -ne 'PASS' }).Count -eq 0
    Add-Check 'all Phase 2 foundation gates are reconciled to PASS' $foundationGatesComplete 'overall PASS and eight PASS gates; full application completion is a separate scope'

    $testModules = @('foundation', 'account', 'checkout', 'storefront', 'firebase', 'app')
    $missingTestReports = [System.Collections.Generic.List[string]]::new()
    $testFailures = 0
    foreach ($module in $testModules) {
        $testRoot = Join-Path $repoRoot "$module/build/test-results"
        $reports = @()
        if (Test-Path -LiteralPath $testRoot) {
            $reports = @(Get-ChildItem -LiteralPath $testRoot -Recurse -File -Filter '*.xml' -ErrorAction SilentlyContinue)
        }
        if ($reports.Count -eq 0) {
            $missingTestReports.Add($module)
        }
        foreach ($report in $reports) {
            [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
            if ($null -ne $xml.testsuite) {
                $testFailures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
            }
        }
    }
    Add-Check 'JVM test reports exist for all six modules with zero failures' ($missingTestReports.Count -eq 0 -and $testFailures -eq 0) "missing modules: $($missingTestReports -join ', '); failures/errors: $testFailures"

    $lintRoot = Join-Path $repoRoot 'app/build/reports'
    $lintReports = @()
    if (Test-Path -LiteralPath $lintRoot) {
        $lintReports = @(Get-ChildItem -LiteralPath $lintRoot -Recurse -File -Filter 'lint-results*.xml' -ErrorAction SilentlyContinue)
    }
    $lintIssues = 0
    foreach ($report in $lintReports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        if ($null -ne $xml.issues) {
            $lintIssues += $xml.SelectNodes('//issue').Count
        }
    }
    Add-Check 'Android Lint XML report exists with zero issues' ($lintReports.Count -gt 0 -and $lintIssues -eq 0) "reports: $($lintReports.Count); issues: $lintIssues"

    $expectedAppOutputDirectories = @(
        'app/build/outputs/apk/development/debug'
        'app/build/outputs/apk/development/release'
        'app/build/outputs/apk/staging/debug'
        'app/build/outputs/apk/staging/release'
    )
    $missingAppOutputs = @($expectedAppOutputDirectories | Where-Object {
        @(Get-ChildItem -LiteralPath (Join-Path $repoRoot $_) -File -Filter '*.apk' -ErrorAction SilentlyContinue).Count -eq 0
    })
    $expectedAndroidTestOutputDirectories = @(
        'app/build/outputs/apk/androidTest/development/debug'
        'app/build/outputs/apk/androidTest/staging/debug'
    )
    $missingAndroidTestOutputs = @($expectedAndroidTestOutputDirectories | Where-Object {
        @(Get-ChildItem -LiteralPath (Join-Path $repoRoot $_) -File -Filter '*.apk' -ErrorAction SilentlyContinue).Count -eq 0
    })
    Add-Check 'development and staging application and Android-test packages exist' (($missingAppOutputs.Count + $missingAndroidTestOutputs.Count) -eq 0) "missing app outputs: $($missingAppOutputs -join ', '); missing Android-test outputs: $($missingAndroidTestOutputs -join ', ')"

    if ($RequireCleanWorktree) {
        $dirty = @(git status --porcelain=v1 --untracked-files=all)
        $worktreeEvidence = if ($dirty.Count -eq 0) { 'clean' } else { "$($dirty.Count) changed paths" }
        Add-Check 'Git worktree is clean' ($dirty.Count -eq 0) $worktreeEvidence
    }

    $failed = @($checks | Where-Object { -not $_.passed })
    $checks | ForEach-Object {
        $label = if ($_.passed) { 'PASS' } else { 'FAIL' }
        Write-Output ("[{0}] {1} - {2}" -f $label, $_.name, $_.evidence)
    }
    Write-Output ("SUMMARY: {0} passed, {1} failed" -f ($checks.Count - $failed.Count), $failed.Count)
    if ($failed.Count -gt 0) {
        exit 1
    }
} finally {
    Pop-Location
}
