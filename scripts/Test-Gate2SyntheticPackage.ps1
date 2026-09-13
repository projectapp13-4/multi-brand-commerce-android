[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release', 'All')]
    [string]$Variant = 'All',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $repoRoot 'apps/synthetic/build/outputs/apk'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [bool]$Passed,
        [Parameter(Mandatory)] [string]$Evidence
    )

    $checks.Add([pscustomobject]@{ check = $Name; passed = $Passed; evidence = $Evidence })
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [Parameter(Mandatory)] [string[]]$CommandArguments
    )

    $output = & $FilePath @CommandArguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Resolve-AndroidSdkRoot {
    $candidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
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
        [Parameter(Mandatory)] [string]$SdkRoot,
        [Parameter(Mandatory)] [string]$ToolName
    )

    $fileName = if ($IsWindows) {
        if ($ToolName -eq 'apksigner') { "$ToolName.bat" } else { "$ToolName.exe" }
    } else {
        $ToolName
    }
    $buildToolDirectories = @(
        Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory |
            Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending
    )
    foreach ($directory in $buildToolDirectories) {
        $candidate = Join-Path $directory.FullName $fileName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw "Android build tool not found: $ToolName"
}

function Find-ApkArtifact {
    param([Parameter(Mandatory)] [string]$VariantName)

    if (-not (Test-Path -LiteralPath $outputRoot -PathType Container)) {
        throw "Synthetic APK output directory is missing: $outputRoot"
    }
    $artifacts = [System.Collections.Generic.List[object]]::new()
    foreach ($metadataFile in Get-ChildItem -LiteralPath $outputRoot -Recurse -File -Filter 'output-metadata.json') {
        if ($metadataFile.FullName -match '[\\/]androidTest[\\/]') {
            continue
        }
        $metadata = Get-Content -LiteralPath $metadataFile.FullName -Raw -Encoding utf8 | ConvertFrom-Json
        if ($metadata.variantName -ine $VariantName) {
            continue
        }
        if (@($metadata.elements).Count -ne 1) {
            throw "Expected one APK element in $($metadataFile.FullName)."
        }
        $outputFile = [string]$metadata.elements[0].outputFile
        if ([System.IO.Path]::GetFileName($outputFile) -ne $outputFile) {
            throw "APK metadata outputFile must be a leaf filename: $outputFile"
        }
        $apkPath = [System.IO.Path]::GetFullPath((Join-Path $metadataFile.DirectoryName $outputFile))
        $metadataDirectory = [System.IO.Path]::GetFullPath($metadataFile.DirectoryName) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $apkPath.StartsWith($metadataDirectory, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "APK metadata escaped its output directory: $apkPath"
        }
        $artifacts.Add([pscustomobject]@{
            MetadataPath = $metadataFile.FullName
            Metadata = $metadata
            ApkPath = $apkPath
        })
    }
    if ($artifacts.Count -ne 1) {
        throw "Expected exactly one $VariantName synthetic APK metadata record; found $($artifacts.Count)."
    }
    if (-not (Test-Path -LiteralPath $artifacts[0].ApkPath -PathType Leaf)) {
        throw "APK declared by metadata does not exist: $($artifacts[0].ApkPath)"
    }
    return $artifacts[0]
}

function Get-ResourceBlock {
    param(
        [Parameter(Mandatory)] [string]$ResourceDump,
        [Parameter(Mandatory)] [string]$ResourceName
    )

    $escapedName = [regex]::Escape($ResourceName)
    $match = [regex]::Match(
        $ResourceDump,
        "(?ms)^\s*resource\s+0x[0-9a-f]+\s+$escapedName\s*\r?\n(?<body>.*?)(?=^\s*resource\s+0x[0-9a-f]+\s+|\z)"
    )
    return $(if ($match.Success) { $match.Groups['body'].Value } else { '' })
}

function Get-CompiledXmlResourcePath {
    param(
        [Parameter(Mandatory)] [string]$ResourceDump,
        [Parameter(Mandatory)] [string]$ResourceName
    )

    $block = Get-ResourceBlock -ResourceDump $ResourceDump -ResourceName $ResourceName
    $pathMatches = @(
        [regex]::Matches($block, '(?m)^\s*\([^)]*\)\s+\(file\)\s+(?<path>res/[A-Za-z0-9_./-]+\.xml)\s+type=XML\s*$')
    )
    if ($pathMatches.Count -ne 1) {
        return $null
    }
    $path = $pathMatches[0].Groups['path'].Value
    if ($path -match '(?:^|/)\.\.(?:/|$)') {
        return $null
    }
    return $path
}

function Get-AaptResourceReferenceId {
    param(
        [Parameter(Mandatory)] [string]$XmlTree,
        [Parameter(Mandatory)] [string]$AttributeName
    )

    $escapedName = [regex]::Escape($AttributeName)
    $matches = @(
        [regex]::Matches(
            $XmlTree,
            '(?m)^\s*A:\s+android:' + $escapedName + '\(0x[0-9a-f]+\)=@(?<id>0x[0-9a-f]{8})\s*$'
        )
    )
    if ($matches.Count -ne 1) {
        return $null
    }
    return $matches[0].Groups['id'].Value
}

function Get-CompiledXmlResourcePathById {
    param(
        [Parameter(Mandatory)] [string]$ResourceDump,
        [Parameter(Mandatory)] [string]$ResourceId
    )

    if ($ResourceId -notmatch '^0x[0-9a-f]{8}$') {
        return $null
    }
    $escapedId = [regex]::Escape($ResourceId)
    $resourceMatches = @(
        [regex]::Matches(
            $ResourceDump,
            "(?ms)^\s*resource\s+$escapedId\s+xml/[A-Za-z0-9_.-]+\s*\r?\n(?<body>.*?)(?=^\s*resource\s+0x[0-9a-f]+\s+|\z)"
        )
    )
    if ($resourceMatches.Count -ne 1) {
        return $null
    }
    $pathMatches = @(
        [regex]::Matches(
            $resourceMatches[0].Groups['body'].Value,
            '(?m)^\s*\([^)]*\)\s+\(file\)\s+(?<path>res/[A-Za-z0-9_./-]+\.xml)\s+type=XML\s*$'
        )
    )
    if ($pathMatches.Count -ne 1) {
        return $null
    }
    $path = $pathMatches[0].Groups['path'].Value
    if ($path -match '(?:^|/)\.\.(?:/|$)') {
        return $null
    }
    return $path
}

function Get-ManifestReferencedCompiledXmlPath {
    param(
        [Parameter(Mandatory)] [string]$ManifestXmlTree,
        [Parameter(Mandatory)] [string]$ResourceDump,
        [Parameter(Mandatory)] [string]$AttributeName
    )

    $resourceId = Get-AaptResourceReferenceId -XmlTree $ManifestXmlTree -AttributeName $AttributeName
    if ($null -eq $resourceId) {
        return $null
    }
    return Get-CompiledXmlResourcePathById -ResourceDump $ResourceDump -ResourceId $resourceId
}

function Get-AaptXmlElementBlocks {
    param(
        [Parameter(Mandatory)] [string]$XmlTree,
        [Parameter(Mandatory)] [string]$ElementName
    )

    $lines = @($XmlTree -split '\r?\n')
    $blocks = [System.Collections.Generic.List[string]]::new()
    $elementPattern = '^\s*E:\s+' + [regex]::Escape($ElementName) + '(?:\s|\(|$)'
    for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex++) {
        if ($lines[$lineIndex] -notmatch $elementPattern) {
            continue
        }
        $indentLength = ([regex]::Match($lines[$lineIndex], '^\s*')).Value.Length
        $builder = [System.Text.StringBuilder]::new()
        for ($blockIndex = $lineIndex; $blockIndex -lt $lines.Count; $blockIndex++) {
            $line = $lines[$blockIndex]
            if ($blockIndex -gt $lineIndex -and $line -match '^\s*E:\s+') {
                $candidateIndent = ([regex]::Match($line, '^\s*')).Value.Length
                if ($candidateIndent -le $indentLength) {
                    break
                }
            }
            [void]$builder.AppendLine($line)
        }
        $blocks.Add($builder.ToString())
    }
    return @($blocks)
}

