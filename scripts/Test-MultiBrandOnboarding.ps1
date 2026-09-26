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

function Copy-TestValue {
    param([Parameter(Mandatory)]$Value)

    return (($Value | ConvertTo-Json -Depth 32 -Compress) | ConvertFrom-Json -AsHashtable -Depth 32)
}

function Get-TestFileSnapshot {
    param([Parameter(Mandatory)][string]$Path)

    $exists = Test-Path -LiteralPath $Path -PathType Leaf
    [pscustomobject]@{
        Exists = $exists
        Bytes = if ($exists) { [System.IO.File]::ReadAllBytes($Path) } else { $null }
    }
}

function Restore-TestFileSnapshot {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)]$Snapshot
    )

    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    if ([bool]$Snapshot.Exists) {
        [System.IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
        [System.IO.File]::WriteAllBytes($Path, [byte[]]$Snapshot.Bytes)
    }
}

function Write-TestProbeRecoveryEvidence {
    param(
        [Parameter(Mandatory)]$Plan,
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][string]$Name,
        [scriptblock]$MutatePlan,
        [scriptblock]$MutateIntent,
        [scriptblock]$MutateRecovery
    )

    $planCopy = Copy-TestValue $Plan
    if ($null -ne $MutatePlan) { & $MutatePlan $planCopy }
    $actions = @($planCopy.actions)
    $action = $actions[0]
    $intent = & (Get-Module Onboarding.Operator) {
        param($Receipt, [object[]]$ReceiptActions, $ReceiptAction)
        New-OnboardingRecoverySnapshot -Plan $Receipt -Actions $ReceiptActions -Action $ReceiptAction
    } $planCopy $actions $action
    if ($null -ne $MutateIntent) { & $MutateIntent $intent }
    $ambiguousActions = Copy-TestValue $actions
    @($ambiguousActions)[0].status = 'AMBIGUOUS'
    $recovery = & (Get-Module Onboarding.Operator) {
        param($Receipt, [object[]]$ReceiptActions, $ReceiptAction)
        New-OnboardingRecoverySnapshot -Plan $Receipt -Actions $ReceiptActions -Action $ReceiptAction
    } $planCopy @($ambiguousActions) @($ambiguousActions)[0]
    if ($null -ne $MutateRecovery) { & $MutateRecovery $recovery }

    $planPath = Join-Path $Directory "$Name-plan.json"
    $recoveryPath = Join-Path $Directory "$Name-recovery.json"
    Write-OnboardingReceipt $planPath $planCopy
    Write-OnboardingReceipt "$planPath.intent-$([int]$action.ordinal).json" $intent
    Write-OnboardingReceipt $recoveryPath $recovery
    [pscustomobject]@{ PlanPath = $planPath; RecoveryPath = $recoveryPath }
}

