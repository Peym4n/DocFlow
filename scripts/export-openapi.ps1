param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$apiRoot = $BaseUrl.TrimEnd('/')
$contract = Invoke-RestMethod -Uri "$apiRoot/v3/api-docs"
if (-not $contract.paths.'/api/v1/documents' -or -not $contract.paths.'/api/v1/documents/{id}/metadata') {
    throw "The running API does not expose the expected document contract."
}
$yaml = Invoke-WebRequest -Uri "$apiRoot/v3/api-docs.yaml" -UseBasicParsing
$contractDirectory = Join-Path $PSScriptRoot '../contracts'
New-Item -ItemType Directory -Path $contractDirectory -Force | Out-Null
$yamlText = if ($yaml.Content -is [byte[]]) {
    [System.Text.Encoding]::UTF8.GetString($yaml.Content)
} else {
    [string]$yaml.Content
}
if (-not $yamlText.StartsWith('openapi:')) {
    throw "The API did not return an OpenAPI YAML document."
}
$target = Join-Path $contractDirectory 'openapi.yaml'
[System.IO.File]::WriteAllText($target, $yamlText, [System.Text.UTF8Encoding]::new($false))
Write-Output "Exported contracts/openapi.yaml from $apiRoot"
