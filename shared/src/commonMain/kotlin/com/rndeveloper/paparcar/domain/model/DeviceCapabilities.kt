package com.rndeveloper.paparcar.domain.model

/**
 * What the PLATFORM structurally supports — as opposed to what the user has configured.
 * [IOS-F0-03, auditoría P5]
 *
 * The reliability evaluator's legs are remedies the user can act on; a leg the platform cannot
 * offer at all is N/A — never "unsatisfied": it must not lower the level, must not surface an
 * issue, and must not render a fix CTA the user cannot complete ("pair your car" on a platform
 * where third-party apps cannot see BT Classic connections).
 *
 * Static per platform, declared once in each platform's Koin module:
 *  - Android: both true (ACL receiver + `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
 *  - iOS: both false — BT Classic ACL events are invisible to third-party apps
 *    (`docs/IOS-IMPLEMENTATION-PLAN.md` §2.4) and no battery-exemption concept exists; the
 *    OS wake mesh replaces it. [DetectionTier.AUTOMATIC] is therefore unreachable on iOS and
 *    the tier ceiling is ASSISTED (the with/without-Always sub-state arrives with the port's F3).
 */
data class DeviceCapabilities(
    val supportsBtStrategy: Boolean,
    val supportsBatteryExemption: Boolean,
)
