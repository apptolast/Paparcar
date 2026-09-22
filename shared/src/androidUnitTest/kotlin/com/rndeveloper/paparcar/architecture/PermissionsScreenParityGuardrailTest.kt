package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Las dos pantallas de permisos —`PermissionsScreen.android.kt` y `PermissionsScreen.ios.kt`— tienen
 * que entregarle a `PermissionsContent` el MISMO juego de acciones, salvo las que se declaren
 * ausentes aquí con su razón.
 *
 * ## Por qué existe
 *
 * `PermissionsContent` declara sus callbacks con default `= {}`. Y `PermissionRow` pinta la fila
 * pulsable cuando `onGrant != null && !granted` — **`{}` no es `null`**. O sea: olvidarse de cablear
 * uno no rompe la compilación, no deja un hueco visible y no cambia nada en pantalla; produce una
 * fila con su ripple, su sombra y su semántica de botón, que al pulsarla no hace nada.
 *
 * Eso fue exactamente lo que pasó: iOS pasaba **4 de los 15** callbacks y seis filas —ubicación,
 * servicios de ubicación, segundo plano, actividad, notificaciones y batería— llevaban así desde
 * que la pantalla se compartió. Ningún test podía verlo porque el repo no tiene tests de UI de
 * Compose, y ninguna revisión lo vio porque el fichero se lee perfectamente bien: lo que falta no
 * está escrito en ninguna parte.
 *
 * Un `actual` nuevo (macOS, escritorio) hereda este guard sin tocarlo.
 * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
 */
class PermissionsScreenParityGuardrailTest {

    /**
     * La población: los parámetros de acción que declara `PermissionsContent`. Si el selector
     * dejara de encontrarlos —renombre, cambio de firma, fichero movido— la lista quedaría vacía y
     * la regla pasaría a no comprobar nada, que es el fallo contra el que existe [GuardrailScope].
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private fun contentCallbacks(): List<String> {
        val file = GuardrailScope.filesUnderPath(
            name = "PermissionsContent.kt (commonMain)",
            pathFragment = Regex("/commonMain/.*/presentation/permissions/PermissionsContent\\.kt$"),
            floor = 1,
        ).single()
        val signature = file.text.substringAfter("fun PermissionsContent(").substringBefore("\n) {")
        val callbacks = CALLBACK_PARAM.findAll(signature).map { it.groupValues[1] }.distinct().toList()
        if (callbacks.size < CALLBACK_FLOOR) {
            fail(
                "[empty population] se encontraron ${callbacks.size} callbacks en la firma de " +
                    "PermissionsContent, y se esperaban al menos $CALLBACK_FLOOR. La regla de " +
                    "paridad entre plataformas ha dejado de mirar: arregla el selector, no bajes " +
                    "el número.",
            )
        }
        return callbacks
    }

    @Test
    fun `every platform screen wires every PermissionsContent action`() {
        val callbacks = contentCallbacks()
        val offences = mutableListOf<String>()

        for ((platform, pathFragment) in PLATFORM_SCREENS) {
            val screen = GuardrailScope.filesUnderPath(
                name = "PermissionsScreen.$platform.kt",
                pathFragment = pathFragment,
                floor = 1,
            ).single()
            val absentHere = DECLARED_ABSENT[platform].orEmpty()
            for (callback in callbacks) {
                val wired = Regex("""\b$callback\s*=""").containsMatchIn(screen.text)
                if (!wired && callback !in absentHere) {
                    offences += "  - $platform: $callback"
                }
                if (wired && callback in absentHere) {
                    offences += "  - $platform: $callback está cableado pero declarado ausente aquí"
                }
            }
        }

        assertTrue(
            offences.isEmpty(),
            "[una fila pulsable que no hace nada] ${offences.size} acción(es) de PermissionsContent " +
                "sin cablear en su pantalla:\n${offences.joinToString("\n")}\n\n" +
                "Un callback sin pasar NO es null: se queda en el default `= {}` y la fila sigue " +
                "pintándose pulsable. Cablea la acción, o —si ese permiso no existe en esa " +
                "plataforma— declara la ausencia en DECLARED_ABSENT con su razón, y asegúrate de " +
                "que su fila tampoco se pinta.",
        )
    }

    private companion object {
        val CALLBACK_PARAM = Regex("""(on[A-Z]\w*)\s*:\s*\(\)\s*->\s*Unit""")

        /** Medidos 15 en master el 22-09-2026; el suelo va a la mitad, como el resto. */
        const val CALLBACK_FLOOR = 8

        val PLATFORM_SCREENS = listOf(
            "android" to Regex("/androidMain/.*/presentation/permissions/PermissionsScreen\\.android\\.kt$"),
            "ios" to Regex("/iosMain/.*/presentation/permissions/PermissionsScreen\\.ios\\.kt$"),
        )

        /**
         * Acciones que una plataforma NO cablea porque el permiso no existe ahí — y cuya fila
         * tampoco se pinta, que es la otra mitad del trato: `isBatteryOptimizationRelevant` es
         * `false` en iOS y `OemBackgroundReliabilityManager.requiresAutostartWhitelist` también.
         *
         * Cablearlas a un `Unit` sería fingir que el permiso existe; dejarlas fuera SIN que la fila
         * desaparezca es el defecto original. Por eso la lista es corta y cada entrada se gana su
         * sitio: una excepción sobre algo que sí se renderiza no es una excepción, es un agujero.
         * [UI-TYPE-SYSTEM-HYGIENE-001]
         */
        val DECLARED_ABSENT = mapOf(
            "ios" to setOf(
                "onRequestBatteryOptimization",
                "onRequestOemAutostart",
                "onRequestOemBatterySettings",
                // El carril BT no puede existir en iOS (sin eventos de conexión del BT clásico
                // para terceros), el emparejamiento no desbloquea nada y la fila no se pinta
                // (`isBtStrategyRelevant = false`). [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
                "onRequestBluetooth",
            ),
        )
    }
}
