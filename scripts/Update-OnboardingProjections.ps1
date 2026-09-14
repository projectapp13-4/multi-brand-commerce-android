[CmdletBinding()]
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$commonModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Common.psm1'
$registryModule = Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1'
Import-Module $registryModule -Force
Import-Module $commonModule -Force

$registryPath = Join-Path $repoRoot 'config\onboarding\application-registry.v1.json'
$registry = Import-OnboardingRegistry -Path $registryPath -RepositoryRoot $repoRoot
$registrySha = Get-OnboardingSha256 -Path $registryPath
$encoding = [System.Text.UTF8Encoding]::new($false)
$updated = 0

foreach ($application in @($registry.applications)) {
    if ($null -eq $application.configurationProjection) {
        continue
    }
    foreach ($profile in @($application.profiles)) {
        $relativePath = '{0}/{1}.properties' -f $application.configurationProjection, $profile.key
        $path = Test-OnboardingSafeRelativePath -RepositoryRoot $repoRoot -RelativePath $relativePath -Field 'projection.path'
        $lines = Get-OnboardingProjectionLines `
            -Registry $registry `
            -ApplicationRecord $application `
            -ProfileRecord $profile `
            -RegistrySha256 $registrySha
        if ($Check) {
            Test-OnboardingProjection -Path $path -ExpectedLines $lines
            continue
        }
        $directory = [System.IO.Path]::GetDirectoryName($path)
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
        $content = ($lines -join "`n") + "`n"
        [System.IO.File]::WriteAllText($path, $content, $encoding)
        $updated++
    }
}

if ($Check) {
    Write-Output 'PASS: tracked onboarding projections match the registry byte-for-byte.'
} else {
    Write-Output ("PASS: wrote {0} deterministic non-secret onboarding projection(s)." -f $updated)
}
