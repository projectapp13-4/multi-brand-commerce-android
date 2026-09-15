Set-StrictMode -Version Latest

$script:Utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$script:OnboardingUserAgent = 'MultiBrandCommerceAndroid-Gate8/1.0 (+https://github.com/projectapp13-4/multi-brand-commerce-android)'

function New-OnboardingContractError {
    param(
        [Parameter(Mandatory)][string]$Code,
        [Parameter(Mandatory)][string]$Field
    )

    return [System.InvalidOperationException]::new(('{0}: {1}' -f $Code, $Field))
}

function Assert-OnboardingText {
    [CmdletBinding()]
    param(
        [AllowNull()][AllowEmptyString()][string]$Value,
        [Parameter(Mandatory)][string]$Field,
        [int]$MinimumLength = 1,
        [int]$MaximumLength = 256,
        [switch]$AllowEmpty
    )

    if ($null -eq $Value) {
        throw (New-OnboardingContractError -Code 'INVALID_TEXT' -Field $Field)
    }
    if ($AllowEmpty -and $Value.Length -eq 0) {
        return
    }
    if ($Value.Length -lt $MinimumLength -or $Value.Length -gt $MaximumLength) {
        throw (New-OnboardingContractError -Code 'INVALID_TEXT_LENGTH' -Field $Field)
    }
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $code = [int][char]$Value[$index]
        if (($code -ge 0 -and $code -le 0x1f) -or
            ($code -ge 0x7f -and $code -le 0x9f) -or
            ($code -ge 0x202a -and $code -le 0x202e) -or
            ($code -ge 0x2066 -and $code -le 0x2069) -or
            ($code -ge 0xfdd0 -and $code -le 0xfdef) -or
            (($code -band 0xfffe) -eq 0xfffe)) {
            throw (New-OnboardingContractError -Code 'UNSAFE_UNICODE' -Field $Field)
        }
        if ([char]::IsHighSurrogate($Value[$index])) {
            if ($index + 1 -ge $Value.Length -or -not [char]::IsLowSurrogate($Value[$index + 1])) {
                throw (New-OnboardingContractError -Code 'UNPAIRED_SURROGATE' -Field $Field)
            }
            $scalar = [char]::ConvertToUtf32($Value[$index], $Value[$index + 1])
            if (($scalar -band 0xffff) -in @(0xfffe, 0xffff)) {
                throw (New-OnboardingContractError -Code 'UNICODE_NONCHARACTER' -Field $Field)
            }
            $index++
        } elseif ([char]::IsLowSurrogate($Value[$index])) {
            throw (New-OnboardingContractError -Code 'UNPAIRED_SURROGATE' -Field $Field)
        }
    }
}

function Assert-OnboardingObjectFields {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Object,
        [Parameter(Mandatory)][string[]]$Allowed,
        [string[]]$Required = @(),
        [Parameter(Mandatory)][string]$Field
    )

    foreach ($key in @($Object.Keys)) {
        if ([string]$key -cnotin $Allowed) {
            throw (New-OnboardingContractError -Code 'UNKNOWN_FIELD' -Field "$Field.$key")
        }
    }
    foreach ($requiredName in $Required) {
        if (-not $Object.Contains($requiredName)) {
            throw (New-OnboardingContractError -Code 'MISSING_FIELD' -Field "$Field.$requiredName")
        }
    }
}

function Test-OnboardingJsonElement {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Field
    )

    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw (New-OnboardingContractError -Code 'DUPLICATE_FIELD' -Field "$Field.$($property.Name)")
            }
            Assert-OnboardingText -Value $property.Name -Field "$Field.propertyName"
            Test-OnboardingJsonElement -Element $property.Value -Field "$Field.$($property.Name)"
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Test-OnboardingJsonElement -Element $item -Field "$Field[$index]"
            $index++
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::String) {
        Assert-OnboardingText -Value $Element.GetString() -Field $Field -AllowEmpty
    }
}

