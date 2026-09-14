Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
function Get-OnboardingAssetLinksState {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Selected, [scriptblock]$Transport)
    $host = ([uri][string]$Selected.Application.identity.webRoles.collectionAppLink.origin).Host
    $response = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]"https://$host/.well-known/assetlinks.json") -Transport $Transport
    if ($response.StatusCode -ne 200) { return [pscustomobject]@{ Classification = 'EXTERNALLY_BLOCKED'; Fingerprint = ('0' * 64) } }
    $canonical = Get-OnboardingCanonicalJson $response.Data
    $sha = [System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))
    [pscustomobject]@{ Classification = 'PASS'; Fingerprint = [Convert]::ToHexString($sha).ToLowerInvariant() }
}
Export-ModuleMember -Function 'Get-OnboardingAssetLinksState'
