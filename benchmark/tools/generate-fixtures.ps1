param(
    [string]$SourceRoot,
    [switch]$Check
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$benchmarkRoot = Join-Path $repoRoot "benchmark"
$fixtureRoot = Join-Path $benchmarkRoot 'src/main/assets/fixtures'
$manifestPath = Join-Path $fixtureRoot "fixture-manifest.json"
if (-not $SourceRoot) {
    $SourceRoot = Join-Path $repoRoot 'app/src/benchmark/assets/fixtures'
}

$resolvedFixtureRoot = [IO.Path]::GetFullPath($fixtureRoot)
$resolvedBenchmarkRoot = [IO.Path]::GetFullPath($benchmarkRoot)
if (-not $resolvedFixtureRoot.StartsWith($resolvedBenchmarkRoot + [IO.Path]::DirectorySeparatorChar)) {
    throw "Refusing to clean fixture path outside benchmark/: $resolvedFixtureRoot"
}

$sourceManifestPath = Join-Path $SourceRoot "fixture-manifest.json"
if (-not (Test-Path -LiteralPath $sourceManifestPath)) {
    throw "Target fixture manifest not found: $sourceManifestPath"
}
$sourceManifest = Get-Content -Raw -LiteralPath $sourceManifestPath | ConvertFrom-Json

function Assert-TemplateHash {
    param([string]$Name, [string]$Expected)
    $path = Join-Path $SourceRoot $Name
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Target fixture template not found: $path"
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $Name. Expected $Expected, got $actual"
    }
    $metadata = $sourceManifest.templateMetadata.$Name
    if ($null -eq $metadata) {
        throw "Template metadata missing for $Name"
    }
    if ([string]::IsNullOrWhiteSpace([string]$metadata.id) -or
        [string]::IsNullOrWhiteSpace([string]$metadata.mediaType) -or
        [string]::IsNullOrWhiteSpace([string]$metadata.mimeType)) {
        throw "Incomplete template identity for $Name"
    }
    $actualLength = (Get-Item -LiteralPath $path).Length
    if ($actualLength -ne [long]$metadata.byteSize) {
        throw "Byte-size mismatch for $Name. Expected $($metadata.byteSize), got $actualLength"
    }
}

$sourceManifest.templates.PSObject.Properties | ForEach-Object {
    Assert-TemplateHash -Name $_.Name -Expected ([string]$_.Value)
}