function Get-OnboardingRepositoryRoot {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$StartPath)

    $candidate = [System.IO.Path]::GetFullPath($StartPath)
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        $candidate = [System.IO.Path]::GetDirectoryName($candidate)
    }
    while (-not [string]::IsNullOrWhiteSpace($candidate)) {
        if (Test-Path -LiteralPath (Join-Path $candidate '.git')) {
            return $candidate
        }
        $parent = [System.IO.Directory]::GetParent($candidate)
        if ($null -eq $parent) {
            break
        }
        $candidate = $parent.FullName
    }
    throw (New-OnboardingContractError -Code 'REPOSITORY_ROOT_NOT_FOUND' -Field $StartPath)
}

function Get-OnboardingSha256 {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw (New-OnboardingContractError -Code 'MISSING_FILE' -Field $Path)
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-OnboardingStrictJson {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [int]$MaximumBytes = 262144
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw (New-OnboardingContractError -Code 'MISSING_FILE' -Field $Path)
    }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -gt $MaximumBytes) {
        throw (New-OnboardingContractError -Code 'FILE_TOO_LARGE' -Field $Path)
    }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf) {
        throw (New-OnboardingContractError -Code 'UTF8_BOM_FORBIDDEN' -Field $Path)
    }
    try {
        $text = $script:Utf8Strict.GetString($bytes)
        $options = [System.Text.Json.JsonDocumentOptions]::new()
        $options.AllowTrailingCommas = $false
        $options.CommentHandling = [System.Text.Json.JsonCommentHandling]::Disallow
        $options.MaxDepth = 32
        $document = [System.Text.Json.JsonDocument]::Parse($text, $options)
        try {
            Test-OnboardingJsonElement -Element $document.RootElement -Field '$'
        } finally {
            $document.Dispose()
        }
        return ($text | ConvertFrom-Json -AsHashtable -Depth 32 -DateKind String)
    } catch [System.InvalidOperationException] {
        throw
    } catch {
        throw (New-OnboardingContractError -Code 'INVALID_JSON' -Field $Path)
    }
}

function Read-OnboardingProperties {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string[]]$AllowedKeys,
        [int]$MaximumBytes = 262144,
        [switch]$AllowEmptyValues
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw (New-OnboardingContractError -Code 'MISSING_FILE' -Field $Path)
    }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -gt $MaximumBytes) {
        throw (New-OnboardingContractError -Code 'FILE_TOO_LARGE' -Field $Path)
    }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf) {
        throw (New-OnboardingContractError -Code 'UTF8_BOM_FORBIDDEN' -Field $Path)
    }
    try {
        $text = $script:Utf8Strict.GetString($bytes)
    } catch {
        throw (New-OnboardingContractError -Code 'INVALID_UTF8' -Field $Path)
    }
    if ($text.Contains("`r")) {
        throw (New-OnboardingContractError -Code 'CRLF_FORBIDDEN' -Field $Path)
    }
    $values = [ordered]@{}
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $lines = $text.Split("`n")
    for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex++) {
        $line = $lines[$lineIndex]
        $lineNumber = $lineIndex + 1
        if ($lineIndex -eq $lines.Count - 1 -and $line.Length -eq 0) {
            continue
        }
        if ($line.Length -eq 0 -or $line.StartsWith('#')) {
            throw (New-OnboardingContractError -Code 'INVALID_PROPERTY_LINE' -Field "${Path}:$lineNumber")
        }
        if ($line.Contains('\')) {
            throw (New-OnboardingContractError -Code 'PROPERTY_ESCAPE_FORBIDDEN' -Field "${Path}:$lineNumber")
        }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) {
            throw (New-OnboardingContractError -Code 'INVALID_PROPERTY_LINE' -Field "${Path}:$lineNumber")
        }
        $key = $line.Substring(0, $separator)
        $value = $line.Substring($separator + 1)
        if ($key -ne $key.Trim() -or $value -ne $value.Trim()) {
            throw (New-OnboardingContractError -Code 'PROPERTY_WHITESPACE' -Field "${Path}:$lineNumber")
        }
        if ($key -cnotin $AllowedKeys) {
            throw (New-OnboardingContractError -Code 'UNKNOWN_PROPERTY' -Field $key)
        }
        if (-not $seen.Add($key)) {
            throw (New-OnboardingContractError -Code 'DUPLICATE_PROPERTY' -Field $key)
        }
        Assert-OnboardingText -Value $key -Field $key
        Assert-OnboardingText -Value $value -Field $key -MaximumLength 4096 -AllowEmpty:$AllowEmptyValues
        $values[$key] = $value
    }
    return $values
}

