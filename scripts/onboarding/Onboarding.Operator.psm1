Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Registry.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Shopify.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.CustomerAccount.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Firebase.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.AppLinks.psm1') -Force

# This is the exact operator contract digest that produced the preserved
# configured-acceptance Plan/intent/recovery chain before the probe readback
# comparator was corrected. Recovery accepts this contract transition only;
# it never treats an arbitrary or merely stale operator digest as trusted.
$script:ProbeRecoveryPredecessorOperatorSha256 = 'b504b2102d6edec9556f347ef38b4bc41ff259a39dedd3691577d3b46b323f57'
# Successful RESULT receipts produced by the immediately preceding merged
# operator remain valid attribution evidence across this corrective. This is a
# single reviewed contract transition, not a general stale-digest allowance.
$script:ProbeAttributionPredecessorOperatorSha256 = '0f3e05c72969a0cd47606537691724a417688afd0f8fe4d18b2b1ed0a59321e8'

function Get-OnboardingCredentialName { param([string]$Application,[string]$Profile,[string]$Suffix) ('MB_{0}_{1}_{2}' -f $Application.Replace('-','_'),$Profile.Replace('-','_'),$Suffix).ToUpperInvariant() }
function Get-OnboardingCredential { param([string]$Name) [Environment]::GetEnvironmentVariable($Name, 'Process') }
function Get-OnboardingHomeOperatorContract {
    param([Parameter(Mandatory)]$Context)
    $contractId = [string]$Context.Selected.Profile.storefront.home.definitionContract
    switch -CaseSensitive ($contractId) {
        'gate7-v1' {
            return [pscustomobject]@{
                ContractId = $contractId
                OperationContractVersion = 'gate8-v1'
                SchemaRelativePath = 'config/onboarding/shopify-home-schema.v1.json'
            }
        }
        'pilot-media-v2' {
            return [pscustomobject]@{
                ContractId = $contractId
                OperationContractVersion = 'gate9-v2'
                SchemaRelativePath = 'config/onboarding/shopify-home-schema.v2.json'
            }
        }
        default { throw 'HOME_CONTRACT_MISMATCH' }
    }
}
function Get-ObjectSha { param($Value) $json = Get-OnboardingCanonicalJson $Value; [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($json))).ToLowerInvariant() }
function New-ReceiptAction {
    param([int]$Ordinal,[string]$Kind,[string]$Key,[string]$Mode,[string]$Before,[string]$Action,[string]$Fingerprint)
    [ordered]@{ ordinal=$Ordinal;resourceKind=$Kind;resourceKey=$Key;managementMode=$Mode;beforeClassification=$Before;intendedAction=$Action;beforeFingerprint=$Fingerprint;providerResourceId=$null;status=if($Action -ceq 'NONE'){'NO_OP'}else{'PLANNED'};afterClassification='UNKNOWN';afterFingerprint=('0'*64) }
}
function Get-OnboardingActionContractView {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Actions)

    return @(
        foreach ($action in @($Actions | Sort-Object ordinal)) {
            [ordered]@{
                ordinal = [int]$action.ordinal
                resourceKind = [string]$action.resourceKind
                resourceKey = [string]$action.resourceKey
                managementMode = [string]$action.managementMode
                beforeClassification = [string]$action.beforeClassification
                intendedAction = [string]$action.intendedAction
                beforeFingerprint = [string]$action.beforeFingerprint
                providerResourceId = $action.providerResourceId
                status = [string]$action.status
            }
        }
    )
}
function Test-OnboardingProbeAttribution {
    param(
        [AllowEmptyString()][string]$ReceiptPath,
        [Parameter(Mandatory)]$Context,
        [Parameter(Mandatory)]$ProbeState,
        [Parameter(Mandatory)][hashtable]$CurrentDigests
    )

    if ([string]::IsNullOrWhiteSpace($ReceiptPath)) { return $false }
    $receipt = Import-OnboardingReceipt -Path $ReceiptPath
    if ([string]$receipt.kind -cne 'RESULT' -or
        [string]$receipt.overallStatus -cne 'SUCCEEDED' -or
        [string]$receipt.application -cne [string]$Context.Selected.Application.key -or
        [string]$receipt.verifiedTarget.shopId -cne [string]$Context.Binding.shopify.shopId -or
        [string]$receipt.verifiedTarget.adminShopDomain -cne [string]$Context.Binding.shopify.adminShopDomain) {
        return $false
    }
    $priorSelection = Get-OnboardingApplicationProfile `
        -Registry $Context.Registry `
        -Application ([string]$receipt.application) `
        -Profile ([string]$receipt.profile)
    $currentGroup = [string]$Context.Selected.Profile.storefront.sharedResourceGroup
    $priorGroup = [string]$priorSelection.Profile.storefront.sharedResourceGroup
    if ([string]::IsNullOrWhiteSpace($currentGroup) -or $priorGroup -cne $currentGroup) { return $false }
    foreach ($key in @('registrySha256', 'homeSchemaSha256')) {
        if ([string]$receipt.digests[$key] -cne [string]$CurrentDigests[$key]) { return $false }
    }
    $receiptOperatorDigest = [string]$receipt.digests.operatorSha256
    if ($receiptOperatorDigest -cne [string]$CurrentDigests.operatorSha256 -and
        $receiptOperatorDigest -cne $script:ProbeAttributionPredecessorOperatorSha256) {
        return $false
    }
    if ([string]$receipt.profile -ceq [string]$Context.Selected.Profile.key -and
        [string]$receipt.digests.providerBindingSha256 -cne [string]$CurrentDigests.providerBindingSha256) {
        return $false
    }
    $probeActions = @(
        $receipt.actions | Where-Object {
            [string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE' -and
            [string]$_.resourceKey -ceq 'gate8-operator-acceptance-v1' -and
            [string]$_.status -ceq 'SUCCEEDED' -and
            [string]$_.providerResourceId -ceq [string]$ProbeState.ResourceId
        }
    )
    return $probeActions.Count -eq 1
}
function Get-OnboardingManagedHomeTypes {
    param([Parameter(Mandatory)]$HomeState)

    if ($null -ne $HomeState.PSObject.Properties['ManagedTypes'] -and
        @($HomeState.ManagedTypes).Count -gt 0) {
        return @($HomeState.ManagedTypes | ForEach-Object { [string]$_ })
    }
    return @('mobile_home_collection_grid', 'mobile_home_featured_product', 'mobile_home')
}

function New-OnboardingExpectedActions {
    param(
        [Parameter(Mandatory)]$HomeState,
        [Parameter(Mandatory)]$ProbeState,
        [switch]$IncludeAcceptanceProbe,
        [switch]$ProbeIsAttributed
    )

    if ([string]$HomeState.Classification -eq 'INCOMPATIBLE') { throw 'INCOMPATIBLE_HOME_DEFINITIONS' }
    $actions = [Collections.Generic.List[object]]::new()
    $ordinal = 1
    $managedTypes = @(Get-OnboardingManagedHomeTypes -HomeState $HomeState)
    foreach ($type in $managedTypes) {
        if ($type -in @($HomeState.MissingTypes)) {
            $actions.Add((New-ReceiptAction $ordinal 'SHOPIFY_HOME_DEFINITION' $type 'CREATE_IF_MISSING' 'ABSENT' 'CREATE' $HomeState.Fingerprint))
            $ordinal++
        }
    }
    if ($IncludeAcceptanceProbe) {
        if ([string]$ProbeState.Classification -ceq 'ABSENT') {
            $actions.Add((New-ReceiptAction $ordinal 'SHOPIFY_HOME_ACCEPTANCE_PROBE' 'gate8-operator-acceptance-v1' 'PROBE_CREATE_IF_MISSING' 'ABSENT' 'CREATE' $ProbeState.Fingerprint))
        } elseif ([string]$ProbeState.Classification -ceq 'COMPATIBLE' -and $ProbeIsAttributed) {
            $action = New-ReceiptAction $ordinal 'SHOPIFY_HOME_ACCEPTANCE_PROBE' 'gate8-operator-acceptance-v1' 'PROBE_CREATE_IF_MISSING' 'COMPATIBLE' 'NONE' $ProbeState.Fingerprint
            $action.providerResourceId = [string]$ProbeState.ResourceId
            $actions.Add($action)
        } else {
            throw 'PROBE_COLLISION'
        }
    }
    # PowerShell enumerates ordinary array output. An empty array therefore
    # collapses to $null unless it is emitted as one collection object.
    return ,([object[]]$actions.ToArray())
}
function New-OnboardingRecoverySnapshot {
    param([Parameter(Mandatory)]$Plan,[Parameter(Mandatory)][object[]]$Actions,[Parameter(Mandatory)]$Action)
    $snapshot = (Get-OnboardingCanonicalJson $Plan) | ConvertFrom-Json -AsHashtable
    $snapshot.kind = 'RECOVERY'
    $snapshot.expiresAtUtc = $null
    $snapshot.overallStatus = 'PARTIAL'
    $snapshot.actions = $Actions
    $snapshot.diagnosticCodes = @('PARTIAL_APPLY')
    $snapshot.readback = @()
    $snapshot.recovery = @([ordered]@{
        ordinal = [int]$Action.ordinal
        resourceKind = [string]$Action.resourceKind
        resourceKey = [string]$Action.resourceKey
        classification = 'UNKNOWN'
        nextAction = 'REINSPECT'
    })
    return $snapshot
}
function New-OnboardingFinalReadbackRecoverySnapshot {
    param(
        [Parameter(Mandatory)]$Plan,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Actions,
        $FinalHome,
        $FinalProbe,
        [switch]$IncludeAcceptanceProbe
    )

    $snapshot = (Get-OnboardingCanonicalJson $Plan) | ConvertFrom-Json -AsHashtable
    $snapshot.kind = 'RECOVERY'
    $snapshot.expiresAtUtc = $null
    $snapshot.overallStatus = 'PARTIAL'
    $snapshot.actions = @($Actions)
    $snapshot.diagnosticCodes = @('READBACK_MISMATCH')
    $probeActions = @(
        $Actions | Where-Object {
            [string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'
        }
    )
    if ($IncludeAcceptanceProbe -and $probeActions.Count -ne 1) {
        throw 'PROBE_RECOVERY_ACTION_MISMATCH'
    }
    $snapshot.readback = @(
        [ordered]@{
            surface = 'SHOPIFY_ADMIN'
            resourceKey = 'home-definitions'
            classification = if ($null -ne $FinalHome -and [string]$FinalHome.Classification -ceq 'COMPATIBLE' -and @($FinalHome.MissingTypes).Count -eq 0) { 'PASS' } else { 'FAIL' }
            identityFingerprint = if ($null -ne $FinalHome) { [string]$FinalHome.Fingerprint } else { '0' * 64 }
        }
        if ($IncludeAcceptanceProbe) {
            [ordered]@{
                surface = 'SHOPIFY_ADMIN'
                resourceKey = [string]$probeActions[0].resourceKey
                classification = if ($null -ne $FinalProbe -and [string]$FinalProbe.Classification -ceq 'COMPATIBLE') { 'PASS' } else { 'FAIL' }
                identityFingerprint = if ($null -ne $FinalProbe) { [string]$FinalProbe.Fingerprint } else { '0' * 64 }
            }
        }
    )
    $lastWrite = @(
        $Actions | Where-Object {
            [string]$_.intendedAction -ceq 'CREATE' -and [string]$_.status -ceq 'SUCCEEDED'
        } | Sort-Object ordinal | Select-Object -Last 1
    )
    $snapshot.recovery = if ($lastWrite.Count -eq 1) {
        @([ordered]@{
            ordinal = [int]$lastWrite[0].ordinal
            resourceKind = [string]$lastWrite[0].resourceKind
            resourceKey = [string]$lastWrite[0].resourceKey
            classification = 'DRIFTED'
            nextAction = 'REINSPECT'
        })
    } else {
        @()
    }
    return $snapshot
}
function Resolve-OnboardingEvidencePath {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Field
    )

    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'RECEIPT_PATH_REQUIRED' }
    $evidenceRoot = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'out\onboarding')).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $full = if ([IO.Path]::IsPathRooted($Path)) { [IO.Path]::GetFullPath($Path) } else { [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $Path)) }
    if (-not $full.StartsWith($evidenceRoot, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($full) -cne '.json') {
        throw "UNSAFE_EVIDENCE_PATH:$Field"
    }
    $candidate = Split-Path -Parent $full
    while ($candidate.StartsWith($evidenceRoot.TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase)) {
        if ((Test-Path -LiteralPath $candidate) -and ([IO.File]::GetAttributes($candidate) -band [IO.FileAttributes]::ReparsePoint)) {
            throw "UNSAFE_EVIDENCE_PATH:$Field"
        }
        if ($candidate.TrimEnd('\', '/') -ceq $evidenceRoot.TrimEnd('\', '/')) { break }
        $candidate = Split-Path -Parent $candidate
    }
    $relative = [IO.Path]::GetRelativePath($RepositoryRoot, $full).Replace('\', '/')
    & git -C $RepositoryRoot check-ignore --no-index --quiet -- $relative
    if ($LASTEXITCODE -ne 0) { throw "EVIDENCE_PATH_NOT_IGNORED:$Field" }
    & git -C $RepositoryRoot ls-files --error-unmatch -- $relative 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { throw "EVIDENCE_PATH_TRACKED:$Field" }
    return $full
}
function Assert-OnboardingLocalWritePath {
    param([Parameter(Mandatory)][string]$RepositoryRoot,[Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)][string]$Field)
    $relative=[IO.Path]::GetRelativePath($RepositoryRoot,$Path).Replace('\','/')
    & git -C $RepositoryRoot check-ignore --no-index --quiet -- $relative
    if($LASTEXITCODE-ne 0){throw "LOCAL_CONFIGURATION_NOT_IGNORED:$Field"}
    & git -C $RepositoryRoot ls-files --error-unmatch -- $relative 2>$null | Out-Null
    if($LASTEXITCODE-eq 0){throw "LOCAL_CONFIGURATION_TRACKED:$Field"}
    $candidate = if (Test-Path -LiteralPath $Path) { $Path } else { Split-Path -Parent $Path }
    $root = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    while (-not [string]::IsNullOrWhiteSpace($candidate) -and $candidate.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        if ((Test-Path -LiteralPath $candidate) -and ([IO.File]::GetAttributes($candidate) -band [IO.FileAttributes]::ReparsePoint)) {
            throw "UNSAFE_LOCAL_CONFIGURATION_PATH:$Field"
        }
        if ($candidate.TrimEnd('\', '/') -ceq $root) { break }
        $candidate = Split-Path -Parent $candidate
    }
}
function Set-OnboardingPrivateFilePermissions {
    param([Parameter(Mandatory)][string]$Path)
    if ($IsWindows) {
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        & icacls.exe $Path '/inheritance:r' '/grant:r' "*${sid}:(F)" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'LOCAL_CONFIGURATION_PERMISSION_FAILURE' }
    } else {
        [IO.File]::SetUnixFileMode($Path, [IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite)
    }
}
function Assert-OnboardingVisibleAsciiValue {
    param([Parameter(Mandatory)][string]$Value,[Parameter(Mandatory)][string]$Field,[Parameter(Mandatory)][int]$MaximumLength)
    if ($Value.Length -lt 1 -or $Value.Length -gt $MaximumLength -or $Value -cnotmatch '^[\x21-\x7e]+$') {
        throw "UNSAFE_CLIENT_CONFIGURATION:$Field"
    }
}
function Assert-OnboardingHttpsClientEndpoint {
    param([Parameter(Mandatory)][string]$Value,[Parameter(Mandatory)][string]$Field)
    if ($Value.Length -gt 2048) { throw "UNSAFE_CLIENT_CONFIGURATION:$Field" }
    $uri = $null
    if (-not [uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -cne 'https' -or $uri.UserInfo.Length -ne 0 -or $uri.Fragment.Length -ne 0) {
        throw "UNSAFE_CLIENT_CONFIGURATION:$Field"
    }
    Assert-OnboardingHost -HostName $uri.IdnHost -Field $Field
    if (-not (Test-OnboardingShopifyCustomerUri -Uri $uri)) {
        throw "UNSAFE_CLIENT_CONFIGURATION:$Field"
    }
    return $uri
}
function Get-OnboardingValidatedClientConfigurationLines {
    param([Parameter(Mandatory)]$Context,[Parameter(Mandatory)][System.Collections.IDictionary]$Values)
    $requiredKeys = [Collections.Generic.List[string]]::new()
    if ([string]$Context.Selected.Profile.storefront.mode -ceq 'enabled') {
        $requiredKeys.Add('shopify.storefrontPublicToken')
    }
    if ([string]$Context.Selected.Profile.customerAccount.mode -ceq 'enabled-manual-registration') {
        foreach ($key in @(
            'shopify.customerAccountClientId',
            'shopify.customerAccountIssuer',
            'shopify.customerAccountAuthorizationEndpoint',
            'shopify.customerAccountTokenEndpoint',
            'shopify.customerAccountLogoutEndpoint',
            'shopify.customerAccountGraphqlEndpoint',
            'shopify.customerAccountRedirectUri'
        )) {
            $requiredKeys.Add($key)
        }
    }
    if ((@($Values.Keys | Sort-Object -CaseSensitive) -join "`n") -cne (@($requiredKeys | Sort-Object -CaseSensitive) -join "`n")) {
        throw 'MISSING_OR_UNSAFE_CLIENT_CONFIGURATION'
    }
    foreach($key in $requiredKeys){if([string]::IsNullOrWhiteSpace([string]$Values[$key])-or[string]$Values[$key]-match '[\r\n]'){throw 'MISSING_OR_UNSAFE_CLIENT_CONFIGURATION'}}
    if ([string]$Context.Selected.Profile.storefront.mode -ceq 'enabled') {
        Assert-OnboardingVisibleAsciiValue -Value ([string]$Values['shopify.storefrontPublicToken']) -Field 'shopify.storefrontPublicToken' -MaximumLength 4096
    }
    if ([string]$Context.Selected.Profile.customerAccount.mode -ceq 'enabled-manual-registration') {
        Assert-OnboardingVisibleAsciiValue -Value ([string]$Values['shopify.customerAccountClientId']) -Field 'shopify.customerAccountClientId' -MaximumLength 1024
        $expectedIssuer="https://shopify.com/authentication/$($Context.Binding.shopify.shopId)"
        if([string]$Values['shopify.customerAccountIssuer']-cne$expectedIssuer){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountIssuer'}
        foreach($key in @('shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint','shopify.customerAccountLogoutEndpoint')){[void](Assert-OnboardingHttpsClientEndpoint ([string]$Values[$key]) $key)}
        $graphql=[uri](Assert-OnboardingHttpsClientEndpoint ([string]$Values['shopify.customerAccountGraphqlEndpoint']) 'shopify.customerAccountGraphqlEndpoint')
        $apiVersion=[string]$Context.Registry.providerContracts.shopifyCustomerAccountApiVersion
        if(-not$graphql.AbsolutePath.EndsWith("/$apiVersion/graphql",[StringComparison]::Ordinal)){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountGraphqlEndpoint'}
        $identity=$Context.Selected.Application.identity.customerAccount
        $expectedCallback="shop.$($Context.Binding.shopify.shopId).$($identity.callbackSchemeSuffix)://$($identity.callbackHost)$($identity.callbackPath)"
        if([string]$Values['shopify.customerAccountRedirectUri']-cne$expectedCallback){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountRedirectUri'}
    }
    return @($requiredKeys|ForEach-Object{"$_=$([string]$Values[$_])"})
}
function Get-OnboardingClientConfigurationValues {
    param(
        [Parameter(Mandatory)]$Context,
        [System.Collections.IDictionary]$ClientValues
    )
    $allowedKeys=@(
        'shopify.storefrontPublicToken','shopify.customerAccountClientId','shopify.customerAccountIssuer',
        'shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint',
        'shopify.customerAccountLogoutEndpoint','shopify.customerAccountGraphqlEndpoint','shopify.customerAccountRedirectUri'
    )
    $requiresLocal = [string]$Context.Selected.Profile.storefront.mode -ceq 'enabled' -or
        [string]$Context.Selected.Profile.customerAccount.mode -ceq 'enabled-manual-registration'
    if ($null -ne $ClientValues) {
        $values = $ClientValues
    } elseif ($requiresLocal) {
        $relativePath = [string]$Context.Selected.Profile.localConfiguration
        if ([string]::IsNullOrWhiteSpace($relativePath)) { throw 'MISSING_CLIENT_CONFIGURATION_PATH' }
        $localPath = Test-OnboardingSafeRelativePath $Context.RepositoryRoot $relativePath 'localConfiguration'
        $values = Read-OnboardingProperties -Path $localPath -AllowedKeys $allowedKeys
    } else {
        $values = [ordered]@{}
    }
    [void](Get-OnboardingValidatedClientConfigurationLines -Context $Context -Values $values)
    return $values
}
function Write-OnboardingPrivateProperties {
    param([Parameter(Mandatory)][string]$RepositoryRoot,[Parameter(Mandatory)][string]$Destination,[Parameter(Mandatory)][string[]]$Lines,[switch]$RefuseOverwrite)
    Assert-OnboardingLocalWritePath $RepositoryRoot $Destination 'localConfiguration'
    if($RefuseOverwrite -and (Test-Path -LiteralPath $Destination)){throw 'DESTINATION_EXISTS'}
    $parent=Split-Path -Parent $Destination;[IO.Directory]::CreateDirectory($parent)|Out-Null
    Assert-OnboardingLocalWritePath $RepositoryRoot $Destination 'localConfiguration'
    $temporary=Join-Path $parent ('.gate8-'+[guid]::NewGuid().ToString('N')+'.tmp')
    $backup="$Destination.bak";Assert-OnboardingLocalWritePath $RepositoryRoot $backup 'localConfigurationBackup'
    if((Test-Path -LiteralPath $Destination)-and(Test-Path -LiteralPath $backup)){throw 'LOCAL_CONFIGURATION_BACKUP_EXISTS'}
    try{
        [IO.File]::WriteAllText($temporary,(($Lines-join"`n")+"`n"),[Text.UTF8Encoding]::new($false));Set-OnboardingPrivateFilePermissions $temporary
        if(Test-Path -LiteralPath $Destination){[IO.File]::Replace($temporary,$Destination,$backup,$true);Set-OnboardingPrivateFilePermissions $backup}else{[IO.File]::Move($temporary,$Destination)}
        Set-OnboardingPrivateFilePermissions $Destination
    }finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}}
}
function Write-OnboardingReceipt { param([string]$Path,$Receipt,[switch]$NoOverwrite)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'RECEIPT_PATH_REQUIRED' }
    $full=[IO.Path]::GetFullPath($Path); $parent=Split-Path -Parent $full; [IO.Directory]::CreateDirectory($parent)|Out-Null
    if ($NoOverwrite -and (Test-Path -LiteralPath $full)) { throw 'RECEIPT_ALREADY_EXISTS' }
    $temporary=Join-Path $parent ('.gate8-receipt-'+[guid]::NewGuid().ToString('N')+'.tmp')
    try {
        $json=Get-OnboardingCanonicalJson $Receipt
        [IO.File]::WriteAllText($temporary,$json+"`n",[Text.UTF8Encoding]::new($false))
        [void](Import-OnboardingReceipt -Path $temporary)
        [IO.File]::Move($temporary,$full,-not [bool]$NoOverwrite)
    } finally {
        if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary -Force}
    }
}
function Get-OnboardingOperatorDigest {
    param([Parameter(Mandatory)][string]$RepositoryRoot)
    $relativePaths=@(
        'scripts/Invoke-MultiBrandOnboarding.ps1',
        'scripts/onboarding/Onboarding.Common.psm1',
        'scripts/onboarding/Onboarding.Registry.psm1',
        'scripts/onboarding/Onboarding.Shopify.psm1',
        'scripts/onboarding/Onboarding.CustomerAccount.psm1',
        'scripts/onboarding/Onboarding.Firebase.psm1',
        'scripts/onboarding/Onboarding.AppLinks.psm1',
        'scripts/onboarding/Onboarding.Operator.psm1'
    )
    $contract=[ordered]@{}
    foreach($relativePath in $relativePaths){
        $contract[$relativePath]=Get-OnboardingSha256 (Join-Path $RepositoryRoot $relativePath)
    }
    return Get-ObjectSha $contract
}
function Get-OnboardingOperatorContext {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile)
    $registryPath=Join-Path $RepositoryRoot 'config\onboarding\application-registry.v1.json'
    $registry=Import-OnboardingRegistry -Path $registryPath -RepositoryRoot $RepositoryRoot
    $selected=Get-OnboardingApplicationProfile -Registry $registry -Application $Application -Profile $Profile
    if ([bool]$selected.Application.fixtureOnly -or [string]$selected.Application.role -eq 'synthetic-conformance-application') { throw 'UNSAFE_EXTERNAL_TARGET' }
    $bindingPath=Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot -RelativePath ([string]$selected.Profile.providerBindingFile) -Field 'providerBindingFile'
    $variants=@($selected.Profile.variants|Where-Object{$null-ne$_.firebaseConfig}|ForEach-Object{[string]$_.name})
    $binding=Import-OnboardingProviderBinding -Path $bindingPath -Application $Application -Profile $Profile -ExpectedFirebaseVariants $variants
    [pscustomobject]@{RepositoryRoot=$RepositoryRoot;Registry=$registry;Selected=$selected;Binding=$binding;RegistryPath=$registryPath;BindingPath=$bindingPath}
}
function Get-OnboardingManualCheckpointState {
    param(
        [Parameter(Mandatory)]$Context,
        [AllowEmptyString()][string]$CheckpointPath,
        [Parameter(Mandatory)][System.Collections.IDictionary]$ClientValues
    )
    if ([string]$Context.Selected.Profile.customerAccount.mode -ceq 'disabled') {
        return [pscustomobject]@{ Classification = 'NOT_APPLICABLE'; Fingerprint = ('0' * 64) }
    }
    if ([string]::IsNullOrWhiteSpace($CheckpointPath)) {
        return [pscustomobject]@{ Classification = 'MANUAL_REQUIRED'; Fingerprint = ('0' * 64) }
    }
    $full = Resolve-OnboardingEvidencePath $Context.RepositoryRoot $CheckpointPath 'ManualCheckpointPath'
    $checkpoint = Read-OnboardingStrictJson -Path $full -MaximumBytes 16384
    Assert-OnboardingObjectFields `
        -Object $checkpoint `
        -Allowed @('schemaVersion','application','profile','shopId','clientIdSha256','callback','recordedAtUtc','approvedEvidenceRef') `
        -Required @('schemaVersion','application','profile','shopId','clientIdSha256','callback','recordedAtUtc','approvedEvidenceRef') `
        -Field '$'
    $clientHash = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData(
            [Text.Encoding]::UTF8.GetBytes([string]$ClientValues['shopify.customerAccountClientId'])
        )
    ).ToLowerInvariant()
    $identity = $Context.Selected.Application.identity.customerAccount
    $expectedCallback = "shop.$($Context.Binding.shopify.shopId).$($identity.callbackSchemeSuffix)://$($identity.callbackHost)$($identity.callbackPath)"
    if ($checkpoint.schemaVersion -isnot [long] -or [long]$checkpoint.schemaVersion -ne 1 -or
        [string]$checkpoint.application -cne [string]$Context.Selected.Application.key -or
        [string]$checkpoint.profile -cne [string]$Context.Selected.Profile.key -or
        [string]$checkpoint.shopId -cne [string]$Context.Binding.shopify.shopId -or
        [string]$checkpoint.clientIdSha256 -cne $clientHash -or
        [string]$checkpoint.callback -cne $expectedCallback -or
        [string]$ClientValues['shopify.customerAccountRedirectUri'] -cne $expectedCallback -or
        [string]$checkpoint.clientIdSha256 -cnotmatch '^[0-9a-f]{64}$' -or
        [string]$checkpoint.recordedAtUtc -cnotmatch '^[0-9]{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$' -or
        [string]$checkpoint.approvedEvidenceRef -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$') {
        throw 'CUSTOMER_REGISTRATION_CHECKPOINT_MISMATCH'
    }
    $fingerprint = Get-OnboardingSha256 $full
    return [pscustomobject]@{ Classification = 'PASS'; Fingerprint = $fingerprint }
}
function Get-OnboardingFirebaseInspectionState {
    param(
        [Parameter(Mandatory)]$Context,
        [Parameter(Mandatory)][string]$Application,
        [Parameter(Mandatory)][string]$Profile,
        [scriptblock]$Transport
    )
    if ([string]$Context.Selected.Profile.firebase.mode -ceq 'disabled') {
        return Get-OnboardingFirebaseState $Context.Selected $Context.Binding '' $Transport
    }
    $firebaseName = Get-OnboardingCredentialName $Application $Profile 'FIREBASE_ACCESS_TOKEN'
    $firebase = Get-OnboardingCredential $firebaseName
    if ([string]::IsNullOrWhiteSpace($firebase)) {
        return [pscustomobject]@{ Classification = 'EXTERNALLY_BLOCKED'; Fingerprint = ('0' * 64) }
    }
    return Get-OnboardingFirebaseState $Context.Selected $Context.Binding $firebase $Transport
}
function Invoke-OnboardingInspect {
    param(
        [string]$RepositoryRoot,
        [string]$Application,
        [string]$Profile,
        [scriptblock]$Transport,
        [System.Collections.IDictionary]$ClientValues,
        [AllowEmptyString()][string]$ManualCheckpointPath = ''
    )
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeContract=Get-OnboardingHomeOperatorContract $context
    $values = Get-OnboardingClientConfigurationValues -Context $context -ClientValues $ClientValues
    $result=[ordered]@{application=$Application;profile=$Profile;runtimeEnvironment=[string]$context.Selected.Profile.runtimeEnvironment;shopId=[string]$context.Binding.shopify.shopId}
    if ([string]$context.Selected.Profile.storefront.mode -ceq 'enabled') {
        $result.storefrontTarget=(Get-ShopifyStorefrontTargetState $context.Selected $context.Binding ([string]$values['shopify.storefrontPublicToken']) $Transport).Classification
        $adminName=Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN'; $admin=Get-OnboardingCredential $adminName
        if ([string]::IsNullOrWhiteSpace($admin)) { $result.shopifyTarget='EXTERNALLY_BLOCKED';$result.menu='EXTERNALLY_BLOCKED';$result.homeDefinitions='EXTERNALLY_BLOCKED';$result.acceptanceProbe='EXTERNALLY_BLOCKED' }
        else {
            $result.shopifyTarget=(Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport).Classification
            $menu=Get-ShopifyMenuState $context.Binding $admin ([string]$context.Selected.Profile.storefront.catalog.menuHandle) $Transport
            $homeState=Get-ShopifyHomeDefinitionState `
                $context.Binding $admin $Transport -ContractId $homeContract.ContractId
            $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
            $result.menu=$menu.Classification;$result.homeDefinitions=$homeState.Classification;$result.acceptanceProbe=$probe.Classification
        }
    } else {
        $result.storefrontTarget='NOT_APPLICABLE';$result.shopifyTarget='NOT_APPLICABLE';$result.menu='NOT_APPLICABLE';$result.homeDefinitions='NOT_APPLICABLE';$result.acceptanceProbe='NOT_APPLICABLE'
    }
    try { $result.customerDiscovery=(Get-OnboardingCustomerDiscovery $context.Selected $context.Binding $Transport).Classification } catch {
        if ([string]$_.Exception.Message -match '^(PROVIDER_TRANSPORT_FAILURE|CUSTOMER_DISCOVERY_HTTP_FAILURE)') { $result.customerDiscovery='EXTERNALLY_BLOCKED' } else { throw }
    }
    $result.customer=$result.customerDiscovery
    $result.customerRegistration=(Get-OnboardingManualCheckpointState -Context $context -CheckpointPath $ManualCheckpointPath -ClientValues $values).Classification
    try { $result.assetLinks=(Get-OnboardingAssetLinksState $context.Selected $Transport).Classification } catch {
        if ([string]$_.Exception.Message -match '^PROVIDER_TRANSPORT_FAILURE') { $result.assetLinks='EXTERNALLY_BLOCKED' } else { throw }
    }
    $result.firebase=(Get-OnboardingFirebaseInspectionState `
        -Context $context -Application $Application -Profile $Profile -Transport $Transport).Classification
    return [pscustomobject]$result
}
function Get-OnboardingExecutedStorefrontProofs {
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string[]]$ExpectedProofs
    )
    $resultRoot = Join-Path $RepositoryRoot 'storefront\build\test-results\testDebugUnitTest'
    $executed = [Collections.Generic.List[string]]::new()
    foreach ($proof in $ExpectedProofs) {
        $path = Join-Path $resultRoot "TEST-$proof.xml"
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        $settings = [Xml.XmlReaderSettings]::new()
        $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver = $null
        $reader = [Xml.XmlReader]::Create($path, $settings)
        try {
            $document = [Xml.XmlDocument]::new()
            $document.XmlResolver = $null
            $document.Load($reader)
        } finally {
            $reader.Dispose()
        }
        $suite = $document.DocumentElement
        if ($null -eq $suite -or $suite.Name -cne 'testsuite' -or
            [int]$suite.GetAttribute('tests') -lt 1 -or
            [int]$suite.GetAttribute('failures') -ne 0 -or
            [int]$suite.GetAttribute('errors') -ne 0 -or
            [int]$suite.GetAttribute('skipped') -ne 0) {
            continue
        }
        $testCases = @($suite.SelectNodes('testcase'))
        if (@($testCases | Where-Object { [string]$_.GetAttribute('classname') -ceq $proof }).Count -lt 1) { continue }
        $executed.Add($proof)
    }
    return ,([string[]]$executed.ToArray())
}
function Invoke-OnboardingReadback {
    param(
        [string]$RepositoryRoot,
        [string]$Application,
        [string]$Profile,
        [scriptblock]$Transport,
        [scriptblock]$ProcessRunner,
        [System.Collections.IDictionary]$ClientValues,
        [AllowEmptyString()][string]$ManualCheckpointPath = ''
    )
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeContract=Get-OnboardingHomeOperatorContract $context
    $values=Get-OnboardingClientConfigurationValues -Context $context -ClientValues $ClientValues
    $inspection=Invoke-OnboardingInspect `
        -RepositoryRoot $RepositoryRoot -Application $Application -Profile $Profile `
        -Transport $Transport -ClientValues $values -ManualCheckpointPath $ManualCheckpointPath
    if ([string]$context.Selected.Profile.storefront.mode -cne 'enabled') {
        $result=[ordered]@{};foreach($property in $inspection.PSObject.Properties){$result[$property.Name]=$property.Value};$result.storefrontMobileReadback='NOT_APPLICABLE'
        return [pscustomobject]$result
    }
    $expectedProofs = if ([string]$homeContract.ContractId -ceq 'pilot-media-v2') {
        @('com.gurbakir.storefront.OwnedHomeV2ReadProofTest')
    } else {
        @(
            'com.gurbakir.storefront.OwnedCatalogDiscoveryProofTest',
            'com.gurbakir.storefront.OwnedHomeContentReadbackTest'
        )
    }
    $arguments=[Collections.Generic.List[string]]::new()
    $arguments.Add(':storefront:testDebugUnitTest')
    foreach($proof in $expectedProofs){$arguments.Add('--tests');$arguments.Add($proof)}
    foreach($argument in @('--rerun-tasks','--no-build-cache','--console=plain')){$arguments.Add($argument)}
    if ([string]$homeContract.ContractId -ceq 'pilot-media-v2') {
        $arguments.Add('-PonboardingRunOwnedHomeV2Readback=true')
    } else {
        $arguments.Add('-PgurbakirRunOwnedStorefrontProof=true')
        $arguments.Add('-PonboardingRunOwnedHomeReadback=true')
    }
    $arguments.Add("-PonboardingApplication=$Application")
    $arguments.Add("-PonboardingProfile=$Profile")
    $privilegedNames=@([Environment]::GetEnvironmentVariables('Process').Keys|Where-Object{[string]$_ -match '^MB_[A-Z0-9_]+_(SHOPIFY_ADMIN_TOKEN|FIREBASE_ACCESS_TOKEN)$'}|ForEach-Object{[string]$_})
    if($null-ne$ProcessRunner){
        $runnerResult = & $ProcessRunner @($arguments) $privilegedNames
        if ($runnerResult -is [int]) {
            $exitCode = [int]$runnerResult
            $executedProofs = @()
        } else {
            $exitCode = [int]$runnerResult.ExitCode
            $executedProofs = @($runnerResult.ExecutedProofs | ForEach-Object { [string]$_ })
        }
    }else{
        foreach ($proof in $expectedProofs) {
            $existingReport = Join-Path $RepositoryRoot "storefront\build\test-results\testDebugUnitTest\TEST-$proof.xml"
            if (Test-Path -LiteralPath $existingReport) { Remove-Item -LiteralPath $existingReport -Force }
        }
        $executable=if($IsWindows){Join-Path $RepositoryRoot 'gradlew.bat'}else{Join-Path $RepositoryRoot 'gradlew'}
        $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$executable;$start.WorkingDirectory=$RepositoryRoot;$start.UseShellExecute=$false;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        foreach($argument in @($arguments)){[void]$start.ArgumentList.Add($argument)}
        foreach($name in $privilegedNames){[void]$start.Environment.Remove($name)}
        $process=[Diagnostics.Process]::new();$process.StartInfo=$start
        try{[void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync();$process.WaitForExit();[void]$stdout.GetAwaiter().GetResult();[void]$stderr.GetAwaiter().GetResult();$exitCode=$process.ExitCode}finally{$process.Dispose()}
        $executedProofs = if ($exitCode -eq 0) { @(Get-OnboardingExecutedStorefrontProofs $RepositoryRoot $expectedProofs) } else { @() }
    }
    if($exitCode-ne 0){throw 'MOBILE_READBACK_FAILURE'}
    if ((@($executedProofs | Sort-Object -CaseSensitive) -join "`n") -cne (@($expectedProofs | Sort-Object -CaseSensitive) -join "`n")) {
        throw 'MOBILE_READBACK_PROOF_INCOMPLETE'
    }
    $result=[ordered]@{};foreach($property in $inspection.PSObject.Properties){$result[$property.Name]=$property.Value};$result.storefrontMobileReadback='PASS'
    return [pscustomobject]$result
}
function New-OnboardingReceiptTimeWindow {
    param([Parameter(Mandatory)][DateTimeOffset]$Now)
    $utc = $Now.ToUniversalTime()
    $wholeSecond = [DateTimeOffset]::new(
        $utc.Year,
        $utc.Month,
        $utc.Day,
        $utc.Hour,
        $utc.Minute,
        $utc.Second,
        [TimeSpan]::Zero
    )
    [pscustomobject]@{
        CreatedAtUtc = $wholeSecond.ToString('yyyy-MM-ddTHH:mm:ssZ')
        ExpiresAtUtc = $wholeSecond.AddMinutes(15).ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
}
function New-OnboardingPlan {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[switch]$IncludeAcceptanceProbe,[string]$PriorReceipt,[scriptblock]$Transport,[System.Collections.IDictionary]$ClientValues)
    $timeWindow=New-OnboardingReceiptTimeWindow -Now ([DateTimeOffset]::UtcNow);$created=[string]$timeWindow.CreatedAtUtc;$expires=[string]$timeWindow.ExpiresAtUtc
    if([string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath="out/onboarding/$created-$Application-$Profile-plan.json" -replace ':',''}
    $OutputPath=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'
    if(-not[string]::IsNullOrWhiteSpace($PriorReceipt)){$PriorReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PriorReceipt 'PriorReceipt'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeContract=Get-OnboardingHomeOperatorContract $context
    if ([string]$context.Selected.Application.releaseBoundary -cne 'nonproduction-only') { throw 'UNSAFE_RELEASE_BOUNDARY' }
    if ([string]$context.Selected.Profile.storefront.mode -cne 'enabled') { throw 'SHOPIFY_STOREFRONT_DISABLED' }
    if ($IncludeAcceptanceProbe -and [string]$homeContract.ContractId -cne 'gate7-v1') { throw 'PROBE_NOT_SUPPORTED_FOR_HOME_CONTRACT' }
    $values=Get-OnboardingClientConfigurationValues -Context $context -ClientValues $ClientValues
    $admin=Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
    [void](Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport)
    [void](Get-ShopifyStorefrontTargetState $context.Selected $context.Binding ([string]$values['shopify.storefrontPublicToken']) $Transport)
    $homeState=Get-ShopifyHomeDefinitionState `
        $context.Binding $admin $Transport -ContractId $homeContract.ContractId
    $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
    $homeSchema=Join-Path $RepositoryRoot $homeContract.SchemaRelativePath
    $digests=[ordered]@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingOperatorDigest $RepositoryRoot}
    $probeIsAttributed=Test-OnboardingProbeAttribution -ReceiptPath $PriorReceipt -Context $context -ProbeState $probe -CurrentDigests $digests
    $actions=New-OnboardingExpectedActions -HomeState $homeState -ProbeState $probe -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -ProbeIsAttributed:$probeIsAttributed
    $receipt=[ordered]@{receiptSchemaVersion=1;operationContractVersion=[string]$homeContract.OperationContractVersion;kind='PLAN';application=$Application;profile=$Profile;runtimeEnvironment=[string]$context.Selected.Profile.runtimeEnvironment;releaseBoundary=[string]$context.Selected.Application.releaseBoundary;createdAtUtc=$created;expiresAtUtc=$expires;verifiedTarget=[ordered]@{shopId=[string]$context.Binding.shopify.shopId;adminShopDomain=[string]$context.Binding.shopify.adminShopDomain;firebaseProjectId=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectId}else{$null};firebaseProjectNumber=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectNumber}else{$null}};digests=$digests;stateFingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint});actions=$actions;overallStatus='PLANNED';diagnosticCodes=@();readback=@();recovery=@()}
    Write-OnboardingReceipt $OutputPath $receipt -NoOverwrite;return $receipt
}
function Invoke-OnboardingApply {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$PlanReceipt,[string]$ConfirmApplication,[string]$ConfirmProfile,[switch]$ConfirmApply,[switch]$IncludeAcceptanceProbe,[string]$PriorReceipt,[string]$OutputPath,[scriptblock]$Transport,[System.Collections.IDictionary]$ClientValues)
    if(-not $ConfirmApply -or $ConfirmApplication -cne $Application -or $ConfirmProfile -cne $Profile){throw 'CONFIRMATION_MISMATCH'}
    $PlanReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PlanReceipt 'PlanReceipt'
    if(-not[string]::IsNullOrWhiteSpace($PriorReceipt)){$PriorReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PriorReceipt 'PriorReceipt'}
    if(-not[string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'}
    $plan=Import-OnboardingReceipt -Path $PlanReceipt
    if([string]$plan.kind -cne 'PLAN' -or [string]$plan.application -cne $Application -or [string]$plan.profile -cne $Profile){throw 'PLAN_TARGET_MISMATCH'}
    if([DateTimeOffset]::Parse([string]$plan.expiresAtUtc) -lt [DateTimeOffset]::UtcNow){throw 'PLAN_EXPIRED'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeContract=Get-OnboardingHomeOperatorContract $context
    if ([string]$plan.operationContractVersion -cne [string]$homeContract.OperationContractVersion) { throw 'PLAN_CONTRACT_DRIFT' }
    if ([string]$context.Selected.Application.releaseBoundary -cne 'nonproduction-only' -or
        [string]$plan.releaseBoundary -cne [string]$context.Selected.Application.releaseBoundary -or
        [string]$plan.runtimeEnvironment -cne [string]$context.Selected.Profile.runtimeEnvironment) {
        throw 'UNSAFE_RELEASE_BOUNDARY'
    }
    if ([string]$context.Selected.Profile.storefront.mode -cne 'enabled') { throw 'SHOPIFY_STOREFRONT_DISABLED' }
    if ($IncludeAcceptanceProbe -and [string]$homeContract.ContractId -cne 'gate7-v1') { throw 'PROBE_NOT_SUPPORTED_FOR_HOME_CONTRACT' }
    $values=Get-OnboardingClientConfigurationValues -Context $context -ClientValues $ClientValues
    $homeSchema=Join-Path $RepositoryRoot $homeContract.SchemaRelativePath
    $currentDigests=@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingOperatorDigest $RepositoryRoot}
    foreach($key in $currentDigests.Keys){if([string]$plan.digests[$key] -cne [string]$currentDigests[$key]){throw 'PLAN_CONTRACT_DRIFT'}}
    if([string]$plan.verifiedTarget.shopId -cne [string]$context.Binding.shopify.shopId -or [string]$plan.verifiedTarget.adminShopDomain -cne [string]$context.Binding.shopify.adminShopDomain){throw 'PLAN_TARGET_DRIFT'}
    $lockDirectory=Join-Path $RepositoryRoot 'out\onboarding\locks';[IO.Directory]::CreateDirectory($lockDirectory)|Out-Null
    $lockPath=Join-Path $lockDirectory "shop-$($context.Binding.shopify.shopId).lock";$lock=$null
    try {
        try{$lock=[IO.File]::Open($lockPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)}catch{throw 'LOCAL_OPERATION_LOCKED'}
        $hasProbe=@($plan.actions|Where-Object{[string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'}).Count -gt 0
        if($hasProbe -ne [bool]$IncludeAcceptanceProbe){throw 'PROBE_CONFIRMATION_MISMATCH'}
        $admin=Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
        [void](Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport)
        [void](Get-ShopifyStorefrontTargetState $context.Selected $context.Binding ([string]$values['shopify.storefrontPublicToken']) $Transport)
        $homeState=Get-ShopifyHomeDefinitionState `
            $context.Binding $admin $Transport -ContractId $homeContract.ContractId
        $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
        $fingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint})
        if($fingerprint -cne [string]$plan.stateFingerprint){throw 'PLAN_STATE_DRIFT'}
        $probeIsAttributed=Test-OnboardingProbeAttribution -ReceiptPath $PriorReceipt -Context $context -ProbeState $probe -CurrentDigests $currentDigests
        $expectedActions=New-OnboardingExpectedActions -HomeState $homeState -ProbeState $probe -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -ProbeIsAttributed:$probeIsAttributed
        if((Get-OnboardingCanonicalJson (Get-OnboardingActionContractView $expectedActions)) -cne (Get-OnboardingCanonicalJson (Get-OnboardingActionContractView @($plan.actions)))){throw 'PLAN_ACTION_DRIFT'}
        $actions=@($plan.actions);$createdIds=@{}
        foreach($type in @(Get-OnboardingManagedHomeTypes -HomeState $homeState)){$existing=@($homeState.Definitions[$type]);if($existing.Count-eq 1){$createdIds[$type]=[string]$existing[0].id}}
        foreach($action in $actions|Sort-Object ordinal){
            if([string]$action.intendedAction -ceq 'NONE'){continue}
            if([string]$action.resourceKind -ceq 'SHOPIFY_HOME_DEFINITION'){
                $fresh=Get-ShopifyHomeDefinitionState `
                    $context.Binding $admin $Transport -ContractId $homeContract.ContractId
                if(@($fresh.Definitions[[string]$action.resourceKey]).Count -ne 0){throw 'LATER_STEP_DRIFT'}
                if([string]$fresh.Classification -eq 'INCOMPATIBLE'){throw 'LATER_STEP_DRIFT'}
                foreach($existingType in @($fresh.Definitions.Keys|Where-Object{@($fresh.Definitions[$_]).Count -eq 1})){
                    $existingId=[string]$fresh.Definitions[$existingType][0].id
                    if($createdIds.ContainsKey([string]$existingType)-and[string]$createdIds[[string]$existingType]-cne$existingId){throw 'LATER_STEP_DRIFT'}
                    $createdIds[[string]$existingType]=$existingId
                }
                $schema=Import-ShopifyHomeSchemaContract -ContractId $homeContract.ContractId
                $contract=@($schema.definitions|Where-Object{[string]$_.type -ceq [string]$action.resourceKey})[0]
                # Shopify owns the Admin access value for merchant-owned definitions. Gate 8
                # maps its semantic contract to Shopify's mutation representation, requests
                # Storefront readability, and validates the normalized Admin readback.
                $definition = ConvertTo-ShopifyHomeDefinitionCreateInput -Contract $contract -DefinitionIdsByType $createdIds
                Write-OnboardingReceipt "$PlanReceipt.intent-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action) -NoOverwrite
                try {
                    $created=New-ShopifyHomeDefinition $context.Binding $admin $definition $Transport
                    if([string]$created.id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$'){throw 'SHOPIFY_DEFINITION_READBACK_FAILED'}
                    $createdIds[[string]$action.resourceKey]=[string]$created.id
                    $readback=Get-ShopifyHomeDefinitionState `
                        $context.Binding $admin $Transport -ContractId $homeContract.ContractId
                    if([string]$readback.Classification -eq 'INCOMPATIBLE'-or@($readback.Definitions[[string]$action.resourceKey]).Count-ne 1){throw 'SHOPIFY_DEFINITION_READBACK_FAILED'}
                    $action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=[string]$readback.Fingerprint
                } catch {
                    $action.status='AMBIGUOUS';Write-OnboardingReceipt "$PlanReceipt.recovery-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action) -NoOverwrite;throw 'PARTIAL_APPLY'
                }
            } elseif([string]$action.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'){
                $fresh=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport;if([string]$fresh.Classification -cne 'ABSENT'){throw 'PROBE_COLLISION'}
                Write-OnboardingReceipt "$PlanReceipt.intent-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action) -NoOverwrite
                try {
                    $created=New-ShopifyAcceptanceProbe $context.Binding $admin $Transport
                    if([string]$created.id -cnotmatch '^gid://shopify/Metaobject/[0-9]+$'){throw 'SHOPIFY_PROBE_READBACK_FAILED'}
                    $readback=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
                    if([string]$readback.Classification -cne 'COMPATIBLE'-or[string]$readback.ResourceId-cne[string]$created.id){throw 'SHOPIFY_PROBE_READBACK_FAILED'}
                    $action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=[string]$readback.Fingerprint
                } catch {
                    $action.status='AMBIGUOUS';Write-OnboardingReceipt "$PlanReceipt.recovery-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action) -NoOverwrite;throw 'PARTIAL_APPLY'
                }
            }
        }
        $finalHome=$null;$finalProbe=$null
        try {
            [void](Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport)
            [void](Get-ShopifyStorefrontTargetState $context.Selected $context.Binding ([string]$values['shopify.storefrontPublicToken']) $Transport)
            $finalHome=Get-ShopifyHomeDefinitionState `
                $context.Binding $admin $Transport -ContractId $homeContract.ContractId
            $finalProbe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
            $homeCompatible = [string]$finalHome.Classification -ceq 'COMPATIBLE' -and @($finalHome.MissingTypes).Count -eq 0
            $probeAction = @($actions | Where-Object { [string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE' } | Select-Object -First 1)
            $probeCompatible = -not $IncludeAcceptanceProbe -or (
                $probeAction.Count -eq 1 -and
                [string]$finalProbe.Classification -ceq 'COMPATIBLE' -and
                [string]$finalProbe.ResourceId -ceq [string]$probeAction[0].providerResourceId
            )
            if (-not $homeCompatible -or -not $probeCompatible) { throw 'READBACK_MISMATCH' }
        } catch {
            $providerWriteOccurred = @(
                $actions | Where-Object {
                    [string]$_.intendedAction -ceq 'CREATE' -and [string]$_.status -ceq 'SUCCEEDED'
                }
            ).Count -gt 0
            if ($providerWriteOccurred) {
                $recovery = New-OnboardingFinalReadbackRecoverySnapshot `
                    -Plan $plan -Actions $actions -FinalHome $finalHome -FinalProbe $finalProbe `
                    -IncludeAcceptanceProbe:$IncludeAcceptanceProbe
                Write-OnboardingReceipt "$PlanReceipt.recovery-final.json" $recovery -NoOverwrite
                throw 'PARTIAL_APPLY'
            }
            throw
        }
        $plan.kind='RESULT';$plan.expiresAtUtc=$null;$plan.overallStatus='SUCCEEDED';$plan.actions=$actions
        $probeAction = @($actions | Where-Object { [string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE' } | Select-Object -First 1)
        $plan.readback=@(
            [ordered]@{surface='SHOPIFY_ADMIN';resourceKey='home-definitions';classification='PASS';identityFingerprint=[string]$finalHome.Fingerprint}
            if($IncludeAcceptanceProbe){[ordered]@{surface='SHOPIFY_ADMIN';resourceKey=[string]$probeAction[0].resourceKey;classification='PASS';identityFingerprint=[string]$finalProbe.Fingerprint}}
        )
        if([string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath=[IO.Path]::ChangeExtension($PlanReceipt,'.result.json')};Write-OnboardingReceipt $OutputPath $plan -NoOverwrite;return $plan
    } finally {
        if($null-ne$lock){$lock.Dispose();if(Test-Path -LiteralPath $lockPath){Remove-Item -LiteralPath $lockPath -Force}}
    }
}
function Write-OnboardingLocalConfiguration {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[switch]$ConfirmApply)
    if(-not $ConfirmApply){throw 'CONFIRMATION_MISMATCH'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $selected=$context.Selected
    $destination=Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot -RelativePath ([string]$selected.Profile.localConfiguration) -Field 'localConfiguration'
    Assert-OnboardingLocalWritePath $RepositoryRoot $destination 'localConfiguration'
    $map=[ordered]@{}
    if ([string]$selected.Profile.storefront.mode -ceq 'enabled') {
        $map['shopify.storefrontPublicToken']='STOREFRONT_PUBLIC_TOKEN'
    }
    if ([string]$selected.Profile.customerAccount.mode -ceq 'enabled-manual-registration') {
        $map['shopify.customerAccountClientId']='CUSTOMER_ACCOUNT_CLIENT_ID'
        $map['shopify.customerAccountIssuer']='CUSTOMER_ACCOUNT_ISSUER'
        $map['shopify.customerAccountAuthorizationEndpoint']='CUSTOMER_ACCOUNT_AUTHORIZATION_ENDPOINT'
        $map['shopify.customerAccountTokenEndpoint']='CUSTOMER_ACCOUNT_TOKEN_ENDPOINT'
        $map['shopify.customerAccountLogoutEndpoint']='CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT'
        $map['shopify.customerAccountGraphqlEndpoint']='CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT'
        $map['shopify.customerAccountRedirectUri']='CUSTOMER_ACCOUNT_REDIRECT_URI'
    }
    $values=[ordered]@{};foreach($entry in $map.GetEnumerator()){$name=Get-OnboardingCredentialName $Application $Profile ([string]$entry.Value);$values[$entry.Key]=[string](Get-OnboardingCredential $name)}
    $lines=Get-OnboardingValidatedClientConfigurationLines $context $values
    Write-OnboardingPrivateProperties $RepositoryRoot $destination $lines
    Write-Output 'PASS: scoped client configuration was written atomically; values were not printed.'
}
function Write-OnboardingManualCheckpoint {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[string]$EvidenceRef)
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $local=Read-OnboardingProperties -Path (Test-OnboardingSafeRelativePath $RepositoryRoot ([string]$context.Selected.Profile.localConfiguration) 'localConfiguration') -AllowedKeys @('shopify.storefrontPublicToken','shopify.customerAccountClientId','shopify.customerAccountIssuer','shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint','shopify.customerAccountLogoutEndpoint','shopify.customerAccountGraphqlEndpoint','shopify.customerAccountRedirectUri')
    [void](Get-OnboardingValidatedClientConfigurationLines -Context $context -Values $local)
    Assert-OnboardingText -Value $EvidenceRef -Field 'approvedEvidenceRef'
    if ($EvidenceRef -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$') { throw 'INVALID_APPROVED_EVIDENCE_REF' }
    $client=[string]$local['shopify.customerAccountClientId'];$callback=[string]$local['shopify.customerAccountRedirectUri'];$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($client))).ToLowerInvariant()
    $record=[ordered]@{schemaVersion=1;application=$Application;profile=$Profile;shopId=[string]$context.Binding.shopify.shopId;clientIdSha256=$hash;callback=$callback;recordedAtUtc=[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');approvedEvidenceRef=$EvidenceRef}
    $full=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'
    if (Test-Path -LiteralPath $full) { throw 'MANUAL_CHECKPOINT_EXISTS' }
    $parent=Split-Path -Parent $full;[IO.Directory]::CreateDirectory($parent)|Out-Null
    $temporary=Join-Path $parent ('.gate8-checkpoint-'+[guid]::NewGuid().ToString('N')+'.tmp')
    try {
        [IO.File]::WriteAllText($temporary,((Get-OnboardingCanonicalJson $record)+"`n"),[Text.UTF8Encoding]::new($false))
        [IO.File]::Move($temporary,$full,$false)
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
    Write-Output 'PASS: sanitized manual registration checkpoint recorded.'
}
function Invoke-OnboardingProbeRecovery {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$Application,
        [Parameter(Mandatory)][string]$Profile,
        [Parameter(Mandatory)][string]$PlanReceipt,
        [Parameter(Mandatory)][string]$RecoveryReceipt,
        [Parameter(Mandatory)][string]$OutputPath,
        [scriptblock]$Transport,
        [System.Collections.IDictionary]$ClientValues
    )

    $PlanReceipt = Resolve-OnboardingEvidencePath $RepositoryRoot $PlanReceipt 'PlanReceipt'
    $RecoveryReceipt = Resolve-OnboardingEvidencePath $RepositoryRoot $RecoveryReceipt 'RecoveryReceipt'
    $OutputPath = Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'
    if (Test-Path -LiteralPath $OutputPath) {
        throw 'PROBE_RECOVERY_OUTPUT_EXISTS'
    }
    $plan = Import-OnboardingReceipt -Path $PlanReceipt
    $recovery = Import-OnboardingReceipt -Path $RecoveryReceipt
    $context = Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeContract = Get-OnboardingHomeOperatorContract $context
    if ([string]$context.Selected.Application.releaseBoundary -cne 'nonproduction-only') {
        throw 'UNSAFE_RELEASE_BOUNDARY'
    }
    if ([string]$homeContract.ContractId -cne 'gate7-v1' -or
        [string]$plan.operationContractVersion -cne 'gate8-v1') {
        throw 'PROBE_RECOVERY_CONTRACT_MISMATCH'
    }
    $values = Get-OnboardingClientConfigurationValues -Context $context -ClientValues $ClientValues

    $sharedResourceGroup = [string]$context.Selected.Profile.storefront.sharedResourceGroup
    if ([string]::IsNullOrWhiteSpace($sharedResourceGroup) -or
        [string]$plan.kind -cne 'PLAN' -or
        [string]$plan.overallStatus -cne 'PLANNED' -or
        [string]$plan.application -cne $Application -or
        [string]$plan.profile -cne $Profile -or
        [string]$plan.runtimeEnvironment -cne [string]$context.Selected.Profile.runtimeEnvironment -or
        [string]$plan.releaseBoundary -cne [string]$context.Selected.Application.releaseBoundary) {
        throw 'PROBE_RECOVERY_TARGET_MISMATCH'
    }

    $expectedTarget = [ordered]@{
        shopId = [string]$context.Binding.shopify.shopId
        adminShopDomain = [string]$context.Binding.shopify.adminShopDomain
        firebaseProjectId = if ($null -ne $context.Binding.firebase) { [string]$context.Binding.firebase.projectId } else { $null }
        firebaseProjectNumber = if ($null -ne $context.Binding.firebase) { [string]$context.Binding.firebase.projectNumber } else { $null }
    }
    if ((Get-OnboardingCanonicalJson $plan.verifiedTarget) -cne (Get-OnboardingCanonicalJson $expectedTarget)) {
        throw 'PROBE_RECOVERY_TARGET_MISMATCH'
    }

    $homeSchema = Join-Path $RepositoryRoot $homeContract.SchemaRelativePath
    $currentDigests = [ordered]@{
        registrySha256 = Get-OnboardingSha256 $context.RegistryPath
        providerBindingSha256 = Get-OnboardingSha256 $context.BindingPath
        homeSchemaSha256 = Get-OnboardingSha256 $homeSchema
        operatorSha256 = Get-OnboardingOperatorDigest $RepositoryRoot
    }
    foreach ($key in @('registrySha256', 'providerBindingSha256', 'homeSchemaSha256')) {
        if ([string]$plan.digests[$key] -cne [string]$currentDigests[$key]) {
            throw 'PROBE_RECOVERY_CONTRACT_MISMATCH'
        }
    }
    $sourceOperatorDigest = [string]$plan.digests.operatorSha256
    if ($sourceOperatorDigest -cne $script:ProbeRecoveryPredecessorOperatorSha256 -and
        $sourceOperatorDigest -cne [string]$currentDigests.operatorSha256) {
        throw 'PROBE_RECOVERY_CONTRACT_MISMATCH'
    }

    $actions = @($plan.actions)
    if ($actions.Count -ne 1) { throw 'PROBE_RECOVERY_ACTION_MISMATCH' }
    $action = $actions[0]
    if ([int]$action.ordinal -ne 1 -or
        [string]$action.resourceKind -cne 'SHOPIFY_HOME_ACCEPTANCE_PROBE' -or
        [string]$action.resourceKey -cne 'gate8-operator-acceptance-v1' -or
        [string]$action.managementMode -cne 'PROBE_CREATE_IF_MISSING' -or
        [string]$action.beforeClassification -cne 'ABSENT' -or
        [string]$action.intendedAction -cne 'CREATE' -or
        [string]$action.status -cne 'PLANNED' -or
        $null -ne $action.providerResourceId -or
        [string]$action.afterClassification -cne 'UNKNOWN' -or
        [string]$action.afterFingerprint -cne ('0' * 64) -or
        @($plan.diagnosticCodes).Count -ne 0 -or
        @($plan.readback).Count -ne 0 -or
        @($plan.recovery).Count -ne 0) {
        throw 'PROBE_RECOVERY_ACTION_MISMATCH'
    }
    $absentProbeFingerprint = Get-ObjectSha ([ordered]@{ probe = $null })
    if ([string]$action.beforeFingerprint -cne $absentProbeFingerprint) {
        throw 'PROBE_RECOVERY_ACTION_MISMATCH'
    }

    $intentPath = "$PlanReceipt.intent-$([int]$action.ordinal).json"
    $intent = Import-OnboardingReceipt -Path $intentPath
    $expectedIntent = New-OnboardingRecoverySnapshot -Plan $plan -Actions $actions -Action $action
    if ((Get-OnboardingCanonicalJson $intent) -cne (Get-OnboardingCanonicalJson $expectedIntent)) {
        throw 'PROBE_RECOVERY_EVIDENCE_MISMATCH:INTENT'
    }
    $ambiguousActions = @((Get-OnboardingCanonicalJson $actions) | ConvertFrom-Json -AsHashtable -Depth 32)
    $ambiguousActions[0].status = 'AMBIGUOUS'
    $expectedRecovery = New-OnboardingRecoverySnapshot `
        -Plan $plan `
        -Actions $ambiguousActions `
        -Action $ambiguousActions[0]
    if ((Get-OnboardingCanonicalJson $recovery) -cne (Get-OnboardingCanonicalJson $expectedRecovery)) {
        throw 'PROBE_RECOVERY_EVIDENCE_MISMATCH:RECOVERY'
    }

    $admin = Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
    [void](Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport)
    [void](Get-ShopifyStorefrontTargetState $context.Selected $context.Binding ([string]$values['shopify.storefrontPublicToken']) $Transport)
    $homeState = Get-ShopifyHomeDefinitionState `
        $context.Binding $admin $Transport -ContractId $homeContract.ContractId
    $menuState = Get-ShopifyMenuState `
        $context.Binding `
        $admin `
        ([string]$context.Selected.Profile.storefront.catalog.menuHandle) `
        $Transport
    $probeState = Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
    $selectedHomeHandle = [string]$context.Selected.Profile.storefront.home.rootHandle
    if ([string]$homeState.Classification -cne 'COMPATIBLE' -or
        @($homeState.MissingTypes).Count -ne 0 -or
        [string]$menuState.Classification -cne 'CORRECT' -or
        [string]$probeState.Classification -cne 'COMPATIBLE' -or
        [string]$probeState.ResourceId -cnotmatch '^gid://shopify/Metaobject/[0-9]+$' -or
        [string]::IsNullOrWhiteSpace($selectedHomeHandle) -or
        $selectedHomeHandle -ceq 'gate8-operator-acceptance-v1') {
        throw 'PROBE_RECOVERY_CURRENT_STATE_MISMATCH'
    }
    $expectedOriginalState = Get-ObjectSha ([ordered]@{
        home = [string]$homeState.Fingerprint
        probe = $absentProbeFingerprint
    })
    if ([string]$plan.stateFingerprint -cne $expectedOriginalState) {
        throw 'PROBE_RECOVERY_EVIDENCE_MISMATCH'
    }

    $created = [DateTimeOffset]::UtcNow.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    $recoveredAction = (Get-OnboardingCanonicalJson $action) | ConvertFrom-Json -AsHashtable -Depth 32
    $recoveredAction.providerResourceId = [string]$probeState.ResourceId
    $recoveredAction.status = 'SUCCEEDED'
    $recoveredAction.afterClassification = 'CORRECT'
    $recoveredAction.afterFingerprint = [string]$probeState.Fingerprint
    $result = [ordered]@{
        receiptSchemaVersion = 1
        operationContractVersion = 'gate8-v1'
        kind = 'RESULT'
        application = $Application
        profile = $Profile
        runtimeEnvironment = [string]$context.Selected.Profile.runtimeEnvironment
        releaseBoundary = [string]$context.Selected.Application.releaseBoundary
        createdAtUtc = $created
        expiresAtUtc = $null
        verifiedTarget = $expectedTarget
        digests = $currentDigests
        stateFingerprint = Get-ObjectSha ([ordered]@{
            home = [string]$homeState.Fingerprint
            probe = [string]$probeState.Fingerprint
        })
        actions = @($recoveredAction)
        overallStatus = 'SUCCEEDED'
        diagnosticCodes = @()
        readback = @(
            [ordered]@{ surface = 'SHOPIFY_ADMIN'; resourceKey = 'home-definitions'; classification = 'PASS'; identityFingerprint = [string]$homeState.Fingerprint },
            [ordered]@{ surface = 'SHOPIFY_ADMIN'; resourceKey = [string]$context.Selected.Profile.storefront.catalog.menuHandle; classification = 'PASS'; identityFingerprint = [string]$menuState.Fingerprint },
            [ordered]@{ surface = 'SHOPIFY_ADMIN'; resourceKey = [string]$action.resourceKey; classification = 'PASS'; identityFingerprint = [string]$probeState.Fingerprint }
        )
        recovery = @([ordered]@{
            ordinal = 1
            resourceKind = 'SHOPIFY_HOME_ACCEPTANCE_PROBE'
            resourceKey = [string]$action.resourceKey
            classification = 'COMPATIBLE'
            nextAction = 'NO_ACTION'
        })
    }
    Write-OnboardingReceipt $OutputPath $result -NoOverwrite
    return $result
}
function Get-OnboardingRecovery { param([string]$RepositoryRoot,[string]$PlanReceipt) $path=Resolve-OnboardingEvidencePath $RepositoryRoot $PlanReceipt 'PlanReceipt';$receipt=Import-OnboardingReceipt -Path $path;[pscustomobject]@{application=$receipt.application;profile=$receipt.profile;kind=$receipt.kind;overallStatus=$receipt.overallStatus;nextAction='REINSPECT'} }
Export-ModuleMember -Function @('Invoke-OnboardingInspect','Invoke-OnboardingReadback','New-OnboardingPlan','Invoke-OnboardingApply','Invoke-OnboardingProbeRecovery','Write-OnboardingLocalConfiguration','Write-OnboardingManualCheckpoint','Get-OnboardingRecovery','Get-OnboardingOperatorContext','Get-OnboardingCredentialName','Get-OnboardingCredential','Write-OnboardingReceipt','Get-OnboardingValidatedClientConfigurationLines','Write-OnboardingPrivateProperties','Assert-OnboardingLocalWritePath','Set-OnboardingPrivateFilePermissions','Get-OnboardingOperatorDigest')
