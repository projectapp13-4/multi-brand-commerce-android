Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
function Get-OnboardingAssetLinksState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Selected,
        [scriptblock]$Transport,
        [System.Collections.IDictionary]$TrustedFingerprintsByPackage
    )
    if ([string]$Selected.Application.identity.webRoles.assetLinks.mode -ceq 'disabled') {
        return [pscustomobject]@{ Classification = 'NOT_APPLICABLE'; Fingerprint = ('0' * 64) }
    }

    $hosts = @(
        @(
            foreach ($role in @('collectionAppLink', 'productAppLink', 'orderAppLink')) {
                $link = $Selected.Application.identity.webRoles[$role]
                if ($null -ne $link) { ([uri][string]$link.origin).IdnHost.ToLowerInvariant() }
            }
        ) | Sort-Object -Unique -CaseSensitive
    )
    $expectedPackages = @(
        @($Selected.Profile.variants | ForEach-Object { [string]$_.applicationId }) |
            Sort-Object -Unique -CaseSensitive
    )
    $observations = [Collections.Generic.List[object]]::new()
    $coveredPairs = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $providerFingerprints = @{}

    foreach ($hostName in $hosts) {
        $response = Invoke-OnboardingJsonRequest `
            -Method GET `
            -Uri ([uri]"https://$hostName/.well-known/assetlinks.json") `
            -Transport $Transport
        if ($response.StatusCode -ne 200) {
            return [pscustomobject]@{ Classification = 'EXTERNALLY_BLOCKED'; Fingerprint = ('0' * 64) }
        }
        $observations.Add([ordered]@{ host = $hostName; statements = $response.Data })
        foreach ($statement in @($response.Data)) {
            if ($statement -isnot [System.Collections.IDictionary] -or
                $statement.target -isnot [System.Collections.IDictionary] -or
                [string]$statement.target.namespace -cne 'android_app' -or
                [string]$statement.target.package_name -cnotin $expectedPackages -or
                'delegate_permission/common.handle_all_urls' -cnotin @($statement.relation)) {
                continue
            }
            $fingerprints = @(
                $statement.target.sha256_cert_fingerprints |
                    ForEach-Object { ([string]$_).ToUpperInvariant() }
            )
            if ($fingerprints.Count -eq 0 -or
                @($fingerprints | Where-Object { $_ -cnotmatch '^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$' }).Count -gt 0) {
                continue
            }
            $packageName = [string]$statement.target.package_name
            $pair = "$hostName|$packageName"
            [void]$coveredPairs.Add($pair)
            if (-not $providerFingerprints.ContainsKey($pair)) {
                $providerFingerprints[$pair] = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            }
            foreach ($fingerprint in $fingerprints) { [void]$providerFingerprints[$pair].Add($fingerprint) }
        }
    }

    $canonical = Get-OnboardingCanonicalJson @($observations.ToArray())
    $sha = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($canonical))
    $fingerprint = [Convert]::ToHexString($sha).ToLowerInvariant()
    $expectedPairCount = $hosts.Count * $expectedPackages.Count
    if ($coveredPairs.Count -eq 0) {
        return [pscustomobject]@{ Classification = 'FAIL'; Fingerprint = $fingerprint }
    }
    if ($coveredPairs.Count -ne $expectedPairCount) {
        return [pscustomobject]@{ Classification = 'PARTIAL'; Fingerprint = $fingerprint }
    }
    if ($null -eq $TrustedFingerprintsByPackage) {
        return [pscustomobject]@{ Classification = 'NOT_VERIFIED'; Fingerprint = $fingerprint }
    }
    foreach ($packageName in $expectedPackages) {
        if (-not $TrustedFingerprintsByPackage.Contains($packageName)) {
            return [pscustomobject]@{ Classification = 'NOT_VERIFIED'; Fingerprint = $fingerprint }
        }
        $trusted = @(
            $TrustedFingerprintsByPackage[$packageName] |
                ForEach-Object { ([string]$_).ToUpperInvariant() }
        )
        if ($trusted.Count -eq 0 -or
            @($trusted | Where-Object { $_ -cnotmatch '^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$' }).Count -gt 0) {
            return [pscustomobject]@{ Classification = 'FAIL'; Fingerprint = $fingerprint }
        }
        foreach ($hostName in $hosts) {
            $provider = $providerFingerprints["$hostName|$packageName"]
            if (@($trusted | Where-Object { $provider.Contains($_) }).Count -eq 0) {
                return [pscustomobject]@{ Classification = 'FAIL'; Fingerprint = $fingerprint }
            }
        }
    }
    [pscustomobject]@{ Classification = 'PASS'; Fingerprint = $fingerprint }
}
Export-ModuleMember -Function 'Get-OnboardingAssetLinksState'