function Test-OnboardingSafeRelativePath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$RelativePath,
        [Parameter(Mandatory)][string]$Field
    )

    Assert-OnboardingText -Value $RelativePath -Field $Field
    if ([System.IO.Path]::IsPathRooted($RelativePath) -or
        $RelativePath.Contains('\') -or
        $RelativePath.StartsWith('/') -or
        $RelativePath -match '(^|/)\.{1,2}(/|$)') {
        throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
    }
    $reserved = '^(?i:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)'
    foreach ($segment in $RelativePath.Split('/')) {
        if ($segment -match $reserved) {
            throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
        }
    }
    $comparison = if ($IsWindows) { [System.StringComparison]::OrdinalIgnoreCase } else { [System.StringComparison]::Ordinal }
    try {
        $rootItem = Get-Item -LiteralPath ([System.IO.Path]::GetFullPath($RepositoryRoot)) -Force -ErrorAction Stop
        if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            $rootItem = $rootItem.ResolveLinkTarget($true)
        }
        if ($null -eq $rootItem -or -not $rootItem.PSIsContainer) {
            throw 'root is not a directory'
        }
        $canonicalRoot = [System.IO.Path]::GetFullPath($rootItem.FullName).TrimEnd('\', '/')
    } catch {
        throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
    }

    $rootPrefix = $canonicalRoot + [System.IO.Path]::DirectorySeparatorChar
    $current = $canonicalRoot
    foreach ($segment in $RelativePath.Split('/')) {
        $candidate = [System.IO.Path]::GetFullPath((Join-Path $current $segment))
        $existing = Get-Item -LiteralPath $candidate -Force -ErrorAction SilentlyContinue
        if ($null -ne $existing) {
            if (($existing.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                try {
                    $existing = $existing.ResolveLinkTarget($true)
                } catch {
                    throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
                }
                if ($null -eq $existing) {
                    throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
                }
            }
            $candidate = [System.IO.Path]::GetFullPath($existing.FullName)
        }
        if (-not $candidate.Equals($canonicalRoot, $comparison) -and
            -not $candidate.StartsWith($rootPrefix, $comparison)) {
            throw (New-OnboardingContractError -Code 'UNSAFE_PATH' -Field $Field)
        }
        $current = $candidate
    }
    return $current
}

function Assert-OnboardingHost {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][string]$Field,
        [switch]$AllowInvalidTld
    )

    Assert-OnboardingText -Value $HostName -Field $Field -MaximumLength 253
    if ($HostName -cne $HostName.ToLowerInvariant() -or
        $HostName.EndsWith('.') -or
        $HostName.Contains('*') -or
        $HostName.Contains(':') -or
        $HostName -eq 'localhost') {
        throw (New-OnboardingContractError -Code 'UNSAFE_HOST' -Field $Field)
    }
    $ip = $null
    if ([System.Net.IPAddress]::TryParse($HostName, [ref]$ip)) {
        throw (New-OnboardingContractError -Code 'UNSAFE_HOST' -Field $Field)
    }
    $ascii = [System.Globalization.IdnMapping]::new().GetAscii($HostName)
    if ($ascii -cne $HostName -or $HostName -notmatch '^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$') {
        throw (New-OnboardingContractError -Code 'UNSAFE_HOST' -Field $Field)
    }
    if (-not $AllowInvalidTld -and $HostName.EndsWith('.invalid')) {
        throw (New-OnboardingContractError -Code 'UNSAFE_HOST' -Field $Field)
    }
}

function Get-OnboardingCanonicalJson {
    param([Parameter(Mandatory)][AllowNull()][AllowEmptyCollection()]$Value)
    return (ConvertTo-Json -InputObject $Value -Depth 32 -Compress)
}

