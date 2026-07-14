[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $PSCommandPath) '../..'))
$git = Get-Command git -ErrorAction SilentlyContinue
if ($null -eq $git) { throw 'git is required for the repository hygiene gate.' }
$tracked = @(& $git.Source -C $repositoryRoot ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate tracked files.' }

$forbiddenNames = '(?i)(^|/)(local\.properties|keystore\.properties|\.env(?:\..*)?)$'
$forbiddenExtensions = '(?i)\.(jks|keystore|p12|pfx|pem|key)$'
$badFiles = @($tracked | Where-Object { $_ -match $forbiddenNames -or $_ -match $forbiddenExtensions })
if ($badFiles.Count -gt 0) { throw "Tracked secret-bearing file type detected: $($badFiles -join ', ')" }

$secretPatterns = @(
    '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
    '\bAKIA[0-9A-Z]{16}\b',
    '\bAIza[0-9A-Za-z_-]{35}\b',
    '\bgithub_pat_[0-9A-Za-z_]{20,}\b',
    '\bghp_[0-9A-Za-z]{30,}\b',
    '\bxox[baprs]-[0-9A-Za-z-]{10,}\b'
)
$findings = [Collections.Generic.List[string]]::new()
foreach ($relative in $tracked) {
    $path = Join-Path $repositoryRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
    try {
        $content = Get-Content -LiteralPath $path -Raw -ErrorAction Stop
    } catch { continue }
    foreach ($pattern in $secretPatterns) {
        if ($content -match $pattern) { $findings.Add($relative); break }
    }
}
if ($findings.Count -gt 0) {
    throw "High-confidence credential pattern detected in tracked files (values redacted): $(@($findings | Sort-Object -Unique) -join ', ')"
}
Write-Host 'Repository hygiene gate passed.'
