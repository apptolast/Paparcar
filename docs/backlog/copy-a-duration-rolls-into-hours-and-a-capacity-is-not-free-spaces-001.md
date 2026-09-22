# COPY-A-DURATION-ROLLS-INTO-HOURS-AND-A-CAPACITY-IS-NOT-FREE-SPACES-001 · «955 min en coche» y «10 plazas»

**Estado:** ✅ Done · en master · `testAll`, `:app:compileMockDebugKotlin` y
`compileKotlinIosSimulatorArm64` verdes; `LocaleParityGuardrailTest` y
`ComposeResourcesEscapeGuardrailTest` con `--rerun-tasks` · ⏳ sin ver en device

Cierra **issue #130**.

## Problema

El peek de un parking público, en una sola tarjeta, dice dos cosas mal:

1. **`477.3 km · 955 min en coche`** — los minutos no pasan nunca a horas. Y el número tampoco es
   verdad: sale de `distancia_en_línea_recta / 500 m·min⁻¹`, una constante **urbana de 30 km/h**
   extrapolada a 477 km. Ese viaje son ~4,5 h por autovía, no 15 h 55 min. Formatearlo sin más
   dejaría una mentira bien escrita.
2. **`10 plazas`** debajo de `De pago` — es la CAPACIDAD del parking, y se lee como *«hay 10 plazas
   libres»*. En una app cuyo producto central son las plazas libres, esa confusión no es un matiz.

## Doctrina violada

- **Vocabulario** [COPY-SPOT-IS-NOT-A-PARKING-001]: *PLAZA es de la comunidad*. Usar «plazas» para
  el aforo de una instalación de un tercero mete la palabra del producto en un hecho que no es el
  producto — y encima insinuando disponibilidad que nadie ha medido.
- **Una estimación se llama por su nombre** [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001]: una
  cifra derivada de una constante sólo vale dentro del terreno donde esa constante es cierta.
- **Sistemas, no parches**: `durationText` ya sabe escribir un lapso («42 min», «1 h 12 min», «7 h»,
  con su comentario de por qué «7 h 0 min» no lo dice nadie). Los tiempos de andar y de conducir se
  escribían por su cuenta, en minutos, para siempre. Dos formateadores del mismo hecho.

## Señales / datos disponibles

- `TimeStringComposables.kt`: `walkTimeString` / `driveTimeString` → `%1$d min` directo; y a 30 cm
  de ahí, `durationText`, que hace justo lo que falta.
- `SpotUiUtils.kt`: `DRIVE_SPEED_M_PER_MIN = 500f` (30 km/h) y `WALK_SPEED_M_PER_MIN = 80f`
  (4,8 km/h).
- `PeekShared.DistanceRow` compone «distancia · tiempo» para plazas, parkings y gasolineras.
- `PublicParkingPeek.kt:125` → `parking_capacity` = `%1$s spaces` / `%1$s plazas`.

## Diseño

1. **Un solo idioma para un lapso de tiempo.** El span se formatea una vez (`durationText` y los
   tiempos de viaje comparten formateador) y las cadenas de viaje pasan de `%1$d` a `%1$s`:
   `"%1$s en coche"`, `"%1$s a pie"`. Así «15 h 55 min en coche» y «1 h 12 min» se escriben igual en
   toda la app, y un cambio de formato no deja la mitad de las superficies atrás.
2. **Una estimación sólo habla dentro de su terreno.** La constante de conducción es urbana, así que
   por encima de `DRIVE_ESTIMATE_MAX_METERS` el tiempo **no se escribe**: la fila dice sólo la
   distancia. Un parking a 477 km no necesita una duración inventada; necesita no inventarla.
   ⚠️ **La de andar NO se capa**, y no es una inconsistencia: la velocidad de una persona andando no
   cambia con la distancia, así que «1 h 20 min a pie» sigue siendo verdad. Lo que caduca con la
   distancia es la constante del coche, no la del pie.
3. **El aforo dice que es aforo.** `parking_capacity` pasa a «Capacity: %1$s» / «Capacidad: %1$s» en
   los 9 locales: nombra el hecho (cuántas plazas TIENE) y no puede leerse como disponibilidad.
4. **Testigo**: la decisión de si hay tiempo que escribir sale de una función **pura**
   (`driveMinutesOrNull` / `walkMinutes`) con su test, no de un `if` dentro de un `@Composable`,
   que es donde no se puede mirar.

## Criterio de éxito

- El peek de un parking a 477 km enseña `477.3 km` **y nada más**; el de uno a 2 km enseña
  `2.0 km · 4 min en coche`.
- Una duración larga se escribe en horas y minutos, con el mismo formato que las cifras de Stats.
- Ningún texto de un parking público usa «plazas» para hablar de su aforo.
- `LocaleParityGuardrailTest` verde con las tres claves tocadas en los 9 locales.

## Consumidores auditados

| Sitio | Qué escribe | Veredicto |
|---|---|---|
| `PeekShared.DistanceRow` | distancia · tiempo (plaza / parking / gasolinera) | ✅ pasa por los formateadores nuevos |
| `HomeSpotRows.kt:234` | tiempo en coche en la fila de plaza | ✅ mismo formateador |
| `durationText` (Stats, Lugares) | lapsos de sesión | ✅ ya era el bueno; ahora es el ÚNICO |
| `PublicParkingPeek.kt:125` | aforo | 🔴 el bug → cerrado aquí |
| `parkingFacts` de la fila de parking | tarifa / altura | ✅ no habla de plazas |

## Fuera de alcance (encontrado, NO tocado)

⚠️ **`formatDistance` escribe el separador decimal a mano como `.`** («477.3 km», y lo mismo el
precio «1.799 EUR/l»). En **ocho de los nueve idiomas** que enviamos el separador decimal es la
COMA. No entra aquí porque es otro invariante (formato numérico por locale) y toca más superficies
que esta tarjeta — pero está medido y merece su propio ticket.