function Protect-OnboardingOutput {
    param(
        [AllowEmptyString()][string]$Text,
        [string[]]$SensitiveValues = @()
    )
    $safe = $Text
    foreach ($value in @($SensitiveValues | Where-Object { -not [string]::IsNullOrEmpty($_) } | Sort-Object Length -Descending)) {
        $safe = $safe.Replace([string]$value, '<redacted>')
    }
    return $safe
}

function Test-OnboardingShopifyCustomerUri {
    param([Parameter(Mandatory)][uri]$Uri)

    $hostName = $Uri.IdnHost.ToLowerInvariant()
    return $Uri.Scheme -ceq 'https' -and
        $Uri.IsDefaultPort -and
        $Uri.UserInfo.Length -eq 0 -and
        $Uri.Fragment.Length -eq 0 -and
        ($hostName -ceq 'shopify.com' -or $hostName.EndsWith('.shopify.com', [System.StringComparison]::Ordinal))
}

function Read-OnboardingBoundedStream {
    param(
        [Parameter(Mandatory)][System.IO.Stream]$Stream,
        [Parameter(Mandatory)][int]$MaximumBytes,
        [Parameter(Mandatory)][System.Threading.CancellationToken]$CancellationToken
    )

    $memory = [System.IO.MemoryStream]::new()
    try {
        $buffer = [byte[]]::new(8192)
        while (($read = $Stream.ReadAsync($buffer, 0, $buffer.Length, $CancellationToken).GetAwaiter().GetResult()) -gt 0) {
            if ($memory.Length + $read -gt $MaximumBytes) {
                throw 'PROVIDER_RESPONSE_TOO_LARGE'
            }
            $memory.Write($buffer, 0, $read)
        }
        return $memory.ToArray()
    } catch [System.OperationCanceledException] {
        throw 'PROVIDER_RESPONSE_TIMEOUT'
    } finally {
        $memory.Dispose()
    }
}

function ConvertFrom-OnboardingJsonResponse {
    param(
        [Parameter(Mandatory)][int]$StatusCode,
        [Parameter(Mandatory)][AllowEmptyCollection()][byte[]]$RawBytes,
        $Headers,
        [Parameter(Mandatory)][int]$MaximumBytes
    )

    if ($RawBytes.Length -gt $MaximumBytes) { throw 'PROVIDER_RESPONSE_TOO_LARGE' }
    if ($StatusCode -ge 300 -and $StatusCode -lt 400) { throw 'PROVIDER_REDIRECT_BLOCKED' }
    if ($StatusCode -lt 200 -or $StatusCode -ge 300) {
        return [pscustomobject]@{ StatusCode = $StatusCode; Data = $null; Headers = $Headers }
    }
    try {
        $text = $script:Utf8Strict.GetString($RawBytes)
        $data = if ([string]::IsNullOrWhiteSpace($text)) {
            $null
        } else {
            $text | ConvertFrom-Json -AsHashtable -Depth 32 -NoEnumerate
        }
    } catch {
        throw 'PROVIDER_RESPONSE_INVALID_JSON'
    }
    return [pscustomobject]@{ StatusCode = $StatusCode; Data = $data; Headers = $Headers }
}

