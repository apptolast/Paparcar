# I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001 · 22 strings que no lee nadie, y ningún guardarraíl los veía

**Estado:** ✅ Done · mergeado a master (30-08-2026) · abierto al cerrar
`I18N-PLURAL-CATEGORIES-FOLLOW-THE-LANGUAGE-NOT-ENGLISH-001`

## Problema

Barrido de todas las keys de la base contra **857 ficheros** del repo (`.kt`, `.kts`, `.xml`,
`.java`, `.json`, `.pro`), excluyendo `build/` y los propios `strings.xml`:

| Superficie | Keys | Sin uso |
|---|---|---|
| `shared/…/composeResources` | 542 | **22** |
| `app/src/main/res` | 47 | **0** |

El ticket se abrió diciendo «23». Eran **22**: `attribution_detection_label` **sí se usa**, en
`AndroidManifest.xml:48` (`android:label="@string/…"`). El barrido inicial solo miraba `.kt`, que es
justo el fallo que el propio doc marcó como riesgo antes de tocar nada.

## Doctrina violada

Ninguna, y ese era el punto. `LocaleParityGuardrailTest` comprobaba que una key estuviese en los 9
idiomas y que sus plurales declarasen las categorías del idioma. **Una key muerta pasa las dos**:
está muerta en los nueve por igual, así que la paridad la ve perfecta. El coste no es el byte del
APK — es que **cada idioma nuevo obliga a traducirla** y cada auditoría la cuenta como viva.

## Medido antes de borrar

Primero, lo que hacía fiable el barrido: **el repo no tiene ningún acceso dinámico a recursos**
(`allStringResources`, `allPluralsResources`, `getIdentifier` → cero apariciones). Una key solo se
puede nombrar literalmente, así que buscar la cadena es suficiente. Si eso cambia, el test empieza a
mentir.

Después, las 22 una a una — no en bloque:

| Grupo | Veredicto |
|---|---|
| `home_peek_spot_compat_*` + `_compatible` / `_incompatible` / `_occupied` (8) | Muertas, **dicho por el código**: `SpotFitRow.kt:45` — *«Replaces the legacy boolean compat row with the `SpotFit` outcome»*. La fila de compatibilidad vive; estas eran su versión booleana anterior. |
| `home_vehicle_chip_status_parked` · `_driving` (2) | El chip vive (`home_vehicle_chip_status_candidate` sí se usa), pero su estado salió a `home_det_monitoring` (`HomeParkingRow.kt:153`, `BrowsePeek.kt:99`). |
| `home_vehicle_chip_badge_active` · `_badge_bt` · `home_zone_private_badge` (3) | Duplicados **exactos** de keys vivas: `vehicle_status_active`, `vehicle_card_detection_bt`, `home_zone_private_label`. |
| `home_parking_release` | Reemplazada por `home_parking_leave_release` («I'm leaving»). |
| `home_stats_free_spots_badge` | El contador se partió en cifra + `home_counter_unit_free`. [UI-SHEET-001] |
| `home_vehicle_card_status_empty` · `my_car_active_vehicle` · `my_car_set_active` · `vehicle_bt_cd` · `home_peek_no_spots` · `settings_profile_member_since` (6) | Sin equivalente vivo: la superficie que las pintaba ya no existe. |
| `vehicle_status_inactive_cd` («Detection off») | ⚠️ Parecía un agujero de accesibilidad, porque su pareja `vehicle_status_active_cd` **sí** se usa. No lo es: `VehicleWatchDot` hace *early return* con `VehicleWatch.Off` («no dot for an unwatched vehicle»), así que no hay elemento que describir. |

## Diseño

1. Las 22 borradas de los 9 ficheros (−198 líneas).
2. **`LocaleParityGuardrailTest` gana un quinto test**: toda key de la base tiene que aparecer en
   algún `.kt` **o `.xml` que no sea un recurso** — el manifest cuenta, y por eso
   `attribution_detection_label` no necesita excusa.
3. **`USAGE_ALLOWLIST` queda VACÍA.** Una allowlist nace justificando keys; si arranca con entradas
   «por si acaso», es el agujero que el test venía a cerrar.

## Criterio de éxito — verificado

- ✅ **0 keys sin uso** en las dos superficies (520 + 47).
- ✅ `:shared:testDebugUnitTest` verde: **158 clases, 1.804 tests, 0 fallos**;
  `compileProdDebugKotlin`, `compileMockDebugKotlin` y `assembleMockDebug` verdes.
- ✅ **Visto fallar**, y la falsación encontró un fallo real del propio test (abajo).

### La falsación destapó un agujero en el guardarraíl

Dos mutaciones: (A) declarar `zzz_never_wired` en los 9 sin usarla; (B) quitar del manifest la
referencia a `attribution_detection_label`.

**(A) puso el test en rojo. (B) no.** El motivo: el barrido recorría `shared/src` **entero, tests
incluidos**, y el KDoc de este mismo test nombra `attribution_detection_label` como ejemplo. **La
propia documentación del guardarraíl mantenía viva la key que vigilaba.** Cualquier key citada en un
comentario contaba como usada.

Arreglado excluyendo las fuentes de test del barrido: un string de producto lo tiene que leer código
de producto; que lo mencione un comentario no es usarlo. Repetidas las dos mutaciones, rojo con las
dos, nombrando fichero y key.

Es exactamente la lección de `UI-TYPE-SYSTEM-HYGIENE-001` en otra forma: una exención sobre algo que
no se renderiza no es una exención, es un agujero. Aquí ni siquiera hacía falta una exención — bastó
con que el test se leyera a sí mismo.
