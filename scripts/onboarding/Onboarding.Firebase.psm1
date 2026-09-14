Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
function Get-OnboardingFirebaseState {
    [CmdletBinding()]
    param($Selected, $Binding, [string]$Token, [scriptblock]$Transport)
    if ([string]$Selected.Profile.firebase.mode -ceq 'disabled') { return [pscustomobject]@{ Classification = 'NOT_APPLICABLE'; Fingerprint = ('0' * 64) } }
    if ($null -eq $Binding.firebase) { throw 'MISSING_FIREBASE_BINDING' }
    if ([string]::IsNullOrWhiteSpace($Token)) { throw 'MISSING_FIREBASE_ACCESS_TOKEN' }
    $project = [string]$Binding.firebase.projectId
    $response = Invoke-OnboardingJsonRequest -Method GET -Uri ([uri]"https://firebase.googleapis.com/v1beta1/projects/$project/androidApps?pageSize=100") -Headers @{ Authorization = "Bearer $Token" } -Transport $Transport
    if ($response.StatusCode -ne 200) { throw 'FIREBASE_MANAGEMENT_FAILURE' }
    $apps = @($response.Data.apps)
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
