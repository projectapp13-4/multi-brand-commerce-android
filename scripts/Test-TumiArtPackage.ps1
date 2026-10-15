[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release', 'All')]
    [string]$Variant = 'All',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $repoRoot 'apps/tumiart/build/outputs/apk'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Passed,
        [Parameter(Mandatory)][string]$Evidence
    )

    $checks.Add([pscustomobject]@{ check = $Name; passed = $Passed; evidence = $Evidence })
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$CommandArguments
    )

    $output = & $FilePath @CommandArguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Resolve-AndroidSdkRoot {
    $candidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($candidate in $candidates) {
        $resolved = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath (Join-Path $resolved 'build-tools') -PathType Container) {
            return $resolved
        }
    }
    throw 'ANDROID_SDK_ROOT or ANDROID_HOME must reference an Android SDK with build-tools.'
}

function Resolve-BuildTool {
    param(
        [Parameter(Mandatory)][string]$SdkRoot,
        [Parameter(Mandatory)][string]$ToolName
    )

    $fileName = if ($IsWindows) {
        if ($ToolName -eq 'apksigner') { "$ToolName.bat" } else { "$ToolName.exe" }
    } else {
        $ToolName
    }
    foreach ($directory in @(
        Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
            Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending
    )) {
        $candidate = Join-Path $directory.FullName $fileName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw "Android build tool not found: $ToolName"
}

function Find-TumiArtApk {
    param([Parameter(Mandatory)][string]$VariantName)

    if (-not (Test-Path -LiteralPath $outputRoot -PathType Container)) {
        throw "TumiArt APK output directory is missing: $outputRoot"
    }
    $records = [System.Collections.Generic.List[object]]::new()
    foreach ($metadataFile in Get-ChildItem -LiteralPath $outputRoot -Recurse -File -Filter 'output-metadata.json') {
        if ($metadataFile.FullName -match '[\\/]androidTest[\\/]') {
            continue
        }
        $metadata = Get-Content -LiteralPath $metadataFile.FullName -Raw -Encoding utf8 | ConvertFrom-Json
        if ([string]$metadata.variantName -cne $VariantName) {
            continue
        }
        if (@($metadata.elements).Count -ne 1) {
            throw "Expected one APK element in $($metadataFile.FullName)."
        }
        $outputFile = [string]$metadata.elements[0].outputFile
        if ([System.IO.Path]::GetFileName($outputFile) -cne $outputFile) {
            throw "APK metadata outputFile must be a leaf filename: $outputFile"
        }
        $apkPath = [System.IO.Path]::GetFullPath((Join-Path $metadataFile.DirectoryName $outputFile))
        $metadataRoot = [System.IO.Path]::GetFullPath($metadataFile.DirectoryName) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $apkPath.StartsWith($metadataRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "APK metadata escaped its output directory: $apkPath"
        }
        $records.Add([pscustomobject]@{ Metadata = $metadata; ApkPath = $apkPath })
    }
    if ($records.Count -ne 1) {
        throw "Expected exactly one $VariantName TumiArt APK metadata record; found $($records.Count)."
    }
    if (-not (Test-Path -LiteralPath $records[0].ApkPath -PathType Leaf)) {
        throw "APK declared by metadata does not exist: $($records[0].ApkPath)"
    }
    return $records[0]
}

function Test-NoUsableCredential {
    param([Parameter(Mandatory)][string]$Text)

    $pattern = '(?i)(?:shpat_|shpca_|shpss_|shpua_|ghp_|AIza[0-9A-Za-z_-]{20,}|eyJ[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.)'
    return $Text -notmatch $pattern
}

function Get-AaptApplicationTree {
    param([Parameter(Mandatory)][string]$Text)

    $lines = $Text -split '\r?\n'
    $starts = @(for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^\s*E: application(?:\s|$)') { $index }
    })
    if ($starts.Count -ne 1) { return '' }
    $start = $starts[0]
    $indent = ([regex]::Match($lines[$start], '^\s*')).Length
    $end = $lines.Count
    for ($index = $start + 1; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^(?<indent>\s*)E:' -and $Matches.indent.Length -le $indent) {
            $end = $index
            break
        }
    }
    return ($lines[$start..($end - 1)] -join "`n")
}

