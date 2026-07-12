# DET-KINEMATIC-EGRESS-001 — GPS-measured egress for mute-step-counter devices

## Field motivation (2026-07-11, Redmi)

The coordinator SAW the real parking (78-s stop at Avenida Sanlúcar) but the step sensor
delivered **zero events for the entire walk home** (MIUI sensor-hub batching flushed them 8
minutes late, in bursts). Without steps, no egress proof exists in the live session:

- The healthy device (Oppo) confirmed at **0.9 within 3 minutes** (steps+egress).
- The mute device ended with a **0.5 unattended save 18 minutes late** (after
  DET-ANCHOR-FREEZE-001 fixed the position; before it, the pin landed at the user's front door).

The step sensor is intermittent BY HARDWARE on this device class (verified: the sensor source is
byte-identical to master; the raw events were absent from the stream, then arrived batched).
Latency and reliability must not degrade systematically for exactly the "assisted tier" users.

## Insight

DET-ANCHOR-FREEZE-001 already makes the inference we need: when the anchor is FROZEN
(drive-entered stop matured ≥ `anchorFreezeStopMs`) and pedestrian-band fixes move away from it,
the freeze rule refuses to move the anchor **because it believes that movement is the person**.
This ticket uses that same belief, in one more place, to CONFIRM: a sustained, quality,
GPS-measured walk away from where the car provably rests *is* the egress — measured movement,
not a nominating OS event, so it sits inside the doctrine.

## Rule

Confirm (`kinematic+egress`, reliability `reliabilityKinematicEgress` = 0.85) when ALL hold:

1. Anchor **FROZEN** (drive-entered stop matured — carries its own walk-fix budget and the
   anchor-in-this-stop requirement).
2. ≥ `kinematicEgressMinWalkFixes` (6 ≈ 30 s of walk) quality pedestrian-band fixes since the
   freeze: speed in [`stoppedSpeedThresholdMps`, `minimumTripSpeedMps`), accuracy ≤
   `minGpsAccuracyForDriving`. Walk pauses don't reset; only a resolved CAR movement does
   (which also clears the anchor).
3. Egress displacement ≥ `minEgressDisplacementMeters` from the anchor (DET-C-01: mandatory for
   every confirm path).
4. The session **measured driving in its own stream** (`maxSpeedKmh ≥ minimumTripSpeedMps`) — a
   seeded arm whose stream never saw the trip can freeze on a pedestrian stand and must keep
   asking instead.

Precedence: steps outrank kinematics (they fire earlier and are ground truth). Human-powered
profiles still degrade to a prompt. The 2-min post-confirm hold applies (errand protection: a
resumed drive discards and re-anchors). Pin position = the frozen anchor, never the walker.

## Accepted risk

A car creeping below `minimumTripSpeedMps` for 30+ s after a 60-s stop imitates the signature —
the confirm would fire one stop early, bounded by the creep length, revertible via the saved
card, and discarded by the hold if real driving resumes. Same envelope class as the step path's
jam-jiggle, judged acceptable under the asymmetric-error rule (the systematic alternative is
late 0.5 saves for every mute-counter user).

## Future options (not in this ticket)

- AR `WALKING`/`ON_FOOT` ENTER as an optional third nomination (same MIUI hub, same latency
  disease as steps — accelerator only, never the proof).
- Wake-up variant of the step sensor (battery cost, hardware experiment).
