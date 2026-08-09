# tools/

Build-time tooling. Nothing here ships inside the plugin jar — these scripts *produce* files
that live in `src/main/resources/`, and are run by hand when the upstream data changes.

## generate-drewshelper-transports.ps1

Regenerates `src/main/resources/drewshelper-transports.tsv`, the transport graph the router
loads at startup.

### What it does

Reads the per-family TSVs from a checkout of the upstream Shortest Path plugin and flattens
them into one 10-column file:

```
category  source  destination  label  duration  skills  quests  items  varbits  varplayers
```

`category` is the family name and must match a constant in `DrewsHelperTransportCategory`.
Rows whose category the enum does not know are skipped at load time rather than failing the
build, so adding a family to the data before the code is safe.

### Running it

```powershell
powershell -ExecutionPolicy Bypass -File tools\generate-drewshelper-transports.ps1 `
  -TransportDir "..\Drew Shortest Path\src\main\resources\transports" `
  -OutFile      "src\main\resources\drewshelper-transports.tsv"
```

Both parameters are required. `-TransportDir` points at the upstream `transports` folder;
`-OutFile` is overwritten in place.

### Things that will bite you if you edit this script

- **Columns are read by header name, not position.** The upstream files do not agree on
  column order, and several omit columns entirely. Never index by number.
- **Boarding and landing rows are separate records.** Gliders, balloons, mushtrees, quetzals
  and the wilderness obelisks store origins and destinations as *different rows* — the real
  edges are the cross product of the two sets. Requirements are unioned across the pair and
  the duration is the max of the two, which is what upstream's `Transport.java` does when it
  merges them. Treating one row as one edge silently drops thousands of edges and quietly
  collapsed the wilderness obelisk network from 325 edges to 7 the first time this ran.
- **Output must be UTF-8 with no BOM and LF line endings.** `Set-Content -Encoding UTF8`
  writes a BOM; use `[System.IO.File]::WriteAllText` with `UTF8Encoding($false)`.
- **PowerShell 5.1 is the target.** `[HashSet[string]]::new($collection)` does not work there
  and fails silently rather than throwing — use hashtables for set operations.

### Verifying a regeneration

The acceptance test is **"nothing was lost"**, not "the counts match". Row counts legitimately
rise when a family gains requirement data it previously had to discard, so diff the *set* of
`source -> destination` pairs against the previous file and confirm the old set is a subset of
the new one. The last regeneration went from 5,683 to 7,331 edges with zero pre-existing edges
missing.
