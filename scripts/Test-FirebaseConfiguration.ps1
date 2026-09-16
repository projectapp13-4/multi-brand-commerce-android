[CmdletBinding()]
param(
    [switch]$RequireConfigured
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1') -Force
$registry = Import-OnboardingRegistry -Path (Join-Path $repoRoot 'config\onboarding\application-registry.v1.json') -RepositoryRoot $repoRoot
$applications = @($registry.applications | Where-Object { [string]$_.role -ceq 'real-brand-application' })
$expected = [ordered]@{}
$expectedByApplication = [ordered]@{}
foreach ($application in $applications) {
    $applicationEntries = [System.Collections.Generic.List[object]]::new()
    foreach ($profile in @($application.profiles | Where-Object { [string]$_.firebase.mode -cne 'disabled' })) {
        foreach ($variant in @($profile.variants)) {
            if ([string]::IsNullOrWhiteSpace([string]$variant.firebaseConfig)) {
                throw "Enabled Firebase profile has no configuration path for $($application.key)/$($profile.key)/$($variant.name)."
            }
            $entry = @{
                Application = [string]$application.key
                Package = [string]$variant.applicationId
                Profile = [string]$profile.key
                Binding = [string]$profile.providerBindingFile
                Variant = [string]$variant.name
                ProfileRecord = $profile
            }
            $firebasePath = [string]$variant.firebaseConfig
            if ($expected.Contains($firebasePath)) {
                throw "Firebase configuration path has more than one enrolled owner: $firebasePath"
            }
            $expected[$firebasePath] = $entry
            $applicationEntries.Add([pscustomobject]@{ Path = $firebasePath; Entry = $entry })
        }
    }
    if ($applicationEntries.Count -gt 0) { $expectedByApplication[[string]$application.key] = $applicationEntries.ToArray() }
}

function Find-ProhibitedJsonKey {
    param([Parameter(Mandatory = $true)]$Value)

    if ($null -eq $Value) { return $false }
    if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string] -and
        $Value -isnot [pscustomobject]) {
        foreach ($item in $Value) {
            if (Find-ProhibitedJsonKey -Value $item) { return $true }
        }
        return $false
    }
    if ($Value -is [pscustomobject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -in @('private_key', 'private_key_id', 'client_secret', 'service_account')) {
                return $true
            }
            if (Find-ProhibitedJsonKey -Value $property.Value) { return $true }
        }
    }
    return $false
}

Push-Location $repoRoot
try {
    $present = @($expected.Keys | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
    if ($expected.Count -eq 0) {
        Write-Output 'PASS: no enrolled real application profile enables Firebase.'
        exit 0
    }
    if ($present.Count -eq 0) {
        if ($RequireConfigured) { throw 'Firebase configuration is required but no variant files exist.' }
        Write-Output 'PASS: Firebase is unconfigured and fails closed; no variant file is present.'
        exit 0
    }

    foreach ($applicationEntry in $expectedByApplication.GetEnumerator()) {
        $applicationPaths = @($applicationEntry.Value | ForEach-Object { [string]$_.Path })
        $applicationPresent = @($applicationPaths | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
        if ($applicationPresent.Count -eq 0) {
            if ($RequireConfigured) { throw "Firebase configuration is required for $($applicationEntry.Key)." }
            continue
        }
        if ($applicationPresent.Count -ne $applicationPaths.Count) {
            throw "Partial Firebase configuration is forbidden for $($applicationEntry.Key); every enabled variant file is required."
        }
    }

    $projectIdsByProfile = [ordered]@{}
    foreach ($entry in @($expected.GetEnumerator() | Where-Object { Test-Path -LiteralPath ([string]$_.Key) -PathType Leaf })) {
        $relativePath = [string]$entry.Key
        $applicationKey = [string]$entry.Value.Application
        $packageName = [string]$entry.Value.Package
        $profileKey = [string]$entry.Value.Profile
        $bindingPath = [string]$entry.Value.Binding
        if (-not (Test-Path -LiteralPath $bindingPath -PathType Leaf)) { throw "Required independent provider binding is missing for $applicationKey/$profileKey." }
        $profileRecord = $entry.Value.ProfileRecord
        $binding = Import-OnboardingProviderBinding -Path $bindingPath -Application $applicationKey -Profile $profileKey -ExpectedFirebaseVariants @($profileRecord.variants | ForEach-Object { [string]$_.name })
        $json = Get-Content -LiteralPath $relativePath -Raw | ConvertFrom-Json
        if (Find-ProhibitedJsonKey -Value $json) {
            throw "A server/private credential key was found in $relativePath."
        }
        $projectId = [string]$json.project_info.project_id
        if ([string]::IsNullOrWhiteSpace($projectId)) { throw "Missing Firebase project_id in $relativePath." }
        if ($projectId -cne [string]$binding.firebase.projectId -or [string]$json.project_info.project_number -cne [string]$binding.firebase.projectNumber) {
            throw "Firebase project identity does not match the approved binding for $applicationKey/$profileKey."
        }
        $profileIdentity = "$applicationKey/$profileKey"
        if (-not $projectIdsByProfile.Contains($profileIdentity)) {
            $projectIdsByProfile[$profileIdentity] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        }
        [void]$projectIdsByProfile[$profileIdentity].Add($projectId)

        $clients = @($json.client)
        $matches = @($clients | Where-Object {
            [string]$_.client_info.android_client_info.package_name -eq $packageName
        })
        if ($matches.Count -ne 1) { throw "Expected one exact Android package match in $relativePath." }
        $client = $matches[0]
        if ([string]::IsNullOrWhiteSpace([string]$client.client_info.mobilesdk_app_id)) {
            throw "Missing mobilesdk_app_id in $relativePath."
        }
        if ([string]$client.client_info.mobilesdk_app_id -cne [string]$binding.firebase.androidAppIdsByVariant[[string]$entry.Value.Variant]) {
            throw "Firebase Android app identity does not match the approved binding for $applicationKey/$profileKey."
        }
        $apiKeys = @($client.api_key | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.current_key) })
        if ($apiKeys.Count -lt 1) { throw "Missing Firebase Android public API key in $relativePath." }

        $ignored = git check-ignore -- $relativePath
        if ($ignored -ne $relativePath) { throw "$relativePath is not protected by .gitignore." }
    }

    foreach ($profileProjects in $projectIdsByProfile.GetEnumerator()) {
        if ($profileProjects.Value.Count -ne 1) {
            throw "Firebase variants must share one project within $($profileProjects.Key)."
        }
    }
    $profileProjectIds = @($projectIdsByProfile.Values | ForEach-Object { @($_)[0] })
    if (@($profileProjectIds | Sort-Object -Unique).Count -ne $profileProjectIds.Count) {
        throw 'Distinct enabled application profiles must not share one Firebase project.'
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $fingerprints = @(
            foreach ($profileProject in $projectIdsByProfile.GetEnumerator()) {
                $projectId = @($profileProject.Value)[0]
                $digest = [System.BitConverter]::ToString(
                    $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($projectId))
                ).Replace('-', '').Substring(0, 12)
                "$($profileProject.Key)=$digest"
            }
        )
    } finally {
        $sha.Dispose()
    }
    Write-Output "PASS: $($present.Count) ignored Firebase configs map to isolated enrolled application profiles; redacted fingerprints=$($fingerprints -join ',')"
} finally {
    Pop-Location
}
