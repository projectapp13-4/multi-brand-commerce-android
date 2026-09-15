Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Onboarding.Common.psm1') -Force

function Invoke-ShopifyAdminOperation {
    param($Binding, [string]$Token, [string]$Query, [hashtable]$Variables, [scriptblock]$Transport)
    if ([string]::IsNullOrWhiteSpace($Token)) { throw 'MISSING_SHOPIFY_ADMIN_TOKEN' }
    $uri = [uri]"https://$($Binding.shopify.adminShopDomain)/admin/api/2026-07/graphql.json"
    $body = [ordered]@{ query = $Query; variables = $Variables } | ConvertTo-Json -Depth 12 -Compress
    $response = Invoke-OnboardingJsonRequest -Method POST -Uri $uri -Headers @{ 'X-Shopify-Access-Token' = $Token } -Body $body -Transport $Transport
    if ($response.StatusCode -ne 200 -or ($response.Data -is [Collections.IDictionary] -and $response.Data.Contains('errors'))) {
        throw 'SHOPIFY_ADMIN_OPERATION_FAILED'
    }
    return $response.Data.data
}

function Import-ShopifyHomeSchemaContract {
    [CmdletBinding()]
    param()

    $repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $path = Join-Path $repositoryRoot 'config\onboarding\shopify-home-schema.v1.json'
    $schema = Read-OnboardingStrictJson -Path $path -MaximumBytes 65536
    $expected = [ordered]@{
        schemaVersion = 1
        contract = 'gate7-v1'
        adminApiVersion = '2026-07'
        access = [ordered]@{ admin = 'PUBLIC_READ_WRITE'; storefront = 'PUBLIC_READ' }
        capabilities = [ordered]@{ publishable = $true }
        definitions = @(
            [ordered]@{
                type = 'mobile_home_collection_grid'
                displayNameKey = 'title'
                fields = @(
                    [ordered]@{ key = 'title'; type = 'single_line_text_field'; required = $true; validations = [ordered]@{ min = 1; max = 80 } }
                    [ordered]@{ key = 'collections'; type = 'list.collection_reference'; required = $true; validations = [ordered]@{ min = 1; max = 6 } }
                )
            }
            [ordered]@{
                type = 'mobile_home_featured_product'
                displayNameKey = 'title'
                fields = @(
                    [ordered]@{ key = 'title'; type = 'single_line_text_field'; required = $true; validations = [ordered]@{ min = 1; max = 80 } }
                    [ordered]@{ key = 'product'; type = 'product_reference'; required = $true; validations = [ordered]@{} }
                )
            }
            [ordered]@{
                type = 'mobile_home'
                displayNameKey = 'schema_version'
                fields = @(
                    [ordered]@{ key = 'schema_version'; type = 'number_integer'; required = $true; validations = [ordered]@{ min = 1; max = 1 } }
                    [ordered]@{ key = 'declared_section_count'; type = 'number_integer'; required = $true; validations = [ordered]@{ min = 0; max = 2 } }
                    [ordered]@{ key = 'sections'; type = 'list.mixed_reference'; required = $false; validations = [ordered]@{ max = 2; definitionTypes = @('mobile_home_collection_grid', 'mobile_home_featured_product') } }
                )
            }
        )
    }
    if ((Get-OnboardingCanonicalJson $schema) -cne (Get-OnboardingCanonicalJson $expected)) {
        throw 'UNSUPPORTED_HOME_SCHEMA_CONTRACT'
    }
    return $schema
}

function ConvertFrom-ShopifyDefinitionIdsValidation {
    param([Parameter(Mandatory)][string]$Value)

    $document = $null
    try {
        $options = [System.Text.Json.JsonDocumentOptions]::new()
        $options.AllowTrailingCommas = $false
        $options.CommentHandling = [System.Text.Json.JsonCommentHandling]::Disallow
        $options.MaxDepth = 4
        $document = [System.Text.Json.JsonDocument]::Parse($Value, $options)
        if ($document.RootElement.ValueKind -ne [System.Text.Json.JsonValueKind]::Array) {
            throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
        }
        $ids = [System.Collections.Generic.List[string]]::new()
        $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($element in $document.RootElement.EnumerateArray()) {
            if ($element.ValueKind -ne [System.Text.Json.JsonValueKind]::String) {
                throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
            }
            $id = [string]$element.GetString()
            if ($id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$' -or -not $seen.Add($id)) {
                throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
            }
            $ids.Add($id)
        }
        return [string[]]@($ids | Sort-Object -CaseSensitive)
    } catch {
        throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
    } finally {
        if ($null -ne $document) { $document.Dispose() }
    }
}

