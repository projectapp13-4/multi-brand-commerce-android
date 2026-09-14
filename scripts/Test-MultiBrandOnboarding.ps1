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

switch ($Suite) {
    'Registry' { Invoke-RegistrySuite }
    'All' { Invoke-RegistrySuite }
    default {
        throw "Suite $Suite has not been implemented yet."
    }
}

$results | Format-Table -AutoSize
Write-Output ("Multi-Brand onboarding {0} suite: PASS ({1}/{1})" -f $Suite, $results.Count)
