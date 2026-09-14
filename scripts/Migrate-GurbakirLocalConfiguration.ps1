[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('development', 'staging')]
    [string]$Profile,
    [string]$LegacyPath = '',
    [string]$DestinationPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Common.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1') -Force

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ([string]::IsNullOrWhiteSpace($LegacyPath)) { $LegacyPath = Join-Path $repoRoot 'config\local.properties' }
if ([string]::IsNullOrWhiteSpace($DestinationPath)) {
    $DestinationPath = Join-Path $repoRoot "config\local\gurbakir\$Profile.properties"
}
$legacy = [System.IO.Path]::GetFullPath($LegacyPath)
$destination = [System.IO.Path]::GetFullPath($DestinationPath)
if (-not (Test-Path -LiteralPath $legacy -PathType Leaf)) { throw 'LEGACY_CONFIGURATION_MISSING' }
if (Test-Path -LiteralPath $destination) { throw 'DESTINATION_EXISTS' }
if (-not $destination.StartsWith($repoRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'UNSAFE_CONFIGURATION_PATH'
}
$relative = [System.IO.Path]::GetRelativePath($repoRoot, $destination).Replace('\', '/')
$ignore = & git -C $repoRoot check-ignore --no-index --quiet -- $relative
if ($LASTEXITCODE -ne 0) { throw 'DESTINATION_NOT_IGNORED' }

$legacyValues = [ordered]@{}
foreach ($line in [System.IO.File]::ReadAllLines($legacy, [System.Text.Encoding]::UTF8)) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw 'MALFORMED_LEGACY_CONFIGURATION' }
    $key = $line.Substring(0, $separator).Trim()
    if ($legacyValues.Contains($key)) { throw "DUPLICATE_LEGACY_KEY:$key" }
    $legacyValues[$key] = $line.Substring($separator + 1).Trim()
}
$allowedLegacy = @(
    'environment.displayName', 'shopify.storefrontDomain', 'shopify.storefrontApiVersion',
    'shopify.storefrontPublicToken', 'shopify.catalogMenuHandle', 'shopify.homeContentRootHandle',
    'shopify.customerAccountClientId', 'shopify.customerAccountIssuer',
    'shopify.customerAccountAuthorizationEndpoint', 'shopify.customerAccountTokenEndpoint',
    'shopify.customerAccountLogoutEndpoint', 'shopify.customerAccountGraphqlEndpoint',
    'shopify.customerAccountRedirectUri', 'shopify.customerAccountScopes'
)
foreach ($key in $legacyValues.Keys) { if ($key -notin $allowedLegacy) { throw "UNSUPPORTED_LEGACY_KEY:$key" } }
$projectionPath = Join-Path $repoRoot "config\onboarding\generated\gurbakir\$Profile.properties"
$projection = Read-OnboardingProperties -Path $projectionPath -AllowedKeys (Get-OnboardingProjectionKeyOrder)
$tracked = @{
    'shopify.storefrontDomain' = 'shopify.storefrontDomain'
    'shopify.storefrontApiVersion' = 'shopify.storefrontApiVersion'
    'shopify.catalogMenuHandle' = 'shopify.catalogMenuHandle'
    'shopify.homeContentRootHandle' = 'shopify.homeRootHandle'
    'shopify.customerAccountScopes' = 'app.customerAccountScopes'
}
foreach ($legacyKey in $tracked.Keys) {
    if ($legacyValues.Contains($legacyKey)) {
        $actual = [string]$legacyValues[$legacyKey]
        $expected = [string]$projection[$tracked[$legacyKey]]
        if ($legacyKey -eq 'shopify.customerAccountScopes') { $actual = $actual.Replace(' ', ',') }
        if ($legacyKey -eq 'shopify.homeContentRootHandle' -and [string]::IsNullOrWhiteSpace($actual)) {
            throw 'HOME_SELECTOR_REQUIRES_OPERATOR_DECISION'
        }
        if ($actual -cne $expected) { throw "TRACKED_CONFIGURATION_CONFLICT:$legacyKey" }
    }
}
$localKeys = @(
    'shopify.storefrontPublicToken', 'shopify.customerAccountClientId',
    'shopify.customerAccountIssuer', 'shopify.customerAccountAuthorizationEndpoint',
    'shopify.customerAccountTokenEndpoint', 'shopify.customerAccountLogoutEndpoint',
    'shopify.customerAccountGraphqlEndpoint', 'shopify.customerAccountRedirectUri'
)
$lines = foreach ($key in $localKeys) { '{0}={1}' -f $key, ([string]$legacyValues[$key]) }
$parent = Split-Path -Parent $destination
[System.IO.Directory]::CreateDirectory($parent) | Out-Null
$temporary = Join-Path $parent ('.' + [System.IO.Path]::GetFileName($destination) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    [System.IO.File]::WriteAllText($temporary, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::Move($temporary, $destination)
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
}
Write-Output "PASS: migrated legacy local configuration to application gurbakir profile $Profile; values were not printed."
