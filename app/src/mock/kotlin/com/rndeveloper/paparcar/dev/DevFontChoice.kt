package com.rndeveloper.paparcar.dev

/**
 * Con qué familia se pinta TODA la app en el build mock [UI-TYPE-FAMILY-CANDIDATES-001].
 *
 * El selector vive en el Dev Catalog y su elección viaja por `LocalPapFontSet` desde `DevRoot`, así
 * que alcanza al grafo real: Home, Ajustes, Vehículos, onboarding… no sólo a la fila del laboratorio.
 * Ver una candidata en una fila aislada no dice si aguanta una pantalla de Ajustes entera.
 */
enum class DevFontChoice(val label: String) {
    /** Lo que se envía. Es el PRIMERO y el que arranca por defecto: si el catálogo abriera en otra
     *  familia, cualquier captura tomada del mock estaría documentando algo que no existe. */
    Shipping("Jakarta (app)"),
    Legacy("Outfit + Inter"),
    JakartaWithBarlow("Jakarta + Barlow"),
    Archivo("Archivo"),
}
