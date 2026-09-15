package com.rndeveloper.paparcar.data.util

/**
 * Unpredictable bytes from the platform's own source. [VEH-SHARED-001]
 *
 * `kotlin.random.Random` is deliberately NOT used: it is seeded from the clock, and two phones
 * generating an invite in the same instant could produce the same code. This is the only place in
 * the app that needs randomness somebody else must not be able to reproduce.
 */
expect fun secureRandomBytes(size: Int): ByteArray
