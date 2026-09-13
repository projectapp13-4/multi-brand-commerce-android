[CmdletBinding()]
param(
    [switch]$RequireCleanWorktree,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [bool]$Passed,
        [Parameter(Mandatory)] [string]$Evidence
    )

    $checks.Add([pscustomobject]@{
        check = $Name
        passed = $Passed
        evidence = $Evidence
    })
}

function Read-Text {
    param([Parameter(Mandatory)] [string]$RelativePath)

    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return $null
    }
    return Get-Content -LiteralPath $path -Raw
}

function Get-ModuleInventory {
    return @(
        [pscustomobject]@{ LogicalPath = ':app'; Directory = 'app'; Role = 'application-gurbakir' }
        [pscustomobject]@{ LogicalPath = ':synthetic'; Directory = 'apps/synthetic'; Role = 'application-synthetic' }
        [pscustomobject]@{ LogicalPath = ':mobile-core'; Directory = 'mobile-core'; Role = 'shared' }
        [pscustomobject]@{ LogicalPath = ':foundation'; Directory = 'foundation'; Role = 'shared' }
        [pscustomobject]@{ LogicalPath = ':storefront'; Directory = 'storefront'; Role = 'shared' }
        [pscustomobject]@{ LogicalPath = ':account'; Directory = 'account'; Role = 'shared' }
        [pscustomobject]@{ LogicalPath = ':checkout'; Directory = 'checkout'; Role = 'shared' }
        [pscustomobject]@{ LogicalPath = ':firebase'; Directory = 'firebase'; Role = 'provider' }
    )
}

function Get-IncludedProjectPaths {
    param([Parameter(Mandatory)] [string]$SettingsText)

    $declared = @(
        [regex]::Matches($SettingsText, '(?s)\binclude\s*\((?<arguments>.*?)\)') |
            ForEach-Object {
                [regex]::Matches($_.Groups['arguments'].Value, '["''](?<project>:[^"'']+)["'']') |
                    ForEach-Object { $_.Groups['project'].Value }
            } |
            Select-Object -Unique
    )
    $expanded = [System.Collections.Generic.List[string]]::new()
    foreach ($projectPath in $declared) {
        $segments = @($projectPath.TrimStart(':').Split(':', [System.StringSplitOptions]::RemoveEmptyEntries))
        for ($segmentCount = 1; $segmentCount -le $segments.Count; $segmentCount++) {
            $expanded.Add(':' + (($segments | Select-Object -First $segmentCount) -join ':'))
        }
    }
    return @($expanded | Select-Object -Unique)
}

function Get-UnsupportedIncludeExpressions {
    param([Parameter(Mandatory)] [string]$SettingsText)

    return @(
        [regex]::Matches($SettingsText, '(?s)\binclude\s*\((?<arguments>.*?)\)') |
            ForEach-Object {
                $arguments = $_.Groups['arguments'].Value
                $residual = [regex]::Replace($arguments, '["'']:[^"'']+["'']', '')
                $residual = [regex]::Replace($residual, '[\s,]', '')
                if (-not [string]::IsNullOrEmpty($residual)) {
                    $arguments.Trim()
                }
            }
    )
}

function Get-ProjectDirectoryMappings {
    param([Parameter(Mandatory)] [string]$SettingsText)

    $mappings = @{}
    $pattern = '\bproject\s*\(\s*["''](?<project>:[^"'']+)["'']\s*\)\s*\.projectDir\s*=\s*file\s*\(\s*["''](?<directory>[^"'']+)["'']\s*\)'
    foreach ($match in [regex]::Matches($SettingsText, $pattern)) {
        $mappings[$match.Groups['project'].Value] = $match.Groups['directory'].Value.Replace('\', '/')
    }
    return $mappings
}

function Test-ApprovedTopology {
    param(
        [Parameter(Mandatory)] [string]$SettingsText,
        [Parameter(Mandatory)] [object[]]$Inventory
    )

    $includedProjects = @(Get-IncludedProjectPaths -SettingsText $SettingsText)
    $unsupportedIncludes = @(Get-UnsupportedIncludeExpressions -SettingsText $SettingsText)
    $expectedProjects = @($Inventory | ForEach-Object { $_.LogicalPath })
    $missingProjects = @($expectedProjects | Where-Object { $_ -notin $includedProjects })
    $unexpectedProjects = @($includedProjects | Where-Object { $_ -notin $expectedProjects })
    $mappings = Get-ProjectDirectoryMappings -SettingsText $SettingsText
    $syntheticMappingCorrect = $mappings.ContainsKey(':synthetic') -and
        $mappings[':synthetic'] -eq 'apps/synthetic'
    $passed = $missingProjects.Count -eq 0 -and
        $unexpectedProjects.Count -eq 0 -and
        $includedProjects.Count -eq $expectedProjects.Count -and
        $unsupportedIncludes.Count -eq 0 -and
        ':apps' -notin $includedProjects -and
        $syntheticMappingCorrect
    $evidence = if ($passed) {
        "8 included subprojects; :synthetic -> apps/synthetic; no :apps project"
    } else {
        "missing: $($missingProjects -join ', '); unexpected: $($unexpectedProjects -join ', '); unsupported include expressions: $($unsupportedIncludes.Count); synthetic mapping: $($mappings[':synthetic'])"
    }
    return [pscustomobject]@{ Passed = $passed; Evidence = $evidence }
}

function Get-GradleProjectCallArguments {
    param([Parameter(Mandatory)] [string]$BuildScript)

    $argumentsList = [System.Collections.Generic.List[string]]::new()
    foreach ($projectCall in [regex]::Matches($BuildScript, '\bproject\s*\(')) {
        $argumentStart = $projectCall.Index + $projectCall.Length
        $parenthesisDepth = 1
        $insideString = $false
        $quote = [char]0
        $escapedCharacter = $false
        for ($index = $argumentStart; $index -lt $BuildScript.Length; $index++) {
            $character = $BuildScript[$index]
            if ($insideString) {
                if ($escapedCharacter) {
                    $escapedCharacter = $false
                } elseif ($character -eq '\') {
                    $escapedCharacter = $true
                } elseif ($character -eq $quote) {
                    $insideString = $false
                }
                continue
            }
            if ($character -eq '"' -or $character -eq "'") {
                $insideString = $true
                $quote = $character
            } elseif ($character -eq '(') {
                $parenthesisDepth++
            } elseif ($character -eq ')') {
                $parenthesisDepth--
                if ($parenthesisDepth -eq 0) {
                    $argumentsList.Add($BuildScript.Substring($argumentStart, $index - $argumentStart))
                    break
                }
            }
        }
    }
    return @($argumentsList)
}

function Test-SupportedGradleProjectCall {
    param([Parameter(Mandatory)] [string]$Arguments)

    $quotedProject = '["'']:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*["'']'
    return $Arguments -match ('^\s*' + $quotedProject + '\s*$') -or
        $Arguments -match ('^\s*path\s*=\s*' + $quotedProject + '\s*$') -or
        $Arguments -match ('^\s*mapOf\s*\(\s*["'']path["'']\s+to\s*' + $quotedProject + '\s*\)\s*$')
}

function Test-GradleProjectDependencySyntax {
    param([Parameter(Mandatory)] [string]$BuildScript)

    foreach ($arguments in Get-GradleProjectCallArguments -BuildScript $BuildScript) {
        if (-not (Test-SupportedGradleProjectCall -Arguments $arguments)) {
            return $false
        }
    }
    return $true
}

function Test-GradleProjectDependency {
    param(
        [Parameter(Mandatory)] [string]$BuildScript,
        [Parameter(Mandatory)] [string]$TargetProject
    )

    $accessor = ($TargetProject.TrimStart(':').Split('-') | ForEach-Object -Begin { $first = $true } -Process {
        if ($first) {
            $first = $false
            $_
        } else {
            $_.Substring(0, 1).ToUpperInvariant() + $_.Substring(1)
        }
    }) -join ''
    if ($BuildScript -match "projects\.$([regex]::Escape($accessor))\b") {
        return $true
    }

    $target = [regex]::Escape($TargetProject)
    $quotedTarget = '["'']' + $target + '["'']'
    foreach ($arguments in Get-GradleProjectCallArguments -BuildScript $BuildScript) {
        $isPositionalPath = $arguments -match ('^\s*' + $quotedTarget + '\s*$')
        $isNamedPath = $arguments -match ('^\s*path\s*=\s*' + $quotedTarget + '\s*$')
        $isMapPath = $arguments -match ('^\s*mapOf\s*\(\s*["'']path["'']\s+to\s*' + $quotedTarget + '\s*\)\s*$')
        if ($isPositionalPath -or $isNamedPath -or $isMapPath) {
            return $true
        }
    }
    return $false
}

function Get-ProductionSourceRecords {
    param(
        [Parameter(Mandatory)] [string]$Root,
        [Parameter(Mandatory)] [string]$RelativeRoot
    )

    $sourceRoot = Join-Path $Root $RelativeRoot
    if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
        return @()
    }
    return @(
        Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
            Where-Object { $_.Extension -in @('.kt', '.java', '.xml') } |
            ForEach-Object {
                [pscustomobject]@{
                    path = $_.FullName.Substring($Root.Length + 1).Replace('\', '/')
                    text = Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8
                }
            }
    )
}

function Get-DecodedResourceRecords {
    param(
        [Parameter(Mandatory)] [string]$Root,
        [Parameter(Mandatory)] [string]$RelativeResourceRoot
    )

    $resourceRoot = Join-Path $Root $RelativeResourceRoot
    if (-not (Test-Path -LiteralPath $resourceRoot -PathType Container)) {
        return @()
    }
    return @(
        Get-ChildItem -LiteralPath $resourceRoot -Recurse -File -Filter '*.xml' |
            ForEach-Object {
                $relativePath = $_.FullName.Substring($Root.Length + 1).Replace('\', '/')
                $resourceDocument = [xml](Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8)
                if ($resourceDocument.DocumentElement.LocalName -eq 'resources') {
                    foreach ($resourceNode in $resourceDocument.DocumentElement.ChildNodes) {
                        if ($resourceNode.NodeType -eq [System.Xml.XmlNodeType]::Element) {
                            [pscustomobject]@{
                                path = $relativePath
                                resource = "$($resourceNode.LocalName)/$($resourceNode.GetAttribute('name'))"
                                value = $resourceNode.InnerText
                            }
                        }
                    }
                }
            }
    )
}

function Find-RecordMatches {
    param(
        [Parameter(Mandatory)] [object[]]$Records,
        [Parameter(Mandatory)] [string]$Pattern,
        [string]$ValueProperty = 'text'
    )

    return @(
        $Records |
            Where-Object { $_.$ValueProperty -match $Pattern } |
            ForEach-Object { $_.path }
    )
}

function Get-StringLiteralValues {
    param([Parameter(Mandatory)] [string]$Text)

    $literalPattern = '"(?<value>(?:\\.|[^"\\])*)"'
    $values = [System.Collections.Generic.List[string]]::new()
    foreach ($literal in [regex]::Matches($Text, $literalPattern)) {
        $values.Add($literal.Groups['value'].Value)
    }
    $concatenationPattern = '(?s)"(?:\\.|[^"\\])*"(?:\s*\+\s*"(?:\\.|[^"\\])*")+'
    foreach ($expression in [regex]::Matches($Text, $concatenationPattern)) {
        $combined = ([regex]::Matches($expression.Value, $literalPattern) |
            ForEach-Object { $_.Groups['value'].Value }) -join ''
        $values.Add($combined)
    }
    return @($values | Select-Object -Unique)
}

function Find-StringLiteralFingerprintMatches {
    param(
        [Parameter(Mandatory)] [object[]]$Records,
        [Parameter(Mandatory)] [string]$Pattern
    )

    $matcher = [regex]::new($Pattern, [System.Text.RegularExpressions.RegexOptions]::CultureInvariant)
    return @(
        $Records |
            Where-Object {
                foreach ($value in Get-StringLiteralValues -Text $_.text) {
                    $fingerprint = ($value -replace '[^A-Za-z0-9]', '').ToLowerInvariant()
                    if ($matcher.IsMatch($fingerprint)) {
                        return $true
                    }
                }
                return $false
            } |
            ForEach-Object { $_.path }
    )
}

function Find-ConcreteMerchantDomainMatches {
    param([Parameter(Mandatory)] [object[]]$Records)

    return @(
        @(
            Find-StringLiteralFingerprintMatches $Records 'gurbakircom'
            Find-RecordMatches $Records '(?i)gurbakir\.com'
        ) | Select-Object -Unique
    )
}

function Find-ConcreteProtectedIdentityMatches {
    param([Parameter(Mandatory)] [object[]]$Records)

    return @(
        @(
            Find-StringLiteralFingerprintMatches $Records 'gurbakir(?:secure(?:cart|customersession)|cart|customersession)'
            Find-RecordMatches $Records '(?i)(?:gurbakir_secure_(?:cart|customer_session)|gurbakir\.(?:cart|customer\.session)\.)'
        ) | Select-Object -Unique
    )
}

function Find-ConcreteMarketLiteralMatches {
    param([Parameter(Mandatory)] [object[]]$Records)

    $matcher = [regex]::new('(?<![A-Za-z0-9])(?:TR|TRY|tr-TR)(?![A-Za-z0-9])')
    return @(
        $Records |
            Where-Object {
                foreach ($value in Get-StringLiteralValues -Text $_.text) {
                    if ($matcher.IsMatch($value.Trim())) {
                        return $true
                    }
                }
                return $false
            } |
            ForEach-Object { $_.path }
    )
}

function Find-ConcreteCapabilityNavigationCompositionMatches {
    param([Parameter(Mandatory)] [object[]]$Records)

    return @(
        Find-RecordMatches $Records '(?i)\b(?:BuildConfigurationSource|gurbakirComposition|GurbakirApp|GurbakirBrand|GurbakirDeepLinkConfiguration|Gate2SyntheticApp|Gate2SyntheticBrand|Gate2SyntheticConfiguration)\b'
    )
}

function Test-RemovedProviderControlsAbsent {
    param([Parameter(Mandatory)] [string]$Text)

    return $Text -notmatch '(?im)\b(?:firebaseEnabled|TelemetryPolicy)\b|^\s*(?:firebase\.enabled|telemetry\.(?:analyticsEnabled|crashlyticsEnabled))\s*='
}

function Get-GraphQLBlockBody {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [int]$OpeningBraceIndex
    )

    if ($OpeningBraceIndex -lt 0 -or $OpeningBraceIndex -ge $Text.Length -or $Text[$OpeningBraceIndex] -ne '{') {
        return $null
    }
    $depth = 0
    $inComment = $false
    for ($index = $OpeningBraceIndex; $index -lt $Text.Length; $index++) {
        if ($inComment) {
            if ($Text[$index] -in @("`r", "`n")) {
                $inComment = $false
            }
            continue
        }
        if ($Text[$index] -eq '#') {
            $inComment = $true
        } elseif ($Text[$index] -eq '{') {
            $depth++
        } elseif ($Text[$index] -eq '}') {
            $depth--
            if ($depth -eq 0) {
                return $Text.Substring($OpeningBraceIndex + 1, $index - $OpeningBraceIndex - 1)
            }
        }
    }
    return $null
}

function Get-GraphQLFragmentBody {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [string]$TypeName
    )

    $header = [regex]::Match(
        $Text,
        "(?m)\bfragment\s+$([regex]::Escape($Name))\s+on\s+$([regex]::Escape($TypeName))\s*\{"
    )
    if (-not $header.Success) {
        return $null
    }
    $openingBraceIndex = $Text.IndexOf('{', $header.Index)
    return Get-GraphQLBlockBody -Text $Text -OpeningBraceIndex $openingBraceIndex
}

