package io.apptolast.paparcar.domain.model

/**
 * How much the user can trust automatic detection to fire, given their one-time setup and their
 * device's background-execution environment. [DET-RELIABILITY-001]
 *
 * This is deliberately NOT the same axis as permission health ([DetectionReadiness.Blocked]):
 * broken permissions mean detection CANNOT run and precede everything; reliability grades how
 * dependable it is once it can. The industry-standard framing (Sentiance/DriveQuant/Zendrive):
 * background restrictions are manufacturer policy that no app can code around — the honest
 * answer is a reliability level plus the remedies, never a hard gate.
 */
enum class DetectionReliabilityLevel {
    /** The strongest available setup for this device — nothing left worth asking for. */
    OPTIMAL,

    /** Detection works, but one remedy would make it sturdier (surfaced as [DetectionReliabilityIssue]s). */
    GOOD,

    /** Aggressive-OEM device with neither remedy configured: the manufacturer is expected to
     *  freeze background execution and departures may be missed. The only level that warrants
     *  proactive UI (amber health row, onboarding callout). */
    REDUCED,
}

/** An actionable gap, each mapping to an existing fix surface (BT pairing / battery exemption). */
enum class DetectionReliabilityIssue {
    /** No vehicle paired to its car Bluetooth — the deterministic trigger (manifest ACL receiver:
     *  revives a dead process, no registration to lose, no Doze deferral) is unavailable. */
    NO_BLUETOOTH_PAIRING,

    /** Doze/App-Standby exemption not granted — AOSP defers our background wake-ups while parked. */
    BATTERY_OPTIMIZATION_ACTIVE,
}

/**
 * The honest PROMISE the app can make about automatic detection, given the user's setup.
 * [DET-TIERS-001]
 *
 * A different axis from [DetectionReliabilityLevel]: the level grades how *sturdy* detection is
 * within a tier (and drives the amber REDUCED nag on aggressive OEMs), while the tier is the
 * product-facing *expectation* we commit to. Deliberately depends only on the two setup facts the
 * user can act on — car Bluetooth pairing and the battery exemption — NOT on the OEM environment,
 * because the manufacturer's background policy changes robustness, not the promise we make.
 *
 * | BT paired | Battery exemption | Tier          |
 * |-----------|-------------------|---------------|
 * | ✅        | —                 | AUTOMATIC     |
 * | ❌        | ✅                | ASSISTED_PLUS |
 * | ❌        | ❌                | ASSISTED      |
 *
 * Governing rule (asymmetric failure): a lower tier promises MORE prompts, never a phantom pin —
 * "better a false negative (ask) than a false positive". Bluetooth is the only deterministic
 * arbiter, so pairing it is the single jump to AUTOMATIC.
 */
enum class DetectionTier {
    /** Car Bluetooth paired — the deterministic disconnect trigger marks and frees the spot on its
     *  own. "Tu plaza se marca y libera sola." */
    AUTOMATIC,

    /** No Bluetooth, but the battery exemption (and any OEM tweaks) lift Doze deferral — mostly
     *  hands-off, an occasional prompt. "Casi siempre solo; alguna vez te preguntaremos." */
    ASSISTED_PLUS,

    /** Neither remedy — detection runs on whatever background execution the device allows, and asks
     *  whenever it is unsure. "Detectamos lo que el móvil nos deja; cuando dudemos, te preguntamos." */
    ASSISTED,
}

data class DetectionReliabilityReport(
    val level: DetectionReliabilityLevel,
    val tier: DetectionTier,
    val issues: List<DetectionReliabilityIssue>,
)
