Set-StrictMode -Version Latest

function Get-NewAudioAdbValue {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    try {
        $value = @(& $AdbPath @Arguments 2>$null)
        if ($LASTEXITCODE -ne 0) { return $null }
        return (($value -join "`n").Trim())
    } catch {
        return $null
    }
}

function Get-NewAudioSha256Text {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
        return (($algorithm.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $algorithm.Dispose()
    }
}

function Get-NewAudioRepositoryProvenance {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($null -eq $git) {
        throw 'git is required to record benchmark provenance.'
    }

    $commitLines = @(& $git.Source -C $RepositoryRoot rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or $commitLines.Count -ne 1) {
        throw "Unable to resolve the Git commit for '$RepositoryRoot'."
    }
    $statusLines = @(& $git.Source -C $RepositoryRoot status --porcelain=v1 --untracked-files=all 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the Git worktree for '$RepositoryRoot'."
    }
    $diffLines = @(& $git.Source -C $RepositoryRoot diff --binary HEAD -- 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to record the Git diff for '$RepositoryRoot'."
    }

    $untrackedPaths = @(& $git.Source -C $RepositoryRoot ls-files --others --exclude-standard 2>$null |
        Sort-Object)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect untracked files for '$RepositoryRoot'."
    }
    $untrackedEntries = [Collections.Generic.List[object]]::new()
    $untrackedIdentityLines = [Collections.Generic.List[string]]::new()
    foreach ($relativePath in $untrackedPaths) {
        $absolutePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $relativePath))
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { continue }
        $item = Get-Item -LiteralPath $absolutePath
        $sha256 = (Get-FileHash -LiteralPath $absolutePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $normalizedPath = $relativePath.Replace('\', '/')
        $untrackedEntries.Add([ordered]@{
            path = $normalizedPath
            lengthBytes = $item.Length
            sha256 = $sha256
        })
        $untrackedIdentityLines.Add("$normalizedPath`0$($item.Length)`0$sha256")
    }

    $statusText = if ($statusLines.Count -eq 0) { '<clean>' } else { $statusLines -join "`n" }
    $diffText = if ($diffLines.Count -eq 0) { '<clean>' } else { $diffLines -join "`n" }
    $untrackedText = if ($untrackedIdentityLines.Count -eq 0) {
        '<none>'
    } else {
        $untrackedIdentityLines -join "`n"
    }
    $statusSha256 = Get-NewAudioSha256Text -Value $statusText
    $diffSha256 = Get-NewAudioSha256Text -Value $diffText
    $untrackedSha256 = Get-NewAudioSha256Text -Value $untrackedText
    return [ordered]@{
        commit = $commitLines[0].Trim()
        dirty = ($statusLines.Count -gt 0)
        statusSha256 = $statusSha256
        diffSha256 = $diffSha256
        untrackedSha256 = $untrackedSha256
        worktreeStateSha256 = Get-NewAudioSha256Text -Value (
            "$($commitLines[0].Trim())`n$statusSha256`n$diffSha256`n$untrackedSha256"
        )
        untrackedPresent = ($untrackedEntries.Count -gt 0)
        statusEntries = @($statusLines)
        untrackedEntries = $untrackedEntries.ToArray()
    }
}

function Assert-NewAudioRepositoryPolicy {
    param(
        [Parameter(Mandatory = $true)]$Provenance,
        [switch]$AllowDirty
    )

    if ([bool]$Provenance.dirty -and -not $AllowDirty) {
        throw 'The Git worktree is dirty. Commit/stash changes, or use -AllowDirty for a diagnostic run that is never baseline eligible.'
    }
}

function Test-NewAudioBaselineEligibility {
    param(
        [Parameter(Mandatory = $true)]$Provenance,
        [bool]$AllowDirty,
        [bool]$IterationOverride
    )

    return (-not [bool]$Provenance.dirty -and -not $AllowDirty -and -not $IterationOverride)
}

