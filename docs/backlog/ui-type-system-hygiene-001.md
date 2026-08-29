# UI-TYPE-SYSTEM-HYGIENE-001 · Lo que la auditoría tipográfica destapó y no toca el sistema de voces

**Estado:** ✅ Done · 8 de los 9 puntos cerrados; el 9º (medir `counterUnit` con `fontScale` bajo en
device) queda anotado en §6 y en `MEMORY.md`, porque es una medida, no código.

Hallazgos verificados en el árbol (`b949efa1`, post F7) durante la auditoría tipográfica del 29-08,
**re-verificados uno a uno sobre `3d6e24cc`** al abrir esta rama. Ninguno afecta al reparto de
voces, por eso salen de aquel ticket. Rutas relativas a
`shared/src/commonMain/kotlin/com/rndeveloper/paparcar/` salvo indicación.

## Estado de cada hallazgo al re-verificar (29-08, sobre `3d6e24cc`)

| # | Hallazgo | Al abrir esta rama |
|---|---|---|
| 1 | `PaparcarBottomActionBar` muerto y allowlisted | 🔴 vivo → se cierra aquí |
| 2 | `ConnectivityBanner` en Roboto | ✅ lo cerró `80c00faf` (`label`) |
| 3 | CTA de `HomeLocationBlockedState` fuera de la convención | 🟡 el rol ya es `cta`; lo estructural sale a ticket propio |
| 4 | `eyebrow` con un solo consumidor | 🔴 vivo → se cierra aquí |
| 5 | Documentación desalineada (3 puntos) | 🔴 2 de 3 vivos (el docstring de `cta` se arregló solo al reescribir los roles) |
| 6 | `counterUnit` a 8.5sp sin medir con `fontScale` | 🔴 vivo → y al mirarlo apareció un defecto REAL de `fontScale` (§6) |
| 7 | Roles CIFRA sin `lineHeight` | 🟡 quedan `chartLabel` y `chartValue` (`sizeToken`/`distance` ya no existen; `badge` pasó a LECTURA) |
| 8 | `CLAUDE.md` dice 23 roles | 🔴 vivo → hay **22**, y el 23º de la lista (`rowDistance`) **no existe en el código** |
| 9 | *(nuevo)* Los comentarios siguen nombrando familias retiradas | 🔴 ~30 sitios → se cierra aquí |

## Doctrina violada

- **La allowlist de un guardarraíl no puede eximir a código que no se renderiza** (§1): la excepción
  deja de ser una decisión documentada y pasa a ser un agujero.
- **El rol dice lo que es** (§4): pintar un eyebrow con `label` reintroduce la elección de estilo en
  el call site, que es justo lo que el sistema de roles retiró.
- **Un comentario que nombra una familia que ya no se compila miente** (§9). La app envía UNA
  familia desde `d0fdc4ae`; el vocabulario durable son las VOCES (marca / lectura / cifra), no
  Outfit / Inter / Barlow.
- **Una métrica en sp aplicada como dp deja de escalar con el usuario** (§6).

## 1 · `PaparcarBottomActionBar` está muerto y el guardrail lo protege

`ui/components/PaparcarBottomActionBar.kt` no lo instancia **ninguna** pantalla de producción: sus
únicos consumidores son dos previews en `shared/src/androidMain/.../HomeSheetPreviews.kt:471,479`.

Y está en `INLINE_SP_ALLOWLIST` de `TypographyGuardrailTest` (y por herencia en `WEIGHT_ALLOWLIST`),
o sea **el test exime de la regla a código que no se renderiza**.

**Hecho:** borrado el componente, sus dos previews, su mención en la cabecera de sección de
`HomeSheetPreviews.kt` y sus dos entradas de allowlist.

## 2 · `ConnectivityBanner` se pintaba con la fuente del sistema

✅ Cerrado fuera de este ticket por `80c00faf` [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]:
`ConnectivityBanner.kt:108` usa `PaparcarType.current.label` y salió de la allowlist.

## 3 · Un CTA fuera de la convención de botones

`HomeLocationBlockedState.kt:87` ya pinta su label con el rol `cta`, así que lo TIPOGRÁFICO está
cerrado. Lo que queda es estructural: sigue siendo un `Button` de M3 con `colors`/`shape`/altura
propios, porque `PapPrimaryButton` no tiene variante destructiva.

