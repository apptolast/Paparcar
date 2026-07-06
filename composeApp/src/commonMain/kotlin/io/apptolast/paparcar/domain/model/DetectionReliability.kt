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

data class DetectionReliabilityReport(
    val level: DetectionReliabilityLevel,
    val issues: List<DetectionReliabilityIssue>,
)
