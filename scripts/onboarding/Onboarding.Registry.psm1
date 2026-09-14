Set-StrictMode -Version Latest

$commonModule = Join-Path $PSScriptRoot 'Onboarding.Common.psm1'
Import-Module $commonModule -Force

$script:KeyPattern = '^[a-z][a-z0-9-]{1,31}$'
$script:GradlePathPattern = '^:[a-z][a-z0-9-]{0,63}$'
$script:ApplicationIdPattern = '^[a-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)+$'
$script:DigestPattern = '^[0-9a-f]{64}$'
$script:ReceiptDiagnosticCodes = @(
    'UNCONFIGURED',
    'MANUAL_REQUIRED',
    'BLOCKED_INCOMPATIBLE',
    'BLOCKED_OPERATOR_SCHEMA_POLICY',
    'BLOCKED_DRIFT',
    'EXTERNAL_FAILURE',
    'PARTIAL_READBACK',
    'MOBILE_READBACK_FAILURE',
    'MERCHANT_DRIFT',
    'UNSAFE_TARGET',
    'MISSING_CREDENTIAL',
    'PROVIDER_ERROR',
    'RATE_LIMITED',
    'PUBLIC_ASSOCIATION_UNAVAILABLE',
    'NOT_APPLICABLE',
    'VALIDATION_FAILURE',
    'READBACK_MISMATCH',
    'PARTIAL_APPLY'
)

function Assert-OnboardingEnum {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string[]]$Allowed,
        [Parameter(Mandatory)][string]$Field
    )

    if ($Value -cnotin $Allowed) {
        throw (New-OnboardingContractError -Code 'INVALID_ENUM' -Field $Field)
    }
}

function Assert-OnboardingKey {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Field
    )

    Assert-OnboardingText -Value $Value -Field $Field
    if ($Value -cnotmatch $script:KeyPattern) {
        throw (New-OnboardingContractError -Code 'INVALID_KEY' -Field $Field)
    }
}

function Assert-OnboardingArray {
    param(
        [AllowNull()]$Value,
        [Parameter(Mandatory)][string]$Field,
        [int]$MaximumCount = 128
    )

    if ($null -eq $Value -or $Value -is [string] -or $Value -isnot [System.Collections.IEnumerable]) {
        throw (New-OnboardingContractError -Code 'INVALID_ARRAY' -Field $Field)
    }
    $items = @($Value)
    if ($items.Count -gt $MaximumCount) {
        throw (New-OnboardingContractError -Code 'ARRAY_TOO_LARGE' -Field $Field)
    }
    return $items
}

function Assert-OnboardingHttpsOrigin {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Field,
        [switch]$AllowInvalidTld
    )

    Assert-OnboardingText -Value $Value -Field $Field -MaximumLength 2048
    $uri = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -cne 'https' -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        -not [string]::IsNullOrEmpty($uri.Fragment) -or
        $uri.AbsolutePath -cne '/') {
        throw (New-OnboardingContractError -Code 'UNSAFE_URL' -Field $Field)
    }
    Assert-OnboardingHost -HostName $uri.IdnHost -Field "$Field.host" -AllowInvalidTld:$AllowInvalidTld
}

function Assert-OnboardingPathPrefix {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Field
    )

    Assert-OnboardingText -Value $Value -Field $Field -MaximumLength 256
    if (-not $Value.StartsWith('/') -or -not $Value.EndsWith('/') -or
        $Value.Contains('//') -or $Value.Contains('?') -or $Value.Contains('#')) {
        throw (New-OnboardingContractError -Code 'INVALID_PATH_PREFIX' -Field $Field)
    }
}

function Assert-OnboardingModule {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Module,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][int]$Index
    )

    $field = "modules[$Index]"
    $fields = @('key', 'gradleProject', 'directory', 'role', 'allowedDirectProjects', 'ciTasks')
    Assert-OnboardingObjectFields -Object $Module -Allowed $fields -Required $fields -Field $field
    Assert-OnboardingKey -Value ([string]$Module.key) -Field "$field.key"
    if ([string]$Module.gradleProject -cnotmatch $script:GradlePathPattern) {
        throw (New-OnboardingContractError -Code 'INVALID_GRADLE_PATH' -Field "$field.gradleProject")
    }
    $directory = Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot -RelativePath ([string]$Module.directory) -Field "$field.directory"
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw (New-OnboardingContractError -Code 'MISSING_MODULE_DIRECTORY' -Field "$field.directory")
    }
    Assert-OnboardingEnum -Value ([string]$Module.role) `
        -Allowed @('real-brand-application', 'synthetic-conformance-application', 'shared', 'provider') `
        -Field "$field.role"
    foreach ($dependency in (Assert-OnboardingArray -Value $Module.allowedDirectProjects -Field "$field.allowedDirectProjects" -MaximumCount 16)) {
        if ([string]$dependency -cnotmatch $script:GradlePathPattern) {
            throw (New-OnboardingContractError -Code 'INVALID_GRADLE_PATH' -Field "$field.allowedDirectProjects")
        }
    }
    Assert-OnboardingObjectFields -Object $Module.ciTasks `
        -Allowed @('unit', 'assemble', 'api30', 'api23') `
        -Required @('unit', 'assemble', 'api30', 'api23') `
        -Field "$field.ciTasks"
    foreach ($lane in @('unit', 'assemble', 'api30', 'api23')) {
        foreach ($task in (Assert-OnboardingArray -Value $Module.ciTasks[$lane] -Field "$field.ciTasks.$lane")) {
            Assert-OnboardingText -Value ([string]$task) -Field "$field.ciTasks.$lane"
            if ([string]$task -notmatch '^:[a-z][a-z0-9-]*(?::[A-Za-z][A-Za-z0-9]*)$' -or
                -not ([string]$task).StartsWith(([string]$Module.gradleProject + ':'), [System.StringComparison]::Ordinal)) {
                throw (New-OnboardingContractError -Code 'INVALID_GRADLE_TASK' -Field "$field.ciTasks.$lane")
            }
        }
    }
}

function Assert-OnboardingAppLink {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Link,
        [Parameter(Mandatory)][string]$Field,
        [switch]$AllowInvalidTld
    )

    Assert-OnboardingObjectFields -Object $Link `
        -Allowed @('origin', 'pathPrefix') `
        -Required @('origin', 'pathPrefix') `
        -Field $Field
    Assert-OnboardingHttpsOrigin -Value ([string]$Link.origin) -Field "$Field.origin" -AllowInvalidTld:$AllowInvalidTld
    Assert-OnboardingPathPrefix -Value ([string]$Link.pathPrefix) -Field "$Field.pathPrefix"
}