function Get-AaptStringAttributeValues {
    param(
        [Parameter(Mandatory)] [string]$XmlTree,
        [Parameter(Mandatory)] [string]$AttributeName
    )

    $escapedName = [regex]::Escape($AttributeName)
    $pattern = '(?m)^\s*A:\s+android:' + $escapedName + '\(0x[0-9a-f]+\)="(?<value>[^"]*)"\s+\(Raw:\s+"[^"]*"\)\s*$'
    return @([regex]::Matches($XmlTree, $pattern) | ForEach-Object { $_.Groups['value'].Value })
}

function Test-AaptBooleanAttributeFalse {
    param(
        [Parameter(Mandatory)] [string]$XmlTree,
        [Parameter(Mandatory)] [string]$AttributeName
    )

    $escapedName = [regex]::Escape($AttributeName)
    $pattern = '(?m)^\s*A:\s+android:' + $escapedName + '\(0x[0-9a-f]+\)=\(type 0x12\)0x0\s*$'
    return ([regex]::Matches($XmlTree, $pattern)).Count -eq 1
}

function Get-AaptUnqualifiedStringAttributeValues {
    param(
        [Parameter(Mandatory)] [string]$XmlTree,
        [Parameter(Mandatory)] [string]$AttributeName
    )

    $escapedName = [regex]::Escape($AttributeName)
    $pattern = '(?m)^\s*A:\s+' + $escapedName + '="(?<value>[^"]*)"\s+\(Raw:\s+"[^"]*"\)\s*$'
    return @([regex]::Matches($XmlTree, $pattern) | ForEach-Object { $_.Groups['value'].Value })
}

function Test-DataExtractionRulesContract {
    param([Parameter(Mandatory)] [string]$XmlTree)

    $rootBlocks = @(Get-AaptXmlElementBlocks $XmlTree 'data-extraction-rules')
    $cloudBlocks = @(Get-AaptXmlElementBlocks $XmlTree 'cloud-backup')
    $transferBlocks = @(Get-AaptXmlElementBlocks $XmlTree 'device-transfer')
    if ($rootBlocks.Count -ne 1 -or $cloudBlocks.Count -ne 1 -or $transferBlocks.Count -ne 1) {
        return $false
    }
    $elementNames = @(
        [regex]::Matches($XmlTree, '(?m)^\s*E:\s+(?<name>[^\s(]+)') |
            ForEach-Object { $_.Groups['name'].Value }
    )
    if ($elementNames.Count -ne 9 -or @($elementNames | Where-Object {
        $_ -notin @('data-extraction-rules', 'cloud-backup', 'device-transfer', 'exclude')
    }).Count -ne 0) {
        return $false
    }

    function Test-ExclusionScope {
        param([Parameter(Mandatory)] [string]$Scope)

        $excludeBlocks = @(Get-AaptXmlElementBlocks $Scope 'exclude')
        if ($excludeBlocks.Count -ne 3) {
            return $false
        }
        $domains = [System.Collections.Generic.List[string]]::new()
        foreach ($block in $excludeBlocks) {
            $domain = @(Get-AaptUnqualifiedStringAttributeValues $block 'domain')
            $path = @(Get-AaptUnqualifiedStringAttributeValues $block 'path')
            if ($domain.Count -ne 1 -or $path.Count -ne 1 -or $path[0] -ne '.' -or
                ([regex]::Matches($block, '(?m)^\s*A:\s+')).Count -ne 2) {
                return $false
            }
            $domains.Add($domain[0])
        }
        return (($domains | Sort-Object) -join '|') -eq 'database|file|sharedpref'
    }

    $cloudEncryptionRequired =
        ([regex]::Matches($cloudBlocks[0], '(?m)^\s*A:\s+disableIfNoEncryptionCapabilities=\(type 0x12\)0xffffffff\s*$')).Count -eq 1
    return $cloudEncryptionRequired -and
        (Test-ExclusionScope $cloudBlocks[0]) -and
        (Test-ExclusionScope $transferBlocks[0])
}

function Test-RetiredSyntheticAccountStringsAbsent {
    param([Parameter(Mandatory)] [string]$DexText)

    $retiredStrings = @(
        'https://accounts.gate2.invalid',
        'shop.0.gate2synthetic://oauth/callback',
        'https://links.gate2.invalid/apps/mobile/orders'
    )
    return @($retiredStrings | Where-Object { $DexText -match [regex]::Escape($_) }).Count -eq 0
}

function Test-SyntheticAppAuthBoundary {
    param(
        [Parameter(Mandatory)] [string]$ManifestXmlTree,
        [Parameter(Mandatory)] [string]$DexText
    )

    $managementActivities = @(
        Get-AaptXmlElementBlocks -XmlTree $ManifestXmlTree -ElementName 'activity' |
            Where-Object {
                (Get-AaptStringAttributeValues -XmlTree $_ -AttributeName 'name') -contains
                    'net.openid.appauth.AuthorizationManagementActivity'
            }
    )
    if ($managementActivities.Count -ne 1 -or
        -not (Test-AaptBooleanAttributeFalse -XmlTree $managementActivities[0] -AttributeName 'exported')) {
        return $false
    }

    return $DexText -match [regex]::Escape('Lnet/openid/appauth/AuthorizationManagementActivity;')
}

function Test-SyntheticDeepLinkManifestContract {
    param([Parameter(Mandatory)] [string]$XmlTree)

    $activityBlocks = @(Get-AaptXmlElementBlocks -XmlTree $XmlTree -ElementName 'activity')
    $mainActivityBlocks = @($activityBlocks | Where-Object {
        (Get-AaptStringAttributeValues -XmlTree $_ -AttributeName 'name') -contains 'com.example.gate2synthetic.MainActivity'
    })
    $redirectActivityBlocks = @($activityBlocks | Where-Object {
        (Get-AaptStringAttributeValues -XmlTree $_ -AttributeName 'name') -contains 'net.openid.appauth.RedirectUriReceiverActivity'
    })
    if ($mainActivityBlocks.Count -ne 1 -or $redirectActivityBlocks.Count -ne 0) {
        return $false
    }

    $mainFilters = @(Get-AaptXmlElementBlocks -XmlTree $mainActivityBlocks[0] -ElementName 'intent-filter')
    $launcherFilters = @($mainFilters | Where-Object { $_ -match 'android\.intent\.action\.MAIN' -and $_ -match 'android\.intent\.category\.LAUNCHER' })
    $viewFilters = @($mainFilters | Where-Object { $_ -match 'android\.intent\.action\.VIEW' })
    if ($mainFilters.Count -ne 3 -or $launcherFilters.Count -ne 1 -or $viewFilters.Count -ne 2) {
        return $false
    }

    $actualPaths = [System.Collections.Generic.List[string]]::new()
    foreach ($filter in $viewFilters) {
        $names = @(Get-AaptStringAttributeValues -XmlTree $filter -AttributeName 'name' | Sort-Object)
        $schemes = @(Get-AaptStringAttributeValues -XmlTree $filter -AttributeName 'scheme')
        $hosts = @(Get-AaptStringAttributeValues -XmlTree $filter -AttributeName 'host')
        $paths = @(Get-AaptStringAttributeValues -XmlTree $filter -AttributeName 'pathPrefix')
        $dataBlocks = @(Get-AaptXmlElementBlocks -XmlTree $filter -ElementName 'data')
        $namesExact = ($names -join '|') -eq 'android.intent.action.VIEW|android.intent.category.BROWSABLE|android.intent.category.DEFAULT'
        $dataExact = $dataBlocks.Count -eq 1 -and
            ([regex]::Matches($dataBlocks[0], '(?m)^\s*A:\s+android:')).Count -eq 3 -and
            $schemes.Count -eq 1 -and $schemes[0] -eq 'https' -and
            $hosts.Count -eq 1 -and $hosts[0] -eq 'links.gate2.invalid' -and
            $paths.Count -eq 1
        if (-not $namesExact -or -not $dataExact -or -not (Test-AaptBooleanAttributeFalse -XmlTree $filter -AttributeName 'autoVerify')) {
            return $false
        }
        $actualPaths.Add($paths[0])
    }
    $expectedPaths = @('/apps/mobile/products/', '/collections/')
    if ((@($actualPaths | Sort-Object) -join '|') -ne ($expectedPaths -join '|')) {
        return $false
    }

    $activityAliasBlocks = @(Get-AaptXmlElementBlocks -XmlTree $XmlTree -ElementName 'activity-alias')
    $allUriHandlerBlocks = @($activityBlocks + $activityAliasBlocks)
    $schemeAttributeCount = @($allUriHandlerBlocks | ForEach-Object {
        [regex]::Matches($_, '(?m)^\s*A:\s+android:scheme\(0x[0-9a-f]+\)=')
    }).Count
    $allUriHandlerSchemes = @($allUriHandlerBlocks | ForEach-Object {
        Get-AaptStringAttributeValues -XmlTree $_ -AttributeName 'scheme'
    } | Sort-Object)
    return $schemeAttributeCount -eq 2 -and
        $allUriHandlerSchemes.Count -eq $schemeAttributeCount -and
        ($allUriHandlerSchemes -join '|') -eq 'https|https'
}