function Get-GraphQLFieldSelectionBody {
    param(
        [Parameter(Mandatory)] [string]$Text,
        [Parameter(Mandatory)] [string]$FieldName
    )

    $field = [regex]::Match($Text, "(?m)(?<![A-Za-z0-9_])$([regex]::Escape($FieldName))\s*\{")
    if (-not $field.Success) {
        return $null
    }
    $openingBraceIndex = $Text.IndexOf('{', $field.Index)
    return Get-GraphQLBlockBody -Text $Text -OpeningBraceIndex $openingBraceIndex
}

function Get-GraphQLTopLevelFields {
    param([Parameter(Mandatory)] [string]$SelectionText)

    $fields = [System.Collections.Generic.List[string]]::new()
    $depth = 0
    $index = 0
    while ($index -lt $SelectionText.Length) {
        $character = $SelectionText[$index]
        if ($character -eq '{') {
            $depth++
            $index++
            continue
        }
        if ($character -eq '}') {
            $depth--
            $index++
            continue
        }
        if ($depth -gt 0 -or [char]::IsWhiteSpace($character) -or $character -eq ',') {
            $index++
            continue
        }
        if ($character -eq '#') {
            while ($index -lt $SelectionText.Length -and $SelectionText[$index] -notin @("`r", "`n")) {
                $index++
            }
            continue
        }
        if ($index + 2 -lt $SelectionText.Length -and $SelectionText.Substring($index, 3) -eq '...') {
            $index += 3
            while ($index -lt $SelectionText.Length -and
                ([char]::IsLetterOrDigit($SelectionText[$index]) -or $SelectionText[$index] -eq '_')) {
                $index++
            }
            continue
        }
        if (-not ([char]::IsLetter($character) -or $character -eq '_')) {
            $index++
            continue
        }

        $nameStart = $index
        while ($index -lt $SelectionText.Length -and
            ([char]::IsLetterOrDigit($SelectionText[$index]) -or $SelectionText[$index] -eq '_')) {
            $index++
        }
        $fieldName = $SelectionText.Substring($nameStart, $index - $nameStart)
        while ($index -lt $SelectionText.Length -and [char]::IsWhiteSpace($SelectionText[$index])) {
            $index++
        }
        if ($index -lt $SelectionText.Length -and $SelectionText[$index] -eq ':') {
            $index++
            while ($index -lt $SelectionText.Length -and [char]::IsWhiteSpace($SelectionText[$index])) {
                $index++
            }
            $actualNameStart = $index
            while ($index -lt $SelectionText.Length -and
                ([char]::IsLetterOrDigit($SelectionText[$index]) -or $SelectionText[$index] -eq '_')) {
                $index++
            }
            if ($actualNameStart -eq $index) {
                return @()
            }
            $fieldName = $SelectionText.Substring($actualNameStart, $index - $actualNameStart)
        }
        $fields.Add($fieldName)
    }
    return @($fields)
}

function Test-ExactStringSet {
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [string[]]$Actual,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [string[]]$Expected
    )

    $difference = @(Compare-Object -ReferenceObject $Expected -DifferenceObject $Actual)
    return $difference.Count -eq 0 -and $Actual.Count -eq $Expected.Count
}

function Test-CatalogDiscoveryGraphQLContract {
    param(
        [Parameter(Mandatory)] [string]$QueryText,
        [Parameter(Mandatory)] [string]$ImageFragmentText
    )

    $itemFields = Get-GraphQLFragmentBody $QueryText 'CatalogDiscoveryItemFields' 'MenuItem'
    $level1 = Get-GraphQLFragmentBody $QueryText 'CatalogDiscoveryLevel1' 'MenuItem'
    $level2 = Get-GraphQLFragmentBody $QueryText 'CatalogDiscoveryLevel2' 'MenuItem'
    $level3 = Get-GraphQLFragmentBody $QueryText 'CatalogDiscoveryLevel3' 'MenuItem'
    $imageFields = Get-GraphQLFragmentBody $ImageFragmentText 'HomeImageFields' 'Image'
    if ($null -in @($itemFields, $level1, $level2, $level3, $imageFields)) {
        return $false
    }

    $menuItemFragmentHeaders = @(
        [regex]::Matches($QueryText, '(?m)\bfragment\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\s+on\s+MenuItem\s*\{')
    )
    foreach ($fragmentHeader in $menuItemFragmentHeaders) {
        $fragmentBody = Get-GraphQLFragmentBody $QueryText $fragmentHeader.Groups['name'].Value 'MenuItem'
        $fields = @(Get-GraphQLTopLevelFields $fragmentBody)
        if ('url' -in $fields -or 'resourceId' -in $fields) {
            return $false
        }
    }
    if ($QueryText -match '(?m)\.\.\.\s+on\s+MenuItem\b' -or $QueryText -match '\bitemsCount\b') {
        return $false
    }

    if (-not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $itemFields) @('id', 'title', 'type', 'tags', 'resource'))) {
        return $false
    }
    if (-not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $level1) @('items')) -or
        -not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $level2) @('items')) -or
        -not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $level3) @('items'))) {
        return $false
    }
    if ($level1 -notmatch '(?s)\.\.\.CatalogDiscoveryItemFields.*\bitems\s*\{\s*\.\.\.CatalogDiscoveryLevel2\s*\}' -or
        $level2 -notmatch '(?s)\.\.\.CatalogDiscoveryItemFields.*\bitems\s*\{\s*\.\.\.CatalogDiscoveryLevel3\s*\}' -or
        $level3 -notmatch '(?s)\.\.\.CatalogDiscoveryItemFields.*\bitems\s*\{\s*id\s*\}\s*$') {
        return $false
    }
    $sentinel = Get-GraphQLFieldSelectionBody $level3 'items'
    if ($null -eq $sentinel -or $sentinel -match '[{}]' -or
        -not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $sentinel) @('id'))) {
        return $false
    }
    if (@([regex]::Matches($QueryText, '\.\.\.HomeImageFields\b')).Count -ne 2 -or
        'url' -notin @(Get-GraphQLTopLevelFields $imageFields)) {
        return $false
    }
    return $QueryText -match '(?s)\bquery\s+CatalogDiscoveryMenu\b.*\bmenu\s*\(\s*handle\s*:\s*\$handle\s*\)'
}