function Invoke-RegistrySuite {
    $registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
    $commonSource = [IO.File]::ReadAllText(
        (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Common.psm1'),
        [Text.Encoding]::UTF8
    )
    Assert-True `
        -Condition (
            $commonSource.Contains('function Read-OnboardingBoundedFileBytes') -and
            -not $commonSource.Contains('[System.IO.File]::ReadAllBytes($Path)')
        ) `
        -Name 'strict local contract readers bound allocation before loading complete files'
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
    $production = Get-OnboardingApplicationProfile `
        -Registry $registry `
        -Application 'gurbakir' `
        -Profile 'production'
    Assert-True `
        -Condition (
            [string]$production.Profile.runtimeEnvironment -ceq 'PRODUCTION' -and
            [string]$production.Profile.variants[1].applicationId -ceq 'com.gurbakir.mobile' -and
            [string]$production.Profile.customerAccount.callbackSchemeSuffix -ceq 'gurbakir.production' -and
            [string]$production.Profile.firebase.mode -ceq 'disabled' -and
            [string]$production.Application.releaseBoundary -ceq 'nonproduction-only'
        ) `
        -Name 'Gurbakir production candidate has isolated package callback and disabled Firebase'
    Assert-True `
        -Condition (
            [string]$resolved.Application.identity.webRoles.legalSupport.paths.accountDeletionRequest -ceq
            '/pages/uygulama-hesap-silme-talebi'
        ) `
        -Name 'Gurbakir owns a dedicated brand-level deletion request path'

    $trial = Get-OnboardingApplicationProfile `
        -Registry $registry `
        -Application 'trial' `
        -Profile 'development'
    Assert-True `
        -Condition (
            [string]$trial.Application.module -ceq ':trial' -and
            [string]$trial.Application.identity.brandKey -ceq 'multi-brand-trial' -and
            [string]$trial.Profile.runtimeEnvironment -ceq 'DEVELOPMENT'
        ) `
        -Name 'Trial resolves as an independent development real-application profile'
    Assert-True `
        -Condition (
            [string]$trial.Profile.storefront.domain -ceq 'multi-brand-trial-store.myshopify.com' -and
            [string]$trial.Profile.storefront.apiVersion -ceq '2026-07' -and
            [string]$trial.Profile.storefront.catalog.menuHandle -ceq 'main-menu' -and
            [string]$trial.Profile.storefront.home.rootType -ceq 'mobile_home_v2' -and
            [long]$trial.Profile.storefront.home.contentSchemaVersion -eq 2 -and
            [string]$trial.Profile.storefront.home.definitionContract -ceq 'pilot-media-v2'
        ) `
        -Name 'Trial selects the exact Home v2 Storefront contract after the atomic cutover'
    Assert-True `
        -Condition (
            $null -ne $trial.Application.identity.webRoles.legalSupport -and
            [string]$trial.Application.identity.webRoles.legalSupport.origin -ceq 'https://multi-brand-trial-store.myshopify.com' -and
            @($trial.Application.identity.webRoles.legalSupport.paths.Keys).Count -eq 6 -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.support -ceq '/pages/trial-destek' -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.privacy -ceq '/pages/trial-gizlilik' -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.terms -ceq '/pages/trial-kullanim-kosullari' -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.shipping -ceq '/pages/trial-kargo' -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.returns -ceq '/pages/trial-iade' -and
            [string]$trial.Application.identity.webRoles.legalSupport.paths.legalNotice -ceq '/pages/trial-yasal-bildirim'
        ) `
        -Name 'Trial registry owns the provider-read development legal support pages'
    Assert-True `
        -Condition (-not $trial.Application.identity.webRoles.legalSupport.paths.Contains('accountDeletionRequest')) `
        -Name 'Trial has no unprovisioned account-deletion request route'

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
    Assert-True -Condition ($lines -ccontains 'shopify.homeDefinitionContract=gate7-v1') `
        -Name 'Gurbakir projection preserves the exact Gate 7 Home contract id'
    Assert-True `
        -Condition ($lines -ccontains 'web.legalSupportPath.accountDeletionRequest=/pages/uygulama-hesap-silme-talebi') `
        -Name 'Gurbakir projection carries the deletion route'

    $projectionPath = Join-Path $repoRoot 'config\onboarding\generated\gurbakir\development.properties'
    Test-OnboardingProjection -Path $projectionPath -ExpectedLines $lines
    Add-TestResult -Name 'tracked development projection is byte exact' -Passed $true -Evidence 'matched'

    $trialLines = Get-OnboardingProjectionLines `
        -Registry $registry `
        -ApplicationRecord $trial.Application `
        -ProfileRecord $trial.Profile `
        -RegistrySha256 $sha
    Assert-True `
        -Condition (@($trialLines | Where-Object { $_ -like 'web.legalSupportPath.accountDeletionRequest=*' }).Count -eq 0) `
        -Name 'Trial projection omits the unprovisioned deletion route'
    foreach ($expectedTrialLine in @(
        'app.brandKey=multi-brand-trial',
        'app.databaseName=trial-store-local.db',
        'android.applicationId.debug=com.projectapp134.multibrandtrial.dev.debug',
        'android.applicationId.release=com.projectapp134.multibrandtrial.dev',
        'shopify.storefrontDomain=multi-brand-trial-store.myshopify.com',
        'shopify.storefrontApiVersion=2026-07',
        'shopify.catalogMenuHandle=main-menu',
        'shopify.homeRootType=mobile_home_v2',
        'shopify.homeContentSchemaVersion=2',
        'shopify.homeDefinitionContract=pilot-media-v2',
        'web.legalSupportOrigin=https://multi-brand-trial-store.myshopify.com',
        'web.legalSupportPath.support=/pages/trial-destek',
        'web.legalSupportPath.privacy=/pages/trial-gizlilik',
        'web.legalSupportPath.terms=/pages/trial-kullanim-kosullari',
        'web.legalSupportPath.shipping=/pages/trial-kargo',
        'web.legalSupportPath.returns=/pages/trial-iade',
        'web.legalSupportPath.legalNotice=/pages/trial-yasal-bildirim',
        'firebase.ownershipKey=multi-brand-trial-development'
    )) {
        Assert-True -Condition ($trialLines -ccontains $expectedTrialLine) `
            -Name "Trial projection owns $expectedTrialLine"
    }
    $trialProjectionPath = Join-Path $repoRoot 'config\onboarding\generated\trial\development.properties'
    Test-OnboardingProjection -Path $trialProjectionPath -ExpectedLines $trialLines
    Add-TestResult -Name 'tracked Trial development projection is byte exact' -Passed $true -Evidence 'matched'

    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('gate8-registry-' + [guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $temporaryRoot)
    $futureDirectory = $null
    $escapeLink = $null
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

        $stringSchemaPath = Join-Path $temporaryRoot 'string-schema-version.json'
        [System.IO.File]::WriteAllText(
            $stringSchemaPath,
            $raw.Replace('"schemaVersion": 1,', '"schemaVersion": "1",'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $stringSchemaPath -RepositoryRoot $repoRoot } `
            -Pattern 'INVALID_JSON_TYPE' `
            -Name 'registry schema version requires a JSON integer rather than a coercible string'

        $unsafeOriginPath = Join-Path $temporaryRoot 'unsafe-app-link-port.json'
        [System.IO.File]::WriteAllText(
            $unsafeOriginPath,
            $raw.Replace('"origin": "https://gurbakir.com",', '"origin": "https://gurbakir.com:443/",'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $unsafeOriginPath -RepositoryRoot $repoRoot } `
            -Pattern 'UNSAFE_URL' `
            -Name 'App Link origins reject even an explicitly written default port'

        $queryOriginPath = Join-Path $temporaryRoot 'unsafe-app-link-query.json'
        [System.IO.File]::WriteAllText(
            $queryOriginPath,
            $raw.Replace('"origin": "https://gurbakir.com",', '"origin": "https://gurbakir.com/?audit=1",'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $queryOriginPath -RepositoryRoot $repoRoot } `
            -Pattern 'UNSAFE_URL' `
            -Name 'App Link origins reject query components'

        foreach ($case in @(
            @{ Name = 'encoded traversal'; Value = '/pages/%2e%2e/contact'; Error = 'INVALID_LEGAL_PATH' },
            @{ Name = 'query'; Value = '/pages/request?next=contact'; Error = 'INVALID_LEGAL_PATH' },
            @{ Name = 'fragment'; Value = '/pages/request#done'; Error = 'INVALID_LEGAL_PATH' },
            @{ Name = 'generic homepage'; Value = '/'; Error = 'INVALID_LEGAL_PATH' },
            @{ Name = 'duplicate support route'; Value = '/pages/contact'; Error = 'DUPLICATE_LEGAL_PATH' },
            @{ Name = 'non-page route'; Value = '/policies/account-deletion'; Error = 'INVALID_DELETION_PATH' }
        )) {
            $unsafeDeletionPath = Join-Path $temporaryRoot ('deletion-' + $case.Name.Replace(' ', '-') + '.json')
            [System.IO.File]::WriteAllText(
                $unsafeDeletionPath,
                $raw.Replace(
                    '"accountDeletionRequest": "/pages/uygulama-hesap-silme-talebi"',
                    '"accountDeletionRequest": "' + $case.Value + '"'
                ),
                [System.Text.UTF8Encoding]::new($false)
            )
            Assert-Throws `
                -Action { Import-OnboardingRegistry -Path $unsafeDeletionPath -RepositoryRoot $repoRoot } `
                -Pattern $case.Error `
                -Name ("deletion path rejects " + $case.Name)
        }

        $nativeMismatchPath = Join-Path $temporaryRoot 'native-composition-mismatch.json'
        [System.IO.File]::WriteAllText(
            $nativeMismatchPath,
            $raw.Replace(
                '"wishlist": "ENABLED",' + "`n" + '          "customerAccount": "ENABLED",' + "`n" + '          "primaryNavigation": ["HOME", "CATEGORIES", "SEARCH", "WISHLIST", "ACCOUNT"]',
                '"wishlist": "DISABLED",' + "`n" + '          "customerAccount": "ENABLED",' + "`n" + '          "primaryNavigation": ["HOME", "CATEGORIES", "SEARCH", "ACCOUNT"]'
            ),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $nativeMismatchPath -RepositoryRoot $repoRoot } `
            -Pattern 'NATIVE_COMPOSITION_MISMATCH' `
            -Name 'tracked native assertions must match independently frozen application composition'

        $fixturePath = Join-Path $temporaryRoot 'fixture.json'
        $fixtureMarker = '"fixtureOnly": false,'
        $fixtureIndex = $raw.IndexOf($fixtureMarker, [StringComparison]::Ordinal)
        if ($fixtureIndex -lt 0) { throw 'The current registry has no real enrollment fixture marker.' }
        $fixtureRaw = $raw.Remove($fixtureIndex, $fixtureMarker.Length).Insert($fixtureIndex, '"fixtureOnly": true,')
        $fixtureDocument = $fixtureRaw | ConvertFrom-Json -AsHashtable -Depth 32
        Assert-True `
            -Condition (
                [bool]$fixtureDocument.applications[0].fixtureOnly -and
                -not [bool]$fixtureDocument.applications[1].fixtureOnly
            ) `
            -Name 'fixture mutation changes only the first enrolled application record'
        [System.IO.File]::WriteAllText(
            $fixturePath,
            $fixtureRaw,
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
        $futureApplication.profiles[0].localConfiguration = 'config/local/future-fixture/conformance.properties'
        $futureApplication.profiles[0].providerBindingFile = 'config/local/future-fixture/conformance.providers.json'
        $futureApplication.profiles[0].variants[0].applicationId = 'com.example.futurefixture.debug'
        $futureApplication.profiles[0].variants[1].applicationId = 'com.example.futurefixture'
        $futureApplication.profiles[0].storefront = [ordered]@{
            mode = 'enabled'
            domain = 'future.invalid'
            apiVersion = '2026-07'
            publicTokenLocalKey = 'shopify.storefrontPublicToken'
            mediaOrigins = @('future.invalid')
            sharedResourceGroup = 'future-fixture-shop'
            catalog = [ordered]@{ menuHandle = 'future-menu'; managementMode = 'validate-only' }
            home = [ordered]@{
                rootType = 'mobile_home'
                rootHandle = 'primary'
                contentSchemaVersion = 1
                definitionContract = 'gate7-v1'
                definitionManagementMode = 'create-if-missing'
                entryManagementMode = 'validate-only'
                sourceMode = 'shopify-metaobject'
            }
        }
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
        Assert-True -Condition (@($acceptedFixture.applications | Where-Object { $_.role -ceq 'real-brand-application' }).Count -eq 3) `
            -Name 'fixture registry preserves both enrolled real applications while adding an independent fixture record'
        $futureSelected = Get-OnboardingApplicationProfile `
            -Registry $acceptedFixture -Application 'future-fixture' -Profile 'conformance'
        Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Operator.psm1') -Force
        $futureContext = [pscustomobject]@{
            Registry = $acceptedFixture
            Selected = $futureSelected
            Binding = @{ application = 'future-fixture'; profile = 'conformance'; shopify = @{ shopId = '1234567890' } }
            RepositoryRoot = $repoRoot
        }
        $futureClientLines = @(Get-OnboardingValidatedClientConfigurationLines `
            -Context $futureContext `
            -Values @{ 'shopify.storefrontPublicToken' = 'fixture-public-token' })
        Assert-True `
            -Condition ($futureClientLines.Count -eq 1 -and $futureClientLines[0] -ceq 'shopify.storefrontPublicToken=fixture-public-token') `
            -Name 'real Storefront profile with Account disabled requires no dummy Customer configuration'
        Import-Module $registryModule -Force
        Import-Module $commonModule -Force
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
            -Name 'enabled Home profile cannot redefine a closed Home contract tuple'

        foreach ($invalidHomeMutation in @(
            @{
                Name = 'v1 type with v2 contract is rejected'
                Old = '"definitionContract": "gate7-v1"'
                New = '"definitionContract": "pilot-media-v2"'
            },
            @{
                Name = 'v2 type with v1 schema version is rejected'
                Old = '"contentSchemaVersion": 2'
                New = '"contentSchemaVersion": 1'
            },
            @{
                Name = 'unknown Home definition contract is rejected'
                Old = '"definitionContract": "pilot-media-v2"'
                New = '"definitionContract": "pilot-media-v3"'
            }
        )) {
            $invalidHomePath = Join-Path $temporaryRoot (([string]$invalidHomeMutation.Name -replace '[^a-z0-9]+', '-') + '.json')
            [System.IO.File]::WriteAllText(
                $invalidHomePath,
                $raw.Replace([string]$invalidHomeMutation.Old, [string]$invalidHomeMutation.New),
                [System.Text.UTF8Encoding]::new($false)
            )
            Assert-Throws `
                -Action { Import-OnboardingRegistry -Path $invalidHomePath -RepositoryRoot $repoRoot } `
                -Pattern 'HOME_CONTRACT_MISMATCH' `
                -Name ([string]$invalidHomeMutation.Name)
        }

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

        $safePathRoot = Join-Path $temporaryRoot 'safe-path-root'
        $outsidePathRoot = Join-Path $temporaryRoot 'outside-path-root'
        [IO.Directory]::CreateDirectory($safePathRoot) | Out-Null
        [IO.Directory]::CreateDirectory($outsidePathRoot) | Out-Null
        $escapeLink = Join-Path $safePathRoot 'escape-link'
        if ($IsWindows) {
            [void](New-Item -ItemType Junction -Path $escapeLink -Target $outsidePathRoot)
        } else {
            [void](New-Item -ItemType SymbolicLink -Path $escapeLink -Target $outsidePathRoot)
        }
        Assert-Throws `
            -Action { Test-OnboardingSafeRelativePath $safePathRoot 'escape-link/credential.properties' 'fixturePath' } `
            -Pattern 'UNSAFE_PATH' `
            -Name 'existing reparse or symbolic-link path component cannot escape the repository root'

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

        $firebasePathCollision = Join-Path $temporaryRoot 'firebase-path-collision.json'
        [System.IO.File]::WriteAllText(
            $firebasePathCollision,
            $raw.Replace(
                '"firebaseConfig": "app/src/stagingDebug/google-services.json"',
                '"firebaseConfig": "app/src/developmentDebug/google-services.json"'
            ),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingRegistry -Path $firebasePathCollision -RepositoryRoot $repoRoot } `
            -Pattern 'FIREBASE_CONFIG_PATH_COLLISION' `
            -Name 'Firebase configuration paths have one global application profile owner'

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

        $stringBindingSchemaPath = Join-Path $temporaryRoot 'string-provider-binding-schema.json'
        [System.IO.File]::WriteAllText(
            $stringBindingSchemaPath,
            $bindingJson.Replace('"schemaVersion": 1', '"schemaVersion": "1"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action {
                Import-OnboardingProviderBinding `
                    -Path $stringBindingSchemaPath `
                    -Application 'gurbakir' `
                    -Profile 'development'
            } `
            -Pattern 'INVALID_JSON_TYPE' `
            -Name 'provider-binding schema version requires a JSON integer'

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
        $gate9ReceiptPath = Join-Path $temporaryRoot 'gate9-receipt.json'
        [System.IO.File]::WriteAllText(
            $gate9ReceiptPath,
            $receiptJson.Replace('"operationContractVersion": "gate8-v1"', '"operationContractVersion": "gate9-v2"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        $gate9Receipt = Import-OnboardingReceipt -Path $gate9ReceiptPath
        Assert-True -Condition ($gate9Receipt.operationContractVersion -ceq 'gate9-v2') `
            -Name 'closed Gate 9 Plan receipt version is accepted without widening unknown versions'
        $gate9ActionReceiptPath = Join-Path $temporaryRoot 'gate9-action-receipt.json'
        $gate9ActionJson = $receiptJson.Replace(
            '"operationContractVersion": "gate8-v1"',
            '"operationContractVersion": "gate9-v2"'
        ).Replace(
                '"actions": []',
                '"actions": [{"ordinal":1,"resourceKind":"SHOPIFY_HOME_DEFINITION","resourceKey":"mobile_home_image_v1","managementMode":"CREATE_IF_MISSING","beforeClassification":"ABSENT","intendedAction":"CREATE","beforeFingerprint":"0000000000000000000000000000000000000000000000000000000000000000","providerResourceId":null,"status":"PLANNED","afterClassification":"UNKNOWN","afterFingerprint":"0000000000000000000000000000000000000000000000000000000000000000"}]'
        )
        [System.IO.File]::WriteAllText(
            $gate9ActionReceiptPath,
            $gate9ActionJson,
            [System.Text.UTF8Encoding]::new($false)
        )
        $gate9ActionReceipt = Import-OnboardingReceipt -Path $gate9ActionReceiptPath
        Assert-True `
            -Condition (
                [string]$gate9ActionReceipt.operationContractVersion -ceq 'gate9-v2' -and
                [string]$gate9ActionReceipt.actions[0].resourceKey -ceq 'mobile_home_image_v1'
            ) `
            -Name 'Gate 9 Plan receipt round-trips a v2 image definition action'
        $gate8V2ActionReceiptPath = Join-Path $temporaryRoot 'gate8-v2-action-receipt.json'
        [System.IO.File]::WriteAllText(
            $gate8V2ActionReceiptPath,
            $gate9ActionJson.Replace('"operationContractVersion": "gate9-v2"', '"operationContractVersion": "gate8-v1"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $gate8V2ActionReceiptPath } `
            -Pattern 'INVALID_RESOURCE_KEY' `
            -Name 'Gate 8 receipt cannot admit a Gate 9 definition key'
        $unknownReceiptPath = Join-Path $temporaryRoot 'unknown-receipt.json'
        [System.IO.File]::WriteAllText(
            $unknownReceiptPath,
            $receiptJson.Replace('"operationContractVersion": "gate8-v1"', '"operationContractVersion": "future-v3"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $unknownReceiptPath } `
            -Pattern 'UNSUPPORTED_RECEIPT_VERSION' `
            -Name 'unknown operator receipt versions remain rejected'

        $stringReceiptSchemaPath = Join-Path $temporaryRoot 'string-receipt-schema.json'
        [System.IO.File]::WriteAllText(
            $stringReceiptSchemaPath,
            $receiptJson.Replace('"receiptSchemaVersion": 1', '"receiptSchemaVersion": "1"'),
            [System.Text.UTF8Encoding]::new($false)
        )
        Assert-Throws `
            -Action { Import-OnboardingReceipt -Path $stringReceiptSchemaPath } `
            -Pattern 'INVALID_JSON_TYPE' `
            -Name 'receipt schema version requires a JSON integer'

        $receiptSchema = Get-Content `
            -LiteralPath (Join-Path $repoRoot 'config\onboarding\operator-receipt.schema.v1.json') `
            -Raw | ConvertFrom-Json
        $timestampDefinition = $receiptSchema.'$defs'.utcSecondTimestamp
        Assert-True `
            -Condition (
                $null -ne $timestampDefinition -and
                [string]$receiptSchema.properties.createdAtUtc.'$ref' -ceq '#/$defs/utcSecondTimestamp' -and
                [string]$receiptSchema.properties.expiresAtUtc.oneOf[0].'$ref' -ceq '#/$defs/utcSecondTimestamp' -and
                [regex]::IsMatch('2026-09-14T12:00:00Z', [string]$timestampDefinition.pattern) -and
                -not [regex]::IsMatch('2026-09-14T12:00:00.123Z', [string]$timestampDefinition.pattern) -and
                -not [regex]::IsMatch('2026-09-14T12:00:00+00:00', [string]$timestampDefinition.pattern)
            ) `
            -Name 'receipt schema permits only whole-second UTC timestamps'

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
        if ($null -ne $escapeLink -and (Get-Item -LiteralPath $escapeLink -Force -ErrorAction SilentlyContinue)) {
            Remove-Item -LiteralPath $escapeLink -Force
        }
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
    $trialBuild = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'apps\trial\build.gradle.kts'),
        [System.Text.Encoding]::UTF8
    )
    $ownedConfiguration = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'storefront\src\test\kotlin\com\gurbakir\storefront\OwnedOnboardingConfiguration.kt'),
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
    foreach ($reader in @(
        @{ Name = 'Gurbakir Gradle reader'; Source = $appBuild },
        @{ Name = 'Trial Gradle reader'; Source = $trialBuild },
        @{ Name = 'Storefront Gradle reader'; Source = $storefrontBuild },
        @{ Name = 'owned Storefront proof reader'; Source = $ownedConfiguration }
    )) {
        Assert-True `
            -Condition ([string]$reader.Source).Contains('shopify.homeDefinitionContract') `
            -Name "$($reader.Name) consumes the strict Home definition contract projection"
    }
    Assert-True `
        -Condition (
            $appBuild.Contains('Triple("mobile_home", "1", "gate7-v1")') -and
            $trialBuild.Contains('Triple("mobile_home_v2", "2", "pilot-media-v2")') -and
            $storefrontBuild.Contains('Triple("mobile_home", "1", "gate7-v1")') -and
            $storefrontBuild.Contains('Triple("mobile_home_v2", "2", "pilot-media-v2")')
        ) `
        -Name 'all Gradle readers keep the two Home contract tuples closed and exact'
    Assert-True `
        -Condition (
            -not $appBuild.Contains('check(selectedApplication == "gurbakir")') -and
            $appBuild.Contains('selectedApplication == "gurbakir"') -and
            $appBuild.Contains('gradle.taskGraph.whenReady') -and
            $appBuild.Contains('does not belong to the selected onboarding application')
        ) `
        -Name 'Gurbakir module validates its own tasks without vetoing another explicitly enrolled application selection'
    Assert-True `
        -Condition (
            $appBuild.Contains('if (requireConfiguredProfile) {') -and
            $appBuild.Contains('requireConfiguredProfile requires an explicit application and profile.')
        ) `
        -Name 'strict configured selection always requires an explicit application and profile pair'
    Assert-True -Condition (-not $storefrontBuild.Contains('config/local.properties')) `
        -Name 'Storefront proofs do not read root local configuration'
    Assert-True `
        -Condition (
            $storefrontBuild.Contains('JsonSlurper') -and
            $storefrontBuild.Contains('configurationProjection') -and
            $storefrontBuild.Contains('localConfiguration') -and
            -not $storefrontBuild.Contains('config/onboarding/generated/gurbakir') -and
            -not $storefrontBuild.Contains('config/local/gurbakir')
        ) `
        -Name 'shared Storefront proof configuration resolves the selected enrolled application and profile'
    $readme = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'README.md'), [System.Text.Encoding]::UTF8)
    $bootstrapDirectory = $readme.IndexOf('New-Item -ItemType Directory -Force .\config\local\gurbakir', [StringComparison]::Ordinal)
    $bootstrapCopy = $readme.IndexOf('Copy-Item .\config\onboarding\examples\gurbakir-development.properties.example', [StringComparison]::Ordinal)
    Assert-True `
        -Condition ($bootstrapDirectory -ge 0 -and $bootstrapCopy -gt $bootstrapDirectory) `
        -Name 'README bootstrap creates the ignored profile directory before copying configuration'
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
    $taskResolver = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'scripts\Get-RegisteredGradleTasks.ps1'),
        [System.Text.Encoding]::UTF8
    )
    $firebaseValidator = [System.IO.File]::ReadAllText(
        (Join-Path $repoRoot 'scripts\Test-FirebaseConfiguration.ps1'),
        [System.Text.Encoding]::UTF8
    )
    Assert-True `
        -Condition (
            $firebaseValidator.Contains("role -ceq 'real-brand-application'") -and
            -not $firebaseValidator.Contains("key -ceq 'gurbakir'") -and
            -not $firebaseValidator.Contains("-Application 'gurbakir'")
        ) `
        -Name 'Firebase configuration validation derives every enabled real application profile from the registry'
    Assert-True `
        -Condition (
            $taskResolver.Contains('projectDir') -and
            -not $taskResolver.Contains("gradleProject -ceq ':synthetic'")
        ) `
        -Name 'task resolver derives explicit Gradle project directories without a synthetic special case'
    foreach ($lane in @('unit', 'assemble', 'api30', 'api23')) {
        & (Join-Path $repoRoot 'scripts\Get-RegisteredGradleTasks.ps1') -Lane $lane -ValidateOnly
        Assert-True -Condition $true -Name "registry and workflow cover $lane lane"
    }
    $registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
    $temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('gate8-enrollment-' + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    try {
        $workflowPath = Join-Path $repoRoot '.github\workflows\android-foundation.yml'
        $workflowLines = [System.IO.File]::ReadAllLines($workflowPath, [System.Text.Encoding]::UTF8)
        $fakeGradlePath = if ($IsWindows) {
            Join-Path $temporaryRoot 'gradlew.bat'
        } else {
            Join-Path $temporaryRoot 'gradlew'
        }
        $unixFakeGradleText =
            "#!/usr/bin/env bash`nprintf '%s\n' `"`$@`" > `"`$GATE8_GRADLE_ARGUMENTS`"`n"
        $expectedUnixFakeGradleText =
            "#!/usr/bin/env bash`nprintf '%s\n' `"`$@`" > `"`$GATE8_GRADLE_ARGUMENTS`"`n"
        Assert-True `
            -Condition ($unixFakeGradleText -ceq $expectedUnixFakeGradleText) `
            -Name 'Unix workflow Gradle fixture preserves one printf newline escape'
        if ($IsWindows) {
            [System.IO.File]::WriteAllText(
                $fakeGradlePath,
                "@echo off`r`necho %* > `"%GATE8_GRADLE_ARGUMENTS%`"`r`nexit /b 0`r`n",
                [System.Text.ASCIIEncoding]::new()
            )
        } else {
            [System.IO.File]::WriteAllText(
                $fakeGradlePath,
                $unixFakeGradleText,
                [System.Text.UTF8Encoding]::new($false)
            )
            & chmod +x $fakeGradlePath
            if ($LASTEXITCODE -ne 0) { throw 'Unable to make the workflow Gradle fixture executable.' }
        }
        $workflowSteps = [ordered]@{
            'JVM foundation tests' = 'unit'
            'Development and staging package verification' = 'assemble'
            'Core and development instrumentation tests' = 'api30'
            'Shared active-path and synthetic minimum-SDK instrumentation tests' = 'api23'
        }
        $previousArgumentsPath = [Environment]::GetEnvironmentVariable('GATE8_GRADLE_ARGUMENTS', 'Process')
        try {
            foreach ($entry in $workflowSteps.GetEnumerator()) {
                $stepLine = "      - name: $($entry.Key)"
                $stepIndex = [Array]::IndexOf($workflowLines, $stepLine)
                if ($stepIndex -lt 0) { throw "Workflow step is missing: $($entry.Key)" }
                $runIndex = $stepIndex + 1
                while ($runIndex -lt $workflowLines.Count -and $workflowLines[$runIndex] -cne '        run: |') {
                    $runIndex++
                }
                if ($runIndex -ge $workflowLines.Count) { throw "Workflow run block is missing: $($entry.Key)" }
                $blockLines = [Collections.Generic.List[string]]::new()
                for ($lineIndex = $runIndex + 1; $lineIndex -lt $workflowLines.Count; $lineIndex++) {
                    $line = $workflowLines[$lineIndex]
                    if ($line.Length -gt 0 -and -not $line.StartsWith('          ', [StringComparison]::Ordinal)) { break }
                    $blockLines.Add($(if ($line.Length -eq 0) { '' } else { $line.Substring(10) }))
                }
                $argumentsPath = Join-Path $temporaryRoot "$($entry.Value)-arguments.txt"
                if (Test-Path -LiteralPath $argumentsPath) { Remove-Item -LiteralPath $argumentsPath -Force }
                [Environment]::SetEnvironmentVariable('GATE8_GRADLE_ARGUMENTS', $argumentsPath, 'Process')
                $resolverPath = (Join-Path $repoRoot 'scripts\Get-RegisteredGradleTasks.ps1').Replace("'", "''")
                $fakePath = $fakeGradlePath.Replace("'", "''")
                $runBlock = ($blockLines -join "`n").Replace(
                    './scripts/Get-RegisteredGradleTasks.ps1',
                    "& '$resolverPath'"
                ).Replace('& ./gradlew', "& '$fakePath'")
                $childOutput = & pwsh -NoProfile -Command $runBlock 2>&1 | Out-String
                $childExitCode = $LASTEXITCODE
                $resolvedTasks = @(& (Join-Path $repoRoot 'scripts\Get-RegisteredGradleTasks.ps1') -Lane ([string]$entry.Value))
                $invokedArguments = @(if (Test-Path -LiteralPath $argumentsPath -PathType Leaf) {
                    if ($IsWindows) {
                        @(([System.IO.File]::ReadAllText($argumentsPath) -split '\s+') | Where-Object { $_.Length -gt 0 })
                    } else {
                        @([System.IO.File]::ReadAllLines($argumentsPath) | Where-Object { $_.Length -gt 0 })
                    }
                } else {
                    @()
                })
                Assert-True `
                    -Condition (
                        $childExitCode -eq 0 -and
                        $invokedArguments.Count -gt 0 -and
                        @($resolvedTasks | Where-Object { $_ -notin $invokedArguments }).Count -eq 0
                    ) `
                    -Name "workflow $($entry.Value) lane executes every resolved Gradle task in a fresh PowerShell process"
            }
        } finally {
            [Environment]::SetEnvironmentVariable('GATE8_GRADLE_ARGUMENTS', $previousArgumentsPath, 'Process')
        }

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
    if ($null -eq ('Gate8BlockingReadStream' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;

public sealed class Gate8BlockingReadStream : Stream
{
    public override bool CanRead => true;
    public override bool CanSeek => false;
    public override bool CanWrite => false;
    public override long Length => throw new NotSupportedException();
    public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
    public override void Flush() { }
    public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
    public override void SetLength(long value) => throw new NotSupportedException();
    public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    public override Task<int> ReadAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
    {
        var completion = new TaskCompletionSource<int>(TaskCreationOptions.RunContinuationsAsynchronously);
        cancellationToken.Register(() => completion.TrySetCanceled(cancellationToken));
        return completion.Task;
    }
}
'@
    }
    $blockingStream = [Gate8BlockingReadStream]::new()
    $readDeadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromMilliseconds(100))
    try {
        Assert-Throws `
            -Action {
                & (Get-Module Onboarding.Common) {
                    param([IO.Stream]$Stream, [Threading.CancellationToken]$CancellationToken)
                    Read-OnboardingBoundedStream -Stream $Stream -MaximumBytes 1024 -CancellationToken $CancellationToken
                } $blockingStream $readDeadline.Token
            } `
            -Pattern 'PROVIDER_RESPONSE_TIMEOUT' `
            -Name 'provider response body reads honor the shared request deadline'
    } finally {
        $readDeadline.Dispose()
        $blockingStream.Dispose()
    }
    $safeRequest = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture-shop.myshopify.com/admin/api/2026-07/graphql.json') -Transport {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{} }
    }
    Assert-True -Condition ($safeRequest.StatusCode -eq 200) -Name 'fixed HTTPS provider request accepts an empty user-info component'
    $observedPublicHeaders = @{}
    $validJsonRequest = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
        param($method, $uri, $headers, $body, $maximumBytes)
        foreach ($key in $headers.Keys) { $observedPublicHeaders[[string]$key] = [string]$headers[$key] }
        [pscustomobject]@{
            StatusCode = 200
            RawBytes = [Text.UTF8Encoding]::new($false).GetBytes('{"valid":true}')
            Headers = @{ 'Content-Type' = 'application/json' }
        }
    }.GetNewClosure()
    Assert-True `
        -Condition (
            $validJsonRequest.StatusCode -eq 200 -and
            $validJsonRequest.Data.valid -eq $true -and
            [string]$observedPublicHeaders['User-Agent'] -ceq 'MultiBrandCommerceAndroid-Gate8/1.0 (+https://github.com/projectapp13-4/multi-brand-commerce-android)'
        ) `
        -Name 'public JSON requests use the stable descriptive project User-Agent required by live endpoints'
    $emptyJsonArray = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
        [pscustomobject]@{
            StatusCode = 200
            RawBytes = [Text.UTF8Encoding]::new($false).GetBytes('[]')
            Headers = @{ 'Content-Type' = 'application/json' }
        }
    }
    Assert-True `
        -Condition (
            $emptyJsonArray.Data -is [object[]] -and
            $emptyJsonArray.Data.Count -eq 0
        ) `
        -Name 'successful top-level empty JSON arrays remain arrays for public association classification'
    Assert-True `
        -Condition ((Get-OnboardingCanonicalJson -Value ([object[]]@())) -ceq '[]') `
        -Name 'empty public JSON arrays have a deterministic non-null fingerprint input'
    Assert-Throws `
        -Action {
            Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
                [pscustomobject]@{
                    StatusCode = 200
                    RawBytes = [Text.UTF8Encoding]::new($false).GetBytes('<html>not json</html>')
                    Headers = @{ 'Content-Type' = 'text/html' }
                }
            }
        } `
        -Pattern 'PROVIDER_RESPONSE_INVALID_JSON' `
        -Name 'successful malformed JSON remains a provider response error'
    $forbiddenHtml = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
        [pscustomobject]@{
            StatusCode = 403
            RawBytes = [Text.UTF8Encoding]::new($false).GetBytes('<html>forbidden</html>')
            Headers = @{ 'Content-Type' = 'text/html' }
        }
    }
    Assert-True `
        -Condition ($forbiddenHtml.StatusCode -eq 403 -and $null -eq $forbiddenHtml.Data) `
        -Name 'non-success HTML remains an HTTP classification instead of invalid JSON'
    Assert-Throws `
        -Action {
            Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
                [pscustomobject]@{ StatusCode = 302; RawBytes = @(); Headers = @{ Location = 'https://other.invalid' } }
            }
        } `
        -Pattern 'PROVIDER_REDIRECT_BLOCKED' `
        -Name 'redirect responses remain blocked before JSON classification'
    Assert-Throws `
        -Action {
            Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]'https://fixture.invalid/data') -Transport {
                throw [OperationCanceledException]::new('fixture deadline')
            }
        } `
        -Pattern 'PROVIDER_RESPONSE_TIMEOUT' `
        -Name 'request deadline cancellation retains a sanitized timeout classification'
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
    $stagingSelection = Get-OnboardingApplicationProfile -Registry $registry -Application 'gurbakir' -Profile 'staging'
    $trialSelection = Get-OnboardingApplicationProfile -Registry $registry -Application 'trial' -Profile 'development'
    $syntheticSelection = Get-OnboardingApplicationProfile -Registry $registry -Application 'synthetic' -Profile 'conformance'
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Operator.psm1') -Force
    $gurbakirHomeOperatorContract = & (Get-Module Onboarding.Operator) {
        param($Selected)
        Get-OnboardingHomeOperatorContract ([pscustomobject]@{ Selected = $Selected })
    } $selected
    $trialHomeOperatorContract = & (Get-Module Onboarding.Operator) {
        param($Selected)
        Get-OnboardingHomeOperatorContract ([pscustomobject]@{ Selected = $Selected })
    } $trialSelection
    Assert-True `
        -Condition (
            [string]$gurbakirHomeOperatorContract.ContractId -ceq 'gate7-v1' -and
            [string]$gurbakirHomeOperatorContract.OperationContractVersion -ceq 'gate8-v1' -and
            [string]$trialHomeOperatorContract.ContractId -ceq 'pilot-media-v2' -and
            [string]$trialHomeOperatorContract.OperationContractVersion -ceq 'gate9-v2' -and
            [string]$trialHomeOperatorContract.SchemaRelativePath -ceq 'config/onboarding/shopify-home-schema.v2.json'
        ) `
        -Name 'operator dispatches Gürbakır v1 and Trial v2 from the closed profile contract tuple'
    $disabledFirebaseState = & (Get-Module Onboarding.Operator) {
        param($Context)
        Get-OnboardingFirebaseInspectionState `
            -Context $Context -Application 'synthetic' -Profile 'conformance' `
            -Transport { throw 'disabled Firebase must not issue a request' }
    } ([pscustomobject]@{ Selected = $syntheticSelection; Binding = @{} })
    Assert-True `
        -Condition ([string]$disabledFirebaseState.Classification -ceq 'NOT_APPLICABLE') `
        -Name 'Inspect classifies disabled Firebase before any credential lookup or provider request'
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Shopify.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.CustomerAccount.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.AppLinks.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Firebase.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Common.psm1') -Force
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
    $script:storefrontIdentityUri = $null
    $script:storefrontIdentityHeaders = $null
    $storefrontIdentityTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:storefrontIdentityUri = $uri
        $script:storefrontIdentityHeaders = $headers
        [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ shop = @{
            id = 'gid://shopify/Shop/1234567890'
            primaryDomain = @{ host = 'gurbakir.com' }
        } } } }
    }
    $storefrontIdentity = Get-ShopifyStorefrontTargetState `
        $selected $binding 'fixture-public-token' $storefrontIdentityTransport
    Assert-True `
        -Condition (
            [string]$storefrontIdentity.Classification -ceq 'PASS' -and
            [string]$script:storefrontIdentityUri.Host -ceq 'gurbakir.com' -and
            [string]$script:storefrontIdentityHeaders['X-Shopify-Storefront-Access-Token'] -ceq 'fixture-public-token'
        ) `
        -Name 'Storefront public client shop identity joins the selected profile to the independent shop binding'
    Assert-Throws `
        -Action {
            Get-ShopifyStorefrontTargetState $selected $binding 'fixture-public-token' {
                [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ shop = @{
                    id = 'gid://shopify/Shop/9999999999'
                    primaryDomain = @{ host = 'gurbakir.com' }
                } } } }
            }
        } `
        -Pattern 'SHOPIFY_STOREFRONT_IDENTITY_MISMATCH' `
        -Name 'Storefront public client cannot target a different shop than the independent binding'

    $liveCompatibleDefinitions = @(
        @{
            id = 'gid://shopify/MetaobjectDefinition/1'
            type = 'mobile_home_collection_grid'
            name = 'Collection grid'
            displayNameKey = 'title'
            fieldDefinitions = @(
                @{ key = 'title'; name = 'Title'; type = @{ name = 'single_line_text_field' }; required = $true; validations = @(@{ name = 'max'; value = '80' }, @{ name = 'min'; value = '1' }) },
                @{ key = 'collections'; name = 'Collections'; type = @{ name = 'list.collection_reference' }; required = $true; validations = @(@{ name = 'list.max'; value = '6' }, @{ name = 'list.min'; value = '1' }) }
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
            displayNameKey = $null
            fieldDefinitions = @(
                @{ key = 'schema_version'; name = 'Schema version'; type = @{ name = 'number_integer' }; required = $true; validations = @(@{ name = 'max'; value = '1' }, @{ name = 'min'; value = '1' }) },
                @{ key = 'declared_section_count'; name = 'Declared section count'; type = @{ name = 'number_integer' }; required = $true; validations = @(@{ name = 'min'; value = '0' }, @{ name = 'max'; value = '2' }) },
                @{
                    key = 'sections'
                    name = 'Sections'
                    type = @{ name = 'list.mixed_reference' }
                    required = $false
                    validations = @(
                        @{ name = 'list.max'; value = '2' },
                        @{ name = 'metaobject_definition_ids'; value = '["gid://shopify/MetaobjectDefinition/2","gid://shopify/MetaobjectDefinition/1"]' }
                    )
                }
            )
            capabilities = @{ publishable = @{ enabled = $true } }
            access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        }
    )
    $getHomeState = {
        param($definitions)
        $fixtureTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectDefinitions = @{
                nodes = $definitions
                pageInfo = @{ hasNextPage = $false; endCursor = $null }
            } } } }
        }.GetNewClosure()
        Get-ShopifyHomeDefinitionState $binding 'fixture-admin-token' $fixtureTransport
    }.GetNewClosure()
    Assert-True `
        -Condition ((& $getHomeState $liveCompatibleDefinitions).Classification -ceq 'COMPATIBLE') `
        -Name 'live-normalized Home definitions map to the exact Gate 7 semantic contract'
    $equivalentReferenceOrder = Copy-TestValue $liveCompatibleDefinitions
    $equivalentReferenceOrder[2].fieldDefinitions[2].validations[1].value = '["gid://shopify/MetaobjectDefinition/1","gid://shopify/MetaobjectDefinition/2"]'
    Assert-True `
        -Condition (
            (& $getHomeState $liveCompatibleDefinitions).Fingerprint -ceq
            (& $getHomeState $equivalentReferenceOrder).Fingerprint
        ) `
        -Name 'mixed-reference ID ordering canonicalizes to one deterministic fingerprint'

    $wrongCardinality = Copy-TestValue $liveCompatibleDefinitions
    $wrongCardinality[0].fieldDefinitions[1].validations[0].value = '7'
    Assert-True `
        -Condition ((& $getHomeState $wrongCardinality).Classification -ceq 'INCOMPATIBLE') `
        -Name 'wrong list cardinality remains incompatible'

    $wrongValidationCase = Copy-TestValue $liveCompatibleDefinitions
    $wrongValidationCase[0].fieldDefinitions[1].validations[0].name = 'LIST.MAX'
    Assert-True `
        -Condition ((& $getHomeState $wrongValidationCase).Classification -ceq 'INCOMPATIBLE') `
        -Name 'provider validation names require the exact documented case'

    $wrongReference = Copy-TestValue $liveCompatibleDefinitions
    $wrongReference[2].fieldDefinitions[2].validations[1].value = '["gid://shopify/MetaobjectDefinition/1","gid://shopify/MetaobjectDefinition/9"]'
    Assert-True `
        -Condition ((& $getHomeState $wrongReference).Classification -ceq 'INCOMPATIBLE') `
        -Name 'wrong mixed-reference definition remains incompatible'

    $missingReference = Copy-TestValue $liveCompatibleDefinitions
    $missingReference[2].fieldDefinitions[2].validations[1].value = '["gid://shopify/MetaobjectDefinition/1"]'
    Assert-True `
        -Condition ((& $getHomeState $missingReference).Classification -ceq 'INCOMPATIBLE') `
        -Name 'missing mixed-reference definition remains incompatible'

    $extraReference = Copy-TestValue $liveCompatibleDefinitions
    $extraReference[2].fieldDefinitions[2].validations[1].value = '["gid://shopify/MetaobjectDefinition/1","gid://shopify/MetaobjectDefinition/2","gid://shopify/MetaobjectDefinition/9"]'
    Assert-True `
        -Condition ((& $getHomeState $extraReference).Classification -ceq 'INCOMPATIBLE') `
        -Name 'extra mixed-reference definition remains incompatible'

    $singleReferenceShape = Copy-TestValue $liveCompatibleDefinitions
    $singleReferenceShape[2].fieldDefinitions[2].validations[1] = @{
        name = 'metaobject_definition_id'
        value = 'gid://shopify/MetaobjectDefinition/1'
    }
    Assert-True `
        -Condition ((& $getHomeState $singleReferenceShape).Classification -ceq 'INCOMPATIBLE') `
        -Name 'single-reference validation is rejected for the Gate 7 mixed-reference field'

    $unknownValidation = Copy-TestValue $liveCompatibleDefinitions
    $unknownValidation[0].fieldDefinitions[0].validations += @{ name = 'regex'; value = '.*' }
    Assert-True `
        -Condition ((& $getHomeState $unknownValidation).Classification -ceq 'INCOMPATIBLE') `
        -Name 'unexpected Home validation names remain incompatible'

    $missingDefinition = @((Copy-TestValue $liveCompatibleDefinitions) | Where-Object { [string]$_.type -cne 'mobile_home_featured_product' })
    Assert-True `
        -Condition ((& $getHomeState $missingDefinition).Classification -ceq 'INCOMPATIBLE') `
        -Name 'reference to an absent child definition remains incompatible'

    $wrongAccess = Copy-TestValue $liveCompatibleDefinitions
    $wrongAccess[0].access.storefront = 'NONE'
    Assert-True `
        -Condition ((& $getHomeState $wrongAccess).Classification -ceq 'INCOMPATIBLE') `
        -Name 'incompatible Home Storefront access remains incompatible'

    $wrongPublishability = Copy-TestValue $liveCompatibleDefinitions
    $wrongPublishability[0].capabilities.publishable.enabled = $false
    Assert-True `
        -Condition ((& $getHomeState $wrongPublishability).Classification -ceq 'INCOMPATIBLE') `
        -Name 'incompatible Home publishability remains incompatible'

    $wrongFieldType = Copy-TestValue $liveCompatibleDefinitions
    $wrongFieldType[0].fieldDefinitions[0].type.name = 'multi_line_text_field'
    Assert-True `
        -Condition ((& $getHomeState $wrongFieldType).Classification -ceq 'INCOMPATIBLE') `
        -Name 'wrong existing Home field type blocks create-if-missing provisioning'

    $wrongRequiredness = Copy-TestValue $liveCompatibleDefinitions
    $wrongRequiredness[0].fieldDefinitions[0].required = $false
    Assert-True `
        -Condition ((& $getHomeState $wrongRequiredness).Classification -ceq 'INCOMPATIBLE') `
        -Name 'wrong existing Home requiredness remains incompatible'

    $missingChildDisplayName = Copy-TestValue $liveCompatibleDefinitions
    $missingChildDisplayName[0].displayNameKey = $null
    Assert-True `
        -Condition ((& $getHomeState $missingChildDisplayName).Classification -ceq 'INCOMPATIBLE') `
        -Name 'nullable display-name equivalence is not applied to title-based child definitions'

    $v2Schema = Import-ShopifyHomeSchemaContract -ContractId 'pilot-media-v2'
    $v2DefinitionIds = @{
        mobile_home_collection_grid = 'gid://shopify/MetaobjectDefinition/11'
        mobile_home_featured_product = 'gid://shopify/MetaobjectDefinition/12'
        mobile_home_image_v1 = 'gid://shopify/MetaobjectDefinition/13'
        mobile_home_video_v1 = 'gid://shopify/MetaobjectDefinition/14'
        mobile_home_v2 = 'gid://shopify/MetaobjectDefinition/15'
    }
    $liveV2CompatibleDefinitions = @(
        foreach ($contract in @($v2Schema.definitions)) {
            $input = ConvertTo-ShopifyHomeDefinitionCreateInput `
                -Contract $contract `
                -DefinitionIdsByType $v2DefinitionIds
            @{
                id = [string]$v2DefinitionIds[[string]$contract.type]
                type = [string]$contract.type
                name = [string]$input.name
                displayNameKey = if ($input.ContainsKey('displayNameKey')) { [string]$input.displayNameKey } else { $null }
                fieldDefinitions = @(
                    foreach ($field in @($input.fieldDefinitions)) {
                        @{
                            key = [string]$field.key
                            name = [string]$field.name
                            type = @{ name = [string]$field.type }
                            required = [bool]$field.required
                            validations = @($field.validations)
                        }
                    }
                )
                capabilities = @{ publishable = @{ enabled = $true } }
                access = @{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
            }
        }
    )
    $getV2HomeState = {
        param($definitions)
        $fixtureTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectDefinitions = @{
                nodes = $definitions
                pageInfo = @{ hasNextPage = $false; endCursor = $null }
            } } } }
        }.GetNewClosure()
        Get-ShopifyHomeDefinitionState `
            $binding 'fixture-admin-token' $fixtureTransport `
            -ContractId 'pilot-media-v2'
    }.GetNewClosure()
    $v2HomeState = & $getV2HomeState $liveV2CompatibleDefinitions
    Assert-True `
        -Condition (
            [string]$v2HomeState.Classification -ceq 'COMPATIBLE' -and
            (@($v2HomeState.ManagedTypes) -join ',') -ceq 'mobile_home_collection_grid,mobile_home_featured_product,mobile_home_image_v1,mobile_home_video_v1,mobile_home_v2'
        ) `
        -Name 'Home v2 provider definitions map to the exact pilot-media-v2 contract'
    $imageInput = @(
        $liveV2CompatibleDefinitions | Where-Object { [string]$_.type -ceq 'mobile_home_image_v1' }
    )[0]
    $presentationValidation = @(
        $imageInput.fieldDefinitions | Where-Object { [string]$_.key -ceq 'presentation' }
    )[0].validations
    $imageFileValidation = @(
        $imageInput.fieldDefinitions | Where-Object { [string]$_.key -ceq 'media' }
    )[0].validations
    Assert-True `
        -Condition (
            @($presentationValidation).Count -eq 1 -and
            [string]$presentationValidation[0].name -ceq 'choices' -and
            [string]$presentationValidation[0].value -ceq '["banner","photo"]' -and
            @($imageFileValidation).Count -eq 1 -and
            [string]$imageFileValidation[0].name -ceq 'file_type_options' -and
            [string]$imageFileValidation[0].value -ceq '["Image"]'
        ) `
        -Name 'Home v2 semantic media validations compile to documented Shopify provider names'
    $wrongV2FileType = Copy-TestValue $liveV2CompatibleDefinitions
    $wrongV2FileTypeField = @(
        ($wrongV2FileType | Where-Object { [string]$_.type -ceq 'mobile_home_video_v1' }).fieldDefinitions |
            Where-Object { [string]$_.key -ceq 'media' }
    )[0]
    $wrongV2FileTypeField.validations[0].value = '["IMAGE"]'
    Assert-True `
        -Condition ((& $getV2HomeState $wrongV2FileType).Classification -ceq 'INCOMPATIBLE') `
        -Name 'Home v2 rejects a provider media definition with the wrong file type'
    $v2Actions = & (Get-Module Onboarding.Operator) {
        param($HomeState)
        New-OnboardingExpectedActions `
            -HomeState $HomeState `
            -ProbeState ([pscustomobject]@{ Classification = 'ABSENT'; Fingerprint = ('0' * 64) })
    } ([pscustomobject]@{
        Classification = 'ABSENT'
        Fingerprint = ('1' * 64)
        MissingTypes = @($v2Schema.definitions.type)
        ManagedTypes = @($v2Schema.definitions.type)
    })
    Assert-True `
        -Condition ((@($v2Actions | ForEach-Object { [string]$_.resourceKey }) -join ',') -ceq (@($v2Schema.definitions.type) -join ',')) `
        -Name 'Home v2 definition plan preserves dependency order and keeps the root last'
    $legacyManagedTypes = & (Get-Module Onboarding.Operator) {
        param($HomeState)
        @(Get-OnboardingManagedHomeTypes -HomeState $HomeState)
    } ([pscustomobject]@{
        Classification = 'ABSENT'
        Fingerprint = ('1' * 64)
        MissingTypes = @('mobile_home_collection_grid', 'mobile_home_featured_product', 'mobile_home')
    })
    Assert-True `
        -Condition (($legacyManagedTypes -join ',') -ceq 'mobile_home_collection_grid,mobile_home_featured_product,mobile_home') `
        -Name 'legacy Home state fallback is shared by Plan and Apply definition ordering'

    $compatibleProbe = @{
        id = 'gid://shopify/Metaobject/99'
        type = 'mobile_home'
        handle = 'gate8-operator-acceptance-v1'
        fields = @(
            @{ key = 'schema_version'; value = '1' },
            @{ key = 'declared_section_count'; value = '0' }
        )
        capabilities = @{ publishable = @{ status = 'DRAFT' } }
    }
    $getProbeState = {
        param($probe)
        $fixtureTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectByHandle = $probe } } }
        }.GetNewClosure()
        Get-ShopifyAcceptanceProbeState $binding 'fixture-admin-token' $fixtureTransport
    }.GetNewClosure()
    Assert-True `
        -Condition ((& $getProbeState $compatibleProbe).Classification -ceq 'COMPATIBLE') `
        -Name 'DRAFT acceptance probe with only its two required fields remains compatible'

    $providerEmptySectionsProbe = Copy-TestValue $compatibleProbe
    $providerEmptySectionsProbe.fields += @{ key = 'sections'; value = $null }
    Assert-True `
        -Condition ((& $getProbeState $providerEmptySectionsProbe).Classification -ceq 'COMPATIBLE') `
        -Name 'DRAFT acceptance probe accepts the observed provider-null optional sections field'

    foreach ($invalidSections in @(
        @{ Name = 'non-empty'; Value = '["gid://shopify/Metaobject/12"]' },
        @{ Name = 'JSON empty array'; Value = '[]' },
        @{ Name = 'empty string'; Value = '' },
        @{ Name = 'whitespace'; Value = ' ' }
    )) {
        $invalidProbe = Copy-TestValue $compatibleProbe
        $invalidProbe.fields += @{ key = 'sections'; value = $invalidSections.Value }
        Assert-True `
            -Condition ((& $getProbeState $invalidProbe).Classification -ceq 'INCOMPATIBLE') `
            -Name "DRAFT acceptance probe rejects $($invalidSections.Name) optional sections representation"
    }

    $extraFieldProbe = Copy-TestValue $compatibleProbe
    $extraFieldProbe.fields += @{ key = 'unexpected'; value = '' }
    Assert-True `
        -Condition ((& $getProbeState $extraFieldProbe).Classification -ceq 'INCOMPATIBLE') `
        -Name 'DRAFT acceptance probe rejects arbitrary extra fields'

    $duplicateFieldProbe = Copy-TestValue $compatibleProbe
    $duplicateFieldProbe.fields += @{ key = 'schema_version'; value = '1' }
    Assert-True `
        -Condition ((& $getProbeState $duplicateFieldProbe).Classification -ceq 'INCOMPATIBLE') `
        -Name 'DRAFT acceptance probe rejects duplicate field keys'
    Assert-True `
        -Condition ((& $getProbeState @($compatibleProbe, $compatibleProbe)).Classification -ceq 'INCOMPATIBLE') `
        -Name 'DRAFT acceptance probe rejects conflicting duplicate current resources'

    foreach ($incompatibleMutation in @(
        @{ Name = 'ACTIVE state'; Apply = { param($probe) $probe.capabilities.publishable.status = 'ACTIVE' } },
        @{ Name = 'wrong schema version'; Apply = { param($probe) $probe.fields[0].value = '2' } },
        @{ Name = 'nonzero declared count'; Apply = { param($probe) $probe.fields[1].value = '1' } },
        @{ Name = 'wrong handle'; Apply = { param($probe) $probe.handle = 'primary' } },
        @{ Name = 'wrong type'; Apply = { param($probe) $probe.type = 'other_type' } },
        @{ Name = 'missing required field'; Apply = { param($probe) $probe.fields = @($probe.fields | Select-Object -Skip 1) } }
    )) {
        $invalidProbe = Copy-TestValue $compatibleProbe
        & $incompatibleMutation.Apply $invalidProbe
        Assert-True `
            -Condition ((& $getProbeState $invalidProbe).Classification -ceq 'INCOMPATIBLE') `
            -Name "DRAFT acceptance probe rejects $($incompatibleMutation.Name)"
    }
    $menuBodies = [Collections.Generic.List[string]]::new()
    $requestedMenu = @{
        id = 'gid://shopify/Menu/2'
        handle = 'main-menu'
        title = 'Requested menu with unrelated title'
        items = @()
    }
    $menuTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $menuBodies.Add([string]$body)
        $request = $body | ConvertFrom-Json -AsHashtable
        if ([string]$request.variables.after -ceq 'menu-page-2') {
            return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menus = @{
                nodes = @($requestedMenu)
                pageInfo = @{ hasNextPage = $false; endCursor = $null }
            } } } }
        }
        return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menus = @{
            nodes = @(@{
                id = 'gid://shopify/Menu/1'
                handle = 'different-handle'
                title = 'main-menu'
                items = @()
            })
            pageInfo = @{ hasNextPage = $true; endCursor = 'menu-page-2' }
        } } } }
    }.GetNewClosure()
    $menu = Get-ShopifyMenuState $binding 'fixture-admin-token' 'main-menu' $menuTransport
    $expectedMenuFingerprintJson = Get-OnboardingCanonicalJson ([ordered]@{ handle = 'main-menu'; menu = $requestedMenu })
    $expectedMenuFingerprint = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($expectedMenuFingerprintJson))
    ).ToLowerInvariant()
    Assert-True `
        -Condition (
            $menu.Classification -ceq 'CORRECT' -and
            $menu.Fingerprint -ceq $expectedMenuFingerprint -and
            $menuBodies.Count -eq 2 -and
            $menuBodies[0] -match 'Gate8Menus' -and
            $menuBodies[0] -match 'menus\(first:\$first,after:\$after\)' -and
            $menuBodies[0] -notmatch 'menu\(handle:' -and
            $menuBodies[0] -match '"first":100' -and
            $menuBodies[1] -match '"after":"menu-page-2"'
        ) `
        -Name 'Menu inspection enumerates bounded pages and selects only the exact requested handle'
    $ambiguousMenuTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menus = @{
            nodes = @($requestedMenu, $requestedMenu)
            pageInfo = @{ hasNextPage = $false; endCursor = $null }
        } } } }
    }.GetNewClosure()
    Assert-Throws `
        -Action { Get-ShopifyMenuState $binding 'fixture-admin-token' 'main-menu' $ambiguousMenuTransport } `
        -Pattern 'SHOPIFY_MENU_IDENTITY_CONFLICT' `
        -Name 'Menu inspection fails closed on ambiguous exact handle matches'
    $incompleteMenuTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $request = $body | ConvertFrom-Json -AsHashtable
        $page = if ($null -eq $request.variables.after) {
            1
        } else {
            [int]([string]$request.variables.after -replace '^menu-page-', '')
        }
        [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menus = @{
            nodes = @()
            pageInfo = @{ hasNextPage = $true; endCursor = 'menu-page-' + ($page + 1) }
        } } } }
    }
    Assert-Throws `
        -Action { Get-ShopifyMenuState $binding 'fixture-admin-token' 'main-menu' $incompleteMenuTransport } `
        -Pattern 'SHOPIFY_PAGINATION_INCOMPLETE' `
        -Name 'Menu inspection cannot infer absence after five incomplete pages'
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
        application = 'gurbakir'
        profile = 'development'
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
    $script:firebasePageHeaders = [Collections.Generic.List[object]]::new()
    $firebaseTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:firebasePageRequests.Add([string]$uri.Query)
        $script:firebasePageHeaders.Add($headers)
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
    Assert-True `
        -Condition (
            $script:firebasePageHeaders.Count -eq 2 -and
            [string]$script:firebasePageHeaders[0]['x-goog-user-project'] -ceq 'fixture-project-123' -and
            [string]$script:firebasePageHeaders[1]['x-goog-user-project'] -ceq 'fixture-project-123'
        ) `
        -Name 'Firebase development inspection bills only the independently bound development project'

    $stagingSelected = $stagingSelection
    $stagingFirebaseBinding = @{
        application = 'gurbakir'
        profile = 'staging'
        firebase = @{
            projectId = 'fixture-staging-456'
            projectNumber = '987654321098'
            androidAppIdsByVariant = @{
                stagingDebug = '1:987654321098:android:0123456789abcdef'
                stagingRelease = '1:987654321098:android:fedcba9876543210'
            }
        }
    }
    $script:stagingFirebaseHeaders = [Collections.Generic.List[object]]::new()
    $stagingFirebaseTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:stagingFirebaseHeaders.Add($headers)
        [pscustomobject]@{ StatusCode = 200; Data = @{
            apps = @(
                @{ appId = '1:987654321098:android:0123456789abcdef'; packageName = 'com.gurbakir.mobile.staging.debug' },
                @{ appId = '1:987654321098:android:fedcba9876543210'; packageName = 'com.gurbakir.mobile.staging' }
            )
        } }
    }
    $stagingFirebase = Get-OnboardingFirebaseState `
        $stagingSelected `
        $stagingFirebaseBinding `
        'fixture-firebase-token' `
        $stagingFirebaseTransport
    Assert-True `
        -Condition (
            $stagingFirebase.Classification -ceq 'PASS' -and
            $script:stagingFirebaseHeaders.Count -eq 1 -and
            [string]$script:stagingFirebaseHeaders[0]['x-goog-user-project'] -ceq 'fixture-staging-456'
        ) `
        -Name 'Firebase staging inspection bills only the independently bound staging project'

    $crossProfileFirebaseBinding = @{
        application = 'gurbakir'
        profile = 'staging'
        firebase = $firebaseBinding.firebase
    }
    Assert-Throws `
        -Action {
            Get-OnboardingFirebaseState `
                $selected `
                $crossProfileFirebaseBinding `
                'fixture-firebase-token' `
                $firebaseTransport
        } `
        -Pattern 'FIREBASE_BINDING_TARGET_MISMATCH' `
        -Name 'Firebase inspection rejects a provider binding selected for another profile'

    $previousQuotaProject = [Environment]::GetEnvironmentVariable('GOOGLE_CLOUD_QUOTA_PROJECT', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('GOOGLE_CLOUD_QUOTA_PROJECT', 'attacker-project-999', 'Process')
        $script:injectionFirebaseHeaders = [Collections.Generic.List[object]]::new()
        $injectionFirebaseTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $script:injectionFirebaseHeaders.Add($headers)
            & $firebaseTransport $method $uri $headers $body $maximumBytes
        }
        [void](Get-OnboardingFirebaseState $selected $firebaseBinding 'fixture-firebase-token' $injectionFirebaseTransport)
        Assert-True `
            -Condition (
                $script:injectionFirebaseHeaders.Count -gt 0 -and
                [string]$script:injectionFirebaseHeaders[0]['x-goog-user-project'] -ceq 'fixture-project-123'
            ) `
            -Name 'Firebase inspection ignores caller-controlled quota-project environment overrides'
    } finally {
        [Environment]::SetEnvironmentVariable('GOOGLE_CLOUD_QUOTA_PROJECT', $previousQuotaProject, 'Process')
    }

    $firebaseIdentityMismatchTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @{
            apps = @(
                @{ appId = '1:123456789012:android:0123456789abcdef'; packageName = 'com.attacker.mobile' },
                @{ appId = '1:123456789012:android:fedcba9876543210'; packageName = 'com.gurbakir.mobile.dev' }
            )
        } }
    }
    Assert-Throws `
        -Action {
            Get-OnboardingFirebaseState `
                $selected `
                $firebaseBinding `
                'fixture-firebase-token' `
                $firebaseIdentityMismatchTransport
        } `
        -Pattern 'FIREBASE_APP_IDENTITY_MISMATCH' `
        -Name 'Firebase quota-project routing preserves exact app identity matching'
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
        -Condition ((Get-OnboardingAssetLinksState $selected $matchingAssetLinksTransport).Classification -ceq 'PARTIAL') `
        -Name 'one enrolled variant does not overstate full App Links coverage'
    $lowercaseAssetLinksTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @(@{
            relation = @('delegate_permission/common.handle_all_urls')
            target = @{ namespace = 'android_app'; package_name = 'com.gurbakir.mobile.dev.debug'; sha256_cert_fingerprints = @('aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa:aa') }
        }) }
    }
    Assert-True `
        -Condition ((Get-OnboardingAssetLinksState $selected $lowercaseAssetLinksTransport).Classification -ceq 'PARTIAL') `
        -Name 'lowercase certificate syntax is normalized without overstating partial package coverage'

    $allAssetLinksTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        [pscustomobject]@{ StatusCode = 200; Data = @(
            @{
                relation = @('delegate_permission/common.handle_all_urls')
                target = @{ namespace = 'android_app'; package_name = 'com.gurbakir.mobile.dev.debug'; sha256_cert_fingerprints = @('AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA') }
            },
            @{
                relation = @('delegate_permission/common.handle_all_urls')
                target = @{ namespace = 'android_app'; package_name = 'com.gurbakir.mobile.dev'; sha256_cert_fingerprints = @('BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB') }
            }
        ) }
    }
    Assert-True `
        -Condition ((Get-OnboardingAssetLinksState $selected $allAssetLinksTransport).Classification -ceq 'NOT_VERIFIED') `
        -Name 'shape-valid complete App Links statements remain unverified without trusted signing fingerprints'
    $trustedFingerprints = @{
        'com.gurbakir.mobile.dev.debug' = @('AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA:AA')
        'com.gurbakir.mobile.dev' = @('BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB:BB')
    }
    Assert-True `
        -Condition ((Get-OnboardingAssetLinksState $selected $allAssetLinksTransport $trustedFingerprints).Classification -ceq 'PASS') `
        -Name 'App Links PASS requires complete package coverage and exact trusted certificate fingerprints'

    $multiHostSelected = Copy-TestValue $selected
    $multiHostSelected.Application.identity.webRoles.productAppLink.origin = 'https://products.gurbakir.com'
    $multiHostSelected.Application.identity.webRoles.orderAppLink.origin = 'https://orders.gurbakir.com'
    $assetLinksHosts = [Collections.Generic.List[string]]::new()
    $multiHostTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $assetLinksHosts.Add([string]$uri.Host)
        & $allAssetLinksTransport $method $uri $headers $body $maximumBytes
    }.GetNewClosure()
    $multiHostState = Get-OnboardingAssetLinksState $multiHostSelected $multiHostTransport
    Assert-True `
        -Condition (
            [string]$multiHostState.Classification -ceq 'NOT_VERIFIED' -and
            (@($assetLinksHosts | Sort-Object -Unique) -join ',') -ceq 'gurbakir.com,orders.gurbakir.com,products.gurbakir.com'
        ) `
        -Name 'App Links inspection queries every distinct declared application-link host'

    $disabledAssetLinksState = Get-OnboardingAssetLinksState $syntheticSelection { throw 'disabled App Links must not issue a request' }
    Assert-True `
        -Condition ([string]$disabledAssetLinksState.Classification -ceq 'NOT_APPLICABLE') `
        -Name 'disabled App Links are not applicable and perform no public request'
    Assert-True -Condition ((Protect-OnboardingOutput 'failure fixture-secret' @('fixture-secret')) -ceq 'failure <redacted>') -Name 'loaded values are redacted before output'
}

function Invoke-OperatorApplySuite {
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Operator.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Common.psm1') -Force
    $receiptTimeWindow = & (Get-Module Onboarding.Operator) {
        param([DateTimeOffset]$Now)
        New-OnboardingReceiptTimeWindow -Now $Now
    } ([DateTimeOffset]::Parse('2026-09-14T12:00:00.9999999Z', [Globalization.CultureInfo]::InvariantCulture))
    Assert-True `
        -Condition (
            [string]$receiptTimeWindow.CreatedAtUtc -ceq '2026-09-14T12:00:00Z' -and
            [string]$receiptTimeWindow.ExpiresAtUtc -ceq '2026-09-14T12:15:00Z'
        ) `
        -Name 'Plan timestamps share one whole-second clock sample and an exact fifteen-minute window'
    $temporaryRoot = Join-Path $repoRoot ('out\onboarding\gate8-apply-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $bindingPath = Join-Path $repoRoot 'config\local\gurbakir\development.providers.json'
    $stagingBindingPath = Join-Path $repoRoot 'config\local\gurbakir\staging.providers.json'
    $localConfigurationPath = Join-Path $repoRoot 'config\local\gurbakir\development.properties'
    $stagingLocalConfigurationPath = Join-Path $repoRoot 'config\local\gurbakir\staging.properties'
    $manualCheckpointPath = Join-Path $temporaryRoot 'manual-checkpoint.json'
    $bindingSnapshot = Get-TestFileSnapshot -Path $bindingPath
    $stagingBindingSnapshot = Get-TestFileSnapshot -Path $stagingBindingPath
    $localConfigurationSnapshot = Get-TestFileSnapshot -Path $localConfigurationPath
    $stagingLocalConfigurationSnapshot = Get-TestFileSnapshot -Path $stagingLocalConfigurationPath
    [IO.Directory]::CreateDirectory((Split-Path -Parent $bindingPath)) | Out-Null
    $bindingJson = '{"schemaVersion":1,"application":"gurbakir","profile":"development","approvedEvidenceRef":"owner-evidence:fixture-apply","shopify":{"adminShopDomain":"fixture-shop.myshopify.com","shopId":"1234567890"},"firebase":{"projectId":"fixture-project-123","projectNumber":"123456789012","androidAppIdsByVariant":{"developmentDebug":"1:123456789012:android:0123456789abcdef","developmentRelease":"1:123456789012:android:fedcba9876543210"}}}'
    $credentialName = 'MB_GURBAKIR_DEVELOPMENT_SHOPIFY_ADMIN_TOKEN'
    $stagingCredentialName = 'MB_GURBAKIR_STAGING_SHOPIFY_ADMIN_TOKEN'
    $previous = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
    $previousStaging = [Environment]::GetEnvironmentVariable($stagingCredentialName, 'Process')
    $script:createdDefinitions = [Collections.Generic.List[object]]::new()
    $script:definitionInputs = [Collections.Generic.List[object]]::new()
    $script:probe = $null
    $script:menuNodes = @(@{
        id='gid://shopify/Menu/77'
        handle='main-menu'
        title='Fixture menu'
        items=@()
    })
    $script:writeCount = 0
    $script:lockObserved = $false
    $clientValues=[ordered]@{
        'shopify.storefrontPublicToken'='fixture-public-token';'shopify.customerAccountClientId'='fixture-client-id';
        'shopify.customerAccountIssuer'='https://shopify.com/authentication/1234567890';
        'shopify.customerAccountAuthorizationEndpoint'='https://shopify.com/auth';'shopify.customerAccountTokenEndpoint'='https://shopify.com/token';
        'shopify.customerAccountLogoutEndpoint'='https://shopify.com/logout';'shopify.customerAccountGraphqlEndpoint'='https://shopify.com/customer-account/api/2026-07/graphql';
        'shopify.customerAccountRedirectUri'='shop.1234567890.gurbakir://oauth/callback'
    }
    $transport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $request = $body | ConvertFrom-Json -AsHashtable
        if ($request.query -match 'Gate8VerifyShop') {
            return [pscustomobject]@{ StatusCode=200; Data=@{ data=@{ shop=@{ id='gid://shopify/Shop/1234567890'; myshopifyDomain='fixture-shop.myshopify.com' } } } }
        }
        if ($request.query -match 'Gate8VerifyStorefrontShop') {
            return [pscustomobject]@{ StatusCode=200; Data=@{ data=@{ shop=@{ id='gid://shopify/Shop/1234567890'; primaryDomain=@{host='gurbakir.com'} } } } }
        }
        if ($request.query -match 'Gate8HomeDefinitions') {
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitions=@{nodes=@($script:createdDefinitions);pageInfo=@{hasNextPage=$false;endCursor=$null}}}}}
        }
        if ($request.query -match 'Gate8Menus') {
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{menus=@{
                nodes=@($script:menuNodes)
                pageInfo=@{hasNextPage=$false;endCursor=$null}
            }}}}
        }
        if ($request.query -match 'query Gate8Probe') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectByHandle=$script:probe}}} }
        if ($request.query -match 'Gate8DefinitionCreate') {
            $script:lockObserved = Test-Path -LiteralPath (Join-Path $repoRoot 'out\onboarding\locks\shop-1234567890.lock')
            $script:writeCount++;$definition=$request.variables.definition;$id="gid://shopify/MetaobjectDefinition/$($script:writeCount)"
            $script:definitionInputs.Add($definition)
            $fields=@(
                foreach($field in @($definition.fieldDefinitions)){
                    @{key=[string]$field.key;name=[string]$field.name;type=@{name=[string]$field.type};required=[bool]$field.required;validations=@($field.validations)}
                }
            )
            if ($definition.access.Contains('admin')) { throw 'fixture detected forbidden explicit Admin access input' }
            $displayNameKey = if ($definition.Contains('displayNameKey')) { [string]$definition.displayNameKey } else { $null }
            $node=@{id=$id;type=[string]$definition.type;name=[string]$definition.name;displayNameKey=$displayNameKey;fieldDefinitions=$fields;capabilities=@{publishable=@{enabled=$true}};access=@{admin='PUBLIC_READ_WRITE';storefront='PUBLIC_READ'}};$script:createdDefinitions.Add($node)
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitionCreate=@{metaobjectDefinition=@{id=$id;type=[string]$definition.type};userErrors=@()}}}}
        }
        if ($request.query -match 'Gate8ProbeCreate') {$script:writeCount++;$script:probe=@{id='gid://shopify/Metaobject/99';type='mobile_home';handle='gate8-operator-acceptance-v1';fields=@(@{key='schema_version';value='1'},@{key='declared_section_count';value='0'});capabilities=@{publishable=@{status='DRAFT'}}};return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectCreate=@{metaobject=$script:probe;userErrors=@()}}}}}
        throw 'unexpected fixture operation'
    }
    $outsideReceipt = $null
    try {
        [IO.File]::WriteAllText($bindingPath, $bindingJson, [Text.UTF8Encoding]::new($false))
        [Environment]::SetEnvironmentVariable($credentialName, 'fixture-admin-token', 'Process')
        $context = Get-OnboardingOperatorContext $repoRoot 'gurbakir' 'development'
        Write-OnboardingPrivateProperties `
            -RepositoryRoot $repoRoot `
            -Destination $localConfigurationPath `
            -Lines (Get-OnboardingValidatedClientConfigurationLines -Context $context -Values $clientValues) `
            -RefuseOverwrite
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
                @{ key = 'collections'; name = 'Collections'; type = @{ name = 'list.collection_reference' }; required = $true; validations = @(@{ name = 'list.min'; value = '1' }, @{ name = 'list.max'; value = '6' }) }
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
            if ($request.query -match 'Gate8VerifyStorefrontShop') {
                return [pscustomobject]@{StatusCode=200;Data=@{data=@{shop=@{id='gid://shopify/Shop/1234567890';primaryDomain=@{host='gurbakir.com'}}}}}
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

        $wrongStorefrontPlanPath = Join-Path $temporaryRoot 'wrong-storefront-plan.json'
        $wrongStorefrontTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $request = $body | ConvertFrom-Json -AsHashtable
            if ($request.query -match 'Gate8VerifyStorefrontShop') {
                return [pscustomobject]@{StatusCode=200;Data=@{data=@{shop=@{id='gid://shopify/Shop/9999999999';primaryDomain=@{host='gurbakir.com'}}}}}
            }
            & $transport $method $uri $headers $body $maximumBytes
        }.GetNewClosure()
        Assert-Throws `
            -Action { New-OnboardingPlan $repoRoot 'gurbakir' 'development' $wrongStorefrontPlanPath -Transport $wrongStorefrontTransport } `
            -Pattern 'SHOPIFY_STOREFRONT_IDENTITY_MISMATCH' `
            -Name 'Plan fails before provider actions when Admin and mobile Storefront identities target different shops'

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
            if ($request.query -match 'Gate8VerifyStorefrontShop') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{shop=@{id='gid://shopify/Shop/1234567890';primaryDomain=@{host='gurbakir.com'}}}}} }
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

        $finalDriftPlanPath = Join-Path $temporaryRoot 'final-drift-plan.json'
        $finalDriftResultPath = Join-Path $temporaryRoot 'final-drift-result.json'
        [void](New-OnboardingPlan $repoRoot 'gurbakir' 'development' $finalDriftPlanPath -Transport $transport)
        $script:finalDriftHomeReads = 0
        $finalDriftTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $request = $body | ConvertFrom-Json -AsHashtable
            $response = & $transport $method $uri $headers $body $maximumBytes
            if ($request.query -match 'Gate8HomeDefinitions') {
                $script:finalDriftHomeReads++
                if ($script:finalDriftHomeReads -ge 8) {
                    $nodes = @(
                        $response.Data.data.metaobjectDefinitions.nodes | ForEach-Object {
                            (($_ | ConvertTo-Json -Depth 32 -Compress) | ConvertFrom-Json -AsHashtable -Depth 32)
                        }
                    )
                    $nodes[0].access.storefront = 'NONE'
                    $response.Data.data.metaobjectDefinitions.nodes = $nodes
                }
            }
            return $response
        }.GetNewClosure()
        Assert-True `
            -Condition ($finalDriftTransport.ToString() -notmatch '\bCopy-TestValue\b') `
            -Name 'module-invoked drift transport is self-contained across PowerShell session states'
        Assert-Throws `
            -Action {
                Invoke-OnboardingApply `
                    $repoRoot 'gurbakir' 'development' $finalDriftPlanPath 'gurbakir' 'development' `
                    -ConfirmApply -OutputPath $finalDriftResultPath -Transport $finalDriftTransport
            } `
            -Pattern 'PARTIAL_APPLY' `
            -Name 'Apply fails closed when final Home readback becomes incompatible after provider writes'
        Assert-True `
            -Condition (-not (Test-Path -LiteralPath $finalDriftResultPath)) `
            -Name 'post-write final readback drift never produces a successful RESULT receipt'
        $finalDriftRecovery = Import-OnboardingReceipt "$finalDriftPlanPath.recovery-final.json"
        Assert-True `
            -Condition (
                [string]$finalDriftRecovery.kind -ceq 'RECOVERY' -and
                [string]$finalDriftRecovery.overallStatus -ceq 'PARTIAL' -and
                'READBACK_MISMATCH' -in @($finalDriftRecovery.diagnosticCodes)
            ) `
            -Name 'post-write final readback drift preserves explicit partial recovery evidence'

        $script:createdDefinitions.Clear()
        $script:definitionInputs.Clear()
        $script:writeCount = 0

        [void](Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $planPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -OutputPath $resultPath -Transport $transport)
        Assert-True -Condition ($script:writeCount -eq 4) -Name 'Apply creates only three definitions and one DRAFT probe'
        $collectionGridInput = @($script:definitionInputs | Where-Object { [string]$_.type -ceq 'mobile_home_collection_grid' })[0]
        $collectionValidations = @($collectionGridInput.fieldDefinitions | Where-Object { [string]$_.key -ceq 'collections' })[0].validations
        $rootInput = @($script:definitionInputs | Where-Object { [string]$_.type -ceq 'mobile_home' })[0]
        $rootValidations = @($rootInput.fieldDefinitions | Where-Object { [string]$_.key -ceq 'sections' })[0].validations
        $rootDefinitionIdsValidation = @(
            $rootValidations | Where-Object { [string]$_.name -ceq 'metaobject_definition_ids' }
        )[0]
        $rootDefinitionIds = @([string]$rootDefinitionIdsValidation.value | ConvertFrom-Json)
        Assert-True `
            -Condition (
                (@($collectionValidations.name | Sort-Object -CaseSensitive) -join ',') -ceq 'list.max,list.min' -and
                (@($rootValidations.name | Sort-Object -CaseSensitive) -join ',') -ceq 'list.max,metaobject_definition_ids' -and
                (@($rootDefinitionIds | Sort-Object -CaseSensitive) -join ',') -ceq 'gid://shopify/MetaobjectDefinition/1,gid://shopify/MetaobjectDefinition/2' -and
                -not $rootInput.Contains('displayNameKey')
            ) `
            -Name 'Apply maps Gate 7 semantic intent to Shopify definition-create representation'
        Assert-True -Condition $script:lockObserved -Name 'Apply holds a verified-shop local operation lock while mutating'
        Assert-True -Condition ((Import-OnboardingReceipt $resultPath).kind -ceq 'RESULT') -Name 'Apply writes a closed redacted result receipt'

        $emptyPlanPath = Join-Path $temporaryRoot 'empty-plan.json'
        $emptyResultPath = Join-Path $temporaryRoot 'empty-result.json'
        $emptyPlan = New-OnboardingPlan $repoRoot 'gurbakir' 'development' $emptyPlanPath -Transport $transport
        Assert-True `
            -Condition ($null -ne $emptyPlan.actions -and @($emptyPlan.actions).Count -eq 0) `
            -Name 'Plan preserves a real empty actions array when Home is compatible and probe is not selected'
        $writesBeforeEmptyApply = $script:writeCount
        $emptyResult = Invoke-OnboardingApply `
            $repoRoot 'gurbakir' 'development' $emptyPlanPath 'gurbakir' 'development' `
            -ConfirmApply -OutputPath $emptyResultPath -Transport $transport
        Assert-True `
            -Condition (
                [string]$emptyResult.kind -ceq 'RESULT' -and
                [string]$emptyResult.overallStatus -ceq 'SUCCEEDED' -and
                @($emptyResult.actions).Count -eq 0 -and
                $script:writeCount -eq $writesBeforeEmptyApply
            ) `
            -Name 'zero-action Apply succeeds without probe opt-in and performs no provider writes'

        $noWriteDriftPlanPath = Join-Path $temporaryRoot 'no-write-final-drift-plan.json'
        $noWriteDriftResultPath = Join-Path $temporaryRoot 'no-write-final-drift-result.json'
        [void](New-OnboardingPlan $repoRoot 'gurbakir' 'development' $noWriteDriftPlanPath -Transport $transport)
        $script:noWriteDriftHomeReads = 0
        $noWriteDriftTransport = {
            param($method, $uri, $headers, $body, $maximumBytes)
            $request = $body | ConvertFrom-Json -AsHashtable
            $response = & $transport $method $uri $headers $body $maximumBytes
            if ($request.query -match 'Gate8HomeDefinitions') {
                $script:noWriteDriftHomeReads++
                if ($script:noWriteDriftHomeReads -ge 2) {
                    $nodes = @(
                        $response.Data.data.metaobjectDefinitions.nodes | ForEach-Object {
                            (($_ | ConvertTo-Json -Depth 32 -Compress) | ConvertFrom-Json -AsHashtable -Depth 32)
                        }
                    )
                    $nodes[0].access.storefront = 'NONE'
                    $response.Data.data.metaobjectDefinitions.nodes = $nodes
                }
            }
            return $response
        }.GetNewClosure()
        Assert-True `
            -Condition ($noWriteDriftTransport.ToString() -notmatch '\bCopy-TestValue\b') `
            -Name 'zero-write drift transport is self-contained across PowerShell session states'
        $writesBeforeNoWriteDrift = $script:writeCount
        Assert-Throws `
            -Action {
                Invoke-OnboardingApply `
                    $repoRoot 'gurbakir' 'development' $noWriteDriftPlanPath 'gurbakir' 'development' `
                    -ConfirmApply -OutputPath $noWriteDriftResultPath -Transport $noWriteDriftTransport
            } `
            -Pattern 'READBACK_MISMATCH' `
            -Name 'zero-action Apply fails without success evidence when final Home readback drifts'
        Assert-True `
            -Condition (
                -not (Test-Path -LiteralPath $noWriteDriftResultPath) -and
                -not (Test-Path -LiteralPath "$noWriteDriftPlanPath.recovery-final.json") -and
                $script:writeCount -eq $writesBeforeNoWriteDrift
            ) `
            -Name 'pre-write final readback drift emits neither success nor misleading partial-write recovery evidence'

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

        $predecessorReceiptPath = Join-Path $temporaryRoot 'pre-audit-operator-result.json'
        $predecessorReceipt = Import-OnboardingReceipt $resultPath
        $predecessorReceipt.digests.operatorSha256 = '0f3e05c72969a0cd47606537691724a417688afd0f8fe4d18b2b1ed0a59321e8'
        Write-OnboardingReceipt $predecessorReceiptPath $predecessorReceipt
        $predecessorPlanPath = Join-Path $temporaryRoot 'pre-audit-operator-plan.json'
        $predecessorPlan = New-OnboardingPlan `
            $repoRoot 'gurbakir' 'development' $predecessorPlanPath `
            -IncludeAcceptanceProbe -PriorReceipt $predecessorReceiptPath -Transport $transport
        Assert-True `
            -Condition (
                @($predecessorPlan.actions).Count -eq 1 -and
                [string]$predecessorPlan.actions[0].intendedAction -ceq 'NONE'
            ) `
            -Name 'explicit one-generation operator digest transition preserves prior successful probe attribution'

        $untrustedDigestReceiptPath = Join-Path $temporaryRoot 'untrusted-operator-result.json'
        $untrustedDigestReceipt = Import-OnboardingReceipt $resultPath
        $untrustedDigestReceipt.digests.operatorSha256 = ('9' * 64)
        Write-OnboardingReceipt $untrustedDigestReceiptPath $untrustedDigestReceipt
        Assert-Throws `
            -Action {
                New-OnboardingPlan `
                    $repoRoot 'gurbakir' 'development' (Join-Path $temporaryRoot 'untrusted-operator-plan.json') `
                    -IncludeAcceptanceProbe -PriorReceipt $untrustedDigestReceiptPath -Transport $transport
            } `
            -Pattern 'PROBE_COLLISION' `
            -Name 'arbitrary stale operator digests remain unable to attribute an existing probe'

        $successfulProbe = Copy-TestValue $script:probe
        $script:probe = $null
        $historicalPlanSourcePath = Join-Path $temporaryRoot 'historical-source-plan.json'
        $historicalPlan = New-OnboardingPlan `
            $repoRoot 'gurbakir' 'development' $historicalPlanSourcePath `
            -IncludeAcceptanceProbe -Transport $transport
        $historicalPlan.digests.operatorSha256 = 'b504b2102d6edec9556f347ef38b4bc41ff259a39dedd3691577d3b46b323f57'
        $historicalEvidence = Write-TestProbeRecoveryEvidence `
            -Plan $historicalPlan `
            -Directory $temporaryRoot `
            -Name 'historical-partial-probe-create'
        $script:probe = Copy-TestValue $successfulProbe
        $script:probe.fields += @{ key = 'sections'; value = $null }
        $writesBeforeRecovery = $script:writeCount
        $recoveredResultPath = Join-Path $temporaryRoot 'recovered-probe-result.json'
        $recoveredResult = Invoke-OnboardingProbeRecovery `
            -RepositoryRoot $repoRoot `
            -Application 'gurbakir' `
            -Profile 'development' `
            -PlanReceipt $historicalEvidence.PlanPath `
            -RecoveryReceipt $historicalEvidence.RecoveryPath `
            -OutputPath $recoveredResultPath `
            -Transport $transport
        $recoveredAction = @($recoveredResult.actions)[0]
        $currentOperatorDigest = Get-OnboardingOperatorDigest $repoRoot
        Assert-True `
            -Condition (
                [string]$recoveredResult.kind -ceq 'RESULT' -and
                [string]$recoveredResult.overallStatus -ceq 'SUCCEEDED' -and
                [string]$recoveredResult.digests.operatorSha256 -ceq $currentOperatorDigest -and
                [string]$recoveredResult.digests.operatorSha256 -cne [string]$historicalPlan.digests.operatorSha256 -and
                [string]$recoveredAction.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE' -and
                [string]$recoveredAction.status -ceq 'SUCCEEDED' -and
                [string]$recoveredAction.providerResourceId -ceq 'gid://shopify/Metaobject/99'
            ) `
            -Name 'exact historical partial probe creation recovers to current attributable evidence'
        Assert-True `
            -Condition ($script:writeCount -eq $writesBeforeRecovery) `
            -Name 'probe recovery attribution performs zero Shopify writes'
        Assert-Throws `
            -Action {
                Invoke-OnboardingProbeRecovery `
                    -RepositoryRoot $repoRoot `
                    -Application 'gurbakir' `
                    -Profile 'development' `
                    -PlanReceipt $historicalEvidence.PlanPath `
                    -RecoveryReceipt $historicalEvidence.RecoveryPath `
                    -OutputPath $recoveredResultPath `
                    -Transport $transport
            } `
            -Pattern 'PROBE_RECOVERY_OUTPUT_EXISTS' `
            -Name 'probe recovery preserves existing evidence instead of overwriting it'
        $immutableEvidencePath = Join-Path $temporaryRoot 'immutable-recovery-evidence.json'
        [IO.File]::WriteAllText($immutableEvidencePath, 'preserve-me', [Text.UTF8Encoding]::new($false))
        Assert-Throws `
            -Action { Write-OnboardingReceipt $immutableEvidencePath $recoveredResult -NoOverwrite } `
            -Pattern 'RECEIPT_ALREADY_EXISTS' `
            -Name 'no-overwrite receipt write rejects an existing destination'
        Assert-True `
            -Condition ([IO.File]::ReadAllText($immutableEvidencePath) -ceq 'preserve-me') `
            -Name 'no-overwrite receipt write preserves existing bytes'

        $recoveredPlanPath = Join-Path $temporaryRoot 'recovered-plan.json'
        $recoveredApplyPath = Join-Path $temporaryRoot 'recovered-apply.json'
        $recoveredPlan = New-OnboardingPlan `
            $repoRoot 'gurbakir' 'development' $recoveredPlanPath `
            -IncludeAcceptanceProbe -PriorReceipt $recoveredResultPath -Transport $transport
        Assert-True `
            -Condition (
                @($recoveredPlan.actions).Count -eq 1 -and
                [string]@($recoveredPlan.actions)[0].intendedAction -ceq 'NONE'
            ) `
            -Name 'recovered evidence permits a fresh Plan with an attributed probe no-op'
        [void](Invoke-OnboardingApply `
            $repoRoot 'gurbakir' 'development' $recoveredPlanPath 'gurbakir' 'development' `
            -ConfirmApply -IncludeAcceptanceProbe -PriorReceipt $recoveredResultPath `
            -OutputPath $recoveredApplyPath -Transport $transport)
        Assert-True `
            -Condition ($script:writeCount -eq $writesBeforeRecovery) `
            -Name 'Apply using recovered probe attribution performs zero Shopify writes'

        foreach ($hostileEvidence in @(
            @{
                Name = 'wrong-shop-id'
                Pattern = 'PROBE_RECOVERY_TARGET_MISMATCH'
                MutatePlan = { param($receipt) $receipt.verifiedTarget.shopId = '9999999999' }
            },
            @{
                Name = 'wrong-shop-domain'
                Pattern = 'PROBE_RECOVERY_TARGET_MISMATCH'
                MutatePlan = { param($receipt) $receipt.verifiedTarget.adminShopDomain = 'other-shop.myshopify.com' }
            },
            @{
                Name = 'wrong-application-profile-group'
                Pattern = 'PROBE_RECOVERY_TARGET_MISMATCH'
                MutatePlan = {
                    param($receipt)
                    $receipt.application = 'synthetic'
                    $receipt.profile = 'conformance'
                    $receipt.releaseBoundary = 'never-production'
                }
            },
            @{
                Name = 'wrong-profile-even-with-shared-resource-group'
                Pattern = 'PROBE_RECOVERY_TARGET_MISMATCH'
                MutatePlan = {
                    param($receipt)
                    $receipt.profile = 'staging'
                    $receipt.runtimeEnvironment = 'STAGING'
                }
            },
            @{
                Name = 'prior-plan-not-absent'
                Pattern = 'PROBE_RECOVERY_ACTION_MISMATCH'
                MutatePlan = { param($receipt) @($receipt.actions)[0].beforeClassification = 'COMPATIBLE' }
            },
            @{
                Name = 'wrong-resource-kind-key'
                Pattern = 'PROBE_RECOVERY_ACTION_MISMATCH'
                MutatePlan = {
                    param($receipt)
                    $action = @($receipt.actions)[0]
                    $action.resourceKind = 'SHOPIFY_HOME_DEFINITION'
                    $action.resourceKey = 'mobile_home'
                    $action.managementMode = 'CREATE_IF_MISSING'
                }
            },
            @{
                Name = 'wrong-intended-action'
                Pattern = 'PROBE_RECOVERY_ACTION_MISMATCH'
                MutatePlan = {
                    param($receipt)
                    $action = @($receipt.actions)[0]
                    $action.intendedAction = 'NONE'
                    $action.status = 'NO_OP'
                }
            },
            @{
                Name = 'unknown-operator-digest'
                Pattern = 'PROBE_RECOVERY_CONTRACT_MISMATCH'
                MutatePlan = { param($receipt) $receipt.digests.operatorSha256 = ('0' * 64) }
            },
            @{
                Name = 'tampered-provider-binding-digest'
                Pattern = 'PROBE_RECOVERY_CONTRACT_MISMATCH'
                MutatePlan = { param($receipt) $receipt.digests.providerBindingSha256 = ('0' * 64) }
            },
            @{
                Name = 'unrelated-recovery-record'
                Pattern = 'PROBE_RECOVERY_EVIDENCE_MISMATCH'
                MutateRecovery = {
                    param($receipt)
                    $receipt.recovery[0].resourceKind = 'SHOPIFY_HOME_DEFINITION'
                    $receipt.recovery[0].resourceKey = 'mobile_home'
                }
            },
            @{
                Name = 'tampered-recovery-action'
                Pattern = 'PROBE_RECOVERY_EVIDENCE_MISMATCH'
                MutateRecovery = { param($receipt) @($receipt.actions)[0].beforeFingerprint = ('0' * 64) }
            }
        )) {
            $mutatePlan = if ($hostileEvidence.ContainsKey('MutatePlan')) { $hostileEvidence.MutatePlan } else { $null }
            $mutateRecovery = if ($hostileEvidence.ContainsKey('MutateRecovery')) { $hostileEvidence.MutateRecovery } else { $null }
            $evidence = Write-TestProbeRecoveryEvidence `
                -Plan $historicalPlan `
                -Directory $temporaryRoot `
                -Name $hostileEvidence.Name `
                -MutatePlan $mutatePlan `
                -MutateRecovery $mutateRecovery
            Assert-Throws `
                -Action {
                    Invoke-OnboardingProbeRecovery `
                        -RepositoryRoot $repoRoot `
                        -Application 'gurbakir' `
                        -Profile 'development' `
                        -PlanReceipt $evidence.PlanPath `
                        -RecoveryReceipt $evidence.RecoveryPath `
                        -OutputPath (Join-Path $temporaryRoot "$($hostileEvidence.Name)-result.json") `
                        -Transport $transport
                } `
                -Pattern $hostileEvidence.Pattern `
                -Name "probe recovery rejects $($hostileEvidence.Name.Replace('-', ' ')) evidence"
        }

        $compatibleProbeAfterRecovery = Copy-TestValue $script:probe
        foreach ($hostileLiveState in @(
            @{
                Name = 'non-empty current probe'
                Pattern = 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
                Arrange = { $script:probe.fields[2].value = '["gid://shopify/Metaobject/12"]' }
            },
            @{
                Name = 'ACTIVE current probe'
                Pattern = 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
                Arrange = { $script:probe.capabilities.publishable.status = 'ACTIVE' }
            },
            @{
                Name = 'duplicate current probe'
                Pattern = 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
                Arrange = { $script:probe = @((Copy-TestValue $compatibleProbeAfterRecovery), (Copy-TestValue $compatibleProbeAfterRecovery)) }
            },
            @{
                Name = 'incompatible current Home definitions'
                Pattern = 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
                Arrange = { $script:createdDefinitions.RemoveAt(1) }
            },
            @{
                Name = 'missing selected Menu'
                Pattern = 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
                Arrange = { $script:menuNodes = @() }
            }
        )) {
            $definitionSnapshot = @($script:createdDefinitions | ForEach-Object { Copy-TestValue $_ })
            $menuSnapshot = Copy-TestValue $script:menuNodes
            $script:probe = Copy-TestValue $compatibleProbeAfterRecovery
            & $hostileLiveState.Arrange
            try {
                Assert-Throws `
                    -Action {
                        Invoke-OnboardingProbeRecovery `
                            -RepositoryRoot $repoRoot `
                            -Application 'gurbakir' `
                            -Profile 'development' `
                            -PlanReceipt $historicalEvidence.PlanPath `
                            -RecoveryReceipt $historicalEvidence.RecoveryPath `
                            -OutputPath (Join-Path $temporaryRoot 'hostile-live-result.json') `
                            -Transport $transport
                    } `
                    -Pattern $hostileLiveState.Pattern `
                    -Name "probe recovery rejects $($hostileLiveState.Name)"
            } finally {
                $script:createdDefinitions.Clear()
                foreach ($definition in $definitionSnapshot) { $script:createdDefinitions.Add($definition) }
                $script:menuNodes = $menuSnapshot
                $script:probe = Copy-TestValue $compatibleProbeAfterRecovery
            }
        }

        $stagingBindingJson = $bindingJson.Replace('"profile":"development"','"profile":"staging"').Replace('"developmentDebug"','"stagingDebug"').Replace('"developmentRelease"','"stagingRelease"')
        [IO.File]::WriteAllText($stagingBindingPath, $stagingBindingJson, [Text.UTF8Encoding]::new($false))
        $stagingContext = Get-OnboardingOperatorContext $repoRoot 'gurbakir' 'staging'
        Write-OnboardingPrivateProperties `
            -RepositoryRoot $repoRoot `
            -Destination $stagingLocalConfigurationPath `
            -Lines (Get-OnboardingValidatedClientConfigurationLines -Context $stagingContext -Values $clientValues) `
            -RefuseOverwrite
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
        if (Test-Path -LiteralPath $localConfigurationPath) { Remove-Item -LiteralPath $localConfigurationPath -Force }
        $context = Get-OnboardingOperatorContext $repoRoot 'gurbakir' 'development'
        Write-OnboardingPrivateProperties `
            -RepositoryRoot $repoRoot `
            -Destination $localConfigurationPath `
            -Lines (Get-OnboardingValidatedClientConfigurationLines -Context $context -Values $clientValues) `
            -RefuseOverwrite
        $invalidLines = Get-Content -LiteralPath $localConfigurationPath
        $invalidLines = @($invalidLines | ForEach-Object {
            if ($_ -clike 'shopify.customerAccountRedirectUri=*') {
                'shopify.customerAccountRedirectUri=shop.999.gurbakir://oauth/callback'
            } else {
                $_
            }
        })
        [IO.File]::WriteAllText($localConfigurationPath, (($invalidLines -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
        Assert-Throws `
            -Action { Write-OnboardingManualCheckpoint $repoRoot 'gurbakir' 'development' $manualCheckpointPath 'owner-evidence:fixture-001' } `
            -Pattern 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountRedirectUri' `
            -Name 'manual checkpoint rejects a scoped callback that mismatches the independently bound shop'

        Remove-Item -LiteralPath $localConfigurationPath -Force
        Write-OnboardingPrivateProperties `
            -RepositoryRoot $repoRoot `
            -Destination $localConfigurationPath `
            -Lines (Get-OnboardingValidatedClientConfigurationLines -Context $context -Values $clientValues) `
            -RefuseOverwrite
        Assert-Throws `
            -Action { Write-OnboardingManualCheckpoint $repoRoot 'gurbakir' 'development' $manualCheckpointPath 'unsafe evidence with spaces' } `
            -Pattern 'INVALID_APPROVED_EVIDENCE_REF' `
            -Name 'manual checkpoint rejects an unbounded free-form evidence reference'
        [void](Write-OnboardingManualCheckpoint $repoRoot 'gurbakir' 'development' $manualCheckpointPath 'owner-evidence:fixture-001')
        $manualCheckpoint = Get-Content -LiteralPath $manualCheckpointPath -Raw | ConvertFrom-Json -AsHashtable
        Assert-True `
            -Condition (
                [string]$manualCheckpoint.callback -ceq 'shop.1234567890.gurbakir://oauth/callback' -and
                [string]$manualCheckpoint.approvedEvidenceRef -ceq 'owner-evidence:fixture-001' -and
                [string]$manualCheckpoint.clientIdSha256 -cmatch '^[0-9a-f]{64}$' -and
                ([string]$manualCheckpoint.clientIdSha256 -cnotmatch 'fixture-client-id')
            ) `
            -Name 'manual checkpoint records only validated callback identity, an approved evidence reference, and a client ID digest'
        Assert-Throws `
            -Action { Write-OnboardingManualCheckpoint $repoRoot 'gurbakir' 'development' $manualCheckpointPath 'owner-evidence:fixture-002' } `
            -Pattern 'MANUAL_CHECKPOINT_EXISTS' `
            -Name 'manual registration evidence is immutable and cannot be overwritten in place'
        $script:readbackArguments=@();$script:readbackPrivileged=@()
        $readbackRunner={
            param($arguments,$privilegedNames)
            $script:readbackArguments=@($arguments);$script:readbackPrivileged=@($privilegedNames)
            return [pscustomobject]@{
                ExitCode = 0
                ExecutedProofs = @(
                    'com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest'
                    'com.gurbakir.storefront.OwnedHomeContentReadbackTest'
                )
            }
        }
        $readback=Invoke-OnboardingReadback $repoRoot 'gurbakir' 'development' -Transport $readbackTransport -ProcessRunner $readbackRunner -ClientValues $clientValues -ManualCheckpointPath $manualCheckpointPath
        Assert-True -Condition ([string]$readback.storefrontMobileReadback -ceq 'PASS') -Name 'Readback executes the bounded Storefront Menu and Home proof lane'
        Assert-True `
            -Condition ([string]$readback.customerDiscovery -ceq 'PASS' -and [string]$readback.customerRegistration -ceq 'PASS') `
            -Name 'Readback distinguishes public Customer discovery from validated manual registration evidence'
        Assert-True `
            -Condition (
                'com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest' -in $script:readbackArguments -and
                'com.gurbakir.storefront.OwnedHomeContentReadbackTest' -in $script:readbackArguments -and
                '--rerun-tasks' -in $script:readbackArguments -and
                '--no-build-cache' -in $script:readbackArguments -and
                '-PonboardingApplication=gurbakir' -in $script:readbackArguments -and
                '-PonboardingProfile=development' -in $script:readbackArguments -and
                $credentialName -in $script:readbackPrivileged
            ) `
            -Name 'Readback forces both profile-bound proof classes to execute fresh and strips privileged provider credentials from Gradle'
        Assert-Throws `
            -Action { Invoke-OnboardingReadback $repoRoot 'gurbakir' 'development' -Transport $readbackTransport -ProcessRunner { return 1 } -ClientValues $clientValues -ManualCheckpointPath $manualCheckpointPath } `
            -Pattern 'MOBILE_READBACK_FAILURE' `
            -Name 'Readback reports a mobile-facing proof failure instead of Inspect success'
        Assert-Throws `
            -Action {
                Invoke-OnboardingReadback $repoRoot 'gurbakir' 'development' -Transport $readbackTransport -ProcessRunner {
                    [pscustomobject]@{ ExitCode = 0; ExecutedProofs = @('com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest') }
                } -ClientValues $clientValues -ManualCheckpointPath $manualCheckpointPath
            } `
            -Pattern 'MOBILE_READBACK_PROOF_INCOMPLETE' `
            -Name 'Readback rejects exit zero when an expected Storefront proof did not execute'

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
        Restore-TestFileSnapshot -Path $bindingPath -Snapshot $bindingSnapshot
        Restore-TestFileSnapshot -Path $stagingBindingPath -Snapshot $stagingBindingSnapshot
        Restore-TestFileSnapshot -Path $localConfigurationPath -Snapshot $localConfigurationSnapshot
        Restore-TestFileSnapshot -Path $stagingLocalConfigurationPath -Snapshot $stagingLocalConfigurationSnapshot
        if(Test-Path $temporaryRoot){Remove-Item -LiteralPath $temporaryRoot -Recurse -Force}
    }
}

function Invoke-OperatorApplyPreservationRegression {
    $bindingPath = Join-Path $repoRoot 'config\local\gurbakir\development.providers.json'
    $stagingBindingPath = Join-Path $repoRoot 'config\local\gurbakir\staging.providers.json'
    $bindingSnapshot = Get-TestFileSnapshot -Path $bindingPath
    $stagingBindingSnapshot = Get-TestFileSnapshot -Path $stagingBindingPath
    $developmentSentinel = [System.Text.UTF8Encoding]::new($false).GetBytes('{"fixture":"preserve-development"}')
    $stagingSentinel = [System.Text.UTF8Encoding]::new($false).GetBytes('{"fixture":"preserve-staging"}')
    try {
        [System.IO.Directory]::CreateDirectory((Split-Path -Parent $bindingPath)) | Out-Null
        [System.IO.File]::WriteAllBytes($bindingPath, $developmentSentinel)
        [System.IO.File]::WriteAllBytes($stagingBindingPath, $stagingSentinel)
        Invoke-OperatorApplySuite
        Assert-True `
            -Condition (
                [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($bindingPath)) -ceq [Convert]::ToBase64String($developmentSentinel) -and
                [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($stagingBindingPath)) -ceq [Convert]::ToBase64String($stagingSentinel)
            ) `
            -Name 'operator Apply self-tests restore pre-existing ignored provider bindings byte for byte'
    } finally {
        Restore-TestFileSnapshot -Path $bindingPath -Snapshot $bindingSnapshot
        Restore-TestFileSnapshot -Path $stagingBindingPath -Snapshot $stagingBindingSnapshot
    }
}

switch ($Suite) {
    'Registry' { Invoke-RegistrySuite }
    'Configuration' { Invoke-ConfigurationSuite }
    'Enrollment' { Invoke-EnrollmentSuite }
    'OperatorReadOnly' { Invoke-OperatorReadOnlySuite }
    'OperatorApply' { Invoke-OperatorApplyPreservationRegression }
    'Security' { Invoke-OperatorReadOnlySuite; Invoke-OperatorApplyPreservationRegression }
    'All' {
        Invoke-RegistrySuite
        Invoke-ConfigurationSuite
        Invoke-EnrollmentSuite
        Invoke-OperatorReadOnlySuite
        Invoke-OperatorApplyPreservationRegression
    }
    default {
        throw "Suite $Suite has not been implemented yet."
    }
}

$results | Format-Table -AutoSize
Write-Output ("Multi-Brand onboarding {0} suite: PASS ({1}/{1})" -f $Suite, $results.Count)

# Negative native-command fixtures intentionally exercise nonzero child exits.
# Do not leak their final LASTEXITCODE into a successful caller such as a
# multi-command GitHub Actions pwsh step.
$global:LASTEXITCODE = 0
