[CmdletBinding()]
param(
    [string]$ConfigurationPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($ConfigurationPath)) {
    $ConfigurationPath = Join-Path $PSScriptRoot '..\config\local.properties'
}

function Read-LocalProperties {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'The ignored local configuration file does not exist.'
    }

    $ordered = [ordered]@{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { throw 'The local configuration contains a malformed property line.' }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        $ordered[$key] = $value
    }
    return $ordered
}

function Require-HttpsUri {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $uri = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($uri.Host) -or
        -not [string]::IsNullOrWhiteSpace($uri.UserInfo) -or $uri.Fragment.Length -ne 0) {
        throw "$Label was not a safe HTTPS URI."
    }
    return $uri
}

$resolvedPath = [System.IO.Path]::GetFullPath($ConfigurationPath)
$properties = Read-LocalProperties -Path $resolvedPath
$domain = [string]$properties['shopify.storefrontDomain']
if ($domain -ne 'gurbakir.com') {
    throw 'Refusing discovery because the configured storefront is not the verified Gurbakir domain.'
}

$openId = Invoke-RestMethod -Uri "https://$domain/.well-known/openid-configuration" -TimeoutSec 45
$customerApi = Invoke-RestMethod -Uri "https://$domain/.well-known/customer-account-api" -TimeoutSec 45

$issuer = Require-HttpsUri -Value ([string]$openId.issuer) -Label 'issuer'
$authorizationEndpoint = Require-HttpsUri -Value ([string]$openId.authorization_endpoint) -Label 'authorization endpoint'
$tokenEndpoint = Require-HttpsUri -Value ([string]$openId.token_endpoint) -Label 'token endpoint'
$logoutEndpoint = Require-HttpsUri -Value ([string]$openId.end_session_endpoint) -Label 'logout endpoint'
$graphqlEndpoint = Require-HttpsUri -Value ([string]$customerApi.graphql_api) -Label 'Customer Account GraphQL endpoint'

if ($issuer.Host -ne 'shopify.com' -or $issuer.AbsolutePath -notmatch '^/authentication/(?<shopId>[0-9]+)$') {
    throw 'The discovery issuer did not identify a Shopify mobile Customer Account shop.'
}
$shopId = $Matches['shopId']

$properties['shopify.customerAccountIssuer'] = $issuer.AbsoluteUri.TrimEnd('/')
$properties['shopify.customerAccountAuthorizationEndpoint'] = $authorizationEndpoint.AbsoluteUri
$properties['shopify.customerAccountTokenEndpoint'] = $tokenEndpoint.AbsoluteUri
$properties['shopify.customerAccountLogoutEndpoint'] = $logoutEndpoint.AbsoluteUri
$properties['shopify.customerAccountGraphqlEndpoint'] = $graphqlEndpoint.AbsoluteUri
$properties['shopify.customerAccountRedirectUri'] = "shop.$shopId.gurbakir://oauth/callback"
$properties['shopify.customerAccountScopes'] = 'openid email customer-account-api:full'

$lines = @(
    '# Generated local public-client configuration. Never commit this file.'
    '# Controlled Storefront token and Customer Account client ID remain redacted from output.'
)
foreach ($entry in $properties.GetEnumerator()) {
    $lines += ('{0}={1}' -f $entry.Key, $entry.Value)
}
[System.IO.File]::WriteAllLines($resolvedPath, $lines, (New-Object System.Text.UTF8Encoding($false)))

$clientConfigured = -not [string]::IsNullOrWhiteSpace([string]$properties['shopify.customerAccountClientId'])
Write-Output ('PASS: verified Shopify discovery saved to ignored local configuration; client_id configured={0}' -f $clientConfigured)
