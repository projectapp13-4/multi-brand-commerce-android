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
    } finally {
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
    } finally {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

function Invoke-OperatorReadOnlySuite {
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Shopify.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.CustomerAccount.psm1') -Force
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.AppLinks.psm1') -Force
    $registry = Import-OnboardingRegistry -Path (Join-Path $repoRoot 'config\onboarding\application-registry.v1.json') -RepositoryRoot $repoRoot
    $selected = Get-OnboardingApplicationProfile -Registry $registry -Application 'gurbakir' -Profile 'development'
    $binding = @{ shopify = @{ adminShopDomain = 'fixture-shop.myshopify.com'; shopId = '1234567890' } }
    $script:adminBodies = [Collections.Generic.List[string]]::new()
    $adminTransport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $script:adminBodies.Add([string]$body)
        if ($body -match 'Gate8HomeDefinitions') {
            return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ metaobjectDefinitions = @{ nodes = @(); pageInfo = @{ hasNextPage = $false; endCursor = $null } } } } }
        }
        return [pscustomobject]@{ StatusCode = 200; Data = @{ data = @{ menu = $null } } }
    }
    $homeState = Get-ShopifyHomeDefinitionState $binding 'fixture-admin-token' $adminTransport
    Assert-True -Condition ($homeState.Classification -ceq 'ABSENT') -Name 'absent Home definitions classify without mutation'
    Assert-True -Condition ($script:adminBodies[0] -match '"first":100') -Name 'Admin definition inspection uses fixed page size 100'
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
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Common.psm1') -Force
    Assert-True -Condition ((Protect-OnboardingOutput 'failure fixture-secret' @('fixture-secret')) -ceq 'failure <redacted>') -Name 'loaded values are redacted before output'
}

function Invoke-OperatorApplySuite {
    Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Operator.psm1') -Force
    $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('gate8-apply-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $bindingPath = Join-Path $repoRoot 'config\local\gurbakir\development.providers.json'
    [IO.Directory]::CreateDirectory((Split-Path -Parent $bindingPath)) | Out-Null
    $bindingJson = '{"schemaVersion":1,"application":"gurbakir","profile":"development","approvedEvidenceRef":"owner-evidence:fixture-apply","shopify":{"adminShopDomain":"fixture-shop.myshopify.com","shopId":"1234567890"},"firebase":{"projectId":"fixture-project-123","projectNumber":"123456789012","androidAppIdsByVariant":{"developmentDebug":"1:123456789012:android:0123456789abcdef","developmentRelease":"1:123456789012:android:fedcba9876543210"}}}'
    [IO.File]::WriteAllText($bindingPath, $bindingJson, [Text.UTF8Encoding]::new($false))
    $credentialName = 'MB_GURBAKIR_DEVELOPMENT_SHOPIFY_ADMIN_TOKEN'
    $previous = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
    [Environment]::SetEnvironmentVariable($credentialName, 'fixture-admin-token', 'Process')
    $script:createdDefinitions = [Collections.Generic.List[object]]::new(); $script:probe = $null; $script:writeCount = 0
    $transport = {
        param($method, $uri, $headers, $body, $maximumBytes)
        $request = $body | ConvertFrom-Json -AsHashtable
        if ($request.query -match 'Gate8HomeDefinitions') {
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitions=@{nodes=@($script:createdDefinitions);pageInfo=@{hasNextPage=$false;endCursor=$null}}}}}
        }
        if ($request.query -match 'query Gate8Probe') { return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectByHandle=$script:probe}}} }
        if ($request.query -match 'Gate8DefinitionCreate') {
            $script:writeCount++;$definition=$request.variables.definition;$id="gid://shopify/MetaobjectDefinition/$($script:writeCount)"
            $node=@{id=$id;type=[string]$definition.type;name=[string]$definition.name;fieldDefinitions=@($definition.fieldDefinitions);capabilities=@{publishable=@{enabled=$true}};access=@{storefront='PUBLIC_READ'}};$script:createdDefinitions.Add($node)
            return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectDefinitionCreate=@{metaobjectDefinition=@{id=$id;type=[string]$definition.type};userErrors=@()}}}}
        }
        if ($request.query -match 'Gate8ProbeCreate') {$script:writeCount++;$script:probe=@{id='gid://shopify/Metaobject/99';type='mobile_home';handle='gate8-operator-acceptance-v1';fields=@(@{key='schema_version';value='1'},@{key='declared_section_count';value='0'});capabilities=@{publishable=@{status='DRAFT'}}};return [pscustomobject]@{StatusCode=200;Data=@{data=@{metaobjectCreate=@{metaobject=$script:probe;userErrors=@()}}}}}
        throw 'unexpected fixture operation'
    }
    try {
        $planPath=Join-Path $temporaryRoot 'plan.json';$resultPath=Join-Path $temporaryRoot 'result.json'
        [void](New-OnboardingPlan $repoRoot 'gurbakir' 'development' $planPath -IncludeAcceptanceProbe -Transport $transport)
        Assert-Throws -Action { Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $planPath 'gurbakir' 'development' -IncludeAcceptanceProbe -Transport $transport } -Pattern 'CONFIRMATION_MISMATCH' -Name 'Apply requires explicit confirmation'
        [void](Invoke-OnboardingApply $repoRoot 'gurbakir' 'development' $planPath 'gurbakir' 'development' -ConfirmApply -IncludeAcceptanceProbe -OutputPath $resultPath -Transport $transport)
        Assert-True -Condition ($script:writeCount -eq 4) -Name 'Apply creates only three definitions and one DRAFT probe'
        Import-Module (Join-Path $repoRoot 'scripts\onboarding\Onboarding.Registry.psm1') -Force
        Assert-True -Condition ((Import-OnboardingReceipt $resultPath).kind -ceq 'RESULT') -Name 'Apply writes a closed redacted result receipt'
    } finally {
        [Environment]::SetEnvironmentVariable($credentialName, $previous, 'Process')
        if(Test-Path $bindingPath){Remove-Item -LiteralPath $bindingPath -Force}
        if(Test-Path $temporaryRoot){Remove-Item -LiteralPath $temporaryRoot -Recurse -Force}
    }
}

switch ($Suite) {
    'Registry' { Invoke-RegistrySuite }
    'Configuration' { Invoke-ConfigurationSuite }
    'Enrollment' { Invoke-EnrollmentSuite }
    'OperatorReadOnly' { Invoke-OperatorReadOnlySuite }
    'OperatorApply' { Invoke-OperatorApplySuite }
    'Security' { Invoke-OperatorReadOnlySuite }
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