function Invoke-OnboardingJsonRequest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateSet('GET', 'POST')][string]$Method,
        [Parameter(Mandatory)][uri]$Uri,
        [hashtable]$Headers = @{},
        [AllowEmptyString()][string]$Body = '',
        [int]$MaximumBytes = 1048576,
        [int]$TimeoutSeconds = 30,
        [scriptblock]$Transport
    )
    if ($Uri.Scheme -cne 'https' -or $Uri.UserInfo.Length -ne 0 -or $Uri.Fragment.Length -ne 0) {
        throw (New-OnboardingContractError -Code 'UNSAFE_PROVIDER_URI' -Field 'request')
    }
    $effectiveHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $effectiveHeaders[[string]$key] = [string]$Headers[$key]
    }
    $effectiveHeaders['User-Agent'] = $script:OnboardingUserAgent
    if ($null -ne $Transport) {
        try {
            $fixtureResponse = & $Transport $Method $Uri $effectiveHeaders $Body $MaximumBytes
        } catch [System.OperationCanceledException] {
            throw 'PROVIDER_RESPONSE_TIMEOUT'
        }
        $statusCode = [int]$fixtureResponse.StatusCode
        $fixtureHeaders = if ($fixtureResponse.PSObject.Properties.Name -contains 'Headers') {
            $fixtureResponse.Headers
        } else {
            @{}
        }
        if ($fixtureResponse.PSObject.Properties.Name -contains 'RawBytes') {
            return ConvertFrom-OnboardingJsonResponse `
                -StatusCode $statusCode `
                -RawBytes ([byte[]]$fixtureResponse.RawBytes) `
                -Headers $fixtureHeaders `
                -MaximumBytes $MaximumBytes
        }
        if ($statusCode -ge 300 -and $statusCode -lt 400) { throw 'PROVIDER_REDIRECT_BLOCKED' }
        if ($statusCode -lt 200 -or $statusCode -ge 300) {
            return [pscustomobject]@{ StatusCode = $statusCode; Data = $null; Headers = $fixtureHeaders }
        }
        $fixtureBytes = if ($null -eq $fixtureResponse.Data) { 0 } else { [Text.Encoding]::UTF8.GetByteCount((Get-OnboardingCanonicalJson $fixtureResponse.Data)) }
        if ($fixtureBytes -gt $MaximumBytes) { throw 'PROVIDER_RESPONSE_TOO_LARGE' }
        return $fixtureResponse
    }
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan
    $deadline = [System.Threading.CancellationTokenSource]::new()
    $deadline.CancelAfter([timespan]::FromSeconds($TimeoutSeconds))
    $request = $null
    $response = $null
    $stream = $null
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Uri)
        foreach ($key in $effectiveHeaders.Keys) {
            [void]$request.Headers.TryAddWithoutValidation([string]$key, [string]$effectiveHeaders[$key])
        }
        if ($Method -ceq 'POST') { $request.Content = [System.Net.Http.StringContent]::new($Body, [System.Text.Encoding]::UTF8, 'application/json') }
        $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token).GetAwaiter().GetResult()
        $statusCode = [int]$response.StatusCode
        if ($statusCode -ge 300 -and $statusCode -lt 400) { throw 'PROVIDER_REDIRECT_BLOCKED' }
        if ($statusCode -lt 200 -or $statusCode -ge 300) {
            return [pscustomobject]@{ StatusCode = $statusCode; Data = $null; Headers = $response.Headers }
        }
        $stream = $response.Content.ReadAsStreamAsync($deadline.Token).GetAwaiter().GetResult()
        $bytes = Read-OnboardingBoundedStream -Stream $stream -MaximumBytes $MaximumBytes -CancellationToken $deadline.Token
        return ConvertFrom-OnboardingJsonResponse `
            -StatusCode $statusCode `
            -RawBytes $bytes `
            -Headers $response.Headers `
            -MaximumBytes $MaximumBytes
    } catch [System.OperationCanceledException] {
        throw 'PROVIDER_RESPONSE_TIMEOUT'
    } catch {
        if ([string]$_.Exception.Message -match '^PROVIDER_') { throw }
        throw (New-OnboardingContractError -Code 'PROVIDER_TRANSPORT_FAILURE' -Field $Uri.Host)
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $request) { $request.Dispose() }
        $deadline.Dispose()
        $client.Dispose()
        $handler.Dispose()
    }
}

Export-ModuleMember -Function @(
    'Assert-OnboardingHost'
    'Assert-OnboardingObjectFields'
    'Assert-OnboardingText'
    'Get-OnboardingRepositoryRoot'
    'Get-OnboardingSha256'
    'Get-OnboardingCanonicalJson'
    'Protect-OnboardingOutput'
    'Test-OnboardingShopifyCustomerUri'
    'Invoke-OnboardingJsonRequest'
    'New-OnboardingContractError'
    'Read-OnboardingStrictJson'
    'Read-OnboardingProperties'
    'Test-OnboardingSafeRelativePath'
)
