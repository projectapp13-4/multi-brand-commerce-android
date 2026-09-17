[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Get-Location).Path,
    [switch]$SelfTest,
    [switch]$RequireCleanWorktree
)

$ErrorActionPreference = "Stop"

function New-CheckResult {
    param(
        [string]$Id,
        [bool]$Passed,
        [string]$Detail
    )

    [pscustomobject]@{
        Id = $Id
        Passed = $Passed
        Detail = $Detail
    }
}

function Get-RepositoryFiles {
    param([string]$Root)

    $gitDirectory = Join-Path $Root ".git"
    if (Test-Path -LiteralPath $gitDirectory) {
        $files = @(& git -C $Root ls-files)
        if ($LASTEXITCODE -ne 0) {
            throw "git ls-files failed for $Root"
        }
        return @($files | ForEach-Object { $_ -replace "/", "\" })
    }

    return @(
        Get-ChildItem -LiteralPath $Root -Recurse -File -Force |
            ForEach-Object { [System.IO.Path]::GetRelativePath($Root, $_.FullName) }
    )
}

function Get-TextContent {
    param(
        [string]$Root,
        [string[]]$RelativePaths
    )

    $binaryExtensions = @(
        ".apk", ".aab", ".bin", ".class", ".dex", ".gif", ".ico", ".jar", ".jpeg",
        ".jpg", ".keystore", ".otf", ".p12", ".pdf", ".png", ".so", ".ttf", ".webp", ".zip"
    )
    $builder = [System.Text.StringBuilder]::new()

    foreach ($relativePath in $RelativePaths) {
        if ([System.IO.Path]::GetExtension($relativePath).ToLowerInvariant() -in $binaryExtensions) {
            continue
        }
        $fullPath = Join-Path $Root $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            continue
        }
        try {
            [void]$builder.AppendLine((Get-Content -LiteralPath $fullPath -Raw))
        } catch {
            throw "Unable to read tracked text file '$relativePath': $($_.Exception.Message)"
        }
    }

    return $builder.ToString()
}

function Invoke-PublicReadinessValidation {
    param(
        [string]$Root,
        [switch]$CheckCleanWorktree
    )

    $rootPath = (Resolve-Path -LiteralPath $Root).Path
    $gitDirectory = Join-Path $rootPath ".git"
    $files = @(Get-RepositoryFiles -Root $rootPath)
    $normalizedFiles = @($files | ForEach-Object { $_ -replace "/", "\" })
    $results = [System.Collections.Generic.List[object]]::new()

    $requiredFiles = @(
        "LICENSE",
        "TRADEMARKS.md",
        "ASSET-LICENSES.md",
        "THIRD_PARTY_NOTICES.md",
        "SECURITY.md",
        "docs\reference-model\COMMERCE-BEHAVIOR.md",
        "docs\reference-model\SYSTEM-BOUNDARIES-AND-LIMITATIONS.md"
    )
    $missingRequired = @($requiredFiles | Where-Object { $_ -notin $normalizedFiles })
    $requiredDetail = if ($missingRequired.Count -eq 0) { "all required public files are present" } else { "missing: $($missingRequired -join ', ')" }
    $results.Add((New-CheckResult "required-public-files" ($missingRequired.Count -eq 0) $requiredDetail))

    $forbiddenPrefixes = @(
        ".agents\skills\navigate-gurbakir-evidence\",
        "docs\reference-apk\",
        "docs\preparation\evidence\"
    )
    $forbiddenExact = @(
        ".codex\config.toml",
        "docs\preparation\APK-REFERENCE-GUIDE.md",
        "docs\preparation\GAP-AND-REUSE-MATRIX.md",
        "docs\preparation\gap-and-reuse-matrix.csv",
        "docs\preparation\PROJECT-REFERENCE-INDEX.md",
        "docs\preparation\project-reference-index.json",
        "scripts\Test-Preparation.ps1"
    )
    $forbiddenFound = @()
    foreach ($file in $normalizedFiles) {
        $isForbidden = $file -in $forbiddenExact
        foreach ($prefix in $forbiddenPrefixes) {
            if ($file.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                $isForbidden = $true
            }
        }
        if ($isForbidden) {
            $forbiddenFound += $file
        }
    }
    $forbiddenDetail = if ($forbiddenFound.Count -eq 0) { "no private/forensic paths are tracked" } else { "found: $($forbiddenFound -join ', ')" }
    $results.Add((New-CheckResult "forbidden-private-material" ($forbiddenFound.Count -eq 0) $forbiddenDetail))

    $settingsPath = Join-Path $rootPath "settings.gradle.kts"
    $settings = if (Test-Path -LiteralPath $settingsPath) { Get-Content -LiteralPath $settingsPath -Raw } else { "" }
    $rootNameOk = $settings -match 'rootProject\.name\s*=\s*"multi-brand-commerce-android"'
    $results.Add((New-CheckResult "neutral-root-project-name" $rootNameOk "rootProject.name must be multi-brand-commerce-android"))

    $includeMatch = [regex]::Match($settings, '(?s)include\s*\((.*?)\)')
    $actualModules = @()
    if ($includeMatch.Success) {
        $actualModules = @([regex]::Matches($includeMatch.Groups[1].Value, '"(:[A-Za-z0-9-]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    }
    $registryPath = Join-Path $rootPath 'config\onboarding\application-registry.v1.json'
    $expectedModules = if (Test-Path -LiteralPath $registryPath) {
        @((Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json -AsHashtable).modules |
            ForEach-Object { [string]$_.gradleProject } | Sort-Object -Unique)
    } else {
        @(':account', ':app', ':checkout', ':firebase', ':foundation', ':mobile-core', ':storefront', ':synthetic', ':trial')
    }
    $moduleDifference = @(Compare-Object -ReferenceObject $expectedModules -DifferenceObject $actualModules)
    $moduleGraphOk = $moduleDifference.Count -eq 0 -and
        $settings -match 'project\(":synthetic"\)\.projectDir\s*=\s*file\("apps/synthetic"\)' -and
        $settings -match 'project\(":trial"\)\.projectDir\s*=\s*file\("apps/trial"\)'
    $results.Add((New-CheckResult "exact-module-graph" $moduleGraphOk "expected explicitly enrolled modules and physical synthetic/trial mappings"))

    $text = Get-TextContent -Root $rootPath -RelativePaths $normalizedFiles
    $publicText = Get-TextContent -Root $rootPath -RelativePaths @(
        $normalizedFiles | Where-Object {
            $_ -notin @('scripts\Test-PublicReadiness.ps1', 'scripts\Test-PublicReadiness.Tests.ps1')
        }
    )
    $localPathPatterns = @(
        '(?i)[A-Z]:[\\/]Users[\\/][^\\/\r\n]+[\\/]',
        '(?i)Gur Bakir New Version',
        '(?i)Business Partner APK'
    )
    $localPathFound = @($localPathPatterns | Where-Object { $publicText -match $_ })
    $localPathDetail = if ($localPathFound.Count -eq 0) { "no workstation-specific paths found" } else { "one or more forbidden path patterns found" }
    $results.Add((New-CheckResult "no-workstation-paths" ($localPathFound.Count -eq 0) $localPathDetail))

    $privateUrlFound = $publicText -match '(?i)https://github\.com/projectapp13-4/Shopify-app-GurBakir'
    $results.Add((New-CheckResult "no-private-repository-urls" (-not $privateUrlFound) "historical private repository URLs must not be published"))

    $unapprovedReferenceMarks = @('Kristal Kutu', 'Baguette')
    $foundReferenceMarks = @($unapprovedReferenceMarks | Where-Object { $publicText.Contains($_) })
    $referenceMarkDetail = if ($foundReferenceMarks.Count -eq 0) { "no competitor/reference brand names found" } else { "found: $($foundReferenceMarks -join ', ')" }
    $results.Add((New-CheckResult "no-unapproved-reference-marks" ($foundReferenceMarks.Count -eq 0) $referenceMarkDetail))

    $forensicTextPatterns = @(
        '(?i)docs[\\/]reference-apk',
        '(?i)navigate-gurbakir-evidence',
        '(?i)analysis[\\/]05_decompiled',
        '(?i)Business Partner APK'
    )
    $forensicTextFound = @($forensicTextPatterns | Where-Object { $publicText -match $_ })
    $forensicTextDetail = if ($forensicTextFound.Count -eq 0) { "no private forensic paths or helper names found in public text" } else { "one or more private forensic references found" }
    $results.Add((New-CheckResult "no-forensic-text-references" ($forensicTextFound.Count -eq 0) $forensicTextDetail))

    $rightsPath = Join-Path $rootPath "ASSET-LICENSES.md"
    $rights = if (Test-Path -LiteralPath $rightsPath) { Get-Content -LiteralPath $rightsPath -Raw } else { "" }
    $rightsResolved = $rights.Length -gt 0 -and $rights -notmatch '(?i)\bUNRESOLVED\b' -and $rights -match '(?i)publication gate:\s*resolved'
    $results.Add((New-CheckResult "asset-publication-rights" $rightsResolved "ASSET-LICENSES.md must declare Publication gate: RESOLVED and contain no UNRESOLVED entry"))

    $workflowPath = Join-Path $rootPath ".github\workflows\android-foundation.yml"
    $workflow = if (Test-Path -LiteralPath $workflowPath) { Get-Content -LiteralPath $workflowPath -Raw } else { "" }
    $triggersOk = $workflow -match '(?m)^\s{2}pull_request:\s*$' -and $workflow -match '(?m)^\s{2}push:\s*$' -and $workflow -match '(?m)^\s{2}workflow_dispatch:\s*$'
    $results.Add((New-CheckResult "canonical-workflow-triggers" $triggersOk "pull_request, main push, and workflow_dispatch must remain enabled"))

    $permissionsOk = $workflow -match '(?m)^permissions:\s*$' -and $workflow -notmatch '(?m)^\s+[A-Za-z-]+:\s*write\s*$'
    $results.Add((New-CheckResult "read-only-workflow-permissions" $permissionsOk "workflow permissions must not grant write access"))

    $unsafeTrigger = $workflow -match '(?m)^\s{2}(pull_request_target|workflow_run):\s*$'
    $nonTokenSecret = [regex]::Matches($workflow, 'secrets\.([A-Za-z0-9_]+)') | Where-Object { $_.Groups[1].Value -ne 'GITHUB_TOKEN' }
    $externalForkSafe = -not $unsafeTrigger -and @($nonTokenSecret).Count -eq 0
    $results.Add((New-CheckResult "external-fork-workflow-safety" $externalForkSafe "no privileged trigger or repository secret may be exposed to pull requests"))

    $usesLines = @([regex]::Matches($workflow, '(?m)^\s*-?\s*uses:\s*([^\s#]+)') | ForEach-Object { $_.Groups[1].Value })
    $unpinnedActions = @($usesLines | Where-Object { -not $_.StartsWith('./') -and $_ -notmatch '@[0-9a-fA-F]{40}$' })
    $actionsPinned = $usesLines.Count -gt 0 -and $unpinnedActions.Count -eq 0
    $actionsDetail = if ($actionsPinned) { "all external Actions use immutable 40-character SHAs" } else { "unpinned: $($unpinnedActions -join ', ')" }
    $results.Add((New-CheckResult "sha-pinned-actions" $actionsPinned $actionsDetail))

    $setupAndroidActions = [regex]::Matches($workflow, '(?m)^\s+-?\s*uses:\s*android-actions/setup-android@')
    $boundedSetupAndroidActions = [regex]::Matches(
        $workflow,
        '(?m)^\s+-?\s*uses:\s*android-actions/setup-android@[^\r\n#]+(?:\s+#[^\r\n]*)?\r?\n\s+with:\s*\r?\n\s+packages:\s*platform-tools\s*$'
    )
    $androidSdkBootstrapOk = $setupAndroidActions.Count -gt 0 -and $boundedSetupAndroidActions.Count -eq $setupAndroidActions.Count
    $results.Add((New-CheckResult "bounded-android-sdk-bootstrap" $androidSdkBootstrapOk "every setup-android action must explicitly install platform-tools instead of the removed legacy tools package"))

    $guardedGradleLanes = @('unit', 'assemble', 'api30', 'api23') | Where-Object {
        $lane = [regex]::Escape($_)
        $workflow -match (
            '(?m)^\s+\$ErrorActionPreference\s*=\s*''Stop''\s*$\r?\n' +
            ('\s+\$tasks\s*=\s*@\(\./scripts/Get-RegisteredGradleTasks\.ps1\s+-Lane\s+{0}\)\s*$\r?\n' -f $lane) +
            '\s+if\s*\(-not\s+\$\?\)\s*\{\s*throw\s+[^\r\n]+\}\s*$\r?\n' +
            '\s+if\s*\(\$tasks\.Count\s+-eq\s+0\)\s*\{\s*throw\s+[^\r\n]+\}\s*$'
        )
    }
    $gradleLaneResolutionOk = @($guardedGradleLanes).Count -eq 4
    $results.Add((New-CheckResult "nonempty-gradle-lane-resolution" $gradleLaneResolutionOk "every registry-driven Gradle lane must use PowerShell resolver status and fail on resolver errors or an empty task list"))

    $checkoutSafe = $workflow -match '(?m)^\s+persist-credentials:\s*false\s*$'
    $results.Add((New-CheckResult "checkout-credentials-disabled" $checkoutSafe "checkout must not persist GitHub credentials"))

    $gitleaksIgnorePath = Join-Path $rootPath ".gitleaksignore"
    $ignoreLines = if (Test-Path -LiteralPath $gitleaksIgnorePath) { @(Get-Content -LiteralPath $gitleaksIgnorePath | Where-Object { $_.Trim().Length -gt 0 }) } else { @() }
    $results.Add((New-CheckResult "empty-gitleaks-ignore" ($ignoreLines.Count -eq 0) "fresh public history must not carry legacy leak exceptions"))

    $appBuildPath = Join-Path $rootPath "app\build.gradle.kts"
    $appBuild = if (Test-Path -LiteralPath $appBuildPath) { Get-Content -LiteralPath $appBuildPath -Raw } else { "" }
    $developmentProjectionPath = Join-Path $rootPath 'config\onboarding\generated\gurbakir\development.properties'
    $stagingProjectionPath = Join-Path $rootPath 'config\onboarding\generated\gurbakir\staging.properties'
    $developmentProjection = if (Test-Path -LiteralPath $developmentProjectionPath) { Get-Content -LiteralPath $developmentProjectionPath -Raw } else { "" }
    $stagingProjection = if (Test-Path -LiteralPath $stagingProjectionPath) { Get-Content -LiteralPath $stagingProjectionPath -Raw } else { "" }
    $onboardingIdentityText = Get-TextContent -Root $rootPath -RelativePaths @(
        'config\onboarding\application-registry.v1.json',
        'config\onboarding\generated\gurbakir\development.properties',
        'config\onboarding\generated\gurbakir\staging.properties'
    )
    $requiredAppBuildIdentity = @(
        'namespace = "com.gurbakir.mobile"',
        'applicationId = "com.gurbakir.mobile.unconfigured"',
        'applicationIdSuffix = ".debug"'
    )
    $requiredProjectedAppIdentity = @(
        'android.applicationId.debug=com.gurbakir.mobile.dev.debug',
        'android.applicationId.release=com.gurbakir.mobile.dev',
        'android.applicationId.debug=com.gurbakir.mobile.staging.debug',
        'android.applicationId.release=com.gurbakir.mobile.staging'
    )
    $missingAppIdentity = @($requiredAppBuildIdentity | Where-Object { -not $appBuild.Contains($_) })
    $missingAppIdentity += @($requiredProjectedAppIdentity | Where-Object { -not $onboardingIdentityText.Contains($_) })
    $appIdentityDetail = if ($missingAppIdentity.Count -eq 0) { "Gurbakir namespace and non-production application IDs remain exact" } else { "missing: $($missingAppIdentity -join ', ')" }
    $results.Add((New-CheckResult "gurbakir-application-identities" (@($missingAppIdentity).Count -eq 0) $appIdentityDetail))

    $sourceText = Get-TextContent -Root $rootPath -RelativePaths @(
        $normalizedFiles | Where-Object { $_ -match '^(app|account|mobile-core)\\' }
    )
    $persistenceLiterals = @(
        'gurbakir-local.db',
        'gurbakir_secure_cart_development',
        'gurbakir.cart.development.v1',
        'gurbakir_secure_cart_staging',
        'gurbakir.cart.staging.v1',
        'gurbakir_secure_customer_session_development',
        'gurbakir.customer.session.development.v1',
        'gurbakir_secure_customer_session_staging',
        'gurbakir.customer.session.staging.v1'
    )
    $identitySourceText = $sourceText + $onboardingIdentityText
    $missingPersistence = @($persistenceLiterals | Where-Object { -not $identitySourceText.Contains($_) })
    $persistenceDetail = if ($missingPersistence.Count -eq 0) { "protected persistence identities remain present" } else { "missing: $($missingPersistence -join ', ')" }
    $results.Add((New-CheckResult "gurbakir-persistence-identities" ($missingPersistence.Count -eq 0) $persistenceDetail))

    $manifestPath = Join-Path $rootPath "app\src\main\AndroidManifest.xml"
    $manifest = if (Test-Path -LiteralPath $manifestPath) { Get-Content -LiteralPath $manifestPath -Raw } else { "" }
    $linkPaths = @(
        'web.collectionAppLinkPathPrefix=/collections/',
        'web.productAppLinkPathPrefix=/apps/mobile/products/',
        'web.orderAppLinkPathPrefix=/apps/mobile/orders/'
    )
    $oauthWiringOk = $appBuild.Contains('manifestPlaceholders["appAuthRedirectScheme"] = profile.redirectScheme()') -and
        $appBuild.Contains('?: "shop.unconfigured.gurbakir"')
    $missingManifestRoles = @(
        '${collectionAppLinkHost}', '${collectionAppLinkPathPrefix}',
        '${productAppLinkHost}', '${productAppLinkPathPrefix}',
        '${orderAppLinkHost}', '${orderAppLinkPathPrefix}'
    ) | ForEach-Object { $manifest.Contains($_) } | Where-Object { -not $_ }
    $projectionLinksOk = $true
    foreach ($projection in @($developmentProjection, $stagingProjection)) {
        if (-not $projection.Contains('web.collectionAppLinkOrigin=https://gurbakir.com') -or
            -not $projection.Contains('web.productAppLinkOrigin=https://gurbakir.com') -or
            -not $projection.Contains('web.orderAppLinkOrigin=https://gurbakir.com') -or
            @($linkPaths | Where-Object { -not $projection.Contains($_) }).Count -ne 0) {
            $projectionLinksOk = $false
        }
    }
    $linksOk = $projectionLinksOk -and
        @($missingManifestRoles).Count -eq 0 -and
        $oauthWiringOk
    $results.Add((New-CheckResult "gurbakir-oauth-app-links" $linksOk "OAuth placeholder wiring, fail-closed scheme, and three Gurbakir App Link paths must remain exact"))

    $firebaseOk = $appBuild.Contains('FIREBASE_CONFIGURED') -and $sourceText.Contains('BuildConfig.FIREBASE_CONFIGURED')
    $results.Add((New-CheckResult "gurbakir-firebase-selection" $firebaseOk "Gurbakir app-owned Firebase readiness and selection must remain present"))

    $userAgentOk = $developmentProjection.Contains('app.customerAccountUserAgent=Gurbakir-Android') -and
        $stagingProjection.Contains('app.customerAccountUserAgent=Gurbakir-Android') -and
        $appBuild.Contains('profile.projectionValue("app.customerAccountUserAgent")') -and
        $sourceText.Contains('BuildConfig.CUSTOMER_ACCOUNT_USER_AGENT')
    $results.Add((New-CheckResult "gurbakir-client-user-agent" $userAgentOk "compatibility-sensitive Gurbakir-Android user agent must remain exact"))

    $referenceDocs = @(
        (Join-Path $rootPath "docs\reference-model\COMMERCE-BEHAVIOR.md")
        (Join-Path $rootPath "docs\reference-model\SYSTEM-BOUNDARIES-AND-LIMITATIONS.md")
    )
    $referenceBoundaryOk = $true
    foreach ($referenceDoc in $referenceDocs) {
        if (-not (Test-Path -LiteralPath $referenceDoc) -or (Get-Content -LiteralPath $referenceDoc -Raw) -notmatch '(?i)non-authoritative|not a specification|subordinate to') {
            $referenceBoundaryOk = $false
        }
    }
    $results.Add((New-CheckResult "reference-model-authority-boundary" $referenceBoundaryOk "both neutral reference documents must declare their non-authoritative status"))

    if ($CheckCleanWorktree -and (Test-Path -LiteralPath $gitDirectory)) {
        $status = @(& git -C $rootPath status --porcelain=v1 --untracked-files=all)
        $cleanDetail = if ($status.Count -eq 0) { "worktree is clean" } else { "worktree has $($status.Count) changed path(s)" }
        $results.Add((New-CheckResult "clean-worktree" ($status.Count -eq 0) $cleanDetail))
    }

    return @($results)
}

function New-SelfTestFixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("public-readiness-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $root | Out-Null

    $content = @{
        "LICENSE" = "Apache License`n"
        "TRADEMARKS.md" = "Marks remain with their owners.`n"
        "ASSET-LICENSES.md" = "Publication gate: RESOLVED`nAll listed material has a recorded disposition.`n"
        "THIRD_PARTY_NOTICES.md" = "Dependency licenses remain upstream.`n"
        "SECURITY.md" = "Report vulnerabilities privately.`n"
        ".gitleaksignore" = ""
        "docs/reference-model/COMMERCE-BEHAVIOR.md" = "Status: non-authoritative historical reference`n"
        "docs/reference-model/SYSTEM-BOUNDARIES-AND-LIMITATIONS.md" = "Status: non-authoritative historical reference`n"
        "settings.gradle.kts" = @'
rootProject.name = "multi-brand-commerce-android"
include(
    ":app",
    ":synthetic",
    ":trial",
    ":mobile-core",
    ":foundation",
    ":storefront",
    ":account",
    ":checkout",
    ":firebase"
)
project(":synthetic").projectDir = file("apps/synthetic")
project(":trial").projectDir = file("apps/trial")
'@
        ".github/workflows/android-foundation.yml" = @'
name: Android foundation
on:
  pull_request:
  push:
    branches: [main]
  workflow_dispatch:
permissions:
  contents: read
  pull-requests: read
jobs:
  validate:
    steps:
      - uses: actions/checkout@1111111111111111111111111111111111111111
        with:
          persist-credentials: false
      - uses: android-actions/setup-android@2222222222222222222222222222222222222222
        with:
          packages: platform-tools
      - name: Unit lane
        shell: pwsh
        run: |
          $ErrorActionPreference = 'Stop'
          $tasks = @(./scripts/Get-RegisteredGradleTasks.ps1 -Lane unit)
          if (-not $?) { throw "Failed to resolve the unit Gradle lane." }
          if ($tasks.Count -eq 0) { throw "No Gradle tasks were resolved for the unit lane." }
          & ./gradlew --no-daemon @tasks
      - name: Assemble lane
        shell: pwsh
        run: |
          $ErrorActionPreference = 'Stop'
          $tasks = @(./scripts/Get-RegisteredGradleTasks.ps1 -Lane assemble)
          if (-not $?) { throw "Failed to resolve the assemble Gradle lane." }
          if ($tasks.Count -eq 0) { throw "No Gradle tasks were resolved for the assemble lane." }
          & ./gradlew --no-daemon @tasks
      - name: API 30 lane
        shell: pwsh
        run: |
          $ErrorActionPreference = 'Stop'
          $tasks = @(./scripts/Get-RegisteredGradleTasks.ps1 -Lane api30)
          if (-not $?) { throw "Failed to resolve the api30 Gradle lane." }
          if ($tasks.Count -eq 0) { throw "No Gradle tasks were resolved for the api30 lane." }
          & ./gradlew --no-daemon @tasks
      - name: API 23 lane
        shell: pwsh
        run: |
          $ErrorActionPreference = 'Stop'
          $tasks = @(./scripts/Get-RegisteredGradleTasks.ps1 -Lane api23)
          if (-not $?) { throw "Failed to resolve the api23 Gradle lane." }
          if ($tasks.Count -eq 0) { throw "No Gradle tasks were resolved for the api23 lane." }
          & ./gradlew --no-daemon @tasks
'@
        "app/build.gradle.kts" = @'
namespace = "com.gurbakir.mobile"
applicationId = "com.gurbakir.mobile.unconfigured"
applicationIdSuffix = ".debug"
applicationId = releaseApplicationId
buildConfigField("boolean", "FIREBASE_CONFIGURED", "false")
field("CUSTOMER_ACCOUNT_USER_AGENT", profile.projectionValue("app.customerAccountUserAgent"))
fun redirectScheme() = configuredScheme ?: "shop.unconfigured.gurbakir"
manifestPlaceholders["appAuthRedirectScheme"] = profile.redirectScheme()
'@
        "app/src/main/AndroidManifest.xml" = @'
<manifest><application><activity>
<data android:scheme="${appAuthRedirectScheme}" />
<data android:host="${collectionAppLinkHost}" android:pathPrefix="${collectionAppLinkPathPrefix}" />
<data android:host="${productAppLinkHost}" android:pathPrefix="${productAppLinkPathPrefix}" />
<data android:host="${orderAppLinkHost}" android:pathPrefix="${orderAppLinkPathPrefix}" />
</activity></application></manifest>
'@
        "app/src/main/kotlin/Identity.kt" = @'
val configured = BuildConfig.FIREBASE_CONFIGURED
val userAgent = BuildConfig.CUSTOMER_ACCOUNT_USER_AGENT
'@
        "config/onboarding/generated/gurbakir/development.properties" = @'
android.applicationId.debug=com.gurbakir.mobile.dev.debug
android.applicationId.release=com.gurbakir.mobile.dev
app.databaseName=gurbakir-local.db
app.cartPreferences=gurbakir_secure_cart_development
app.cartKeyAlias=gurbakir.cart.development.v1
app.customerPreferences=gurbakir_secure_customer_session_development
app.customerKeyAlias=gurbakir.customer.session.development.v1
app.customerAccountUserAgent=Gurbakir-Android
web.collectionAppLinkOrigin=https://gurbakir.com
web.collectionAppLinkPathPrefix=/collections/
web.productAppLinkOrigin=https://gurbakir.com
web.productAppLinkPathPrefix=/apps/mobile/products/
web.orderAppLinkOrigin=https://gurbakir.com
web.orderAppLinkPathPrefix=/apps/mobile/orders/
'@
        "config/onboarding/generated/gurbakir/staging.properties" = @'
android.applicationId.debug=com.gurbakir.mobile.staging.debug
android.applicationId.release=com.gurbakir.mobile.staging
app.databaseName=gurbakir-local.db
app.cartPreferences=gurbakir_secure_cart_staging
app.cartKeyAlias=gurbakir.cart.staging.v1
app.customerPreferences=gurbakir_secure_customer_session_staging
app.customerKeyAlias=gurbakir.customer.session.staging.v1
app.customerAccountUserAgent=Gurbakir-Android
web.collectionAppLinkOrigin=https://gurbakir.com
web.collectionAppLinkPathPrefix=/collections/
web.productAppLinkOrigin=https://gurbakir.com
web.productAppLinkPathPrefix=/apps/mobile/products/
web.orderAppLinkOrigin=https://gurbakir.com
web.orderAppLinkPathPrefix=/apps/mobile/orders/
'@
    }

    foreach ($entry in $content.GetEnumerator()) {
        $path = Join-Path $root $entry.Key
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [System.IO.File]::WriteAllText($path, $entry.Value, [System.Text.UTF8Encoding]::new($false))
    }

    return $root
}

function Invoke-SelfTests {
    $tests = @(
        @{ Name = "valid public tree passes"; Mutate = { param($root) }; ExpectedFailure = $null },
        @{ Name = "brand-owned root identity fails"; Mutate = { param($root) (Get-Content (Join-Path $root 'settings.gradle.kts') -Raw).Replace('multi-brand-commerce-android', 'gurbakir-android') | Set-Content -NoNewline (Join-Path $root 'settings.gradle.kts') }; ExpectedFailure = "neutral-root-project-name" },
        @{ Name = "forensic path fails"; Mutate = { param($root) $p=Join-Path $root 'docs/reference-apk/raw.txt'; New-Item -ItemType Directory -Path (Split-Path -Parent $p) -Force | Out-Null; Set-Content -LiteralPath $p -Value 'raw' }; ExpectedFailure = "forbidden-private-material" },
        @{ Name = "workstation path fails"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'README.md') -Value 'C:\Users\Example\private' }; ExpectedFailure = "no-workstation-paths" },
        @{ Name = "private repository URL fails"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'README.md') -Value 'https://github.com/projectapp13-4/Shopify-app-GurBakir' }; ExpectedFailure = "no-private-repository-urls" },
        @{ Name = "unapproved reference mark fails"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'README.md') -Value 'Kristal Kutu reference application' }; ExpectedFailure = "no-unapproved-reference-marks" },
        @{ Name = "forensic text reference fails"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'README.md') -Value 'docs/reference-apk/analysis/05_decompiled' }; ExpectedFailure = "no-forensic-text-references" },
        @{ Name = "unresolved rights fail"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'ASSET-LICENSES.md') -Value 'Publication gate: UNRESOLVED' }; ExpectedFailure = "asset-publication-rights" },
        @{ Name = "unpinned action fails"; Mutate = { param($root) (Get-Content (Join-Path $root '.github/workflows/android-foundation.yml') -Raw).Replace('@1111111111111111111111111111111111111111', '@main') | Set-Content -NoNewline (Join-Path $root '.github/workflows/android-foundation.yml') }; ExpectedFailure = "sha-pinned-actions" },
        @{ Name = "implicit legacy Android SDK package fails"; Mutate = { param($root) $p=Join-Path $root '.github/workflows/android-foundation.yml'; (Get-Content -LiteralPath $p -Raw).Replace('packages: platform-tools', 'packages: tools platform-tools') | Set-Content -NoNewline $p }; ExpectedFailure = "bounded-android-sdk-bootstrap" },
        @{ Name = "empty Gradle lane guard removal fails"; Mutate = { param($root) $p=Join-Path $root '.github/workflows/android-foundation.yml'; (Get-Content -LiteralPath $p -Raw).Replace('if ($tasks.Count -eq 0) { throw "No Gradle tasks were resolved for the unit lane." }', '') | Set-Content -NoNewline $p }; ExpectedFailure = "nonempty-gradle-lane-resolution" },
        @{ Name = "native exit-code guard after PowerShell resolver fails"; Mutate = { param($root) $p=Join-Path $root '.github/workflows/android-foundation.yml'; (Get-Content -LiteralPath $p -Raw).Replace('if (-not $?) { throw "Failed to resolve the unit Gradle lane." }', 'if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }') | Set-Content -NoNewline $p }; ExpectedFailure = "nonempty-gradle-lane-resolution" },
        @{ Name = "privileged pull request trigger fails"; Mutate = { param($root) $p=Join-Path $root '.github/workflows/android-foundation.yml'; $value=(Get-Content -LiteralPath $p -Raw) + "`n  pull_request_target:`n"; [System.IO.File]::WriteAllText($p, $value, [System.Text.UTF8Encoding]::new($false)) }; ExpectedFailure = "external-fork-workflow-safety" },
        @{ Name = "application identity mutation fails"; Mutate = { param($root) $p=Join-Path $root 'config/onboarding/generated/gurbakir/development.properties'; (Get-Content $p -Raw).Replace('com.gurbakir.mobile.dev', 'com.example.changed') | Set-Content -NoNewline $p }; ExpectedFailure = "gurbakir-application-identities" },
        @{ Name = "OAuth placeholder mutation fails"; Mutate = { param($root) (Get-Content (Join-Path $root 'app/build.gradle.kts') -Raw).Replace('manifestPlaceholders["appAuthRedirectScheme"]', 'manifestPlaceholders["renamedScheme"]') | Set-Content -NoNewline (Join-Path $root 'app/build.gradle.kts') }; ExpectedFailure = "gurbakir-oauth-app-links" },
        @{ Name = "App Link projection mutation fails"; Mutate = { param($root) $p=Join-Path $root 'config/onboarding/generated/gurbakir/development.properties'; (Get-Content $p -Raw).Replace('/apps/mobile/products/', '/products/') | Set-Content -NoNewline $p }; ExpectedFailure = "gurbakir-oauth-app-links" },
        @{ Name = "reference authority boundary mutation fails"; Mutate = { param($root) Set-Content -LiteralPath (Join-Path $root 'docs/reference-model/COMMERCE-BEHAVIOR.md') -Value 'Historical notes.' }; ExpectedFailure = "reference-model-authority-boundary" },
        @{ Name = "persistence identity mutation fails"; Mutate = { param($root) $p=Join-Path $root 'config/onboarding/generated/gurbakir/staging.properties'; (Get-Content $p -Raw).Replace('gurbakir.cart.staging.v1', 'renamed.cart.staging.v1') | Set-Content -NoNewline $p }; ExpectedFailure = "gurbakir-persistence-identities" },
        @{ Name = "Customer Account user agent mutation fails"; Mutate = { param($root) $p=Join-Path $root 'config/onboarding/generated/gurbakir/development.properties'; (Get-Content $p -Raw).Replace('Gurbakir-Android', 'Changed-Android') | Set-Content -NoNewline $p }; ExpectedFailure = "gurbakir-client-user-agent" }
    )

    $passed = 0
    foreach ($test in $tests) {
        $fixture = New-SelfTestFixture
        try {
            & $test.Mutate $fixture
            $results = @(Invoke-PublicReadinessValidation -Root $fixture)
            $failedIds = @($results | Where-Object { -not $_.Passed } | ForEach-Object { $_.Id })
            if ($null -eq $test.ExpectedFailure) {
                if ($failedIds.Count -ne 0) {
                    throw "expected no failures; got $($failedIds -join ', ')"
                }
            } elseif ($test.ExpectedFailure -notin $failedIds) {
                throw "expected '$($test.ExpectedFailure)' to fail; got $($failedIds -join ', ')"
            }
            $passed++
            Write-Output "PASS [$passed/$($tests.Count)] $($test.Name)"
        } finally {
            Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Output "Public-readiness self-tests: PASS ($passed/$($tests.Count))"
}

if ($SelfTest) {
    Invoke-SelfTests
    exit 0
}

$validation = @(Invoke-PublicReadinessValidation -Root $RepositoryRoot -CheckCleanWorktree:$RequireCleanWorktree)
foreach ($result in $validation) {
    $status = if ($result.Passed) { "PASS" } else { "FAIL" }
    Write-Output "$status [$($result.Id)] $($result.Detail)"
}

$failures = @($validation | Where-Object { -not $_.Passed })
if ($failures.Count -gt 0) {
    Write-Error "Public-readiness validation failed: $($failures.Count) check(s)."
    exit 1
}

Write-Output "Public-readiness validation: PASS ($($validation.Count)/$($validation.Count))"