function Test-CatalogDiscoveryOwnership {
    param(
        [Parameter(Mandatory)] [string]$CatalogConfigurationText,
        [Parameter(Mandatory)] [string]$AppCatalogConfigurationText,
        [Parameter(Mandatory)] [string]$AppResourceText,
        [Parameter(Mandatory)] [string]$MobileCatalogText,
        [Parameter(Mandatory)] [string]$SyntheticConfigurationText,
        [Parameter(Mandatory)] [string]$SyntheticManifestText,
        [Parameter(Mandatory)] [string]$QueryText,
        [Parameter(Mandatory)] [string]$ImageFragmentText,
        [Parameter(Mandatory)] [string]$HomeConfigurationText,
        [Parameter(Mandatory)] [string]$HomeResourceText
    )

    $catalogContract = $CatalogConfigurationText -match '(?s)\bdata\s+class\s+CatalogConfiguration\s*\(\s*val\s+menuHandle\s*:\s*String\s*\)' -and
        $CatalogConfigurationText -notmatch '\bCatalogCategorySource\b|\bcategories\s*[:=]'
    $appSelector = $AppCatalogConfigurationText -match '(?s)\bval\s+value\s*=\s*CatalogConfiguration\s*\(\s*menuHandle\s*=\s*BuildConfig\.CATALOG_MENU_HANDLE\b' -and
        $AppCatalogConfigurationText -notmatch '\bCatalogCategorySource\b|\bcategories\s*[:=]'
    $appLabelsRemoved = $AppResourceText -notmatch '\bcategory_label_[A-Za-z0-9_]*\b'
    $catalogRecords = @([pscustomobject]@{ path = 'mobile-core/catalog'; text = $MobileCatalogText })
    $sharedCatalogNeutral = $MobileCatalogText -notmatch '\bGurbakirCatalogConfiguration\b' -and
        @(Find-StringLiteralFingerprintMatches $catalogRecords '^gurbakir$').Count -eq 0
    $internetDeclarations = @(
        [regex]::Matches(
            $SyntheticManifestText,
            '(?is)<uses-permission\b(?=[^>]*android:name\s*=\s*"android\.permission\.INTERNET")[^>]*>'
        )
    )
    $activeInternetDeclarations = @(
        $internetDeclarations | Where-Object { $_.Value -notmatch '(?i)tools:node\s*=\s*"remove"' }
    )
    $syntheticInert = $SyntheticConfigurationText -match 'menuHandle\s*=\s*"synthetic-catalog-menu"' -and
        $SyntheticConfigurationText -match 'remoteSource\s*=\s*HomeRemoteSource\.Disabled' -and
        $activeInternetDeclarations.Count -eq 0
    $expectedHomePairs = @(
        'bardaklar|home_collection_drinkware',
        'cezveler|home_collection_coffee_pots',
        'tavalar-sahanlar|home_collection_pans',
        'tencereler|home_collection_pots',
        'ozel-urunlerimiz|home_collection_special'
    )
    $actualHomePairs = @(
        [regex]::Matches(
            $HomeConfigurationText,
            '(?s)\bHomeCollectionSource\s*\(\s*"[^"]+"\s*,\s*"(?<handle>[^"]+)"\s*,\s*R\.string\.(?<label>[A-Za-z0-9_]+)\s*\)'
        ) | ForEach-Object { "$($_.Groups['handle'].Value)|$($_.Groups['label'].Value)" }
    )
    $homePairDifferences = @(
        for ($index = 0; $index -lt [Math]::Min($actualHomePairs.Count, $expectedHomePairs.Count); $index++) {
            if ($actualHomePairs[$index] -cne $expectedHomePairs[$index]) {
                $index
            }
        }
    )
    $homePackagedFallbackPreserved = $actualHomePairs.Count -eq $expectedHomePairs.Count -and
        $homePairDifferences.Count -eq 0 -and
        @($expectedHomePairs | ForEach-Object { ($_ -split '\|', 2)[1] } | Where-Object {
            $HomeResourceText -notmatch [regex]::Escape($_)
        }).Count -eq 0
    $homeRemoteOwnership = $HomeConfigurationText -match 'BuildConfig\.HOME_CONTENT_ROOT_HANDLE' -and
        $HomeConfigurationText -match 'HomeRemoteSource\.ShopifyMetaobject' -and
        $HomeConfigurationText -match 'HomeDocumentSelector\("mobile_home",\s*handle\)' -and
        $HomeConfigurationText -match 'HomeRemoteSource\.Disabled' -and
        $HomeConfigurationText -match 'HomePackagedFallback'

    return $catalogContract -and
        $appSelector -and
        $appLabelsRemoved -and
        $sharedCatalogNeutral -and
        $syntheticInert -and
        $homePackagedFallbackPreserved -and
        $homeRemoteOwnership -and
        (Test-CatalogDiscoveryGraphQLContract $QueryText $ImageFragmentText)
}

function Test-HomeContentGraphQLContract {
    param(
        [Parameter(Mandatory)] [string]$RootQueryText,
        [Parameter(Mandatory)] [string]$ResourcesQueryText,
        [Parameter(Mandatory)] [string]$ImageFragmentText
    )

    $imageBody = Get-GraphQLFragmentBody $ImageFragmentText 'HomeImageFields' 'Image'
    if ($null -eq $imageBody -or
        -not (Test-ExactStringSet @(Get-GraphQLTopLevelFields $imageBody) @('url', 'altText', 'width', 'height'))) {
        return $false
    }
    $queries = $RootQueryText + "`n" + $ResourcesQueryText
    if ($queries -match '(?i)\b(?:route|action|component|destination)[A-Za-z0-9_]*\s*:\s*url\b' -or
        $queries -match '(?i)\b(?:routeUrl|actionUrl|componentUrl|destinationUrl)\b') {
        return $false
    }
    if ($queries -match '(?m)^\s*(?:[A-Za-z_][A-Za-z0-9_]*\s*:\s*)?url\b') {
        return $false
    }
    return $RootQueryText -match '(?s)\bquery\s+HomeContentMetaobject\b.*\bmetaobject\s*\(\s*handle\s*:\s*\$handle\s*\)' -and
        $RootQueryText -match 'field\s*\(\s*key\s*:\s*"declared_section_count"\s*\)' -and
        $RootQueryText -match '(?s)sections:\s*field\s*\(\s*key\s*:\s*"sections"\s*\).*?\btype\b.*?\bvalue\b.*?references\s*\(\s*first\s*:\s*3\s*\)' -and
        $RootQueryText -match '(?s)collections:\s*field\s*\(\s*key\s*:\s*"collections"\s*\).*?\btype\b.*?\bvalue\b.*?references\s*\(\s*first\s*:\s*7\s*\)' -and
        @([regex]::Matches($RootQueryText, '\bhasNextPage\b')).Count -eq 2 -and
        $RootQueryText -match '(?s)product:\s*field\s*\(\s*key\s*:\s*"product"\s*\).*?\btype\b.*?\bvalue\b.*?\breference\b' -and
        $ResourcesQueryText -match '(?s)\bquery\s+HomeResources\s*\(\s*\$ids\s*:\s*\[ID!\]!\s*\).*\bnodes\s*\(\s*ids\s*:\s*\$ids\s*\)' -and
        @([regex]::Matches($queries, '\.\.\.HomeImageFields\b')).Count -ge 6
}