function Test-TumiArtDexBoundary {
    param([Parameter(Mandatory)][string]$Text)

    $required = @(
        'tumiart-development-local.db',
        'tumiart_secure_cart_development',
        'tumiart.cart.development.v1',
        'tumiart_secure_customer_session_development',
        'tumiart.customer.session.development.v1',
        'shop.80183034101.tumiart://oauth/callback'
    )
    $forbidden = @(
        'pilot-urun',
        'pilot-koleksiyonu',
        'Lcom/google/firebase/',
        'Lcom/gurbakir/firebase/',
        'trial-store-local.db',
        'trial_secure_cart_development',
        'trial.customer.session.development.v1',
        'gurbakir-local.db',
        'gurbakir_secure_cart_development',
        'gurbakir_secure_customer_session_development',
        'gurbakir.cart.development.v1',
        'gurbakir.customer.session.development.v1',
        'bardaklar',
        'cezveler',
        'tavalar-sahanlar',
        'tencereler',
        'ozel-urunlerimiz',
        'bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu'
    )
    return @($required | Where-Object { $Text -notmatch [regex]::Escape($_) }).Count -eq 0 -and
        @($forbidden | Where-Object { $Text -match [regex]::Escape($_) }).Count -eq 0
}

function Test-SourceBackupContract {
    $manifest = Get-Content -LiteralPath (Join-Path $repoRoot 'apps/tumiart/src/main/AndroidManifest.xml') -Raw
    $rules = Get-Content -LiteralPath (Join-Path $repoRoot 'apps/tumiart/src/main/res/xml/data_extraction_rules.xml') -Raw
    $domains = @('sharedpref', 'database', 'file')
    return $manifest -match 'android:allowBackup="false"' -and
        $manifest -match 'android:fullBackupContent="false"' -and
        $manifest -match 'android:dataExtractionRules="@xml/data_extraction_rules"' -and
        $manifest -match 'android:usesCleartextTraffic="false"' -and
        @($domains | Where-Object {
            ([regex]::Matches($rules, '<exclude\s+domain="' + $_ + '"\s+path="\."\s*/>')).Count -ne 2
        }).Count -eq 0
}

function Invoke-SelfTests {
    $visibilityFixture = "E: manifest`n  E: queries`n    E: intent`n      E: data`n        A: android:scheme(0x01010027)=`"https`"`n  E: application`n    E: activity`n      E: intent-filter`n        E: data`n          A: android:scheme(0x01010027)=`"shop.80183034101.tumiart`"`n  E: uses-permission"
    $applicationFixture = Get-AaptApplicationTree $visibilityFixture
    $validDex = @'
tumiart-development-local.db
tumiart_secure_cart_development
tumiart.cart.development.v1
tumiart_secure_customer_session_development
tumiart.customer.session.development.v1
shop.80183034101.tumiart://oauth/callback
'@
    $results = @(
        [pscustomobject]@{ test = 'browser visibility queries are separate from application URI handlers'; passed = ($applicationFixture -match 'shop.80183034101.tumiart' -and $applicationFixture -notmatch 'queries|https|uses-permission') }
        [pscustomobject]@{ test = 'exact TumiArt persistence and fallback fixture passes'; passed = (Test-TumiArtDexBoundary $validDex) }
        [pscustomobject]@{ test = 'missing TumiArt identity is rejected'; passed = -not (Test-TumiArtDexBoundary ($validDex.Replace('tumiart-development-local.db', ''))) }
        [pscustomobject]@{ test = 'Trial catalog contamination is rejected'; passed = -not (Test-TumiArtDexBoundary ($validDex + "`npilot-urun")) }
        [pscustomobject]@{ test = 'Firebase class contamination is rejected'; passed = -not (Test-TumiArtDexBoundary ($validDex + "`nLcom/google/firebase/FirebaseApp;")) }
        [pscustomobject]@{ test = 'Gurbakir persistence contamination is rejected'; passed = -not (Test-TumiArtDexBoundary ($validDex + "`ngurbakir-local.db")) }
        [pscustomobject]@{ test = 'ordinary public identifiers are not classified as credentials'; passed = (Test-NoUsableCredential 'shop.80183034101.tumiart') }
        [pscustomobject]@{ test = 'private Admin token shape is rejected'; passed = -not (Test-NoUsableCredential 'shpat_example') }
        [pscustomobject]@{ test = 'source backup and transfer policy is complete'; passed = (Test-SourceBackupContract) }
    )
    $results | Format-Table -AutoSize | Out-Host
    $failed = @($results | Where-Object { -not $_.passed })
    if ($failed.Count -gt 0) {
        throw "TumiArt package validator self-tests failed: $($failed.test -join '; ')"
    }
    Write-Host "TumiArt package validator self-tests PASS ($($results.Count) fixtures)."
}

