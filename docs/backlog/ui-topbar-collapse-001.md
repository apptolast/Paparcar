# UI-TOPBAR-COLLAPSE-001 · Una sola cabecera: se retira al scrollear y deja pasar el contenido

**Estado:** implementado en `feature/UI-TOPBAR-COLLAPSE-001-collapsing-headers` (worktree
`Paparcar-topbar`) · 1113 tests verdes · APK instalado y validado en Oppo · ⏳ revisión del usuario

## Síntoma que lo dispara

"¿Por qué la pantalla de Ajustes no tiene la status bar transparente como las otras?"

Medido en device (píxeles de la franja superior, x=1200):

| Pantalla | Franja 0–94 px | Contenido |
|---|---|---|
| Ajustes (scrolleada) | `131A24` opaco | empieza en y≈95, **recortado** contra la franja |
| Vehículos | `131A24` opaco | nunca llega arriba (cabecera fija) |
| Home | mapa | pasa por debajo del reloj |

## Causa

Ajustes era la **única** pantalla con `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`. Dos
divergencias:

1. **Comportamiento**: Ajustes retiraba el título al scrollear; Vehículos / formulario de vehículo /
   config Bluetooth tenían cabecera fija. Dos gramáticas de cabecera en la misma app.
2. **Status bar**: Material 3 **no deja colapsar el inset**. El `TopAppBar` mete su
   `windowInsetsPadding` FUERA del bloque que encoge, así que aun colapsado del todo queda una
   franja opaca del alto de la status bar contra la que el contenido se recorta. Eso es lo que se
   leía como "status bar no transparente".

## Decisión (elegida por el usuario)

- El contenido **pasa por debajo** de la status bar (transparencia real, como el mapa de Home),
  asumiendo que el texto de las filas pase por detrás del reloj.
- El colapso de cabecera se **unifica en todas** las pantallas con `TopAppBar`.

## Solución — `ui/components/PapCollapsingTopBarScaffold.kt`

Un único scaffold para las cuatro pantallas. Reglas:

- El cuerpo se dibuja **a sangre desde y=0**; el `contentPadding` que recibe el `content` reserva la
  altura de la cabecera EN REPOSO. El llamante lo aplica como `contentPadding` del scrollable (o
  DENTRO del `verticalScroll`), **nunca como padding externo**: por eso el contenido puede pasar por
  debajo en vez de recortarse.
- La cabecera se retira **entera**, con todo lo que lleve (`subHeader`): al final no queda banda y
  el contenido llega a la status bar. **Nada de chrome que sobreviva al colapso** — si algo tiene
  que estar siempre a mano, no es cabecera. Mientras se retira es **opaca**, así el contenido pasa
  por debajo del título en vez de encima.
- `expandKey`: al cambiar de valor la cabecera se despliega. Es para contenidos que se sustituyen
  por otro que empieza arriba (cambiar de página del pager de vehículos): dejarla retirada abriría
  un hueco donde estaba el título.
- La cabecera se mueve con lo que la lista **consume** (`onPostScroll`), no con lo disponible:
  - una lista que no scrollea consume 0 → la cabecera no se va;
  - cuando scrollea, cabecera y contenido viajan 1:1 (no hay el clásico "el contenido corre al doble
    que el dedo" de aplicar `innerPadding` variable).
  - al soltar, `onPostFling` asienta al borde más cercano: nunca se queda a medias.
- La cabecera **encoge y se recorta** (`layout` + `clipToBounds`), no se desplaza: así el título
  desaparece BAJO lo que quede por encima en vez de dibujarse sobre el reloj.
- La franja de la status bar se mide con `windowInsetsTopHeight` (respeta el inset **consumido** por
  el banner de conectividad, [CONN-BANNER-001]); insets horizontales de recorte de cámara aplicados
  aparte para que el cuerpo no pinte bajo la cámara en horizontal.

## Alcance

| Pantalla | Cambio |
|---|---|
| `SettingsScreen` | `Scaffold`+`exitUntilCollapsed` → scaffold común; `LazyColumn` con padding de contenido |
| `VehiclesScreen` | pestañas → `subHeader` (se retiran con el título; pager izado a `VehiclesContent`) + `expandKey = pagerState.settledPage`; `VehiclesPager` recibe `pagerState` + `contentPadding` |
| `VehiclePageContent` / `HistoryContent` | `contentPadding` deja de ser padding de layout y entra como `contentPadding` del `LazyColumn`; el empty state descuenta la cabecera de `fillParentMaxSize` |
| `BluetoothConfigScreen` | scaffold común; título pasa de `cardTitle`+bold al rol `screenTitle` (unificación) |
| `VehicleRegistrationScreen` | scaffold común; padding movido DENTRO del `verticalScroll` |

## Por qué las pestañas de vehículo NO quedan ancladas (decisión 14-08)

Primero se probaron ancladas. No se sostiene: cambiar de coche ya se hace con **swipe horizontal en
cualquier punto de la página**, así que las pestañas son sobre todo una etiqueta de "qué coche
miro" —que ya da la hero card—. Con **un solo vehículo** —el caso normal— la fila anclada no conmuta
nada y se come ~48dp permanentes sobre un historial largo, y obliga a que esa banda sea opaca: la
única pantalla donde el contenido no llega a la status bar, justo la incoherencia que este ticket
venía a quitar. Se retiran con el título.

## Verificado en device (Oppo CPH2371 `LNRCMZ8H6HBITWNJ`, vertical y horizontal)

- Ajustes: en reposo idéntico; al scrollear el contenido pasa bajo el reloj, sin franja opaca.
- Vehículos: cabecera (título + pestañas) se retira entera y vuelve al subir; con 3 coches, cambiar
  de página con swipe horizontal la re-despliega y la página nueva arranca arriba, sin hueco.
- Formulario: cabecera se retira y **vuelve al scrollear hacia arriba** (la flecha de volver no se
  queda inaccesible).

## Siguiente paso (acordado con el usuario)

- **Botón "volver arriba"** cuando se ha scrolleado mucho, en las pantallas con cabecera colapsable.
  Ticket aparte, sobre este scaffold.

## Notas

- Bluetooth con BT apagado no tiene lista scrollable → la cabecera no se mueve. Es el diseño (se
  conduce con lo CONSUMIDO). La lista de dispositivos emparejados queda sin probar en device.
- El solape del contenido con el reloj se nota más en el Oppo (barra con más iconos) que en el
  Redmi. Si molesta, el dial es un degradado bajo la status bar.