function Invoke-PortabilitySelfTest {
    $fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gate2-portability-$([guid]::NewGuid().ToString('N'))"
    $results = [System.Collections.Generic.List[object]]::new()
    function Add-SelfTestResult {
        param([string]$Name, [bool]$Passed)
        $results.Add([pscustomobject]@{ test = $Name; passed = $Passed })
    }
    function Write-Fixture {
        param([string]$RelativePath, [string]$Text)
        $path = Join-Path $fixtureRoot $RelativePath
        $directory = Split-Path -Parent $path
        [void](New-Item -ItemType Directory -Path $directory -Force)
        [System.IO.File]::WriteAllText($path, $Text, [System.Text.UTF8Encoding]::new($false))
    }
    function Assert-FixtureMutation {
        param([string]$Name, [bool]$Occurred)

        if (-not $Occurred) {
            throw "Repository-portability self-test mutation did not occur: $Name"
        }
    }

    try {
        [void](New-Item -ItemType Directory -Path $fixtureRoot)
        $inventory = @(Get-ModuleInventory)
        $validSettings = @'
include(":app", ":synthetic", ":mobile-core", ":foundation", ":storefront", ":account", ":checkout", ":firebase")
project(":synthetic").projectDir = file("apps/synthetic")
'@
        Add-SelfTestResult 'valid flat topology and mapping pass' (Test-ApprovedTopology $validSettings $inventory).Passed
        $hierarchicalSettings = $validSettings.Replace('":synthetic"', '":apps:synthetic"')
        Add-SelfTestResult 'hierarchical include creates and rejects implicit :apps' (-not (Test-ApprovedTopology $hierarchicalSettings $inventory).Passed)
        $computedIncludeSettings = $validSettings + "`nval hidden = `":unexpected`"`ninclude(hidden)"
        Add-SelfTestResult 'computed include expression is rejected' (-not (Test-ApprovedTopology $computedIncludeSettings $inventory).Passed)
        Add-SelfTestResult 'missing physical mapping is rejected' (-not (Test-ApprovedTopology ($validSettings -replace '(?m)^project.*$', '') $inventory).Passed)
        $unexpectedSettings = $validSettings.Replace('":firebase"', '":firebase", ":unexpected"')
        Add-SelfTestResult 'unexpected included project is rejected' (-not (Test-ApprovedTopology $unexpectedSettings $inventory).Passed)

        Add-SelfTestResult 'type-safe accessor dependency is recognized' (Test-GradleProjectDependency 'implementation(projects.app)' ':app')
        Add-SelfTestResult 'positional project dependency is recognized' (Test-GradleProjectDependency 'implementation(project(":app"))' ':app')
        Add-SelfTestResult 'named project dependency is recognized' (Test-GradleProjectDependency 'implementation(project(path = '':app''))' ':app')
        Add-SelfTestResult 'map project dependency is recognized' (Test-GradleProjectDependency 'implementation(project(mapOf("path" to ":app")))' ':app')
        Add-SelfTestResult 'unrelated project dependency remains accepted' (-not (Test-GradleProjectDependency 'implementation(projects.mobileCore)' ':app'))
        Add-SelfTestResult 'literal project dependency syntax remains auditable' (Test-GradleProjectDependencySyntax 'implementation(project(":app"))')
        Add-SelfTestResult 'computed project dependency syntax is rejected' (-not (Test-GradleProjectDependencySyntax 'val hidden = ":app"; implementation(project(hidden))'))
        Add-SelfTestResult 'interpolated project dependency syntax is rejected' (-not (Test-GradleProjectDependencySyntax 'val hidden = "app"; implementation(project(":$hidden"))'))

        Write-Fixture 'apps/synthetic/src/main/kotlin/Valid.kt' 'import com.gurbakir.mobile.MobileCoreApp'
        Write-Fixture 'apps/synthetic/src/main/java/Invalid.java' 'final class Invalid { String value = "GurbakirBrand"; }'
        Write-Fixture 'apps/synthetic/src/main/res/values/encoded.xml' '<?xml version="1.0" encoding="utf-8"?><resources><string name="bad">G&#252;r Bak&#305;r</string></resources>'
        Write-Fixture 'app/src/main/kotlin/Invalid.kt' 'val marker = "Gate2Synthetic"'
        Write-Fixture 'storefront/src/main/kotlin/Gate3Leaks.kt' @'
val merchantDomain = "gurba" + "kir.com"
val preferencesName = "gurbakir_secure_" + "cart_development"
val marketId = "T" + "R"
val bypass = StorefrontMediaPolicy()
'@
        Write-Fixture 'storefront/src/main/kotlin/RawStringLeaks.kt' @'
val merchantDomain = """prefix " gurbakir.com suffix"""
val preferencesName = """prefix " gurbakir_secure_cart_development suffix"""
'@
        Write-Fixture 'storefront/src/main/kotlin/MarketBoundaryLeak.kt' 'val marketDescription = "market: TR"'
        Write-Fixture 'storefront/src/main/kotlin/GurbakirCompositionLeak.kt' 'val composition = gurbakirComposition(configuration)'
        Write-Fixture 'storefront/src/main/kotlin/GurbakirBrandLeak.kt' 'val brand = GurbakirBrand'
        Write-Fixture 'storefront/src/main/java/SyntheticNavigationLeak.java' 'final class SyntheticNavigationLeak { Object links = Gate2SyntheticConfiguration.deepLinks; }'
        Write-Fixture 'storefront/src/main/java/SyntheticBrandLeak.java' 'final class SyntheticBrandLeak { Object brand = Gate2SyntheticBrand.INSTANCE; }'
        Write-Fixture 'storefront/src/main/kotlin/NeutralComposition.kt' 'fun render(composition: ApplicationComposition, deepLinks: MobileDeepLinkConfiguration) = Unit'
        Write-Fixture 'storefront/src/main/res/values/plain.xml' '<?xml version="1.0" encoding="utf-8"?><resources><string name="bad_domain">gurbakir.com</string></resources>'
        Write-Fixture 'storefront/src/main/res/values-fr/encoded.xml' '<?xml version="1.0" encoding="utf-8"?><resources><string name="bad_identity">gurbakir&#95;secure&#95;cart&#95;development</string></resources>'
        $syntheticRecords = @(Get-ProductionSourceRecords $fixtureRoot 'apps/synthetic/src/main')
        $appRecords = @(Get-ProductionSourceRecords $fixtureRoot 'app/src/main')
        $decoded = @(Get-DecodedResourceRecords $fixtureRoot 'apps/synthetic/src/main/res')
        $sharedRecords = @(Get-ProductionSourceRecords $fixtureRoot 'storefront/src/main')
        $sharedDecoded = @(Get-DecodedResourceRecords $fixtureRoot 'storefront/src/main/res')
        $computedLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*Gate3Leaks.kt' })
        $rawStringLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*RawStringLeaks.kt' })
        $marketBoundaryLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*MarketBoundaryLeak.kt' })
        $gurbakirCompositionLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*GurbakirCompositionLeak.kt' })
        $gurbakirBrandLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*GurbakirBrandLeak.kt' })
        $syntheticNavigationLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*SyntheticNavigationLeak.java' })
        $syntheticBrandLeakRecords = @($sharedRecords | Where-Object { $_.path -like '*SyntheticBrandLeak.java' })
        $neutralCompositionRecords = @($sharedRecords | Where-Object { $_.path -like '*NeutralComposition.kt' })
        Assert-FixtureMutation 'concrete Gürbakır composition reference added' (
            $gurbakirCompositionLeakRecords.Count -eq 1 -and
            $gurbakirCompositionLeakRecords[0].text -match '\bgurbakirComposition\b'
        )
        Assert-FixtureMutation 'concrete synthetic navigation reference added' (
            $syntheticNavigationLeakRecords.Count -eq 1 -and
            $syntheticNavigationLeakRecords[0].text -match '\bGate2SyntheticConfiguration\b'
        )
        Assert-FixtureMutation 'concrete Gürbakır brand provider reference added' (
            $gurbakirBrandLeakRecords.Count -eq 1 -and
            $gurbakirBrandLeakRecords[0].text -match '\bGurbakirBrand\b'
        )
        Assert-FixtureMutation 'concrete synthetic brand provider reference added' (
            $syntheticBrandLeakRecords.Count -eq 1 -and
            $syntheticBrandLeakRecords[0].text -match '\bGate2SyntheticBrand\b'
        )
        Add-SelfTestResult 'legitimate shared package import is retained in the fixture' (@(Find-RecordMatches $syntheticRecords '(?m)^\s*import\s+com\.gurbakir\.mobile\.MobileCoreApp\s*$').Count -eq 1)
        Add-SelfTestResult 'Kotlin or Java concrete Gürbakır symbol is detected' (@(Find-RecordMatches $syntheticRecords '\bGurbakirBrand\b').Count -eq 1)
        Add-SelfTestResult 'app-side synthetic contamination is detected' (@(Find-RecordMatches $appRecords '\bGate2Synthetic\b').Count -eq 1)
        Add-SelfTestResult 'entity-encoded XML is decoded before contamination matching' (@(Find-RecordMatches $decoded 'Gür\s*Bakır' 'value').Count -eq 1)

        $firebaseBuild = 'implementation(projects.firebase); id("com.google.gms.google-services")'
        Add-SelfTestResult 'synthetic Firebase dependency is detected' (Test-GradleProjectDependency $firebaseBuild ':firebase')
        Add-SelfTestResult 'synthetic Google Services plugin is detected' ($firebaseBuild -match 'com\.google\.gms\.google-services')
        $neutralProviderFixture = 'val configuration = AppConfiguration(brand, environment)'
        foreach ($providerMutation in @(
            [pscustomobject]@{ Name = 'generic firebaseEnabled state'; Text = "$neutralProviderFixture`nval firebaseEnabled = false" }
            [pscustomobject]@{ Name = 'generic TelemetryPolicy state'; Text = "$neutralProviderFixture`nval telemetry = TelemetryPolicy(false, false)" }
            [pscustomobject]@{ Name = 'tracked firebase.enabled property'; Text = "$neutralProviderFixture`nfirebase.enabled=false" }
            [pscustomobject]@{ Name = 'tracked telemetry property'; Text = "$neutralProviderFixture`ntelemetry.analyticsEnabled=false" }
        )) {
            Assert-FixtureMutation $providerMutation.Name ($providerMutation.Text -ne $neutralProviderFixture)
            Add-SelfTestResult "$($providerMutation.Name) is rejected" (-not (Test-RemovedProviderControlsAbsent $providerMutation.Text))
        }
        Add-SelfTestResult 'provider-neutral configuration fixture passes' (Test-RemovedProviderControlsAbsent $neutralProviderFixture)

        $validCatalogOwnership = @{
            CatalogConfigurationText = 'data class CatalogConfiguration(val menuHandle: String)'
            AppCatalogConfigurationText = 'val value = CatalogConfiguration(menuHandle = BuildConfig.CATALOG_MENU_HANDLE)'
            AppResourceText = '<resources><string name="categories_empty">There are no categories to show right now.</string></resources>'
            MobileCatalogText = 'fun project(menu: CatalogDiscoveryMenu) = menu.items'
            SyntheticConfigurationText = 'CatalogConfiguration(menuHandle = "synthetic-catalog-menu"); HomeConfiguration(remoteSource = HomeRemoteSource.Disabled)'
            SyntheticManifestText = '<manifest package="com.example.gate2synthetic"><application /></manifest>'
            QueryText = @'
query CatalogDiscoveryMenu($handle: String!) {
  menu(handle: $handle) { id handle items { ...CatalogDiscoveryLevel1 } }
}
fragment CatalogDiscoveryItemFields on MenuItem {
  id
  title
  type
  tags
  resource { __typename ... on Collection { id handle title image { ...HomeImageFields } products(first: 1) { nodes { featuredImage { ...HomeImageFields } } } } }
}
fragment CatalogDiscoveryLevel1 on MenuItem { ...CatalogDiscoveryItemFields items { ...CatalogDiscoveryLevel2 } }
fragment CatalogDiscoveryLevel2 on MenuItem { ...CatalogDiscoveryItemFields items { ...CatalogDiscoveryLevel3 } }
fragment CatalogDiscoveryLevel3 on MenuItem { ...CatalogDiscoveryItemFields items { id } }
'@
            ImageFragmentText = 'fragment HomeImageFields on Image { url altText width height }'
            HomeConfigurationText = @'
val source = BuildConfig.HOME_CONTENT_ROOT_HANDLE.takeIf(String::isNotBlank)?.let { handle ->
  HomeRemoteSource.ShopifyMetaobject(HomeDocumentSelector("mobile_home", handle))
} ?: HomeRemoteSource.Disabled
val fallback = HomePackagedFallback(
HomeCollectionSource("HOME_RANGE_DRINKWARE", "bardaklar", R.string.home_collection_drinkware)
HomeCollectionSource("HOME_RANGE_COFFEE_POTS", "cezveler", R.string.home_collection_coffee_pots)
HomeCollectionSource("HOME_RANGE_PANS", "tavalar-sahanlar", R.string.home_collection_pans)
HomeCollectionSource("HOME_RANGE_POTS", "tencereler", R.string.home_collection_pots)
HomeCollectionSource("HOME_RANGE_SPECIAL", "ozel-urunlerimiz", R.string.home_collection_special)
)
'@
            HomeResourceText = 'home_collection_drinkware home_collection_coffee_pots home_collection_pans home_collection_pots home_collection_special'
        }
        Add-SelfTestResult 'Catalog discovery ownership contract accepts the approved fixture' (
            Test-CatalogDiscoveryOwnership @validCatalogOwnership
        )
        foreach ($catalogMutation in @(
            [pscustomobject]@{
                Name = 'reintroduced CatalogCategorySource is rejected'
                Field = 'CatalogConfigurationText'
                Text = $validCatalogOwnership.CatalogConfigurationText + "`ndata class CatalogCategorySource(val handle: String)"
            }
            [pscustomobject]@{
                Name = 'reintroduced compiled Catalog list is rejected'
                Field = 'CatalogConfigurationText'
                Text = 'data class CatalogConfiguration(val categories: List<String> = listOf("bardaklar"))'
            }
            [pscustomobject]@{
                Name = 'app Catalog selector bypass is rejected'
                Field = 'AppCatalogConfigurationText'
                Text = 'CatalogConfiguration(menuHandle = "main-menu")'
            }
            [pscustomobject]@{
                Name = 'app Catalog selector mixed decoy bypass is rejected'
                Field = 'AppCatalogConfigurationText'
                Text = "val unrelated = BuildConfig.CATALOG_MENU_HANDLE`nCatalogConfiguration(menuHandle = `"main-menu`")"
            }
            [pscustomobject]@{
                Name = 'app Catalog value selector decoy bypass is rejected'
                Field = 'AppCatalogConfigurationText'
                Text = "val value = CatalogConfiguration(menuHandle = `"main-menu`")`nval decoy = CatalogConfiguration(menuHandle = BuildConfig.CATALOG_MENU_HANDLE)"
            }
            [pscustomobject]@{
                Name = 'Android-owned Catalog label is rejected'
                Field = 'AppResourceText'
                Text = $validCatalogOwnership.AppResourceText + '<string name="category_label_pots">Pots</string>'
            }
            [pscustomobject]@{
                Name = 'concrete brand branch in shared Catalog is rejected'
                Field = 'MobileCatalogText'
                Text = $validCatalogOwnership.MobileCatalogText + "`nif (brand == `"gurbakir`") emptyList() else menu.items"
            }
            [pscustomobject]@{
                Name = 'direct MenuItem url field is rejected'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace('  tags', "  tags`n  url")
            }
            [pscustomobject]@{
                Name = 'aliased MenuItem url field is rejected'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace('  tags', "  tags`n  destination: url")
            }
            [pscustomobject]@{
                Name = 'MenuItem url in a fragment is rejected'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace('  tags', "  tags`n  ...RemoteRoute") + "`nfragment RemoteRoute on MenuItem { route: url }"
            }
            [pscustomobject]@{
                Name = 'GraphQL comment brace cannot hide MenuItem url'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace(
                    '  resource { __typename ... on Collection { id handle title image { ...HomeImageFields } products(first: 1) { nodes { featuredImage { ...HomeImageFields } } } } }',
                    "  resource { __typename ... on Collection { id handle title image { ...HomeImageFields } products(first: 1) { nodes { featuredImage { ...HomeImageFields } } } } }`n  # } valid GraphQL comment`n  destination: url"
                )
            }
            [pscustomobject]@{
                Name = 'missing depth sentinel is rejected'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace('fragment CatalogDiscoveryLevel3 on MenuItem { ...CatalogDiscoveryItemFields items { id } }', 'fragment CatalogDiscoveryLevel3 on MenuItem { ...CatalogDiscoveryItemFields }')
            }
            [pscustomobject]@{
                Name = 'missing MenuItem tags is rejected'
                Field = 'QueryText'
                Text = $validCatalogOwnership.QueryText.Replace('  tags', '')
            }
            [pscustomobject]@{
                Name = 'missing Image url is rejected'
                Field = 'ImageFragmentText'
                Text = 'fragment HomeImageFields on Image { altText width height }'
            }
            [pscustomobject]@{
                Name = 'changed Home handles are rejected'
                Field = 'HomeConfigurationText'
                Text = $validCatalogOwnership.HomeConfigurationText.Replace('"bardaklar"', '"different-home"')
            }
            [pscustomobject]@{
                Name = 'appended Home source is rejected'
                Field = 'HomeConfigurationText'
                Text = $validCatalogOwnership.HomeConfigurationText + "`nHomeCollectionSource(`"HOME_RANGE_EXTRA`", `"extra`", R.string.home_collection_special)"
            }
            [pscustomobject]@{
                Name = 'reordered Home sources are rejected'
                Field = 'HomeConfigurationText'
                Text = $validCatalogOwnership.HomeConfigurationText.Replace(
                    'HomeCollectionSource("HOME_RANGE_DRINKWARE", "bardaklar", R.string.home_collection_drinkware)',
                    'HomeCollectionSource("HOME_RANGE_TEMP", "cezveler", R.string.home_collection_coffee_pots)'
                ).Replace(
                    'HomeCollectionSource("HOME_RANGE_COFFEE_POTS", "cezveler", R.string.home_collection_coffee_pots)',
                    'HomeCollectionSource("HOME_RANGE_COFFEE_POTS", "bardaklar", R.string.home_collection_drinkware)'
                ).Replace(
                    'HomeCollectionSource("HOME_RANGE_TEMP", "cezveler", R.string.home_collection_coffee_pots)',
                    'HomeCollectionSource("HOME_RANGE_DRINKWARE", "cezveler", R.string.home_collection_coffee_pots)'
                )
            }
            [pscustomobject]@{
                Name = 'remapped Home labels are rejected'
                Field = 'HomeConfigurationText'
                Text = $validCatalogOwnership.HomeConfigurationText.Replace(
                    'R.string.home_collection_drinkware',
                    'R.string.home_collection_temp'
                ).Replace(
                    'R.string.home_collection_coffee_pots',
                    'R.string.home_collection_drinkware'
                ).Replace(
                    'R.string.home_collection_temp',
                    'R.string.home_collection_coffee_pots'
                )
            }
            [pscustomobject]@{
                Name = 'blank synthetic selector is rejected'
                Field = 'SyntheticConfigurationText'
                Text = 'CatalogConfiguration(menuHandle = "")'
            }
            [pscustomobject]@{
                Name = 'synthetic INTERNET permission is rejected'
                Field = 'SyntheticManifestText'
                Text = '<manifest><uses-permission android:name="android.permission.INTERNET" /><application /></manifest>'
            }
        )) {
            $mutatedCatalogOwnership = $validCatalogOwnership.Clone()
            $mutatedCatalogOwnership[$catalogMutation.Field] = $catalogMutation.Text
            Assert-FixtureMutation $catalogMutation.Name (
                $mutatedCatalogOwnership[$catalogMutation.Field] -ne $validCatalogOwnership[$catalogMutation.Field]
            )
            Add-SelfTestResult $catalogMutation.Name (-not (Test-CatalogDiscoveryOwnership @mutatedCatalogOwnership))
        }
        $validHomeRootQuery = @'
query HomeContentMetaobject($handle: MetaobjectHandleInput!) {
  metaobject(handle: $handle) {
    schemaVersion: field(key: "schema_version") { type value }
    declaredSectionCount: field(key: "declared_section_count") { type value }
    sections: field(key: "sections") {
      type value references(first: 3) {
        nodes { ... on Metaobject {
          collections: field(key: "collections") { type value references(first: 7) { nodes { ... on Collection { image { ...HomeImageFields } products(first: 1) { nodes { featuredImage { ...HomeImageFields } } } } } pageInfo { hasNextPage } } }
          product: field(key: "product") { type value reference { ... on Product { featuredImage { ...HomeImageFields } } } }
        } }
        pageInfo { hasNextPage }
      }
    }
  }
}
'@
        $validHomeResourcesQuery = @'
query HomeResources($ids: [ID!]!) {
  nodes(ids: $ids) {
    ... on Collection { image { ...HomeImageFields } products(first: 1) { nodes { featuredImage { ...HomeImageFields } } } }
    ... on Product { featuredImage { ...HomeImageFields } }
  }
}
'@
        $validHomeImageFragment = 'fragment HomeImageFields on Image { url altText width height }'
        Add-SelfTestResult 'bounded Home GraphQL contract accepts required Image url only' (
            Test-HomeContentGraphQLContract $validHomeRootQuery $validHomeResourcesQuery $validHomeImageFragment
        )
        Add-SelfTestResult 'aliased Home navigation url is rejected' (-not (
            Test-HomeContentGraphQLContract ($validHomeRootQuery + "`ndestination: url") $validHomeResourcesQuery $validHomeImageFragment
        ))
        Add-SelfTestResult 'missing stored section value is rejected' (-not (
            Test-HomeContentGraphQLContract ($validHomeRootQuery.Replace('type value references(first: 3)', 'type references(first: 3)')) $validHomeResourcesQuery $validHomeImageFragment
        ))
        Add-SelfTestResult 'missing required Image url is rejected for Home' (-not (
            Test-HomeContentGraphQLContract $validHomeRootQuery $validHomeResourcesQuery 'fragment HomeImageFields on Image { altText width height }'
        ))
        Add-SelfTestResult 'computed merchant domain literal is detected' (@(Find-StringLiteralFingerprintMatches $computedLeakRecords '^gurbakircom$').Count -eq 1)
        Add-SelfTestResult 'computed protected identity literal is detected' (@(Find-StringLiteralFingerprintMatches $computedLeakRecords 'gurbakirsecure(?:cart|customersession)').Count -eq 1)
        Add-SelfTestResult 'computed concrete market literal is detected' (@(Find-ConcreteMarketLiteralMatches $computedLeakRecords).Count -eq 1)
        Add-SelfTestResult 'raw-string merchant domain literal is detected' (@(Find-ConcreteMerchantDomainMatches $rawStringLeakRecords).Count -eq 1)
        Add-SelfTestResult 'raw-string protected identity literal is detected' (@(Find-ConcreteProtectedIdentityMatches $rawStringLeakRecords).Count -eq 1)
        Add-SelfTestResult 'space-delimited concrete market literal is detected' (@(Find-ConcreteMarketLiteralMatches $marketBoundaryLeakRecords).Count -eq 1)
        Add-SelfTestResult 'media policy empty construction bypass is detected' (@(Find-RecordMatches $sharedRecords '\bStorefrontMediaPolicy\s*\(\s*\)').Count -eq 1)
        $compositionMatcherPresent = $null -ne (
            Get-Command Find-ConcreteCapabilityNavigationCompositionMatches -ErrorAction SilentlyContinue
        )
        Add-SelfTestResult 'concrete Gürbakır capability composition reference is detected' (
            $compositionMatcherPresent -and
            @(Find-ConcreteCapabilityNavigationCompositionMatches $gurbakirCompositionLeakRecords).Count -eq 1
        )
        Add-SelfTestResult 'concrete synthetic navigation composition reference is detected' (
            $compositionMatcherPresent -and
            @(Find-ConcreteCapabilityNavigationCompositionMatches $syntheticNavigationLeakRecords).Count -eq 1
        )
        Add-SelfTestResult 'concrete Gürbakır brand provider reference is detected' (
            $compositionMatcherPresent -and
            @(Find-ConcreteCapabilityNavigationCompositionMatches $gurbakirBrandLeakRecords).Count -eq 1
        )
        Add-SelfTestResult 'concrete synthetic brand provider reference is detected' (
            $compositionMatcherPresent -and
            @(Find-ConcreteCapabilityNavigationCompositionMatches $syntheticBrandLeakRecords).Count -eq 1
        )
        Add-SelfTestResult 'neutral shared capability and deep-link contracts remain accepted' (
            $compositionMatcherPresent -and
            @(Find-ConcreteCapabilityNavigationCompositionMatches $neutralCompositionRecords).Count -eq 0
        )
        Add-SelfTestResult 'plain shared resource merchant domain is detected after XML decoding' (@(Find-RecordMatches $sharedDecoded '(?i)gurbakir\.com' 'value').Count -eq 1)
        Add-SelfTestResult 'entity-encoded qualified resource protected identity is detected after XML decoding' (@(Find-RecordMatches $sharedDecoded '(?i)gurbakir_secure_cart' 'value').Count -eq 1)

        $results | Format-Table -AutoSize | Out-Host
        $failed = @($results | Where-Object { -not $_.passed })
        if ($failed.Count -gt 0) {
            throw "Repository portability self-tests failed: $($failed.test -join '; ')"
        }
        Write-Host "Repository portability self-tests PASS ($($results.Count) fixtures)."
    }
    finally {
        $resolvedFixtureRoot = [System.IO.Path]::GetFullPath($fixtureRoot)
        $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if ($resolvedFixtureRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $resolvedFixtureRoot) -like 'gate2-portability-*' -and
            (Test-Path -LiteralPath $resolvedFixtureRoot -PathType Container)) {
            Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
        }
    }
}

