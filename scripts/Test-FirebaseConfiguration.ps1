[CmdletBinding()]
param(
    [switch]$RequireConfigured
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$expected = [ordered]@{
    'app/src/developmentDebug/google-services.json' = @{ Package = 'com.gurbakir.mobile.dev.debug'; Environment = 'development' }
    'app/src/developmentRelease/google-services.json' = @{ Package = 'com.gurbakir.mobile.dev'; Environment = 'development' }
    'app/src/stagingDebug/google-services.json' = @{ Package = 'com.gurbakir.mobile.staging.debug'; Environment = 'staging' }
    'app/src/stagingRelease/google-services.json' = @{ Package = 'com.gurbakir.mobile.staging'; Environment = 'staging' }
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
    if ($present.Count -eq 0) {
        if ($RequireConfigured) { throw 'Firebase configuration is required but no variant files exist.' }
        Write-Output 'PASS: Firebase is unconfigured and fails closed; no variant file is present.'
        exit 0
    }

    if ($present.Count -ne $expected.Count) {
        throw 'Partial Firebase configuration is forbidden; all four non-production variant files are required.'
    }

    $projectIdsByEnvironment = @{
        development = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        staging = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    }
    foreach ($entry in $expected.GetEnumerator()) {
        $relativePath = [string]$entry.Key
        $packageName = [string]$entry.Value.Package
        $environment = [string]$entry.Value.Environment
        $json = Get-Content -LiteralPath $relativePath -Raw | ConvertFrom-Json
        if (Find-ProhibitedJsonKey -Value $json) {
            throw "A server/private credential key was found in $relativePath."
        }
        $projectId = [string]$json.project_info.project_id
        if ([string]::IsNullOrWhiteSpace($projectId)) { throw "Missing Firebase project_id in $relativePath." }
        [void]$projectIdsByEnvironment[$environment].Add($projectId)

        $clients = @($json.client)
        $matches = @($clients | Where-Object {
            [string]$_.client_info.android_client_info.package_name -eq $packageName
        })
        if ($matches.Count -ne 1) { throw "Expected one exact Android package match in $relativePath." }
        $client = $matches[0]
        if ([string]::IsNullOrWhiteSpace([string]$client.client_info.mobilesdk_app_id)) {
            throw "Missing mobilesdk_app_id in $relativePath."
        }
        $apiKeys = @($client.api_key | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.current_key) })
        if ($apiKeys.Count -lt 1) { throw "Missing Firebase Android public API key in $relativePath." }

        $ignored = git check-ignore -- $relativePath
        if ($ignored -ne $relativePath) { throw "$relativePath is not protected by .gitignore." }
    }

    if ($projectIdsByEnvironment.development.Count -ne 1 -or $projectIdsByEnvironment.staging.Count -ne 1) {
        throw 'Firebase debug and release files must share one project per environment.'
    }
    $developmentProjectId = @($projectIdsByEnvironment.development)[0]
    $stagingProjectId = @($projectIdsByEnvironment.staging)[0]
    if ($developmentProjectId -eq $stagingProjectId) {
        throw 'Firebase development and staging files must point to separate projects.'
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $developmentFingerprint = [System.BitConverter]::ToString(
            $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($developmentProjectId))
        ).Replace('-', '').Substring(0, 12)
        $stagingFingerprint = [System.BitConverter]::ToString(
            $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($stagingProjectId))
        ).Replace('-', '').Substring(0, 12)
    } finally {
        $sha.Dispose()
    }
    Write-Output "PASS: four ignored Firebase configs map to isolated development/staging projects; redacted fingerprints=$developmentFingerprint/$stagingFingerprint"
} finally {
    Pop-Location
}
