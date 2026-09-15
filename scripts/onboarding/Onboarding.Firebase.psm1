Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
function Get-OnboardingFirebaseState {
    [CmdletBinding()]
    param($Selected, $Binding, [string]$Token, [scriptblock]$Transport)
    if ([string]$Selected.Profile.firebase.mode -ceq 'disabled') { return [pscustomobject]@{ Classification = 'NOT_APPLICABLE'; Fingerprint = ('0' * 64) } }
    if ($null -eq $Binding.firebase) { throw 'MISSING_FIREBASE_BINDING' }
    if ([string]::IsNullOrWhiteSpace($Token)) { throw 'MISSING_FIREBASE_ACCESS_TOKEN' }
    if ([string]$Binding.application -cne [string]$Selected.Application.key -or
        [string]$Binding.profile -cne [string]$Selected.Profile.key) {
        throw 'FIREBASE_BINDING_TARGET_MISMATCH'
    }
    $project = [string]$Binding.firebase.projectId
    if ($project -cnotmatch '^[a-z][a-z0-9-]{4,28}[a-z0-9]$') { throw 'INVALID_FIREBASE_BINDING' }
    $apps = [System.Collections.Generic.List[object]]::new()
    $pageToken = ''
    $seenTokens = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    for ($page = 1; $page -le 5; $page++) {
        $query = 'pageSize=100'
        if ($pageToken.Length -gt 0) {
            $query += '&pageToken=' + [uri]::EscapeDataString($pageToken)
        }
        $response = Invoke-OnboardingJsonRequest `
            -Method GET `
            -Uri ([uri]"https://firebase.googleapis.com/v1beta1/projects/$project/androidApps?$query") `
            -Headers @{
                Authorization = "Bearer $Token"
                'x-goog-user-project' = $project
            } `
            -Transport $Transport
        if ($response.StatusCode -ne 200) { throw 'FIREBASE_MANAGEMENT_FAILURE' }
        foreach ($app in @($response.Data.apps)) { $apps.Add($app) }
        $nextPageToken = if ($response.Data -is [System.Collections.IDictionary] -and $response.Data.Contains('nextPageToken')) {
            [string]$response.Data.nextPageToken
        } else {
            ''
        }
        if ([string]::IsNullOrWhiteSpace($nextPageToken)) { break }
        Assert-OnboardingText -Value $nextPageToken -Field 'firebase.nextPageToken' -MaximumLength 2048
        if ($page -eq 5 -or -not $seenTokens.Add($nextPageToken) -or $nextPageToken -ceq $pageToken) {
            throw 'FIREBASE_PAGINATION_INCOMPLETE'
        }
        $pageToken = $nextPageToken
    }
    foreach ($variant in @($Selected.Profile.variants)) {
        $expectedId = [string]$Binding.firebase.androidAppIdsByVariant[[string]$variant.name]
        if (@($apps | Where-Object { [string]$_.appId -ceq $expectedId -and [string]$_.packageName -ceq [string]$variant.applicationId }).Count -ne 1) {
            throw 'FIREBASE_APP_IDENTITY_MISMATCH'
        }
    }
    $canonical = Get-OnboardingCanonicalJson (@($apps | Sort-Object appId | ForEach-Object { [ordered]@{ appId = $_.appId; packageName = $_.packageName; projectId = $project } }))
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    [pscustomobject]@{ Classification = 'PASS'; Fingerprint = $hash }
}
Export-ModuleMember -Function 'Get-OnboardingFirebaseState'
