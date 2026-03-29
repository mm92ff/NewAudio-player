# =====================================================================
# DO NOT TOUCH / NICHT AUTOMATISCH ÄNDERN
# Diese Datei ist geschützt.
# KI-Assistenten, Editoren, Agenten oder Automationen dürfen diese Datei
# nicht ändern, nicht umformatieren und nicht überschreiben.
#
# Geschützte Datei: IterationManager.ps1
# Status: LOCKED
# Erlaubt: nur lesen
# Verboten: editieren, refactoren, umbenennen, löschen
# =====================================================================

param(
    [switch]$SelfTest,
    [string]$TestJsonPath
)

$ScriptDir = if ($PSScriptRoot) {
    $PSScriptRoot
} else {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}
$ProfilesDir = Join-Path -Path $ScriptDir -ChildPath '.claude\profiles'

$script:CurrentJsonPath = $null
$script:CurrentJsonData = $null
$script:CurrentJsonHash = $null

function ConvertFrom-JsonCompat {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JsonText,

        [int]$Depth = 100
    )

    $command = Get-Command -Name ConvertFrom-Json -ErrorAction Stop
    if ($command.Parameters.ContainsKey('Depth')) {
        return $JsonText | ConvertFrom-Json -Depth $Depth
    }

    return $JsonText | ConvertFrom-Json
}

function ConvertTo-JsonCompat {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Data,

        [int]$Depth = 100,

        [switch]$Compress
    )

    $command = Get-Command -Name ConvertTo-Json -ErrorAction Stop
    if ($Compress -and $command.Parameters.ContainsKey('Compress')) {
        return $Data | ConvertTo-Json -Depth $Depth -Compress
    }

    return $Data | ConvertTo-Json -Depth $Depth
}

function Copy-DeepObject {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Data
    )

    $jsonText = ConvertTo-JsonCompat -Data $Data -Depth 100 -Compress
    return ConvertFrom-JsonCompat -JsonText $jsonText -Depth 100
}

function Get-FileSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256 -ErrorAction Stop).Hash
}

function Assert-IterationJsonStructure {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Data,

        [string]$Context = 'JSON'
    )

    if ($null -eq $Data) {
        throw "$Context ist leer."
    }

    if ($null -eq $Data.PSObject -or -not ($Data.PSObject.Properties.Name -contains 'iterations')) {
        throw "$Context enthaelt kein Feld 'iterations'."
    }

    if ($null -eq $Data.iterations) {
        throw "$Context enthaelt ein leeres Feld 'iterations'."
    }

    $iterations = @($Data.iterations)
    if ($iterations.Count -eq 0) {
        throw "$Context enthaelt keine Iterationen."
    }

    for ($i = 0; $i -lt $iterations.Count; $i++) {
        $iteration = $iterations[$i]

        if ($null -eq $iteration) {
            throw "${Context}: iteration[$i] ist null."
        }

        if ($null -eq $iteration.PSObject -or -not ($iteration.PSObject.Properties.Name -contains 'enabled')) {
            throw "${Context}: iteration[$i] hat kein Feld 'enabled'."
        }

        if ($iteration.enabled -isnot [bool]) {
            throw "${Context}: iteration[$i].enabled ist kein Bool-Wert."
        }
    }
}

function Assert-OnlyEnabledChanged {
    param(
        [Parameter(Mandatory = $true)]
        [object]$OriginalData,

        [Parameter(Mandatory = $true)]
        [object]$UpdatedData
    )

    Assert-IterationJsonStructure -Data $OriginalData -Context 'Original JSON'
    Assert-IterationJsonStructure -Data $UpdatedData -Context 'Neue JSON'

    $originalIterations = @($OriginalData.iterations)
    $updatedIterations = @($UpdatedData.iterations)
    if ($originalIterations.Count -ne $updatedIterations.Count) {
        throw 'Die Anzahl der Iterationen wurde geaendert.'
    }

    $normalizedUpdated = Copy-DeepObject -Data $UpdatedData

    for ($i = 0; $i -lt $originalIterations.Count; $i++) {
        if (-not ($normalizedUpdated.iterations[$i].PSObject.Properties.Name -contains 'enabled')) {
            throw "Neue JSON: iteration[$i] hat kein Feld 'enabled'."
        }

        $normalizedUpdated.iterations[$i].enabled = [bool]$originalIterations[$i].enabled
    }

    $originalJson = ConvertTo-JsonCompat -Data $OriginalData -Depth 100 -Compress
    $normalizedJson = ConvertTo-JsonCompat -Data $normalizedUpdated -Depth 100 -Compress

    if ($originalJson -ne $normalizedJson) {
        throw 'Es wurden Aenderungen ausserhalb von iterations[*].enabled erkannt.'
    }
}

