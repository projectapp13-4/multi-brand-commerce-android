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
    [string]$ConfirmApplication = '',
    [string]$ConfirmProfile = '',
    [switch]$ConfirmApply,
    [switch]$IncludeAcceptanceProbe
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

    if ($Command -cne 'Validate') {
        throw (New-OnboardingContractError -Code 'COMMAND_NOT_IMPLEMENTED' -Field $Command)
    }

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
} catch {
    $message = [string]$_.Exception.Message
    if ($message -match '^(?<code>[A-Z0-9_]+):') {
        [Console]::Error.WriteLine(('FAIL [{0}]' -f $Matches.code))
    } else {
        [Console]::Error.WriteLine('FAIL [VALIDATION_FAILURE]')
    }
    exit 2
}
