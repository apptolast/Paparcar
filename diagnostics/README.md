# Diagnostics

Captured `PARKDIAG/*` logs from debug builds, organized by date and device. Used to investigate parking-detection bugs after they happen in the wild.

## Folder layout

```
diagnostics/
  README.md                          ← this file
  YYYY-MM-DD/                        ← one folder per capture session
    <device-slug>.log                ← raw PARKDIAG log from that device
    <device-slug>.notes.md           ← optional analysis notes for that capture
```

`<device-slug>` is the short identifier of the device (`redmi-note-11`, `oppo-cph2371`, `samsung-a53`, etc.). Lowercase, hyphens for spaces. One file per device per day. If a device is captured twice in the same day, suffix with `-2`, `-3`, … (`redmi-note-11-2.log`).

Use `YYYY-MM-DD` even when the bug spans midnight; the date is the *start* of the capture session. Inside the log the original timestamps are preserved (`MM-DD HH:mm:ss.SSS`), so the cross-midnight case is unambiguous.

The `.notes.md` companion file is optional. Use it when a capture justified a ticket — link to the ticket, paste the relevant log excerpt, summarize the diagnosis. Keeps the analysis next to the raw evidence.

## Capturing a log

The diagnostic logger is `FileAntilog` (`composeApp/src/androidMain/.../logging/FileAntilog.kt`). It writes every Napier log line tagged `PARKDIAG/*` to `${context.filesDir}/parkdiag.log` on debug builds. The file rotates at 5 MB to `parkdiag.log.old`.

### Pull from device

Plug the phone in with USB debugging on, accept the fingerprint, then:

**PowerShell (recommended on this project):**

```powershell
$date = Get-Date -Format "yyyy-MM-dd"
$device = "redmi-note-11"   # adjust per device
$destDir = "diagnostics/$date"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > "$destDir/$device.log"
# Pull the rotated half if it exists
adb shell run-as io.apptolast.paparcar cat files/parkdiag.log.old > "$destDir/$device.old.log" 2>$null
```

**Bash:**

```bash
DATE=$(date +%F)
DEVICE=redmi-note-11
DEST="diagnostics/$DATE"
mkdir -p "$DEST"

adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > "$DEST/$DEVICE.log"
adb shell run-as io.apptolast.paparcar cat files/parkdiag.log.old > "$DEST/$DEVICE.old.log" 2>/dev/null
```

Multiple devices connected: prepend `-s <serial>` to each `adb` call. Get the serial with `adb devices`.

### Clear before a fresh test

```powershell
adb shell run-as io.apptolast.paparcar rm files/parkdiag.log
adb shell run-as io.apptolast.paparcar rm files/parkdiag.log.old
```

### Filter by tag

PARKDIAG tags carry the subsystem (`/Coord`, `/Confirm`, `/Service`, `/Notify`, `/SyncScheduler`, `/SyncWorker`, `/LocationUpdateSyncWorker`). For most parking-precision bugs `/Coord` and `/Confirm` are the relevant ones.

**PowerShell:**

```powershell
Select-String -Path diagnostics/2026-05-12/redmi-note-11.log `
    -Pattern "PARKDIAG/(Coord|Confirm|Service|Notify)" |
    ForEach-Object { $_.Line } |
    Set-Content diagnostics/2026-05-12/redmi-note-11.filtered.log -Encoding utf8
```

**Bash:**

```bash
grep -E "PARKDIAG/(Coord|Confirm|Service|Notify)" \
    diagnostics/2026-05-12/redmi-note-11.log \
    > diagnostics/2026-05-12/redmi-note-11.filtered.log
```

The filtered output is derivable, so we keep the raw `.log` in the repo and only commit filtered copies when they were used as the source for a ticket.

### Slice by parking session

A "parking session" in the logs is bracketed by `coordinator.invoke() entry` and `coordinator.invoke() EXITED`. To extract the Nth session:

**PowerShell:**

```powershell
$lines = Get-Content diagnostics/2026-05-12/redmi-note-11.log
$entries = (0..($lines.Count-1)) |
    Where-Object { $lines[$_] -match "coordinator\.invoke\(\) entry" }
$N = 2  # 1-based index
$start = $entries[$N-1]
$end = if ($N -lt $entries.Count) { $entries[$N]-1 } else { $lines.Count-1 }
$lines[$start..$end] | Set-Content diagnostics/2026-05-12/redmi-note-11.session-$N.log
```

## What lives here vs `.gitignore`

The repo commits raw `.log` files because they're the canonical evidence behind every detection ticket and they let the AI pair reconstruct a bug without re-running the test. They are not large in absolute terms (~700 KB per session) and survive `gh archive` cleanly.

Per-capture `.filtered.log` derivatives that nobody references can be left out of commits — they're regeneratable.

If a device ever ends up emitting genuinely huge logs (multi-MB sustained per day), consider:

- Lower `FileAntilog.maxBytes` (currently 5 MB).
- Add `*.old.log` to a gitignore once we have an archival strategy.
- Compress with `.log.gz` before committing.

## Reference

- Algorithm and fix history: `docs/detection/PARKING-DETECTION.md`.
- Logger source: `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/logging/FileAntilog.kt`.
- Tags used in the codebase: search for `PARKDIAG/` across `composeApp/src/`.
