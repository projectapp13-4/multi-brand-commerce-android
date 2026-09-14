[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('unit', 'assemble', 'api30', 'api23')]
    [string]$Lane,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Import-Module (Join-Path $PSScriptRoot 'onboarding\Onboarding.Registry.psm1') -Force
$registry = Import-OnboardingRegistry `
    -Path (Join-Path $repoRoot 'config\onboarding\application-registry.v1.json') `
    -RepositoryRoot $repoRoot

$settings = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'settings.gradle.kts'))
$included = @(
    [regex]::Matches($settings, '["''](?<path>:[a-z][a-z0-9-]*)["'']') |
        ForEach-Object { $_.Groups['path'].Value } |
        Select-Object -Unique
)
$registered = @($registry.modules | ForEach-Object { [string]$_.gradleProject })
if ((@($included | Sort-Object) -join "`n") -cne (@($registered | Sort-Object) -join "`n")) {
    throw 'ENROLLMENT_SETTINGS_MISMATCH'
}
foreach ($module in @($registry.modules)) {
    $expectedDirectory = [string]$module.directory
    $actualDirectory = if ([string]$module.gradleProject -ceq ':synthetic') { 'apps/synthetic' } else { ([string]$module.gradleProject).TrimStart(':') }
    if ($expectedDirectory -cne $actualDirectory -or -not (Test-Path -LiteralPath (Join-Path $repoRoot $actualDirectory) -PathType Container)) {
        throw "ENROLLMENT_DIRECTORY_MISMATCH:$($module.gradleProject)"
    }
    $buildFile = Join-Path $repoRoot "$actualDirectory\build.gradle.kts"
    $text = [System.IO.File]::ReadAllText($buildFile)
    $isApplication = $text.Contains('libs.plugins.android.application')
    $applicationRole = [string]$module.role -in @('real-brand-application', 'synthetic-conformance-application')
    if ($isApplication -ne $applicationRole) { throw "ENROLLMENT_PLUGIN_ROLE_MISMATCH:$($module.gradleProject)" }
    foreach ($forbidden in @($registry.modules | Where-Object { [string]$_.role -like '*application' })) {
        if ([string]$module.gradleProject -ne [string]$forbidden.gradleProject -and
            $text -match [regex]::Escape("project($([string]$forbidden.gradleProject)") ) {
            throw "FORBIDDEN_APPLICATION_DEPENDENCY:$($module.gradleProject)"
        }
    }
}
$workflow = [System.IO.File]::ReadAllText((Join-Path $repoRoot '.github\workflows\android-foundation.yml'))
foreach ($task in @($registry.ciLanes[$Lane])) {
    $taskText = [string]$task
    if ($taskText -notmatch '^:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9]*$') { throw "UNSAFE_GRADLE_TASK:$taskText" }
    if (-not $workflow.Contains($taskText)) { throw "CI_TASK_NOT_IN_WORKFLOW:$taskText" }
}
if (-not $ValidateOnly) { @($registry.ciLanes[$Lane]) | ForEach-Object { Write-Output ([string]$_) } }