Barrido de consumidores — `Button(` crudo de M3 en `presentation/` + `ui/`:

| Call site | Por qué no pasa por `PapPrimaryButton` | Veredicto |
|---|---|---|
| `HomeLocationBlockedState.kt:78` | relleno `error` + alto 52dp + `shapes.medium` | → ticket nuevo |
| `BluetoothConfigScreen.kt:273` | ninguna: es un CTA normal | → ticket nuevo |
| `VehicleRegistrationScreen.kt:474` | botón compacto en el `trailing` de una fila | → ticket nuevo |
| `PapAlertDialog.kt:249` | ES un componente del sistema (acento parametrizado) | exento |

Los cuatro usan ya el rol `cta`: no hay deuda tipográfica. La consolidación del botón sale a
`docs/backlog/ui-button-one-canonical-cta-001.md` — es una decisión de API de componente, no de
tipografía, y tocar el relleno `error` entra en la doctrina de color.

Sí se arregla aquí un daño colateral: `HomeLocationBlockedState.kt` importaba `FontWeight` sin
usarlo, residuo de cuando el call site fijaba el peso.

## 4 · El rol `eyebrow` casi no se usa, y donde tocaría se usa otro

`presentation/permissions/DetectionTierStatusCard.kt:68` pinta un eyebrow de manual —string
`permissions_tier_status_eyebrow`, `.uppercase()` aplicado— con el rol `label` (Inter SemiBold 12,
tracking 0.5) en vez de `eyebrow` (Bold 11, tracking 1.2).

**Hecho:** pasa a `eyebrow`. Con esto el rol tiene 2 consumidores (`PapSheet` y esta tarjeta), que
son exactamente los dos sitios donde una línea en caps cualifica un título justo debajo.

*(El default `overlineStyle` de `PapListItem` ya quedó apuntando a `eyebrow` en
`UI-TYPE-TWO-VOICES-ONE-ROW-001` — verificado.)*

## 5 · Documentación desalineada con el código

- **`ui/theme/PaparcarType.kt:89`** — el docstring de `cta` decía *"labelLarge weight-bumped to
  Bold"*. ✅ Ya no: la reescritura de roles lo dejó en *"Primary CTA / button label."*
- **`ui/components/PapSectionHeader.kt:21`** — describe la receta como *"labelMedium + ExtraBold +
  1sp tracking"*, redacción anterior al sistema de roles, y ya ni siquiera es cierta (el componente
  lee `sectionHeader` / `subsectionHeader`). **Reescrito.**
- **`ui/theme/Typography.kt:97`** — la extensión `Typography.appBarTitle` sólo la consumían dos
  previews. Duplica el rol `screenTitle` y su KDoc aún prometía Outfit. **Retirada**, y las previews
  pasan a `PaparcarType.current.screenTitle`.

## 6 · `counterUnit` a 8.5sp — y el `fontScale` que no llegaba

`ui/theme/PaparcarType.kt:273` — 8.5sp es el único valor fraccionario del sistema y el más pequeño
(el siguiente es `chartValue` a 10sp). Es el `LIBRES` bajo el contador del sheet.

Al ir a medirlo apareció el defecto de verdad, en `PapSheet.kt:391-397`: la corrección óptica del
bloque «cifra sobre unidad» se calcula en **sp** (`figureOpticalLiftSp`, alimentada con
`fontSize.value`) y se aplica como **`.dp`**. Coinciden sólo con `fontScale = 1.0`. Con el tipo de
letra grande del sistema el texto crece y el hueco muerto con él, pero el desplazamiento se queda
donde estaba, así que el par vuelve a hundirse contra el borde inferior del tile — exactamente el
síntoma que `UI-SHEET-001` había arreglado, reapareciendo en cuanto el usuario toca el tamaño de
fuente.

**Hecho:** el lift se convierte con la densidad (`sp.toDp()`), así que escala con el usuario.

**Pendiente de device:** si 8.5sp sobrevive a `fontScale` bajo. Es una decisión de medida, no de
código; la nota queda aquí para no cerrarla a ojo.

## 7 · Los roles CIFRA de chart no declaran `lineHeight`

