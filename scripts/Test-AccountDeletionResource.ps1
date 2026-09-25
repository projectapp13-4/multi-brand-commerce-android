[CmdletBinding()]
param(
    [string]$Application = 'gurbakir',
    [string]$Profile = 'development'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'onboarding/Onboarding.Registry.psm1') -Force
$registry = Import-OnboardingRegistry `
    -Path (Join-Path $repositoryRoot 'config/onboarding/application-registry.v1.json') `
    -RepositoryRoot $repositoryRoot
$selected = Get-OnboardingApplicationProfile -Registry $registry -Application $Application -Profile $Profile
if ([string]$selected.Profile.customerAccount.mode -ceq 'disabled') {
    throw 'ACCOUNT_DISABLED'
}
$legal = $selected.Application.identity.webRoles.legalSupport
if ($null -eq $legal -or -not $legal.paths.Contains('accountDeletionRequest')) {
    throw 'MISSING_DELETION_RESOURCE'
}
$url = [string]$legal.origin + [string]$legal.paths.accountDeletionRequest
$response = Invoke-WebRequest -Uri $url -MaximumRedirection 0 -SkipHttpErrorCheck -TimeoutSec 20
if ([int]$response.StatusCode -ne 200) {
    throw ('DELETION_RESOURCE_HTTP_' + [int]$response.StatusCode)
}
$body = [string]$response.Content
$merchantName = [string]$selected.Application.identity.displayName
if (($body -replace '\s', '') -notmatch [regex]::Escape(($merchantName -replace '\s', ''))) {
    throw 'DELETION_RESOURCE_MERCHANT_MISMATCH'
}
if ($body -notmatch '(?i)hesap.{0,30}silme|account.{0,30}delet' -or
    $body -notmatch '(?i)<form\b' -or
    $body -notmatch 'contact\[body\]' -or
    $body -notmatch '(?i)type=["'']submit["'']') {
    throw 'DELETION_RESOURCE_AFFORDANCE_MISSING'
}
Write-Output ("PASS: signed-out public deletion page and request form are reachable for {0}/{1} (HTTP 200)." -f $Application, $Profile)
Write-Output 'NOT VERIFIED: form submission, merchant receipt, acknowledgement, retention decision, or remote erasure.'
