# =====================================================================
# DO NOT TOUCH / NICHT AUTOMATISCH ÄNDERN
# Diese Datei ist geschützt.
# KI-Assistenten, Editoren, Agenten oder Automationen dürfen diese Datei
# nicht ändern, nicht umformatieren und nicht überschreiben.
#
# Geschützte Datei: select_profile.ps1
# Status: LOCKED
# Erlaubt: nur lesen
# Verboten: editieren, refactoren, umbenennen, löschen
# =====================================================================

# select_profile.ps1
# Zeigt Profile aus .claude/profiles rekursiv in einer GUI an.
# Auswahl mit "Uebernehmen" aktualisiert workflow/runtime/app_state.json,
# ohne dass das GUI-Fenster geschlossen wird.

$ErrorActionPreference = "Stop"

# Pfade relativ zum Speicherort dieses Scripts
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$profilesDir = Join-Path $scriptDir ".claude\profiles"
$appStateFile = Join-Path $scriptDir "workflow\runtime\app_state.json"
$profilesRootFull = [System.IO.Path]::GetFullPath($profilesDir).TrimEnd('\', '/')

if (-not (Test-Path -LiteralPath $profilesDir -PathType Container)) {
    Write-Error "Profiles-Verzeichnis nicht gefunden: $profilesDir"
    exit 1
}
if (-not (Test-Path -LiteralPath $appStateFile -PathType Leaf)) {
    Write-Error "app_state.json nicht gefunden: $appStateFile"
    exit 1
}

function Get-AppState {
    return Get-Content -LiteralPath $appStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Save-AppState {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Data
    )

    $jsonText = $Data | ConvertTo-Json -Depth 20
    $directory = [System.IO.Path]::GetDirectoryName($appStateFile)
    $fileName = [System.IO.Path]::GetFileName($appStateFile)
    $tempPath = [System.IO.Path]::Combine($directory, "$fileName.tmp.$([guid]::NewGuid().ToString('N'))")
    $backupPath = [System.IO.Path]::Combine($directory, "$fileName.bak.$([guid]::NewGuid().ToString('N'))")
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)

    try {
        [System.IO.File]::WriteAllText($tempPath, $jsonText, $utf8NoBom)

        if (Test-Path -LiteralPath $appStateFile) {
            $replaceSucceeded = $false
            $maxAttempts = 8

            for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                try {
                    [System.IO.File]::Replace($tempPath, $appStateFile, $backupPath)
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
                throw "Atomisches Ersetzen von app_state.json ist fehlgeschlagen."
            }
        }
        else {
            [System.IO.File]::Move($tempPath, $appStateFile)
        }
    }
    finally {
        Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-ProfileList {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CurrentProfile
    )

    $currentProfileNormalized = ([string]$CurrentProfile -replace "\\", "/").Trim()
    $profileFiles = Get-ChildItem -Path $profilesDir -Filter "*.json" -File -Recurse | Sort-Object FullName
    $items = @()

    foreach ($file in $profileFiles) {
        try {
            $json = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
            $iterations = @($json.iterations)
            $enabledCount = ($iterations | Where-Object { $_.enabled -eq $true }).Count
            $totalCount = $iterations.Count

            $fileFull = [System.IO.Path]::GetFullPath($file.FullName)
            $relativePath = if ($fileFull.StartsWith($profilesRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
                $fileFull.Substring($profilesRootFull.Length).TrimStart('\', '/')
            } else {
                $file.Name
            }
            $relativePathUnix = $relativePath -replace "\\", "/"
            $path = ".claude/profiles/$relativePathUnix"
            $isCurrent = $path -ieq $currentProfileNormalized

            $items += [PSCustomObject]@{
                Aktuell     = if ($isCurrent) { "JA" } else { "" }
                Label       = $file.Name
                Beschreibung = if ($json.description) { $json.description } else { "-" }
                Aktiv       = "$enabledCount / $totalCount Iterationen"
                Sprache     = if ($json.code_language) { $json.code_language } else { "-" }
                Path        = $path
                _isCurrent  = $isCurrent
            }
        }
        catch {
            Write-Warning "Konnte $($file.FullName) nicht lesen: $($_.Exception.Message)"
        }
    }

    return @(
        $items |
            Sort-Object -Property @{ Expression = "_isCurrent"; Descending = $true }, Label
    )
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Data
[System.Windows.Forms.Application]::EnableVisualStyles()

$form = New-Object System.Windows.Forms.Form
$form.Text = "Profile Selector"
$form.StartPosition = "CenterScreen"
$form.Size = New-Object System.Drawing.Size(1180, 700)
$form.MinimumSize = New-Object System.Drawing.Size(900, 620)

$labelCurrent = New-Object System.Windows.Forms.Label
$labelCurrent.Location = New-Object System.Drawing.Point(14, 14)
$labelCurrent.Size = New-Object System.Drawing.Size(1140, 22)
$labelCurrent.Anchor = "Top,Left,Right"
$form.Controls.Add($labelCurrent)

$grid = New-Object System.Windows.Forms.DataGridView
$grid.Location = New-Object System.Drawing.Point(14, 42)
$grid.Size = New-Object System.Drawing.Size(1140, 300)
$grid.Anchor = "Top,Left,Right"
$grid.ReadOnly = $true
$grid.SelectionMode = "FullRowSelect"
$grid.MultiSelect = $false
$grid.AutoGenerateColumns = $true
$grid.AllowUserToAddRows = $false
$grid.AllowUserToDeleteRows = $false
$grid.AllowUserToResizeRows = $false
$grid.AllowUserToResizeColumns = $true
$grid.RowHeadersVisible = $false
$grid.AutoSizeColumnsMode = "Fill"
$form.Controls.Add($grid)

$labelAppState = New-Object System.Windows.Forms.Label
$labelAppState.Location = New-Object System.Drawing.Point(14, 350)
$labelAppState.Size = New-Object System.Drawing.Size(1140, 18)
$labelAppState.Anchor = "Top,Left,Right"
$labelAppState.Text = "app_state.json (aktueller Inhalt):"
$form.Controls.Add($labelAppState)

$textAppState = New-Object System.Windows.Forms.TextBox
$textAppState.Location = New-Object System.Drawing.Point(14, 372)
$textAppState.Size = New-Object System.Drawing.Size(1140, 220)
$textAppState.Anchor = "Top,Bottom,Left,Right"
$textAppState.Multiline = $true
$textAppState.ReadOnly = $true
$textAppState.ScrollBars = "Both"
$textAppState.WordWrap = $false
$textAppState.Font = New-Object System.Drawing.Font("Consolas", 9)
$form.Controls.Add($textAppState)

$buttonApply = New-Object System.Windows.Forms.Button
$buttonApply.Location = New-Object System.Drawing.Point(770, 600)
$buttonApply.Size = New-Object System.Drawing.Size(120, 30)
$buttonApply.Anchor = "Right,Bottom"
$buttonApply.Text = "Uebernehmen"
$form.Controls.Add($buttonApply)

$buttonReload = New-Object System.Windows.Forms.Button
$buttonReload.Location = New-Object System.Drawing.Point(900, 600)
$buttonReload.Size = New-Object System.Drawing.Size(120, 30)
$buttonReload.Anchor = "Right,Bottom"
$buttonReload.Text = "Neu laden"
$form.Controls.Add($buttonReload)

$buttonClose = New-Object System.Windows.Forms.Button
$buttonClose.Location = New-Object System.Drawing.Point(1030, 600)
$buttonClose.Size = New-Object System.Drawing.Size(120, 30)
$buttonClose.Anchor = "Right,Bottom"
$buttonClose.Text = "Schliessen"
$form.Controls.Add($buttonClose)

$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Location = New-Object System.Drawing.Point(14, 605)
$statusLabel.Size = New-Object System.Drawing.Size(740, 24)
$statusLabel.Anchor = "Left,Right,Bottom"
$statusLabel.Text = "Bereit."
$form.Controls.Add($statusLabel)

$script:CurrentProfilePath = $null

function ConvertTo-ProfileDataTable {
    param(
        [Parameter(Mandatory = $true)]
        [array]$Items
    )

    $table = New-Object System.Data.DataTable "Profiles"
    [void]$table.Columns.Add("Aktuell", [string])
    [void]$table.Columns.Add("Label", [string])
    [void]$table.Columns.Add("Beschreibung", [string])
    [void]$table.Columns.Add("Aktiv", [string])
    [void]$table.Columns.Add("Sprache", [string])
    [void]$table.Columns.Add("Path", [string])
    [void]$table.Columns.Add("_isCurrent", [bool])

    foreach ($item in $Items) {
        $row = $table.NewRow()
        $row["Aktuell"] = [string]$item.Aktuell
        $row["Label"] = [string]$item.Label
        $row["Beschreibung"] = [string]$item.Beschreibung
        $row["Aktiv"] = [string]$item.Aktiv
        $row["Sprache"] = [string]$item.Sprache
        $row["Path"] = [string]$item.Path
        $row["_isCurrent"] = [bool]$item._isCurrent
        [void]$table.Rows.Add($row)
    }

    return ,$table
}

function Refresh-AppStatePreview {
    try {
        $rawText = Get-Content -LiteralPath $appStateFile -Raw -Encoding UTF8
        if ([string]::IsNullOrWhiteSpace($rawText)) {
            $textAppState.Text = "{}"
            return
        }

        try {
            $obj = $rawText | ConvertFrom-Json
            $textAppState.Text = $obj | ConvertTo-Json -Depth 20
        }
        catch {
            # Falls app_state.json formatiert/inkonsistent ist, Rohtext anzeigen.
            $textAppState.Text = $rawText
        }
    }
    catch {
        $textAppState.Text = "Fehler beim Lesen von app_state.json: $($_.Exception.Message)"
    }
}

function Refresh-Grid {
    try {
        $appState = Get-AppState
        $script:CurrentProfilePath = [string]$appState.profile_path
        $list = @(Get-ProfileList -CurrentProfile $script:CurrentProfilePath)

        if ($list.Count -eq 0) {
            $grid.DataSource = $null
            $labelCurrent.Text = "Aktuelles Profil: $($script:CurrentProfilePath)"
            $statusLabel.Text = "Keine lesbaren Profile gefunden unter $profilesDir"
            Refresh-AppStatePreview
            return
        }

        $table = ConvertTo-ProfileDataTable -Items $list
        $bindingSource = New-Object System.Windows.Forms.BindingSource
        $bindingSource.DataSource = $table
        $grid.DataSource = $null
        $grid.DataSource = $bindingSource

        if ($grid.Columns.Contains("_isCurrent")) {
            $grid.Columns["_isCurrent"].Visible = $false
        }

        foreach ($columnName in @("Aktuell", "Label", "Beschreibung", "Aktiv", "Sprache", "Path")) {
            if ($grid.Columns.Contains($columnName)) {
                $grid.Columns[$columnName].SortMode = [System.Windows.Forms.DataGridViewColumnSortMode]::Automatic
            }
        }

        # Fill-Modus: sinnvolle Mindestbreiten und Gewichtung pro Spalte.
        if ($grid.Columns.Contains("Aktuell")) {
            $grid.Columns["Aktuell"].MinimumWidth = 60
            $grid.Columns["Aktuell"].FillWeight = 40
        }
        if ($grid.Columns.Contains("Label")) {
            $grid.Columns["Label"].MinimumWidth = 150
            $grid.Columns["Label"].FillWeight = 140
        }
        if ($grid.Columns.Contains("Beschreibung")) {
            $grid.Columns["Beschreibung"].MinimumWidth = 220
            $grid.Columns["Beschreibung"].FillWeight = 220
        }
        if ($grid.Columns.Contains("Aktiv")) {
            $grid.Columns["Aktiv"].MinimumWidth = 120
            $grid.Columns["Aktiv"].FillWeight = 90
        }
        if ($grid.Columns.Contains("Sprache")) {
            $grid.Columns["Sprache"].MinimumWidth = 90
            $grid.Columns["Sprache"].FillWeight = 70
        }
        if ($grid.Columns.Contains("Path")) {
            $grid.Columns["Path"].MinimumWidth = 240
            $grid.Columns["Path"].FillWeight = 230
        }

        $labelCurrent.Text = "Aktuelles Profil: $($script:CurrentProfilePath)"
        $statusLabel.Text = "Profile geladen: $($list.Count)"
        Refresh-AppStatePreview

        $selectedRowIndex = -1
        for ($i = 0; $i -lt $grid.Rows.Count; $i++) {
            $rowView = $grid.Rows[$i].DataBoundItem
            if ($null -ne $rowView -and [string]$rowView.Row["Path"] -ieq $script:CurrentProfilePath) {
                $selectedRowIndex = $i
                break
            }
        }

        if ($selectedRowIndex -ge 0 -and $selectedRowIndex -lt $grid.Rows.Count) {
            $grid.ClearSelection()
            $grid.Rows[$selectedRowIndex].Selected = $true
            $grid.CurrentCell = $grid.Rows[$selectedRowIndex].Cells[0]
        }
    }
    catch {
        $statusLabel.Text = "Fehler beim Laden: $($_.Exception.Message)"
        Refresh-AppStatePreview
        [System.Windows.Forms.MessageBox]::Show(
            "Profile konnten nicht geladen werden.`n`n$($_.Exception.Message)",
            "Fehler",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
}

function Apply-SelectedProfile {
    if ($grid.SelectedRows.Count -eq 0) {
        [System.Windows.Forms.MessageBox]::Show(
            "Bitte zuerst ein Profil auswaehlen.",
            "Hinweis",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        ) | Out-Null
        return
    }

    $rowItem = $grid.SelectedRows[0].DataBoundItem
    if ($null -eq $rowItem -or $null -eq $rowItem.Row -or -not $rowItem.Row.Table.Columns.Contains("Path")) {
        [System.Windows.Forms.MessageBox]::Show(
            "Die ausgewaehlte Zeile ist ungueltig.",
            "Fehler",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
        return
    }

    $selectedPath = [string]$rowItem.Row["Path"]
    if ([string]::IsNullOrWhiteSpace($selectedPath)) {
        [System.Windows.Forms.MessageBox]::Show(
            "Der Pfad des ausgewaehlten Profils ist leer.",
            "Fehler",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
        return
    }

    if ($selectedPath -ieq $script:CurrentProfilePath) {
        $statusLabel.Text = "Profil ist bereits aktiv: $selectedPath"
        return
    }

    try {
        $appState = Get-AppState
        $appState.profile_path = $selectedPath
        Save-AppState -Data $appState

        $statusLabel.Text = "Aktiv gesetzt: $selectedPath"
        Refresh-Grid
    }
    catch {
        $statusLabel.Text = "Fehler beim Speichern: $($_.Exception.Message)"
        [System.Windows.Forms.MessageBox]::Show(
            "app_state.json konnte nicht aktualisiert werden.`n`n$($_.Exception.Message)",
            "Fehler",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
}

$buttonApply.Add_Click({ Apply-SelectedProfile })
$buttonReload.Add_Click({ Refresh-Grid })
$buttonClose.Add_Click({ $form.Close() })
$grid.Add_CellDoubleClick({ Apply-SelectedProfile })
$form.Add_Shown({ Refresh-Grid })

[void]$form.ShowDialog()
