[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Validate', 'Inspect', 'Plan', 'Apply', 'Readback', 'Recover', 'GenerateLocalConfiguration', 'RecordManualCheckpoint')]
    [string]$Command,
    [string]$Application = '',
    [string]$Profile = '',
    [switch]$ProjectionOnly,
    [string]$OutputPath = '',
    [string]$PlanReceipt = '',
    [string]$PriorReceipt = '',
    [string]$ConfirmApplication = '',
    [string]$ConfirmProfile = '',
    [switch]$ConfirmApply,
    [switch]$IncludeAcceptanceProbe,
    [string]$ApprovedEvidenceRef = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$commonModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Common.psm1'
$registryModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1'
Import-Module $registryModule -Force
Import-Module $commonModule -Force

try {
    $registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
    $registry = Import-OnboardingRegistry -Path $registryPath -RepositoryRoot $repoRoot
    $registrySha = Get-OnboardingSha256 -Path $registryPath

    if (($Application.Length -eq 0) -xor ($Profile.Length -eq 0)) {
        throw (New-OnboardingContractError -Code 'TARGET_PAIR_REQUIRED' -Field 'Application/Profile')
    }
    $selected = $null
    if ($Application.Length -gt 0) {
        $selected = Get-OnboardingApplicationProfile -Registry $registry -Application $Application -Profile $Profile
    }

    if ($Command -ceq 'Validate') {
      Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Shopify.psm1') -Force
      [void](Import-ShopifyHomeSchemaContract)
      Import-Module $commonModule -Force
      $profiles = if ($null -ne $selected) {
        @([pscustomobject]@{ Application = $selected.Application; Profile = $selected.Profile })
    } else {
        @(
            foreach ($applicationRecord in @($registry.applications)) {
                foreach ($profileRecord in @($applicationRecord.profiles)) {
                    [pscustomobject]@{ Application = $applicationRecord; Profile = $profileRecord }
                }
            }
        )
    }
      foreach ($record in $profiles) {
        if ($null -eq $record.Application.configurationProjection) {
            continue
        }
        $relativePath = '{0}/{1}.properties' -f $record.Application.configurationProjection, $record.Profile.key
        $projectionPath = Test-OnboardingSafeRelativePath -RepositoryRoot $repoRoot -RelativePath $relativePath -Field 'projection.path'
        $expected = Get-OnboardingProjectionLines `
            -Registry $registry `
            -ApplicationRecord $record.Application `
            -ProfileRecord $record.Profile `
            -RegistrySha256 $registrySha
        Test-OnboardingProjection -Path $projectionPath -ExpectedLines $expected
      }
      if ($ProjectionOnly) {
        Write-Output 'PASS: onboarding projections are valid; no credentials, network, Gradle, or repair action was used.'
      } else {
        Write-Output 'PASS: onboarding registry and projections are valid. Missing ignored client configuration remains UNCONFIGURED.'
      }
      exit 0
    }
    if ($Application.Length -eq 0) { throw (New-OnboardingContractError -Code 'TARGET_PAIR_REQUIRED' -Field 'Application/Profile') }
    Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Operator.psm1') -Force
    switch ($Command) {
        'Inspect' { Invoke-OnboardingInspect -RepositoryRoot $repoRoot -Application $Application -Profile $Profile | ConvertTo-Json -Depth 8; exit 0 }
        'Plan' { [void](New-OnboardingPlan -RepositoryRoot $repoRoot -Application $Application -Profile $Profile -OutputPath $OutputPath -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -PriorReceipt $PriorReceipt); Write-Output 'PASS: redacted immutable onboarding Plan receipt created.'; exit 0 }
        'Apply' { [void](Invoke-OnboardingApply -RepositoryRoot $repoRoot -Application $Application -Profile $Profile -PlanReceipt $PlanReceipt -ConfirmApplication $ConfirmApplication -ConfirmProfile $ConfirmProfile -ConfirmApply:$ConfirmApply -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -PriorReceipt $PriorReceipt -OutputPath $OutputPath); Write-Output 'PASS: bounded onboarding Apply completed and readback receipt was written.'; exit 0 }
        'Readback' { Invoke-OnboardingInspect -RepositoryRoot $repoRoot -Application $Application -Profile $Profile | ConvertTo-Json -Depth 8; exit 0 }
        'Recover' { Get-OnboardingRecovery -RepositoryRoot $repoRoot -PlanReceipt $PlanReceipt | ConvertTo-Json; exit 0 }
        'GenerateLocalConfiguration' { Write-OnboardingLocalConfiguration -RepositoryRoot $repoRoot -Application $Application -Profile $Profile -ConfirmApply:$ConfirmApply; exit 0 }
        'RecordManualCheckpoint' { Write-OnboardingManualCheckpoint -RepositoryRoot $repoRoot -Application $Application -Profile $Profile -OutputPath $OutputPath -EvidenceRef $ApprovedEvidenceRef; exit 0 }
        default { throw (New-OnboardingContractError -Code 'COMMAND_NOT_IMPLEMENTED' -Field $Command) }
    }
} catch {
    $message = [string]$_.Exception.Message
    if ($message -match '^(?<code>[A-Z0-9_]+):') {
        [Console]::Error.WriteLine(('FAIL [{0}]' -f $Matches.code))
    } else {
        [Console]::Error.WriteLine('FAIL [VALIDATION_FAILURE]')
    }
    if ($message -match 'MISSING_.*(TOKEN|BINDING)|MISSING_FILE') { exit 3 }
    if ($message -match 'UNSAFE_|TARGET_|CONFIRM') { exit 7 }
    if ($message -match 'INCOMPATIBLE|DRIFT|COLLISION') { exit 4 }
    if ($message -match 'PARTIAL_APPLY') { exit 8 }
    if ($message -match 'PROVIDER_|SHOPIFY_|FIREBASE_|CUSTOMER_') { exit 5 }
    exit 2
}
