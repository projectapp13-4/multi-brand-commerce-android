Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Registry.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Shopify.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.CustomerAccount.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Firebase.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Onboarding.AppLinks.psm1') -Force

function Get-OnboardingCredentialName { param([string]$Application,[string]$Profile,[string]$Suffix) ('MB_{0}_{1}_{2}' -f $Application.Replace('-','_'),$Profile.Replace('-','_'),$Suffix).ToUpperInvariant() }
function Get-OnboardingCredential { param([string]$Name) [Environment]::GetEnvironmentVariable($Name, 'Process') }
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
        [string]$receipt.profile -cne [string]$Context.Selected.Profile.key -or
        [string]$receipt.verifiedTarget.shopId -cne [string]$Context.Binding.shopify.shopId -or
        [string]$receipt.verifiedTarget.adminShopDomain -cne [string]$Context.Binding.shopify.adminShopDomain) {
        return $false
    }
    foreach ($key in $CurrentDigests.Keys) {
        if ([string]$receipt.digests[$key] -cne [string]$CurrentDigests[$key]) { return $false }
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
    foreach ($type in @('mobile_home_collection_grid', 'mobile_home_featured_product', 'mobile_home')) {
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
    return @($actions.ToArray())
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
        & icacls.exe $Path '/inheritance:r' '/grant:r' "${sid}:(F)" | Out-Null
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
    return $uri
}
function Write-OnboardingReceipt { param([string]$Path,$Receipt)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'RECEIPT_PATH_REQUIRED' }
    $full=[IO.Path]::GetFullPath($Path); $parent=Split-Path -Parent $full; [IO.Directory]::CreateDirectory($parent)|Out-Null
    $json=Get-OnboardingCanonicalJson $Receipt; [IO.File]::WriteAllText($full,$json+"`n",[Text.UTF8Encoding]::new($false)); [void](Import-OnboardingReceipt -Path $full)
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
    [pscustomobject]@{Registry=$registry;Selected=$selected;Binding=$binding;RegistryPath=$registryPath;BindingPath=$bindingPath}
}
function Invoke-OnboardingInspect {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[scriptblock]$Transport)
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $adminName=Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN'; $admin=Get-OnboardingCredential $adminName
    $firebaseName=Get-OnboardingCredentialName $Application $Profile 'FIREBASE_ACCESS_TOKEN'; $firebase=Get-OnboardingCredential $firebaseName
    $result=[ordered]@{application=$Application;profile=$Profile;runtimeEnvironment=[string]$context.Selected.Profile.runtimeEnvironment;shopId=[string]$context.Binding.shopify.shopId}
    if ([string]::IsNullOrWhiteSpace($admin)) { $result.menu='EXTERNALLY_BLOCKED';$result.homeDefinitions='EXTERNALLY_BLOCKED';$result.acceptanceProbe='EXTERNALLY_BLOCKED' }
    else {
        $result.shopifyTarget=(Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport).Classification
        $menu=Get-ShopifyMenuState $context.Binding $admin ([string]$context.Selected.Profile.storefront.catalog.menuHandle) $Transport
        $homeState=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
        $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
        $result.menu=$menu.Classification;$result.homeDefinitions=$homeState.Classification;$result.acceptanceProbe=$probe.Classification
    }
    try { $result.customer=(Get-OnboardingCustomerDiscovery $context.Selected $context.Binding $Transport).Classification } catch { $result.customer='EXTERNALLY_BLOCKED' }
    try { $result.assetLinks=(Get-OnboardingAssetLinksState $context.Selected $Transport).Classification } catch { $result.assetLinks='EXTERNALLY_BLOCKED' }
    if ([string]::IsNullOrWhiteSpace($firebase)){$result.firebase='EXTERNALLY_BLOCKED'}else{try{$result.firebase=(Get-OnboardingFirebaseState $context.Selected $context.Binding $firebase $Transport).Classification}catch{$result.firebase='FAIL'}}
    return [pscustomobject]$result
}
function New-OnboardingPlan {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[switch]$IncludeAcceptanceProbe,[string]$PriorReceipt,[scriptblock]$Transport)
    $created=[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');$expires=[DateTimeOffset]::UtcNow.AddMinutes(15).ToString('yyyy-MM-ddTHH:mm:ssZ')
    if([string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath="out/onboarding/$created-$Application-$Profile-plan.json" -replace ':',''}
    $OutputPath=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'
    if(-not[string]::IsNullOrWhiteSpace($PriorReceipt)){$PriorReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PriorReceipt 'PriorReceipt'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    if ([string]$context.Selected.Application.releaseBoundary -cne 'nonproduction-only') { throw 'UNSAFE_RELEASE_BOUNDARY' }
    $admin=Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
    [void](Get-ShopifyVerifiedTargetState $context.Binding $admin $Transport)
    $homeState=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
    $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
    $homeSchema=Join-Path $RepositoryRoot 'config\onboarding\shopify-home-schema.v1.json';$operatorPath=Join-Path $RepositoryRoot 'scripts\onboarding\Onboarding.Operator.psm1'
    $digests=[ordered]@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingSha256 $operatorPath}
    $probeIsAttributed=Test-OnboardingProbeAttribution -ReceiptPath $PriorReceipt -Context $context -ProbeState $probe -CurrentDigests $digests
    $actions=New-OnboardingExpectedActions -HomeState $homeState -ProbeState $probe -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -ProbeIsAttributed:$probeIsAttributed
    $receipt=[ordered]@{receiptSchemaVersion=1;operationContractVersion='gate8-v1';kind='PLAN';application=$Application;profile=$Profile;runtimeEnvironment=[string]$context.Selected.Profile.runtimeEnvironment;releaseBoundary=[string]$context.Selected.Application.releaseBoundary;createdAtUtc=$created;expiresAtUtc=$expires;verifiedTarget=[ordered]@{shopId=[string]$context.Binding.shopify.shopId;adminShopDomain=[string]$context.Binding.shopify.adminShopDomain;firebaseProjectId=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectId}else{$null};firebaseProjectNumber=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectNumber}else{$null}};digests=$digests;stateFingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint});actions=$actions;overallStatus='PLANNED';diagnosticCodes=@();readback=@();recovery=@()}
    Write-OnboardingReceipt $OutputPath $receipt;return $receipt
}
function Invoke-OnboardingApply {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$PlanReceipt,[string]$ConfirmApplication,[string]$ConfirmProfile,[switch]$ConfirmApply,[switch]$IncludeAcceptanceProbe,[string]$PriorReceipt,[string]$OutputPath,[scriptblock]$Transport)
    if(-not $ConfirmApply -or $ConfirmApplication -cne $Application -or $ConfirmProfile -cne $Profile){throw 'CONFIRMATION_MISMATCH'}
    $PlanReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PlanReceipt 'PlanReceipt'
    if(-not[string]::IsNullOrWhiteSpace($PriorReceipt)){$PriorReceipt=Resolve-OnboardingEvidencePath $RepositoryRoot $PriorReceipt 'PriorReceipt'}
    if(-not[string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath'}
    $plan=Import-OnboardingReceipt -Path $PlanReceipt
    if([string]$plan.kind -cne 'PLAN' -or [string]$plan.application -cne $Application -or [string]$plan.profile -cne $Profile){throw 'PLAN_TARGET_MISMATCH'}
    if([DateTimeOffset]::Parse([string]$plan.expiresAtUtc) -lt [DateTimeOffset]::UtcNow){throw 'PLAN_EXPIRED'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeSchema=Join-Path $RepositoryRoot 'config\onboarding\shopify-home-schema.v1.json';$operatorPath=Join-Path $RepositoryRoot 'scripts\onboarding\Onboarding.Operator.psm1'
    $currentDigests=@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingSha256 $operatorPath}
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
        $homeState=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport;$probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
        $fingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint})
        if($fingerprint -cne [string]$plan.stateFingerprint){throw 'PLAN_STATE_DRIFT'}
        $probeIsAttributed=Test-OnboardingProbeAttribution -ReceiptPath $PriorReceipt -Context $context -ProbeState $probe -CurrentDigests $currentDigests
        $expectedActions=New-OnboardingExpectedActions -HomeState $homeState -ProbeState $probe -IncludeAcceptanceProbe:$IncludeAcceptanceProbe -ProbeIsAttributed:$probeIsAttributed
        if((Get-OnboardingCanonicalJson (Get-OnboardingActionContractView $expectedActions)) -cne (Get-OnboardingCanonicalJson (Get-OnboardingActionContractView @($plan.actions)))){throw 'PLAN_ACTION_DRIFT'}
        $actions=@($plan.actions);$createdIds=@{}
        foreach($type in @('mobile_home_collection_grid','mobile_home_featured_product','mobile_home')){$existing=@($homeState.Definitions[$type]);if($existing.Count-eq 1){$createdIds[$type]=[string]$existing[0].id}}
        foreach($action in $actions|Sort-Object ordinal){
            if([string]$action.intendedAction -ceq 'NONE'){continue}
            if([string]$action.resourceKind -ceq 'SHOPIFY_HOME_DEFINITION'){
                $fresh=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
                if(@($fresh.Definitions[[string]$action.resourceKey]).Count -ne 0){throw 'LATER_STEP_DRIFT'}
                if([string]$fresh.Classification -eq 'INCOMPATIBLE'){throw 'LATER_STEP_DRIFT'}
                foreach($existingType in @($fresh.Definitions.Keys|Where-Object{@($fresh.Definitions[$_]).Count -eq 1})){
                    $existingId=[string]$fresh.Definitions[$existingType][0].id
                    if($createdIds.ContainsKey([string]$existingType)-and[string]$createdIds[[string]$existingType]-cne$existingId){throw 'LATER_STEP_DRIFT'}
                    $createdIds[[string]$existingType]=$existingId
                }
                $schema=Import-ShopifyHomeSchemaContract
                $contract=@($schema.definitions|Where-Object{[string]$_.type -ceq [string]$action.resourceKey})[0]
                $fields = @(
                    foreach ($fieldContract in @($contract.fields)) {
                        $validations = @(
                            foreach ($entry in $fieldContract.validations.GetEnumerator()) {
                                if ([string]$entry.Key -cne 'definitionTypes') {
                                    @{ name = [string]$entry.Key; value = [string]$entry.Value }
                                }
                            }
                        )
                        if ($fieldContract.validations.Contains('definitionTypes')) {
                            foreach ($childType in @($fieldContract.validations.definitionTypes)) {
                                $validations += @{ name = 'metaobject_definition_id'; value = [string]$createdIds[[string]$childType] }
                            }
                        }
                        @{ name = ([string]$fieldContract.key -replace '_', ' '); key = [string]$fieldContract.key; type = [string]$fieldContract.type; required = [bool]$fieldContract.required; validations = $validations }
                    }
                )
                $definition=@{name=([string]$contract.type -replace '_',' ');type=[string]$contract.type;displayNameKey=[string]$contract.displayNameKey;access=@{admin='MERCHANT_READ_WRITE';storefront='PUBLIC_READ'};capabilities=@{publishable=@{enabled=$true}};fieldDefinitions=$fields}
                Write-OnboardingReceipt "$PlanReceipt.intent-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action)
                try {
                    $created=New-ShopifyHomeDefinition $context.Binding $admin $definition $Transport;$createdIds[[string]$action.resourceKey]=[string]$created.id
                    $readback=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
                    if([string]$readback.Classification -eq 'INCOMPATIBLE'-or@($readback.Definitions[[string]$action.resourceKey]).Count-ne 1){throw 'SHOPIFY_DEFINITION_READBACK_FAILED'}
                    $action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=[string]$readback.Fingerprint
                } catch {
                    $action.status='AMBIGUOUS';Write-OnboardingReceipt "$PlanReceipt.recovery-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action);throw 'PARTIAL_APPLY'
                }
            } elseif([string]$action.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'){
                $fresh=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport;if([string]$fresh.Classification -cne 'ABSENT'){throw 'PROBE_COLLISION'}
                Write-OnboardingReceipt "$PlanReceipt.intent-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action)
                try {
                    $created=New-ShopifyAcceptanceProbe $context.Binding $admin $Transport
                    $readback=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
                    if([string]$readback.Classification -cne 'COMPATIBLE'-or[string]$readback.ResourceId-cne[string]$created.id){throw 'SHOPIFY_PROBE_READBACK_FAILED'}
                    $action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=[string]$readback.Fingerprint
                } catch {
                    $action.status='AMBIGUOUS';Write-OnboardingReceipt "$PlanReceipt.recovery-$($action.ordinal).json" (New-OnboardingRecoverySnapshot $plan $actions $action);throw 'PARTIAL_APPLY'
                }
            }
        }
        $finalHome=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport;$finalProbe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
        $plan.kind='RESULT';$plan.expiresAtUtc=$null;$plan.overallStatus='SUCCEEDED';$plan.actions=$actions
        $probeAction = @($actions | Where-Object { [string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE' } | Select-Object -First 1)
        $plan.readback=@(
            [ordered]@{surface='SHOPIFY_ADMIN';resourceKey='home-definitions';classification=if(@($finalHome.MissingTypes).Count-eq 0){'PASS'}else{'PARTIAL'};identityFingerprint=[string]$finalHome.Fingerprint}
            if($IncludeAcceptanceProbe){[ordered]@{surface='SHOPIFY_ADMIN';resourceKey=[string]$probeAction[0].resourceKey;classification=if([string]$finalProbe.Classification-ceq'COMPATIBLE'){'PASS'}else{'FAIL'};identityFingerprint=[string]$finalProbe.Fingerprint}}
        )
        if([string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath=[IO.Path]::ChangeExtension($PlanReceipt,'.result.json')};Write-OnboardingReceipt $OutputPath $plan;return $plan
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
    $map=[ordered]@{
      'shopify.storefrontPublicToken'='STOREFRONT_PUBLIC_TOKEN';'shopify.customerAccountClientId'='CUSTOMER_ACCOUNT_CLIENT_ID';'shopify.customerAccountIssuer'='CUSTOMER_ACCOUNT_ISSUER';'shopify.customerAccountAuthorizationEndpoint'='CUSTOMER_ACCOUNT_AUTHORIZATION_ENDPOINT';'shopify.customerAccountTokenEndpoint'='CUSTOMER_ACCOUNT_TOKEN_ENDPOINT';'shopify.customerAccountLogoutEndpoint'='CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT';'shopify.customerAccountGraphqlEndpoint'='CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT';'shopify.customerAccountRedirectUri'='CUSTOMER_ACCOUNT_REDIRECT_URI'
    }
    $values=[ordered]@{};foreach($entry in $map.GetEnumerator()){$name=Get-OnboardingCredentialName $Application $Profile ([string]$entry.Value);$value=Get-OnboardingCredential $name;if([string]::IsNullOrWhiteSpace($value)-or$value-match '[\r\n]'){throw 'MISSING_OR_UNSAFE_CLIENT_CONFIGURATION'};$values[$entry.Key]=[string]$value}
    Assert-OnboardingVisibleAsciiValue $values['shopify.storefrontPublicToken'] 'shopify.storefrontPublicToken' 4096
    Assert-OnboardingVisibleAsciiValue $values['shopify.customerAccountClientId'] 'shopify.customerAccountClientId' 1024
    $expectedIssuer="https://shopify.com/authentication/$($context.Binding.shopify.shopId)"
    if($values['shopify.customerAccountIssuer']-cne$expectedIssuer){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountIssuer'}
    foreach($key in @('shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint','shopify.customerAccountLogoutEndpoint')){[void](Assert-OnboardingHttpsClientEndpoint $values[$key] $key)}
    $graphql=[uri](Assert-OnboardingHttpsClientEndpoint $values['shopify.customerAccountGraphqlEndpoint'] 'shopify.customerAccountGraphqlEndpoint')
    $apiVersion=[string]$context.Registry.providerContracts.shopifyCustomerAccountApiVersion
    if(-not$graphql.AbsolutePath.EndsWith("/$apiVersion/graphql",[StringComparison]::Ordinal)){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountGraphqlEndpoint'}
    $identity=$selected.Application.identity.customerAccount
    $expectedCallback="shop.$($context.Binding.shopify.shopId).$($identity.callbackSchemeSuffix)://$($identity.callbackHost)$($identity.callbackPath)"
    if($values['shopify.customerAccountRedirectUri']-cne$expectedCallback){throw 'UNSAFE_CLIENT_CONFIGURATION:shopify.customerAccountRedirectUri'}
    $lines=@($map.Keys|ForEach-Object{"$_=$($values[$_])"})
    $parent=Split-Path -Parent $destination;[IO.Directory]::CreateDirectory($parent)|Out-Null;Assert-OnboardingLocalWritePath $RepositoryRoot $destination 'localConfiguration';$temporary=Join-Path $parent ('.gate8-'+[guid]::NewGuid().ToString('N')+'.tmp')
    $backup="$destination.bak";Assert-OnboardingLocalWritePath $RepositoryRoot $backup 'localConfigurationBackup'
    if((Test-Path -LiteralPath $destination)-and(Test-Path -LiteralPath $backup)){throw 'LOCAL_CONFIGURATION_BACKUP_EXISTS'}
    try{
        [IO.File]::WriteAllText($temporary,(($lines-join"`n")+"`n"),[Text.UTF8Encoding]::new($false));Set-OnboardingPrivateFilePermissions $temporary
        [void](Read-OnboardingProperties -Path $temporary -AllowedKeys @($map.Keys))
        if(Test-Path -LiteralPath $destination){[IO.File]::Replace($temporary,$destination,$backup,$true);Set-OnboardingPrivateFilePermissions $backup}else{[IO.File]::Move($temporary,$destination)}
        Set-OnboardingPrivateFilePermissions $destination
    }finally{if(Test-Path $temporary){Remove-Item -LiteralPath $temporary -Force}}
    Write-Output 'PASS: scoped client configuration was written atomically; values were not printed.'
}
function Write-OnboardingManualCheckpoint {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[string]$EvidenceRef)
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $local=Read-OnboardingProperties -Path (Test-OnboardingSafeRelativePath $RepositoryRoot ([string]$context.Selected.Profile.localConfiguration) 'localConfiguration') -AllowedKeys @('shopify.storefrontPublicToken','shopify.customerAccountClientId','shopify.customerAccountIssuer','shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint','shopify.customerAccountLogoutEndpoint','shopify.customerAccountGraphqlEndpoint','shopify.customerAccountRedirectUri')
    $client=[string]$local['shopify.customerAccountClientId'];$callback=[string]$local['shopify.customerAccountRedirectUri'];$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($client))).ToLowerInvariant()
    $record=[ordered]@{schemaVersion=1;application=$Application;profile=$Profile;shopId=[string]$context.Binding.shopify.shopId;clientIdSha256=$hash;callback=$callback;recordedAtUtc=[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');approvedEvidenceRef=$EvidenceRef}
    $full=Resolve-OnboardingEvidencePath $RepositoryRoot $OutputPath 'OutputPath';[IO.Directory]::CreateDirectory((Split-Path -Parent $full))|Out-Null;[IO.File]::WriteAllText($full,((Get-OnboardingCanonicalJson $record)+"`n"),[Text.UTF8Encoding]::new($false));Write-Output 'PASS: sanitized manual registration checkpoint recorded.'
}
function Get-OnboardingRecovery { param([string]$RepositoryRoot,[string]$PlanReceipt) $path=Resolve-OnboardingEvidencePath $RepositoryRoot $PlanReceipt 'PlanReceipt';$receipt=Import-OnboardingReceipt -Path $path;[pscustomobject]@{application=$receipt.application;profile=$receipt.profile;kind=$receipt.kind;overallStatus=$receipt.overallStatus;nextAction='REINSPECT'} }
Export-ModuleMember -Function @('Invoke-OnboardingInspect','New-OnboardingPlan','Invoke-OnboardingApply','Write-OnboardingLocalConfiguration','Write-OnboardingManualCheckpoint','Get-OnboardingRecovery','Get-OnboardingOperatorContext','Get-OnboardingCredentialName','Get-OnboardingCredential','Write-OnboardingReceipt')
