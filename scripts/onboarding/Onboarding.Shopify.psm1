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

function Get-ShopifyHomeDefinitionState {
    [CmdletBinding()]
    param($Binding, [string]$Token, [scriptblock]$Transport)
    $query = 'query Gate8HomeDefinitions($first:Int!,$after:String){metaobjectDefinitions(first:$first,after:$after){nodes{id type name fieldDefinitions{key type{name} required validations{name value}} capabilities{publishable{enabled}} access{storefront}} pageInfo{hasNextPage endCursor}}}'
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
    $classification = if ($selected.Count -eq 0) { 'ABSENT' } elseif ($selected.Count -eq 3) { 'COMPATIBLE' } else { 'COMPATIBLE' }
    $canonical = Get-OnboardingCanonicalJson ([ordered]@{ definitions = @($selected | Sort-Object type | ForEach-Object { [ordered]@{ id = $_.id; type = $_.type; fields = $_.fieldDefinitions; capabilities = $_.capabilities; access = $_.access } }) })
    $hash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.Encoding]::UTF8.GetBytes($canonical))).ToLowerInvariant()
    [pscustomobject]@{ Classification = $classification; Fingerprint = $hash; Definitions = $byType }
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

Export-ModuleMember -Function @('Get-ShopifyHomeDefinitionState', 'Get-ShopifyMenuState', 'Get-ShopifyAcceptanceProbeState', 'New-ShopifyHomeDefinition', 'New-ShopifyAcceptanceProbe')
