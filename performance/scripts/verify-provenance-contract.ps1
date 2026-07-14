[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $PSCommandPath
. (Join-Path $scriptRoot 'performance-common.ps1')

function Invoke-Git {
    param([string]$Root, [string[]]$Arguments)
    $output = @(& git -C $Root @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('newaudio-provenance-{0}' -f [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    Invoke-Git $root @('init', '-q') | Out-Null
    Invoke-Git $root @('config', 'user.email', 'newaudio-contract@example.invalid') | Out-Null
    Invoke-Git $root @('config', 'user.name', 'NewAudio Contract') | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Value 'one' -Encoding utf8
    Invoke-Git $root @('add', 'tracked.txt') | Out-Null
    Invoke-Git $root @('commit', '-q', '-m', 'fixture') | Out-Null

    $clean = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    $cleanAgain = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    if ($clean.dirty -or $clean.untrackedPresent -or
        $clean.worktreeStateSha256 -ne $cleanAgain.worktreeStateSha256) {
        throw 'Clean repository provenance is not stable.'
    }
    if (-not (Test-NewAudioBaselineEligibility -Provenance $clean -AllowDirty $false -IterationOverride $false) -or
        (Test-NewAudioBaselineEligibility -Provenance $clean -AllowDirty $true -IterationOverride $false) -or
        (Test-NewAudioBaselineEligibility -Provenance $clean -AllowDirty $false -IterationOverride $true)) {
        throw 'Baseline eligibility does not fail closed for -AllowDirty or reduced iterations.'
    }

    Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Value 'two' -Encoding utf8
    $unstaged = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    if (-not $unstaged.dirty -or $unstaged.diffSha256 -eq $clean.diffSha256) {
        throw 'Unstaged tracked changes were not represented in provenance.'
    }
    Invoke-Git $root @('add', 'tracked.txt') | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Value 'three' -Encoding utf8
    $stagedAndUnstaged = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    if (-not $stagedAndUnstaged.dirty -or $stagedAndUnstaged.diffSha256 -eq $unstaged.diffSha256) {
        throw 'Combined staged and unstaged changes did not alter provenance.'
    }

    Set-Content -LiteralPath (Join-Path $root 'untracked.txt') -Value 'alpha' -Encoding utf8
    $untracked = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    Set-Content -LiteralPath (Join-Path $root 'untracked.txt') -Value 'beta' -Encoding utf8
    $untrackedChanged = Get-NewAudioRepositoryProvenance -RepositoryRoot $root
    if (-not $untracked.untrackedPresent -or
        $untracked.untrackedSha256 -eq $untrackedChanged.untrackedSha256) {
        throw 'Untracked file content was not represented in provenance.'
    }

    $rejected = $false
    try { Assert-NewAudioRepositoryPolicy -Provenance $untrackedChanged } catch { $rejected = $true }
    if (-not $rejected) { throw 'Dirty provenance was accepted without -AllowDirty.' }
    Assert-NewAudioRepositoryPolicy -Provenance $untrackedChanged -AllowDirty

    if ((Resolve-NewAudioRefreshRate -DisplayDump 'renderFrameRate 60.000004,' -ConfiguredPeakRate 'null') -ne '60') {
        throw 'Refresh-rate fallback did not normalize the active display rate.'
    }
    $battery = Get-NewAudioBatteryMetadata -BatteryDump "USB powered: true`nlevel: 75`nscale: 100"
    if ($battery.percent -ne 75 -or $battery.powerSource -ne 'usb') {
        throw 'Battery/power metadata normalization failed.'
    }
    if ((Resolve-NewAudioDeviceRoleId -Emulator $true) -ne 'emulator-smoke') {
        throw 'Emulator role fallback is not stable.'
    }

    Write-Host 'Repository provenance and device metadata contracts passed.'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
