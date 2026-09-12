[CmdletBinding()]
param(
    [switch] $RequireCleanWorktree
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [bool] $Passed,
        [Parameter(Mandatory)] [string] $Detail
    )

    $checks.Add([pscustomobject]@{ check = $Name; passed = $Passed; detail = $Detail })
    if (-not $Passed) {
        $failures.Add("${Name}: ${Detail}")
    }
}

$requiredFiles = @(
    'docs/phase3/README.md',
    'docs/phase3/P3-00-HANDOFF.md',
    'docs/phase3/P3-00-PRODUCT-SCOPE-AND-SLICE-READINESS.md',
    'docs/phase3/p3-00-feature-readiness.csv',
    'docs/phase3/P3-00-INFORMATION-ARCHITECTURE-AND-SCREEN-INVENTORY.md',
    'docs/phase3/p3-00-screen-inventory.csv',
    'docs/phase3/P3-00-CRITICAL-USER-FLOWS.md',
    'docs/phase3/P3-00-DESIGN-SYSTEM-AND-UX.md',
    'docs/phase3/P3-00-CONTENT-ASSET-MARKET-INVENTORY.md',
    'docs/phase3/P3-00-DATA-OWNERSHIP-AND-PERSISTENCE.md',
    'docs/phase3/PHASE-3-PRODUCT-DECISIONS.md',
    'docs/phase3/PHASE-3-IMPLEMENTATION-ROADMAP.md',
    'docs/phase3/PHASE-3-ACCEPTANCE-MATRIX.md',
    'docs/phase3/phase-3-acceptance-matrix.csv'
)
$missingFiles = @($requiredFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
Add-Check 'required P3-00 artifacts exist' ($missingFiles.Count -eq 0) $(
    if ($missingFiles.Count -eq 0) { "count=$($requiredFiles.Count)" } else { "missing=$($missingFiles -join ',')" }
)

$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
$phase3Files = @(Get-ChildItem -LiteralPath 'docs/phase3' -File | Sort-Object FullName)
$decodeFailures = [System.Collections.Generic.List[string]]::new()
$mojibakeFiles = [System.Collections.Generic.List[string]]::new()
$mojibakePattern = ([char]0x00C3).ToString() + '.|' + ([char]0x00C4).ToString() + '.|' + ([char]0x00C2).ToString() + '.'
foreach ($file in $phase3Files) {
    try {
        $text = [System.IO.File]::ReadAllText($file.FullName, $strictUtf8)
        if ([regex]::IsMatch($text, $mojibakePattern)) {
            $mojibakeFiles.Add($file.Name)
        }
    } catch {
        $decodeFailures.Add($file.Name)
    }
}
Add-Check 'phase3 files are strict UTF-8' ($decodeFailures.Count -eq 0) $(
    if ($decodeFailures.Count -eq 0) { "count=$($phase3Files.Count)" } else { "failed=$($decodeFailures -join ',')" }
)
Add-Check 'phase3 files contain no mojibake markers' ($mojibakeFiles.Count -eq 0) $(
    if ($mojibakeFiles.Count -eq 0) { 'hits=0' } else { "files=$($mojibakeFiles -join ',')" }
)

$brokenLinks = [System.Collections.Generic.List[string]]::new()
$markdownFiles = @(Get-ChildItem -LiteralPath 'docs/phase3' -Filter '*.md' -File)
foreach ($file in $markdownFiles) {
    $text = [System.IO.File]::ReadAllText($file.FullName, $strictUtf8)
    foreach ($match in [regex]::Matches($text, '\[[^\]]+\]\((?<target>[^)]+)\)')) {
        $target = $match.Groups['target'].Value.Trim().Trim('<', '>')
        if ($target -match '^(https?://|mailto:|#)') {
            continue
        }
        $localPart = ($target -split '#', 2)[0]
        if ([string]::IsNullOrWhiteSpace($localPart)) {
            continue
        }
        $decoded = [System.Uri]::UnescapeDataString($localPart)
        $resolved = Join-Path $file.DirectoryName $decoded
        if (-not (Test-Path -LiteralPath $resolved)) {
            $brokenLinks.Add("$($file.Name)->$target")
        }
    }
}
Add-Check 'phase3 local Markdown links resolve' ($brokenLinks.Count -eq 0) $(
    if ($brokenLinks.Count -eq 0) { "files=$($markdownFiles.Count)" } else { "broken=$($brokenLinks -join ';')" }
)

$canonical = @(Import-Csv -LiteralPath 'docs/phase3/phase-3-acceptance-matrix.csv' -Encoding UTF8)
$readiness = @(Import-Csv -LiteralPath 'docs/phase3/p3-00-feature-readiness.csv' -Encoding UTF8)
$canonicalById = @{}
foreach ($row in $canonical) { $canonicalById[[int]$row.id] = $row.final_status }
$readinessById = @{}
foreach ($row in $readiness) { $readinessById[[int]$row.id] = $row.final_status }
$expectedIds = @(1..24)
$matrixMismatch = [System.Collections.Generic.List[string]]::new()
foreach ($id in $expectedIds) {
    if (-not $canonicalById.ContainsKey($id) -or -not $readinessById.ContainsKey($id)) {
        $matrixMismatch.Add("missing:$id")
    } elseif ($canonicalById[$id] -ne $readinessById[$id]) {
        $matrixMismatch.Add("status:${id}:$($canonicalById[$id])!=$($readinessById[$id])")
    }
}
$matrixIdsCorrect = $canonical.Count -eq 24 -and $readiness.Count -eq 24 -and $matrixMismatch.Count -eq 0
Add-Check '24 feature rows and statuses agree' $matrixIdsCorrect $(
    if ($matrixIdsCorrect) { 'canonical=24 readiness=24 mismatches=0' } else { "canonical=$($canonical.Count) readiness=$($readiness.Count) mismatch=$($matrixMismatch -join ',')" }
)

$expectedCounts = @{
    ACCEPTED = 11
    INTENTIONALLY_DIFFERENT = 8
    DEFERRED = 2
    EXTERNALLY_BLOCKED = 2
    NOT_APPLICABLE = 1
}
$countErrors = [System.Collections.Generic.List[string]]::new()
foreach ($status in $expectedCounts.Keys) {
    $actual = @($readiness | Where-Object final_status -eq $status).Count
    if ($actual -ne $expectedCounts[$status]) {
        $countErrors.Add("$status=$actual expected=$($expectedCounts[$status])")
    }
}
Add-Check 'feature status distribution is 11/8/2/2/1' ($countErrors.Count -eq 0) $(
    if ($countErrors.Count -eq 0) { 'ACCEPTED=11 INTENTIONALLY_DIFFERENT=8 DEFERRED=2 EXTERNALLY_BLOCKED=2 NOT_APPLICABLE=1' } else { $countErrors -join ';' }
)

$requiredFeatureColumns = @(
    'id', 'feature', 'final_status', 'gurbakir_behavior', 'reference_behavior_or_difference',
    'phase2_dependency', 'external_input_owner', 'implementation_slice', 'acceptance_evidence',
    'release_consequence_if_incomplete'
)
$featureColumns = @($readiness[0].PSObject.Properties.Name)
$missingFeatureColumns = @($requiredFeatureColumns | Where-Object { $_ -notin $featureColumns })
$blankFeatureCells = @()
foreach ($row in $readiness) {
    foreach ($column in $requiredFeatureColumns) {
        if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
            $blankFeatureCells += "$($row.id):$column"
        }
    }
}
Add-Check 'feature readiness fields are complete' ($missingFeatureColumns.Count -eq 0 -and $blankFeatureCells.Count -eq 0) (
    "missingColumns=$($missingFeatureColumns.Count) blankCells=$($blankFeatureCells.Count)"
)

