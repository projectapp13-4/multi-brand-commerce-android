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
    $expectedPackages = @($Selected.Profile.variants | ForEach-Object { [string]$_.applicationId })
    $matchingStatements = @(
        foreach ($statement in @($response.Data)) {
            if ($statement -isnot [System.Collections.IDictionary] -or
                $statement.target -isnot [System.Collections.IDictionary] -or
                [string]$statement.target.namespace -cne 'android_app' -or
                [string]$statement.target.package_name -cnotin $expectedPackages -or
                'delegate_permission/common.handle_all_urls' -cnotin @($statement.relation)) {
                continue
            }
            $fingerprints = @($statement.target.sha256_cert_fingerprints)
            if ($fingerprints.Count -eq 0 -or @($fingerprints | Where-Object { [string]$_ -cnotmatch '^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$' }).Count -gt 0) {
                continue
            }
            $statement
        }
    )
    $classification = if ($matchingStatements.Count -gt 0) { 'PASS' } else { 'FAIL' }
    [pscustomobject]@{ Classification = $classification; Fingerprint = [Convert]::ToHexString($sha).ToLowerInvariant() }
}
Export-ModuleMember -Function 'Get-OnboardingAssetLinksState'