function Get-ShopifyExpectedHomeValidationTokens {
    param(
        [Parameter(Mandatory)]$FieldContract,
        [Parameter(Mandatory)][hashtable]$DefinitionsByType
    )

    $tokens = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $FieldContract.validations.GetEnumerator()) {
        if ([string]$entry.Key -ceq 'definitionTypes') {
            $ids = [System.Collections.Generic.List[string]]::new()
            foreach ($childType in @($entry.Value)) {
                $children = @($DefinitionsByType[[string]$childType])
                if ($children.Count -ne 1 -or
                    [string]$children[0].id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$') {
                    throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
                }
                $ids.Add([string]$children[0].id)
            }
            $tokens.Add(('definitionTypes={0}' -f (@($ids | Sort-Object -CaseSensitive) -join ',')))
        } else {
            $tokens.Add(('{0}={1}' -f [string]$entry.Key, [string]$entry.Value))
        }
    }
    return [string[]]@($tokens | Sort-Object -CaseSensitive)
}

function Get-ShopifyReadbackHomeValidationTokens {
    param(
        [Parameter(Mandatory)]$Field,
        [Parameter(Mandatory)]$FieldContract
    )

    $fieldType = [string]$FieldContract.type
    $isList = $fieldType.StartsWith('list.', [System.StringComparison]::Ordinal)
    $tokens = [System.Collections.Generic.List[string]]::new()
    foreach ($validation in @($Field.validations)) {
        $name = [string]$validation.name
        $value = [string]$validation.value
        if ($name -cin @('min', 'max')) {
            if ($isList) { throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID' }
            $tokens.Add(('{0}={1}' -f $name, $value))
        } elseif ($name -cin @('list.min', 'list.max')) {
            if (-not $isList) { throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID' }
            $tokens.Add(('{0}={1}' -f $name.Substring(5), $value))
        } elseif ($name -ceq 'metaobject_definition_ids') {
            if ($fieldType -cnotin @('mixed_reference', 'list.mixed_reference')) {
                throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
            }
            $ids = ConvertFrom-ShopifyDefinitionIdsValidation -Value $value
            $tokens.Add(('definitionTypes={0}' -f ($ids -join ',')))
        } elseif ($name -ceq 'metaobject_definition_id') {
            if ($fieldType -cnotin @('metaobject_reference', 'list.metaobject_reference') -or
                $value -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$') {
                throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
            }
            $tokens.Add(('definitionTypes={0}' -f $value))
        } else {
            throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
        }
    }
    return [string[]]@($tokens | Sort-Object -CaseSensitive)
}

function Test-ShopifyHomeUsesProviderNullDisplayName {
    param([Parameter(Mandatory)]$Contract)

    if ([string]$Contract.type -cne 'mobile_home' -or
        [string]$Contract.displayNameKey -cne 'schema_version') {
        return $false
    }
    $displayFields = @($Contract.fields | Where-Object {
        [string]$_.key -ceq 'schema_version' -and [string]$_.type -ceq 'number_integer'
    })
    return $displayFields.Count -eq 1
}

function Test-ShopifyHomeDisplayNameCompatibility {
    param(
        [Parameter(Mandatory)]$Definition,
        [Parameter(Mandatory)]$Contract
    )

    if ($null -ne $Definition.displayNameKey) {
        return [string]$Definition.displayNameKey -ceq [string]$Contract.displayNameKey
    }
    return Test-ShopifyHomeUsesProviderNullDisplayName -Contract $Contract
}

function ConvertTo-ShopifyHomeDefinitionCreateInput {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Contract,
        [Parameter(Mandatory)][hashtable]$DefinitionIdsByType
    )

    $fields = @(
        foreach ($fieldContract in @($Contract.fields)) {
            $validations = [System.Collections.Generic.List[object]]::new()
            foreach ($entry in $fieldContract.validations.GetEnumerator()) {
                $semanticName = [string]$entry.Key
                if ($semanticName -ceq 'definitionTypes') {
                    $ids = [System.Collections.Generic.List[string]]::new()
                    foreach ($childType in @($entry.Value)) {
                        $id = [string]$DefinitionIdsByType[[string]$childType]
                        if ($id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$') {
                            throw 'SHOPIFY_DEFINITION_DEPENDENCY_MISSING'
                        }
                        $ids.Add($id)
                    }
                    $orderedIds = [string[]]@($ids | Sort-Object -CaseSensitive)
                    if ([string]$fieldContract.type -cin @('mixed_reference', 'list.mixed_reference')) {
                        $validations.Add(@{
                            name = 'metaobject_definition_ids'
                            value = ConvertTo-Json -InputObject $orderedIds -Compress
                        })
                    } elseif ([string]$fieldContract.type -cin @('metaobject_reference', 'list.metaobject_reference') -and
                        $orderedIds.Count -eq 1) {
                        $validations.Add(@{ name = 'metaobject_definition_id'; value = $orderedIds[0] })
                    } else {
                        throw 'SHOPIFY_DEFINITION_VALIDATION_INVALID'
                    }
                } else {
                    $providerName = if (
                        [string]$fieldContract.type -clike 'list.*' -and
                        $semanticName -cin @('min', 'max')
                    ) {
                        'list.' + $semanticName
                    } else {
                        $semanticName
                    }
                    $validations.Add(@{ name = $providerName; value = [string]$entry.Value })
                }
            }
            @{
                name = ([string]$fieldContract.key -replace '_', ' ')
                key = [string]$fieldContract.key
                type = [string]$fieldContract.type
                required = [bool]$fieldContract.required
                validations = @($validations | Sort-Object { [string]$_.name })
            }
        }
    )
    $definition = @{
        name = ([string]$Contract.type -replace '_', ' ')
        type = [string]$Contract.type
        access = @{ storefront = 'PUBLIC_READ' }
        capabilities = @{ publishable = @{ enabled = $true } }
        fieldDefinitions = $fields
    }
    if (-not (Test-ShopifyHomeUsesProviderNullDisplayName -Contract $Contract)) {
        $definition.displayNameKey = [string]$Contract.displayNameKey
    }
    return $definition
}

function Test-ShopifyHomeDefinitionCompatibility {
    param(
        [Parameter(Mandatory)]$Definition,
        [Parameter(Mandatory)]$Contract,
        [Parameter(Mandatory)][hashtable]$DefinitionsByType,
        [Parameter(Mandatory)]$Schema
    )

    try {
        if ([string]$Definition.id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$' -or
            [string]$Definition.type -cne [string]$Contract.type -or
            -not (Test-ShopifyHomeDisplayNameCompatibility -Definition $Definition -Contract $Contract) -or
            [string]$Definition.access.admin -cne [string]$Schema.access.admin -or
            [string]$Definition.access.storefront -cne [string]$Schema.access.storefront -or
            $Definition.capabilities.publishable.enabled -ne $true) {
            return $false
        }

        $actualFields = @($Definition.fieldDefinitions)
        $expectedFields = @($Contract.fields)
        if ($actualFields.Count -ne $expectedFields.Count) { return $false }
        foreach ($fieldContract in $expectedFields) {
            $matchingFields = @($actualFields | Where-Object { [string]$_.key -ceq [string]$fieldContract.key })
            if ($matchingFields.Count -ne 1) { return $false }
            $actualField = $matchingFields[0]
            if ([string]$actualField.type.name -cne [string]$fieldContract.type -or
                [bool]$actualField.required -ne [bool]$fieldContract.required) {
                return $false
            }
            $expectedValidations = Get-ShopifyExpectedHomeValidationTokens -FieldContract $fieldContract -DefinitionsByType $DefinitionsByType
            $actualValidations = Get-ShopifyReadbackHomeValidationTokens -Field $actualField -FieldContract $fieldContract
            if (($expectedValidations -join "`n") -cne ($actualValidations -join "`n")) {
                return $false
            }
        }
        return $true
    } catch {
        return $false
    }
}

function Get-ShopifyHomeDefinitionFingerprint {
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Definitions)

    $normalized = @(
        foreach ($definition in @($Definitions | Sort-Object type)) {
            [ordered]@{
                id = [string]$definition.id
                type = [string]$definition.type
                displayNameKey = [string]$definition.displayNameKey
                fields = @(
                    foreach ($field in @($definition.fieldDefinitions | Sort-Object key)) {
                        [ordered]@{
                            key = [string]$field.key
                            type = [string]$field.type.name
                            required = [bool]$field.required
                            validations = @(
                                foreach ($validation in @($field.validations | Sort-Object name, value)) {
                                    $value = [string]$validation.value
                                    if ([string]$validation.name -ceq 'metaobject_definition_ids') {
                                        try {
                                            $ids = ConvertFrom-ShopifyDefinitionIdsValidation -Value $value
                                            $value = ConvertTo-Json -InputObject $ids -Compress
                                        } catch {
                                            # Incompatible provider state still needs a stable fingerprint.
                                        }
                                    }
                                    [ordered]@{ name = [string]$validation.name; value = $value }
                                }
                            )
                        }
                    }
                )
                access = [ordered]@{ admin = [string]$definition.access.admin; storefront = [string]$definition.access.storefront }
                publishable = [bool]$definition.capabilities.publishable.enabled
            }
        }
    )
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ definitions = $normalized })
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
}