$screens = @(Import-Csv -LiteralPath 'docs/phase3/p3-00-screen-inventory.csv' -Encoding UTF8)
$requiredScreenColumns = @(
    'screen_id', 'purpose', 'feature_groups', 'roadmap_slice', 'entry_points', 'exit_paths',
    'navigation_arguments', 'authentication', 'state_holder', 'phase2_dependency', 'data_source',
    'persistence', 'loading_state', 'empty_state', 'success_state', 'recoverable_error_state',
    'terminal_error_state', 'offline_behavior', 'accessibility', 'localization_content',
    'analytics_policy', 'reference_evidence_or_rationale', 'release_disposition'
)
$expectedScreens = @(
    'STARTUP_GATE', 'CONDITIONAL_SETUP', 'HOME', 'CATEGORIES', 'PRODUCT_LIST', 'FILTER_SORT',
    'SEARCH', 'PRODUCT_DETAIL', 'MEDIA_VIEWER', 'WISHLIST', 'CART', 'CHECKOUT_SHEET',
    'LEGAL_SUPPORT_INDEX', 'EXTERNAL_OWNED_PAGE', 'ACCOUNT', 'HOSTED_ACCOUNT_JOURNEY',
    'PROFILE', 'ADDRESS_LIST', 'ADDRESS_FORM', 'ORDER_LIST', 'ORDER_DETAIL', 'TRACKING_EXTERNAL',
    'ACCOUNT_DELETION', 'UPDATE_NOTICE', 'HARD_UPDATE_GATE', 'LOCAL_DATA_CONTROLS', 'ROUTE_RECOVERY'
)
$screenColumns = @($screens[0].PSObject.Properties.Name)
$missingScreenColumns = @($requiredScreenColumns | Where-Object { $_ -notin $screenColumns })
$screenIds = @($screens | ForEach-Object screen_id)
$missingScreens = @($expectedScreens | Where-Object { $_ -notin $screenIds })
$duplicateScreens = @($screenIds | Group-Object | Where-Object Count -ne 1 | ForEach-Object Name)
$blankScreenCells = @()
foreach ($row in $screens) {
    foreach ($column in $requiredScreenColumns) {
        if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
            $blankScreenCells += "$($row.screen_id):$column"
        }
    }
}
$screenInventoryCorrect = (
    $screens.Count -eq $expectedScreens.Count -and
    $missingScreens.Count -eq 0 -and
    $duplicateScreens.Count -eq 0 -and
    $missingScreenColumns.Count -eq 0 -and
    $blankScreenCells.Count -eq 0
)
Add-Check '27-screen inventory is complete and unique' $screenInventoryCorrect (
    "rows=$($screens.Count) missing=$($missingScreens.Count) duplicates=$($duplicateScreens.Count) missingColumns=$($missingScreenColumns.Count) blankCells=$($blankScreenCells.Count)"
)

