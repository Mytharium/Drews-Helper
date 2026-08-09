param(
    [Parameter(Mandatory=$true)][string]$TransportDir,
    [Parameter(Mandatory=$true)][string]$OutFile
)

# Regenerates the Drew's Helper transport graph resource from the upstream
# Skretzo/shortest-path transport TSV files, carrying per-edge requirements
# and durations through instead of discarding them.
#
# Output columns (tab separated):
#   category source destination label duration skills quests items varbits varplayers
#
# Two row shapes exist upstream and both are handled:
#   1. Direct rows  - Origin and Destination both filled. One row = one edge.
#   2. Split rows   - interface transports (gliders, balloons, mushtrees,
#                     quetzals, obelisks, some minecarts) store BOARDING tiles
#                     (origin only) and LANDING tiles (destination only) as
#                     separate rows. The edge set is the cross product of the
#                     two, with requirements unioned and duration = max.

$ErrorActionPreference = 'Stop'

$TAB = [char]9
$LF  = [string][char]10

$FileCategories = [ordered]@{
    'transports.tsv'           = 'BASELINE'
    'boats.tsv'                = 'BASELINE'
    'charter_ships.tsv'        = 'BASELINE'
    'ships.tsv'                = 'BASELINE'
    'magic_carpets.tsv'        = 'BASELINE'
    'minecarts.tsv'            = 'BASELINE'
    'teleportation_levers.tsv' = 'WILDERNESS'
    'wilderness_obelisks.tsv'  = 'WILDERNESS'
    'agility_shortcuts.tsv'    = 'AGILITY_SHORTCUT'
    'canoes.tsv'               = 'CANOE'
    'gnome_gliders.tsv'        = 'GNOME_GLIDER'
    'hot_air_balloons.tsv'     = 'HOT_AIR_BALLOON'
    'magic_mushtrees.tsv'      = 'MAGIC_MUSHTREE'
    'quetzals.tsv'             = 'QUETZAL'
}

# Upstream coordinates are space separated ("2220 3155 0").
# Drew's resource format is comma separated ("2220,3155,0").
function Convert-Coord {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $parts = @($Value.Trim() -split '\s+')
    if ($parts.Count -ne 3) { return $null }
    foreach ($p in $parts) {
        $n = 0
        if (-not [int]::TryParse($p, [ref]$n)) { return $null }
    }
    return ($parts -join ',')
}

# "12 Woodcutting" / "8 Agility;37 Ranged" -> hashtable of SkillName -> level
function ConvertFrom-SkillField {
    param([string]$Value)
    $h = @{}
    if ([string]::IsNullOrWhiteSpace($Value)) { return $h }
    foreach ($tok in ($Value -split '[;,]')) {
        $t = $tok.Trim()
        if ($t -eq '') { continue }
        $bits = @($t -split '\s+')
        if ($bits.Count -lt 2) { continue }
        $lvl = 0
        if (-not [int]::TryParse($bits[0], [ref]$lvl)) { continue }
        $name = ($bits[1..($bits.Count - 1)] -join ' ').Trim()
        if ($name -eq '') { continue }
        if ((-not $h.ContainsKey($name)) -or ($lvl -gt $h[$name])) { $h[$name] = $lvl }
    }
    return $h
}

# Merge two skill maps, taking the higher level for any skill in both.
function Merge-SkillMaps {
    param($A, $B)
    $h = @{}
    foreach ($k in $A.Keys) { $h[$k] = $A[$k] }
    foreach ($k in $B.Keys) {
        if ((-not $h.ContainsKey($k)) -or ($B[$k] -gt $h[$k])) { $h[$k] = $B[$k] }
    }
    return $h
}

function Format-SkillMap {
    param($H)
    if ($H.Count -eq 0) { return '' }
    $parts = @()
    foreach ($k in ($H.Keys | Sort-Object)) { $parts += ($k + '=' + $H[$k]) }
    return ($parts -join ';')
}

# Union two semicolon lists, de-duplicated, order preserved.
function Merge-ListField {
    param([string]$A, [string]$B)
    $seen  = @{}
    $parts = @()
    foreach ($src in @($A, $B)) {
        if ([string]::IsNullOrWhiteSpace($src)) { continue }
        foreach ($tok in ($src -split ';')) {
            $t = $tok.Trim()
            if ($t -eq '') { continue }
            if ($seen.ContainsKey($t)) { continue }
            $seen[$t] = $true
            $parts += $t
        }
    }
    return ($parts -join ';')
}