`chartLabel` y `chartValue` son los dos únicos roles sin `lineHeight` (`sizeToken` y `distance`
desaparecieron absorbidos por `badge`; `badge` ya es LECTURA y sí lo declara).

No es cosmético: los dos se dibujan en **canvas** (`HistoryWeeklyChart.drawDayLabel` /
`drawCountLabel`), y ahí la caja de texto ES la posición — `topLeft` se calcula restando
`result.size.height`. Sin `lineHeight` esa altura sale del ascenso/descenso del TTF, así que la
posición de las etiquetas del gráfico depende de la familia. Es el mismo fallo que ya obligó a
poner caja recortada en `statNumber`, `statLabel`, `counter` y `counterUnit`.

**Hecho:** `lineHeight` + `LineHeightStyle(Center, Trim.Both)` en ambos, como el resto del grupo
CIFRA. ⚠️ Mueve unos px las etiquetas del gráfico semanal — **verificar en device**.

## 8 · `CLAUDE.md` dice 23 roles; hay 22 — y uno de los que lista no existe

La lista de la sección de tipografía incluye `rowDistance`, que **no está en `PaparcarType`** (0
usos en todo el repo). De ahí salía el 23. **Corregido a 22 y retirado el fantasma.**

De paso, la sección seguía repartiendo las voces por familia retirada (Outfit / Inter / Barlow) y
prohibiendo *"proponer una condensada de Outfit o Inter"*, fuentes que ya no se compilan. Se
reescribe en el vocabulario que sigue vivo —las VOCES— conservando la lección (⛔ no proponer un
corte condensado: la familia que se envía no lo tiene).

## 9 · *(nuevo)* Los comentarios siguen nombrando familias que ya no se envían

Desde `d0fdc4ae` la app envía **una** familia (Plus Jakarta Sans) en las tres voces. Pero el KDoc de
`PaparcarType` sigue titulando *"Marca · Outfit"*, *"Cifra · Barlow Condensed"*; sus variables
locales se llaman `outfit` / `inter` / `barlow`; y ~30 comentarios de call site justifican una
decisión con *"→ Inter (caption)"* o *"Barlow es para datos que se repiten"*.

Un lector nuevo no puede saber si eso describe el código de hoy o el de anteayer, y es lo que hace
que un rol se elija por la familia que uno recuerda en vez de por la pregunta del sistema.

**Criterio aplicado:** el comentario que afirma algo **en presente** pasa a la voz (MARCA / LECTURA
/ CIFRA); el que **narra historia en pasado** (*"Barlow aquí era el choque visible"*) se conserva
tal cual, porque en su momento fue cierto y explica por qué existe la regla.

## Criterio de éxito

| | Estado |
|---|---|
| `:shared:testDebugUnitTest` verde con la allowlist recortada | ✅ `TypographyGuardrailTest` 5/5, 0 fallos |
| `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` | ✅ |
| 0 referencias de código a `PaparcarBottomActionBar`, `appBarTitle`, `rowDistance` | ✅ (sólo quedan menciones en pasado, que explican por qué se fueron) |
| 0 comentarios **en presente** que nombren Outfit / Inter / Barlow como la familia de un rol | ✅ 39 reescritos; los 10 que quedan narran historia |
| El contador del sheet y las etiquetas del gráfico, vistos en device | ⏳ pendiente |

## Consumidores auditados

| Barrido | Resultado |
|---|---|
| `grep -r PaparcarBottomActionBar` | componente + 2 previews + 1 cabecera + 1 allowlist → todos cerrados |
| `grep -r appBarTitle` | extensión + 2 previews → cerrados |
| `grep -r rowDistance` | 0 en código; sólo `CLAUDE.md` → corregido |
| `grep -r "Outfit\|Barlow\|\bInter\b"` en `.kt` | 20 ficheros → reescritos los que hablan en presente |
| `Button(` crudo de M3 | 4 call sites → 1 exento, 3 a ticket nuevo |
| Eyebrows pintados a mano (`.uppercase()` + rol que no es `eyebrow`) | 1 (`DetectionTierStatusCard`) → cerrado |

## Follow-ups deliberados

- `docs/backlog/ui-button-one-canonical-cta-001.md` — los 3 `Button(` de M3 que no pasan por
  `PapPrimaryButton`, y la variante destructiva que falta.
- §6, medida en device de `counterUnit` con `fontScale` bajo.
