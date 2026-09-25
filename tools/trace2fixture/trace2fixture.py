#!/usr/bin/env python3
"""[DET-SOLID-001][C4] Convert a Firestore diagnostics session export into a Kotlin replay fixture.

Input: a JSON array of the session's `events` documents (as returned by the Firestore MCP
`firestore_list_documents` on `diagnostics/{uid}/sessions/{sessionId}/events`, or an equivalent
export) — only LOCATION_FIX and STEP events are consumed.

Usage:
    python trace2fixture.py events.json TRACE_MY_BUG > Trace_MyBug.kt

Then move the file to shared/src/commonTest/kotlin/com/rndeveloper/paparcar/domain/detection/coordinator/replay/
and write a DetectionTraceReplayTest case asserting the corrected outcome.

[IOS-F0-02] TraceEvent lives in commonMain (domain.detection.coordinator.ingestion) — the generated file
imports it. Kind.ACTIVITY events (AR IN_VEHICLE ENTER/EXIT) are hand-authored from the session
logs: the diagnostics export only carries LOCATION_FIX/STEP/CADENCE.

[DET-THE-CADENCE-CORPUS-MUST-BE-EXECUTABLE-001] CADENCE events are read as STEPS. A pedal stroke is
NOT a STEP event — the two counters are disjoint in the export (field 2026-09-25 session
1790330246055: 265 STEP events, the first at Δ139 s, and ZERO in the 80 s of driving where the 25
strokes that vetoed the session happened). Consuming only LOCATION_FIX/STEP therefore produced a
fixture that looked complete at 574 rows and could not reproduce the veto it was recorded for. That
is why the 26-08 trace had to hand-reconstruct its twelve; the tool now does it, so nobody has to.
"""
import json
import sys


def field(doc: dict, name: str):
    """Supports both raw Firestore REST docs ({fields:{x:{doubleValue:..}}}) and flat dicts."""
    if "fields" in doc:
        value = doc["fields"].get(name)
        if value is None:
            return None
        return next(iter(value.values()))
    return doc.get(name)


def main() -> None:
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    events = json.load(open(sys.argv[1], encoding="utf-8"))
    if isinstance(events, dict):
        events = events.get("documents", [])
    name = sys.argv[2]

    rows = []
    cadence_strokes = []
    for doc in events:
        kind = field(doc, "type")
        t = int(field(doc, "timestampMs") or 0)
        if kind == "LOCATION_FIX":
            rows.append((t, "FIX", float(field(doc, "lat") or 0), float(field(doc, "lon") or 0),
                         float(field(doc, "accuracy") or 0), float(field(doc, "speed") or 0)))
        elif kind == "STEP":
            rows.append((t, "STEP", 0, 0, 0, 0))
        elif kind == "CADENCE":
            # [DET-THE-CADENCE-CORPUS-MUST-BE-EXECUTABLE-001] One event per newly credited fix,
            # carrying the RUNNING stroke total (`stepCount`) — so the strokes since the previous
            # event are its delta, and they are emitted AT this event's instant.
            #
            # Why that placement is faithful rather than convenient: `cadenceQualifies` judges a
            # stroke against the freshest credible fix, and this event IS the moment that fix was
            # credited (`enterAgeMs` records how stale it was — 84-1150 ms in the 25-09 session). So
            # every reconstructed stroke is judged against exactly the fix the field judged it
            # against. What is NOT recoverable is where inside the interval each stroke fell, and
            # nothing in the veto reads that.
            total = int(field(doc, "stepCount") or 0)
            cadence_strokes.append((t, total))
        elif kind == "ACTIVITY_TRANSITION":
            # [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The AR lane rides in the fixture too: the
            # vehicle-exit is what routes the scorer into its Medium-capped fast path, and the
            # cycling stamp is what can veto a whole session — a replay that drops either cannot
            # reproduce what the session actually did. Both are replayed at their TRUE transition
            # time (delivery time minus the recorded staleness), because that is the clock the
            # human-powered verdict arbitrates on.
            activity, transition = field(doc, "activity"), field(doc, "transition")
            true_t = t - int(field(doc, "enterAgeMs") or 0)
            if activity == "IN_VEHICLE" and transition == "EXIT":
                rows.append((t, "VEHICLE_EXIT", 0, 0, 0, 0))
            elif activity == "ON_BICYCLE" and transition == "ENTER":
                rows.append((true_t, "BICYCLE_ENTER", 0, 0, 0, 0))
    # Expanded after the scan so the deltas read a total-ordered sequence whatever order the export
    # came in — the REST listing is not sorted by time.
    cadence_strokes.sort(key=lambda c: c[0])
    previous_total = 0
    for t, total in cadence_strokes:
        for _ in range(max(0, total - previous_total)):
            rows.append((t, "STEP", 0, 0, 0, 0))
        previous_total = max(previous_total, total)

    rows.sort(key=lambda r: r[0])
    if not rows:
        sys.exit("no LOCATION_FIX/STEP events found")

    t0 = rows[0][0]
    print("package com.rndeveloper.paparcar.domain.detection.coordinator.replay\n")
    print("import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.TraceEvent\n")
    print(f"/** Generated by tools/trace2fixture from a diagnostics session export (t0={t0}). */")
    print(f"val {name}: List<TraceEvent> = buildList {{")
    print(f"    val t0 = {t0}L")
    print("    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =")
    print("        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))")
    print("    fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))")
    print("    fun vehicleExit(dtMs: Long) =")
    print("        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.ACTIVITY, "
          "activity = TraceEvent.Activity.VEHICLE_EXIT))")
    print("    fun bicycleEnter(dtMs: Long) =")
    print("        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.ACTIVITY, "
          "activity = TraceEvent.Activity.BICYCLE_ENTER))")
    for t, kind, lat, lon, acc, speed in rows:
        dt = t - t0
        if kind == "FIX":
            print(f"    fix({dt}, {lat}, {lon}, {acc}f, {speed}f)")
        elif kind == "VEHICLE_EXIT":
            print(f"    vehicleExit({dt})")
        elif kind == "BICYCLE_ENTER":
            print(f"    bicycleEnter({dt})")
        else:
            print(f"    step({dt})")
    print("}")


if __name__ == "__main__":
    main()
