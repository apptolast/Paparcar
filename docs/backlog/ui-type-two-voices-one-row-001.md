# UI-TYPE-TWO-VOICES-ONE-ROW-001 · La app habla con dos voces, y una fila se lee como una sola cosa

**Estado:** ✅ **Done** (29-08) — mergeado a master con el visto bueno del user ("está bien así por
ahora"), tras verlo corriendo en el Redmi.

⏳ **Lo que queda vivo, deliberadamente fuera de este cierre:**
- **Sólo se revisó Home en device.** Ajustes, Vehículos, historial, onboarding y permisos cambian de
  peso o de familia en algún punto y **no se han mirado en mano**.
- **Oppo sin instalar** — sólo se probó en el Redmi (`5f8991cb`).
- PL sigue truncando el tiempo en el caso PROVISIONAL (Resultado 6, opción 1 = aceptado por ahora).
- Explorar familias alternativas (Plus Jakarta Sans / Archivo) en el lab — petición del user, va
  aparte de este ticket.
- La higiene destapada por la auditoría sigue abierta en `ui-type-system-hygiene-001.md`.

> Sustituye al borrador `UI-TYPE-ROLE-OWNS-ITS-WEIGHT-001` (mismo worktree, doc retirado). Aquel
> atacaba sólo el peso; el user pidió reanalizar el sistema entero desde cero tras ver el choque de
> familias en la fila de plaza. El problema del peso sigue dentro, como una de las fases.

## Problema

Captura de Home en device (Redmi, tema oscuro, 29-08). Una sola pantalla, **tres familias**:

| En pantalla | Hoy |
|---|---|
| `TU ZONA` | Inter Bold 11 |
| `6` / `LIBRES` | **Barlow** 21 / **Barlow** 8.5 |
| `Calle Real` | **Outfit** Bold 18 |
| `Puerto de Santa María, Cádiz` | Inter 12 |
| `Plaza del Arenal 1` | **Outfit** Bold 14 |
| `BAJA · 426 m · 1 min en coche` | **Barlow** 13 |
| `3 en camino` | **Barlow** 13 |
| `Avisar plaza libre` | **Outfit** Bold 14 |

El choque que el user identificó a ojo, sin ver el código: en la fila de plaza conviven **Outfit**
(geométrica, ancha, redonda) y **Barlow Condensed** (estrecha, humanista) a 14 y 13 px, separadas
por 2 px de aire. Son las dos caras más distintas del set puestas en el punto donde más compiten.

Regla tipográfica que no depende del gusto: **dos caras conviven si contrastan mucho (tamaño/peso
muy distintos) o si no compiten (están lejos). Aquí no se cumple ninguna de las dos.** A esa
distancia y ese tamaño el ojo no lee jerarquía, lee un error de renderizado.

Efecto secundario medible: `426 m` en Barlow 13 pesa ópticamente **menos** que
`Puerto de Santa María` en Inter 12, porque el condensado encoge la altura de x aparente. La línea
que lleva el dato accionable —cuánto hay que andar— es la de menos presencia de la pantalla.

### Y la tercera familia no se está cobrando lo que cuesta

Barlow se justifica como *"datos que compiten por ancho horizontal"*. En la captura,
`BAJA · 426 m · 1 min en coche` **le sobra media línea**. No compite con nada. El ahorro de ancho
que paga la tercera familia no se cobra en el sitio donde más se ve.

Sitios donde Barlow sí se gana el sueldo hoy: el `6` del contador, las cifras grandes de la card de
vehículo, los ejes del chart del historial, y la fila densa
`MEDIA · SIN CONFIRMAR · 214 m · 1 min en coche`. Tres casos y medio sosteniendo una familia entera.

## Doctrina violada

- **`CLAUDE.md` § Tipografía** — la regla mental vigente es *"¿título? → Outfit. ¿Frase que se lee?
  → Inter. ¿Dato/token que se repite en filas o compite en horizontal con un nombre? → Barlow."*
  La tercera pregunta **es subjetiva** ("¿compite?" lo decide quien mira) y por eso derivó: la
  meta-line de Home se clasificó como DATA sin que compita con nada.
  ⚠️ Esta tarea **cambia esa doctrina**; hay que reescribirla en `CLAUDE.md` en el mismo commit.
- **`feedback_systems_not_patches`** — el invariante "el rol decide cómo se ve el texto" vive hoy en
  dos sitios: la tabla de roles, y 50 call sites que le sobrescriben el peso.
- **`UI-LIST-ITEM-001`** — un solo esqueleto de fila, pero con dos familias de título según quién lo
  instancie.

## Señales / datos disponibles

Todo medible en el árbol; es UI estática, no hace falta campo.
- 22 roles reales en `ui/theme/PaparcarType.kt` (⚠️ `CLAUDE.md` dice 19: le faltan `eyebrow`,
  `counter`, `counterUnit`).
- **50 overrides de peso** sobre un rol, en 3 sintaxis (`fontWeight=`, `titleWeight=`,
  `.copy(fontWeight=)`). `rowTitle`: 12 de 14 usos lo sobrescriben — su Medium declarado **no se usa
  jamás**, y se sustituye por dos pesos distintos (Bold en Home, SemiBold en Vehículos).
- `PapListItem` (`ui/components/PapListItem.kt:48-49`): 14 call sites, 8 heredan el default
  (`body`/Inter) y 6 pasan `rowTitle` (Outfit). Mismo objeto visual, dos familias.
- Rol `distance` (Barlow 13): **0 usos en producción**. Quedó huérfano al pasar la píldora del mapa
  a canvas.
- Los 4 guardrails Konsist **sí** se actualizaron al paquete `com.rndeveloper.paparcar` en F7. No
  hay verde falso.

## Diseño

### D.1 · Tres voces, con precondición objetiva

El sistema no se ordena por "qué es este texto" (subjetivo) sino por **qué trabajo hace**. Del
inventario real de textos de la app salen seis trabajos, que se reparten así:

| Voz | Familia | Qué le toca | Precondición |
|---|---|---|---|
| **Marca** | Outfit | Nombres del mundo real y títulos: `Calle Real`, `Plaza del Arenal 1`, `Ford Focus`, `Mis coches` | ¿Es un nombre propio o un título? |
| **Lectura** | Inter | Todo lo que se lee o se pulsa: prosa, acciones, estructura, **la meta-line y la taxonomía** | por defecto |
| **Cifra** | Barlow | Cifra protagonista de un bloque propio: el `6` del contador, las métricas del coche, los ejes del chart. **Nunca dentro de una fila de texto.** | ¿Es la cifra el sujeto de su propio bloque, sin compartir renglón con otra cara? |

La regla queda decidible sin abrir un fichero y sin opinar: *¿nombre o título? → Outfit. ¿cifra que
es el sujeto de su bloque? → Barlow. ¿todo lo demás? → Inter.*

⚠️ **Medido (Resultado 5):** un dato alineado en columna NO necesita cambiar de familia — la columna
la marcan la posición y el peso. Por eso la distancia de la fila (D.2) va en **Inter**, no en
Barlow, y **Home queda con dos caras**. Barlow no aparece en Home en absoluto.

Lo que cambia respecto a hoy: **la meta-line y los badges de taxonomía (`BAJA`, `FIABLE`,
`SIN CONFIRMAR`, `3 en camino`) salen de Barlow y pasan a Inter.** La jerarquía dentro de la fila
deja de venir de la familia y pasa a venir de peso, tamaño y color — que ya están haciendo ese
trabajo (`BAJA` lleva su color de frescura).

### D.2 · La fila de plaza se recompone: el dato accionable sube junto al nombre

Decisión del user (29-08). Hoy el nombre va solo y la distancia se pierde en la línea de abajo:

```
[puck]  Plaza del Arenal 1                          [3 en camino]
        BAJA · 426 m · 1 min en coche
```

Pasa a:

```
[puck]  Plaza del Arenal 1                                 426 m
        BAJA · 1 min · 3 en camino
```

Tres cosas se arreglan de una vez:
1. **La distancia recupera su jerarquía.** Es el dato que decide si vas o no; ahora comparte línea
   con el nombre en vez de ir tercera en una lista de tokens.
2. **Se crea la columna.** Alineadas al final a lo largo de las filas, las distancias forman una
   columna de cifras. Medido: **en Inter SemiBold 14 se lee igual de bien que en Barlow** — la
   columna la marcan la posición y el peso, no la cara. Va en Inter.
3. **Se descarga la meta-line.** El caso peor (`MEDIA · SIN CONFIRMAR · 214 m · 1 min en coche`)
   pierde un token, que es justo lo que permite pasarla a Inter sin desbordar.
4. **`1 min en coche` se queda** (decisión del user 29-08): ayuda a decidir rápido qué plaza coger,
   y no es redundante con la distancia para quien no traduce metros a minutos de cabeza.

⚠️ **Colisión a resolver:** `3 en camino` ocupa hoy el trailing, a la altura del título — el sitio
que pasa a ocupar la distancia. El intercambio propuesto es bajarlo a la meta-line, que queda con
hueco. **Verificar en device**: es un trailing condicional (sólo con `enRouteCount > 0`), así que
hay que ver las dos variantes de la fila, con y sin.

### D.3 · El rol posee su peso, y `rowTitle` se parte en dos

**Invariante: un rol define la apariencia COMPLETA de su texto — familia, tamaño y peso.** El call
site no ajusta ninguno de los tres. El color sigue siendo suyo (lo exige la doctrina de color: el
estado se escribe, no se tiñe).

`rowTitle` está haciendo hoy dos trabajos que D.1 separa, y por eso nunca pudo tener un peso solo:

| Rol | Familia · peso · tamaño | Para qué |
|---|---|---|
| `rowName` | **Outfit SemiBold 14** | Nombre propio en una fila: plaza, vehículo, lugar del historial |
| `rowTitle` | **Inter SemiBold 14** | Título estructural de fila: superficie de detección, filas de onboarding, empty states, filas de Ajustes, `Avisar plaza libre` |

`rowTitle`/Inter SemiBold 14 es exactamente lo que `PapListItem` ya aplicaba por defecto
(`body` + `titleWeight = SemiBold`): se asciende de "default con override" a rol de pleno derecho, y
**los 8 call sites de Ajustes/registro no cambian de aspecto** — sólo cambia el nombre de lo que
invocan.

Peso único por rol, fijado en el que la app ya usa de verdad (decisión del user: SemiBold donde haya
empate):

| Rol | Antes | Después | Efecto visible |
|---|---|---|---|
| `rowName` | Outfit Medium (+12 overrides) | Outfit **SemiBold** | Home pierde algo de peso; Vehículos igual |
| `label` | Inter Medium (+11 overrides) | Inter **SemiBold** | 8 de 11 overrides ya lo pedían |
| `heroTitle` · `cardTitle` · `cta` · `sectionTitle` | Bold / Bold / SemiBold / Bold | sin cambio | ninguno — sólo se borran 19 redundancias |

### D.4 · El guardrail vigila el peso

`TypographyGuardrailTest` gana un quinto test: en `presentation.*` / `ui.components.*`, ningún
`fontWeight` / `titleWeight` / `.copy(fontWeight = …)` a menos de N líneas de un
`PaparcarType.current.*`. Sin esto el sistema se desfonda otra vez en dos semanas — es la lección
literal del punto 2 del docstring de `PaparcarType.kt`.

### D.5 · Tabla de roles resultante

IDENTITY · Outfit: `screenTitle` 24 · `heroTitle` 28 · `sectionTitle` 20 · `cardTitle` 18 ·
**`rowName` 14** (nuevo nombre del viejo `rowTitle`)

STRUCTURE + PROSE · Inter: `sectionHeader` 12 · `subsectionHeader` 11 · `eyebrow` 11 · `cta` 15 ·
**`rowTitle` 14** (nuevo) · `subtitle` 16 · `body` 14 · `label` 12 · `caption` 12 ·
**`meta` 12** (nuevo — absorbe `metadata`) · **`badge` 12** (migra de Barlow a Inter, absorbe
`sizeToken`)

STRUCTURE + PROSE · Inter (cont.): **`rowDistance` 14 SemiBold** (nuevo — el token alineado de D.2;
en Inter, no en Barlow, por el Resultado 5. Absorbe el rol `distance` huérfano)

DATA · Barlow: `statNumber` 25 · `counter` 21 · `counterUnit` 8.5 · `chartLabel` 11 ·
`chartValue` 10 — **ninguno aparece en Home**

Neto: 22 → 21 roles, pero cada uno con una precondición que se puede comprobar.

> ✅ **DECIDIDO POR EL USER (29-08), viéndolo en el Redmi: variante E.** La fila de plaza queda en
> Outfit (nombre) + Inter (distancia arriba, meta abajo), `1 min en coche` se mantiene, y Barlow
> sale de Home para quedarse sólo en la ficha de vehículo. El truncado de PL en el caso provisional
> se acepta por ahora (Resultado 6, opción 1).

## Fases

1. ~~**Medir antes de decidir**~~ ✅ **HECHO 29-08** — ver "Medición en device" abajo.
2. ~~**Tabla de roles**~~ ✅ 23 roles: `rowTitle` partido en `rowName`(Outfit)/`rowTitle`(Inter),
   `metadata`→`meta`(Inter), `badge`→Inter absorbiendo `sizeToken`, nuevos `rowDistance`(Inter) y
   `statLabel`(Barlow), retirado el `distance` huérfano.
3. ~~**Recomponer la fila de plaza + barrer los overrides**~~ ✅ 49 overrides borrados por script +
   3 condicionales de selección + 3 `.copy(fontWeight)`. `PapListItem` pierde el parámetro
   `titleWeight` entero y su `overlineStyle` pasa de `badge` a `eyebrow`.
4. ~~**Guardrail del peso + `CLAUDE.md`**~~ ✅ 5ª regla en `TypographyGuardrailTest`, **verificada a
   mano**: se reintrodujo un `fontWeight` en `PapBadge`, el test falló, se revirtió y volvió a pasar.
5. ~~**Dev Catalog**~~ ✅ `assembleMockDebug` verde; el lab vive en el catálogo y la variante A
   reconstruye Barlow a mano para seguir sirviendo de comparador con el estado anterior.

⏳ **Pendiente:** verlo el user en mano (Home ✅ capturado, resto de pantallas sin revisar) · Oppo sin
instalar · decisión sobre explorar familias alternativas (Plus Jakarta Sans / Archivo).

## Medición en device — Redmi (`5f8991cb`), tema oscuro, 29-08

Herramienta: `app/src/mock/.../dev/TypographyLabScreen.kt` (Dev Catalog → "Laboratorio
tipográfico"). Pinta las 4 filas de la captura del problema en 4 tratamientos, más el caso peor de
ancho en ES/PL/RO y la card de métricas. APK verificado por sha256 en device.

### Resultado 1 · `tnum` NO hace nada — hipótesis REFUTADA

La variante C (`fontFeatureSettings = "tnum"`) sale **pixel a pixel idéntica** a la B. La API
compila, pero el render no cambia: ni en la meta-line ni en la cifra grande de métricas
(`1.284` / `92%` se ven iguales en Inter y en Inter+tnum).

⛔ **No apoyar ninguna decisión en cifras tabulares de Inter.** El argumento "los datos se alinean
sin cambiar de familia" queda muerto: si hace falta columna de cifras, la da Barlow.

### Resultado 2 · Inter en la meta-line desborda — el ancho de Barlow SÍ era real

Con la meta-line completa (4 tokens), **Inter 12 trunca en los tres idiomas** donde Barlow 13 cabía:

| | A · Barlow 13 | B · Inter 12 |
|---|---|---|
| ES `MEDIA · SIN CONFIRMAR · 214 m · 1 min en coche` | cabe | `… 1 min en …` |
| PL `ŚREDNIE · NIEPOTWIERDZONE · 214 m · 1 min samochodem` | cabe | `… 1 mi…` |
| RO `MEDIE · NECONFIRMAT · 214 m · 1 min cu mașina` | cabe | `… 1 min cu m…` |

Y en la fila de Repsol, B además trunca el NOMBRE (`Repsol Consistorio · Calle …`) porque el
trailing `3 en camino` le come el ancho.

O sea: la justificación de Barlow **no era falsa, era condicional**. Con la línea tal y como está
hoy, la condensada es necesaria. Lo que estaba mal era dar por hecha la condición.

### Resultado 3 · La recomposición (D) es la que desbloquea todo

Subiendo la distancia junto al nombre y bajando `3 en camino` a la meta:

- ✅ Las distancias forman **columna alineada** (426 / 518 / 419 / 214) — precondición de la voz
  Cifra cumplida de verdad, y ahí Barlow luce sin chocar con nada.
- ✅ `Repsol Consistorio · Calle Cielos` **deja de truncarse** (el trailing quedó libre).
- ✅ `FIABLE · 1 min en coche · 3 en camino` cabe entera en Inter.
- ✅ ES y RO: el caso peor cabe entero en Inter.
- ⚠️ **PL sigue truncando** por poco: `ŚREDNIE · NIEPOTWIERDZONE · 1 min samoc…`.

### Resultado 4 · No existe una condensada de Outfit ni de Inter — descartado

El user preguntó si se podía bajar de familias usando una variante condensada de las que ya hay.
**No se puede**, verificado en los `.ttf` del repo (parseo de `fvar` / `OS/2`) y contrastado con la
documentación de las fuentes:

| Fuente | Ejes variables | `usWidthClass` |
|---|---|---|
| `inter_variable.ttf` | `opsz` 14–32 · `wght` 100–900 — **sin eje `wdth`** | 5 (normal) |
| `outfit_*.ttf` | estática, sin `fvar` | 5 (normal) |
| `barlow_condensed_*.ttf` | estática | **3 (Condensed)** |

- **Outfit no tiene condensada** en ninguna versión: sólo eje de peso.
- **Inter tampoco**, ni Inter ni Inter Tight (Tight es espaciado más apretado, no una anchura
  distinta). El `opsz` al máximo aprieta algo, pero no es una condensada.
- La única superfamilia con anchuras del set es **Barlow** (Barlow / Semi Condensed / Condensed).
  Sería la vía si algún día se quisiera una sola superfamilia, pero implicaría sustituir Inter.

⛔ No volver a proponer "una condensed de Outfit/Inter": no existe.

### Resultado 5 · La variante E cae por su propio peso: Home baja a DOS familias

Si la distancia sube a la línea del nombre, **la columna la marca la POSICIÓN y el peso, no la
familia**: el token alineado al final ya se lee como dato sin necesidad de cambiar de cara.

Variante E medida (= D pero con la distancia en Inter SemiBold 14, cero Barlow en la fila):

| Caso | D (distancia en Barlow) | E (distancia en Inter) |
|---|---|---|
| Filas normales | cabe | **cabe** |
| RO, caso peor | cabe entero | **cabe entero** |
| PL, caso peor | `… 1 min samoc…` | `… 1 min samoc…` (igual) |

E no pierde nada frente a D y gana la coherencia: **la fila de plaza queda en Outfit + Inter, y
Barlow desaparece de Home**. Se queda donde el user dice que le encaja y donde la medición le da la
razón: la card de métricas y los ejes del chart, bloques donde es protagonista y no comparte renglón
con otra cara.

Con eso el sistema pasa a ser: **2 familias en el flujo (Outfit para nombres/títulos, Inter para
todo lo demás) + Barlow confinada a la ficha de vehículo.** Ninguna pantalla de Home mezcla tres
caras.

### Resultado 6 · Lo que queda en PL NO es tipográfico, es redundancia

Con la distancia arriba, `1 min en coche` sigue siendo útil (**el user lo quiere mantener: ayuda a
decidir rápido qué plaza coger**), pero en el caso PROVISIONAL la línea acumula tres tokens
(`ŚREDNIE · NIEPOTWIERDZONE · 1 min samochodem`) y PL trunca el último.

Ese caso es minoritario (spot provisional × idioma polaco) y lo que se pierde es el final del
tiempo. Opciones, en orden de menor a mayor intervención:
1. **Aceptarlo** — 1 de 9 idiomas, en el estado menos frecuente.
2. Sacar `SIN CONFIRMAR` del texto: el puck ya lleva su badge de reloj. ⚠️ Choca con
   `DET-HANDOFF-NOT-MANUAL-001 §B.3`, que separa fiabilidad y confirmación como ejes distintos —
   no hacerlo sin releer aquel ticket.
3. Acortar la traducción PL de `home_spot_unconfirmed_badge`.

**Pendiente de decisión del user.** No bloquea el resto del ticket.

### Resultado 7 (matiz al 1) · La fuente sí trae `tnum`; quien no lo aplica es Compose

`inter_variable.ttf` declara `tnum` en su tabla GSUB (junto a `pnum`, `lnum`, `zero`, `frac`…). O
sea: la fuente puede hacerlo y el que no lo pasa al render es Compose Multiplatform 1.12. La
conclusión práctica no cambia — **no apoyarse en `fontFeatureSettings`** — pero la causa es del
framework, no del fichero.

## Criterio de éxito

1. `grep` de `fontWeight`/`titleWeight` a menos de 4 líneas de un rol en
   `shared/src/commonMain/.../presentation` + `.../ui/components` → **0** fuera de allowlist.
2. `:shared:testDebugUnitTest` verde **con el test de peso fallando** si se reintroduce un override
   (verificado a mano metiendo uno y quitándolo).
3. `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` verdes.
4. **En device (Redmi + Oppo):** la fila de plaza se lee como una unidad — el user confirma que ya
   no "canta" ninguna línea. Caso peor de ancho sin desbordar en PL y RO.
5. Toda la app: como mucho **dos familias por pantalla**, salvo donde la voz Cifra aparezca como
   protagonista (contador del sheet, métricas del coche, chart).
6. `CLAUDE.md` § Tipografía reescrita: 21 roles, las 3 preguntas, `rowName` vs `rowTitle`, y
   retirada la excepción de `fontWeight` inline.

## Consumidores auditados

Rutas relativas a `shared/src/commonMain/kotlin/com/rndeveloper/paparcar/`.

### Migran a `rowName` (Outfit) — son nombres propios, 5 sitios
Los cinco llevan comentario explícito de que su Outfit fue deliberado
(`[CARD-ONE-BADGE-001]`, `[TYPO-AUDIT-001]`) — **este ticket los confirma, no los revoca**:
- `presentation/home/.../HomeSpotRows.kt:177` — nombre de la plaza
- `presentation/home/.../HomeParkingRow.kt:125` — nombre del vehículo (chip compacto)
- `presentation/vehicles/VehiclesScreen.kt:409` — nombre del coche (píldora del selector)
- `presentation/vehicles/components/HistoryTimeline.kt:193` — lugar de la sesión
- `ui/components/CarbodyInfoCard.kt:102, 204` — talla del coche *(⚠️ ¿es nombre propio o token de
  taxonomía? Decidir en fase 2: si es taxonomía va a `badge`/Inter)*

### Migran a `rowTitle` (Inter) — no son nombres propios, 5 sitios
- `presentation/home/.../HomeDetectionSurface.kt:374` — título de la superficie de detección
- `presentation/home/.../HomeSpotRows.kt:311, 347` — títulos de los dos empty states
- `presentation/home/.../HomeSpotRows.kt:386` — card `Avisar plaza libre`
- `presentation/onboarding/OnboardingScreen.kt:250` · `.../VehicleSizeExplainerScreen.kt:185`
- Ya correctos, sólo renombran: `presentation/bluetooth/BluetoothConfigScreen.kt:293` ·
  `presentation/permissions/PermissionRow.kt:99`
- **Los 8 call sites del default de `PapListItem`** (`ui/components/PapSettingRows.kt` ×3 ·
  `presentation/settings/SettingsScreen.kt` ×3 · `ui/components/VehicleTypeSelector.kt` ·
  `.../VehicleRegistrationScreen.kt`) → **sin cambio visual**

### `body` SemiBold → `rowTitle` (son títulos de fila mal etiquetados, 6)
`HomeParkingRow.kt:282, 292` (`:292` usa `.copy`) · `:308` · `PapSheet.kt:481` ·
`.../peek/PeekShared.kt:99` · `ui/components/PapListItem.kt:48-49` (**el default**)

### Barlow → Inter (la migración de D.1)
- `metadata` → `meta`: `presentation/home/.../HomeSpotRows.kt:199` (la meta-line del problema) ·
  `ui/components/PapSettingRows.kt:100` · `presentation/vehicles/components/HistoryWeeklyChart.kt:126`
- `badge` → Inter: `ui/components/SpotIndicators.kt:102, 138` (`FIABLE`, `3 en camino`) ·
  `ui/components/VehicleStatusIndicators.kt:100` · `presentation/vehicles/VehiclePageContent.kt:260` ·
  `ui/components/PapListItem.kt:56` (**default de `overlineStyle` — mal apuntado: debería ser
  `eyebrow`**) · `HomeSpotRows.kt:193` (`.copy(fontWeight)`, tercera sintaxis)
- `sizeToken` → `badge`: `ui/components/CarbodyManualPicker.kt:120`
- **Se quedan en Barlow:** `statNumber` (`VehiclePageContent.kt:236, 250`) · `counter`/`counterUnit`
  (`PapSheet.kt:384, 390`) · `chartLabel`/`chartValue` (`HistoryWeeklyChart.kt`)

### Redundancias puras — se borran sin efecto visual (19)
`heroTitle`+Bold ×7 (`OnboardingScreen.kt:141, 174, 218` · `GpsDisclaimerScreen.kt:62` ·
`PermissionsContent.kt:184` · `VehicleSizeExplainerScreen.kt:85` · `VehiclesScreen.kt:469`) ·
`cardTitle`+Bold ×6 (`DetectionTierStatusCard.kt:74` · `SettingsScreen.kt:719` ·
`HistoryComponents.kt:88` · `HistoryWeeklyChart.kt:204` · `CarbodyManualPicker.kt:73` ·
`PapAlertDialog.kt:175`) · `cta`+SemiBold ×3 (`HomeDetectionSurface.kt:489` ·
`SettingsScreen.kt:596, 746`) · `sectionTitle`+Bold ×2 (`HomeLocationBlockedState.kt:66` ·
`VehicleRegistrationScreen.kt:631`) · `label`+Medium ×2 (`PeekShared.kt:162` ·
`VehicleColorSelector.kt:81`)

### Decidir uno a uno (4)
- `PapSheet.kt:151` — único sitio que **baja** `cardTitle` a SemiBold
- `SettingsScreen.kt:774` — `sectionTitle`+ExtraBold usado como **glifo de avatar**; se eligió por
  tamaño, que es lo que el sistema existe para impedir. Rol propio o `Canvas`
- `SettingsScreen.kt:660` — único `label`+Bold
- `SpotFitRow.kt:149` — `caption`+SemiBold: ¿es `label`?

### Fuera de alcance, follow-up propio
Higiene destapada por la auditoría que no toca el sistema de voces →
`docs/backlog/ui-type-system-hygiene-001.md`: `PaparcarBottomActionBar` muerto (0 usos en
producción, sólo 2 previews — **y está en la allowlist del guardrail, protegiendo código que no se
renderiza**) · `ConnectivityBanner.kt:103` sin familia declarada (cae en `TextStyle.Default` →
fuente del sistema; no hay ningún `ProvideTextStyle` en el repo) · el CTA de
`HomeLocationBlockedState.kt:88` fuera de `PapButton` (hereda `labelLarge` 14sp donde el resto usa
`cta` 15sp) · `eyebrow` con 1 solo uso mientras `DetectionTierStatusCard.kt:68` pinta un eyebrow con
`label` · docstring de `cta` desalineado con su código (dice `labelLarge`+Bold, es SemiBold 15) ·
`counterUnit` a 8.5sp (único fraccionario del sistema) · docstring caducado en
`PapSectionHeader.kt:21`.
