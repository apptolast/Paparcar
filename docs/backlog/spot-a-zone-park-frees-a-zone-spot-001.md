# SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001 · un aparcamiento aproximado libera una plaza aproximada — nunca un pin exacto

**Estado:** ✅ Done (2026-09-08) — mergeado a master por squash; el hash vive en `MEMORY.md`.

> **Ejecutado (08-09):** política + campo end-to-end + los 3 caminos + UI de duda en buscador y
> CTA de validación en el peek propio + fix del badge recortado del tag. Suite completa verde +
> `compileMockDebugKotlin` + `compileProdDebugKotlin` + verificado en device (galería mock).
> Decisiones de ejecución al final del doc.

## Problema

Cuando la detección no presencia el reposo del coche, la sesión se guarda honestamente como
**zona** (`UserParking.zoneRadiusMeters != null`): un área de duda medida, no un punto. El mapa
propio dibuja esa duda. Pero al SALIR, `ProcessConfirmedDepartureUseCase` publica la plaza
comunitaria **como si la duda no existiera**: un spot exacto en el centro de la zona, con
`confidence = detectionReliability` — que es 0,5 en las zonas unattended y **1,0 si la sesión la
confirmó un "Sí" del usuario**, porque el "Sí" prueba el HECHO de aparcar, no el SITIO
[DET-USER-YES-IS-NOT-A-COORDINATE-001]. La publicación afirma más de lo que la sesión sabe.

El caso que destapó esto es el garaje subterráneo: entras conduciendo, el GPS muere, el ancla
queda gap-entered y el pin acaba en zona; al salir, hoy se publicaría una plaza exacta en la
puerta del garaje — repetida en cada visita. **Pero la zona NO implica garaje**, y el corpus lo
demuestra (abajo): la mayoría de zonas vienen de GNSS pobre en calle normal. El invariante no es
"detectar garajes"; es que **la forma y la confianza de una plaza publicada nunca digan más que la
sesión que la libera**.

## Doctrina violada

- **Fallo asimétrico**: ante la duda se pregunta, nunca se planta una plaza fantasma. La sesión
  respetó la doctrina (se guardó como zona); la publicación la viola (convierte la zona en punto).
- **La fiabilidad se estampa en cada sesión** — y se pierde en la frontera con la comunidad: el
  spot hereda el número (`detectionReliability`) pero no la forma (el radio de duda), y con el
  "Sí" hereda un 1,0 que habla del hecho, no de la coordenada.

## Señales / datos disponibles (medición 29-08 → 01-09, dos devices, corpus en `../paparcar-fieldlogs/` — fuera del repo)

- **6 de 36 confirms (~17%) se guardaron como zona**: 2× `unattended_zone_gap_anchor` (r=250 m,
  doubt 339–495 m), 2× `unattended_zone_walk_entered_anchor` (r=60 m), 2×
  `unattended_zone_no_drive_egress` (r=160 m). Más 1 confirm de usuario guardado como zona r=250 m
  tras un agujero GPS de 198 s. No es un caso raro: es una de cada seis sesiones.
- **La zona NO es firma de garaje**: 18 stops GAP-ENTERED en 3 días en el device de GNSS pobre
  (coche visto conduciendo a 5,6–31,9 m/s antes de agujeros de 53–288 s), todos en calle abierta.
  El corpus no contiene ni un garaje subterráneo. Gap ⇒ "reposo no presenciado", nada más.
- **La procedencia del fix no discrimina**: 8.125 fixes con `src=` y el 100% son `fused` — el
  extra `satellites` no atraviesa el FusedLocationProvider en ninguno de los dos devices
  [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001]. La señal utilizable es la que ya usamos: escalera
  de accuracy + tamaño del agujero + desenlace (zona).
- **La salida presenciada valida el ÁREA, no el punto**: el EXIT que dispara la publicación salió
  de la geocerca centrada en el centro de la zona — "un coche se fue de por aquí" es verdad medida
  a granularidad de valla; "había plaza en esta coordenada" no lo es.
- **Precedente de mercado**: la industria publica incertidumbre como área+probabilidad, nunca como
  pin — Parkopedia modela los park-out como probabilidad por región; SpotAngels publica
  "predicted availability" por manzana; las patentes de parking crowdsourced usan decay de
  confianza y expiración a "Unknown". Nuestro spot-zona es un park-out con posición a nivel de
  área: publicarlo como área es lo que hace todo el que ha medido este problema.

## Diseño — el sistema, no el parche

