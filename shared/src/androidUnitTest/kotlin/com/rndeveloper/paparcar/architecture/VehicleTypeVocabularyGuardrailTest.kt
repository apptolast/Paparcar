package com.rndeveloper.paparcar.architecture

import com.rndeveloper.paparcar.domain.model.VehicleType
import org.junit.Test
import kotlin.test.assertTrue

/**
 * [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] **What a vehicle type MEANS is
 * declared on the type, never re-derived at the site that needs it.**
 *
 * The shape this forbids is not exotic — it is the most ordinary line in the codebase:
 *
 * ```kotlin
 * if (vehicleType == VehicleType.SCOOTER || vehicleType == VehicleType.BIKE) …
 * ```
 *
 * Four separate questions were spelled out that way across detection and registration: *is this
 * muscle-powered*, *does this take a parking space*, *does this have a car body*, *does a slow trip
 * contradict this profile*. They agree on today's four types, which is exactly why nobody noticed
 * they are four different questions — and why a fifth type would have inherited every answer by
 * omission, arriving as a car that parks, with a body, whose slow trips are suspicious, whatever it
 * actually was. A moped answers *motorised* and *does not take a car space*; a cargo bike answers
 * *no motor* and *does take one*.
 *
 * The four `when`s now live on [VehicleType] itself and are exhaustive, so a new constant does not
 * compile until its author has answered all four. This rule keeps the answers there.
 *
 * **What it does NOT forbid:** naming a type in an exhaustive `when` (the compiler already owns that
 * vocabulary — [VehicleType.entries] is how the picker and the icon map are built) or constructing
 * one (`vehicleType ?: VehicleType.CAR`, the registration and Room-migration defaults, which are
 * about a MISSING value, not about what a type means).
 */
class VehicleTypeVocabularyGuardrailTest {

    /**
     * Every production file that mentions the type at all. Asked of [GuardrailScope], which refuses
     * an empty selection: this is a prohibition over a filtered set, so a rename of `VehicleType`
     * would otherwise retire the rule and report the same green.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private val mentioningFiles get() = GuardrailScope.productionFilesMentioning(
        symbol = "VehicleType",
        floor = GuardrailScope.VEHICLE_TYPE_MENTIONS_FLOOR,
    )

    @Test
    fun `no production code compares a value to a VehicleType constant`() {
        val violations = mentioningFiles
            .flatMap { file ->
                COMPARISON_REGEX.findAll(file.text.withoutComments())
                    .map { "${file.name}.kt → ${it.value.trim()}" }
                    .toList()
            }
        assertTrue(
            violations.isEmpty(),
            "[a vehicle type compared to a constant — that spelling is a MEANING re-derived away " +
                "from the type, and the next type added inherits it by omission. Ask the type: " +
                "isHumanPowered / parksInASpot / hasCarbody / slowTripContradictsProfile] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it" }}",
        )
    }

    /**
     * The four questions are answered for **every** constant — which the exhaustive `when`s already
     * guarantee at compile time, so what this actually witnesses is that they have not been quietly
     * relaxed into an `else` branch. An `else` compiles forever and is the one way a new type can
     * still slip in silently.
     */
    @Test
    fun `no vehicle type question falls back to an else branch`() {
        val declaration = mentioningFiles.first { it.name == "VehicleType" }.text.withoutComments()
        val elseBranches = ELSE_REGEX.findAll(declaration).count()
        assertTrue(
            elseBranches == 0,
            "[VehicleType.kt has $elseBranches `else` branch(es)] every property here must be an " +
                "exhaustive `when` over the constants — that is the whole mechanism: a new type " +
                "must not compile until its author has answered. An `else` answers for them.",
        )
        assertTrue(
            VehicleType.entries.size >= MIN_TYPES,
            "[VehicleType has ${VehicleType.entries.size} constants] the rule above is vacuous " +
                "below $MIN_TYPES.",
        )
    }

    private fun String.withoutComments(): String =
        replace(BLOCK_COMMENT_REGEX, "").replace(LINE_COMMENT_REGEX, "")

    private companion object {
        /** `x == VehicleType.CAR` and `VehicleType.CAR != x`, either side of the operator. */
        val COMPARISON_REGEX = Regex(
            """[!=]=\s*VehicleType\.[A-Z_]+|VehicleType\.[A-Z_]+\s*[!=]=""",
        )

        /** `else ->` inside `VehicleType.kt` — the escape hatch the exhaustive `when`s must not have. */
        val ELSE_REGEX = Regex("""\belse\s*->""")

        val BLOCK_COMMENT_REGEX = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT_REGEX = Regex("""//[^\n]*""")

        const val MIN_TYPES = 2
    }
}
