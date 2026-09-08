# UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001 · una zona privada solo se lee privada en el mapa; su chip y su pin de colocación siguen pintándola de marca

**Estado:** ✅ Done — en master

## Problema

Captura del user (08-09-2026, Home, tema oscuro). La zona **R8Test** es privada, y en la MISMA
pantalla, a unos 250 px de distancia, se lee de dos maneras incompatibles:

- **chip de la cabecera** → pin **verde** (`primary`), sin candado
- **etiqueta del mapa** → pin **gris** (`onSurfaceVariant`) + candado

El user lo describió como *«el marker de zona privada es totalmente distinta a la de zona normal»*.
No lo es: `ZoneMarker` y el anillo del radio honran la doctrina correctamente. Lo que rompe la
lectura es que **su chip no se entera**, así que la doctrina parece un capricho del marcador en vez
de la regla de la app.

Segunda mordida encontrada en el barrido, no visible en la captura: mientras **creas o editas** una
zona privada, el anillo nativo del radio ya se pinta neutral (`previewZoneIsPrivate` llega a
`zoneCircle`, `PaparcarMapView.kt:1492`) pero el pin de colocación encima sigue en `primary`
(`ZoneCenterPin`, `PaparcarMapMarkers.kt:1265`, tinte fijo). Anillo gris + pin verde, en la misma
pieza y en el mismo frame.

## Doctrina violada

`[UI-COLOR-DOCTRINE-001 F6]` — al retirarse `tertiary` no queda un tercer color para "privada", así
que **privada = neutral + candado**: la categoría la dice el glifo, no un color nuevo. La regla está
escrita en el KDoc de `ZoneMarker` y aplicada en dos sitios; falta en otros dos.

Y `⛔ sistemas, no parches`: el invariante «cómo se pinta una zona privada» no vive en ningún sitio
— está **copiado** en cada superficie (`ZoneMarker` lo resuelve a `onSurfaceVariant`,
`PaparcarMapView` lo resuelve a `outline`, `ZoneChip` y `ZoneCenterPin` no lo resuelven). Cuatro
copias es exactamente lo que produjo dos que se olvidaron.

Mecanismo que lo permitió: `ZoneMarker(..., isPrivate: Boolean = false)`. Un **default** convierte
"olvidarse de la privacidad" en código que compila y se ve bien.

## Señales / datos disponibles

`Zone.isPrivate` ya existe en el dominio, llega a `HeaderZoneChips` dentro del propio `Zone`
(`HomeHeaderSection.kt:168`) y al modo de colocación como `state.addingZoneIsPrivate`
(`HomeViewModel.kt:693/808`, ya rendido a la vista en `HomeScreen.kt:587`). **No hace falta plomería
de estado nueva**: el dato está en la mano de los dos consumidores que lo ignoran.

## Diseño

**Un resolver, como el de vehículos.** Nace `ui/theme/ZoneIdentity.kt`, hermano de
`ui/theme/VehicleIdentity.kt` (mismo patrón, misma razón: cuatro resolvers rivales pintando lo
mismo):

- `zoneIdentityColor(isPrivate)` → `primary` / `onSurfaceVariant` — glifos y bordes de píldora
- `zoneRingColor(isPrivate)` → `primary` / `outline` — el anillo nativo del radio, que es una
  mancha sobre teselas y por eso pide el neutral más flojo (esa diferencia es deliberada, se
  documenta en el resolver en vez de quedar como divergencia accidental)

Consumidores: `ZoneMarker`, `ZoneChip`, `ZoneCenterPin` y `zoneCircle` leen de ahí. El candado sigue
siendo un `Icons.Rounded.Lock` en cada superficie que tenga sitio para él (marker y chip), con el
contrato escrito en el KDoc del resolver.

**Y el default se retira.** `isPrivate` pasa a ser parámetro **obligatorio** en `ZoneMarker`,
`ZoneChip` y `ZoneCenterPin`: olvidarlo debe ser un error de compilación, no un chip verde.

El pin de colocación no lleva candado — es un glifo suelto de 1 color sobre el mapa, sin píldora
donde colgarlo; su neutral basta y coincide con el anillo que ya se pinta debajo.

## Criterio de éxito

1. Una zona privada se lee **neutral + candado** en su chip, y **neutral** en el pin mientras se
   coloca — misma identidad que ya tiene en el mapa.
2. `@Preview` de las dos variantes del chip y del pin de colocación (paridad con el
   `ZoneMarker` privado que ya está previsualizado en `PaparcarMapMarkersPreviews.kt:157`).
3. `:shared:testDebugUnitTest` verde (incluye `ColorGuardrailTest` / `TypographyGuardrailTest`).
4. En mano: crear una zona privada y ver chip, anillo, pin y etiqueta contando lo mismo.