$roadmapText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/PHASE-3-IMPLEMENTATION-ROADMAP.md'), $strictUtf8)
$sliceText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/P3-00-PRODUCT-SCOPE-AND-SLICE-READINESS.md'), $strictUtf8)
$expectedSlices = @(0..16 | ForEach-Object { 'P3-{0:D2}' -f $_ })
$roadmapMissingSlices = @($expectedSlices | Where-Object { $roadmapText -notmatch [regex]::Escape($_) })
$readinessMissingSlices = @($expectedSlices | Where-Object { $sliceText -notmatch [regex]::Escape($_) })
Add-Check 'P3-00 through P3-16 remain represented' ($roadmapMissingSlices.Count -eq 0 -and $readinessMissingSlices.Count -eq 0) (
    "roadmapMissing=$($roadmapMissingSlices.Count) readinessMissing=$($readinessMissingSlices.Count)"
)

$handoffText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/P3-00-HANDOFF.md'), $strictUtf8)
$iaText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/P3-00-INFORMATION-ARCHITECTURE-AND-SCREEN-INVENTORY.md'), $strictUtf8)
$dataText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/P3-00-DATA-OWNERSHIP-AND-PERSISTENCE.md'), $strictUtf8)
$contentText = [System.IO.File]::ReadAllText((Resolve-Path 'docs/phase3/P3-00-CONTENT-ASSET-MARKET-INVENTORY.md'), $strictUtf8)
$boundaryChecks = @(
    ($handoffText -match 'No P3-01 Compose component')
    ($handoffText -match 'proof route/screen/controller/ViewModel')
    ($handoffText -match 'reference.*behavior/completeness evidence only')
    ($iaText -match 'completely absent until P3-06')
    ($iaText -match 'proof-surface replacement ledger')
    (
        $dataText -match 'Anonymous' -and
        $dataText -match 'CustomerAssociated' -and
        $dataText -match 'DetachPending' -and
        $dataText -match 'Quarantined'
    )
    ($dataText -match 'reference Worker.*UNKNOWN')
    ($contentText -match 'P3-01 external input blocker')
    ($contentText -match 'No row below supplies invented legal')
)
Add-Check 'scope architecture proof and blocker boundaries are explicit' (-not ($boundaryChecks -contains $false)) (
    "passed=$(@($boundaryChecks | Where-Object { $_ }).Count)/$($boundaryChecks.Count)"
)