function Assert-NewAudioAdditionalGradleArguments {
    param([string[]]$Arguments)

    $owned = '(?i)(android\.testInstrumentationRunnerArguments\.(class|newaudio\.benchmark\.iterations)|android\.injected\.device\.serial|androidx\.benchmark\.fullTracing\.enable|fullTracing(?:\.enable)?\s*=|-PfullTracing=)'
    $conflicts = @($Arguments | Where-Object { $_ -match $owned })
    if ($conflicts.Count -gt 0) {
        throw "AdditionalGradleArguments may not override runner-owned class, iteration, or tracing settings: $($conflicts -join ', ')"
    }
    $sensitive = @($Arguments | Where-Object {
        $_ -match '(?i)(?:password|passwd|secret|token|api[-_.]?key|credential|signing|keystore)\s*=' -or
        $_ -match '(?i)://[^/\s:@]+:[^/\s@]+@'
    })
    if ($sensitive.Count -gt 0) {
        throw 'AdditionalGradleArguments contains a potentially sensitive value. Use a non-persisted environment or CI secret channel instead.'
    }
}

function Resolve-NewAudioCacheState {
    param(
        [Parameter(Mandatory = $true)][string]$TestClass,
        [string]$RequestedState
    )

    $allowed = @(
        'COLD_EMPTY_IMAGE_CACHE',
        'WARM_PRELOADED_IMAGE_CACHE',
        'MIXED_PER_JOURNEY'
    )
    $expected = if ($TestClass -match '(?i)#(?:br04VideoGallery(?:Two|Three|Four)ColumnsWarm|br06NestedFolderWarmCache|traceNestedFolderWarmCache)$') {
        'WARM_PRELOADED_IMAGE_CACHE'
    } elseif ($TestClass -notmatch '#' -and
        $TestClass -match '(?:BrowserRenderingBenchmark|TraceCaptureTest)$') {
        'MIXED_PER_JOURNEY'
    } else {
        'COLD_EMPTY_IMAGE_CACHE'
    }
    if (-not [string]::IsNullOrWhiteSpace($RequestedState)) {
        if ($RequestedState -notin $allowed) {
            throw "Unknown cache state '$RequestedState'. Allowed values: $($allowed -join ', ')."
        }
        if ($RequestedState -ne $expected) {
            throw "Cache state '$RequestedState' contradicts selector '$TestClass'; expected '$expected'."
        }
    }
    return $expected
}

function Resolve-NewAudioRefreshRate {
    param([string]$DisplayDump, [string]$ConfiguredPeakRate)

    $candidate = $null
    foreach ($pattern in @(
            '(?i)\brenderFrameRate\s+([0-9]+(?:\.[0-9]+)?)',
            '(?i)\bmActiveRenderFrameRate=([0-9]+(?:\.[0-9]+)?)',
            '(?i)\bpeakRefreshRate=([0-9]+(?:\.[0-9]+)?)')) {
        if (-not [string]::IsNullOrWhiteSpace($DisplayDump) -and $DisplayDump -match $pattern) {
            $candidate = $Matches[1]
            break
        }
    }
    if ([string]::IsNullOrWhiteSpace($candidate) -and
        -not [string]::IsNullOrWhiteSpace($ConfiguredPeakRate) -and
        $ConfiguredPeakRate -notin @('null', '0', '0.0')) {
        $candidate = $ConfiguredPeakRate.Trim()
    }
    $rate = 0.0
    if ([double]::TryParse($candidate, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$rate) -and $rate -gt 0) {
        return $rate.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
    }
    return 'unavailable'
}

function Get-NewAudioBatteryMetadata {
    param([string]$BatteryDump)

    $level = $null
    $scale = $null
    if ($BatteryDump -match '(?m)^\s*level:\s*(\d+)\s*$') { $level = [int]$Matches[1] }
    if ($BatteryDump -match '(?m)^\s*scale:\s*(\d+)\s*$') { $scale = [int]$Matches[1] }
    $percent = if ($null -ne $level -and $null -ne $scale -and $scale -gt 0) {
        [math]::Round(100.0 * $level / $scale, 1)
    } else { $null }
    $source = 'battery'
    foreach ($entry in @(
            [pscustomobject]@{ source = 'ac'; pattern = '(?im)^\s*AC powered:\s*true\s*$' },
            [pscustomobject]@{ source = 'usb'; pattern = '(?im)^\s*USB powered:\s*true\s*$' },
            [pscustomobject]@{ source = 'wireless'; pattern = '(?im)^\s*Wireless powered:\s*true\s*$' },
            [pscustomobject]@{ source = 'dock'; pattern = '(?im)^\s*Dock powered:\s*true\s*$' })) {
        if ($BatteryDump -match $entry.pattern) { $source = $entry.source; break }
    }
    return [ordered]@{ percent = $percent; powerSource = $source }
}

