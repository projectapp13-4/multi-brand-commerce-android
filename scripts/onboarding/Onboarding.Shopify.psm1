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
        access = [ordered]@{ admin = 'MERCHANT_READ_WRITE'; storefront = 'PUBLIC_READ' }
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

function Test-ShopifyHomeDefinitionCompatibility {
    param(
        [Parameter(Mandatory)]$Definition,
        [Parameter(Mandatory)]$Contract,
        [Parameter(Mandatory)][hashtable]$DefinitionsByType,
        [Parameter(Mandatory)]$Schema
    )

    if ([string]$Definition.id -cnotmatch '^gid://shopify/MetaobjectDefinition/[0-9]+$' -or
        [string]$Definition.type -cne [string]$Contract.type -or
        [string]$Definition.displayNameKey -cne [string]$Contract.displayNameKey -or
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

        $expectedValidations = [System.Collections.Generic.List[string]]::new()
        foreach ($entry in $fieldContract.validations.GetEnumerator()) {
            if ([string]$entry.Key -ceq 'definitionTypes') {
                foreach ($childType in @($entry.Value)) {
                    $children = @($DefinitionsByType[[string]$childType])
                    if ($children.Count -ne 1) { return $false }
                    $expectedValidations.Add(('metaobject_definition_id={0}' -f [string]$children[0].id))
                }
            } else {
                $expectedValidations.Add(('{0}={1}' -f [string]$entry.Key, [string]$entry.Value))
            }
        }
        $actualValidations = @(
            foreach ($validation in @($actualField.validations)) {
                '{0}={1}' -f [string]$validation.name, [string]$validation.value
            }
        )
        if ((@($expectedValidations | Sort-Object -CaseSensitive) -join "`n") -cne
            (@($actualValidations | Sort-Object -CaseSensitive) -join "`n")) {
            return $false
        }
    }
    return $true
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
                                    [ordered]@{ name = [string]$validation.name; value = [string]$validation.value }
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
    $query = 'query Gate8Menu($handle:String!){menu(handle:$handle){id handle title items{id title type url resourceId items{id title type url resourceId items{id title type url resourceId}}}}}'
    $data = Invoke-ShopifyAdminOperation $Binding $Token $query @{ handle = $Handle } $Transport
    $classification = if ($null -eq $data.menu) { 'ABSENT' } else { 'CORRECT' }
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ handle = $Handle; menu = $data.menu })
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
    if ($null -ne $probe) {
        $fields = @{}; foreach ($field in @($probe.fields)) { $fields[[string]$field.key] = [string]$field.value }
        if ([string]$probe.type -ceq 'mobile_home' -and [string]$probe.handle -ceq 'gate8-operator-acceptance-v1' -and
            $fields.Count -eq 2 -and $fields.schema_version -ceq '1' -and $fields.declared_section_count -ceq '0' -and
            [string]$probe.capabilities.publishable.status -ceq 'DRAFT') { $classification = 'COMPATIBLE' }
        else { $classification = 'INCOMPATIBLE' }
    }
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ probe = $probe })
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    [pscustomobject]@{ Classification = $classification; Fingerprint = $hash; ResourceId = if ($null -eq $probe) { $null } else { [string]$probe.id } }
}

Export-ModuleMember -Function @('Import-ShopifyHomeSchemaContract', 'Get-ShopifyVerifiedTargetState', 'Get-ShopifyHomeDefinitionState', 'Get-ShopifyMenuState', 'Get-ShopifyAcceptanceProbeState', 'New-ShopifyHomeDefinition', 'New-ShopifyAcceptanceProbe')