function Get-ShopifyVerifiedTargetState {
    [CmdletBinding()]
    param($Binding, [string]$Token, [scriptblock]$Transport)

    $query = 'query Gate8VerifyShop{shop{id myshopifyDomain}}'
    $data = Invoke-ShopifyAdminOperation $Binding $Token $query @{} $Transport
    $shopId = [string]$data.shop.id
    if ($shopId -cnotmatch '^gid://shopify/Shop/(?<id>[0-9]+)$' -or
        [string]$Matches.id -cne [string]$Binding.shopify.shopId -or
        [string]$data.shop.myshopifyDomain -cne [string]$Binding.shopify.adminShopDomain) {
        throw 'SHOPIFY_TARGET_IDENTITY_MISMATCH'
    }
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ id = $shopId; myshopifyDomain = [string]$data.shop.myshopifyDomain })
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    return [pscustomobject]@{ Classification = 'PASS'; Fingerprint = $hash }
}

function Get-ShopifyHomeDefinitionState {
    [CmdletBinding()]
    param($Binding, [string]$Token, [scriptblock]$Transport)
    $schema = Import-ShopifyHomeSchemaContract
    $query = 'query Gate8HomeDefinitions($first:Int!,$after:String){metaobjectDefinitions(first:$first,after:$after){nodes{id type name displayNameKey fieldDefinitions{key name type{name} required validations{name value}} capabilities{publishable{enabled}} access{admin storefront}} pageInfo{hasNextPage endCursor}}}'
    $nodes = [System.Collections.Generic.List[object]]::new()
    $after = $null
    for ($page = 1; $page -le 5; $page++) {
        $data = Invoke-ShopifyAdminOperation $Binding $Token $query @{ first = 100; after = $after } $Transport
        foreach ($node in @($data.metaobjectDefinitions.nodes)) { $nodes.Add($node) }
        if (-not [bool]$data.metaobjectDefinitions.pageInfo.hasNextPage) { break }
        $next = [string]$data.metaobjectDefinitions.pageInfo.endCursor
        if ([string]::IsNullOrWhiteSpace($next) -or $next -ceq $after -or $page -eq 5) { throw 'SHOPIFY_PAGINATION_INCOMPLETE' }
        $after = $next
    }
    $managedTypes = @('mobile_home_collection_grid', 'mobile_home_featured_product', 'mobile_home')
    $selected = @($nodes | Where-Object { [string]$_.type -cin $managedTypes })
    $byType = @{}
    foreach ($type in $managedTypes) { $byType[$type] = @($selected | Where-Object { [string]$_.type -ceq $type }) }
    if (@($byType.Values | Where-Object { $_.Count -gt 1 }).Count -gt 0) { throw 'SHOPIFY_DEFINITION_IDENTITY_CONFLICT' }
    $compatible = $true
    foreach ($contract in @($schema.definitions)) {
        $definitions = @($byType[[string]$contract.type])
        if ($definitions.Count -eq 1 -and
            -not (Test-ShopifyHomeDefinitionCompatibility -Definition $definitions[0] -Contract $contract -DefinitionsByType $byType -Schema $schema)) {
            $compatible = $false
        }
    }
    $classification = if ($selected.Count -eq 0) { 'ABSENT' } elseif ($compatible) { 'COMPATIBLE' } else { 'INCOMPATIBLE' }
    $missingTypes = @($managedTypes | Where-Object { @($byType[$_]).Count -eq 0 })
    $hash = Get-ShopifyHomeDefinitionFingerprint -Definitions $selected
    [pscustomobject]@{ Classification = $classification; Fingerprint = $hash; Definitions = $byType; MissingTypes = $missingTypes }
}