**El invariante vive en la frontera de publicación** — `ProcessConfirmedDepartureUseCase`, el
choke point único por donde toda sesión libera plaza — y se enuncia una vez: *la plaza publicada
hereda la FORMA de la sesión (punto o zona con su radio) y una confianza acotada por esa forma.*

Regla de decisión al publicar la salida de una sesión con `zoneRadiusMeters != null`:

1. **`radio ≤ SPOT_ZONE_PUBLISH_MAX_RADIUS_METERS`** (propuesta inicial: 150 m, la escala de
   `egressBirthFloorMeters` — una manzana escaneable a pie; ⚠️ calibrable con campo, ver
   Pendiente) → se publica **spot-zona**: mismo documento `spots` con `zoneRadiusMeters` nuevo,
   `confidence = min(detectionReliability, SPOT_ZONE_MAX_CONFIDENCE)` (propuesta: 0,5 — un
   spot-zona nunca hereda el 1,0 del "Sí", que prueba el hecho y no el sitio), y **vida
   provisional** (el mecanismo de TTL corto de [DET-HANDOFF-NOT-MANUAL-001 §B] ya existe).
2. **`radio > techo`** → **silencio conservador**: no se publica (el histórico propio se conserva
   íntegro, como siempre). Mandar a alguien a buscar plaza en un círculo de 250 m es el mismo daño
   a credibilidad que la plaza fantasma, con más pasos.
3. Sesión exacta (`zoneRadiusMeters == null`) → sin cambios.

Con las zonas medidas: las r=60 m se publican como zona; las r=160 y r=250 callan. El garaje
subterráneo queda cubierto **sin detectarlo**: su sesión acaba en zona gap-entered grande → regla 2.

**Alcance que arrastra el campo nuevo** (regla de paridad DTO end-to-end):

- `Spot.zoneRadiusMeters: Float?` + `SpotDto` + mapper + test de paridad de deserialización.
  Room de spots si aplica (verificar dónde cachea el buscador).
- **Render del buscador**: un spot con radio se dibuja como ÁREA (mismo lenguaje visual que la
  zona propia en el mapa), nunca como marker puntual; copy del peek con causa+consecuencia+remedio
  y sin jerga interna; strings en los 9 locales; estados nuevos → galería mock en la misma tarea.
- **Promoción**: `FinalizeDeducedDepartureUseCase` re-publica el mismo documento — no puede
  "ascender" un spot-zona a punto exacto, porque nada nuevo se midió sobre la coordenada.
- La rampa de frescura/TTL y `enRouteCount` aplican igual; el radio no cambia la caducidad, la
  vida provisional sí.

## Fuera de alcance, a propósito

- **Gate por POI (gasolinera / recinto de parking)**: la medición lo tumbó como proximidad — 8
  pins legítimos de calle a 32–59 m de una gasolinera colindante en 3 días; solo vale como
  containment en polígono, y el POI relevante del corpus es un nodo sin polígono. Necesita ground
  truth + más corpus → ticket futuro propio.
- **Detección de "interior"** (barómetro, GnssStatus): el barómetro está pendiente de confirmar
  que siquiera existe en los devices; el conteo de satélites vía fused está medido como ciego. No
  se promete ningún fix basado en sensores nuevos antes de medir.
- **Reducir las zonas en origen** (menos gaps, mejor ancla): trabajo de detección, con sus propios
  tickets. Este ticket solo garantiza que lo que cruza a la comunidad sea honesto.

## Criterio de éxito

1. Test unitario del choke point: sesión-zona r≤techo + salida presenciada → spot con
   `zoneRadiusMeters` y `confidence ≤ 0,5`, vida provisional; r>techo → nada publicado y sesión
   cerrada igual; sesión exacta → byte a byte como hoy; zona confirmada por "Sí" → nunca
   confidence 1,0.
2. Test de que la promoción de un provisional no borra el radio ni sube la confianza.
3. Paridad de deserialización del campo nuevo (test de sujetos, no solo del happy path).
4. Buscador: spot-zona renderiza como área — variante en galería mock, paridad con Previews.
5. Suite verde + `assembleMockDebug` + `compileProdDebugKotlin`.

## Pendiente de campo (no bloquea el gate, sí la calibración)

- **Un viaje a propósito a un garaje subterráneo** con el APK actual: confirma que la sesión acaba
  en zona grande (→ regla 2) y recoge la escalera de accuracy con `src=`.
- Cosecha fresca de `parkdiag` (01→08-09) + `dumpsys sensorservice | grep -i pressure` en ambos
  devices (cierra la puerta del barómetro).
- Ground truth de los pins junto a la gasolinera (¿alguno fue DENTRO del recinto?) → alimenta el
  ticket POI futuro.
