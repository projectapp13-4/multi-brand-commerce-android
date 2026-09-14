[CmdletBinding()]
param(
    [ValidateSet('Registry', 'Configuration', 'Enrollment', 'OperatorReadOnly', 'OperatorApply', 'Security', 'All')]
    [string]$Suite = 'All'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$commonModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Common.psm1'
$registryModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1'
Import-Module $registryModule -Force
Import-Module $commonModule -Force

$results = [System.Collections.Generic.List[object]]::new()

function Add-TestResult {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Passed,
        [Parameter(Mandatory)][string]$Evidence
    )

    $results.Add([pscustomobject]@{
        suite = $Suite
        test = $Name
        passed = $Passed
        evidence = $Evidence
    })
}

function Assert-True {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Name
    )

    if (-not $Condition) {
        throw "Assertion failed: $Name"
    }
    Add-TestResult -Name $Name -Passed $true -Evidence 'accepted'
}

function Assert-Throws {
    param(
        [Parameter(Mandatory)][scriptblock]$Action,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Name
    )

    try {
        & $Action
    } catch {
        $message = [string]$_.Exception.Message
        if ($message -notmatch $Pattern) {
            throw "Assertion failed: $Name returned unexpected category: $message"
        }
        Add-TestResult -Name $Name -Passed $true -Evidence 'rejected'
        return
    }
    throw "Assertion failed: $Name did not reject the hostile fixture."
}