# Defensive: a stray tab in a passthrough field would break the output format.
function Format-Field {
    param([string]$Value)
    if ($null -eq $Value) { return '' }
    return $Value.Replace([string][char]9, ' ').Trim()
}

function Get-Col {
    param($Fields, $Map, [string]$Name)
    $k = $Name.ToLowerInvariant()
    if (-not $Map.ContainsKey($k)) { return '' }
    $i = $Map[$k]
    if ($i -ge $Fields.Count) { return '' }
    return $Fields[$i]
}

# Live convention: prefer the human "Display info" label, fall back to the menu option.
function Select-Label {
    param([string]$Info, [string]$MenuOption)
    if (-not [string]::IsNullOrWhiteSpace($Info)) { return $Info }
    return $MenuOption
}

$rows             = New-Object System.Collections.ArrayList
$seen             = @{}
$skippedMalformed = 0
$skippedSelfLoop  = 0
$skippedNoCoords  = 0
$dupes            = 0
$crossEdges       = 0
$missingFiles     = New-Object System.Collections.ArrayList

function Add-Edge {
    param([string]$Category, [string]$Source, [string]$Destination, [string]$Label,
          [int]$Duration, [string]$Skills, [string]$Quests, [string]$Items,
          [string]$Varbits, [string]$VarPlayers)

    if ($Source -eq $Destination) {
        $script:skippedSelfLoop++
        return
    }
    $key = $Category + '|' + $Source + '|' + $Destination + '|' + $Label
    if ($script:seen.ContainsKey($key)) {
        $script:dupes++
        return
    }
    $script:seen[$key] = $true
    [void]$script:rows.Add([PSCustomObject]@{
        category    = $Category
        source      = $Source
        destination = $Destination
        label       = $Label
        duration    = $Duration
        skills      = $Skills
        quests      = $Quests
        items       = $Items
        varbits     = $Varbits
        varplayers  = $VarPlayers
    })
}