function Assert-OnboardingIdentity {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Identity,
        [Parameter(Mandatory)][string]$Role,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$Field
    )

    $fields = @(
        'displayName', 'analyticsNamespace', 'compositionSource', 'brandKey',
        'defaultLocale', 'supportedLocales', 'market', 'supportedTerritory',
        'searchNormalizationLocale', 'databaseName', 'customerAccount',
        'webRoles', 'nativeCompositionAssertions'
    )
    Assert-OnboardingObjectFields -Object $Identity -Allowed $fields -Required $fields -Field $Field
    foreach ($name in @('displayName', 'analyticsNamespace', 'brandKey', 'defaultLocale', 'supportedTerritory', 'searchNormalizationLocale', 'databaseName')) {
        Assert-OnboardingText -Value ([string]$Identity[$name]) -Field "$Field.$name"
    }
    $compositionPath = Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot `
        -RelativePath ([string]$Identity.compositionSource) -Field "$Field.compositionSource"
    if (-not (Test-Path -LiteralPath $compositionPath -PathType Leaf)) {
        throw (New-OnboardingContractError -Code 'MISSING_COMPOSITION_SOURCE' -Field "$Field.compositionSource")
    }
    $locales = @(Assert-OnboardingArray -Value $Identity.supportedLocales -Field "$Field.supportedLocales" -MaximumCount 16)
    if ($locales.Count -lt 1 -or [string]$Identity.defaultLocale -cnotin @($locales | ForEach-Object { [string]$_ })) {
        throw (New-OnboardingContractError -Code 'INVALID_LOCALE_SET' -Field "$Field.supportedLocales")
    }
    Assert-OnboardingObjectFields -Object $Identity.market `
        -Allowed @('id', 'countryCode', 'currencyCode') `
        -Required @('id', 'countryCode', 'currencyCode') `
        -Field "$Field.market"
    foreach ($name in @('id', 'countryCode', 'currencyCode')) {
        Assert-OnboardingText -Value ([string]$Identity.market[$name]) -Field "$Field.market.$name"
    }
    $customerIdentityFields = if ([string]$Identity.customerAccount.mode -ceq 'disabled') {
        @('mode')
    } else {
        @('mode', 'userAgent', 'callbackSchemeSuffix', 'callbackHost', 'callbackPath', 'scopes')
    }
    Assert-OnboardingObjectFields -Object $Identity.customerAccount `
        -Allowed $customerIdentityFields -Required $customerIdentityFields -Field "$Field.customerAccount"
    Assert-OnboardingEnum -Value ([string]$Identity.customerAccount.mode) -Allowed @('enabled', 'disabled') -Field "$Field.customerAccount.mode"
    if ([string]$Identity.customerAccount.mode -ceq 'enabled') {
        Assert-OnboardingText -Value ([string]$Identity.customerAccount.userAgent) -Field "$Field.customerAccount.userAgent"
        if ([string]$Identity.customerAccount.userAgent -notmatch '^[\x21-\x7e]+(?: [\x21-\x7e]+)*$') {
            throw (New-OnboardingContractError -Code 'INVALID_USER_AGENT' -Field "$Field.customerAccount.userAgent")
        }
        Assert-OnboardingKey -Value ([string]$Identity.customerAccount.callbackSchemeSuffix) -Field "$Field.customerAccount.callbackSchemeSuffix"
        Assert-OnboardingKey -Value ([string]$Identity.customerAccount.callbackHost) -Field "$Field.customerAccount.callbackHost"
        $callbackPath = [string]$Identity.customerAccount.callbackPath
        if ($callbackPath -notmatch '^/[a-z0-9/-]+$' -or $callbackPath.Contains('//')) {
            throw (New-OnboardingContractError -Code 'INVALID_CALLBACK_PATH' -Field "$Field.customerAccount.callbackPath")
        }
        $scopes = @(Assert-OnboardingArray -Value $Identity.customerAccount.scopes -Field "$Field.customerAccount.scopes" -MaximumCount 16)
        if ($scopes.Count -lt 1) {
            throw (New-OnboardingContractError -Code 'MISSING_SCOPE' -Field "$Field.customerAccount.scopes")
        }
    }

    Assert-OnboardingObjectFields -Object $Identity.webRoles `
        -Allowed @('collectionAppLink', 'productAppLink', 'orderAppLink', 'legalSupport', 'checkout', 'assetLinks') `
        -Required @('collectionAppLink', 'productAppLink', 'orderAppLink', 'legalSupport', 'checkout', 'assetLinks') `
        -Field "$Field.webRoles"
    $allowInvalidTld = $Role -ceq 'synthetic-conformance-application'
    Assert-OnboardingAppLink -Link $Identity.webRoles.collectionAppLink -Field "$Field.webRoles.collectionAppLink" -AllowInvalidTld:$allowInvalidTld
    Assert-OnboardingAppLink -Link $Identity.webRoles.productAppLink -Field "$Field.webRoles.productAppLink" -AllowInvalidTld:$allowInvalidTld
    if ($null -ne $Identity.webRoles.orderAppLink) {
        Assert-OnboardingAppLink -Link $Identity.webRoles.orderAppLink -Field "$Field.webRoles.orderAppLink" -AllowInvalidTld:$allowInvalidTld
    }
    if ($null -ne $Identity.webRoles.legalSupport) {
        Assert-OnboardingObjectFields -Object $Identity.webRoles.legalSupport `
            -Allowed @('origin', 'paths') -Required @('origin', 'paths') -Field "$Field.webRoles.legalSupport"
        Assert-OnboardingHttpsOrigin -Value ([string]$Identity.webRoles.legalSupport.origin) -Field "$Field.webRoles.legalSupport.origin" -AllowInvalidTld:$allowInvalidTld
        Assert-OnboardingObjectFields -Object $Identity.webRoles.legalSupport.paths `
            -Allowed @('support', 'privacy', 'terms', 'shipping', 'returns', 'legalNotice') `
            -Required @('support', 'privacy', 'terms', 'shipping', 'returns', 'legalNotice') `
            -Field "$Field.webRoles.legalSupport.paths"
        foreach ($pathName in @('support', 'privacy', 'terms', 'shipping', 'returns', 'legalNotice')) {
            $pathValue = [string]$Identity.webRoles.legalSupport.paths[$pathName]
            if ($pathValue -notmatch '^/[a-z0-9/-]+$' -or $pathValue.Contains('//')) {
                throw (New-OnboardingContractError -Code 'INVALID_LEGAL_PATH' -Field "$Field.webRoles.legalSupport.paths.$pathName")
            }
        }
    }
    Assert-OnboardingObjectFields -Object $Identity.webRoles.checkout `
        -Allowed @('hostPolicy') -Required @('hostPolicy') -Field "$Field.webRoles.checkout"
    Assert-OnboardingObjectFields -Object $Identity.webRoles.assetLinks `
        -Allowed @('mode', 'manifestAutoVerify') -Required @('mode', 'manifestAutoVerify') -Field "$Field.webRoles.assetLinks"
    Assert-OnboardingEnum -Value ([string]$Identity.webRoles.assetLinks.mode) `
        -Allowed @('validate-only', 'disabled') -Field "$Field.webRoles.assetLinks.mode"

    Assert-OnboardingObjectFields -Object $Identity.nativeCompositionAssertions `
        -Allowed @('search', 'wishlist', 'customerAccount', 'primaryNavigation') `
        -Required @('search', 'wishlist', 'customerAccount', 'primaryNavigation') `
        -Field "$Field.nativeCompositionAssertions"
    foreach ($capability in @('search', 'wishlist', 'customerAccount')) {
        Assert-OnboardingEnum -Value ([string]$Identity.nativeCompositionAssertions[$capability]) `
            -Allowed @('ENABLED', 'DISABLED') -Field "$Field.nativeCompositionAssertions.$capability"
    }
    [void](Assert-OnboardingArray -Value $Identity.nativeCompositionAssertions.primaryNavigation `
        -Field "$Field.nativeCompositionAssertions.primaryNavigation" -MaximumCount 8)
}