function Test-SyntheticManifestFirebaseFree {
    param([Parameter(Mandatory)] [string]$ManifestText)

    return $ManifestText -notmatch '(?i)com\.google\.firebase|\bfirebase_[a-z0-9_]*\b|\bgoogle_analytics_[a-z0-9_]*\b|\bdelivery_metrics_exported_to_big_query_enabled\b|\bgoogle_app_id\b'
}

function Test-SyntheticArchiveFirebaseFree {
    param([Parameter(Mandatory)] [string[]]$EntryNames)

    return @(
        $EntryNames | Where-Object {
            $_ -match '(?i)(?:^|[/._-])firebase(?:[/._-]|$)|(?:^|[/._-])google[-_.]?services(?:[/._-]|$)'
        }
    ).Count -eq 0
}

function Test-SyntheticResourceTableFirebaseFree {
    param([Parameter(Mandatory)] [string]$ResourceTableText)

    return $ResourceTableText -notmatch '(?im)^\s*resource\s+0x[0-9a-f]+\s+string/google_app_id(?:\s|$)'
}

function Test-SyntheticDexFirebaseFree {
    param([Parameter(Mandatory)] [string]$DexText)

    return $DexText -notmatch '(?i)Lcom/(?:google/)?firebase/' -and
        $DexText -notmatch 'com/gurbakir/firebase/'
}

function Test-SyntheticHomeSourceContract {
    param([Parameter(Mandatory)] [string]$SourceText)

    return $SourceText -match 'HomeConfiguration\s*\(' -and
        $SourceText -match 'remoteSource\s*=\s*HomeRemoteSource\.Disabled' -and
        $SourceText -match 'packagedFallback\s*=\s*HomePackagedFallback\s*\(' -and
        $SourceText -notmatch 'HomeRemoteSource\.ShopifyMetaobject'
}

function Test-SyntheticHomeDexContentBoundary {
    param([Parameter(Mandatory)] [string]$DexText)

    $gurbakirHomeContent = @(
        'bardaklar',
        'cezveler',
        'tavalar-sahanlar',
        'tencereler',
        'ozel-urunlerimiz',
        'bakir-tava-ve-sahan-el-dovmesi-cift-pirinc-kulplu'
    )
    return @($gurbakirHomeContent | Where-Object {
        $DexText -match [regex]::Escape($_)
    }).Count -eq 0
}