function Get-ShopifyMenuState {
    [CmdletBinding()]
    param($Binding, [string]$Token, [string]$Handle, [scriptblock]$Transport)
    $query = 'query Gate8Menus($first:Int!,$after:String){menus(first:$first,after:$after){nodes{id handle title items{id title type url resourceId items{id title type url resourceId items{id title type url resourceId}}}} pageInfo{hasNextPage endCursor}}}'
    $nodes = [System.Collections.Generic.List[object]]::new()
    $after = $null
    for ($page = 1; $page -le 5; $page++) {
        $data = Invoke-ShopifyAdminOperation $Binding $Token $query @{ first = 100; after = $after } $Transport
        foreach ($node in @($data.menus.nodes)) { $nodes.Add($node) }
        if (-not [bool]$data.menus.pageInfo.hasNextPage) { break }
        $next = [string]$data.menus.pageInfo.endCursor
        if ([string]::IsNullOrWhiteSpace($next) -or $next -ceq $after -or $page -eq 5) {
            throw 'SHOPIFY_PAGINATION_INCOMPLETE'
        }
        $after = $next
    }
    $matches = @($nodes | Where-Object { [string]$_.handle -ceq $Handle })
    if ($matches.Count -gt 1) { throw 'SHOPIFY_MENU_IDENTITY_CONFLICT' }
    $menu = if ($matches.Count -eq 1) { $matches[0] } else { $null }
    $classification = if ($null -eq $menu) { 'ABSENT' } else { 'CORRECT' }
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ handle = $Handle; menu = $menu })
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    [pscustomobject]@{ Classification = $classification; Fingerprint = $hash }
}