function Resolve-NewAudioDeviceRoleId {
    param([bool]$Emulator, [string]$RequestedRoleId)

    $roleId = if (-not [string]::IsNullOrWhiteSpace($RequestedRoleId)) {
        $RequestedRoleId
    } elseif (-not [string]::IsNullOrWhiteSpace($env:NEWAUDIO_DEVICE_ROLE_ID)) {
        $env:NEWAUDIO_DEVICE_ROLE_ID
    } elseif ($Emulator) {
        'emulator-smoke'
    } else {
        throw 'Physical benchmark runs require -DeviceRoleId or NEWAUDIO_DEVICE_ROLE_ID; raw serial numbers are not valid role IDs.'
    }
    if ($roleId -notmatch '^[a-z0-9][a-z0-9._-]{2,63}$') {
        throw "Device role ID '$roleId' must be a stable lowercase alias (3-64 characters), never a raw serial."
    }
    return $roleId
}

function Copy-NewAudioCurrentTestOutputs {
    param(
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$DestinationRoot,
        [Parameter(Mandatory = $true)][DateTime]$StartedAtUtc,
        [hashtable]$Snapshot
    )

    if (-not (Test-Path -LiteralPath $SourceRoot -PathType Container)) { return @() }
    $sourcePrefix = [IO.Path]::GetFullPath($SourceRoot).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $copied = [Collections.Generic.List[object]]::new()
    foreach ($item in @(Get-ChildItem -LiteralPath $SourceRoot -Recurse -File |
        Where-Object {
            $signature = '{0}:{1}' -f $_.LastWriteTimeUtc.Ticks, $_.Length
            if ($null -ne $Snapshot) {
                -not $Snapshot.ContainsKey($_.FullName) -or $Snapshot[$_.FullName] -ne $signature
            } else {
                $_.LastWriteTimeUtc -ge $StartedAtUtc.AddSeconds(-2)
            }
        } |
        Sort-Object FullName)) {
        $relativePath = $item.FullName.Substring($sourcePrefix.Length)
        $destination = Join-Path $DestinationRoot $relativePath
        $parent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destination -Force
        $copied.Add([ordered]@{
            relativePath = $relativePath.Replace('\', '/')
            lengthBytes = $item.Length
            sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }
    return $copied.ToArray()
}

function Get-NewAudioFileSnapshot {
    param([Parameter(Mandatory = $true)][string]$SourceRoot)

    $snapshot = @{}
    if (Test-Path -LiteralPath $SourceRoot -PathType Container) {
        foreach ($item in Get-ChildItem -LiteralPath $SourceRoot -Recurse -File) {
            $snapshot[$item.FullName] = '{0}:{1}' -f $item.LastWriteTimeUtc.Ticks, $item.Length
        }
    }
    return $snapshot
}

function Get-NewAudioRunEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$Mode,
        [string]$CacheState = 'seeded-app-private',
        [string]$DeviceRoleId
    )

    $fixtureManifestPath = Join-Path $RepositoryRoot 'app/src/benchmark/assets/fixtures/fixture-manifest.json'
    if (-not (Test-Path -LiteralPath $fixtureManifestPath -PathType Leaf)) {
        throw "Fixture manifest not found: $fixtureManifestPath"
    }
    $fixtureManifest = Get-Content -LiteralPath $fixtureManifestPath -Raw | ConvertFrom-Json
    $fixtureHash = (Get-FileHash -LiteralPath $fixtureManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()

    $serial = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('get-serialno')
    $packageDump = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'dumpsys', 'package', 'com.example.newaudio')
    $versionName = if ($packageDump -match '(?m)^\s*versionName=([^\r\n]+)') { $Matches[1].Trim() } else { $null }
    $versionCode = if ($packageDump -match '(?m)^\s*versionCode=(\d+)') { [int64]$Matches[1] } else { $null }
    $versionSource = if ($null -ne $versionName -and $null -ne $versionCode) { 'installed-package' } else { $null }
    if ($null -eq $versionName -or $null -eq $versionCode) {
        # connectedAndroidTest may uninstall the target package before host-side
        # metadata is collected. Fall back to the checked-in build identity so
        # successful benchmark runs never lose their app version.
        $appBuildPath = Join-Path $RepositoryRoot 'app/build.gradle.kts'
        if (Test-Path -LiteralPath $appBuildPath -PathType Leaf) {
            $appBuild = Get-Content -LiteralPath $appBuildPath -Raw
            if ($null -eq $versionName -and $appBuild -match '(?m)^\s*versionName\s*=\s*"([^"]+)"') {
                $versionName = $Matches[1]
            }
            if ($null -eq $versionCode -and $appBuild -match '(?m)^\s*versionCode\s*=\s*(\d+)') {
                $versionCode = [int64]$Matches[1]
            }
            if ($null -ne $versionName -and $null -ne $versionCode) { $versionSource = 'app-build-gradle' }
        }
    }
    $qemu = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.kernel.qemu')
    $isEmulator = ($qemu -eq '1')
    $resolvedDeviceRoleId = Resolve-NewAudioDeviceRoleId -Emulator $isEmulator -RequestedRoleId $DeviceRoleId
    $batteryDump = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'dumpsys', 'battery')
    $batteryMetadata = Get-NewAudioBatteryMetadata -BatteryDump $batteryDump
    $displayDump = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'dumpsys', 'display')
    $peakRate = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'settings', 'get', 'system', 'peak_refresh_rate')
    $thermalStatus = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'cmd', 'thermalservice', 'get-current-thermal-status')
    if ([string]::IsNullOrWhiteSpace($thermalStatus)) { $thermalStatus = 'unavailable' }
    $animationValues = [ordered]@{
        window = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'settings', 'get', 'global', 'window_animation_scale')
        transition = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'settings', 'get', 'global', 'transition_animation_scale')
        animator = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'settings', 'get', 'global', 'animator_duration_scale')
    }

    return [ordered]@{
        mode = $Mode
        build = [ordered]@{
            appId = 'com.example.newaudio'
            variant = 'benchmark'
            compilationMode = 'Partial(warmupIterations=3)'
            versionName = $versionName
            versionCode = $versionCode
            versionSource = $versionSource
        }
        fixtures = [ordered]@{
            manifestVersion = [int]$fixtureManifest.schemaVersion
            manifestSha256 = $fixtureHash
            cacheState = $CacheState
            audioCount = [int]$fixtureManifest.audioCount
            videoCount = [int]$fixtureManifest.videoCount
        }
        host = [ordered]@{
            os = [Runtime.InteropServices.RuntimeInformation]::OSDescription
            architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
            processorCount = [Environment]::ProcessorCount
        }
        device = [ordered]@{
            role = if ($isEmulator) { 'emulator-smoke' } else { 'physical-candidate-device' }
            roleId = $resolvedDeviceRoleId
            physical = (-not $isEmulator)
            serialSha256 = Get-NewAudioSha256Text -Value $serial
            manufacturer = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.product.manufacturer')
            model = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.product.model')
            apiLevel = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.build.version.sdk')
            buildFingerprint = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.build.fingerprint')
            abi = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.product.cpu.abi')
            hardware = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'getprop', 'ro.hardware')
            emulator = $isEmulator
            decoderPolicy = 'device-default-unforced'
            screenResolution = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'wm', 'size')
            screenDensity = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'wm', 'density')
            refreshRate = Resolve-NewAudioRefreshRate -DisplayDump $displayDump -ConfiguredPeakRate $peakRate
            fontScale = Get-NewAudioAdbValue -AdbPath $AdbPath -Arguments @('shell', 'settings', 'get', 'system', 'font_scale')
            batteryPercent = $batteryMetadata.percent
            powerSource = $batteryMetadata.powerSource
            battery = $batteryDump
            thermalStatus = $thermalStatus
            animations = $animationValues
        }
    }
}
