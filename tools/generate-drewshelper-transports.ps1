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
$ORIGINLESS_SOURCE = '-1,-1,0'

$FileCategories = [ordered]@{
    'transports.tsv'           = 'BASELINE'
    'boats.tsv'                = 'BASELINE'
    'charter_ships.tsv'        = 'BASELINE'
    'ships.tsv'                = 'BASELINE'
    'magic_carpets.tsv'        = 'BASELINE'
    'minecarts.tsv'            = 'BASELINE'
    'teleportation_spells_home.tsv' = 'BASELINE'
    'teleportation_levers.tsv' = 'WILDERNESS'
    'wilderness_obelisks.tsv'  = 'WILDERNESS'
    'agility_shortcuts.tsv'    = 'AGILITY_SHORTCUT'
    'canoes.tsv'               = 'CANOE'
    'gnome_gliders.tsv'        = 'GNOME_GLIDER'
    'hot_air_balloons.tsv'     = 'HOT_AIR_BALLOON'
    'magic_mushtrees.tsv'      = 'MAGIC_MUSHTREE'
    'quetzals.tsv'             = 'QUETZAL'
    'spirit_trees.tsv'         = 'SPIRIT_TREE'
    'fairy_rings.tsv'          = 'FAIRY_RING'
}

# Fairy rings are gated as a network by Fairytale II, and upstream records that
# nowhere - every ring row would otherwise look free to an account that cannot
# use a single one. Unioned into the quests field of every FAIRY_RING edge so the
# existing quest reader enforces it with no new code path.
$NetworkQuest = @{
    'FAIRY_RING' = 'Fairytale II - Cure a Queen'
}

# Spirit trees split in two. The base network is quest gated and detectable; the
# planted ones are player grown and nothing in the data can prove you have them.
# Upstream flags its own planted sections in the section comment, so the split is
# read from that annotation rather than a hardcoded list of destination names.
$PlantedSectionPattern = 'planted spirit tree|player-owned house'

# ...but ONLY for files that are actually organised into sections. A section
# comment sets the section for every row after it, so a file with two stray
# comments and no structure (fairy_rings.tsv has exactly that) would tag
# everything downstream of the last comment. Opt in per file, never by default.
$PlantedSplitFiles = @('spirit_trees.tsv')

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

# The id of the thing the player clicks - an object for a tree or a gate, an NPC for a glider
# captain. Upstream only ever puts it on the menu-option column, and only on the row where you
# BOARD. The overlay needs it to outline the real thing instead of the tile, so it is carried
# through on the label; the HUD strips it again for display.
function Get-TrailingId {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return '' }
    $m = [regex]::Match($Text.Trim(), '(?:^|\s)(\d+)$')
    if (-not $m.Success) { return '' }
    return $m.Groups[1].Value
}

