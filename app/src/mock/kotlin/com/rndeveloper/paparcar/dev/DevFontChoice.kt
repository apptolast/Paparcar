package com.rndeveloper.paparcar.dev

/**
 * Con qué familia se pinta TODA la app en el build mock [UI-TYPE-FAMILY-CANDIDATES-001].
 *
 * El selector vive en el Dev Catalog y su elección viaja por `LocalPapFontSet` desde `DevRoot`, así
 * que alcanza al grafo real: Home, Ajustes, Vehículos, onboarding… no sólo a la fila del laboratorio.
 * Ver una candidata en una fila aislada no dice si aguanta una pantalla de Ajustes entera.
 */
enum class DevFontChoice(val label: String) {
    Current("Outfit + Inter"),
    Jakarta("Plus Jakarta"),
    JakartaFull("Jakarta full"),
    Archivo("Archivo"),
}