function Assert-OnboardingProfile {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Profile,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Application,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][int]$Index
    )

    $field = "applications.$($Application.key).profiles[$Index]"
    $fields = @(
        'key', 'runtimeEnvironment', 'displayName', 'localConfiguration',
        'providerBindingFile', 'variants', 'storefront', 'customerAccount',
        'firebase', 'protectedPersistence'
    )
    Assert-OnboardingObjectFields -Object $Profile -Allowed $fields -Required $fields -Field $field
    Assert-OnboardingKey -Value ([string]$Profile.key) -Field "$field.key"
    Assert-OnboardingEnum -Value ([string]$Profile.runtimeEnvironment) -Allowed @('DEVELOPMENT', 'STAGING') -Field "$field.runtimeEnvironment"
    Assert-OnboardingText -Value ([string]$Profile.displayName) -Field "$field.displayName"
    foreach ($pathName in @('localConfiguration', 'providerBindingFile')) {
        if ($null -ne $Profile[$pathName]) {
            [void](Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot `
                -RelativePath ([string]$Profile[$pathName]) -Field "$field.$pathName")
        }
    }

    $variants = @(Assert-OnboardingArray -Value $Profile.variants -Field "$field.variants" -MaximumCount 8)
    if ($variants.Count -lt 1) {
        throw (New-OnboardingContractError -Code 'MISSING_VARIANT' -Field "$field.variants")
    }
    $variantNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($variant in $variants) {
        Assert-OnboardingObjectFields -Object $variant `
            -Allowed @('name', 'buildType', 'applicationId', 'firebaseConfig') `
            -Required @('name', 'buildType', 'applicationId', 'firebaseConfig') `
            -Field "$field.variants"
        if (-not $variantNames.Add([string]$variant.name)) {
            throw (New-OnboardingContractError -Code 'DUPLICATE_VARIANT' -Field "$field.variants")
        }
        Assert-OnboardingEnum -Value ([string]$variant.buildType) -Allowed @('debug', 'release') -Field "$field.variants.buildType"
        if ([string]$variant.applicationId -cnotmatch $script:ApplicationIdPattern) {
            throw (New-OnboardingContractError -Code 'INVALID_APPLICATION_ID' -Field "$field.variants.applicationId")
        }
        if ($null -ne $variant.firebaseConfig) {
            [void](Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot `
                -RelativePath ([string]$variant.firebaseConfig) -Field "$field.variants.firebaseConfig")
        }
    }

    $storefrontAllowed = @('mode', 'domain', 'apiVersion', 'publicTokenLocalKey', 'publicTokenPolicy', 'mediaOrigins', 'sharedResourceGroup', 'catalog', 'home')
    Assert-OnboardingObjectFields -Object $Profile.storefront -Allowed $storefrontAllowed -Required @('mode') -Field "$field.storefront"
    Assert-OnboardingEnum -Value ([string]$Profile.storefront.mode) -Allowed @('enabled', 'disabled-offline-fixture') -Field "$field.storefront.mode"
    if ([string]$Profile.storefront.mode -ceq 'enabled') {
        foreach ($required in @('domain', 'apiVersion', 'publicTokenLocalKey', 'mediaOrigins', 'sharedResourceGroup', 'catalog', 'home')) {
            if (-not $Profile.storefront.Contains($required)) {
                throw (New-OnboardingContractError -Code 'MISSING_FIELD' -Field "$field.storefront.$required")
            }
        }
        Assert-OnboardingHost -HostName ([string]$Profile.storefront.domain) -Field "$field.storefront.domain"
        Assert-OnboardingKey -Value ([string]$Profile.storefront.sharedResourceGroup) -Field "$field.storefront.sharedResourceGroup"
    } else {
        if ($Profile.storefront.Contains('sharedResourceGroup')) {
            throw (New-OnboardingContractError -Code 'DISABLED_RESOURCE_GROUP' -Field "$field.storefront.sharedResourceGroup")
        }
        Assert-OnboardingHost -HostName ([string]$Profile.storefront.domain) -Field "$field.storefront.domain" -AllowInvalidTld
    }
    if ($Profile.storefront.Contains('catalog')) {
        Assert-OnboardingObjectFields -Object $Profile.storefront.catalog `
            -Allowed @('menuHandle', 'managementMode') -Required @('menuHandle', 'managementMode') -Field "$field.storefront.catalog"
        Assert-OnboardingEnum -Value ([string]$Profile.storefront.catalog.managementMode) `
            -Allowed @('validate-only', 'disabled') -Field "$field.storefront.catalog.managementMode"
    }
    if ($Profile.storefront.Contains('home')) {
        Assert-OnboardingObjectFields -Object $Profile.storefront.home `
            -Allowed @('rootType', 'rootHandle', 'contentSchemaVersion', 'definitionContract', 'definitionManagementMode', 'entryManagementMode', 'sourceMode') `
            -Required @('definitionManagementMode', 'entryManagementMode', 'sourceMode') `
            -Field "$field.storefront.home"
        Assert-OnboardingEnum -Value ([string]$Profile.storefront.home.definitionManagementMode) `
            -Allowed @('create-if-missing', 'disabled') -Field "$field.storefront.home.definitionManagementMode"
        Assert-OnboardingEnum -Value ([string]$Profile.storefront.home.entryManagementMode) `
            -Allowed @('validate-only', 'disabled') -Field "$field.storefront.home.entryManagementMode"
    }

    Assert-OnboardingObjectFields -Object $Profile.customerAccount `
        -Allowed @('mode', 'discoveryOrigin', 'clientIdLocalKey') -Required @('mode') -Field "$field.customerAccount"
    Assert-OnboardingEnum -Value ([string]$Profile.customerAccount.mode) `
        -Allowed @('enabled-manual-registration', 'disabled') -Field "$field.customerAccount.mode"
    if ([string]$Profile.customerAccount.mode -ceq 'enabled-manual-registration') {
        foreach ($required in @('discoveryOrigin', 'clientIdLocalKey')) {
            if (-not $Profile.customerAccount.Contains($required)) {
                throw (New-OnboardingContractError -Code 'MISSING_FIELD' -Field "$field.customerAccount.$required")
            }
        }
        Assert-OnboardingHttpsOrigin -Value ([string]$Profile.customerAccount.discoveryOrigin) -Field "$field.customerAccount.discoveryOrigin"
    }

    Assert-OnboardingObjectFields -Object $Profile.firebase `
        -Allowed @('mode', 'ownershipKey', 'registrationManagementMode', 'projectIdentitySource') `
        -Required @('mode') -Field "$field.firebase"
    Assert-OnboardingEnum -Value ([string]$Profile.firebase.mode) `
        -Allowed @('firebase-or-local-default', 'disabled') -Field "$field.firebase.mode"
    if ([string]$Profile.firebase.mode -ceq 'firebase-or-local-default') {
        foreach ($required in @('ownershipKey', 'registrationManagementMode', 'projectIdentitySource')) {
            if (-not $Profile.firebase.Contains($required)) {
                throw (New-OnboardingContractError -Code 'MISSING_FIELD' -Field "$field.firebase.$required")
            }
        }
        Assert-OnboardingKey -Value ([string]$Profile.firebase.ownershipKey) -Field "$field.firebase.ownershipKey"
        Assert-OnboardingEnum -Value ([string]$Profile.firebase.registrationManagementMode) `
            -Allowed @('validate-only') -Field "$field.firebase.registrationManagementMode"
        Assert-OnboardingEnum -Value ([string]$Profile.firebase.projectIdentitySource) `
            -Allowed @('provider-binding') -Field "$field.firebase.projectIdentitySource"
    }

    Assert-OnboardingObjectFields -Object $Profile.protectedPersistence `
        -Allowed @('cartPreferences', 'cartKeyAlias', 'customerPreferences', 'customerKeyAlias') `
        -Required @('cartPreferences', 'cartKeyAlias', 'customerPreferences', 'customerKeyAlias') `
        -Field "$field.protectedPersistence"
    foreach ($name in @('cartPreferences', 'cartKeyAlias', 'customerPreferences', 'customerKeyAlias')) {
        Assert-OnboardingText -Value ([string]$Profile.protectedPersistence[$name]) -Field "$field.protectedPersistence.$name"
    }
}

function Import-OnboardingRegistry {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [switch]$AllowFixtureRecords
    )

    $registry = Read-OnboardingStrictJson -Path $Path -MaximumBytes 262144
    Assert-OnboardingObjectFields -Object $registry `
        -Allowed @('schemaVersion', 'providerContracts', 'modules', 'applications', 'ciLanes') `
        -Required @('schemaVersion', 'providerContracts', 'modules', 'applications', 'ciLanes') `
        -Field '$'
    if ([int]$registry.schemaVersion -ne 1) {
        throw (New-OnboardingContractError -Code 'UNSUPPORTED_SCHEMA_VERSION' -Field '$.schemaVersion')
    }
    Assert-OnboardingObjectFields -Object $registry.providerContracts `
        -Allowed @('shopifyAdminApiVersion', 'shopifyCustomerAccountApiVersion', 'allowedStorefrontApiVersions', 'gate7HomeContentSchemaVersion') `
        -Required @('shopifyAdminApiVersion', 'shopifyCustomerAccountApiVersion', 'allowedStorefrontApiVersions', 'gate7HomeContentSchemaVersion') `
        -Field '$.providerContracts'
    if ([string]$registry.providerContracts.shopifyAdminApiVersion -cne '2026-07' -or
        [string]$registry.providerContracts.shopifyCustomerAccountApiVersion -cne '2026-07' -or
        [int]$registry.providerContracts.gate7HomeContentSchemaVersion -ne 1) {
        throw (New-OnboardingContractError -Code 'UNSUPPORTED_PROVIDER_CONTRACT' -Field '$.providerContracts')
    }
    $storefrontVersions = @(Assert-OnboardingArray -Value $registry.providerContracts.allowedStorefrontApiVersions `
        -Field '$.providerContracts.allowedStorefrontApiVersions' -MaximumCount 8)
    if (@($storefrontVersions | Where-Object { [string]$_ -ceq '2026-07' }).Count -ne 1) {
        throw (New-OnboardingContractError -Code 'UNSUPPORTED_PROVIDER_CONTRACT' -Field '$.providerContracts.allowedStorefrontApiVersions')
    }

    $modules = @(Assert-OnboardingArray -Value $registry.modules -Field '$.modules' -MaximumCount 64)
    $moduleKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $modulePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $moduleDirectories = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    for ($index = 0; $index -lt $modules.Count; $index++) {
        Assert-OnboardingModule -Module $modules[$index] -RepositoryRoot $RepositoryRoot -Index $index
        if (-not $moduleKeys.Add([string]$modules[$index].key) -or
            -not $modulePaths.Add([string]$modules[$index].gradleProject) -or
            -not $moduleDirectories.Add([string]$modules[$index].directory)) {
            throw (New-OnboardingContractError -Code 'DUPLICATE_MODULE' -Field '$.modules')
        }
    }
    $sortedModulePaths = @($modulePaths | Sort-Object -CaseSensitive)
    $declaredModulePaths = @($modules | ForEach-Object { [string]$_.gradleProject })
    if (($sortedModulePaths -join "`n") -cne ($declaredModulePaths -join "`n")) {
        throw (New-OnboardingContractError -Code 'NONCANONICAL_ORDER' -Field '$.modules')
    }
    foreach ($module in $modules) {
        foreach ($dependency in @($module.allowedDirectProjects)) {
            if (-not $modulePaths.Contains([string]$dependency)) {
                throw (New-OnboardingContractError -Code 'UNKNOWN_MODULE_DEPENDENCY' -Field "$($module.gradleProject).allowedDirectProjects")
            }
        }
    }

    $applications = @(Assert-OnboardingArray -Value $registry.applications -Field '$.applications' -MaximumCount 16)
    $applicationKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $applicationIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $persistenceValues = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $firebaseOwnership = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    for ($applicationIndex = 0; $applicationIndex -lt $applications.Count; $applicationIndex++) {
        $application = $applications[$applicationIndex]
        $field = "applications[$applicationIndex]"
        $fields = @('key', 'module', 'role', 'fixtureOnly', 'releaseBoundary', 'providerModules', 'configurationProjection', 'identity', 'profiles')
        Assert-OnboardingObjectFields -Object $application -Allowed $fields -Required $fields -Field $field
        Assert-OnboardingKey -Value ([string]$application.key) -Field "$field.key"
        if (-not $applicationKeys.Add([string]$application.key)) {
            throw (New-OnboardingContractError -Code 'DUPLICATE_APPLICATION' -Field "$field.key")
        }
        Assert-OnboardingEnum -Value ([string]$application.role) `
            -Allowed @('real-brand-application', 'synthetic-conformance-application') -Field "$field.role"
        if (-not $modulePaths.Contains([string]$application.module)) {
            throw (New-OnboardingContractError -Code 'UNKNOWN_APPLICATION_MODULE' -Field "$field.module")
        }
        $moduleRecord = @($modules | Where-Object { [string]$_.gradleProject -ceq [string]$application.module })
        if ($moduleRecord.Count -ne 1 -or [string]$moduleRecord[0].role -cne [string]$application.role) {
            throw (New-OnboardingContractError -Code 'APPLICATION_ROLE_MISMATCH' -Field "$field.role")
        }
        if ([bool]$application.fixtureOnly -and -not $AllowFixtureRecords) {
            throw (New-OnboardingContractError -Code 'FIXTURE_RECORD_FORBIDDEN' -Field "$field.fixtureOnly")
        }
        if ($null -ne $application.configurationProjection) {
            [void](Test-OnboardingSafeRelativePath -RepositoryRoot $RepositoryRoot `
                -RelativePath ([string]$application.configurationProjection) -Field "$field.configurationProjection")
        }
        foreach ($providerModule in (Assert-OnboardingArray -Value $application.providerModules -Field "$field.providerModules" -MaximumCount 16)) {
            $provider = @($modules | Where-Object { [string]$_.gradleProject -ceq [string]$providerModule })
            if ($provider.Count -ne 1 -or [string]$provider[0].role -cne 'provider') {
                throw (New-OnboardingContractError -Code 'INVALID_PROVIDER_MODULE' -Field "$field.providerModules")
            }
        }
        Assert-OnboardingIdentity -Identity $application.identity -Role ([string]$application.role) `
            -RepositoryRoot $RepositoryRoot -Field "$field.identity"
        $profiles = @(Assert-OnboardingArray -Value $application.profiles -Field "$field.profiles" -MaximumCount 8)
        if ($profiles.Count -lt 1) {
            throw (New-OnboardingContractError -Code 'MISSING_PROFILE' -Field "$field.profiles")
        }
        $profileKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        for ($profileIndex = 0; $profileIndex -lt $profiles.Count; $profileIndex++) {
            $profile = $profiles[$profileIndex]
            Assert-OnboardingProfile -Profile $profile -Application $application -RepositoryRoot $RepositoryRoot -Index $profileIndex
            if (-not $profileKeys.Add([string]$profile.key)) {
                throw (New-OnboardingContractError -Code 'DUPLICATE_PROFILE' -Field "$field.profiles")
            }
            foreach ($variant in @($profile.variants)) {
                if (-not $applicationIds.Add([string]$variant.applicationId)) {
                    throw (New-OnboardingContractError -Code 'APPLICATION_ID_COLLISION' -Field "$field.profiles.variants.applicationId")
                }
            }
            foreach ($name in @('cartPreferences', 'cartKeyAlias', 'customerPreferences', 'customerKeyAlias')) {
                if (-not $persistenceValues.Add([string]$profile.protectedPersistence[$name])) {
                    throw (New-OnboardingContractError -Code 'PERSISTENCE_COLLISION' -Field "$field.profiles.protectedPersistence.$name")
                }
            }
            if ([string]$profile.firebase.mode -cne 'disabled' -and
                -not $firebaseOwnership.Add([string]$profile.firebase.ownershipKey)) {
                throw (New-OnboardingContractError -Code 'FIREBASE_OWNERSHIP_COLLISION' -Field "$field.profiles.firebase.ownershipKey")
            }
        }
        $sortedProfileKeys = @($profileKeys | Sort-Object -CaseSensitive)
        $declaredProfileKeys = @($profiles | ForEach-Object { [string]$_.key })
        if (($sortedProfileKeys -join "`n") -cne ($declaredProfileKeys -join "`n")) {
            throw (New-OnboardingContractError -Code 'NONCANONICAL_ORDER' -Field "$field.profiles")
        }
        if ([string]$application.role -ceq 'synthetic-conformance-application') {
            if (@($application.providerModules).Count -ne 0 -or
                [string]$application.releaseBoundary -cne 'never-production' -or
                $null -ne $application.configurationProjection) {
                throw (New-OnboardingContractError -Code 'SYNTHETIC_ROLE_VIOLATION' -Field $field)
            }
        }
    }
    $sortedApplicationKeys = @($applicationKeys | Sort-Object -CaseSensitive)
    $declaredApplicationKeys = @($applications | ForEach-Object { [string]$_.key })
    if (($sortedApplicationKeys -join "`n") -cne ($declaredApplicationKeys -join "`n")) {
        throw (New-OnboardingContractError -Code 'NONCANONICAL_ORDER' -Field '$.applications')
    }

    Assert-OnboardingObjectFields -Object $registry.ciLanes `
        -Allowed @('unit', 'assemble', 'api30', 'api23') `
        -Required @('unit', 'assemble', 'api30', 'api23') `
        -Field '$.ciLanes'
    foreach ($lane in @('unit', 'assemble', 'api30', 'api23')) {
        $tasks = @(Assert-OnboardingArray -Value $registry.ciLanes[$lane] -Field "$.ciLanes.$lane")
        if (@($tasks | Select-Object -Unique).Count -ne $tasks.Count) {
            throw (New-OnboardingContractError -Code 'DUPLICATE_CI_TASK' -Field "$.ciLanes.$lane")
        }
        $moduleTasks = @(
            foreach ($module in $modules) {
                foreach ($task in @($module.ciTasks[$lane])) { [string]$task }
            }
        )
        if ((@($moduleTasks | Sort-Object) -join "`n") -cne
            (@($tasks | ForEach-Object { [string]$_ } | Sort-Object) -join "`n")) {
            throw (New-OnboardingContractError -Code 'CI_LANE_UNION_MISMATCH' -Field "$.ciLanes.$lane")
        }
    }
    foreach ($required in @(
        ':mobile-core:ciApi23DebugAndroidTest',
        ':synthetic:ciApi23DebugAndroidTest',
        ':app:testDevelopmentDebugUnitTest',
        ':app:testStagingDebugUnitTest'
    )) {
        if ($required -notin @($registry.ciLanes.unit) -and $required -notin @($registry.ciLanes.api23)) {
            throw (New-OnboardingContractError -Code 'MISSING_REQUIRED_CI_TASK' -Field $required)
        }
    }
    return $registry
}

function Import-OnboardingProviderBinding {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Application,
        [Parameter(Mandatory)][string]$Profile,
        [string[]]$ExpectedFirebaseVariants = @()
    )

    $binding = Read-OnboardingStrictJson -Path $Path -MaximumBytes 262144
    Assert-OnboardingObjectFields -Object $binding `
        -Allowed @('schemaVersion', 'application', 'profile', 'approvedEvidenceRef', 'shopify', 'firebase') `
        -Required @('schemaVersion', 'application', 'profile', 'approvedEvidenceRef', 'shopify') `
        -Field '$'
    if ([int]$binding.schemaVersion -ne 1) {
        throw (New-OnboardingContractError -Code 'UNSUPPORTED_SCHEMA_VERSION' -Field '$.schemaVersion')
    }
    if ([string]$binding.application -cne $Application -or [string]$binding.profile -cne $Profile) {
        throw (New-OnboardingContractError -Code 'BINDING_TARGET_MISMATCH' -Field '$.application/profile')
    }
    if ([string]$binding.approvedEvidenceRef -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}$') {
        throw (New-OnboardingContractError -Code 'INVALID_EVIDENCE_REFERENCE' -Field '$.approvedEvidenceRef')
    }
    Assert-OnboardingObjectFields -Object $binding.shopify `
        -Allowed @('adminShopDomain', 'shopId') -Required @('adminShopDomain', 'shopId') -Field '$.shopify'
    $adminDomain = [string]$binding.shopify.adminShopDomain
    Assert-OnboardingHost -HostName $adminDomain -Field '$.shopify.adminShopDomain'
    if ($adminDomain -cnotmatch '^[a-z0-9](?:[a-z0-9-]{0,60}[a-z0-9])?\.myshopify\.com$' -or
        [string]$binding.shopify.shopId -cnotmatch '^[0-9]{1,20}$') {
        throw (New-OnboardingContractError -Code 'INVALID_SHOPIFY_BINDING' -Field '$.shopify')
    }
    if ($binding.Contains('firebase')) {
        Assert-OnboardingObjectFields -Object $binding.firebase `
            -Allowed @('projectId', 'projectNumber', 'androidAppIdsByVariant') `
            -Required @('projectId', 'projectNumber', 'androidAppIdsByVariant') `
            -Field '$.firebase'
        if ([string]$binding.firebase.projectId -cnotmatch '^[a-z][a-z0-9-]{4,28}[a-z0-9]$' -or
            [string]$binding.firebase.projectNumber -cnotmatch '^[0-9]{1,20}$') {
            throw (New-OnboardingContractError -Code 'INVALID_FIREBASE_BINDING' -Field '$.firebase')
        }
        $variantIds = $binding.firebase.androidAppIdsByVariant
        $declaredVariants = @($variantIds.Keys | Sort-Object -CaseSensitive)
        $expectedVariants = @($ExpectedFirebaseVariants | Sort-Object -CaseSensitive)
        if ($expectedVariants.Count -gt 0 -and ($declaredVariants -join "`n") -cne ($expectedVariants -join "`n")) {
            throw (New-OnboardingContractError -Code 'FIREBASE_VARIANT_MISMATCH' -Field '$.firebase.androidAppIdsByVariant')
        }
        foreach ($entry in $variantIds.GetEnumerator()) {
            if ([string]$entry.Value -cnotmatch '^1:[0-9]{1,20}:android:[0-9a-f]+$') {
                throw (New-OnboardingContractError -Code 'INVALID_FIREBASE_APP_ID' -Field "$.firebase.androidAppIdsByVariant.$($entry.Key)")
            }
        }
    } elseif ($ExpectedFirebaseVariants.Count -gt 0) {
        throw (New-OnboardingContractError -Code 'MISSING_FIREBASE_BINDING' -Field '$.firebase')
    }
    return $binding
}

function Import-OnboardingReceipt {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $receipt = Read-OnboardingStrictJson -Path $Path -MaximumBytes 262144
    $topFields = @(
        'receiptSchemaVersion', 'operationContractVersion', 'kind', 'application', 'profile',
        'runtimeEnvironment', 'releaseBoundary', 'createdAtUtc', 'expiresAtUtc',
        'verifiedTarget', 'digests', 'stateFingerprint', 'actions', 'overallStatus',
        'diagnosticCodes', 'readback', 'recovery'
    )
    Assert-OnboardingObjectFields -Object $receipt -Allowed $topFields -Required $topFields -Field '$'
    if ([int]$receipt.receiptSchemaVersion -ne 1 -or [string]$receipt.operationContractVersion -cne 'gate8-v1') {
        throw (New-OnboardingContractError -Code 'UNSUPPORTED_RECEIPT_VERSION' -Field '$')
    }
    Assert-OnboardingEnum -Value ([string]$receipt.kind) -Allowed @('PLAN', 'RESULT', 'RECOVERY') -Field '$.kind'
    Assert-OnboardingKey -Value ([string]$receipt.application) -Field '$.application'
    Assert-OnboardingKey -Value ([string]$receipt.profile) -Field '$.profile'
    Assert-OnboardingEnum -Value ([string]$receipt.runtimeEnvironment) -Allowed @('DEVELOPMENT', 'STAGING') -Field '$.runtimeEnvironment'
    $created = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParseExact(
        [string]$receipt.createdAtUtc,
        'yyyy-MM-ddTHH:mm:ssZ',
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::AssumeUniversal,
        [ref]$created
    )) {
        throw (New-OnboardingContractError -Code 'INVALID_RECEIPT_TIME' -Field '$.createdAtUtc')
    }
    if ([string]$receipt.kind -ceq 'PLAN') {
        $expires = [datetimeoffset]::MinValue
        if ($null -eq $receipt.expiresAtUtc -or -not [datetimeoffset]::TryParseExact(
            [string]$receipt.expiresAtUtc,
            'yyyy-MM-ddTHH:mm:ssZ',
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::AssumeUniversal,
            [ref]$expires
        ) -or ($expires - $created).TotalMinutes -ne 15) {
            throw (New-OnboardingContractError -Code 'INVALID_RECEIPT_EXPIRY' -Field '$.expiresAtUtc')
        }
    } elseif ($null -ne $receipt.expiresAtUtc) {
        throw (New-OnboardingContractError -Code 'INVALID_RECEIPT_EXPIRY' -Field '$.expiresAtUtc')
    }
    Assert-OnboardingObjectFields -Object $receipt.verifiedTarget `
        -Allowed @('shopId', 'adminShopDomain', 'firebaseProjectId', 'firebaseProjectNumber') -Field '$.verifiedTarget'
    Assert-OnboardingObjectFields -Object $receipt.digests `
        -Allowed @('registrySha256', 'providerBindingSha256', 'homeSchemaSha256', 'operatorSha256') `
        -Required @('registrySha256', 'providerBindingSha256', 'homeSchemaSha256', 'operatorSha256') `
        -Field '$.digests'
    foreach ($name in @('registrySha256', 'providerBindingSha256', 'homeSchemaSha256', 'operatorSha256')) {
        if ([string]$receipt.digests[$name] -cnotmatch $script:DigestPattern) {
            throw (New-OnboardingContractError -Code 'INVALID_DIGEST' -Field "$.digests.$name")
        }
    }
    if ([string]$receipt.stateFingerprint -cnotmatch $script:DigestPattern) {
        throw (New-OnboardingContractError -Code 'INVALID_DIGEST' -Field '$.stateFingerprint')
    }
    $actions = @(Assert-OnboardingArray -Value $receipt.actions -Field '$.actions' -MaximumCount 16)
    foreach ($action in $actions) {
        $fields = @('ordinal', 'resourceKind', 'resourceKey', 'managementMode', 'beforeClassification', 'intendedAction', 'beforeFingerprint', 'providerResourceId', 'status', 'afterClassification', 'afterFingerprint')
        Assert-OnboardingObjectFields -Object $action -Allowed $fields -Required $fields -Field '$.actions'
        Assert-OnboardingEnum -Value ([string]$action.resourceKind) -Allowed @('LOCAL_CONFIGURATION', 'SHOPIFY_HOME_DEFINITION', 'SHOPIFY_HOME_ACCEPTANCE_PROBE') -Field '$.actions.resourceKind'
        Assert-OnboardingEnum -Value ([string]$action.managementMode) -Allowed @('LOCAL_APPLY', 'CREATE_IF_MISSING', 'PROBE_CREATE_IF_MISSING') -Field '$.actions.managementMode'
        Assert-OnboardingEnum -Value ([string]$action.beforeClassification) -Allowed @('ABSENT', 'CORRECT', 'COMPATIBLE', 'INCOMPATIBLE', 'DRIFTED', 'UNKNOWN') -Field '$.actions.beforeClassification'
        Assert-OnboardingEnum -Value ([string]$action.intendedAction) -Allowed @('NONE', 'CREATE', 'ATOMIC_REPLACE') -Field '$.actions.intendedAction'
        Assert-OnboardingEnum -Value ([string]$action.status) -Allowed @('PLANNED', 'NO_OP', 'SUCCEEDED', 'BLOCKED', 'FAILED', 'AMBIGUOUS') -Field '$.actions.status'
        if ($null -ne $action.providerResourceId -and [string]$action.providerResourceId -cnotmatch '^gid://shopify/[A-Za-z][A-Za-z0-9]{0,64}/[0-9]+$') {
            throw (New-OnboardingContractError -Code 'INVALID_PROVIDER_RESOURCE_ID' -Field '$.actions.providerResourceId')
        }
    }
    foreach ($diagnostic in (Assert-OnboardingArray -Value $receipt.diagnosticCodes -Field '$.diagnosticCodes' -MaximumCount 32)) {
        if ([string]$diagnostic -cnotin $script:ReceiptDiagnosticCodes) {
            throw (New-OnboardingContractError -Code 'UNKNOWN_DIAGNOSTIC_CODE' -Field '$.diagnosticCodes')
        }
    }
    foreach ($readback in (Assert-OnboardingArray -Value $receipt.readback -Field '$.readback' -MaximumCount 16)) {
        Assert-OnboardingObjectFields -Object $readback `
            -Allowed @('surface', 'resourceKey', 'classification', 'identityFingerprint') `
            -Required @('surface', 'resourceKey', 'classification', 'identityFingerprint') -Field '$.readback'
        Assert-OnboardingEnum -Value ([string]$readback.surface) -Allowed @('SHOPIFY_ADMIN', 'SHOPIFY_STOREFRONT', 'CUSTOMER_DISCOVERY', 'FIREBASE_MANAGEMENT', 'ANDROID_BUILD', 'ANDROID_RUNTIME') -Field '$.readback.surface'
        Assert-OnboardingEnum -Value ([string]$readback.classification) -Allowed @('PASS', 'FAIL', 'NOT_RUN', 'PARTIAL', 'EXTERNALLY_BLOCKED', 'NOT_APPLICABLE') -Field '$.readback.classification'
    }
    foreach ($recovery in (Assert-OnboardingArray -Value $receipt.recovery -Field '$.recovery' -MaximumCount 16)) {
        Assert-OnboardingObjectFields -Object $recovery `
            -Allowed @('ordinal', 'resourceKind', 'resourceKey', 'classification', 'nextAction') `
            -Required @('ordinal', 'resourceKind', 'resourceKey', 'classification', 'nextAction') -Field '$.recovery'
        Assert-OnboardingEnum -Value ([string]$recovery.nextAction) -Allowed @('REINSPECT', 'RESTORE_LOCAL_BACKUP', 'MANUAL_REVIEW', 'NO_ACTION') -Field '$.recovery.nextAction'
    }
    return $receipt
}

function Get-OnboardingApplicationProfile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Registry,
        [Parameter(Mandatory)][string]$Application,
        [Parameter(Mandatory)][string]$Profile
    )

    $applications = @($Registry.applications | Where-Object { [string]$_.key -ceq $Application })
    if ($applications.Count -ne 1) {
        throw (New-OnboardingContractError -Code 'UNKNOWN_APPLICATION' -Field $Application)
    }
    $profiles = @($applications[0].profiles | Where-Object { [string]$_.key -ceq $Profile })
    if ($profiles.Count -ne 1) {
        throw (New-OnboardingContractError -Code 'UNKNOWN_PROFILE' -Field "$Application/$Profile")
    }
    return [pscustomobject]@{
        Application = $applications[0]
        Profile = $profiles[0]
    }
}

function Add-OnboardingProjectionLine {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][System.Collections.Generic.List[string]]$Lines,
        [Parameter(Mandatory)][string]$Key,
        [AllowEmptyString()][Parameter(Mandatory)][string]$Value
    )

    if ($Value -ne $Value.Trim() -or $Value.Contains('\') -or $Value.Contains("`r") -or $Value.Contains("`n")) {
        throw (New-OnboardingContractError -Code 'UNSAFE_PROJECTION_VALUE' -Field $Key)
    }
    $Lines.Add("$Key=$Value")
}

function Get-OnboardingProjectionLines {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Registry,
        [Parameter(Mandatory)]$ApplicationRecord,
        [Parameter(Mandatory)]$ProfileRecord,
        [Parameter(Mandatory)][string]$RegistrySha256
    )

    if ($RegistrySha256 -cnotmatch $script:DigestPattern) {
        throw (New-OnboardingContractError -Code 'INVALID_DIGEST' -Field 'registrySha256')
    }
    if ($null -eq $ApplicationRecord.configurationProjection) {
        throw (New-OnboardingContractError -Code 'PROJECTION_DISABLED' -Field ([string]$ApplicationRecord.key))
    }
    $identity = $ApplicationRecord.identity
    $profile = $ProfileRecord
    $debugVariant = @($profile.variants | Where-Object { [string]$_.buildType -ceq 'debug' })
    $releaseVariant = @($profile.variants | Where-Object { [string]$_.buildType -ceq 'release' })
    if ($debugVariant.Count -ne 1 -or $releaseVariant.Count -ne 1) {
        throw (New-OnboardingContractError -Code 'PROJECTION_VARIANT_AMBIGUITY' -Field ([string]$profile.key))
    }
    $lines = [System.Collections.Generic.List[string]]::new()
    Add-OnboardingProjectionLine $lines 'onboarding.schemaVersion' '1'
    Add-OnboardingProjectionLine $lines 'onboarding.sourceRegistrySha256' $RegistrySha256
    Add-OnboardingProjectionLine $lines 'onboarding.application' ([string]$ApplicationRecord.key)
    Add-OnboardingProjectionLine $lines 'onboarding.profile' ([string]$profile.key)
    Add-OnboardingProjectionLine $lines 'app.brandKey' ([string]$identity.brandKey)
    Add-OnboardingProjectionLine $lines 'app.brandDisplayName' ([string]$identity.displayName)
    Add-OnboardingProjectionLine $lines 'app.profileDisplayName' ([string]$profile.displayName)
    Add-OnboardingProjectionLine $lines 'app.analyticsNamespace' ([string]$identity.analyticsNamespace)
    Add-OnboardingProjectionLine $lines 'app.environmentId' ([string]$profile.runtimeEnvironment)
    Add-OnboardingProjectionLine $lines 'app.defaultLocale' ([string]$identity.defaultLocale)
    Add-OnboardingProjectionLine $lines 'app.supportedLocales' ((@($identity.supportedLocales) | ForEach-Object { [string]$_ }) -join ',')
    Add-OnboardingProjectionLine $lines 'app.marketId' ([string]$identity.market.id)
    Add-OnboardingProjectionLine $lines 'app.marketCountryCode' ([string]$identity.market.countryCode)
    Add-OnboardingProjectionLine $lines 'app.marketCurrencyCode' ([string]$identity.market.currencyCode)
    Add-OnboardingProjectionLine $lines 'app.supportedTerritory' ([string]$identity.supportedTerritory)
    Add-OnboardingProjectionLine $lines 'app.searchNormalizationLocale' ([string]$identity.searchNormalizationLocale)
    Add-OnboardingProjectionLine $lines 'app.databaseName' ([string]$identity.databaseName)
    Add-OnboardingProjectionLine $lines 'app.customerAccountMode' ([string]$profile.customerAccount.mode)
    if ([string]$profile.customerAccount.mode -cne 'disabled') {
        Add-OnboardingProjectionLine $lines 'app.customerAccountUserAgent' ([string]$identity.customerAccount.userAgent)
        Add-OnboardingProjectionLine $lines 'app.customerAccountCallbackSchemeSuffix' ([string]$identity.customerAccount.callbackSchemeSuffix)
        Add-OnboardingProjectionLine $lines 'app.customerAccountCallbackHost' ([string]$identity.customerAccount.callbackHost)
        Add-OnboardingProjectionLine $lines 'app.customerAccountCallbackPath' ([string]$identity.customerAccount.callbackPath)
        Add-OnboardingProjectionLine $lines 'app.customerAccountScopes' ((@($identity.customerAccount.scopes) | ForEach-Object { [string]$_ }) -join ',')
    }
    Add-OnboardingProjectionLine $lines 'app.cartPreferences' ([string]$profile.protectedPersistence.cartPreferences)
    Add-OnboardingProjectionLine $lines 'app.cartKeyAlias' ([string]$profile.protectedPersistence.cartKeyAlias)
    Add-OnboardingProjectionLine $lines 'app.customerPreferences' ([string]$profile.protectedPersistence.customerPreferences)
    Add-OnboardingProjectionLine $lines 'app.customerKeyAlias' ([string]$profile.protectedPersistence.customerKeyAlias)
    Add-OnboardingProjectionLine $lines 'android.applicationId.debug' ([string]$debugVariant[0].applicationId)
    Add-OnboardingProjectionLine $lines 'android.applicationId.release' ([string]$releaseVariant[0].applicationId)
    Add-OnboardingProjectionLine $lines 'web.collectionAppLinkOrigin' ([string]$identity.webRoles.collectionAppLink.origin)
    Add-OnboardingProjectionLine $lines 'web.collectionAppLinkPathPrefix' ([string]$identity.webRoles.collectionAppLink.pathPrefix)
    Add-OnboardingProjectionLine $lines 'web.productAppLinkOrigin' ([string]$identity.webRoles.productAppLink.origin)
    Add-OnboardingProjectionLine $lines 'web.productAppLinkPathPrefix' ([string]$identity.webRoles.productAppLink.pathPrefix)
    if ($null -ne $identity.webRoles.orderAppLink) {
        Add-OnboardingProjectionLine $lines 'web.orderAppLinkOrigin' ([string]$identity.webRoles.orderAppLink.origin)
        Add-OnboardingProjectionLine $lines 'web.orderAppLinkPathPrefix' ([string]$identity.webRoles.orderAppLink.pathPrefix)
    }
    if ($null -ne $identity.webRoles.legalSupport) {
        Add-OnboardingProjectionLine $lines 'web.legalSupportOrigin' ([string]$identity.webRoles.legalSupport.origin)
        foreach ($pathName in @('support', 'privacy', 'terms', 'shipping', 'returns', 'legalNotice')) {
            Add-OnboardingProjectionLine $lines "web.legalSupportPath.$pathName" ([string]$identity.webRoles.legalSupport.paths[$pathName])
        }
    }
    Add-OnboardingProjectionLine $lines 'web.checkoutHostPolicy' ([string]$identity.webRoles.checkout.hostPolicy)
    Add-OnboardingProjectionLine $lines 'web.assetLinksMode' ([string]$identity.webRoles.assetLinks.mode)
    Add-OnboardingProjectionLine $lines 'web.manifestAutoVerify' ([string]([bool]$identity.webRoles.assetLinks.manifestAutoVerify).ToString().ToLowerInvariant())
    Add-OnboardingProjectionLine $lines 'shopify.storefrontMode' ([string]$profile.storefront.mode)
    if ($profile.storefront.Contains('domain')) {
        Add-OnboardingProjectionLine $lines 'shopify.storefrontDomain' ([string]$profile.storefront.domain)
    }
    if ($profile.storefront.Contains('apiVersion')) {
        Add-OnboardingProjectionLine $lines 'shopify.storefrontApiVersion' ([string]$profile.storefront.apiVersion)
    }
    if ($profile.storefront.Contains('mediaOrigins')) {
        Add-OnboardingProjectionLine $lines 'shopify.storefrontMediaOrigins' ((@($profile.storefront.mediaOrigins) | ForEach-Object { [string]$_ }) -join ',')
    }
    if ($profile.storefront.Contains('catalog')) {
        Add-OnboardingProjectionLine $lines 'shopify.catalogMenuHandle' ([string]$profile.storefront.catalog.menuHandle)
    }
    if ($profile.storefront.Contains('home') -and [string]$profile.storefront.home.sourceMode -cne 'disabled') {
        Add-OnboardingProjectionLine $lines 'shopify.homeRootType' ([string]$profile.storefront.home.rootType)
        Add-OnboardingProjectionLine $lines 'shopify.homeRootHandle' ([string]$profile.storefront.home.rootHandle)
        Add-OnboardingProjectionLine $lines 'shopify.homeContentSchemaVersion' ([string]$profile.storefront.home.contentSchemaVersion)
    }
    Add-OnboardingProjectionLine $lines 'firebase.mode' ([string]$profile.firebase.mode)
    if ([string]$profile.firebase.mode -cne 'disabled') {
        Add-OnboardingProjectionLine $lines 'firebase.ownershipKey' ([string]$profile.firebase.ownershipKey)
        Add-OnboardingProjectionLine $lines 'firebase.configPath.debug' ([string]$debugVariant[0].firebaseConfig)
        Add-OnboardingProjectionLine $lines 'firebase.configPath.release' ([string]$releaseVariant[0].firebaseConfig)
    }
    return $lines.ToArray()
}

function Test-OnboardingProjection {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string[]]$ExpectedLines
    )

    $expectedText = ($ExpectedLines -join "`n") + "`n"
    $expectedBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($expectedText)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw (New-OnboardingContractError -Code 'MISSING_PROJECTION' -Field $Path)
    }
    $actualBytes = [System.IO.File]::ReadAllBytes($Path)
    if ([Convert]::ToBase64String($actualBytes) -cne [Convert]::ToBase64String($expectedBytes)) {
        throw (New-OnboardingContractError -Code 'STALE_PROJECTION' -Field $Path)
    }
    $allowedKeys = @($ExpectedLines | ForEach-Object { $_.Substring(0, $_.IndexOf('=')) })
    [void](Read-OnboardingProperties -Path $Path -AllowedKeys $allowedKeys)
}

Export-ModuleMember -Function @(
    'Import-OnboardingRegistry'
    'Import-OnboardingProviderBinding'
    'Import-OnboardingReceipt'
    'Get-OnboardingApplicationProfile'
    'Get-OnboardingProjectionLines'
    'Test-OnboardingProjection'
)
