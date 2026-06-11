package io.apptolast.paparcar.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Field-parity guard for Firestore manual deserializers.
 *
 * Every `toXxxDto()` extension on `DocumentSnapshot` in the data/datasource/remote layer must
 * assign every property declared on the target `XxxDto`. Forgetting one causes the field to
 * silently fall back to its DTO default on read (VEHICLE-CATEGORIZATION-001 lost `carbodyType`
 * + `vehicleType` for two days, SpotDto lost six fields — both caught by this test had it
 * existed). The DTOs default every property so the compiler cannot enforce the invariant.
 *
 * How it works:
 *   1. Locate the deserializer function by name (e.g. `toSpotDto`).
 *   2. Locate the target DTO and enumerate its primary-constructor parameters.
 *   3. Read the function body text and require a `propertyName =` token for each parameter.
 *
 * Adding a new DTO field automatically makes the matching `toXxxDto()` fail until the read
 * is added. Adding a new DTO requires one extra entry in [DESERIALIZERS] below.
 */
class FirestoreDeserializerParityTest {

    // Scope to the composeApp module's commonMain — excludes stale copies under
    // `.claude/worktrees/*` and any other tooling-managed mirrors that would otherwise
    // shadow the current source on first-match lookups.
    private val scope = Konsist.scopeFromProduction("composeApp")

    @Test
    fun `every Firestore manual deserializer assigns every property of its target DTO`() {
        val violations = DESERIALIZERS.flatMap { rule ->
            val parent = findClass(rule.parentClass)
            val function = parent
                .functions(includeNested = true, includeLocal = true)
                .firstOrNull { it.name == rule.functionName }
            assertNotNull(
                function,
                "Could not find ${rule.parentClass}.${rule.functionName} in project scope",
            )

            val dto = findClass(rule.targetDto)
            val expectedFields = dtoConstructorPropertyNames(dto)
            val body = function.text
            expectedFields.filterNot { fieldName ->
                body.contains(Regex("""\b$fieldName\s*="""))
            }.map { missingField ->
                "${rule.functionName}() does not assign ${rule.targetDto}.$missingField"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Firestore deserializer parity broken — ${violations.size} field(s) silently dropped:\n" +
                violations.joinToString("\n") { "  • $it" } +
                "\n\nFix: add `<fieldName> = get<T?>(FIELD_*)` to the function body. " +
                "See feedback_dto_field_parity.md for the full checklist.",
        )
    }

    private fun findClass(simpleName: String): KoClassDeclaration {
        val cls = scope.classes(includeNested = true).firstOrNull { it.name == simpleName }
        assertNotNull(cls, "Could not find class $simpleName in project scope")
        return cls
    }

    private fun dtoConstructorPropertyNames(dto: KoClassDeclaration): List<String> {
        val params = dto.primaryConstructor?.parameters.orEmpty()
        check(params.isNotEmpty()) { "${dto.name} has no primary-constructor parameters" }
        return params.map { it.name }
    }

    private data class DeserializerRule(
        val parentClass: String,
        val functionName: String,
        val targetDto: String,
    )

    private companion object {
        // Manual Firestore deserializers — parent class + function name + target DTO.
        // Add a row here whenever a new manual DocumentSnapshot.toXxxDto() is introduced.
        val DESERIALIZERS = listOf(
            DeserializerRule("RemoteUserProfileDataSourceImpl", "toVehicleDto", "VehicleDto"),
            DeserializerRule("RemoteUserProfileDataSourceImpl", "toUserProfileDto", "UserProfileDto"),
            DeserializerRule("RemoteUserProfileDataSourceImpl", "toParkingHistoryDto", "ParkingHistoryDto"),
            DeserializerRule("FirebaseDataSourceImpl", "toSpotDto", "SpotDto"),
            DeserializerRule("FirebaseDataSourceImpl", "toZoneDto", "ZoneDto"),
        )
    }
}
