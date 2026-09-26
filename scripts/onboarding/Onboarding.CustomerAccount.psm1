Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force

function Get-OnboardingSecureDiscoveryEndpoint {
    param([Parameter(Mandatory)]$Document,[Parameter(Mandatory)][string]$Name)
    if (-not $Document.Contains($Name)) { throw 'CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH' }
    $value = [string]$Document[$Name]
    $uri = $null
    if ($value.Length -gt 2048 -or
        -not [uri]::TryCreate($value, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -cne 'https' -or
        $uri.UserInfo.Length -ne 0 -or
        $uri.Fragment.Length -ne 0) {
        throw 'CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH'
    }
    Assert-OnboardingHost -HostName $uri.IdnHost -Field "customerDiscovery.$Name"
    if (-not (Test-OnboardingShopifyCustomerUri -Uri $uri)) {
        throw 'CUSTOMER_DISCOVERY_ENDPOINT_MISMATCH'
    }
    return $uri.AbsoluteUri
}

function Get-OnboardingCustomerDiscovery {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Selected, [Parameter(Mandatory)]$Binding, [scriptblock]$Transport)
    if ([string]$Selected.Profile.customerAccount.mode -ceq 'disabled') {
        return [pscustomobject]@{ Classification = 'NOT_APPLICABLE'; Fingerprint = ('0' * 64); Callback = $null }
    }
    $origin = [uri][string]$Selected.Profile.customerAccount.discoveryOrigin
    $openId = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]::new($origin, '/.well-known/openid-configuration')) -MaximumBytes 131072 -Transport $Transport
    $account = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]::new($origin, '/.well-known/customer-account-api')) -MaximumBytes 131072 -Transport $Transport
    if ($openId.StatusCode -ne 200 -or $account.StatusCode -ne 200) { throw 'CUSTOMER_DISCOVERY_HTTP_FAILURE' }
    $expectedIssuer = "https://shopify.com/authentication/$($Binding.shopify.shopId)"
    if ([string]$openId.Data.issuer -cne $expectedIssuer) { throw 'CUSTOMER_SHOP_ID_MISMATCH' }
    $authorizationEndpoint = Get-OnboardingSecureDiscoveryEndpoint $openId.Data 'authorization_endpoint'
    $tokenEndpoint = Get-OnboardingSecureDiscoveryEndpoint $openId.Data 'token_endpoint'
    $logoutEndpoint = Get-OnboardingSecureDiscoveryEndpoint $openId.Data 'end_session_endpoint'
    $jwksEndpoint = Get-OnboardingSecureDiscoveryEndpoint $openId.Data 'jwks_uri'
    $graphqlEndpoint = Get-OnboardingSecureDiscoveryEndpoint $account.Data 'graphql_api'
    if ('S256' -notin @($openId.Data.code_challenge_methods_supported) -or
        'authorization_code' -notin @($openId.Data.grant_types_supported) -or
        'RS256' -notin @($openId.Data.id_token_signing_alg_values_supported)) { throw 'CUSTOMER_DISCOVERY_CAPABILITY_MISMATCH' }
    $identity = $Selected.Application.identity.customerAccount
    $callbackSuffix = if ($Selected.Profile.customerAccount.Contains('callbackSchemeSuffix')) {
        [string]$Selected.Profile.customerAccount.callbackSchemeSuffix
    } else {
        [string]$identity.callbackSchemeSuffix
    }
    $callback = "shop.$($Binding.shopify.shopId).${callbackSuffix}://$($identity.callbackHost)$($identity.callbackPath)"
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{
        issuer = $openId.Data.issuer
        authorization = $authorizationEndpoint
        token = $tokenEndpoint
        logout = $logoutEndpoint
        jwks = $jwksEndpoint
        graphql = $graphqlEndpoint
        callback = $callback
    })
    $sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))
    [pscustomobject]@{ Classification = 'PASS'; Fingerprint = [Convert]::ToHexString($sha).ToLowerInvariant(); Callback = $callback }
}
Export-ModuleMember -Function 'Get-OnboardingCustomerDiscovery'