- Con más corpus: revisar el techo de 150 m y la confianza 0,5 contra zonas reales.

## Consumidores auditados

| Consumidor | Qué asumía | Veredicto |
|---|---|---|
| `ProcessConfirmedDepartureUseCase` | publicaba punto exacto ignorando `zoneRadiusMeters` | ✅ gated (witnessed + deduced); `published` del evento lee la decisión única |
| `FinalizeDeducedDepartureUseCase` | promoción re-publicaba mismo doc como exacto | ✅ conserva forma+confianza; zona retenida no promociona; `publishedSpot` honesto |
| `ReleaseActiveParkingSessionUseCase` | publicaba el pin de la sesión con su reliability cruda | ✅ misma regla; ahora devuelve `Result<Boolean>` (¿salió plaza?) y el toast de Home solo agradece si salió |
| `ReportSpotReleasedUseCase` / `ReportSpotScheduler` / worker Android / iOS | no transportaban radio | ✅ `zoneRadiusMeters` end-to-end (workData: clave nueva, patrón CREACIÓN seguro per SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001 §ReportSpotWorker) |
| `SpotDtoMapper` (4 direcciones) + `toSpotDto` manual de Firestore + Room v4 | `Spot` sin radio | ✅ paridad completa; el testigo `FirestoreDeserializerParityTest` CAZÓ el deserializador manual en el primer run (rojo→verde) |
| Buscador: marker + `SpotPeek` | todo spot era un punto | ✅ diana ⊙ en el punto de suelo (contentId `_a_`) + fila de duda con copy propio ×9 locales |
| `ObserveZoneSpotAlertsUseCase` | alerta por spot en zona del usuario | ✅ sin cambio A PROPÓSITO: un spot-zona acotado ES una oferta real; su confianza baja y su TTL corto ya lo dicen |
| `SpotFit` / `enRouteCount` / votos | operan por spot | ✅ sin cambio — el radio no altera fit ni votos (el testigo de voto sigue exigiendo presencia) |
| Historial/stats propios | leen `UserParking`, no `Spot` | ✅ exento — el histórico no cambia |
| `ParkingPeek` (fila de zona aproximada) | prometía "edítalo" sin ser pulsable | ✅ fila pulsable + chevron → `EnterAddParkingMode(editingParkingId)`; validar el DÓNDE limpia `zoneRadiusMeters` (vía `updateParkingSessionPosition`, ya existente) y el próximo release publica exacto |

## Decisiones de ejecución (08-09)

- **`SpotReleasePolicy`** vive en `domain/spot/` como objeto puro (patrón predicado de nivel
  superior); techo 150 m y tope 0,5 como constantes SUYAS, no de `ParkingDetectionConfig` — son
  política de publicación, no de detección.
- **Un spot-zona viaja `provisional = true` SIEMPRE**, incluso con salida presenciada y también en
  la promoción: `PROVISIONAL` significa "esta oferta no está del todo probada", y aquí lo no
  probado es el SITIO — la duda de posición no caduca con un drive. Consecuencia deliberada: TTL
  corto (12 min) + nota "sin confirmar" en el peek.
- **En-route + zona**: el contentId del marcador NO codifica la diana en la rama en-route (pill
  azul dominante, set de claves acotado); combinación rara y el peek sigue diciendo la duda.
- **Room v3→v4** (`cached_spots.zoneRadiusMeters REAL` nullable), `MIGRATION_3_4` en
  `ALL_MIGRATIONS`, `4.json` exportado.
- **Sin efecto nuevo de "no se publicó"**: cuando la zona ancha retiene la plaza en un "Me voy",
  simplemente no aparece el toast de agradecimiento. Explicar la retención en el flujo de release
  es un refinamiento de copy futuro si el campo lo pide.
- **Fix arrastrado (reportado por el user, 08-09): el badge de esquina del tag del coche se
  recortaba en cuadrado.** El disco del `?`/⊙ (centro `(90,12)`, r 16+aro) sobresale 3,5u por la
  derecha y 5,5u por arriba del viewBox `104×88`, y el Canvas recorta a sus límites. El badge
  pertenece a la esquina; lo roto era el lienzo → `TAG_PAD_X/TAG_PAD_TOP` (5u laterales simétricas
  + 7u arriba, nada abajo) + `translate` del dibujo. `u` intacto (el tag se ve igual), ancla
  bottom-centre intacta, `TAG_PIN_GROUND_INSET` sigue midiendo desde un borde inferior sin pad.
  La diana nueva del spot NO tenía el defecto (extremos ≤ 78,6u de 82).