$secretPatterns = @(
    '(?i)shpat_[A-Za-z0-9]{16,}',
    '(?i)shp(?:ss|ca|at)_[A-Za-z0-9]{16,}',
    '(?i)sk_(?:live|test)_[A-Za-z0-9]{16,}',
    'AIza[0-9A-Za-z_-]{30,}',
    '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    '(?i)(?:access_token|refresh_token|client_secret)\s*[:=]\s*["''][A-Za-z0-9._-]{16,}["'']'
)
$secretHits = [System.Collections.Generic.List[string]]::new()
foreach ($file in $phase3Files) {
    $text = [System.IO.File]::ReadAllText($file.FullName, $strictUtf8)
    foreach ($pattern in $secretPatterns) {
        if ([regex]::IsMatch($text, $pattern)) {
            $secretHits.Add("$($file.Name):$pattern")
        }
    }
}
Add-Check 'defined credential patterns detect no potential secret' ($secretHits.Count -eq 0) $(
    if ($secretHits.Count -eq 0) { 'hits=0' } else { "hits=$($secretHits -join ';')" }
)

$requiredLocal = @(
    'config/local.properties',
    'app/src/developmentDebug/google-services.json',
    'app/src/developmentRelease/google-services.json',
    'app/src/stagingDebug/google-services.json',
    'app/src/stagingRelease/google-services.json'
)
$localProblems = [System.Collections.Generic.List[string]]::new()
foreach ($path in $requiredLocal) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $localProblems.Add("missing:$path")
        continue
    }
    & git check-ignore --quiet -- $path
    if ($LASTEXITCODE -ne 0) { $localProblems.Add("notIgnored:$path") }
    $tracked = @(& git ls-files -- $path)
    if ($tracked.Count -gt 0) { $localProblems.Add("tracked:$path") }
}
Add-Check 'five ignored local configuration files remain present and untracked' ($localProblems.Count -eq 0) $(
    if ($localProblems.Count -eq 0) { 'count=5' } else { $localProblems -join ';' }
)

$changedPaths = @(
    & git diff --name-only HEAD
    & git diff --cached --name-only
    & git ls-files --others --exclude-standard
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique
$disallowedPaths = @(
    $changedPaths | Where-Object {
        -not ($_.StartsWith('docs/phase3/') -or $_ -eq 'scripts/Test-Phase3Planning.ps1')
    }
)
Add-Check 'change scope is phase3 documentation plus its validator only' ($disallowedPaths.Count -eq 0) $(
    if ($disallowedPaths.Count -eq 0) { "changed=$($changedPaths.Count)" } else { "disallowed=$($disallowedPaths -join ',')" }
)

& git diff --check
$workingDiffCheck = $LASTEXITCODE -eq 0
& git diff --cached --check
$stagedDiffCheck = $LASTEXITCODE -eq 0
Add-Check 'working and staged diffs pass whitespace checks' ($workingDiffCheck -and $stagedDiffCheck) (
    "working=$workingDiffCheck staged=$stagedDiffCheck"
)

$branch = (& git branch --show-current).Trim()
$stash = @(& git stash list)
$remotes = @(& git remote)
$unresolved = @('MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'BISECT_LOG') | Where-Object {
    Test-Path -LiteralPath (Join-Path '.git' $_)
}
$unresolved += @('rebase-merge', 'rebase-apply') | Where-Object { Test-Path -LiteralPath (Join-Path '.git' $_) }
Add-Check 'Git branch stash remote and repository state are expected' (
    $branch -eq 'main' -and $stash.Count -eq 0 -and $remotes.Count -eq 0 -and $unresolved.Count -eq 0
) "branch=$branch stash=$($stash.Count) remotes=$($remotes.Count) unresolved=$($unresolved.Count)"

if ($RequireCleanWorktree) {
    $porcelain = @(& git status --porcelain)
    Add-Check 'worktree is clean' ($porcelain.Count -eq 0) "entries=$($porcelain.Count)"
}

$checks | Format-Table -AutoSize | Out-Host
if ($failures.Count -gt 0) {
    throw "P3-00 validation failed: $($failures -join ' | ')"
}

Write-Host "P3-00 VALIDATION PASS: checks=$($checks.Count) features=$($readiness.Count) screens=$($screens.Count) slices=$($expectedSlices.Count)"
