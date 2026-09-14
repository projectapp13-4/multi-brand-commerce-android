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
$projectDirectoryByPath = @{}
foreach ($registeredPath in $registered) {
    $projectDirectoryByPath[$registeredPath] = $registeredPath.TrimStart(':')
}
foreach ($mapping in [regex]::Matches(
    $settings,
    'project\("(?<path>:[a-z][a-z0-9-]*)"\)\.projectDir\s*=\s*file\("(?<directory>[^"\r\n]+)"\)'
)) {
    $path = [string]$mapping.Groups['path'].Value
    if ($path -notin $registered -or -not $projectDirectoryByPath.ContainsKey($path)) {
        throw "ENROLLMENT_SETTINGS_DIRECTORY_UNKNOWN:$path"
    }
    $projectDirectoryByPath[$path] = [string]$mapping.Groups['directory'].Value
}
$projectByAccessor = @{}
foreach ($registeredModule in @($registry.modules)) {
    $segments = ([string]$registeredModule.gradleProject).TrimStart(':').Split('-')
    $accessor = $segments[0] + (($segments | Select-Object -Skip 1 | ForEach-Object {
        $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1)
    }) -join '')
    $projectByAccessor[$accessor] = [string]$registeredModule.gradleProject
}
foreach ($module in @($registry.modules)) {
    $expectedDirectory = [string]$module.directory
    $actualDirectory = [string]$projectDirectoryByPath[[string]$module.gradleProject]
    if ($expectedDirectory -cne $actualDirectory -or -not (Test-Path -LiteralPath (Join-Path $repoRoot $actualDirectory) -PathType Container)) {
        throw "ENROLLMENT_DIRECTORY_MISMATCH:$($module.gradleProject)"
    }
    $buildFile = Join-Path $repoRoot "$actualDirectory\build.gradle.kts"
    $text = [System.IO.File]::ReadAllText($buildFile)
    $isApplication = $text.Contains('libs.plugins.android.application')
    $isLibrary = $text.Contains('libs.plugins.android.library')
    $applicationRole = [string]$module.role -in @('real-brand-application', 'synthetic-conformance-application')
    if ($isApplication -ne $applicationRole -or $isLibrary -eq $applicationRole) { throw "ENROLLMENT_PLUGIN_ROLE_MISMATCH:$($module.gradleProject)" }
    $actualDependencies = @(
        [regex]::Matches($text, 'projects\.(?<accessor>[A-Za-z][A-Za-z0-9]*)') |
            ForEach-Object {
                $accessor = $_.Groups['accessor'].Value
                if (-not $projectByAccessor.Contains($accessor)) { throw "UNKNOWN_PROJECT_ACCESSOR:$accessor" }
                [string]$projectByAccessor[$accessor]
            } |
            Select-Object -Unique
    )
    $allowedDependencies = @($module.allowedDirectProjects | ForEach-Object { [string]$_ })
    if ((@($actualDependencies | Sort-Object) -join "`n") -cne (@($allowedDependencies | Sort-Object) -join "`n")) {
        throw "ENROLLMENT_DEPENDENCY_MISMATCH:$($module.gradleProject)"
    }
}
$workflow = [System.IO.File]::ReadAllText((Join-Path $repoRoot '.github\workflows\android-foundation.yml'))
foreach ($task in @($registry.ciLanes[$Lane])) {
    $taskText = [string]$task
    if ($taskText -notmatch '^:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9]*$') { throw "UNSAFE_GRADLE_TASK:$taskText" }
}
if ($workflow -notmatch [regex]::Escape("Get-RegisteredGradleTasks.ps1 -Lane $Lane") + '(?! -ValidateOnly)') {
    throw "CI_LANE_NOT_EXECUTED:$Lane"
}
if (-not $ValidateOnly) { @($registry.ciLanes[$Lane]) | ForEach-Object { Write-Output ([string]$_) } }
