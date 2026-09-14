Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force

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
    if ('S256' -notin @($openId.Data.code_challenge_methods_supported) -or
        'authorization_code' -notin @($openId.Data.grant_types_supported) -or
        'RS256' -notin @($openId.Data.id_token_signing_alg_values_supported)) { throw 'CUSTOMER_DISCOVERY_CAPABILITY_MISMATCH' }
    $identity = $Selected.Application.identity.customerAccount
    $callback = "shop.$($Binding.shopify.shopId).$($identity.callbackSchemeSuffix)://$($identity.callbackHost)$($identity.callbackPath)"
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ issuer = $openId.Data.issuer; graphql = $account.Data.graphql_api; callback = $callback })
    $sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))
    [pscustomobject]@{ Classification = 'PASS'; Fingerprint = [Convert]::ToHexString($sha).ToLowerInvariant(); Callback = $callback }
}
Export-ModuleMember -Function 'Get-OnboardingCustomerDiscovery'