function Invoke-PackageValidatorSelfTest {
    function Assert-FixtureMutation {
        param(
            [Parameter(Mandatory)] [string]$Name,
            [Parameter(Mandatory)] [bool]$Occurred
        )

        if (-not $Occurred) {
            throw "Synthetic-package self-test mutation did not occur: $Name"
        }
    }

    $safeManifest = @'
    E: application
      A: android:allowBackup(0x01010280)=(type 0x12)0x0
      A: android:fullBackupContent(0x010104eb)=(type 0x12)0x0
      A: android:dataExtractionRules(0x0101065e)=@0x7f110000
      A: android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0
      E: activity
        A: android:name(0x01010003)="com.example.gate2synthetic.MainActivity" (Raw: "com.example.gate2synthetic.MainActivity")
        E: intent-filter
          E: action
            A: android:name(0x01010003)="android.intent.action.MAIN" (Raw: "android.intent.action.MAIN")
          E: category
            A: android:name(0x01010003)="android.intent.category.LAUNCHER" (Raw: "android.intent.category.LAUNCHER")
        E: intent-filter
          A: android:autoVerify(0x010104ee)=(type 0x12)0x0
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: category
            A: android:name(0x01010003)="android.intent.category.DEFAULT" (Raw: "android.intent.category.DEFAULT")
          E: category
            A: android:name(0x01010003)="android.intent.category.BROWSABLE" (Raw: "android.intent.category.BROWSABLE")
          E: data
            A: android:scheme(0x01010027)="https" (Raw: "https")
            A: android:host(0x01010028)="links.gate2.invalid" (Raw: "links.gate2.invalid")
            A: android:pathPrefix(0x0101002b)="/collections/" (Raw: "/collections/")
        E: intent-filter
          A: android:autoVerify(0x010104ee)=(type 0x12)0x0
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: category
            A: android:name(0x01010003)="android.intent.category.DEFAULT" (Raw: "android.intent.category.DEFAULT")
          E: category
            A: android:name(0x01010003)="android.intent.category.BROWSABLE" (Raw: "android.intent.category.BROWSABLE")
          E: data
            A: android:scheme(0x01010027)="https" (Raw: "https")
            A: android:host(0x01010028)="links.gate2.invalid" (Raw: "links.gate2.invalid")
            A: android:pathPrefix(0x0101002b)="/apps/mobile/products/" (Raw: "/apps/mobile/products/")
      E: activity
        A: android:name(0x01010003)="net.openid.appauth.AuthorizationManagementActivity" (Raw: "net.openid.appauth.AuthorizationManagementActivity")
        A: android:exported(0x01010010)=(type 0x12)0x0
'@
    $appAuthActivityMarker = @'
      E: activity
        A: android:name(0x01010003)="net.openid.appauth.AuthorizationManagementActivity" (Raw: "net.openid.appauth.AuthorizationManagementActivity")
        A: android:exported(0x01010010)=(type 0x12)0x0
'@
    $orderFilter = @'
        E: intent-filter
          A: android:autoVerify(0x010104ee)=(type 0x12)0x0
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: category
            A: android:name(0x01010003)="android.intent.category.DEFAULT" (Raw: "android.intent.category.DEFAULT")
          E: category
            A: android:name(0x01010003)="android.intent.category.BROWSABLE" (Raw: "android.intent.category.BROWSABLE")
          E: data
            A: android:scheme(0x01010027)="https" (Raw: "https")
            A: android:host(0x01010028)="links.gate2.invalid" (Raw: "links.gate2.invalid")
            A: android:pathPrefix(0x0101002b)="/apps/mobile/orders/" (Raw: "/apps/mobile/orders/")
'@
    $orderRestored = $safeManifest.Replace($appAuthActivityMarker, "$orderFilter`n$appAuthActivityMarker")
    $redirectReceiverRestored = $safeManifest + @'

      E: activity
        A: android:name(0x01010003)="net.openid.appauth.RedirectUriReceiverActivity" (Raw: "net.openid.appauth.RedirectUriReceiverActivity")
        E: intent-filter
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: category
            A: android:name(0x01010003)="android.intent.category.DEFAULT" (Raw: "android.intent.category.DEFAULT")
          E: category
            A: android:name(0x01010003)="android.intent.category.BROWSABLE" (Raw: "android.intent.category.BROWSABLE")
          E: data
            A: android:scheme(0x01010027)="shop.0.gate2synthetic" (Raw: "shop.0.gate2synthetic")
'@
    $collectionFilterPattern = '(?m)^        E: intent-filter\r?\n(?:          [^\r\n]*(?:\r?\n|$))*?            A: android:pathPrefix[^\r\n]+/collections/[^\r\n]*(?:\r?\n|$)'
    $productFilterPattern = '(?m)^        E: intent-filter\r?\n(?:          [^\r\n]*(?:\r?\n|$))*?            A: android:pathPrefix[^\r\n]+/apps/mobile/products/[^\r\n]*(?:\r?\n|$)'
    $missingCollectionFilter = [regex]::Replace($safeManifest, $collectionFilterPattern, '', 1)
    $missingProductFilter = [regex]::Replace($safeManifest, $productFilterPattern, '', 1)
    $trueBoolean = $safeManifest.Replace(
        'android:allowBackup(0x01010280)=(type 0x12)0x0',
        'android:allowBackup(0x01010280)=(type 0x12)0xffffffff'
    )
    $trueAutoVerify = $safeManifest.Replace(
        'android:autoVerify(0x010104ee)=(type 0x12)0x0',
        'android:autoVerify(0x010104ee)=(type 0x12)0xffffffff'
    )
    $extraHandler = $safeManifest + @'

      E: activity
        A: android:name(0x01010003)="com.example.UnapprovedActivity" (Raw: "com.example.UnapprovedActivity")
        E: intent-filter
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: data
            A: android:scheme(0x01010027)="https" (Raw: "https")
            A: android:host(0x01010028)="merchant.example" (Raw: "merchant.example")
'@
    $customSchemeHandlerAdded = $safeManifest + @'

      E: activity-alias
        A: android:name(0x01010003)="com.example.UnapprovedAlias" (Raw: "com.example.UnapprovedAlias")
        A: android:targetActivity(0x01010202)="com.example.gate2synthetic.MainActivity" (Raw: "com.example.gate2synthetic.MainActivity")
        E: intent-filter
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: data
            A: android:scheme(0x01010027)="shop.unapproved" (Raw: "shop.unapproved")
'@
    $safeDexText = @'
gate2_synthetic_secure_cart_development
gate2.synthetic.cart.development.v1
gate2_synthetic_secure_customer_session_development
gate2.synthetic.customer.session.development.v1
mobile_home
primary
Lnet/openid/appauth/AuthorizationManagementActivity;
Lnet/openid/appauth/AuthorizationService;
Lnet/openid/appauth/RedirectUriReceiverActivity;
'@
    $safeSyntheticHomeSource = 'HomeConfiguration(remoteSource = HomeRemoteSource.Disabled, packagedFallback = HomePackagedFallback())'
    $remoteSyntheticHomeSource = $safeSyntheticHomeSource.Replace(
        'HomeRemoteSource.Disabled',
        'HomeRemoteSource.ShopifyMetaobject(selector)'
    )
    $gurbakirHomeDex = $safeDexText + "`nbardaklar"
    $retiredAccountStringRestored = $safeDexText + "`nhttps://accounts.gate2.invalid/oauth/authorize"
    $exportedAppAuthActivity = $safeManifest.Replace(
        'android:exported(0x01010010)=(type 0x12)0x0',
        'android:exported(0x01010010)=(type 0x12)0xffffffff'
    )
    $missingCompiledAppAuthClass = $safeDexText.Replace(
        'Lnet/openid/appauth/AuthorizationManagementActivity;',
        ''
    )
    $firebaseInitProviderManifest = $safeManifest + "`n      A: android:name=`"com.google.firebase.provider.FirebaseInitProvider`""
    $googleAppIdManifest = $safeManifest + "`n      A: android:name=`"google_app_id`""
    $firebaseMessagingMetadataManifest = $safeManifest + "`n      A: android:name=`"firebase_messaging_auto_init_enabled`""
    $firebaseCollectionMetadataManifest = $safeManifest + "`n      A: android:name=`"firebase_data_collection_default_enabled`""
    $firebaseDeliveryMetricsManifest = $safeManifest + "`n      A: android:name=`"delivery_metrics_exported_to_big_query_enabled`""
    $firebaseAnalyticsAdIdMetadataManifest = $safeManifest + "`n      A: android:name=`"google_analytics_adid_collection_enabled`""
    $firebaseAnalyticsPersonalizationMetadataManifest = $safeManifest + "`n      A: android:name=`"google_analytics_default_allow_ad_personalization_signals`""
    $firebaseAnalyticsScreenMetadataManifest = $safeManifest + "`n      A: android:name=`"google_analytics_automatic_screen_reporting_enabled`""
    $firebaseManifestNearMatches = $safeManifest + "`n      A: android:name=`"firebaseish_messaging_auto_init_enabled`"`n      A: android:name=`"delivery_metrics_exported_to_big_query_enabled_backup`"`n      A: android:name=`"google_analyticssafe_collection_enabled`""
    $googleFirebaseDex = $safeDexText + "`nLcom/google/firebase/FirebaseApp;"
    $gurbakirFirebaseDex = $safeDexText + "`ncom/gurbakir/firebase/RemoteFeatureFlags"
    $firebaseArchiveEntries = @('AndroidManifest.xml', 'classes.dex', 'assets/firebase/config')
    $firebaseKotlinModuleArchiveEntries = @('AndroidManifest.xml', 'META-INF/com.google.firebase-firebase-common.kotlin_module')
    $firebasePropertiesArchiveEntries = @('AndroidManifest.xml', 'firebase-common.properties')
    $firebaseRawResourceArchiveEntries = @('AndroidManifest.xml', 'res/raw/firebase_common_keep.xml')
    $firebaseArchiveNearMatches = @('AndroidManifest.xml', 'assets/firebased/config', 'META-INF/com.google.firebasesafe.kotlin_module')
    Assert-FixtureMutation 'FirebaseInitProvider manifest entry added' ($firebaseInitProviderManifest -match 'FirebaseInitProvider')
    Assert-FixtureMutation 'google_app_id manifest entry added' ($googleAppIdManifest -match 'google_app_id')
    Assert-FixtureMutation 'Firebase Messaging metadata added' ($firebaseMessagingMetadataManifest -match 'firebase_messaging_auto_init_enabled')
    Assert-FixtureMutation 'Firebase collection metadata added' ($firebaseCollectionMetadataManifest -match 'firebase_data_collection_default_enabled')
    Assert-FixtureMutation 'Firebase delivery metrics metadata added' ($firebaseDeliveryMetricsManifest -match 'delivery_metrics_exported_to_big_query_enabled')
    Assert-FixtureMutation 'Firebase Analytics advertising ID metadata added' ($firebaseAnalyticsAdIdMetadataManifest -match 'google_analytics_adid_collection_enabled')
    Assert-FixtureMutation 'Firebase Analytics personalization metadata added' ($firebaseAnalyticsPersonalizationMetadataManifest -match 'google_analytics_default_allow_ad_personalization_signals')
    Assert-FixtureMutation 'Firebase Analytics screen metadata added' ($firebaseAnalyticsScreenMetadataManifest -match 'google_analytics_automatic_screen_reporting_enabled')
    Assert-FixtureMutation 'Google Firebase DEX descriptor added' ($googleFirebaseDex -match 'Lcom/google/firebase/')
    Assert-FixtureMutation 'project Firebase DEX namespace added' ($gurbakirFirebaseDex -match 'com/gurbakir/firebase/')
    Assert-FixtureMutation 'Firebase archive entry added' ($firebaseArchiveEntries -contains 'assets/firebase/config')
    Assert-FixtureMutation 'Firebase Kotlin module archive entry added' (
        $firebaseKotlinModuleArchiveEntries -contains 'META-INF/com.google.firebase-firebase-common.kotlin_module'
    )
    Assert-FixtureMutation 'Firebase properties archive entry added' ($firebasePropertiesArchiveEntries -contains 'firebase-common.properties')
    Assert-FixtureMutation 'Firebase raw resource archive entry added' ($firebaseRawResourceArchiveEntries -contains 'res/raw/firebase_common_keep.xml')

    Assert-FixtureMutation 'Order HTTPS filter restored' (
        $safeManifest -notmatch '/apps/mobile/orders/' -and $orderRestored -match '/apps/mobile/orders/'
    )
    Assert-FixtureMutation 'AppAuth redirect receiver restored' (
        $safeManifest -notmatch 'RedirectUriReceiverActivity' -and
        $redirectReceiverRestored -match 'RedirectUriReceiverActivity'
    )
    Assert-FixtureMutation 'custom-scheme handler added' (
        $safeManifest -notmatch 'shop\.unapproved' -and $customSchemeHandlerAdded -match 'shop\.unapproved'
    )
    Assert-FixtureMutation 'Collection HTTPS filter removed' (
        ([regex]::Matches($safeManifest, $collectionFilterPattern)).Count -eq 1 -and
        $missingCollectionFilter -notmatch '/collections/' -and
        $missingCollectionFilter -match '/apps/mobile/products/'
    )
    Assert-FixtureMutation 'Product HTTPS filter removed' (
        ([regex]::Matches($safeManifest, $productFilterPattern)).Count -eq 1 -and
        $missingProductFilter -notmatch '/apps/mobile/products/' -and
        $missingProductFilter -match '/collections/'
    )
    Assert-FixtureMutation 'retired Customer Account endpoint restored' (
        $safeDexText -notmatch 'accounts\.gate2\.invalid' -and
        $retiredAccountStringRestored -match 'accounts\.gate2\.invalid'
    )
    Assert-FixtureMutation 'AppAuth management activity exported' (
        $safeManifest -match 'android:exported\(0x01010010\)=\(type 0x12\)0x0' -and
        $exportedAppAuthActivity -match 'android:exported\(0x01010010\)=\(type 0x12\)0xffffffff'
    )
    Assert-FixtureMutation 'compiled AppAuth management class removed' (
        $safeDexText -match 'Lnet/openid/appauth/AuthorizationManagementActivity;' -and
        $missingCompiledAppAuthClass -notmatch 'Lnet/openid/appauth/AuthorizationManagementActivity;'
    )
    $resourceReferencedAliasHandler = $safeManifest + @'

      E: activity-alias
        A: android:name(0x01010003)="com.example.ResourceAlias" (Raw: "com.example.ResourceAlias")
        A: android:targetActivity(0x01010202)="com.example.gate2synthetic.MainActivity" (Raw: "com.example.gate2synthetic.MainActivity")
        E: intent-filter
          E: action
            A: android:name(0x01010003)="android.intent.action.VIEW" (Raw: "android.intent.action.VIEW")
          E: data
            A: android:scheme(0x01010027)=@0x7f100001
'@
    $safeDataExtractionRules = @'
E: data-extraction-rules
  E: cloud-backup
    A: disableIfNoEncryptionCapabilities=(type 0x12)0xffffffff
    E: exclude
      A: domain="sharedpref" (Raw: "sharedpref")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="database" (Raw: "database")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="file" (Raw: "file")
      A: path="." (Raw: ".")
  E: device-transfer
    E: exclude
      A: domain="sharedpref" (Raw: "sharedpref")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="database" (Raw: "database")
      A: path="." (Raw: ".")
    E: exclude
      A: domain="file" (Raw: "file")
      A: path="." (Raw: ".")
'@
    $transferFilePattern = '(?m)^    E: exclude\r?\n      A: domain="file" \(Raw: "file"\)\r?\n      A: path="\." \(Raw: "\."\)(?:\r?\n|$)'
    $transferFileMatches = [regex]::Matches($safeDataExtractionRules, $transferFilePattern)
    if ($transferFileMatches.Count -ne 2) {
        throw "Synthetic-package self-test fixture is invalid."
    }
    $transferFileExclusion = $transferFileMatches[$transferFileMatches.Count - 1]
    $missingTransferExclusion = $safeDataExtractionRules.Remove(
        $transferFileExclusion.Index,
        $transferFileExclusion.Length
    )
    $wrongTransferBlock = $transferFileExclusion.Value.Replace(
        'domain="file" (Raw: "file")',
        'domain="sharedpref" (Raw: "sharedpref")',
        [System.StringComparison]::Ordinal
    )
    $wrongTransferDomain = $safeDataExtractionRules.Remove(
        $transferFileExclusion.Index,
        $transferFileExclusion.Length
    ).Insert($transferFileExclusion.Index, $wrongTransferBlock)
    $unexpectedInclusion = $safeDataExtractionRules + "`n    E: include`n      A: domain=`"file`" (Raw: `"file`")`n      A: path=`".`" (Raw: `".`")"
    $optimizedResourceDump = @'
  type xml id=11 entryCount=1
    resource 0x7f110000 xml/data_extraction_rules
      () (file) res/4j.xml type=XML
'@
    $googleAppIdResourceDump = $optimizedResourceDump + "`n    resource 0x7f120000 string/google_app_id`n      () `"synthetic-firebase-id`""
    Assert-FixtureMutation 'google_app_id resource added' (
        $optimizedResourceDump -notmatch 'string/google_app_id' -and
        $googleAppIdResourceDump -match 'string/google_app_id'
    )
    $ambiguousResourceDump = $optimizedResourceDump + "`n      () (file) res/other.xml type=XML"
    $mismatchedDataExtractionManifest = $safeManifest.Replace('@0x7f110000', '@0x7f110001')

    $results = @(
        [pscustomobject]@{ test = 'neutral manifest fixture is Firebase-free'; passed = (Test-SyntheticManifestFirebaseFree $safeManifest) }
        [pscustomobject]@{ test = 'FirebaseInitProvider manifest entry is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseInitProviderManifest) }
        [pscustomobject]@{ test = 'google_app_id manifest entry is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $googleAppIdManifest) }
        [pscustomobject]@{ test = 'Firebase Messaging metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseMessagingMetadataManifest) }
        [pscustomobject]@{ test = 'Firebase collection metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseCollectionMetadataManifest) }
        [pscustomobject]@{ test = 'Firebase delivery metrics metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseDeliveryMetricsManifest) }
        [pscustomobject]@{ test = 'Firebase Analytics advertising ID metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseAnalyticsAdIdMetadataManifest) }
        [pscustomobject]@{ test = 'Firebase Analytics personalization metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseAnalyticsPersonalizationMetadataManifest) }
        [pscustomobject]@{ test = 'Firebase Analytics screen metadata is rejected'; passed = -not (Test-SyntheticManifestFirebaseFree $firebaseAnalyticsScreenMetadataManifest) }
        [pscustomobject]@{ test = 'Firebase manifest near matches remain accepted'; passed = (Test-SyntheticManifestFirebaseFree $firebaseManifestNearMatches) }
        [pscustomobject]@{ test = 'neutral DEX fixture is Firebase-free'; passed = (Test-SyntheticDexFirebaseFree $safeDexText) }
        [pscustomobject]@{ test = 'Google Firebase DEX descriptor is rejected'; passed = -not (Test-SyntheticDexFirebaseFree $googleFirebaseDex) }
        [pscustomobject]@{ test = 'project Firebase DEX namespace is rejected'; passed = -not (Test-SyntheticDexFirebaseFree $gurbakirFirebaseDex) }
        [pscustomobject]@{ test = 'synthetic Home source is explicitly disabled'; passed = (Test-SyntheticHomeSourceContract $safeSyntheticHomeSource) }
        [pscustomobject]@{ test = 'synthetic remote Home source is rejected'; passed = -not (Test-SyntheticHomeSourceContract $remoteSyntheticHomeSource) }
        [pscustomobject]@{ test = 'neutral schema and selector strings remain allowed in DEX'; passed = (Test-SyntheticHomeDexContentBoundary $safeDexText) }
        [pscustomobject]@{ test = 'concrete Gürbakır Home content is rejected from DEX'; passed = -not (Test-SyntheticHomeDexContentBoundary $gurbakirHomeDex) }
        [pscustomobject]@{ test = 'neutral archive fixture is Firebase-free'; passed = (Test-SyntheticArchiveFirebaseFree @('AndroidManifest.xml', 'classes.dex')) }
        [pscustomobject]@{ test = 'Firebase archive entry is rejected'; passed = -not (Test-SyntheticArchiveFirebaseFree $firebaseArchiveEntries) }
        [pscustomobject]@{ test = 'Firebase Kotlin module archive entry is rejected'; passed = -not (Test-SyntheticArchiveFirebaseFree $firebaseKotlinModuleArchiveEntries) }
        [pscustomobject]@{ test = 'Firebase properties archive entry is rejected'; passed = -not (Test-SyntheticArchiveFirebaseFree $firebasePropertiesArchiveEntries) }
        [pscustomobject]@{ test = 'Firebase raw resource archive entry is rejected'; passed = -not (Test-SyntheticArchiveFirebaseFree $firebaseRawResourceArchiveEntries) }
        [pscustomobject]@{ test = 'Firebase archive near matches remain accepted'; passed = (Test-SyntheticArchiveFirebaseFree $firebaseArchiveNearMatches) }
        [pscustomobject]@{ test = 'neutral resource fixture is Firebase-free'; passed = (Test-SyntheticResourceTableFirebaseFree $optimizedResourceDump) }
        [pscustomobject]@{ test = 'google_app_id resource-table entry is rejected'; passed = -not (Test-SyntheticResourceTableFirebaseFree $googleAppIdResourceDump) }
        [pscustomobject]@{ test = 'exact reduced manifest fixture passes'; passed = (Test-SyntheticDeepLinkManifestContract $safeManifest) }
        [pscustomobject]@{ test = 'typed true is rejected for restrictive manifest boolean'; passed = -not (Test-AaptBooleanAttributeFalse $trueBoolean 'allowBackup') }
        [pscustomobject]@{ test = 'typed true is rejected for autoVerify'; passed = -not (Test-SyntheticDeepLinkManifestContract $trueAutoVerify) }
        [pscustomobject]@{ test = 'restored Order HTTPS filter is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $orderRestored) }
        [pscustomobject]@{ test = 'restored AppAuth redirect receiver is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $redirectReceiverRestored) }
        [pscustomobject]@{ test = 'missing Collection HTTPS filter is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $missingCollectionFilter) }
        [pscustomobject]@{ test = 'missing Product HTTPS filter is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $missingProductFilter) }
        [pscustomobject]@{ test = 'extra URI handler is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $extraHandler) }
        [pscustomobject]@{ test = 'added custom-scheme handler is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $customSchemeHandlerAdded) }
        [pscustomobject]@{ test = 'resource-referenced activity-alias URI handler is rejected'; passed = -not (Test-SyntheticDeepLinkManifestContract $resourceReferencedAliasHandler) }
        [pscustomobject]@{
            test = 'restored retired Customer Account endpoint is rejected'
            passed =
                $null -ne (Get-Command Test-RetiredSyntheticAccountStringsAbsent -ErrorAction SilentlyContinue) -and
                -not (Test-RetiredSyntheticAccountStringsAbsent $retiredAccountStringRestored)
        }
        [pscustomobject]@{
            test = 'compiled non-exported AppAuth boundary passes'
            passed =
                $null -ne (Get-Command Test-SyntheticAppAuthBoundary -ErrorAction SilentlyContinue) -and
                (Test-SyntheticAppAuthBoundary $safeManifest $safeDexText)
        }
        [pscustomobject]@{
            test = 'exported AppAuth management activity is rejected'
            passed =
                $null -ne (Get-Command Test-SyntheticAppAuthBoundary -ErrorAction SilentlyContinue) -and
                -not (Test-SyntheticAppAuthBoundary $exportedAppAuthActivity $safeDexText)
        }
        [pscustomobject]@{
            test = 'missing compiled AppAuth management class is rejected'
            passed =
                $null -ne (Get-Command Test-SyntheticAppAuthBoundary -ErrorAction SilentlyContinue) -and
                -not (Test-SyntheticAppAuthBoundary $safeManifest $missingCompiledAppAuthClass)
        }
        [pscustomobject]@{ test = 'exact backup and device-transfer exclusions pass'; passed = (Test-DataExtractionRulesContract $safeDataExtractionRules) }
        [pscustomobject]@{ test = 'missing transfer exclusion is rejected'; passed = -not (Test-DataExtractionRulesContract $missingTransferExclusion) }
        [pscustomobject]@{ test = 'duplicated transfer domain is rejected'; passed = -not (Test-DataExtractionRulesContract $wrongTransferDomain) }
        [pscustomobject]@{ test = 'unexpected backup inclusion is rejected'; passed = -not (Test-DataExtractionRulesContract $unexpectedInclusion) }
        [pscustomobject]@{ test = 'optimized compiled XML path is resolved from the resource table'; passed = (Get-CompiledXmlResourcePath $optimizedResourceDump 'xml/data_extraction_rules') -eq 'res/4j.xml' }
        [pscustomobject]@{ test = 'ambiguous compiled XML path is rejected'; passed = $null -eq (Get-CompiledXmlResourcePath $ambiguousResourceDump 'xml/data_extraction_rules') }
        [pscustomobject]@{ test = 'manifest-referenced compiled XML path is resolved'; passed = (Get-ManifestReferencedCompiledXmlPath $safeManifest $optimizedResourceDump 'dataExtractionRules') -eq 'res/4j.xml' }
        [pscustomobject]@{ test = 'manifest and resource-table ID mismatch is rejected'; passed = $null -eq (Get-ManifestReferencedCompiledXmlPath $mismatchedDataExtractionManifest $optimizedResourceDump 'dataExtractionRules') }
    )
    $results | Format-Table -AutoSize | Out-Host
    $failed = @($results | Where-Object { -not $_.passed })
    if ($failed.Count -gt 0) {
        throw "Gate 2 package validator self-tests failed: $($failed.test -join '; ')"
    }
    Write-Host "Gate 2 package validator self-tests PASS ($($results.Count) fixtures)."
}

function Test-VariantPackage {
    param(
        [Parameter(Mandatory)] [string]$VariantName,
        [Parameter(Mandatory)] [string]$Aapt,
        [Parameter(Mandatory)] [string]$Aapt2,
        [Parameter(Mandatory)] [string]$ApkSigner,
        [Parameter(Mandatory)] [string]$DexDump
    )

    $artifact = Find-ApkArtifact -VariantName $VariantName
    $apk = $artifact.ApkPath
    $expectedApplicationId = if ($VariantName -eq 'debug') {
        'com.example.gate2synthetic.debug'
    } else {
        'com.example.gate2synthetic'
    }
    $prefix = "synthetic $VariantName"
    $syntheticConfiguration = Get-Content -LiteralPath (
        Join-Path $repoRoot 'apps/synthetic/src/main/kotlin/com/example/gate2synthetic/config/Gate2SyntheticConfiguration.kt'
    ) -Raw -Encoding utf8
    Add-Check "$prefix production composition consumes disabled Home remote source" (
        Test-SyntheticHomeSourceContract $syntheticConfiguration
    ) 'HomeConfiguration uses Disabled with an independent packaged fallback'
    $relativeApk = $apk.Substring($repoRoot.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash

    Add-Check "$prefix APK is discovered through output metadata" $true "$relativeApk; SHA256 $hash"
    Add-Check "$prefix metadata has the exact application identity" ($artifact.Metadata.applicationId -eq $expectedApplicationId) "applicationId $($artifact.Metadata.applicationId)"

    $badging = Invoke-NativeCapture $Aapt @('dump', 'badging', $apk)
    Add-Check "$prefix badging is readable" ($badging.ExitCode -eq 0) "aapt exit $($badging.ExitCode)"
    $badgingExact = $badging.Output -match "package: name='$([regex]::Escape($expectedApplicationId))' versionCode='1' versionName='0\.1\.0-gate2'" -and
        $badging.Output -match "compileSdkVersion='36'" -and
        $badging.Output -match "sdkVersion:'23'" -and
        $badging.Output -match "targetSdkVersion:'36'" -and
        $badging.Output -match "application-label:'Gate 2 Synthetic'" -and
        $badging.Output -match "launchable-activity: name='com\.example\.gate2synthetic\.MainActivity'"
    Add-Check "$prefix badging matches the approved package contract" $badgingExact 'ID/version/SDK/label/launcher checked'
    $debuggableCorrect = if ($VariantName -eq 'debug') {
        $badging.Output -match '(?m)^application-debuggable\s*$'
    } else {
        $badging.Output -notmatch '(?m)^application-debuggable\s*$'
    }
    Add-Check "$prefix debuggable state matches build type" $debuggableCorrect $(if ($VariantName -eq 'debug') { 'debuggable' } else { 'not debuggable' })

    $permissions = Invoke-NativeCapture $Aapt @('dump', 'permissions', $apk)
    $noInternetPermission = $permissions.ExitCode -eq 0 -and
        $permissions.Output -notmatch '(?im)uses-permission:.*android\.permission\.INTERNET'
    Add-Check "$prefix has no INTERNET permission" $noInternetPermission 'merged permissions inspected'

    $manifest = Invoke-NativeCapture $Aapt @('dump', 'xmltree', $apk, 'AndroidManifest.xml')
    Add-Check "$prefix merged manifest is readable" ($manifest.ExitCode -eq 0) "aapt exit $($manifest.ExitCode)"
    $dataExtractionRulesReferenceId = Get-AaptResourceReferenceId $manifest.Output 'dataExtractionRules'
    $manifestIdentity = $manifest.Output -match 'com\.example\.gate2synthetic\.Gate2SyntheticApplication' -and
        $manifest.Output -match 'com\.example\.gate2synthetic\.MainActivity' -and
        (Test-AaptBooleanAttributeFalse -XmlTree $manifest.Output -AttributeName 'allowBackup') -and
        (Test-AaptBooleanAttributeFalse -XmlTree $manifest.Output -AttributeName 'fullBackupContent') -and
        (Test-AaptBooleanAttributeFalse -XmlTree $manifest.Output -AttributeName 'usesCleartextTraffic') -and
        $null -ne $dataExtractionRulesReferenceId
    Add-Check "$prefix manifest owns the synthetic application and restrictive storage/network flags" $manifestIdentity 'Application, Activity, backup, transfer and cleartext flags checked'
    $deepLinksExact = Test-SyntheticDeepLinkManifestContract -XmlTree $manifest.Output
    Add-Check "$prefix manifest contains only the approved inert HTTPS links" $deepLinksExact 'Collection and Product prefixes only; autoVerify=false; no redirect receiver or custom scheme'
    $manifestFirebaseFree = Test-SyntheticManifestFirebaseFree $manifest.Output
    $manifestGurbakirFree = $manifest.Output -notmatch '(?i)gurbakir\.com|com\.gurbakir\.mobile\.(?:GurbakirApplication|MainActivity)|gurbakir-local\.db'
    Add-Check "$prefix manifest has no Firebase registrations or Gürbakır app identity" ($manifestFirebaseFree -and $manifestGurbakirFree) 'merged manifest strings inspected'

    $resources = Invoke-NativeCapture $Aapt2 @('dump', 'resources', $apk)
    Add-Check "$prefix resource table is readable" ($resources.ExitCode -eq 0) "aapt2 exit $($resources.ExitCode)"
    Add-Check "$prefix resource table has no generated Firebase configuration" (
        $resources.ExitCode -eq 0 -and (Test-SyntheticResourceTableFirebaseFree $resources.Output)
    ) 'string/google_app_id absent'
    $dataExtractionRulesPath = if ($null -eq $dataExtractionRulesReferenceId) {
        $null
    } else {
        Get-CompiledXmlResourcePathById $resources.Output $dataExtractionRulesReferenceId
    }
    $dataExtractionRules = if ($null -eq $dataExtractionRulesPath) {
        [pscustomobject]@{ ExitCode = -1; Output = '' }
    } else {
        Invoke-NativeCapture $Aapt @('dump', 'xmltree', $apk, $dataExtractionRulesPath)
    }
    Add-Check "$prefix data-extraction rules are readable" ($dataExtractionRules.ExitCode -eq 0) $(if ($null -eq $dataExtractionRulesPath) { 'resource-table path missing or ambiguous' } else { "$dataExtractionRulesPath; aapt exit $($dataExtractionRules.ExitCode)" })
    Add-Check "$prefix excludes shared preferences databases and files from cloud backup and device transfer" (Test-DataExtractionRulesContract $dataExtractionRules.Output) 'decoded compiled XML has the exact six exclusions'

    foreach ($resourceExpectation in @(
        [pscustomobject]@{ Name = 'string/app_name'; Value = 'Gate 2 Synthetic' }
        [pscustomobject]@{ Name = 'string/home_title'; Value = 'Synthetic Lab' }
        [pscustomobject]@{ Name = 'string/home_product_range_title'; Value = 'Synthetic Picks' }
    )) {
        $block = Get-ResourceBlock $resources.Output $resourceExpectation.Name
        $escapedValue = [regex]::Escape($resourceExpectation.Value)
        $defaultPattern = '(?m)^\s*\(\)\s+"' + $escapedValue + '"\s*$'
        $englishPattern = '(?m)^\s*\(en\)\s+"' + $escapedValue + '"\s*$'
        $defaultAndEnglish = $block -match $defaultPattern -and $block -match $englishPattern
        Add-Check "$prefix packages $($resourceExpectation.Name) in default and English" $defaultAndEnglish $resourceExpectation.Value
    }
    Add-Check "$prefix packages the synthetic Android theme" ($resources.Output -match '(?m)^\s*resource\s+0x[0-9a-f]+\s+style/Theme\.Gate2Synthetic\s*$') 'style/Theme.Gate2Synthetic present'

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $dexEntries = @($archive.Entries | Where-Object { $_.FullName -match '^classes(?:\d+)?\.dex$' })
        $archiveFirebaseFree = Test-SyntheticArchiveFirebaseFree $entryNames
        Add-Check "$prefix APK archive contains DEX and no Firebase-owned entries" ($dexEntries.Count -gt 0 -and $archiveFirebaseFree) "DEX files $($dexEntries.Count); Firebase-free $archiveFirebaseFree"

        $dexTempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gate2-dex-$([guid]::NewGuid().ToString('N'))"
        [void](New-Item -ItemType Directory -Path $dexTempRoot)
        try {
            $dexStrings = [System.Text.StringBuilder]::new()
            $dexChecksumsValid = $true
            foreach ($entry in $dexEntries) {
                $dexPath = Join-Path $dexTempRoot ([System.IO.Path]::GetFileName($entry.FullName))
                $input = $entry.Open()
                $output = [System.IO.File]::Create($dexPath)
                try {
                    $input.CopyTo($output)
                } finally {
                    $output.Dispose()
                    $input.Dispose()
                }
                $checksumResult = Invoke-NativeCapture $DexDump @('-c', $dexPath)
                if ($checksumResult.ExitCode -ne 0) {
                    $dexChecksumsValid = $false
                }
                $stringsResult = Invoke-NativeCapture $DexDump @('-s', $dexPath)
                if ($stringsResult.ExitCode -ne 0) {
                    $dexChecksumsValid = $false
                }
                [void]$dexStrings.AppendLine($stringsResult.Output)
            }
            $dexText = $dexStrings.ToString()
            Add-Check "$prefix DEX checksums and string tables are readable" $dexChecksumsValid "$($dexEntries.Count) DEX files"
            $syntheticEntrypointsPresent = $dexText -match 'com/example/gate2synthetic/Gate2SyntheticApplication' -and
                $dexText -match 'com/example/gate2synthetic/MainActivity'
            Add-Check "$prefix DEX contains the synthetic Application and Activity" $syntheticEntrypointsPresent 'manifest entrypoint descriptors checked'
            $dexFirebaseFree = Test-SyntheticDexFirebaseFree $dexText
            Add-Check "$prefix DEX contains no Firebase implementation namespace" $dexFirebaseFree 'string tables inspected without claiming removal of unrelated dormant Gürbakır constants'
            Add-Check "$prefix DEX contains no concrete Gürbakır Home merchandising" (
                Test-SyntheticHomeDexContentBoundary $dexText
            ) 'exact Gürbakır collection and featured-product handles absent; neutral schema strings are allowed'
            Add-Check "$prefix retains compiled AppAuth code behind a non-exported management activity" (Test-SyntheticAppAuthBoundary $manifest.Output $dexText) 'AuthorizationManagementActivity manifest component and DEX descriptor checked'
            Add-Check "$prefix DEX contains no retired synthetic Customer Account or Order input" (Test-RetiredSyntheticAccountStringsAbsent $dexText) 'dummy Account endpoint, full callback URI and Order base checked'
            $syntheticProtectedIdentities = @(
                'gate2_synthetic_secure_cart_development',
                'gate2.synthetic.cart.development.v1',
                'gate2_synthetic_secure_customer_session_development',
                'gate2.synthetic.customer.session.development.v1'
            )
            $syntheticProtectedIdentitiesExact = @($syntheticProtectedIdentities | Where-Object {
                $dexText -notmatch [regex]::Escape($_)
            }).Count -eq 0
            Add-Check "$prefix DEX contains every exact synthetic protected-store identity" $syntheticProtectedIdentitiesExact 'two preference names and two Keystore aliases checked'
            $gurbakirProtectedIdentities = @(
                'gurbakir_secure_cart_development',
                'gurbakir_secure_cart_staging',
                'gurbakir.cart.development.v1',
                'gurbakir.cart.staging.v1',
                'gurbakir_secure_customer_session_development',
                'gurbakir_secure_customer_session_staging',
                'gurbakir.customer.session.development.v1',
                'gurbakir.customer.session.staging.v1'
            )
            $gurbakirProtectedIdentitiesAbsent = @($gurbakirProtectedIdentities | Where-Object {
                $dexText -match [regex]::Escape($_)
            }).Count -eq 0
            Add-Check "$prefix DEX contains no exact Gürbakır protected-store identity" $gurbakirProtectedIdentitiesAbsent 'all development and staging names and aliases absent'
            $databaseIdentityExact = $dexText -match [regex]::Escape('gate2-synthetic-local.db') -and
                $dexText -notmatch [regex]::Escape('gurbakir-local.db')
            $gurbakirApplicationIdentityAbsent = $dexText -notmatch 'com/gurbakir/mobile/GurbakirApplication' -and
                $dexText -notmatch 'com\.gurbakir\.mobile\.GurbakirApplication'
            Add-Check "$prefix DEX has the synthetic database and no Gürbakır database or Application identity" ($databaseIdentityExact -and $gurbakirApplicationIdentityAbsent) 'exact database and application-class strings checked'
            $usableCredentialPattern = '(?i)(?:shpat_|shpca_|shpss_|shpua_|ghp_|AIza[0-9A-Za-z_-]{20,}|eyJ[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.)'
            Add-Check "$prefix DEX contains no recognizable usable credential" ($dexText -notmatch $usableCredentialPattern) 'known credential prefixes and JWT structure checked'
        }
        finally {
            $resolvedDexRoot = [System.IO.Path]::GetFullPath($dexTempRoot)
            $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
            if ($resolvedDexRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
                (Split-Path -Leaf $resolvedDexRoot) -like 'gate2-dex-*' -and
                (Test-Path -LiteralPath $resolvedDexRoot -PathType Container)) {
                Remove-Item -LiteralPath $resolvedDexRoot -Recurse -Force
            }
        }
    }
    finally {
        $archive.Dispose()
    }

    $signature = Invoke-NativeCapture $ApkSigner @('verify', '--print-certs', $apk)
    $signatureCorrect = if ($VariantName -eq 'debug') {
        $signature.ExitCode -eq 0
    } else {
        $signature.ExitCode -ne 0
    }
    Add-Check "$prefix signing state matches the non-production contract" $signatureCorrect $(if ($VariantName -eq 'debug') { 'debug-signed' } else { 'unsigned release' })
    if ($VariantName -eq 'release') {
        $mapping = Join-Path $repoRoot 'apps/synthetic/build/outputs/mapping/release/mapping.txt'
        Add-Check 'synthetic release was processed by R8' (Test-Path -LiteralPath $mapping -PathType Leaf) 'release mapping.txt present'
    }
}

if ($SelfTest) {
    Invoke-PackageValidatorSelfTest
    exit 0
}

$sdkRoot = Resolve-AndroidSdkRoot
$aapt = Resolve-BuildTool $sdkRoot 'aapt'
$aapt2 = Resolve-BuildTool $sdkRoot 'aapt2'
$apkSigner = Resolve-BuildTool $sdkRoot 'apksigner'
$dexDump = Resolve-BuildTool $sdkRoot 'dexdump'
$variants = if ($Variant -eq 'All') { @('debug', 'release') } else { @($Variant.ToLowerInvariant()) }

foreach ($variantName in $variants) {
    Test-VariantPackage $variantName $aapt $aapt2 $apkSigner $dexDump
}

$checks | Format-Table -AutoSize | Out-Host
$failed = @($checks | Where-Object { -not $_.passed })
if ($failed.Count -gt 0) {
    throw "Gate 2 synthetic package validation failed: $($failed.check -join '; ')"
}

Write-Host "Gate 2 synthetic package validation PASS ($($checks.Count) checks)."
exit 0