function New-ShopifyHomeDefinition {
    [CmdletBinding()]
    param($Binding, [string]$Token, [hashtable]$Definition, [scriptblock]$Transport)
    $mutation = 'mutation Gate8DefinitionCreate($definition:MetaobjectDefinitionCreateInput!){metaobjectDefinitionCreate(definition:$definition){metaobjectDefinition{id type} userErrors{field code message}}}'
    $data = Invoke-ShopifyAdminOperation $Binding $Token $mutation @{ definition = $Definition } $Transport
    if (@($data.metaobjectDefinitionCreate.userErrors).Count -gt 0 -or $null -eq $data.metaobjectDefinitionCreate.metaobjectDefinition) { throw 'SHOPIFY_DEFINITION_CREATE_FAILED' }
    return $data.metaobjectDefinitionCreate.metaobjectDefinition
}

function New-ShopifyAcceptanceProbe {
    [CmdletBinding()]
    param($Binding, [string]$Token, [scriptblock]$Transport)
    $mutation = 'mutation Gate8ProbeCreate($metaobject:MetaobjectCreateInput!){metaobjectCreate(metaobject:$metaobject){metaobject{id type handle capabilities{publishable{status}}} userErrors{field code message}}}'
    $input = @{ type = 'mobile_home'; handle = 'gate8-operator-acceptance-v1'; values = @{ schema_version = '1'; declared_section_count = '0' }; capabilities = @{ publishable = @{ status = 'DRAFT' } } }
    $data = Invoke-ShopifyAdminOperation $Binding $Token $mutation @{ metaobject = $input } $Transport
    if (@($data.metaobjectCreate.userErrors).Count -gt 0 -or $null -eq $data.metaobjectCreate.metaobject) { throw 'SHOPIFY_PROBE_CREATE_FAILED' }
    return $data.metaobjectCreate.metaobject
}