foreach ($fileName in $FileCategories.Keys) {
    $path = Join-Path -Path $TransportDir -ChildPath $fileName
    if (-not (Test-Path -LiteralPath $path)) {
        [void]$missingFiles.Add($fileName)
        continue
    }

    $baseCategory = $FileCategories[$fileName]
    $isAgility    = ($fileName -eq 'agility_shortcuts.tsv')
    $map          = $null

    $originOnly = New-Object System.Collections.ArrayList
    $destOnly   = New-Object System.Collections.ArrayList

    foreach ($line in (Get-Content -LiteralPath $path)) {
        if ($null -eq $line) { continue }
        $trimmed = $line.Trim()
        if ($trimmed -eq '') { continue }

        # First comment line is the header; later ones are section labels.
        if ($trimmed.StartsWith('#')) {
            if ($null -eq $map) {
                $cols = $trimmed.TrimStart('#').Trim().Split($TAB)
                $map  = @{}
                for ($i = 0; $i -lt $cols.Count; $i++) {
                    $key = $cols[$i].Trim().ToLowerInvariant()
                    if ($key -ne '' -and -not $map.ContainsKey($key)) { $map[$key] = $i }
                }
            }
            continue
        }

        # Column ORDER differs between files, so everything is looked up by NAME.
        if ($null -eq $map) { continue }

        $f = $line.Split($TAB)

        $srcRaw = (Get-Col $f $map 'origin').Trim()
        $dstRaw = (Get-Col $f $map 'destination').Trim()
        $hasSrc = -not [string]::IsNullOrWhiteSpace($srcRaw)
        $hasDst = -not [string]::IsNullOrWhiteSpace($dstRaw)
        if (-not $hasSrc -and -not $hasDst) { $skippedNoCoords++; continue }

        $rec = [PSCustomObject]@{
            src        = $null
            dst        = $null
            skills     = ConvertFrom-SkillField (Get-Col $f $map 'skills')
            quests     = Format-Field (Get-Col $f $map 'quests')
            items      = Format-Field (Get-Col $f $map 'items')
            varbits    = Format-Field (Get-Col $f $map 'varbits')
            varplayers = Format-Field (Get-Col $f $map 'varplayers')
            duration   = 1
            label      = Select-Label (Format-Field (Get-Col $f $map 'display info')) (Format-Field (Get-Col $f $map 'menuoption menutarget objectid'))
        }

        $parsed = 0
        if ([int]::TryParse((Get-Col $f $map 'duration').Trim(), [ref]$parsed) -and $parsed -gt 1) {
            $rec.duration = $parsed
        }

        if ($hasSrc) {
            $rec.src = Convert-Coord $srcRaw
            if ($null -eq $rec.src) { $skippedMalformed++; continue }
        }
        if ($hasDst) {
            $rec.dst = Convert-Coord $dstRaw
            if ($null -eq $rec.dst) { $skippedMalformed++; continue }
        }

        if ($hasSrc -and $hasDst) {
            # Direct edge.
            $category = $baseCategory
            if ($isAgility -and ((Format-SkillMap $rec.skills) -match '(^|;)(Ranged|Strength)=')) {
                $category = 'GRAPPLE_SHORTCUT'
            }
            Add-Edge $category $rec.src $rec.dst $rec.label $rec.duration `
                (Format-SkillMap $rec.skills) $rec.quests $rec.items $rec.varbits $rec.varplayers
        }
        elseif ($hasSrc) { [void]$originOnly.Add($rec) }
        else             { [void]$destOnly.Add($rec) }
    }

    # Split rows: every boarding tile reaches every landing tile in the same file.
    if ($originOnly.Count -gt 0 -and $destOnly.Count -gt 0) {
        foreach ($o in $originOnly) {
            foreach ($d in $destOnly) {
                $mergedSkills = Format-SkillMap (Merge-SkillMaps $o.skills $d.skills)
                $category = $baseCategory
                if ($isAgility -and ($mergedSkills -match '(^|;)(Ranged|Strength)=')) {
                    $category = 'GRAPPLE_SHORTCUT'
                }
                $dur = [Math]::Max($o.duration, $d.duration)
                Add-Edge $category $o.src $d.dst $d.label $dur `
                    $mergedSkills `
                    (Merge-ListField $o.quests     $d.quests) `
                    (Merge-ListField $o.items      $d.items) `
                    (Merge-ListField $o.varbits    $d.varbits) `
                    (Merge-ListField $o.varplayers $d.varplayers)
                $crossEdges++
            }
        }
    }
}

$sorted = @($rows) | Sort-Object category, source, destination, label

$sb = New-Object System.Text.StringBuilder
[void]$sb.Append('# Generated from Skretzo/shortest-path transport TSV files.' + $LF)
[void]$sb.Append('# Columns: category' + $TAB + 'source' + $TAB + 'destination' + $TAB + 'label' + $TAB + 'duration' + $TAB + 'skills' + $TAB + 'quests' + $TAB + 'items' + $TAB + 'varbits' + $TAB + 'varplayers' + $LF)
[void]$sb.Append('# duration is in game ticks (floored at 1). Blank requirement fields mean no requirement recorded upstream.' + $LF)

foreach ($r in $sorted) {
    [void]$sb.Append(($r.category, $r.source, $r.destination, $r.label, $r.duration, $r.skills, $r.quests, $r.items, $r.varbits, $r.varplayers) -join $TAB)
    [void]$sb.Append($LF)
}

$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutFile, $sb.ToString(), $enc)

Write-Output ('Wrote ' + $OutFile)
Write-Output ('Total rows: ' + $sorted.Count)
Write-Output ''
Write-Output 'Per-category counts:'
$sorted | Group-Object category | Sort-Object Name | ForEach-Object {
    Write-Output ('  ' + $_.Name.PadRight(18) + $_.Count)
}
Write-Output ''
Write-Output ('Cross-product edges built: ' + $crossEdges)
Write-Output ('Skipped (no coordinates):  ' + $skippedNoCoords)
Write-Output ('Skipped (malformed coords):' + $skippedMalformed)
Write-Output ('Skipped (self-loop):       ' + $skippedSelfLoop)
Write-Output ('Duplicates dropped:        ' + $dupes)
foreach ($m in $missingFiles) {
    Write-Output ('WARNING: expected input file not found: ' + $m)
}