## Consumidores auditados

`grep zoneIconFor` + `grep isPrivate` sobre `shared/src` — todo lo que dibuja la identidad de una zona:

| Consumidor | Fichero | Veredicto |
|---|---|---|
| Etiqueta central del mapa | `ZoneMarker` (`PaparcarMapMarkers.kt:1020`) | ✅ ya honra — pasa a leer del resolver |
| Anillo nativo del radio (guardadas + preview) | `zoneCircle` (`PaparcarMapView.kt:1439`) | ✅ ya honra — pasa a leer del resolver |
| **Chip de la cabecera** | `ZoneChip` (`HomeZoneChips.kt:44`) | 🔴 **muerde** — `tint = cs.primary` fijo, sin candado |
| **Pin mientras colocas/editas la zona** | `ZoneCenterPin` (`PaparcarMapMarkers.kt:1257`) | 🔴 **muerde** — `tint = primary` fijo, con el anillo ya neutral debajo |
| Icono lead del modal de zona | `AddingZonePeek.kt:140` | ⚪ exento — la privacidad es un `Switch` visible en esa misma superficie (`ZonePrivacyToggle`); el glifo no repite lo que el interruptor ya dice |
| Icono del diálogo de nombrar | `AddingZonePeek.kt:258,283` | ⚪ exento — misma razón; además el nombrado ocurre antes de decidir nada |
| Fila del picker de iconos | `AddingZonePeek.kt:325` | ⚪ exento — es la paleta de opciones, no la identidad de una zona |
| Fila de zona en el diálogo de avisos | `ZoneAlertsDialog.kt:64` | ⚪ exento — **todas** las zonas van en `onSurfaceVariant` ahí, así que no hay dos identidades para la misma zona; el sujeto de esa lista es el aviso, no la privacidad |

## Ejecutado

- **`ui/theme/ZoneIdentity.kt`** (nuevo) — `zoneIdentityColor(isPrivate)` + `zoneRingColor(isPrivate)`.
  Los cuatro consumidores leen de ahí; la divergencia `onSurfaceVariant` (glifo sobre píldora opaca)
  vs `outline` (mancha translúcida sobre teselas) queda documentada como intencional, no heredada.
- **`ZoneChip`** — tinte por resolver + candado antes del lápiz (el candado describe la zona, el
  lápiz actúa sobre ella). Content description: **se reutiliza `home_zone_private_label`**, que ya
  vive en los 9 locales — el mismo concepto no puede crecer una segunda redacción, así que **no se
  añade ningún string**.
- **`ZoneCenterPin`** — lee `previewZoneIsPrivate`, el MISMO valor que pinta el anillo, en vez de
  llevar su propia copia en `CenterPinKind.Zone`. Pin y anillo ya solo pueden coincidir.
- **Default retirado** de `isPrivate` en `ZoneMarker` / `ZoneChip` / `ZoneCenterPin`.

### Dos cosas que solo se vieron al ejecutar

1. **El default retirado cazó cuatro call sites en el Dev Catalog** (`StateGalleryScreen.kt`) en el
   primer `compileMockDebugKotlin`. Eso es el guardarraíl haciendo exactamente su trabajo: el
   catálogo llevaba las mismas llamadas sin privacidad que la app.
2. **`ZoneChip` era `internal`**, y la galería de estados vive en `:app` — o sea que el chip era
   *físicamente inalcanzable* para el catálogo. No es una coincidencia que sea justo el componente
   que se quedó atrás: no había ningún sitio donde alguien pudiera ver sus dos variantes juntas.
   Pasa a público, como `HomePeekHandle` o `SpotFitRow`, y entra en la galería **al lado de la
   etiqueta del mapa**, que es donde la contradicción salta.

### Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` → BUILD SUCCESSFUL
- `:shared:testDebugUnitTest` → BUILD SUCCESSFUL (incluye `ColorGuardrailTest`,
  `TypographyGuardrailTest`, `LocaleParityGuardrailTest`)
- ⏳ **falta verlo en mano**: crear una zona privada y comprobar que chip, anillo, pin y etiqueta
  cuentan lo mismo.

## Fuera de alcance (apuntado, no hecho)

- `ZoneAlertsDialog` no distingue privadas. Una zona privada es un garaje: probablemente no debería
  ni ofrecer "avísame de plaza libre aquí". Es una decisión de producto, no de color — ticket aparte
  si el user lo quiere.
- La campana de avisos de zona: el user preguntó por moverla junto al buscador. Se queda donde está
  (`[UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]` ya lo midió: un tercer círculo de 56 dp parte el
  placeholder en dos líneas, y la izquierda no cambia la aritmética).