function Test-VariantPackage {
    param(
        [Parameter(Mandatory)][string]$VariantName,
        [Parameter(Mandatory)][string]$Aapt,
        [Parameter(Mandatory)][string]$ApkSigner,
        [Parameter(Mandatory)][string]$DexDump
    )

    $artifact = Find-TumiArtApk $VariantName
    $apk = $artifact.ApkPath
    $debug = $VariantName -ceq 'developmentDebug'
    $expectedId = if ($debug) { 'com.tumiart.mobile.development.debug' } else { 'com.tumiart.mobile.development' }
    $prefix = "tumiart $VariantName"
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    Add-Check "$prefix APK is discovered through output metadata" $true "$($apk.Substring($repoRoot.Length + 1).Replace('\', '/')); SHA256 $hash"
    Add-Check "$prefix metadata has exact application identity" ([string]$artifact.Metadata.applicationId -ceq $expectedId) "applicationId $($artifact.Metadata.applicationId)"

    $badging = Invoke-NativeCapture $Aapt @('dump', 'badging', $apk)
    $badgingExact = $badging.ExitCode -eq 0 -and
        $badging.Output -match "package: name='$([regex]::Escape($expectedId))' versionCode='1' versionName='0\.1\.0-tumiart-p1'" -and
        $badging.Output -match "compileSdkVersion='36'" -and
        $badging.Output -match "sdkVersion:'23'" -and
        $badging.Output -match "targetSdkVersion:'36'" -and
        $badging.Output -match "application-label:'TumiArt Development'" -and
        $badging.Output -match "launchable-activity: name='com\.tumiart\.mobile\.MainActivity'"
    Add-Check "$prefix badging matches exact package contract" $badgingExact 'ID/version/SDK/label/launcher checked'
    $debuggableCorrect = if ($debug) {
        $badging.Output -match '(?m)^application-debuggable\s*$'
    } else {
        $badging.Output -notmatch '(?m)^application-debuggable\s*$'
    }
    Add-Check "$prefix debuggable state matches build type" $debuggableCorrect $(if ($debug) { 'debuggable' } else { 'not debuggable' })

    $permissions = Invoke-NativeCapture $Aapt @('dump', 'permissions', $apk)
    $internetExact = $permissions.ExitCode -eq 0 -and
        ([regex]::Matches($permissions.Output, '(?im)uses-permission:.*android\.permission\.INTERNET')).Count -eq 1
    Add-Check "$prefix has exactly the required INTERNET permission" $internetExact 'merged permissions inspected'

    $manifest = Invoke-NativeCapture $Aapt @('dump', 'xmltree', $apk, 'AndroidManifest.xml')
    $manifestExact = $manifest.ExitCode -eq 0 -and
        $manifest.Output -match 'com\.tumiart\.mobile\.TumiArtApplication' -and
        $manifest.Output -match 'com\.tumiart\.mobile\.MainActivity' -and
        $manifest.Output -match 'allowBackup\(0x[0-9a-f]+\)=\(type 0x12\)0x0' -and
        $manifest.Output -match 'fullBackupContent\(0x[0-9a-f]+\)=\(type 0x12\)0x0' -and
        $manifest.Output -match 'usesCleartextTraffic\(0x[0-9a-f]+\)=\(type 0x12\)0x0' -and
        $manifest.Output -match 'dataExtractionRules\(0x[0-9a-f]+\)=@0x[0-9a-f]{8}'
    Add-Check "$prefix manifest has restrictive storage and network flags" $manifestExact 'application/activity/backup/transfer/cleartext checked'
    $applicationManifest = Get-AaptApplicationTree $manifest.Output
    $deepLinksExact = ([regex]::Matches($applicationManifest, 'autoVerify\(0x[0-9a-f]+\)=\(type 0x12\)0x0')).Count -eq 3 -and
        ([regex]::Matches($applicationManifest, 'host\(0x[0-9a-f]+\)="tumiart\.com"')).Count -eq 3 -and
        ([regex]::Matches($applicationManifest, 'pathPrefix\(0x[0-9a-f]+\)="/collections/"')).Count -eq 1 -and
        ([regex]::Matches($applicationManifest, 'pathPrefix\(0x[0-9a-f]+\)="/products/"')).Count -eq 1 -and
        ([regex]::Matches($applicationManifest, 'pathPrefix\(0x[0-9a-f]+\)="/apps/mobile/orders/"')).Count -eq 1 -and
        ([regex]::Matches($applicationManifest, 'scheme\(0x[0-9a-f]+\)="shop\.80183034101\.tumiart"')).Count -eq 1
    $deepLinksExact = $deepLinksExact -and
        ([regex]::Matches($applicationManifest, 'host\(0x[0-9a-f]+\)="oauth"')).Count -eq 1 -and
        ([regex]::Matches($applicationManifest, 'path\(0x[0-9a-f]+\)="/callback"')).Count -eq 1 -and
        ([regex]::Matches($applicationManifest, 'scheme\(0x[0-9a-f]+\)="')).Count -eq 4
    Add-Check "$prefix manifest owns exact HTTPS and OAuth scheme routes" $deepLinksExact 'three non-verified HTTPS routes plus one exact AppAuth callback scheme; full callback is checked in DEX'
    $firebaseAbsent = $manifest.Output -notmatch 'com\.google\.firebase|FirebaseInitProvider|firebase_'
    Add-Check "$prefix manifest has no Firebase provider or metadata" $firebaseAbsent 'Firebase absent rather than runtime disabled'

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $dexEntries = @($archive.Entries | Where-Object { $_.FullName -match '^classes(?:\d+)?\.dex$' })
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "tumiart-dex-$([guid]::NewGuid().ToString('N'))"
        [void](New-Item -ItemType Directory -Path $tempRoot)
        try {
            $strings = [System.Text.StringBuilder]::new()
            $readable = $dexEntries.Count -gt 0
            foreach ($entry in $dexEntries) {
                $dexPath = Join-Path $tempRoot ([System.IO.Path]::GetFileName($entry.FullName))
                $input = $entry.Open()
                $output = [System.IO.File]::Create($dexPath)
                try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
                $checksum = Invoke-NativeCapture $DexDump @('-c', $dexPath)
                $table = Invoke-NativeCapture $DexDump @('-s', $dexPath)
                $readable = $readable -and $checksum.ExitCode -eq 0 -and $table.ExitCode -eq 0
                [void]$strings.AppendLine($table.Output)
            }
            $dexText = $strings.ToString()
            Add-Check "$prefix DEX has no Firebase SDK" ($dexText -notmatch 'Lcom/google/firebase/|Lcom/gurbakir/firebase/') 'all DEX string tables inspected'
            Add-Check "$prefix DEX checksum and string tables are readable" $readable "$($dexEntries.Count) DEX files"
            Add-Check "$prefix DEX contains exact TumiArt persistence and fallback only" (Test-TumiArtDexBoundary $dexText) 'TumiArt database/preferences/aliases/callback present; existing-brand identities absent'
            Add-Check "$prefix DEX contains no recognizable usable credential" (Test-NoUsableCredential $dexText) 'private-token prefixes and JWT structure checked'
        } finally {
            $resolved = [System.IO.Path]::GetFullPath($tempRoot)
            $temp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
            if ($resolved.StartsWith($temp, [System.StringComparison]::OrdinalIgnoreCase) -and
                (Split-Path -Leaf $resolved) -like 'tumiart-dex-*' -and
                (Test-Path -LiteralPath $resolved -PathType Container)) {
                Remove-Item -LiteralPath $resolved -Recurse -Force
            }
        }
    } finally {
        $archive.Dispose()
    }

    $signature = Invoke-NativeCapture $ApkSigner @('verify', '--print-certs', $apk)
    $signingInputPresent = $false
    $signatureCorrect = if ($debug -or $signingInputPresent) { $signature.ExitCode -eq 0 } else { $signature.ExitCode -ne 0 }
    $signatureState = if ($debug) { 'debug-signed' } elseif ($signingInputPresent) { 'stable nonproduction signed' } else { 'unsigned development artifact; production identity/signing outside P1' }
    Add-Check "$prefix signing state matches scoped nonproduction inputs" $signatureCorrect $signatureState
    if (-not $debug) {
        $mapping = Join-Path $repoRoot 'apps/tumiart/build/outputs/mapping/developmentRelease/mapping.txt'
        Add-Check 'tumiart developmentRelease was processed by R8' (Test-Path -LiteralPath $mapping -PathType Leaf) 'mapping.txt present'
    }
}

if ($SelfTest) {
    Invoke-SelfTests
    exit 0
}

$sdkRoot = Resolve-AndroidSdkRoot
$aapt = Resolve-BuildTool $sdkRoot 'aapt'
$apkSigner = Resolve-BuildTool $sdkRoot 'apksigner'
$dexDump = Resolve-BuildTool $sdkRoot 'dexdump'
$variants = if ($Variant -eq 'All') { @('developmentDebug', 'developmentRelease') } else { @("development$Variant") }
foreach ($variantName in $variants) {
    Test-VariantPackage $variantName $aapt $apkSigner $dexDump
}

$checks | Format-Table -AutoSize | Out-Host
$failed = @($checks | Where-Object { -not $_.passed })
if ($failed.Count -gt 0) {
    throw "TumiArt package validation failed: $($failed.check -join '; ')"
}
Write-Host "TumiArt package validation PASS ($($checks.Count) checks)."
exit 0