# Binary media lives only in app/src/benchmark, where the benchmark-only receiver
# can access it. Remove obsolete test-APK copies before writing the derived manifest.
if (-not $Check) {
    @("audio", "video", "artwork") | ForEach-Object {
        $path = Join-Path $fixtureRoot $_
        if (Test-Path -LiteralPath $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
}

$audioFolders = @("Albums/One", "Albums/Two", "Unicode", "Long Titles")
$audioEntries = for ($index = 0; $index -lt [int]$sourceManifest.audioCount; $index++) {
    $filename = switch ($index) {
        0 { "Audio_%_Literal.wav" }
        1 { "Audio_underscore_.wav" }
        2 { "Audio_Ünicode_你好.wav" }
        3 { "Audio with a deliberately very long benchmark title for marquee validation.wav" }
        4 { "Audio_05_Cover_Medium.mp3" }
        27 { "Audio_28_Cover_Small.mp3" }
        29 { "Audio_30_Cover_Large.mp3" }
        default { "Audio_{0:D2}.wav" -f ($index + 1) }
    }
    $parent = if ($index -lt $audioFolders.Count) { $audioFolders[$index] } else { "." }
    $sourceTemplate = switch ($index) {
        4 { "audio-cover-medium.mp3" }
        27 { "audio-cover-small.mp3" }
        29 { "audio-cover-large.mp3" }
        default { "audio-template.wav" }
    }
    $artworkPath = switch ($index) {
        27 { "artwork-small.png" }
        29 { "artwork-large.png" }
        default { "artwork.png" }
    }
    $templateMetadata = $sourceManifest.templateMetadata.$sourceTemplate
    [ordered]@{
        id = "audio-{0:D3}" -f ($index + 1)
        sortIndex = $index
        relativePath = if ($parent -eq ".") { "audio/$filename" } else { "audio/$parent/$filename" }
        displayName = $filename
        title = [IO.Path]::GetFileNameWithoutExtension($filename)
        parent = $parent
        sourceTemplate = $sourceTemplate
        sha256 = $sourceManifest.templates.$sourceTemplate
        byteSize = [long]$templateMetadata.byteSize
        mimeType = if ($sourceTemplate.EndsWith('.mp3')) { "audio/mpeg" } else { "audio/wav" }
        durationMs = [int]$templateMetadata.durationMs
        durationToleranceMs = [int]$templateMetadata.durationToleranceMs
        artist = "NewAudio Benchmark Artist $($index % 4)"
        album = "NewAudio Benchmark Album $($index % 3)"
        artworkPath = $artworkPath
        embeddedArtwork = $sourceTemplate.StartsWith('audio-cover-')
        timestampMs = 1700000000000 + $index
    }
}

$videoFolders = @("Clips/One", "Clips/Two", "Unicode", "Markers")
$videoEntries = for ($index = 0; $index -lt [int]$sourceManifest.videoCount; $index++) {
    $filename = switch ($index) {
        0 { "Video_%_Literal.mp4" }
        1 { "Video_underscore_.mp4" }
        2 { "Video_Ünicode_你好.mp4" }
        default { "Video_{0:D2}.mp4" -f ($index + 1) }
    }
    $parent = if ($index -lt $videoFolders.Count) { $videoFolders[$index] } else { "." }
    $thumbnailPath = if ($index -in @($sourceManifest.videoFrameDecoderIndexes)) {
        $null
    } else {
        @("artwork-small.png", "artwork.png", "artwork-large.png")[$index % 3]
    }
    $decoderPath = if ($null -eq $thumbnailPath) { "VIDEO_FRAME_DECODER" } else { "ARTWORK_URI" }
    $videoMetadata = $sourceManifest.templateMetadata.'video-template.mp4'
    [ordered]@{
        id = "video-{0:D3}" -f ($index + 1)
        sortIndex = $index
        relativePath = if ($parent -eq ".") { "video/$filename" } else { "video/$parent/$filename" }
        displayName = $filename
        title = [IO.Path]::GetFileNameWithoutExtension($filename)
        parent = $parent
        sourceTemplate = "video-template.mp4"
        sha256 = $sourceManifest.templates.'video-template.mp4'
        byteSize = [long]$videoMetadata.byteSize
        mimeType = "video/mp4"
        durationMs = [int]$videoMetadata.durationMs
        durationToleranceMs = [int]$videoMetadata.durationToleranceMs
        width = [int]$sourceManifest.video.width
        height = [int]$sourceManifest.video.height
        frameRate = [int]$sourceManifest.video.frameRate
        codec = [string]$sourceManifest.video.codec
        thumbnailPath = $thumbnailPath
        decoderPath = $decoderPath
        timestampMs = 1700000001000 + $index
    }
}

$audioPlaylistA = @($audioEntries | Select-Object -First 20)
$audioPlaylistB = @($audioPlaylistA)
[Array]::Reverse($audioPlaylistB)
$videoPlaylistA = @($videoEntries | Select-Object -First 20)
$videoPlaylistB = @($videoPlaylistA)
[Array]::Reverse($videoPlaylistB)

$manifest = [ordered]@{
    fixtureVersion = 2
    derivedFrom = "app/src/benchmark/assets/fixtures/fixture-manifest.json"
    generator = "benchmark/tools/generate-fixtures.ps1"
    license = [string]$sourceManifest.license
    targetFixtureRoot = "app-private filesDir/benchmark-fixtures"
    audioCount = [int]$sourceManifest.audioCount
    videoCount = [int]$sourceManifest.videoCount
    cacheStates = @($sourceManifest.cacheStates)
    decoderPaths = @($sourceManifest.decoderPaths)
    specialNames = @($sourceManifest.specialNames)
    templateMetadata = $sourceManifest.templateMetadata
    templates = [ordered]@{
        audio = [ordered]@{ path = "audio-template.wav"; sha256 = $sourceManifest.templates.'audio-template.wav' }
        audioCoverSmall = [ordered]@{ path = "audio-cover-small.mp3"; sha256 = $sourceManifest.templates.'audio-cover-small.mp3'; artworkWidth = 96; artworkHeight = 96 }
        audioCoverMedium = [ordered]@{ path = "audio-cover-medium.mp3"; sha256 = $sourceManifest.templates.'audio-cover-medium.mp3'; artworkWidth = 256; artworkHeight = 256 }
        audioCoverLarge = [ordered]@{ path = "audio-cover-large.mp3"; sha256 = $sourceManifest.templates.'audio-cover-large.mp3'; artworkWidth = 512; artworkHeight = 512 }
        video = [ordered]@{ path = "video-template.mp4"; sha256 = $sourceManifest.templates.'video-template.mp4' }
        artworkSmall = [ordered]@{ path = "artwork-small.png"; sha256 = $sourceManifest.templates.'artwork-small.png'; width = 96; height = 96 }
        artwork = [ordered]@{ path = "artwork.png"; sha256 = $sourceManifest.templates.'artwork.png'; width = 256; height = 256 }
        artworkLarge = [ordered]@{ path = "artwork-large.png"; sha256 = $sourceManifest.templates.'artwork-large.png'; width = 512; height = 512 }
    }
    audio = $audioEntries
    video = $videoEntries
    playlists = @(
        [ordered]@{ name = "Benchmark Audio A"; mediaType = "audio"; items = @($audioPlaylistA | ForEach-Object relativePath) },
        [ordered]@{ name = "Benchmark Audio B"; mediaType = "audio"; items = @($audioPlaylistB | ForEach-Object relativePath) },
        [ordered]@{ name = "Benchmark Video A"; mediaType = "video"; items = @($videoPlaylistA | ForEach-Object relativePath) },
        [ordered]@{ name = "Benchmark Video B"; mediaType = "video"; items = @($videoPlaylistB | ForEach-Object relativePath) }
    )
    videoMarkers = @(
        [ordered]@{
            video = ($videoEntries | Where-Object displayName -eq 'Video_05.mp4' | Select-Object -First 1).relativePath
            positionsMs = @(2000, 6000, 10000)
        }
    )
}

$renderedManifest = $manifest | ConvertTo-Json -Depth 10
if ($Check) {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Expanded fixture manifest is missing: $manifestPath"
    }
    $existingManifest = (Get-Content -LiteralPath $manifestPath -Raw).TrimStart([char]0xFEFF).TrimEnd()
    if ($existingManifest -cne $renderedManifest.TrimEnd()) {
        throw "Expanded fixture manifest drift detected. Run benchmark/tools/generate-fixtures.ps1 and commit the result."
    }
    Write-Host "Fixture source/expanded manifest contract is current: $manifestPath"
} else {
    $renderedManifest | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Host "Verified target templates and wrote expanded runtime manifest: $manifestPath"
}