# Only appends when the label has no id of its own - never stacks two.
function Add-TrailingId {
    param([string]$Label, [string]$Id)
    if ([string]::IsNullOrWhiteSpace($Id)) { return $Label }
    if ([string]::IsNullOrWhiteSpace($Label)) { return $Label }
    if ((Get-TrailingId $Label) -ne '') { return $Label }
    return $Label + ' ' + $Id
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
    if ($Source -eq $ORIGINLESS_SOURCE) {
        $key += '|' + $Duration + '|' + $Skills + '|' + $Quests + '|' + $Items + '|' + $Varbits + '|' + $VarPlayers
    }
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

    # Section comments carry upstream's own planted/player-built annotation. The
    # boarding half of the file is annotated and the landing half is not, but both
    # halves reuse the same section NAMES - so a name seen annotated once stays
    # planted for the rest of the file.
    $section        = ''
    $plantedSection = @{}
    $splitPlanted   = ($PlantedSplitFiles -contains $fileName)

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
                continue
            }

            $body    = $trimmed.TrimStart('#').Trim()
            $section = (($body -split '[\t(]')[0]).Trim().ToLowerInvariant()
            if ($splitPlanted -and $section -ne '' -and $body -match $PlantedSectionPattern) { $plantedSection[$section] = $true }
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
            menuId     = Get-TrailingId (Get-Col $f $map 'menuoption menutarget objectid')
            section    = $section
        }

        # Where "Display info" won (boats, canoes, balloons...) the chosen label is a destination
        # name and the id sits on the menu option of the same row. Put it back.
        $rec.label = Add-TrailingId $rec.label $rec.menuId

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
            if ($plantedSection.ContainsKey($rec.section)) { $category = 'PLANTED_' + $baseCategory }
            Add-Edge $category $rec.src $rec.dst $rec.label $rec.duration `
                (Format-SkillMap $rec.skills) `
                (Merge-ListField $rec.quests $NetworkQuest[$baseCategory]) `
                $rec.items $rec.varbits $rec.varplayers
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
                # An edge is only as usable as its weaker end: boarding a tree you
                # never grew is just as impossible as landing at one.
                if ($plantedSection.ContainsKey($o.section) -or $plantedSection.ContainsKey($d.section)) {
                    $category = 'PLANTED_' + $baseCategory
                }
                # A hub landing row is just a destination name - the id of the tree, ring or
                # glider captain you actually click lives on the BOARDING row. Without this the
                # overlay can only mark the tile, which for a spirit tree marks empty grass.
                $dur = [Math]::Max($o.duration, $d.duration)
                $hopLabel = Add-TrailingId $d.label $(if ($d.menuId) { $d.menuId } else { $o.menuId })
                Add-Edge $category $o.src $d.dst $hopLabel $dur `
                    $mergedSkills `
                    (Merge-ListField (Merge-ListField $o.quests $d.quests) $NetworkQuest[$baseCategory]) `
                    (Merge-ListField $o.items      $d.items) `
                    (Merge-ListField $o.varbits    $d.varbits) `
                    (Merge-ListField $o.varplayers $d.varplayers)
                $crossEdges++
            }
        }
    }
    elseif ($destOnly.Count -gt 0) {
        foreach ($d in $destOnly) {
            Add-Edge $baseCategory $ORIGINLESS_SOURCE $d.dst $d.label $d.duration `
                (Format-SkillMap $d.skills) `
                (Merge-ListField $d.quests $NetworkQuest[$baseCategory]) `
                $d.items $d.varbits $d.varplayers
        }
    }
}

# Upstream's data has real gaps - a gate that exists in game, blocks in the
# collision map, and has no row anywhere. Hand-editing the generated file would
# lose the fix on the next regeneration, so verified additions live here and are
# merged in. Same 10 columns as the output. Every row must cite its evidence.
$overrideCount = 0
$overridePath  = Join-Path -Path (Split-Path -Parent $PSCommandPath) -ChildPath 'transport-overrides.tsv'
if (Test-Path -LiteralPath $overridePath) {
    foreach ($line in (Get-Content -LiteralPath $overridePath)) {
        if ($null -eq $line) { continue }
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $raw = $line.Split($TAB)
        if ($raw.Count -lt 5) { throw "Override row needs at least 5 columns: $line" }
        # Trailing empty requirement columns are optional in the source file.
        $f = @('') * 10
        for ($i = 0; $i -lt [Math]::Min($raw.Count, 10); $i++) { $f[$i] = $raw[$i] }
        $dur = 1
        [void][int]::TryParse($f[4].Trim(), [ref]$dur)
        $before = $rows.Count
        Add-Edge $f[0].Trim() $f[1].Trim() $f[2].Trim() $f[3].Trim() $dur `
            (Format-Field $f[5]) (Format-Field $f[6]) (Format-Field $f[7]) `
            (Format-Field $f[8]) (Format-Field $f[9])
        if ($rows.Count -gt $before) { $overrideCount++ }
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
Write-Output ('Override rows merged:      ' + $overrideCount)
Write-Output ('Skipped (no coordinates):  ' + $skippedNoCoords)
Write-Output ('Skipped (malformed coords):' + $skippedMalformed)
Write-Output ('Skipped (self-loop):       ' + $skippedSelfLoop)
Write-Output ('Duplicates dropped:        ' + $dupes)
foreach ($m in $missingFiles) {
    Write-Output ('WARNING: expected input file not found: ' + $m)
}