function Invoke-RegistrySuite {
    $registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
    $registry = Import-OnboardingRegistry -Path $registryPath -RepositoryRoot $repoRoot
    Assert-True -Condition ($registry.schemaVersion -eq 1) -Name 'current registry schema is accepted'

    $resolved = Get-OnboardingApplicationProfile `
        -Registry $registry `
        -Application 'gurbakir' `
        -Profile 'development'
    Assert-True -Condition ($resolved.Profile.runtimeEnvironment -eq 'DEVELOPMENT') `
        -Name 'profile means registry profile rather than runtime enum'
    Assert-True -Condition ($resolved.Application.identity.brandKey -eq 'gurbakir') `
        -Name 'Gurbakir runtime brand key remains exact'

    $sha = Get-OnboardingSha256 -Path $registryPath
    $lines = Get-OnboardingProjectionLines `
        -Registry $registry `
        -ApplicationRecord $resolved.Application `
        -ProfileRecord $resolved.Profile `
        -RegistrySha256 $sha
    Assert-True -Condition ($lines[0] -eq 'onboarding.schemaVersion=1') `
        -Name 'projection begins with schema version'
    Assert-True -Condition ($lines -contains 'app.brandDisplayName=Gürbakır') `
        -Name 'projection preserves UTF-8 display identity'

    $projectionPath = Join-Path $repoRoot 'config\onboarding\generated\gurbakir\development.properties'
    Test-OnboardingProjection -Path $projectionPath -ExpectedLines $lines
    Add-TestResult -Name 'tracked development projection is byte exact' -Passed $true -Evidence 'matched'

    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('gate8-registry-' + [guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $temporaryRoot)
    $futureDirectory = $null
    try {
        $raw = [System.IO.File]::ReadAllText($registryPath, [System.Text.Encoding]::UTF8)
        $unknownPath = Join-Path $temporaryRoot 'unknown.json'
        [System.IO.File]::WriteAllText(
            $unknownPath,
            $raw.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "unknownField": true,'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unknownPath -RepositoryRoot $repoRoot } `
            -Pattern 'UNKNOWN_FIELD' `
            -Name 'unknown registry field fails'

        $duplicatePath = Join-Path $temporaryRoot 'duplicate.json'
        [System.IO.File]::WriteAllText(
            $duplicatePath,
            $raw.Replace('"schemaVersion": 1,', '"schemaVersion": 1, "SchemaVersion": 1,'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $duplicatePath -RepositoryRoot $repoRoot } `
            -Pattern 'DUPLICATE_FIELD' `
            -Name 'case-colliding JSON field fails before projection'

        $fixturePath = Join-Path $temporaryRoot 'fixture.json'
        [System.IO.File]::WriteAllText(
            $fixturePath,
            $raw.Replace('"fixtureOnly": false,', '"fixtureOnly": true,', 1),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $fixturePath -RepositoryRoot $repoRoot } `
            -Pattern 'FIXTURE_RECORD' `
            -Name 'fixture record is rejected from tracked mode'

        $futureDirectoryName = 'out/onboarding-fixture-' + [guid]::NewGuid().ToString('N')
        $futureDirectory = Join-Path $repoRoot $futureDirectoryName
        [void](New-Item -ItemType Directory -Path $futureDirectory)
        $futureRegistryPath = Join-Path $temporaryRoot 'future-fixture.json'
        $futureRegistry = $raw | ConvertFrom-Json -AsHashtable -Depth 32
        $syntheticApplication = @($futureRegistry.applications | Where-Object { $_.key -ceq 'synthetic' })[0]
        $futureModule = [ordered]@{
            key = 'future-fixture'
            gradleProject = ':future-fixture'
            directory = $futureDirectoryName
            role = 'real-brand-application'
            allowedDirectProjects = @(':mobile-core', ':foundation', ':storefront', ':account', ':checkout')
            ciTasks = [ordered]@{
                unit = @(':future-fixture:testDebugUnitTest')
                assemble = @(
                    ':future-fixture:assembleDebug',
                    ':future-fixture:assembleRelease',
                    ':future-fixture:assembleDebugAndroidTest'
                )
                api30 = @(':future-fixture:ciApi30DebugAndroidTest')
                api23 = @()
            }
        }
        $futureApplication = $syntheticApplication | ConvertTo-Json -Depth 32 | ConvertFrom-Json -AsHashtable -Depth 32
        $futureApplication.key = 'future-fixture'
        $futureApplication.module = ':future-fixture'
        $futureApplication.role = 'real-brand-application'
        $futureApplication.fixtureOnly = $true
        $futureApplication.releaseBoundary = 'test-fixture-only'
        $futureApplication.configurationProjection = 'config/onboarding/generated/future-fixture'
        $futureApplication.identity.displayName = 'Future Fixture'
        $futureApplication.identity.analyticsNamespace = 'future_fixture'
        $futureApplication.identity.brandKey = 'future-fixture'
        $futureApplication.identity.databaseName = 'future-fixture-local.db'
        $futureApplication.identity.webRoles.collectionAppLink.origin = 'https://future.invalid'
        $futureApplication.identity.webRoles.productAppLink.origin = 'https://future.invalid'
        $futureApplication.profiles[0].displayName = 'Future Fixture Development'
        $futureApplication.profiles[0].variants[0].applicationId = 'com.example.futurefixture.debug'
        $futureApplication.profiles[0].variants[1].applicationId = 'com.example.futurefixture'
        $futureApplication.profiles[0].protectedPersistence.cartPreferences = 'future_fixture_secure_cart_development'
        $futureApplication.profiles[0].protectedPersistence.cartKeyAlias = 'future-fixture.cart.development.v1'
        $futureApplication.profiles[0].protectedPersistence.customerPreferences = 'future_fixture_secure_customer_session_development'
        $futureApplication.profiles[0].protectedPersistence.customerKeyAlias = 'future-fixture.customer.session.development.v1'
        $futureRegistry.modules = @(
            @($futureRegistry.modules) + $futureModule |
                Sort-Object { [string]$_.gradleProject } -CaseSensitive
        )
        $futureRegistry.applications = @(
            @($futureRegistry.applications) + $futureApplication |
                Sort-Object { [string]$_.key } -CaseSensitive
        )
        foreach ($lane in @('unit', 'assemble', 'api30', 'api23')) {
            $futureRegistry.ciLanes[$lane] = @($futureRegistry.ciLanes[$lane]) + @($futureModule.ciTasks[$lane])
        }
        [System.IO.File]::WriteAllText(
            $futureRegistryPath,
            (Get-OnboardingCanonicalJson -Value $futureRegistry),
            [System.Text.UTF8Encoding]::new($false)
        )
        $acceptedFixture = Import-OnboardingRegistry `
            -Path $futureRegistryPath `
            -RepositoryRoot $repoRoot `
            -AllowFixtureRecords
        Assert-True -Condition (@($acceptedFixture.applications | Where-Object { $_.key -ceq 'future-fixture' }).Count -eq 1) `
            -Name 'future real-brand fixture is accepted only in explicit fixture mode'
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $futureRegistryPath -RepositoryRoot $repoRoot } `
            -Pattern 'FIXTURE_RECORD_FORBIDDEN' `
            -Name 'valid future fixture cannot enter the tracked registry'

        $unsupportedPath = Join-Path $temporaryRoot 'unsupported.json'
        [System.IO.File]::WriteAllText(
            $unsupportedPath,
            $raw.Replace('"schemaVersion": 1,', '"schemaVersion": 2,'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unsupportedPath -RepositoryRoot $repoRoot } `
            -Pattern 'UNSUPPORTED_SCHEMA_VERSION' `
            -Name 'unsupported registry schema fails'

        $unsupportedStorefrontPath = Join-Path $temporaryRoot 'unsupported-storefront.json'
        [System.IO.File]::WriteAllText(
            $unsupportedStorefrontPath,
            $raw.Replace('"apiVersion": "2026-07"', '"apiVersion": "2099-01"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unsupportedStorefrontPath -RepositoryRoot $repoRoot } `
            -Pattern 'UNSUPPORTED_STOREFRONT_API_VERSION' `
            -Name 'enabled Storefront profile must use an independently allowed API version'

        $wrongHomePath = Join-Path $temporaryRoot 'wrong-home.json'
        [System.IO.File]::WriteAllText(
            $wrongHomePath,
            $raw.Replace('"rootType": "mobile_home"', '"rootType": "arbitrary_home"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $wrongHomePath -RepositoryRoot $repoRoot } `
            -Pattern 'HOME_CONTRACT_MISMATCH' `
            -Name 'enabled Home profile cannot redefine the closed Gate 7 root type'

        $unsafeBoundaryPath = Join-Path $temporaryRoot 'unsafe-boundary.json'
        [System.IO.File]::WriteAllText(
            $unsafeBoundaryPath,
            $raw.Replace('"releaseBoundary": "nonproduction-only"', '"releaseBoundary": "production"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unsafeBoundaryPath -RepositoryRoot $repoRoot } `
            -Pattern 'REAL_APPLICATION_RELEASE_BOUNDARY' `
            -Name 'tracked real applications remain constrained to the nonproduction boundary'

        $unsafePath = Join-Path $temporaryRoot 'unsafe-path.json'
        [System.IO.File]::WriteAllText(
            $unsafePath,
            $raw.Replace('"directory": "account"', '"directory": "../account"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unsafePath -RepositoryRoot $repoRoot } `
            -Pattern 'UNSAFE_PATH' `
            -Name 'repository escape path fails'

        $collisionPath = Join-Path $temporaryRoot 'application-collision.json'
        [System.IO.File]::WriteAllText(
            $collisionPath,
            $raw.Replace('"com.example.gate2synthetic.debug"', '"com.gurbakir.mobile.dev.debug"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $collisionPath -RepositoryRoot $repoRoot } `
            -Pattern 'APPLICATION_ID_COLLISION' `
            -Name 'cross-application ID collision fails'

        $projectionCopy = Join-Path $temporaryRoot 'projection.properties'
        $projectionText = ($lines -join "`n") + "`n"
        [System.IO.File]::WriteAllText(
            $projectionCopy,
            $projectionText.Replace('app.brandKey=gurbakir', 'app.brandKey=wrong'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Test-OnboardingProjection -Path $projectionCopy -ExpectedLines $lines } `
            -Pattern 'STALE_PROJECTION' `
            -Name 'tampered projection fails byte validation'

        $propertyPath = Join-Path $temporaryRoot 'equals.properties'
        [System.IO.File]::WriteAllText(
            $propertyPath,
            "fixture.key=value=with=equals`n",
            [System.Text.UTF8Encoding]::new($false)
        )
        $propertyValue = Read-OnboardingProperties -Path $propertyPath -AllowedKeys @('fixture.key')
        Assert-True -Condition ($propertyValue['fixture.key'] -ceq 'value=with=equals') `
            -Name 'property parser preserves equals in values'

        $bindingPath = Join-Path $temporaryRoot 'provider-binding.json'
        $bindingJson = @'
{
  "schemaVersion": 1,
  "application": "gurbakir",
  "profile": "development",
  "approvedEvidenceRef": "owner-evidence:fixture-001",
  "shopify": {
    "adminShopDomain": "fixture-shop.myshopify.com",
    "shopId": "1234567890"
  },
  "firebase": {
    "projectId": "fixture-project-123",
    "projectNumber": "123456789012",
    "androidAppIdsByVariant": {
      "developmentDebug": "1:123456789012:android:0123456789abcdef",
      "developmentRelease": "1:123456789012:android:fedcba9876543210"
    }
  }
}
'@
        [System.IO.File]::WriteAllText($bindingPath, $bindingJson, [System.Text.UTF8Encoding]::new($false))
        $binding = Import-OnboardingProviderBinding `
            -Path $bindingPath `
            -Application 'gurbakir' `
            -Profile 'development' `
            -ExpectedFirebaseVariants @('developmentDebug', 'developmentRelease')
        Assert-True -Condition ($binding.shopify.shopId -ceq '1234567890') `
            -Name 'closed provider binding is accepted'
        Assert-Throws `
            -Action {
                Import-OnboardingProviderBinding `
                    -Path $bindingPath `
                    -Application 'gurbakir' `
                    -Profile 'staging'
            } `
            -Pattern 'BINDING_TARGET_MISMATCH' `
            -Name 'provider binding cannot cross profiles'

        $secretBindingPath = Join-Path $temporaryRoot 'secret-binding.json'
        [System.IO.File]::WriteAllText(
            $secretBindingPath,
            $bindingJson.Replace('"shopId": "1234567890"', '"shopId": "1234567890", "token": "fixture-forbidden"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action {
                Import-OnboardingProviderBinding `
                    -Path $secretBindingPath `
                    -Application 'gurbakir' `
                    -Profile 'development'
            } `
            -Pattern 'UNKNOWN_FIELD' `
            -Name 'provider binding rejects secret-shaped extra fields'

        $receiptPath = Join-Path $temporaryRoot 'receipt.json'
        $receiptJson = @'
{
  "receiptSchemaVersion": 1,
  "operationContractVersion": "gate8-v1",
  "kind": "PLAN",
  "application": "gurbakir",
  "profile": "development",
  "runtimeEnvironment": "DEVELOPMENT",
  "releaseBoundary": "nonproduction-only",
  "createdAtUtc": "2026-09-14T12:00:00Z",
  "expiresAtUtc": "2026-09-14T12:15:00Z",
  "verifiedTarget": {
    "shopId": "1234567890",
    "adminShopDomain": "fixture-shop.myshopify.com",
    "firebaseProjectId": "fixture-project-123",
    "firebaseProjectNumber": "123456789012"
  },
  "digests": {
    "registrySha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "providerBindingSha256": "1111111111111111111111111111111111111111111111111111111111111111",
    "homeSchemaSha256": "2222222222222222222222222222222222222222222222222222222222222222",
    "operatorSha256": "3333333333333333333333333333333333333333333333333333333333333333"
  },
  "stateFingerprint": "4444444444444444444444444444444444444444444444444444444444444444",
  "actions": [],
  "overallStatus": "PLANNED",
  "diagnosticCodes": [],
  "readback": [],
  "recovery": []
}
'@
        [System.IO.File]::WriteAllText($receiptPath, $receiptJson, [System.Text.UTF8Encoding]::new($false))
        $receipt = Import-OnboardingReceipt -Path $receiptPath
        Assert-True -Condition ($receipt.operationContractVersion -ceq 'gate8-v1') `
            -Name 'closed Plan receipt is accepted'

        $unsafeReceiptPath = Join-Path $temporaryRoot 'unsafe-receipt.json'
        [System.IO.File]::WriteAllText(
            $unsafeReceiptPath,
            $receiptJson.Replace('"application": "gurbakir"', '"application": "gurbakir", "requestUrl": "https://fixture.invalid/graphql"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $unsafeReceiptPath } `
            -Pattern 'UNKNOWN_FIELD' `
            -Name 'receipt rejects request URLs and arbitrary fields'

        $invalidStatusReceiptPath = Join-Path $temporaryRoot 'invalid-status-receipt.json'
        [System.IO.File]::WriteAllText(
            $invalidStatusReceiptPath,
            $receiptJson.Replace('"overallStatus": "PLANNED"', '"overallStatus": "run arbitrary text"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $invalidStatusReceiptPath } `
            -Pattern 'INVALID_ENUM' `
            -Name 'receipt rejects arbitrary overall status text'

        $invalidTargetReceiptPath = Join-Path $temporaryRoot 'invalid-target-receipt.json'
        [System.IO.File]::WriteAllText(
            $invalidTargetReceiptPath,
            $receiptJson.Replace('"adminShopDomain": "fixture-shop.myshopify.com"', '"adminShopDomain": "attacker.invalid"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $invalidTargetReceiptPath } `
            -Pattern 'INVALID_SHOPIFY_BINDING|UNSAFE_HOST' `
            -Name 'receipt rejects an unbound Admin target shape'

        $actionReceiptPath = Join-Path $temporaryRoot 'invalid-action-receipt.json'
        [System.IO.File]::WriteAllText(
            $actionReceiptPath,
            $receiptJson.Replace(
                '"actions": []',
                '"actions": [{"ordinal":1,"resourceKind":"SHOPIFY_HOME_DEFINITION","resourceKey":"arbitrary_type","managementMode":"CREATE_IF_MISSING","beforeClassification":"ABSENT","intendedAction":"CREATE","beforeFingerprint":"not-a-digest","providerResourceId":null,"status":"PLANNED","afterClassification":"UNKNOWN","afterFingerprint":"0000000000000000000000000000000000000000000000000000000000000000"}]'
            ),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $actionReceiptPath } `
            -Pattern 'INVALID_(DIGEST|RESOURCE_KEY)' `
            -Name 'receipt rejects untyped resource keys and invalid fingerprints'
    } finally {
        if ($null -ne $futureDirectory -and (Test-Path -LiteralPath $futureDirectory)) {
            Remove-Item -LiteralPath $futureDirectory -Recurse -Force
        }
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

function Invoke-ConfigurationSuite {
    $appBuild = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'app\build.gradle.kts'),
        [System.Text.Encoding]::UTF8
    )
    $storefrontBuild = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'storefront\build.gradle.kts'),
        [System.Text.Encoding]::UTF8
    )
    $manifest = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'app\src\main\AndroidManifest.xml'),
        [System.Text.Encoding]::UTF8
    )

    Assert-True -Condition (-not $appBuild.Contains('config/local.defaults.properties')) `
        -Name 'app build no longer reads root defaults'
    Assert-True -Condition (-not $appBuild.Contains('config/local.properties')) `
        -Name 'app build no longer reads root local configuration'
    Assert-True -Condition ($appBuild.Contains('config/onboarding/generated/gurbakir')) `
        -Name 'app build reads deterministic profile projections'
    Assert-True -Condition ($appBuild.Contains('config/local/gurbakir')) `
        -Name 'app build scopes controlled client values by application and profile'
    Assert-True -Condition (-not $storefrontBuild.Contains('config/local.properties')) `
        -Name 'Storefront proofs do not read root local configuration'
    Assert-True -Condition ($manifest.Contains('${collectionAppLinkHost}')) `
        -Name 'manifest consumes role-specific generated link placeholders'
    Assert-True -Condition (Test-Path -LiteralPath (Join-Path $repoRoot 'scripts\Migrate-GurbakirLocalConfiguration.ps1')) `
        -Name 'explicit one-profile migration helper exists'
    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('gate8-migration-' + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $legacyPath = Join-Path $temporaryRoot 'legacy.properties'
    $migrationName = 'migration-' + [guid]::NewGuid().ToString('N') + '.properties'
    $migrationDestination = Join-Path $repoRoot ('config\local\gurbakir\' + $migrationName)
    $legacyFixture = @'
shopify.storefrontDomain=gurbakir.com
shopify.storefrontApiVersion=2026-07
shopify.storefrontPublicToken=fixture-public-token
shopify.catalogMenuHandle=main-menu
shopify.homeContentRootHandle=primary
shopify.customerAccountClientId=fixture-client-id
shopify.customerAccountIssuer=https://shopify.com/authentication/1234567890
shopify.customerAccountAuthorizationEndpoint=https://accounts.shopify.com/authentication/oauth/authorize
shopify.customerAccountTokenEndpoint=https://accounts.shopify.com/authentication/oauth/token
shopify.customerAccountLogoutEndpoint=https://accounts.shopify.com/authentication/logout
shopify.customerAccountGraphqlEndpoint=https://accounts.shopify.com/customer/api/2026-07/graphql
shopify.customerAccountRedirectUri=shop.1234567890.gurbakir://oauth/callback
shopify.customerAccountScopes=openid email customer-account-api:full
'@
    try {
        [System.IO.File]::WriteAllText($legacyPath, $legacyFixture.Replace("`r", ''), [System.Text.UTF8Encoding]::new($false))
        Assert-Throws `
            -Action { & (Join-Path $repoRoot 'scripts\Migrate-GurbakirLocalConfiguration.ps1') -Profile development -LegacyPath $legacyPath -DestinationPath $migrationDestination } `
            -Pattern 'UNSAFE_CONFIGURATION_PATH' `
            -Name 'legacy migration refuses any destination other than the selected registry path'
    } finally {
        if (Test-Path -LiteralPath $migrationDestination) { Remove-Item -LiteralPath $migrationDestination -Force }
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
    Assert-True -Condition (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'config\local.defaults.properties'))) `
        -Name 'superseded root defaults are removed'
    Assert-True -Condition (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'config\local.properties.example'))) `
        -Name 'superseded root example is removed'
}

function Invoke-EnrollmentSuite {
    foreach ($lane in @('unit', 'assemble', 'api30', 'api23')) {
        & (Join-Path $repoRoot 'scripts\Get-RegisteredGradleTasks.ps1') -Lane $lane -ValidateOnly
        Assert-True -Condition $true -Name "registry and workflow cover $lane lane"
    }
    $registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('gate8-enrollment-' + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    try {
        $badPath = Join-Path $temporaryRoot 'bad-registry.json'
        $json = [System.IO.File]::ReadAllText($registryPath, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText(
            $badPath,
            $json.Replace('":mobile-core:ciApi23DebugAndroidTest"', '":mobile-core:missingApi23"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $badPath -RepositoryRoot $repoRoot } `
            -Pattern 'CI_LANE_UNION_MISMATCH|MISSING_REQUIRED_CI_TASK' `
            -Name 'missing required API 23 coverage fails'

        $selfConsistentMissingAppCoverage = Join-Path $temporaryRoot 'missing-app-coverage.json'
        [System.IO.File]::WriteAllText(
            $selfConsistentMissingAppCoverage,
            $json.Replace(':app:assembleStagingDebugAndroidTest', ':app:assembleImaginaryDebugAndroidTest'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $selfConsistentMissingAppCoverage -RepositoryRoot $repoRoot } `
            -Pattern 'MISSING_APPLICATION_CI_COVERAGE' `
            -Name 'self-consistent registry lanes cannot omit an enrolled application variant task'
    } finally {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

function Invoke-OperatorReadOnlySuite {
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Shopify.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.CustomerAccount.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.AppLinks.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Firebase.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Common.psm1') -Force
    $safeRequest = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture-shop.myshopify.com/admin/api/2026-07/graphql.json') -Transport {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{} }
    }
    Assert-True -Condition ($safeRequest.StatusCode -eq 200) -Name 'fixed HTTPS provider request accepts an empty user-info component'
    Assert-Throws `
        -Action { Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://user@fixture-shop.myshopify.com/admin/api/2026-07/graphql.json') -Transport { [pscustomobject]@{ StatusCode = 200; Data = @{} } } } `
        -Pattern 'UNSAFE_PROVIDER_URI' `
        -Name 'provider URI validation runs before injected transport and rejects user-info'
    Assert-Throws `
        -Action { Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -MaximumBytes 8 -Transport { [pscustomobject]@{StatusCode=200;Data=@{value='oversized'}} } } `
        -Pattern 'PROVIDER_RESPONSE_TOO_LARGE' `
        -Name 'response-size limits apply to injected security fixtures'
    Assert-Throws `
        -Action { Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport { [pscustomobject]@{StatusCode=200;RawBytes=[byte[]](0xff);Headers=@{}} } } `
        -Pattern 'PROVIDER_RESPONSE_INVALID_JSON' `
        -Name 'strict UTF-8 decoding applies to raw injected provider fixtures'
    $registry = Import-OnboardingRegistry -Path (Join-Path $repoRoot 'config\onboarding\application-registry.v1.json') -RepositoryRoot $repoRoot
    $selected = Get-OnboardingApplicationProfile -Registry $registry -Application 'gurbakir' -Profile 'development'
    $binding = @{ shopify = @{ adminShopDomain = 'fixture-shop.myshopify.com'; shopId = '1234567890' } }
    $script:adminBodies = [Collections.Generic.List[string]]::new()
    $adminTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:adminBodies.Add([string]$body)
        if ($body -match 'Gate8VerifyShop') {
            return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ shop = @{ id = 'gid://shopify/Shop/1234567890'; myshopifyDomain = 'fixture-shop.myshopify.com' } } } }
        }
        if ($body -match 'Gate8HomeDefinitions') {
            return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectDefinitions = @{ nodes = @(); pageInfo = @{ hasNextPage = $false; endCursor = $null } } } } }
        }
        return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menu = $null } } }
    }
    $homeState = Get-ShopifyHomeDefinitionState $binding 'fixture-admin-token' $adminTransport
    Assert-True -Condition ($homeState.Classification -ceq 'ABSENT') -Name 'absent Home definitions classify without mutation'
    Assert-True -Condition ($script:adminBodies[0] -match '"first":100') -Name 'Admin definition inspection uses fixed page size 100'
    $wrongShopTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ shop = @{ id = 'gid://shopify/Shop/9999999999'; myshopifyDomain = 'fixture-shop.myshopify.com' } } } }
    }
    Assert-Throws `
        -Action { Get-ShopifyVerifiedTargetState $binding 'fixture-admin-token' $wrongShopTransport } `
        -Pattern 'SHOPIFY_TARGET_IDENTITY_MISMATCH' `
        -Name 'Admin token shop identity must match the independently approved binding'

    $incompatibleDefinitions = @(
        @{
            id = 'gid://shopify/MetaobjectDefinition/1'
            type = 'mobile_home_collection_grid'
            name = 'Collection grid'
            displayNameKey = 'title'
            fieldDefinitions = @(
                @{ key = 'title'; name = 'Title'; type = @{ name = 'multi_line_text_field' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '80' }) },
                @{ key = 'collections'; name = 'Collections'; type = @{ name = 'list.collection_reference' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '6' }) }
            )
            capabilities = @{ publishable = @{ enabled = $true } }
            access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        },
        @{
            id = 'gid://shopify/MetaobjectDefinition/2'
            type = 'mobile_home_featured_product'
            name = 'Featured product'
            displayNameKey = 'title'
            fieldDefinitions = @(
                @{ key = 'title'; name = 'Title'; type = @{ name = 'single_line_text_field' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '80' }) },
                @{ key = 'product'; name = 'Product'; type = @{ name = 'product_reference' }; required = $true; validations = @() }
            )
            capabilities = @{ publishable = @{ enabled = $true } }
            access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        },
        @{
            id = 'gid://shopify/MetaobjectDefinition/3'
            type = 'mobile_home'
            name = 'Mobile home'
            displayNameKey = 'schema_version'
            fieldDefinitions = @(
                @{ key = 'schema_version'; name = 'Schema version'; type = @{ name = 'number_integer' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '1' }) },
                @{ key = 'declared_section_count'; name = 'Declared section count'; type = @{ name = 'number_integer' }; required = $true; validations = @(@{ name = 'min'; value = '0' }, @{ name = 'max'; value = '2' }) },
                @{ key = 'sections'; name = 'Sections'; type = @{ name = 'list.mixed_reference' }; required = $false; validations = @(@{ name = 'max'; value = '2' }, @{ name = 'metaobject_definition_id'; value = 'gid://shopify/MetaobjectDefinition/1' }, @{ name = 'metaobject_definition_id'; value = 'gid://shopify/MetaobjectDefinition/2' }) }
            )
            capabilities = @{ publishable = @{ enabled = $true } }
            access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        }
    )
    $incompatibleTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectDefinitions = @{ nodes = $incompatibleDefinitions; pageInfo = @{ hasNextPage = $false; endCursor = $null } } } } }
    }.GetNewClosure()
    $incompatibleHome = Get-ShopifyHomeDefinitionState $binding 'fixture-admin-token' $incompatibleTransport
    Assert-True -Condition ($incompatibleHome.Classification -ceq 'INCOMPATIBLE') `
        -Name 'wrong existing Home field type blocks create-if-missing provisioning'
    $menu = Get-ShopifyMenuState $binding 'fixture-admin-token' 'main-menu' $adminTransport
    Assert-True -Condition ($menu.Classification -ceq 'ABSENT') -Name 'missing Menu remains validate-only absent state'
    $discoveryTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $data = if ($uri.AbsolutePath -eq '/.well-known/openid-configuration') {
            @{ issuer='https://shopify.com/authentication/1234567890'; authorization_endpoint='https://shopify.com/auth'; token_endpoint='https://shopify.com/token'; end_session_endpoint='https://shopify.com/logout'; jwks_uri='https://shopify.com/jwks'; code_challenge_methods_supported=@('S256'); grant_types_supported=@('authorization_code'); id_token_signing_alg_values_supported=@('RS256') }
        } else { @{ graphql_api='https://shopify.com/customer-account/api/2026-07/graphql' } }
        [pscustomobject]@{ StatusCode=200; Data=$data }
    }
    $customer = Get-OnboardingCustomerDiscovery $selected $binding $discoveryTransport
    Assert-True -Condition ($customer.Callback -ceq 'shop.1234567890.gurbakir://oauth/callback') -Name 'Customer callback derives from pinned shop and app identity'
    $unsafeDiscoveryTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $data = if ($uri.AbsolutePath -eq '/.well-known/openid-configuration') {
            @{ issuer='https://shopify.com/authentication/1234567890'; authorization_endpoint='http://shopify.com/auth'; token_endpoint='https://shopify.com/token'; end_session_endpoint='https://shopify.com/logout'; jwks_uri='https://shopify.com/jwks'; code_challenge_methods_supported=@('S256'); grant_types_supported=@('authorization_code'); id_token_signing_alg_values_supported=@('RS256') }
        } else { @{ graphql_api='https://shopify.com/customer-account/api/2026-07/graphql' } }
        [pscustomobject]@{ StatusCode=200; Data=$data }
    }
    Assert-Throws `
        -Action { Get-OnboardingCustomerDiscovery $selected $binding $unsafeDiscoveryTransport } `
        -Pattern 'CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH' `
        -Name 'Customer discovery rejects an insecure endpoint before configuration evidence'
    $foreignDiscoveryTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $data = if ($uri.AbsolutePath -eq '/.well-known/openid-configuration') {
            @{ issuer='https://shopify.com/authentication/1234567890'; authorization_endpoint='https://attacker.example/auth'; token_endpoint='https://shopify.com/token'; end_session_endpoint='https://shopify.com/logout'; jwks_uri='https://shopify.com/jwks'; code_challenge_methods_supported=@('S256'); grant_types_supported=@('authorization_code'); id_token_signing_alg_values_supported=@('RS256') }
        } else { @{ graphql_api='https://shopify.com/customer-account/api/2026-07/graphql' } }
        [pscustomobject]@{ StatusCode=200; Data=$data }
    }
    Assert-Throws `
        -Action { Get-OnboardingCustomerDiscovery $selected $binding $foreignDiscoveryTransport } `
        -Pattern 'CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH' `
        -Name 'Customer discovery rejects a foreign HTTPS endpoint'
    $firebaseBinding = @{
        firebase = @{
            projectId = 'fixture-project-123'
            projectNumber = '123456789012'
            androidAppIdsByVariant = @{
                developmentDebug = '1:123456789012:android:0123456789abcdef'
                developmentRelease = '1:123456789012:android:fedcba9876543210'
            }
        }
    }
    $script:firebasePageRequests = [Collections.Generic.List[string]]::new()
    $firebaseTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:firebasePageRequests.Add([string]$uri.Query)
        if ([string]$uri.Query -match 'pageToken=next-page') {
            return [pscustomobject]@{ StatusCode = 200; Data = @{
                apps = @(@{ appId = '1:123456789012:android:fedcba9876543210'; packageName = 'com.gurbakir.mobile.dev' })
            } }
        }
        return [pscustomobject]@{ StatusCode = 200; Data = @{
            apps = @(@{ appId = '1:123456789012:android:0123456789abcdef'; packageName = 'com.gurbakir.mobile.dev.debug' })
            nextPageToken = 'next-page'
        } }
    }
    $firebase = Get-OnboardingFirebaseState $selected $firebaseBinding 'fixture-firebase-token' $firebaseTransport
    Assert-True `
        -Condition ($firebase.Classification -ceq 'PASS' -and $script:firebasePageRequests.Count -eq 2 -and $script:firebasePageRequests[0] -match 'pageSize=100') `
        -Name 'Firebase inspection uses fixed 100-record pagination and compares all pages'
    $wrongAssetLinksTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @(@{
            relation = @('delegate_permission/common.handle_all_urls')
            target = @{ namespace = 'android_app'; package_name = 'com.attacker.app'; sha256_cert_fingerprints = @('AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA') }
        }) }
    }
    Assert-True `
        -Condition ((Get-OnboardingAssetLinksState $selected $wrongAssetLinksTransport).Classification -ceq 'FAIL') `
        -Name 'public association for a foreign Android package is not reported as verified'
    $matchingAssetLinksTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @(@{
            relation = @('delegate_permission/common.handle_all_urls')
            target = @{ namespace = 'android_app'; package_name = 'com.gurbakir.mobile.dev.debug'; sha256_cert_fingerprints = @('AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA') }
        }) }
    }
    Assert-True `
        -Condition ((Get-OnboardingAssetLinksState $selected $matchingAssetLinksTransport).Classification -ceq 'PASS') `
        -Name 'public association requires an enrolled Android package and valid fingerprint shape'
    Assert-True -Condition ((Protect-OnboardingOutput 'failure fixture-secret' @('fixture-secret')) -ceq 'failure <redacted>') -Name 'loaded values are redacted before output'
}

function Invoke-OperatorApplySuite {
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Operator.psm1') -Force
    $temporaryRoot = Join-Path $repoRoot ('out\onboarding\gate8-apply-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $bindingPath = Join-Path $repoRoot 'config\local\gurbakir\development.providers.json'
    $stagingBindingPath = Join-Path $repoRoot 'config\local\gurbakir\staging.providers.json'
    [IO.Directory]::CreateDirectory((Split-Path -Parent $bindingPath)) | Out-Null
    $bindingJson = '{"schemaVersion":1,"application":"gurbakir","profile":"development","approvedEvidenceRef":"owner-evidence:fixture-apply","shopify":{"adminShopDomain":"fixture-shop.myshopify.com","shopId":"1234567890"},"firebase":{"projectId":"fixture-project-123","projectNumber":"123456789012","androidAppIdsByVariant":{"developmentDebug":"1:123456789012:android:0123456789abcdef","developmentRelease":"1:123456789012:android:fedcba9876543210"}}}'
    [IO.File]::WriteAllText($bindingPath, $bindingJson, [Text.UTF8Encoding]::new($false))
    $credentialName = 'MB_GURBAKIR_DEVELOPMENT_SHOPIFY_ADMIN_TOKEN'
    $stagingCredentialName = 'MB_GURBAKIR_STAGING_SHOPIFY_ADMIN_TOKEN'
    $previous = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
    $previousStaging = [Environment]::GetEnvironmentVariable($stagingCredentialName, 'Process')
    [Environment]::SetEnvironmentVariable($credentialName, 'fixture-admin-token', 'Process')
    $script:createdDefinitions = [Collections.Generic.List[object]]::new(); $script:probe = $null; $script:writeCount = 0; $script:lockObserved = $false
    $transport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $request = $body | ConvertFrom-Json -AsHashtable
        if ($request.query -match 'Gate8VerifyShop') {
            return [pscustomobject]@{ StatusCode=200; Data=@{ data=@{ shop=@{ id='gid://shopify/Shop/1234567890'; myshopifyDomain='fixture-shop.myshopify.com' } } } }
        }
        if ($request.query -match 'Gate8HomeDefinitions') {
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitions=@{nodes=@($script:createdDefinitions);pageInfo=@{hasNextPage=$false;endCursor=$null}}}}}
        }
        if ($request.query -match 'Gate8Menu') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{menu=$null}}} }
        if ($request.query -match 'query Gate8Probe') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectByHandle=$script:probe}}} }
        if ($request.query -match 'Gate8DefinitionCreate') {
            $script:lockObserved = Test-Path -LiteralPath (Join-Path $repoRoot 'out\onboarding\locks\shop-1234567890.lock')
            $script:writeCount++;$definition=$request.variables.definition;$id="gid://shopify/MetaobjectDefinition/$($script:writeCount)"
            $fields=@(
                foreach($field in @($definition.fieldDefinitions)){
                    @{key=[string]$field.key;name=[string]$field.name;type=@{name=[string]$field.type};required=[bool]$field.required;validations=@($field.validations)}
                }
            )
            if ($definition.access.Contains('admin')) { throw 'fixture detected forbidden explicit Admin access input' }
            $node=@{id=$id;type=[string]$definition.type;name=[string]$definition.name;displayNameKey=[string]$definition.displayNameKey;fieldDefinitions=$fields;capabilities=@{publishable=@{enabled=$true}};access=@{admin='PUBLIC_READ_WRITE';storefront='PUBLIC_READ'}};$script:createdDefinitions.Add($node)
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitionCreate=@{metaobjectDefinition=@{id=$id;type=[string]$definition.type};userErrors=@()}}}}
        }
        if ($request.query -match 'Gate8ProbeCreate') {$script:writeCount++;$script:probe=@{id='gid://shopify/Metaobject/99';type='mobile_home';handle='gate8-operator-acceptance-v1';fields=@(@{key='schema_version';value='1'},@{key='declared_section_count';value='0'});capabilities=@{publishable=@{status='DRAFT'}}};return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectCreate=@{metaobject=$script:probe;userErrors=@()}}}}}
        throw 'unexpected fixture operation'
    }
    try {
        $outsideReceipt = Join-Path ([IO.Path]::GetTempPath()) ('gate8-outside-' + [guid]::NewGuid().ToString('N') + '.json')
        Assert-Throws `
            -Action { New-OnboardingPlan $repoRoot 'gurbakir' 'development' $outsideReceipt -Transport $transport } `
            -Pattern 'UNSAFE_EVIDENCE_PATH' `
            -Name 'operator Plan receipts cannot be written outside ignored onboarding evidence'

        $clientEnvironment = [ordered]@{
            STOREFRONT_PUBLIC_TOKEN = 'invalid token with whitespace'
            CUSTOMER_ACCOUNT_CLIENT_ID = 'fixture-client-id'
            CUSTOMER_ACCOUNT_ISSUER = 'https://shopify.com/authentication/1234567890'
            CUSTOMER_ACCOUNT_AUTHORIZATION_ENDPOINT = 'https://shopify.com/auth'
            CUSTOMER_ACCOUNT_TOKEN_ENDPOINT = 'https://shopify.com/token'
            CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT = 'https://shopify.com/logout'
            CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT = 'https://shopify.com/customer-account/api/2026-07/graphql'
            CUSTOMER_ACCOUNT_REDIRECT_URI = 'shop.1234567890.gurbakir://oauth/callback'
        }
        $previousClientEnvironment = @{}
        foreach ($entry in $clientEnvironment.GetEnumerator()) {
            $name = Get-OnboardingCredentialName 'gurbakir' 'development' ([string]$entry.Key)
            $previousClientEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, [string]$entry.Value, 'Process')
        }
        try {
            Assert-Throws `
                -Action { Write-OnboardingLocalConfiguration $repoRoot 'gurbakir' 'development' -ConfirmApply } `
                -Pattern 'UNSAFE_CLIENT_CONFIGURATION' `
                -Name 'local configuration rejects whitespace-bearing public client tokens before writing'
            [Environment]::SetEnvironmentVariable(
                (Get-OnboardingCredentialName 'gurbakir' 'development' 'STOREFRONT_PUBLIC_TOKEN'),
                'fixture-public-token',
                'Process'
            )
            [Environment]::SetEnvironmentVariable(
                (Get-OnboardingCredentialName 'gurbakir' 'development' 'CUSTOMER_ACCOUNT_TOKEN_ENDPOINT'),
                'https://attacker.example/token',
                'Process'
            )
            Assert-Throws `
                -Action { Write-OnboardingLocalConfiguration $repoRoot 'gurbakir' 'development' -ConfirmApply } `
                -Pattern 'UNSAFE_CLIENT_CONFIGURATION' `
                -Name 'local configuration rejects foreign Customer Account endpoints before writing'
        } finally {
            foreach ($entry in $previousClientEnvironment.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable([string]$entry.Key, $entry.Value, 'Process')
            }
        }

        $partialDefinition = @{
            id = 'gid://shopify/MetaobjectDefinition/41'
            type = 'mobile_home_collection_grid'
            name = 'Collection grid'
            displayNameKey = 'title'
            fieldDefinitions = @(
                @{ key = 'title'; name = 'Title'; type = @{ name = 'single_line_text_field' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '80' }) },
                @{ key = 'collections'; name = 'Collections'; type = @{ name = 'list.collection_reference' }; required = $true; validations = @(@{ name = 'min'; value = '1' }, @{ name = 'max'; value = '6' }) }
            )
            capabilities = @{ publishable = @{ enabled = $true } }
            access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        }
        $partialTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $request = $body | ConvertFrom-Json -AsHashtable
            if ($request.query -match 'Gate8VerifyShop') {
                return [pscustomobject]@{StatusCode=200;Data=@{data=@{shop=@{id='gid://shopify/Shop/1234567890';myshopifyDomain='fixture-shop.myshopify.com'}}}}
            }
            if ($request.query -match 'Gate8HomeDefinitions') {
                return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitions=@{nodes=@($partialDefinition);pageInfo=@{hasNextPage=$false;endCursor=$null}}}}}
            }
            if ($request.query -match 'query Gate8Probe') {
                return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectByHandle=$null}}}
            }
            throw 'unexpected partial fixture operation'
        }.GetNewClosure()
        $partialPlanPath = Join-Path $temporaryRoot 'partial-plan.json'
        $partialPlan = New-OnboardingPlan $repoRoot 'gurbakir' 'development' $partialPlanPath -Transport $partialTransport
        Assert-True -Condition ((@($partialPlan.actions | ForEach-Object { $_.resourceKey }) -join ',') -ceq 'mobile_home_featured_product,mobile_home') `
            -Name 'compatible partial Home state plans only missing definitions in dependency order'

        $planPath=Join-Path $temporaryRoot 'plan.json';$resultPath=Join-Path $temporaryRoot 'result.json'
        [void](New-OnboardingPlan $repoRoot 'gurbakir' 'development' $planPath -IncludeAcceptanceProbe -Transport $transport)
        Assert-Throws -Action { Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $planPath 'gurbakir' 'development' -IncludeAcceptanceProbe -Transport $transport } -Pattern 'CONFIRMATION_MISMATCH' -Name 'Apply requires explicit confirmation'
        Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Registry.psm1') -Force
        $tamperedPlanPath = Join-Path $temporaryRoot 'tampered-plan.json'
        $tamperedPlan = Import-OnboardingReceipt $planPath
        $tamperedPlan.actions = @($tamperedPlan.actions | Select-Object -Skip 1)
        Write-OnboardingReceipt $tamperedPlanPath $tamperedPlan
        Assert-Throws `
            -Action { Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $tamperedPlanPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -Transport $transport } `
            -Pattern 'PLAN_ACTION_DRIFT' `
            -Name 'Apply recomputes and rejects an omitted typed action'

        $wrongBoundaryPath = Join-Path $temporaryRoot 'wrong-boundary-plan.json'
        $wrongBoundary = Import-OnboardingReceipt $planPath
        $wrongBoundary.releaseBoundary = 'never-production'
        Write-OnboardingReceipt $wrongBoundaryPath $wrongBoundary
        Assert-Throws `
            -Action { Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $wrongBoundaryPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -Transport $transport } `
            -Pattern 'UNSAFE_RELEASE_BOUNDARY' `
            -Name 'Apply independently rechecks the current nonproduction release boundary'

        $invalidReceiptPath = Join-Path $temporaryRoot 'invalid-write.json'
        $invalidReceipt = Import-OnboardingReceipt $planPath
        $invalidReceipt.actions[0].providerResourceId = 'provider-controlled-invalid-id'
        Assert-Throws `
            -Action { Write-OnboardingReceipt $invalidReceiptPath $invalidReceipt } `
            -Pattern 'INVALID_PROVIDER_RESOURCE_ID' `
            -Name 'receipt validation occurs before the final evidence path is replaced'
        Assert-True -Condition (-not (Test-Path -LiteralPath $invalidReceiptPath)) `
            -Name 'invalid provider content never persists as a final receipt'

        $ambiguousPlanPath = Join-Path $temporaryRoot 'ambiguous-plan.json'
        [void](New-OnboardingPlan $repoRoot 'gurbakir' 'development' $ambiguousPlanPath -Transport $transport)
        $ambiguousTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $request = $body | ConvertFrom-Json -AsHashtable
            if ($request.query -match 'Gate8VerifyShop') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{shop=@{id='gid://shopify/Shop/1234567890';myshopifyDomain='fixture-shop.myshopify.com'}}}} }
            if ($request.query -match 'Gate8HomeDefinitions') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitions=@{nodes=@();pageInfo=@{hasNextPage=$false;endCursor=$null}}}}} }
            if ($request.query -match 'query Gate8Probe') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectByHandle=$null}}} }
            if ($request.query -match 'Gate8DefinitionCreate') { throw 'fixture ambiguous transport failure' }
            throw 'unexpected ambiguous fixture operation'
        }
        Assert-Throws `
            -Action { Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $ambiguousPlanPath 'gurbakir' 'development' -ConfirmApply -Transport $ambiguousTransport } `
            -Pattern 'PARTIAL_APPLY' `
            -Name 'ambiguous provider mutation emits a bounded recovery classification'
        $recoveryReceipt = Import-OnboardingReceipt "$ambiguousPlanPath.recovery-1.json"
        Assert-True `
            -Condition ([string]$recoveryReceipt.kind -ceq 'RECOVERY' -and [string]$recoveryReceipt.overallStatus -ceq 'PARTIAL') `
            -Name 'ambiguous provider mutation persists a closed recovery receipt'

        [void](Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $planPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -OutputPath $resultPath -Transport $transport)
        Assert-True -Condition ($script:writeCount -eq 4) -Name 'Apply creates only three definitions and one DRAFT probe'
        Assert-True -Condition $script:lockObserved -Name 'Apply holds a verified-shop local operation lock while mutating'
        Assert-True -Condition ((Import-OnboardingReceipt $resultPath).kind -ceq 'RESULT') -Name 'Apply writes a closed redacted result receipt'
        $secondPlanPath = Join-Path $temporaryRoot 'second-plan.json'
        $secondResultPath = Join-Path $temporaryRoot 'second-result.json'
        Assert-Throws `
            -Action { New-OnboardingPlan $repoRoot 'gurbakir' 'development' $secondPlanPath -IncludeAcceptanceProbe -Transport $transport } `
            -Pattern 'PROBE_COLLISION' `
            -Name 'matching DRAFT probe is not adopted without receipt-bound attribution'
        $secondPlan = New-OnboardingPlan $repoRoot 'gurbakir' 'development' $secondPlanPath -IncludeAcceptanceProbe -PriorReceipt $resultPath -Transport $transport
        $secondActions = @($secondPlan.actions)
        Assert-True `
            -Condition ($secondActions.Count -eq 1 -and [string]$secondActions[0].intendedAction -ceq 'NONE') `
            -Name 'receipt-bound matching DRAFT probe plans an attributed no-op'
        [void](Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $secondPlanPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -PriorReceipt $resultPath -OutputPath $secondResultPath -Transport $transport)
        Assert-True -Condition ($script:writeCount -eq 4) -Name 'second attributed Apply performs zero writes'

        $stagingBindingJson = $bindingJson.Replace('"profile":"development"','"profile":"staging"').Replace('"developmentDebug"','"stagingDebug"').Replace('"developmentRelease"','"stagingRelease"')
        [IO.File]::WriteAllText($stagingBindingPath, $stagingBindingJson, [Text.UTF8Encoding]::new($false))
        [Environment]::SetEnvironmentVariable($stagingCredentialName, 'fixture-admin-token', 'Process')
        $stagingPlanPath = Join-Path $temporaryRoot 'staging-plan.json'
        $stagingResultPath = Join-Path $temporaryRoot 'staging-result.json'
        $stagingPlan = New-OnboardingPlan $repoRoot 'gurbakir' 'staging' $stagingPlanPath -IncludeAcceptanceProbe -PriorReceipt $resultPath -Transport $transport
        $stagingActions = @($stagingPlan.actions)
        Assert-True `
            -Condition ($stagingActions.Count -eq 1 -and [string]$stagingActions[0].intendedAction -ceq 'NONE') `
            -Name 'same verified Shopify resource group reuses one receipt-attributed probe across profiles'
        [void](Invoke-OnboardingApply $repoRoot 'gurbakir' 'staging' $stagingPlanPath 'gurbakir' 'staging' -ConfirmApply -IncludeAcceptanceProbe -PriorReceipt $resultPath -OutputPath $stagingResultPath -Transport $transport)
        Assert-True -Condition ($script:writeCount -eq 4) -Name 'cross-profile same-shop Apply performs zero writes'

        $readbackTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            if ($method -ceq 'POST') { return & $transport $method $uri $headers $body $maximumBytes }
            if ($uri.AbsolutePath -eq '/.well-known/openid-configuration') {
                return [pscustomobject]@{StatusCode=200;Data=@{issuer='https://shopify.com/authentication/1234567890';authorization_endpoint='https://shopify.com/auth';token_endpoint='https://shopify.com/token';end_session_endpoint='https://shopify.com/logout';jwks_uri='https://shopify.com/jwks';code_challenge_methods_supported=@('S256');grant_types_supported=@('authorization_code');id_token_signing_alg_values_supported=@('RS256')}}
            }
            if ($uri.AbsolutePath -eq '/.well-known/customer-account-api') { return [pscustomobject]@{StatusCode=200;Data=@{graphql_api='https://shopify.com/customer-account/api/2026-07/graphql'}} }
            if ($uri.AbsolutePath -eq '/.well-known/assetlinks.json') { return [pscustomobject]@{StatusCode=403;Data=$null} }
            throw 'unexpected readback fixture operation'
        }.GetNewClosure()
        $clientValues=[ordered]@{
            'shopify.storefrontPublicToken'='fixture-public-token';'shopify.customerAccountClientId'='fixture-client-id';
            'shopify.customerAccountIssuer'='https://shopify.com/authentication/1234567890';
            'shopify.customerAccountAuthorizationEndpoint'='https://shopify.com/auth';'shopify.customerAccountTokenEndpoint'='https://shopify.com/token';
            'shopify.customerAccountLogoutEndpoint'='https://shopify.com/logout';'shopify.customerAccountGraphqlEndpoint'='https://shopify.com/customer-account/api/2026-07/graphql';
            'shopify.customerAccountRedirectUri'='shop.1234567890.gurbakir://oauth/callback'
        }
        $script:readbackArguments=@();$script:readbackPrivileged=@()
        $readbackRunner={param($arguments,$privilegedNames);$script:readbackArguments=@($arguments);$script:readbackPrivileged=@($privilegedNames);return 0}
        $readback=Invoke-OnboardingReadback $repoRoot 'gurbakir' 'development' -Transport $readbackTransport -ProcessRunner $readbackRunner -ClientValues $clientValues
        Assert-True -Condition ([string]$readback.storefrontMobileReadback -ceq 'PASS') -Name 'Readback executes the bounded Storefront Menu and Home proof lane'
        Assert-True `
            -Condition ('com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest' -in $script:readbackArguments -and 'com.gurbakir.storefront.OwnedHomeContentReadbackTest' -in $script:readbackArguments -and $credentialName -in $script:readbackPrivileged) `
            -Name 'Readback selects both proof classes and strips privileged provider credentials from Gradle'
        Assert-Throws `
            -Action { Invoke-OnboardingReadback $repoRoot 'gurbakir' 'development' -Transport $readbackTransport -ProcessRunner { return 1 } -ClientValues $clientValues } `
            -Pattern 'MOBILE_READBACK_FAILURE' `
            -Name 'Readback reports a mobile-facing proof failure instead of Inspect success'

        $wrongIdentityTransport={
            param($method,$uri,$headers,$body,$maximumBytes)
            if($method-ceq'POST'){return & $transport $method $uri $headers $body $maximumBytes}
            if($uri.AbsolutePath-eq'/.well-known/openid-configuration'){return [pscustomobject]@{StatusCode=200;Data=@{issuer='https://shopify.com/authentication/999';authorization_endpoint='https://shopify.com/auth';token_endpoint='https://shopify.com/token';end_session_endpoint='https://shopify.com/logout';jwks_uri='https://shopify.com/jwks';code_challenge_methods_supported=@('S256');grant_types_supported=@('authorization_code');id_token_signing_alg_values_supported=@('RS256')}}}
            if($uri.AbsolutePath-eq'/.well-known/customer-account-api'){return [pscustomobject]@{StatusCode=200;Data=@{graphql_api='https://shopify.com/customer-account/api/2026-07/graphql'}}}
            if($uri.AbsolutePath-eq'/.well-known/assetlinks.json'){return [pscustomobject]@{StatusCode=403;Data=$null}}
        }.GetNewClosure()
        Assert-Throws `
            -Action { Invoke-OnboardingInspect $repoRoot 'gurbakir' 'development' -Transport $wrongIdentityTransport } `
            -Pattern 'CUSTOMER_SHOP_ID_MISMATCH' `
            -Name 'Inspect fails closed on Customer provider identity mismatch'
    } finally {
        [Environment]::SetEnvironmentVariable($credentialName, $previous, 'Process')
        [Environment]::SetEnvironmentVariable($stagingCredentialName, $previousStaging, 'Process')
        if ($null -ne $outsideReceipt -and (Test-Path -LiteralPath $outsideReceipt)) { Remove-Item -LiteralPath $outsideReceipt -Force }
        if(Test-Path $bindingPath){Remove-Item -LiteralPath $bindingPath -Force}
        if(Test-Path $stagingBindingPath){Remove-Item -LiteralPath $stagingBindingPath -Force}
        if(Test-Path $temporaryRoot){Remove-Item -LiteralPath $temporaryRoot -Recurse -Force}
    }
}

switch ($Suite) {
    'Registry' { Invoke-RegistrySuite }
    'Configuration' { Invoke-ConfigurationSuite }
    'Enrollment' { Invoke-EnrollmentSuite }
    'OperatorReadOnly' { Invoke-OperatorReadOnlySuite }
    'OperatorApply' { Invoke-OperatorApplySuite }
    'Security' { Invoke-OperatorReadOnlySuite; Invoke-OperatorApplySuite }
    'All' {
        Invoke-RegistrySuite
        Invoke-ConfigurationSuite
        Invoke-EnrollmentSuite
        Invoke-OperatorReadOnlySuite
        Invoke-OperatorApplySuite
    }
    default {
        throw "Suite $Suite has not been implemented yet."
    }
}

$results | Format-Table -AutoSize
Write-Output ("Multi-Brand onboarding {0} suite: PASS ({1}/{1})" -f $Suite, $results.Count)
