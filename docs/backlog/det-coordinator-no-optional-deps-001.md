# DET-COORDINATOR-NO-OPTIONAL-DEPS-001 · Tres dependencias que en producción nunca son null

**Estado:** ✅ Done (2026-09-02) · follow-up de `DET-DI-DETECTION-MODULE-001`

## Problema

`CoordinatorParkingDetector` declara tres dependencias de tipo nullable:

```kotlin
private val phaseSink: DetectionPhaseSink?,
private val finalizeDeducedDeparture: FinalizeDeducedDepartureUseCase?,
private val retractDeducedDeparture: RetractDeducedDepartureUseCase?,
```

**Las tres se resuelven con `get()` en producción, en las dos plataformas** — están registradas en
`detectionModule`, que es commonMain. Ninguna es opcional de verdad: el `?` sobrevive únicamente
porque los 3 setups de test pasan `null`.

`DET-DI-DETECTION-MODULE-001` ya les quitó el `= null` por defecto, así que hoy ningún call site
puede omitirlas por descuido. Lo que queda es el tipo: mientras sean `?`, el coordinator tiene tres
ramas `?.let { }` que en producción se ejecutan siempre y en los tests nunca — código vivo que la
suite no mira.

## Por qué no se hizo en el ticket padre

Volverlas no-nulables obliga a los 3 setups a construir `FinalizeDeducedDepartureUseCase` y
`RetractDeducedDepartureUseCase` reales sobre fakes. Eso **mete conducta que hoy no se ejercita**
dentro de 1.657 tests — exactamente el riesgo de tocar asserts que la Fase 3 de F6 cerró con cero.
No es trabajo de un ticket de cableado: necesita su propia pasada, midiendo qué tests cambian de
resultado y por qué.

## Diseño

Fakes de los dos use cases (no los reales sobre fakes de repos: `FakeFinalizeDeducedDeparture` que
solo registra la llamada), `phaseSink` con un `FakeDetectionPhaseSink` que acumula fases. Los tres
parámetros pasan a no-nulables y desaparecen los `?.let`.

**Bonus real:** los tests podrían entonces afirmar *que* el coordinator finaliza o retracta el
departure deducido, que hoy no lo comprueba nadie — la vía `DET-HANDOFF-NOT-MANUAL-001 §B/§B.3`
está sin cobertura de test precisamente porque se inyectaba `null`.

## Criterio de éxito

Cero `?` en dependencias del constructor del coordinator, y al menos un test que falle si el
finalize/retract deja de llamarse.

## Cierre (2026-09-02)

Cumplido, con un matiz de diseño que el ticket no anticipaba: los dos use cases son clases finales
cuyo constructor arrastra el grafo de publicación entero (`ReportSpotReleasedUseCase` →
`GetAddressAndPlaceUseCase` → …), así que "un fake que solo registra" no podía ser una subclase —
habría tenido que construir el grafo real en su `super()`, exactamente el acoplamiento que este doc
prohíbe. La costura es el patrón que el coordinator ya usaba para la tercera dependencia:
**`fun interface` junto a cada use case** (`FinalizeDeducedDeparture` / `RetractDeducedDeparture`,
espejo de `DetectionPhaseSink`), la clase real lo implementa y el módulo lo registra con `bind` —
con lo que `KoinModuleVerifyTest` sigue verificando las tres resoluciones, sin whitelists nuevas.

- Coordinator: los tres parámetros no-nulables; fuera los `?.let`/`?.invoke`/`phaseJob?.cancel()`.
- `FakeCoordinatorSeams.kt` (commonTest): `FakeDetectionPhaseSink` acumula fases,
  `FakeFinalizeDeducedDeparture` registra el vehicleId de cada llamada, `FakeRetractDeducedDeparture`
  cuenta. Los 3 setups los inyectan.
- **El bonus era el ticket**: 3 tests nuevos — el drive probado finaliza la deducción UNA vez y el
  cierre ya no retracta; el cierre sin drive medido retracta; la fase coarse llega al sink.
  Cualquiera de los tres falla si su lane se descablea.
- Medido: suite 2.121/0 (2.118 previos intactos + 3 nuevos). Diff +164/−49 en 7 ficheros + 1 nuevo.
