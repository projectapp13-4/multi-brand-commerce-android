[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$validator = Join-Path $PSScriptRoot "Test-PublicReadiness.ps1"

if (-not (Test-Path -LiteralPath $validator -PathType Leaf)) {
    throw "Public-readiness validator is missing: $validator"
}

& pwsh -NoProfile -File $validator -SelfTest
if ($LASTEXITCODE -ne 0) {
    throw "Public-readiness validator self-tests failed with exit code $LASTEXITCODE"
}
