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
function Write-OnboardingReceipt { param([string]$Path,$Receipt)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'RECEIPT_PATH_REQUIRED' }
    $full=[IO.Path]::GetFullPath($Path); $parent=Split-Path -Parent $full; [IO.Directory]::CreateDirectory($parent)|Out-Null
    $json=($Receipt|ConvertTo-Json -Depth 32); [IO.File]::WriteAllText($full,$json+"`n",[Text.UTF8Encoding]::new($false)); [void](Import-OnboardingReceipt -Path $full)
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
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[switch]$IncludeAcceptanceProbe,[scriptblock]$Transport)
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    if ([string]$context.Selected.Application.releaseBoundary -cne 'nonproduction-only') { throw 'UNSAFE_RELEASE_BOUNDARY' }
    $admin=Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
    $homeState=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
    $probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
    $actions=[Collections.Generic.List[object]]::new();$ordinal=1
    if ($homeState.Classification -ceq 'ABSENT') { foreach($type in @('mobile_home_collection_grid','mobile_home_featured_product','mobile_home')){$actions.Add((New-ReceiptAction $ordinal 'SHOPIFY_HOME_DEFINITION' $type 'CREATE_IF_MISSING' 'ABSENT' 'CREATE' $homeState.Fingerprint));$ordinal++} }
    elseif ($homeState.Classification -cne 'COMPATIBLE') { throw 'INCOMPATIBLE_HOME_DEFINITIONS' }
    if($IncludeAcceptanceProbe){if($probe.Classification -ceq 'ABSENT'){$actions.Add((New-ReceiptAction $ordinal 'SHOPIFY_HOME_ACCEPTANCE_PROBE' 'gate8-operator-acceptance-v1' 'PROBE_CREATE_IF_MISSING' 'ABSENT' 'CREATE' $probe.Fingerprint))}elseif($probe.Classification -ceq 'COMPATIBLE'){$actions.Add((New-ReceiptAction $ordinal 'SHOPIFY_HOME_ACCEPTANCE_PROBE' 'gate8-operator-acceptance-v1' 'PROBE_CREATE_IF_MISSING' 'COMPATIBLE' 'NONE' $probe.Fingerprint))}else{throw 'PROBE_COLLISION'}}
    $created=[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');$expires=[DateTimeOffset]::UtcNow.AddMinutes(15).ToString('yyyy-MM-ddTHH:mm:ssZ')
    $homeSchema=Join-Path $RepositoryRoot 'config\onboarding\shopify-home-schema.v1.json';$operatorPath=Join-Path $RepositoryRoot 'scripts\onboarding\Onboarding.Operator.psm1'
    $receipt=[ordered]@{receiptSchemaVersion=1;operationContractVersion='gate8-v1';kind='PLAN';application=$Application;profile=$Profile;runtimeEnvironment=[string]$context.Selected.Profile.runtimeEnvironment;releaseBoundary=[string]$context.Selected.Application.releaseBoundary;createdAtUtc=$created;expiresAtUtc=$expires;verifiedTarget=[ordered]@{shopId=[string]$context.Binding.shopify.shopId;adminShopDomain=[string]$context.Binding.shopify.adminShopDomain;firebaseProjectId=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectId}else{$null};firebaseProjectNumber=if($null-ne$context.Binding.firebase){[string]$context.Binding.firebase.projectNumber}else{$null}};digests=[ordered]@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingSha256 $operatorPath};stateFingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint});actions=$actions.ToArray();overallStatus='PLANNED';diagnosticCodes=@();readback=@();recovery=@()}
    Write-OnboardingReceipt $OutputPath $receipt;return $receipt
}
function Invoke-OnboardingApply {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$PlanReceipt,[string]$ConfirmApplication,[string]$ConfirmProfile,[switch]$ConfirmApply,[switch]$IncludeAcceptanceProbe,[string]$OutputPath,[scriptblock]$Transport)
    if(-not $ConfirmApply -or $ConfirmApplication -cne $Application -or $ConfirmProfile -cne $Profile){throw 'CONFIRMATION_MISMATCH'}
    $plan=Import-OnboardingReceipt -Path $PlanReceipt
    if([string]$plan.kind -cne 'PLAN' -or [string]$plan.application -cne $Application -or [string]$plan.profile -cne $Profile){throw 'PLAN_TARGET_MISMATCH'}
    if([DateTimeOffset]::Parse([string]$plan.expiresAtUtc) -lt [DateTimeOffset]::UtcNow){throw 'PLAN_EXPIRED'}
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $homeSchema=Join-Path $RepositoryRoot 'config\onboarding\shopify-home-schema.v1.json';$operatorPath=Join-Path $RepositoryRoot 'scripts\onboarding\Onboarding.Operator.psm1'
    $currentDigests=@{registrySha256=Get-OnboardingSha256 $context.RegistryPath;providerBindingSha256=Get-OnboardingSha256 $context.BindingPath;homeSchemaSha256=Get-OnboardingSha256 $homeSchema;operatorSha256=Get-OnboardingSha256 $operatorPath}
    foreach($key in $currentDigests.Keys){if([string]$plan.digests[$key] -cne [string]$currentDigests[$key]){throw 'PLAN_CONTRACT_DRIFT'}}
    if([string]$plan.verifiedTarget.shopId -cne [string]$context.Binding.shopify.shopId -or [string]$plan.verifiedTarget.adminShopDomain -cne [string]$context.Binding.shopify.adminShopDomain){throw 'PLAN_TARGET_DRIFT'}
    $hasProbe=@($plan.actions|Where-Object{[string]$_.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'}).Count -gt 0
    if($hasProbe -ne [bool]$IncludeAcceptanceProbe){throw 'PROBE_CONFIRMATION_MISMATCH'}
    $admin=Get-OnboardingCredential (Get-OnboardingCredentialName $Application $Profile 'SHOPIFY_ADMIN_TOKEN')
    $homeState=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport;$probe=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport
    $fingerprint=Get-ObjectSha ([ordered]@{home=$homeState.Fingerprint;probe=$probe.Fingerprint})
    if($fingerprint -cne [string]$plan.stateFingerprint){throw 'PLAN_STATE_DRIFT'}
    $actions=@($plan.actions);$createdIds=@{}
    foreach($action in $actions|Sort-Object ordinal){
        if([string]$action.intendedAction -ceq 'NONE'){continue}
        $intentPath="$PlanReceipt.intent-$($action.ordinal).json";[IO.File]::WriteAllText($intentPath,('{"ordinal":'+$action.ordinal+',"stateFingerprint":"'+$fingerprint+'"}'+"`n"),[Text.UTF8Encoding]::new($false))
        if([string]$action.resourceKind -ceq 'SHOPIFY_HOME_DEFINITION'){
            $fresh=Get-ShopifyHomeDefinitionState $context.Binding $admin $Transport
            if(@($fresh.Definitions[[string]$action.resourceKey]).Count -ne 0){throw 'LATER_STEP_DRIFT'}
            foreach($existingType in @($fresh.Definitions.Keys|Where-Object{@($fresh.Definitions[$_]).Count -gt 0})){
                if(-not $createdIds.ContainsKey([string]$existingType)){throw 'LATER_STEP_DRIFT'}
            }
            $schema=Get-Content -LiteralPath $homeSchema -Raw|ConvertFrom-Json -AsHashtable
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
            $created=New-ShopifyHomeDefinition $context.Binding $admin $definition $Transport;$createdIds[[string]$action.resourceKey]=[string]$created.id;$action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=$fingerprint
        } elseif([string]$action.resourceKind -ceq 'SHOPIFY_HOME_ACCEPTANCE_PROBE'){
            $fresh=Get-ShopifyAcceptanceProbeState $context.Binding $admin $Transport;if([string]$fresh.Classification -cne 'ABSENT'){throw 'PROBE_COLLISION'}
            $created=New-ShopifyAcceptanceProbe $context.Binding $admin $Transport;$action.providerResourceId=[string]$created.id;$action.status='SUCCEEDED';$action.afterClassification='CORRECT';$action.afterFingerprint=$fingerprint
        }
    }
    $plan.kind='RESULT';$plan.expiresAtUtc=$null;$plan.overallStatus='SUCCEEDED';$plan.actions=$actions
    if([string]::IsNullOrWhiteSpace($OutputPath)){$OutputPath="$PlanReceipt.result.json"};Write-OnboardingReceipt $OutputPath $plan;return $plan
}
function Write-OnboardingLocalConfiguration {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[switch]$ConfirmApply)
    if(-not $ConfirmApply){throw 'CONFIRMATION_MISMATCH'}
    $registry=Import-OnboardingRegistry -Path (Join-Path $RepositoryRoot 'config\onboarding\application-registry.v1.json') -RepositoryRoot $RepositoryRoot
    $selected=Get-OnboardingApplicationProfile -Registry $registry -Application $Application -Profile $Profile
    $destination=Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot -RelativePath ([string]$selected.Profile.localConfiguration) -Field 'localConfiguration'
    $relative=[IO.Path]::GetRelativePath($RepositoryRoot,$destination).Replace('\','/');& git -C $RepositoryRoot check-ignore --no-index --quiet -- $relative;if($LASTEXITCODE-ne 0){throw 'LOCAL_CONFIGURATION_NOT_IGNORED'}
    $map=[ordered]@{
      'shopify.storefrontPublicToken'='STOREFRONT_PUBLIC_TOKEN';'shopify.customerAccountClientId'='CUSTOMER_ACCOUNT_CLIENT_ID';'shopify.customerAccountIssuer'='CUSTOMER_ACCOUNT_ISSUER';'shopify.customerAccountAuthorizationEndpoint'='CUSTOMER_ACCOUNT_AUTHORIZATION_ENDPOINT';'shopify.customerAccountTokenEndpoint'='CUSTOMER_ACCOUNT_TOKEN_ENDPOINT';'shopify.customerAccountLogoutEndpoint'='CUSTOMER_ACCOUNT_LOGOUT_ENDPOINT';'shopify.customerAccountGraphqlEndpoint'='CUSTOMER_ACCOUNT_GRAPHQL_ENDPOINT';'shopify.customerAccountRedirectUri'='CUSTOMER_ACCOUNT_REDIRECT_URI'
    }
    $lines=@();foreach($entry in $map.GetEnumerator()){$name=Get-OnboardingCredentialName $Application $Profile ([string]$entry.Value);$value=Get-OnboardingCredential $name;if([string]::IsNullOrWhiteSpace($value)-or$value-match '[\r\n]'){throw 'MISSING_OR_UNSAFE_CLIENT_CONFIGURATION'};$lines+="$($entry.Key)=$value"}
    $parent=Split-Path -Parent $destination;[IO.Directory]::CreateDirectory($parent)|Out-Null;$temporary=Join-Path $parent ('.gate8-'+[guid]::NewGuid().ToString('N')+'.tmp')
    try{[IO.File]::WriteAllText($temporary,(($lines-join"`n")+"`n"),[Text.UTF8Encoding]::new($false));if(Test-Path $destination){Copy-Item -LiteralPath $destination -Destination "$destination.bak" -Force};Move-Item -LiteralPath $temporary -Destination $destination -Force}finally{if(Test-Path $temporary){Remove-Item -LiteralPath $temporary -Force}}
    Write-Output 'PASS: scoped client configuration was written atomically; values were not printed.'
}
function Write-OnboardingManualCheckpoint {
    param([string]$RepositoryRoot,[string]$Application,[string]$Profile,[string]$OutputPath,[string]$EvidenceRef)
    $context=Get-OnboardingOperatorContext $RepositoryRoot $Application $Profile
    $local=Read-OnboardingProperties -Path (Test-OnboardingSafeRelativePath $RepositoryRoot ([string]$context.Selected.Profile.localConfiguration) 'localConfiguration') -AllowedKeys @('shopify.storefrontPublicToken','shopify.customerAccountClientId','shopify.customerAccountIssuer','shopify.customerAccountAuthorizationEndpoint','shopify.customerAccountTokenEndpoint','shopify.customerAccountLogoutEndpoint','shopify.customerAccountGraphqlEndpoint','shopify.customerAccountRedirectUri')
    $client=[string]$local['shopify.customerAccountClientId'];$callback=[string]$local['shopify.customerAccountRedirectUri'];$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($client))).ToLowerInvariant()
    $record=[ordered]@{schemaVersion=1;application=$Application;profile=$Profile;shopId=[string]$context.Binding.shopify.shopId;clientIdSha256=$hash;callback=$callback;recordedAtUtc=[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ');approvedEvidenceRef=$EvidenceRef}
    $full=[IO.Path]::GetFullPath($OutputPath);[IO.Directory]::CreateDirectory((Split-Path -Parent $full))|Out-Null;[IO.File]::WriteAllText($full,(($record|ConvertTo-Json -Compress)+"`n"),[Text.UTF8Encoding]::new($false));Write-Output 'PASS: sanitized manual registration checkpoint recorded.'
}
function Get-OnboardingRecovery { param([string]$PlanReceipt) $receipt=Import-OnboardingReceipt -Path $PlanReceipt;[pscustomobject]@{application=$receipt.application;profile=$receipt.profile;kind=$receipt.kind;overallStatus=$receipt.overallStatus;nextAction='REINSPECT'} }
Export-ModuleMember -Function @('Invoke-OnboardingInspect','New-OnboardingPlan','Invoke-OnboardingApply','Write-OnboardingLocalConfiguration','Write-OnboardingManualCheckpoint','Get-OnboardingRecovery','Get-OnboardingOperatorContext','Get-OnboardingCredentialName','Get-OnboardingCredential','Write-OnboardingReceipt')