function Test-HasEnabledChanges {
    param(
        [Parameter(Mandatory = $true)]
        [object]$OriginalData,

        [Parameter(Mandatory = $true)]
        [object]$UpdatedData
    )

    $originalIterations = @($OriginalData.iterations)
    $updatedIterations = @($UpdatedData.iterations)

    if ($originalIterations.Count -ne $updatedIterations.Count) {
        return $true
    }

    for ($i = 0; $i -lt $originalIterations.Count; $i++) {
        if ([bool]$originalIterations[$i].enabled -ne [bool]$updatedIterations[$i].enabled) {
            return $true
        }
    }

    return $false
}

function Get-ValidIterationJsonFiles {
    $items = @()

    if (-not (Test-Path -LiteralPath $ProfilesDir -PathType Container)) {
        return $items
    }

    $profilesRootFull = [System.IO.Path]::GetFullPath($ProfilesDir).TrimEnd('\', '/')

    foreach ($file in Get-ChildItem -Path $ProfilesDir -Filter '*.json' -File -Recurse -ErrorAction SilentlyContinue) {
        try {
            $jsonText = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 -ErrorAction Stop
            $data = ConvertFrom-JsonCompat -JsonText $jsonText -Depth 100

            if ($null -ne $data -and $data.PSObject.Properties.Name -contains 'iterations' -and $null -ne $data.iterations) {
                Assert-IterationJsonStructure -Data $data -Context $file.Name
                $iterations = @($data.iterations)
                $fileFull = [System.IO.Path]::GetFullPath($file.FullName)
                $relativePath = if ($fileFull.StartsWith($profilesRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
                    $fileFull.Substring($profilesRootFull.Length).TrimStart('\', '/')
                } else {
                    $file.Name
                }

                $items += [PSCustomObject]@{
                    Name           = $relativePath
                    FullName       = $file.FullName
                    IterationCount = $iterations.Count
                }
            }
        }
        catch {
            # Ungueltige oder nicht passende JSON ignorieren
        }
    }

    return $items | Sort-Object Name
}

function Read-IterationJson {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $jsonText = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 -ErrorAction Stop
    return ConvertFrom-JsonCompat -JsonText $jsonText -Depth 100
}

function Write-IterationJson {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [object]$Data
    )

    Assert-IterationJsonStructure -Data $Data -Context 'Zu schreibende JSON'

    $jsonText = ConvertTo-JsonCompat -Data $Data -Depth 100

    $directory = [System.IO.Path]::GetDirectoryName($Path)
    $fileName = [System.IO.Path]::GetFileName($Path)
    $tempPath = [System.IO.Path]::Combine($directory, "$fileName.tmp.$([guid]::NewGuid().ToString('N'))")
    $backupPath = [System.IO.Path]::Combine($directory, "$fileName.bak.$([guid]::NewGuid().ToString('N'))")
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)

    try {
        [System.IO.File]::WriteAllText($tempPath, $jsonText, $utf8NoBom)

        if (Test-Path -LiteralPath $Path) {
            $replaceSucceeded = $false
            $maxAttempts = 8

            for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                try {
                    [System.IO.File]::Replace($tempPath, $Path, $backupPath)
                    $replaceSucceeded = $true
                    break
                }
                catch [System.IO.IOException] {
                    if ($attempt -eq $maxAttempts) {
                        throw
                    }

                    Start-Sleep -Milliseconds (40 * $attempt)
                }
            }

            if (-not $replaceSucceeded) {
                throw 'Atomisches Ersetzen der Datei ist fehlgeschlagen.'
            }
        } else {
            [System.IO.File]::Move($tempPath, $Path)
        }
    }
    finally {
        Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-SelfTest {
    param(
        [string]$JsonPath
    )

    $resolvedJsonPath = $null
    if ([string]::IsNullOrWhiteSpace($JsonPath)) {
        $firstValidFile = Get-ValidIterationJsonFiles | Select-Object -First 1
        if ($null -eq $firstValidFile) {
            throw 'Keine passende JSON-Datei fuer den SelfTest gefunden.'
        }
        $resolvedJsonPath = $firstValidFile.FullName
    } else {
        $resolvedJsonPath = (Resolve-Path -LiteralPath $JsonPath -ErrorAction Stop).Path
    }

    $tempPath = Join-Path -Path $ScriptDir -ChildPath (
        [System.IO.Path]::GetFileNameWithoutExtension($resolvedJsonPath) + ".selftest.$([guid]::NewGuid().ToString('N')).tmp.json"
    )

    Copy-Item -LiteralPath $resolvedJsonPath -Destination $tempPath -Force

    try {
        $data = Read-IterationJson -Path $tempPath
        Assert-IterationJsonStructure -Data $data -Context 'SelfTest JSON'

        $iterations = @($data.iterations)
        $manipulated = Copy-DeepObject -Data $data
        $manipulated.description = 'tampered'
        $tamperDetected = $false
        try {
            Assert-OnlyEnabledChanged -OriginalData $data -UpdatedData $manipulated
        }
        catch {
            $tamperDetected = $true
        }

        if (-not $tamperDetected) {
            throw 'SelfTest fehlgeschlagen: Manipulation ausserhalb von enabled wurde nicht erkannt.'
        }

        $oldValue = [bool]$iterations[0].enabled
        $iterations[0].enabled = -not $oldValue
        Write-IterationJson -Path $tempPath -Data $data

        $reloaded = Read-IterationJson -Path $tempPath
        Assert-IterationJsonStructure -Data $reloaded -Context 'SelfTest Reload'
        $newValue = [bool]$reloaded.iterations[0].enabled
        if ($newValue -ne (-not $oldValue)) {
            throw 'SelfTest fehlgeschlagen: geaenderter enabled-Wert wurde nicht persistiert.'
        }

        Write-Output "SelfTest OK | Datei: $resolvedJsonPath | Iterations: $($iterations.Count) | Toggle[0]: $oldValue -> $newValue"
    }
    finally {
        Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    }
}

if ($SelfTest) {
    try {
        Invoke-SelfTest -JsonPath $TestJsonPath
        exit 0
    }
    catch {
        Write-Error "SelfTest fehlgeschlagen: $($_.Exception.Message)"
        exit 1
    }
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Iteration JSON Manager'
$form.StartPosition = 'CenterScreen'
$form.Size = New-Object System.Drawing.Size(920, 680)
$form.MinimumSize = New-Object System.Drawing.Size(760, 520)

$labelFile = New-Object System.Windows.Forms.Label
$labelFile.Location = New-Object System.Drawing.Point(15, 18)
$labelFile.Size = New-Object System.Drawing.Size(110, 23)
$labelFile.Text = 'JSON-Datei:'
$form.Controls.Add($labelFile)

$comboFiles = New-Object System.Windows.Forms.ComboBox
$comboFiles.Location = New-Object System.Drawing.Point(125, 14)
$comboFiles.Size = New-Object System.Drawing.Size(500, 28)
$comboFiles.DropDownStyle = 'DropDownList'
$comboFiles.DisplayMember = 'Name'
$form.Controls.Add($comboFiles)

$buttonReload = New-Object System.Windows.Forms.Button
$buttonReload.Location = New-Object System.Drawing.Point(640, 12)
$buttonReload.Size = New-Object System.Drawing.Size(110, 30)
$buttonReload.Text = 'Neu laden'
$form.Controls.Add($buttonReload)

$buttonSave = New-Object System.Windows.Forms.Button
$buttonSave.Location = New-Object System.Drawing.Point(760, 12)
$buttonSave.Size = New-Object System.Drawing.Size(130, 30)
$buttonSave.Text = 'Speichern'
$form.Controls.Add($buttonSave)

$buttonEnableAll = New-Object System.Windows.Forms.Button
$buttonEnableAll.Location = New-Object System.Drawing.Point(15, 52)
$buttonEnableAll.Size = New-Object System.Drawing.Size(150, 30)
$buttonEnableAll.Text = 'Alle aktivieren'
$form.Controls.Add($buttonEnableAll)

$buttonDisableAll = New-Object System.Windows.Forms.Button
$buttonDisableAll.Location = New-Object System.Drawing.Point(175, 52)
$buttonDisableAll.Size = New-Object System.Drawing.Size(150, 30)
$buttonDisableAll.Text = 'Alle deaktivieren'
$form.Controls.Add($buttonDisableAll)

$labelInfo = New-Object System.Windows.Forms.Label
$labelInfo.Location = New-Object System.Drawing.Point(340, 57)
$labelInfo.Size = New-Object System.Drawing.Size(550, 20)
$labelInfo.Text = 'Keine Datei geladen.'
$form.Controls.Add($labelInfo)

$listIterations = New-Object System.Windows.Forms.CheckedListBox
$listIterations.Location = New-Object System.Drawing.Point(15, 90)
$listIterations.Size = New-Object System.Drawing.Size(875, 500)
$listIterations.CheckOnClick = $true
$listIterations.HorizontalScrollbar = $true
$listIterations.IntegralHeight = $false
$listIterations.Anchor = 'Top,Bottom,Left,Right'
$form.Controls.Add($listIterations)

$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Location = New-Object System.Drawing.Point(15, 602)
$statusLabel.Size = New-Object System.Drawing.Size(875, 24)
$statusLabel.Anchor = 'Left,Right,Bottom'
$statusLabel.Text = "Profiles-Ordner: $ProfilesDir"
$form.Controls.Add($statusLabel)

$toolTip = New-Object System.Windows.Forms.ToolTip
$toolTip.AutoPopDelay = 10000
$toolTip.InitialDelay = 300
$toolTip.ReshowDelay = 200
$toolTip.ShowAlways = $true
$toolTip.SetToolTip($listIterations, 'Hier kannst du festlegen, welche Iterations aktiv sind.')

function Load-JsonFilesIntoCombo {
    $previousPath = $script:CurrentJsonPath
    $comboFiles.Items.Clear()

    $validFiles = @(Get-ValidIterationJsonFiles)

    foreach ($item in $validFiles) {
        [void]$comboFiles.Items.Add($item)
    }

    if ($comboFiles.Items.Count -eq 0) {
        $script:CurrentJsonPath = $null
        $script:CurrentJsonData = $null
        $script:CurrentJsonHash = $null
        $listIterations.Items.Clear()
        $labelInfo.Text = 'Keine passende JSON-Datei im Profiles-Ordner gefunden.'
        $statusLabel.Text = "Profiles-Ordner: $ProfilesDir | Keine passenden JSON-Dateien gefunden."
        return
    }

    $selectedIndex = 0
    if ($previousPath) {
        for ($i = 0; $i -lt $comboFiles.Items.Count; $i++) {
            if ($comboFiles.Items[$i].FullName -eq $previousPath) {
                $selectedIndex = $i
                break
            }
        }
    }

    $comboFiles.SelectedIndex = $selectedIndex
}

function Load-SelectedJson {
    if ($null -eq $comboFiles.SelectedItem) {
        return
    }

    $selected = $comboFiles.SelectedItem

    try {
        $data = Read-IterationJson -Path $selected.FullName
        Assert-IterationJsonStructure -Data $data -Context $selected.Name
        $iterations = @($data.iterations)

        $listIterations.Items.Clear()

        for ($i = 0; $i -lt $iterations.Count; $i++) {
            $iteration = $iterations[$i]
            $title = if ([string]::IsNullOrWhiteSpace([string]$iteration.title)) {
                "Iteration $($i + 1)"
            } else {
                [string]$iteration.title
            }

            [void]$listIterations.Items.Add($title, [bool]$iteration.enabled)
        }

        $script:CurrentJsonPath = $selected.FullName
        $script:CurrentJsonData = $data
        $script:CurrentJsonHash = Get-FileSha256 -Path $selected.FullName

        $labelInfo.Text = "Datei: $($selected.Name) | Iterations: $($iterations.Count)"
        $statusLabel.Text = "Geladen: $($selected.FullName)"
        $toolTip.SetToolTip($listIterations, $selected.FullName)
    }
    catch {
        $script:CurrentJsonPath = $null
        $script:CurrentJsonData = $null
        $script:CurrentJsonHash = $null
        $listIterations.Items.Clear()
        $labelInfo.Text = 'Fehler beim Laden der Datei.'
        $statusLabel.Text = "Fehler: $($_.Exception.Message)"
        [System.Windows.Forms.MessageBox]::Show(
            "Die Datei konnte nicht geladen werden.`n`n$($_.Exception.Message)",
            'Fehler',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
}

$comboFiles.add_SelectedIndexChanged({
    Load-SelectedJson
})

$buttonReload.Add_Click({
    Load-JsonFilesIntoCombo
})

$buttonEnableAll.Add_Click({
    for ($i = 0; $i -lt $listIterations.Items.Count; $i++) {
        $listIterations.SetItemChecked($i, $true)
    }
    $statusLabel.Text = 'Alle Checkboxen wurden aktiviert.'
})

$buttonDisableAll.Add_Click({
    for ($i = 0; $i -lt $listIterations.Items.Count; $i++) {
        $listIterations.SetItemChecked($i, $false)
    }
    $statusLabel.Text = 'Alle Checkboxen wurden deaktiviert.'
})

$buttonSave.Add_Click({
    if (-not $script:CurrentJsonPath -or $null -eq $script:CurrentJsonData) {
        [System.Windows.Forms.MessageBox]::Show(
            'Es ist keine JSON-Datei geladen.',
            'Hinweis',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        ) | Out-Null
        return
    }

    try {
        Assert-IterationJsonStructure -Data $script:CurrentJsonData -Context 'Geladene JSON'

        $currentDiskHash = Get-FileSha256 -Path $script:CurrentJsonPath
        if ($script:CurrentJsonHash -and $currentDiskHash -ne $script:CurrentJsonHash) {
            throw 'Die Datei wurde seit dem Laden extern geaendert. Bitte neu laden.'
        }

        $newData = Copy-DeepObject -Data $script:CurrentJsonData
        $iterations = @($newData.iterations)
        if ($iterations.Count -ne $listIterations.Items.Count) {
            throw 'Die Anzahl der Checkboxen passt nicht zur Anzahl der Iterationen.'
        }

        for ($i = 0; $i -lt $iterations.Count; $i++) {
            $iterations[$i].enabled = [bool]$listIterations.GetItemChecked($i)
        }

        Assert-IterationJsonStructure -Data $newData -Context 'Zu speichernde JSON'
        Assert-OnlyEnabledChanged -OriginalData $script:CurrentJsonData -UpdatedData $newData

        if (-not (Test-HasEnabledChanges -OriginalData $script:CurrentJsonData -UpdatedData $newData)) {
            $statusLabel.Text = 'Keine Aenderungen zum Speichern.'
            return
        }

        Write-IterationJson -Path $script:CurrentJsonPath -Data $newData

        $reloadedData = Read-IterationJson -Path $script:CurrentJsonPath
        Assert-IterationJsonStructure -Data $reloadedData -Context 'Nachkontrolle JSON'

        $expectedJson = ConvertTo-JsonCompat -Data $newData -Depth 100 -Compress
        $actualJson = ConvertTo-JsonCompat -Data $reloadedData -Depth 100 -Compress
        if ($actualJson -ne $expectedJson) {
            throw 'Nachkontrolle fehlgeschlagen: gespeicherte JSON weicht vom erwarteten Inhalt ab.'
        }

        $script:CurrentJsonData = $reloadedData
        $script:CurrentJsonHash = Get-FileSha256 -Path $script:CurrentJsonPath

        $statusLabel.Text = "Gespeichert: $script:CurrentJsonPath"
        [System.Windows.Forms.MessageBox]::Show(
            'Die JSON-Datei wurde erfolgreich gespeichert und validiert.',
            'Gespeichert',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        ) | Out-Null
    }
    catch {
        $statusLabel.Text = "Fehler beim Speichern: $($_.Exception.Message)"
        [System.Windows.Forms.MessageBox]::Show(
            "Die Datei konnte nicht gespeichert werden.`n`n$($_.Exception.Message)",
            'Fehler',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
})

Load-JsonFilesIntoCombo

[void]$form.ShowDialog()
