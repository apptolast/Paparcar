package com.rndeveloper.paparcar.domain.sharedcar

/**
 * The paired BT address, as the group is allowed to see it. [VEH-SHARED-001]
 *
 * A member's phone needs to answer ONE question — *"is the device I just paired the same car the
 * owner shared?"* — and that question is answerable by comparing tokens. The MAC itself never
 * leaves the owner's device: what travels is this value, salted with the car id so a token is only
 * meaningful inside its own group and says nothing when read next to another car's.
 *
 * ⛔ **Not a secret and not a security boundary** — it is a comparison token, and it is named that
 * way on purpose so nobody later treats it as proof of anything. Only members can read the document
 * that carries it (Firestore rules); the doctrine it serves is "don't publish a MAC", not
 * "withstand an attacker who is already inside the group".
 *
 * FNV-1a 64-bit: stable across platforms and Kotlin versions, which `String.hashCode()` is not
 * contractually. iOS and Android MUST agree on this number or a member's pairing check fails on
 * one platform only.
 */
fun sharedCarBtToken(carId: String, bluetoothDeviceId: String?): String? {
    val mac = bluetoothDeviceId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    return fnv1a64("$carId:$mac").toString(16).padStart(16, '0')
}

private fun fnv1a64(input: String): ULong {
    var hash = 0xcbf29ce484222325UL
    for (byte in input.encodeToByteArray()) {
        hash = hash xor (byte.toUByte().toULong())
        hash *= 0x100000001b3UL
    }
    return hash
}
