# DET-COORDINATOR-IMPORTS-ITS-OWN-PACKAGE-001 · 50 imports que el compilador ya tenía

**Estado:** 🟢 Listo, sin mergear · rama `refactor/DET-COORDINATOR-IMPORTS-ITS-OWN-PACKAGE-001-drop-redundant-imports`
· worktree `../Paparcar-coord-imports` · **1.675 tests** · coordinator **1.333 → 1.283**

## Problema

`CoordinatorParkingDetector.kt` está en el paquete `io.apptolast.paparcar.domain.detection` e
**importaba 14 símbolos de ese mismo paquete** — Kotlin ya los tiene en scope — y arrastraba **36 más
que ya no usa nadie**: `outrunsPedestrianReach`, `honestZoneRadius`, `AnchorTrust`,
`ConfirmationLifecycle`, los doce predicados de `state/`, `ParkingDecisionInput`,
`UnattendedSaveInput`… sedimento de las fases del refactor, que fueron sacando el código pero no sus
imports.

Cuentan además para el tamaño del fichero. El documento de arquitectura usó *"116 de sus líneas son
el bloque de imports"* para explicar por qué el orquestador no cabía en el presupuesto del plan.
Buena parte de ese bloque no hacía falta.

## Cambio

**1.333 → 1.283 líneas**, 50 imports fuera. El diff tiene **cero** líneas que no sean `import`, salvo
un comentario de 5 líneas (abajo). 1.675 tests, los mismos que master.

Cómo se decidió qué sobra, sin criterio propio:

1. Los del **propio paquete** (`…domain.detection.X` sin más puntos) son redundantes por definición.
2. Los de **subpaquete** se borraron sólo si su símbolo no aparece como palabra en el cuerpo del
   fichero. Después, compilador y suite.

⚠️ **Rehecho sobre master, no rebasado.** El primer intento chocó de frente: master movió ese mismo
fichero con `DET-FIX-REDUCTION-TO-ITS-REDUCER-001`. Qué imports sobran es un hecho **recomputable**,
no un parche que merezca resolverse a mano — se tiró la rama vieja y se recalculó desde cero sobre el
master de hoy. Resolver el conflicto habría sido reintroducir imports que ya no tocaban.

## Lo que venía entremezclado y se ha sacado

Esto llegó **mezclado con dos cosas más** en el árbol de master, sin commitear y sin worktree — de
ahí que lo primero fuera moverlo. Separado siguiendo `feedback_split_entangled_worktree`:

1. **`delay(x)` → `delay(x.milliseconds)`** en el hold-watchdog. **Descartado.** Medido: la
   sobrecarga con `Long` compila **sin warning incluso con `-Werror`**, así que no era consecuencia
   de la subida de dependencias sino una preferencia. Que viaje en su propio cambio, no de polizón.
2. **Un `//FIXME:` preguntando si `if (config.confirmHoldMs > 0)` se cumple siempre.** Contestado en
   el sitio (comentario en inglés, las 5 líneas del diff) y promovido a ticket:
   `DET-STARVED-HOLD-HAS-NO-WITNESS-001`.

## Hallazgo de entorno — para la skill `nuevo-ticket`

Un worktree recién creado **no compila** hasta copiarle dos ficheros no versionados:

```bash
cp ../Paparcar/local.properties .                            # si no: "SDK location not found"
cp ../Paparcar/composeApp/google-services.json composeApp/   # si no: falla processProdDebugGoogleServices
```

Dos intentos perdidos cada vez que se abre uno. Merece estar en la skill.

## Relacionado

- `DET-STARVED-HOLD-HAS-NO-WITNESS-001` — el ticket que sale del FIXME.
- `DET-FIX-REDUCTION-TO-ITS-REDUCER-001` — el commit de master con el que chocó.