function Get-ShopifyAcceptanceProbeState {
    [CmdletBinding()]
    param($Binding, [string]$Token, [scriptblock]$Transport)
    $query = 'query Gate8Probe{metaobjectByHandle(handle:{type:"mobile_home",handle:"gate8-operator-acceptance-v1"}){id type handle fields{key value} capabilities{publishable{status}}}}'
    $data = Invoke-ShopifyAdminOperation $Binding $Token $query @{} $Transport
    $probe = $data.metaobjectByHandle
    $classification = if ($null -eq $probe) { 'ABSENT' } else { 'UNKNOWN' }
    $fingerprintValue = [ordered]@{ probe = $probe }
    if ($null -ne $probe) {
        $fields = @{}
        $validFields = $true
        foreach ($field in @($probe.fields)) {
            $hasKey = if ($field -is [Collections.IDictionary]) {
                $field.Contains('key')
            } else {
                $null -ne $field.PSObject.Properties['key']
            }
            $hasValue = if ($field -is [Collections.IDictionary]) {
                $field.Contains('value')
            } else {
                $null -ne $field.PSObject.Properties['value']
            }
            if (-not $hasKey -or -not $hasValue) {
                $validFields = $false
                continue
            }
            $key = [string]$field.key
            if ($key -cnotin @('schema_version', 'declared_section_count', 'sections') -or $fields.ContainsKey($key)) {
                $validFields = $false
                continue
            }
            $fields[$key] = $field.value
        }
        $hasRequiredFields = $fields.ContainsKey('schema_version') -and $fields.ContainsKey('declared_section_count')
        $hasOnlySupportedCount = $fields.Count -eq 2 -or $fields.Count -eq 3
        $sectionsAreEmpty = -not $fields.ContainsKey('sections') -or $null -eq $fields['sections']
        if ($validFields -and $hasRequiredFields -and $hasOnlySupportedCount -and $sectionsAreEmpty -and
            [string]$probe.id -cmatch '^gid://shopify/Metaobject/[0-9]+$' -and
            [string]$probe.type -ceq 'mobile_home' -and [string]$probe.handle -ceq 'gate8-operator-acceptance-v1' -and
            $null -ne $fields['schema_version'] -and [string]$fields['schema_version'] -ceq '1' -and
            $null -ne $fields['declared_section_count'] -and [string]$fields['declared_section_count'] -ceq '0' -and
            [string]$probe.capabilities.publishable.status -ceq 'DRAFT') {
            $classification = 'COMPATIBLE'
            # Shopify materializes this optional list field as a nullable value
            # whose live value is null for a zero-section metaobject. Normalize
            # that one observed representation to the same semantic probe
            # contract as an omitted optional field.
            $fingerprintValue = [ordered]@{
                probe = [ordered]@{
                    id = [string]$probe.id
                    type = 'mobile_home'
                    handle = 'gate8-operator-acceptance-v1'
                    fields = [ordered]@{
                        schema_version = '1'
                        declared_section_count = '0'
                    }
                    publishableStatus = 'DRAFT'
                }
            }
        } else {
            $classification = 'INCOMPATIBLE'
        }
    }
    $canonical = Get-OnboardingCanonicalJson $fingerprintValue
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    [pscustomobject]@{ Classification = $classification; Fingerprint = $hash; ResourceId = if ($null -eq $probe) { $null } else { [string]$probe.id } }
}

Export-ModuleMember -Function @('Import-ShopifyHomeSchemaContract', 'ConvertTo-ShopifyHomeDefinitionCreateInput', 'Get-ShopifyVerifiedTargetState', 'Get-ShopifyHomeDefinitionState', 'Get-ShopifyMenuState', 'Get-ShopifyAcceptanceProbeState', 'New-ShopifyHomeDefinition', 'New-ShopifyAcceptanceProbe')