if ($SelfTest) {
    Invoke-PortabilitySelfTest
    exit 0
}

Push-Location $repoRoot
try {
    $modules = @(Get-ModuleInventory)
    $requiredFiles = @(
        'settings.gradle.kts',
        'build.gradle.kts',
        'gradle.properties',
        'gradlew',
        'gradlew.bat',
        'gradle/libs.versions.toml',
        'gradle/verification-metadata.xml',
        'gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties',
        'settings-gradle.lockfile',
        '.github/workflows/android-foundation.yml',
        '.gitignore',
        '.gitleaks.toml',
        '.gitleaksignore',
        'config/local.defaults.properties',
        'config/local.properties.example',
        'app/src/main/AndroidManifest.xml',
        'apps/synthetic/src/main/AndroidManifest.xml',
        'mobile-core/consumer-rules.pro',
        'mobile-core/src/main/AndroidManifest.xml',
        'storefront/src/main/graphql/com/gurbakir/storefront/schema.graphqls',
        'storefront/src/main/graphql/com/gurbakir/storefront/CatalogDiscoveryMenu.graphql',
        'storefront/src/main/graphql/com/gurbakir/storefront/HomeImageFields.graphql',
        'account/src/main/graphql/com/gurbakir/account/schema.graphqls',
        'docs/reference-model/COMMERCE-BEHAVIOR.md',
        'docs/reference-model/SYSTEM-BOUNDARIES-AND-LIMITATIONS.md',
        'scripts/Test-Gate2SyntheticPackage.ps1'
    )
    $requiredFiles += @($modules | ForEach-Object { "$($_.Directory)/build.gradle.kts" })
    $requiredFiles += @($modules | ForEach-Object { "$($_.Directory)/gradle.lockfile" })

    $requiredDirectories = @(
        'mobile-core/schemas/'
    )

    $missingRequiredFiles = @($requiredFiles | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Leaf)
    })
    $missingRequiredDirectories = @($requiredDirectories | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot $_) -PathType Container)
    })
    $requiredEvidence = if ($missingRequiredFiles.Count -eq 0 -and $missingRequiredDirectories.Count -eq 0) {
        "all $($requiredFiles.Count + $requiredDirectories.Count) required files and directories present"
    } else {
        "missing files: $($missingRequiredFiles -join ', '); missing directories: $($missingRequiredDirectories -join ', ')"
    }
    Add-Check -Name 'fresh-clone required files and directories are present' -Passed ($missingRequiredFiles.Count -eq 0 -and $missingRequiredDirectories.Count -eq 0) -Evidence $requiredEvidence

    $settings = Read-Text 'settings.gradle.kts'
    $topology = if ($null -eq $settings) {
        [pscustomobject]@{ Passed = $false; Evidence = 'settings.gradle.kts missing' }
    } else {
        Test-ApprovedTopology -SettingsText $settings -Inventory $modules
    }
    Add-Check -Name 'Gradle topology is the approved flat eight-subproject set' -Passed $topology.Passed -Evidence $topology.Evidence

    $gradleWrapper = Join-Path $repoRoot $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    $gradleProjectOutput = & $gradleWrapper projects --console=plain 2>&1 | Out-String
    $gradleProjectExit = $LASTEXITCODE
    $evaluatedProjects = @(
        [regex]::Matches($gradleProjectOutput, "(?m)^[+\\]--- Project '(?<project>:[^']+)'\s*$") |
            ForEach-Object { $_.Groups['project'].Value } |
            Select-Object -Unique
    )
    $expectedEvaluatedProjects = @($modules | ForEach-Object { $_.LogicalPath })
    $evaluatedProjectDiff = @(
        $expectedEvaluatedProjects | Where-Object { $_ -notin $evaluatedProjects }
        $evaluatedProjects | Where-Object { $_ -notin $expectedEvaluatedProjects }
    )
    $evaluatedSyntheticMapping = $gradleProjectOutput -match "(?m)^project ':synthetic' - [\\/]apps[\\/]synthetic\s*$"
    $evaluatedTopologyCorrect = $gradleProjectExit -eq 0 -and
        $evaluatedProjects.Count -eq $expectedEvaluatedProjects.Count -and
        $evaluatedProjectDiff.Count -eq 0 -and
        $evaluatedSyntheticMapping
    Add-Check -Name 'Gradle-evaluated project model matches the approved topology and mapping' -Passed $evaluatedTopologyCorrect -Evidence "Gradle exit $gradleProjectExit; projects $($evaluatedProjects.Count); synthetic mapping $evaluatedSyntheticMapping"

    $wrapperProperties = Read-Text 'gradle/wrapper/gradle-wrapper.properties'
    $wrapperJarPath = Join-Path $repoRoot 'gradle/wrapper/gradle-wrapper.jar'
    $wrapperJarHash = if (Test-Path -LiteralPath $wrapperJarPath) {
        (Get-FileHash -LiteralPath $wrapperJarPath -Algorithm SHA256).Hash
    } else {
        ''
    }
    $wrapperPinned = $null -ne $wrapperProperties -and
        $wrapperProperties -match 'gradle-9\.4\.1-bin\.zip' -and
        $wrapperProperties -match 'distributionSha256Sum=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb' -and
        $wrapperJarHash -eq '55243EF57851F12B070AD14F7F5BB8302DACEEEBC5BCE5ECE5FA6EDB23E1145C'
    Add-Check -Name 'Gradle wrapper is complete and pinned' -Passed $wrapperPinned -Evidence "Gradle 9.4.1; wrapper jar SHA256 $wrapperJarHash"

    $defaults = Read-Text 'config/local.defaults.properties'
    $defaultsFailClosed = $null -ne $defaults -and
        $defaults -match '(?m)^shopify\.storefrontPublicToken=\s*$' -and
        $defaults -match '(?m)^shopify\.catalogMenuHandle=\s*$' -and
        (Test-RemovedProviderControlsAbsent $defaults)
    Add-Check -Name 'tracked local defaults fail closed' -Passed $defaultsFailClosed -Evidence 'Storefront public token and Catalog Menu selector empty; no provider or telemetry control'

    $providerControlRecords = @(
        foreach ($relativeRoot in @('foundation/src', 'app/src', 'apps/synthetic/src')) {
            Get-ProductionSourceRecords $repoRoot $relativeRoot
        }
        foreach ($relativePath in @('app/build.gradle.kts', 'config/local.defaults.properties', 'config/local.properties.example')) {
            $text = Read-Text $relativePath
            if ($null -ne $text) {
                [pscustomobject]@{ path = $relativePath; text = $text }
            }
        }
    )
    $removedProviderControlMatches = @(
        $providerControlRecords | Where-Object { -not (Test-RemovedProviderControlsAbsent $_.text) } | ForEach-Object { $_.path }
    )
    Add-Check -Name 'generic configuration and tracked properties contain no retired provider controls' -Passed ($removedProviderControlMatches.Count -eq 0) -Evidence $(if ($removedProviderControlMatches.Count -eq 0) { 'firebaseEnabled, TelemetryPolicy, firebase.enabled and telemetry toggles absent' } else { $removedProviderControlMatches -join ', ' })

    $gitignore = Read-Text '.gitignore'
    $ignoreRulesPresent = $null -ne $gitignore -and
        $gitignore -match '(?m)^local\.properties\s*$' -and
        $gitignore -match '(?m)^config/local\.properties\s*$' -and
        $gitignore -match '(?m)^\*\*/google-services\.json\s*$' -and
        $gitignore -match '(?m)^\*\.jks\s*$' -and
        $gitignore -match '(?m)^\*\.keystore\s*$' -and
        $gitignore -match '(?m)^key\.properties\s*$'
    Add-Check -Name 'local configuration and signing material stay outside Git' -Passed $ignoreRulesPresent -Evidence 'local.properties, Firebase JSON, keystores and key.properties are ignored'

    $trackedFiles = @(git ls-files)
    $forbiddenTracked = @($trackedFiles | Where-Object {
        $_ -match '(^|/)(local\.properties|google-services\.json|key\.properties)$' -or
        $_ -match '\.(jks|keystore|p12|pem|key)$'
    })
    $trackedEvidence = if ($forbiddenTracked.Count -eq 0) {
        'none'
    } else {
        $forbiddenTracked -join ', '
    }
    Add-Check -Name 'no forbidden local configuration or signing files are tracked' -Passed ($forbiddenTracked.Count -eq 0) -Evidence $trackedEvidence

    $debugProofFiles = @(
        'CommerceProofController.kt',
        'CommerceProofScreen.kt',
        'CommerceProofViewModel.kt',
        'CustomerAccountProofController.kt',
        'CustomerAccountProofViewModel.kt'
    )
    $debugProofMissing = @($debugProofFiles | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot "app/src/debug/kotlin/com/gurbakir/mobile/$_") -PathType Leaf)
    })
    $mainProofPresent = @($debugProofFiles | Where-Object {
        Test-Path -LiteralPath (Join-Path $repoRoot "app/src/main/kotlin/com/gurbakir/mobile/$_") -PathType Leaf
    })
    $proofIsolationCorrect = $debugProofMissing.Count -eq 0 -and $mainProofPresent.Count -eq 0
    $proofEvidence = if ($proofIsolationCorrect) {
        'all proof surfaces present under app/src/debug and absent from app/src/main'
    } else {
        "missing debug: $($debugProofMissing -join ', '); present in main: $($mainProofPresent -join ', ')"
    }
    Add-Check -Name 'proof-only UI and controllers remain debug-only' -Passed $proofIsolationCorrect -Evidence $proofEvidence

    $storefrontSchema = Join-Path $repoRoot 'storefront/src/main/graphql/com/gurbakir/storefront/schema.graphqls'
    $accountSchema = Join-Path $repoRoot 'account/src/main/graphql/com/gurbakir/account/schema.graphqls'
    $homeContentOperation = Join-Path $repoRoot 'storefront/src/main/graphql/com/gurbakir/storefront/HomeContentMetaobject.graphql'
    $homeResourcesOperation = Join-Path $repoRoot 'storefront/src/main/graphql/com/gurbakir/storefront/HomeResources.graphql'
    $graphqlInputsPresent =
        (Test-Path -LiteralPath $storefrontSchema -PathType Leaf) -and
        (Get-Item -LiteralPath $storefrontSchema).Length -gt 1000 -and
        (Test-Path -LiteralPath $accountSchema -PathType Leaf) -and
        (Get-Item -LiteralPath $accountSchema).Length -gt 1000 -and
        (Test-Path -LiteralPath $homeContentOperation -PathType Leaf) -and
        (Test-Path -LiteralPath $homeResourcesOperation -PathType Leaf)
    Add-Check -Name 'Apollo schema and Gate 7 operation inputs required by a clean build are tracked' -Passed $graphqlInputsPresent -Evidence 'Storefront and Customer Account schemas plus both bounded Home operations are tracked'

    $mobileCatalogRecords = @(Get-ProductionSourceRecords $repoRoot 'mobile-core/src/main/kotlin/com/gurbakir/mobile/catalog')
    $catalogOwnershipInputs = @{
        CatalogConfigurationText = [string](Read-Text 'mobile-core/src/main/kotlin/com/gurbakir/mobile/catalog/CatalogConfiguration.kt')
        AppCatalogConfigurationText = [string](Read-Text 'app/src/main/kotlin/com/gurbakir/mobile/catalog/GurbakirCatalogConfiguration.kt')
        AppResourceText = [string](Read-Text 'app/src/main/res/values/strings.xml') + "`n" + [string](Read-Text 'app/src/main/res/values-en/strings.xml')
        MobileCatalogText = ($mobileCatalogRecords | ForEach-Object { $_.text }) -join "`n"
        SyntheticConfigurationText = [string](Read-Text 'apps/synthetic/src/main/kotlin/com/example/gate2synthetic/config/Gate2SyntheticConfiguration.kt')
        SyntheticManifestText = [string](Read-Text 'apps/synthetic/src/main/AndroidManifest.xml')
        QueryText = [string](Read-Text 'storefront/src/main/graphql/com/gurbakir/storefront/CatalogDiscoveryMenu.graphql')
        ImageFragmentText = [string](Read-Text 'storefront/src/main/graphql/com/gurbakir/storefront/HomeImageFields.graphql')
        HomeConfigurationText = [string](Read-Text 'app/src/main/kotlin/com/gurbakir/mobile/home/GurbakirHomeConfiguration.kt')
        HomeResourceText = [string](Read-Text 'app/src/main/res/values/strings.xml') + "`n" + [string](Read-Text 'app/src/main/res/values-en/strings.xml')
    }
    $catalogOwnershipPassed = Test-CatalogDiscoveryOwnership @catalogOwnershipInputs
    Add-Check -Name 'Catalog discovery ownership is Menu-selected, bounded and route-safe' -Passed $catalogOwnershipPassed -Evidence $(if ($catalogOwnershipPassed) { 'BuildConfig selector, neutral bounded query/projection, MenuItem URL exclusion, Image URL, Home preservation and inert synthetic contract verified' } else { 'one or more Catalog discovery ownership invariants failed' })

    $homeGraphQLPassed = Test-HomeContentGraphQLContract `
        ([string](Read-Text 'storefront/src/main/graphql/com/gurbakir/storefront/HomeContentMetaobject.graphql')) `
        ([string](Read-Text 'storefront/src/main/graphql/com/gurbakir/storefront/HomeResources.graphql')) `
        ([string](Read-Text 'storefront/src/main/graphql/com/gurbakir/storefront/HomeImageFields.graphql'))
    Add-Check -Name 'Home content operations preserve bounded declaration and route-safe media authority' -Passed $homeGraphQLPassed -Evidence $(if ($homeGraphQLPassed) { 'stored values, overflow sentinels, exact resource batch shape and Image.url-only media fields verified' } else { 'one or more bounded Home GraphQL invariants failed' })

    $forbiddenReferenceArtifacts = @($trackedFiles | Where-Object {
        $_ -like ('docs/reference-' + 'apk/*') -or
        $_ -like ('.agents/skills/navigate-' + 'gurbakir-evidence/*') -or
        $_ -eq 'scripts/Test-Preparation.ps1' -or
        $_ -eq 'docs/preparation/APK-REFERENCE-GUIDE.md'
    })
    $behaviorReference = Read-Text 'docs/reference-model/COMMERCE-BEHAVIOR.md'
    $boundaryReference = Read-Text 'docs/reference-model/SYSTEM-BOUNDARIES-AND-LIMITATIONS.md'
    $referenceModelPresent = $null -ne $behaviorReference -and
        $null -ne $boundaryReference -and
        $behaviorReference -match '(?i)not a specification|not.*authority' -and
        $boundaryReference -match '(?i)subordinate|not.*authority'
    $referenceEvidence = if ($forbiddenReferenceArtifacts.Count -eq 0) {
        'two neutral non-authoritative reference-model documents present; forensic corpus and helper absent'
    } else {
        "forbidden tracked reference artifacts: $($forbiddenReferenceArtifacts -join ', ')"
    }
    Add-Check -Name 'neutral reference model replaces the private forensic corpus' -Passed ($referenceModelPresent -and $forbiddenReferenceArtifacts.Count -eq 0) -Evidence $referenceEvidence

    $appBuild = Read-Text 'app/build.gradle.kts'
    $productionStillExternal = $null -ne $appBuild -and $appBuild -notmatch 'create\("production"\)'
    Add-Check -Name 'production variant remains intentionally unprovisioned' -Passed $productionStillExternal -Evidence 'development/staging are portable; production inputs remain owner-controlled and external'

    $unsupportedProjectDependencyModules = [System.Collections.Generic.List[string]]::new()
    foreach ($module in $modules) {
        $moduleBuild = Read-Text "$($module.Directory)/build.gradle.kts"
        if ($null -ne $moduleBuild -and -not (Test-GradleProjectDependencySyntax -BuildScript $moduleBuild)) {
            $unsupportedProjectDependencyModules.Add($module.LogicalPath)
        }
    }
    Add-Check -Name 'project dependencies use auditable literal or type-safe syntax' -Passed ($unsupportedProjectDependencyModules.Count -eq 0) -Evidence $(if ($unsupportedProjectDependencyModules.Count -eq 0) { 'no computed project(...) arguments' } else { $unsupportedProjectDependencyModules -join ', ' })

    $sharedAndProviderModules = @($modules | Where-Object { $_.Role -in @('shared', 'provider') })
    $sharedApplicationDependencies = [System.Collections.Generic.List[string]]::new()
    foreach ($module in $sharedAndProviderModules) {
        $moduleBuild = Read-Text "$($module.Directory)/build.gradle.kts"
        if ($null -eq $moduleBuild) {
            continue
        }
        foreach ($applicationPath in @(':app', ':synthetic')) {
            if (Test-GradleProjectDependency -BuildScript $moduleBuild -TargetProject $applicationPath) {
                $sharedApplicationDependencies.Add("$($module.LogicalPath) -> $applicationPath")
            }
        }
    }
    $sharedApplicationDependencyEvidence = if ($sharedApplicationDependencies.Count -eq 0) {
        'none'
    } else {
        $sharedApplicationDependencies -join ', '
    }
    Add-Check -Name 'shared and provider modules do not depend on application modules' -Passed ($sharedApplicationDependencies.Count -eq 0) -Evidence $sharedApplicationDependencyEvidence

    $syntheticBuild = Read-Text 'apps/synthetic/build.gradle.kts'
    $crossApplicationDependencies = [System.Collections.Generic.List[string]]::new()
    if ($null -ne $appBuild -and (Test-GradleProjectDependency $appBuild ':synthetic')) {
        $crossApplicationDependencies.Add(':app -> :synthetic')
    }
    if ($null -ne $syntheticBuild -and (Test-GradleProjectDependency $syntheticBuild ':app')) {
        $crossApplicationDependencies.Add(':synthetic -> :app')
    }
    Add-Check -Name 'application modules do not depend on each other' -Passed ($crossApplicationDependencies.Count -eq 0) -Evidence $(if ($crossApplicationDependencies.Count -eq 0) { 'none' } else { $crossApplicationDependencies -join ', ' })

    $syntheticFirebaseDependency = $null -ne $syntheticBuild -and
        (Test-GradleProjectDependency $syntheticBuild ':firebase')
    Add-Check -Name 'synthetic application does not depend on Firebase' -Passed (-not $syntheticFirebaseDependency) -Evidence $(if ($syntheticFirebaseDependency) { 'Firebase project dependency found' } else { 'none' })

    $mobileCoreBuild = Read-Text 'mobile-core/build.gradle.kts'
    $mobileCoreFirebaseDependency = $null -ne $mobileCoreBuild -and (
        $mobileCoreBuild -match 'projects\.firebase\b' -or
        (Test-GradleProjectDependency -BuildScript $mobileCoreBuild -TargetProject ':firebase')
    )
    Add-Check -Name 'mobile-core does not depend on Firebase' -Passed (-not $mobileCoreFirebaseDependency) -Evidence $(if ($mobileCoreFirebaseDependency) { 'Firebase project dependency found' } else { 'none' })

    $sharedModules = @($modules | Where-Object { $_.Role -eq 'shared' })
    $sharedProductionFiles = @(
        foreach ($module in $sharedModules) {
            Get-ProductionSourceRecords $repoRoot "$($module.Directory)/src/main"
        }
    )
    $sharedResourceValues = @(
        foreach ($module in $sharedModules) {
            Get-DecodedResourceRecords $repoRoot "$($module.Directory)/src/main/res"
        }
    )
    $sharedMerchantDomainMatches = @(Find-ConcreteMerchantDomainMatches $sharedProductionFiles)
    Add-Check -Name 'shared production source contains no concrete merchant domain literal' -Passed ($sharedMerchantDomainMatches.Count -eq 0) -Evidence $(if ($sharedMerchantDomainMatches.Count -eq 0) { 'none' } else { $sharedMerchantDomainMatches -join ', ' })
    $sharedProtectedIdentityMatches = @(Find-ConcreteProtectedIdentityMatches $sharedProductionFiles)
    Add-Check -Name 'shared production source contains no concrete protected-store identity literal' -Passed ($sharedProtectedIdentityMatches.Count -eq 0) -Evidence $(if ($sharedProtectedIdentityMatches.Count -eq 0) { 'none' } else { $sharedProtectedIdentityMatches -join ', ' })
    $sharedMarketMatches = @(Find-ConcreteMarketLiteralMatches $sharedProductionFiles)
    Add-Check -Name 'shared production source contains no concrete TR market fallback literal' -Passed ($sharedMarketMatches.Count -eq 0) -Evidence $(if ($sharedMarketMatches.Count -eq 0) { 'none' } else { $sharedMarketMatches -join ', ' })
    $sharedMediaPolicyBypasses = @(
        Find-RecordMatches $sharedProductionFiles '(?s)\bobject\s+StorefrontMediaPolicy\b|\bStorefrontMediaPolicy\s*\(\s*\)|(?:\(|,)\s*[A-Za-z_][A-Za-z0-9_]*\s*:\s*StorefrontMediaPolicy\s*='
    )
    Add-Check -Name 'shared production source has no static or default media-policy bypass' -Passed ($sharedMediaPolicyBypasses.Count -eq 0) -Evidence $(if ($sharedMediaPolicyBypasses.Count -eq 0) { 'none' } else { $sharedMediaPolicyBypasses -join ', ' })
    $sharedCompositionContamination = @(Find-ConcreteCapabilityNavigationCompositionMatches $sharedProductionFiles)
    Add-Check -Name 'shared production source has no concrete application capability or navigation composition reference' -Passed ($sharedCompositionContamination.Count -eq 0) -Evidence $(if ($sharedCompositionContamination.Count -eq 0) { 'none; neutral composition contracts remain allowed' } else { $sharedCompositionContamination -join ', ' })
    $sharedResourceInputLeaks = @(
        $resourceMatcher = [regex]::new(
            '(?:gurbakir\.com|gurbakir_secure_(?:cart|customer_session)|gurbakir\.(?:cart|customer\.session)\.|(?<![A-Za-z0-9])(?:TR|TRY|tr-TR)(?![A-Za-z0-9]))',
            [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
        )
        $sharedResourceValues |
            Where-Object { $resourceMatcher.IsMatch($_.value) } |
            ForEach-Object { "$($_.path)@$($_.resource)" }
    )
    Add-Check -Name 'decoded shared resource values contain no merchant domain protected identity or concrete market fallback' -Passed ($sharedResourceInputLeaks.Count -eq 0) -Evidence $(if ($sharedResourceInputLeaks.Count -eq 0) { 'none' } else { $sharedResourceInputLeaks -join ', ' })

    $mobileCoreProductionRoot = Join-Path $repoRoot 'mobile-core/src/main'
    $mobileCoreProductionSourceExtensions = @('.kt', '.java', '.xml')
    $mobileCoreProductionFiles = if (Test-Path -LiteralPath $mobileCoreProductionRoot -PathType Container) {
        @(Get-ChildItem -LiteralPath $mobileCoreProductionRoot -Recurse -File |
            Where-Object { $_.Extension -in $mobileCoreProductionSourceExtensions } |
            ForEach-Object {
            [pscustomobject]@{
                path = $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
                text = Get-Content -LiteralPath $_.FullName -Raw
            }
        })
    } else {
        @()
    }

    function Find-MobileCoreProductionMatches {
        param([Parameter(Mandatory)] [string]$Pattern)

        return @($mobileCoreProductionFiles | Where-Object { $_.text -match $Pattern } | ForEach-Object { $_.path })
    }

    $mobileCoreSourceGuardPatterns = @(
        [pscustomobject]@{ name = 'mobile-core production source does not use app BuildConfig'; pattern = '\bBuildConfig\b' },
        [pscustomobject]@{ name = 'mobile-core production source does not import Firebase'; pattern = '(?m)^\s*import\s+.*Firebase' },
        [pscustomobject]@{ name = 'mobile-core production source does not use GurbakirBrand'; pattern = '\bGurbakirBrand\b' },
        [pscustomobject]@{ name = 'mobile-core production source does not use GurbakirHomeConfiguration'; pattern = '\bGurbakirHomeConfiguration\b' },
        [pscustomobject]@{ name = 'mobile-core production source does not use GurbakirCatalogConfiguration'; pattern = '\bGurbakirCatalogConfiguration\b' },
        [pscustomobject]@{ name = 'mobile-core production source does not contain human-readable Gürbakır brand text'; pattern = '(?i)Gür(?:[ \t]+)?Bakır' },
        [pscustomobject]@{ name = 'mobile-core production source does not use gurbakir.com'; pattern = 'gurbakir\.com' },
        [pscustomobject]@{ name = 'mobile-core production source does not use gurbakir-local.db'; pattern = 'gurbakir-local\.db' },
        [pscustomobject]@{ name = 'mobile-core production source does not use SUPPORTED_ADDRESS_TERRITORY'; pattern = '\bSUPPORTED_ADDRESS_TERRITORY\b' }
    )
    foreach ($guard in $mobileCoreSourceGuardPatterns) {
        $matches = @(Find-MobileCoreProductionMatches -Pattern $guard.pattern)
        $evidence = if ($matches.Count -eq 0) { 'none' } else { $matches -join ', ' }
        Add-Check -Name $guard.name -Passed ($matches.Count -eq 0) -Evidence $evidence
    }

    $mobileCoreResourceRoot = Join-Path $mobileCoreProductionRoot 'res'
    $mobileCoreResourceValues = if (Test-Path -LiteralPath $mobileCoreResourceRoot -PathType Container) {
        @(
            Get-ChildItem -LiteralPath $mobileCoreResourceRoot -Recurse -File -Filter '*.xml' |
                ForEach-Object {
                    $relativePath = $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
                    $resourceDocument = [xml](Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8)
                    if ($resourceDocument.DocumentElement.LocalName -eq 'resources') {
                        foreach ($resourceNode in $resourceDocument.DocumentElement.ChildNodes) {
                            if ($resourceNode.NodeType -ne [System.Xml.XmlNodeType]::Element) {
                                continue
                            }
                            [pscustomobject]@{
                                path = $relativePath
                                resource = "$($resourceNode.LocalName)/$($resourceNode.GetAttribute('name'))"
                                value = $resourceNode.InnerText
                            }
                        }
                    }
                }
        )
    } else {
        @()
    }

    function Find-MobileCoreResourceValueMatches {
        param([Parameter(Mandatory)] [string]$Pattern)

        $options = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
            [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
        $matcher = [regex]::new($Pattern, $options)
        return @(
            $mobileCoreResourceValues |
                Where-Object { $matcher.IsMatch($_.value) } |
                ForEach-Object { "$($_.path)@$($_.resource)" }
        )
    }

    $mobileCoreResourceValueGuards = @(
        [pscustomobject]@{
            name = 'mobile-core decoded resource values do not contain concrete brand text'
            pattern = '(?:Gür\s*Bakır|Gur\s*Bakir)'
        },
        [pscustomobject]@{
            name = 'mobile-core decoded resource values do not contain concrete Turkey market text'
            pattern = '(?<![\p{L}\p{N}_])(?:Türkiye|Turkey|Turkish|TR)(?![\p{L}\p{N}_])'
        },
        [pscustomobject]@{
            name = 'mobile-core decoded resource values do not contain concrete +90 guidance'
            pattern = '\+90'
        },
        [pscustomobject]@{
            name = 'mobile-core decoded resource values do not contain fixed five-digit postal guidance'
            pattern = '(?:(?<!\d)5\s*(?:rakam|hane|digits?)|five[-\s]?digits?)'
        }
    )
    foreach ($guard in $mobileCoreResourceValueGuards) {
        $matches = @(Find-MobileCoreResourceValueMatches -Pattern $guard.pattern)
        $evidence = if ($matches.Count -eq 0) { 'none' } else { $matches -join ', ' }
        Add-Check -Name $guard.name -Passed ($matches.Count -eq 0) -Evidence $evidence
    }

    $expectedSyntheticDependencies = @(':mobile-core', ':foundation', ':storefront', ':account', ':checkout')
    $missingSyntheticDependencies = @(
        if ($null -eq $syntheticBuild) {
            $expectedSyntheticDependencies
        } else {
            $expectedSyntheticDependencies | Where-Object {
                -not (Test-GradleProjectDependency $syntheticBuild $_)
            }
        }
    )
    Add-Check -Name 'synthetic application declares the approved shared module edges' -Passed ($missingSyntheticDependencies.Count -eq 0) -Evidence $(if ($missingSyntheticDependencies.Count -eq 0) { $expectedSyntheticDependencies -join ', ' } else { "missing: $($missingSyntheticDependencies -join ', ')" })

    $syntheticPluginViolations = @(
        if ($null -eq $syntheticBuild) {
            'build script missing'
        } else {
            @(
                [pscustomobject]@{ name = 'Kotlin serialization'; pattern = 'libs\.plugins\.kotlin\.serialization|org\.jetbrains\.kotlin\.plugin\.serialization' }
                [pscustomobject]@{ name = 'Room Gradle plugin'; pattern = 'libs\.plugins\.room|androidx\.room' }
                [pscustomobject]@{ name = 'Google Services'; pattern = 'libs\.plugins\.google\.services|com\.google\.gms\.google-services' }
            ) | Where-Object { $syntheticBuild -match $_.pattern } | ForEach-Object { $_.name }
        }
    )
    Add-Check -Name 'synthetic application avoids forbidden plugins' -Passed ($syntheticPluginViolations.Count -eq 0) -Evidence $(if ($syntheticPluginViolations.Count -eq 0) { 'none' } else { $syntheticPluginViolations -join ', ' })

    $syntheticProductionFiles = @(Get-ProductionSourceRecords $repoRoot 'apps/synthetic/src/main')
    $appProductionFiles = @(Get-ProductionSourceRecords $repoRoot 'app/src/main')
    $syntheticGoogleServiceFiles = @(
        Get-ChildItem -LiteralPath (Join-Path $repoRoot 'apps/synthetic') -Recurse -File -Filter 'google-services.json' -ErrorAction SilentlyContinue |
            ForEach-Object { $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/') }
    )
    $syntheticFirebaseSourceMatches = @(Find-RecordMatches $syntheticProductionFiles '(?im)^\s*import\s+.*firebase|\bFirebase(?:App|Analytics|Crashlytics|RemoteConfig|Messaging|Performance)\b|firebase_(?:analytics|crashlytics)_collection_enabled|google_app_id|google-services\.json')
    $syntheticFirebaseFree = -not $syntheticFirebaseDependency -and
        $syntheticPluginViolations -notcontains 'Google Services' -and
        $syntheticGoogleServiceFiles.Count -eq 0 -and
        $syntheticFirebaseSourceMatches.Count -eq 0
    $syntheticFirebaseEvidence = @($syntheticGoogleServiceFiles + $syntheticFirebaseSourceMatches | Select-Object -Unique)
    Add-Check -Name 'synthetic production composition is Firebase-free' -Passed $syntheticFirebaseFree -Evidence $(if ($syntheticFirebaseEvidence.Count -eq 0) { 'no dependency, plugin, config file, source symbol or manifest metadata' } else { $syntheticFirebaseEvidence -join ', ' })

    $syntheticConcreteBrandPattern = '(?i)(?:\bGurbakir(?:Brand|HomeConfiguration|CatalogConfiguration|Application|DeletionPages|DeepLinkConfiguration|TrackingUrlPolicy)\b|Gür[ \t]+Bakır|Gur[ \t]+Bakir|gurbakir\.com|gurbakir-local\.db|gurbakir_secure_|gurbakir\.(?:cart|customer)\.)'
    $appSyntheticPattern = '(?i)(?:com\.example\.gate2synthetic|\bGate2Synthetic[A-Za-z0-9_]*\b|gate2-synthetic|links\.gate2\.invalid|Synthetic\s+Lab)'
    $syntheticConcreteBrandMatches = @(Find-RecordMatches $syntheticProductionFiles $syntheticConcreteBrandPattern)
    $appSyntheticMatches = @(Find-RecordMatches $appProductionFiles $appSyntheticPattern)
    Add-Check -Name 'synthetic production source has no concrete Gürbakır composition contamination' -Passed ($syntheticConcreteBrandMatches.Count -eq 0) -Evidence $(if ($syntheticConcreteBrandMatches.Count -eq 0) { 'none; shared com.gurbakir package imports remain allowed' } else { $syntheticConcreteBrandMatches -join ', ' })
    Add-Check -Name 'Gürbakır production source has no synthetic composition contamination' -Passed ($appSyntheticMatches.Count -eq 0) -Evidence $(if ($appSyntheticMatches.Count -eq 0) { 'none' } else { $appSyntheticMatches -join ', ' })

    $syntheticResourceValues = @(Get-DecodedResourceRecords $repoRoot 'apps/synthetic/src/main/res')
    $appResourceValues = @(Get-DecodedResourceRecords $repoRoot 'app/src/main/res')
    $syntheticResourceContamination = @(Find-RecordMatches $syntheticResourceValues '(?i)(?:Gür\s*Bakır|Gur\s*Bakir|Türkiye|Turkey|\+90|gurbakir\.com)' 'value')
    $appResourceContamination = @(Find-RecordMatches $appResourceValues '(?i)(?:Gate\s*2\s*Synthetic|Synthetic\s+Lab|links\.gate2\.invalid)' 'value')
    Add-Check -Name 'decoded synthetic resources have no Gürbakır or Turkey contamination' -Passed ($syntheticResourceContamination.Count -eq 0) -Evidence $(if ($syntheticResourceContamination.Count -eq 0) { 'none' } else { $syntheticResourceContamination -join ', ' })
    Add-Check -Name 'decoded Gürbakır resources have no synthetic contamination' -Passed ($appResourceContamination.Count -eq 0) -Evidence $(if ($appResourceContamination.Count -eq 0) { 'none' } else { $appResourceContamination -join ', ' })

    $requiredSyntheticResourceValues = @{
        'string/app_name' = 'Gate 2 Synthetic'
        'string/home_title' = 'Synthetic Lab'
        'string/home_product_range_title' = 'Synthetic Picks'
    }
    $syntheticOverlayProblems = [System.Collections.Generic.List[string]]::new()
    foreach ($valuesDirectory in @('values', 'values-en')) {
        $pathSuffix = "apps/synthetic/src/main/res/$valuesDirectory/strings.xml"
        foreach ($entry in $requiredSyntheticResourceValues.GetEnumerator()) {
            $matchingValues = @($syntheticResourceValues | Where-Object {
                $_.path -eq $pathSuffix -and $_.resource -eq $entry.Key -and $_.value -eq $entry.Value
            })
            if ($matchingValues.Count -ne 1) {
                $syntheticOverlayProblems.Add("$valuesDirectory@$($entry.Key)")
            }
        }
    }
    Add-Check -Name 'synthetic default and English resource overlays own exact marker values' -Passed ($syntheticOverlayProblems.Count -eq 0) -Evidence $(if ($syntheticOverlayProblems.Count -eq 0) { 'app_name, home_title and home_product_range_title exact in values and values-en' } else { $syntheticOverlayProblems -join ', ' })

    if ($RequireCleanWorktree) {
        $worktreeStatus = @(git status --porcelain=v1 --untracked-files=normal)
        $worktreeEvidence = if ($worktreeStatus.Count -eq 0) {
            'clean'
        } else {
            $worktreeStatus -join '; '
        }
        Add-Check -Name 'worktree is clean after validation' -Passed ($worktreeStatus.Count -eq 0) -Evidence $worktreeEvidence
    }

    $checks | Format-Table -AutoSize | Out-Host
    $failed = @($checks | Where-Object { -not $_.passed })
    if ($failed.Count -gt 0) {
        throw "Repository portability validation failed: $($failed.check -join '; ')"
    }

    Write-Host "Repository portability validation PASS ($($checks.Count) checks)."
}
finally {
    Pop-Location
}
exit 0
