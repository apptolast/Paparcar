# UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001 · una caja de altura fija recortaba en silencio la sección de coche compartido

**Estado:** ✅ Done · rama `bugfix/UI-AN-EMPTY-HISTORY-MUST-NOT-CROP-ITS-HEADER-001-header-crop` · worktree `../Paparcar-empty-header`

## Problema

En la página de un coche **compartido** cuyo historial está **vacío**, la pestaña Vehículos se corta
a media pantalla: la card «Código para unirse» aparece partida por la mitad y debajo no queda nada
—ni el interruptor de rutas, ni el aviso de emparejamiento, ni los chips de alcance, ni el propio
estado vacío del historial—. El usuario lo describió como «cuando abro compartir mi coche, la
pantalla no se abre bien, se entrecorta, no se renderiza completa» (16-09-2026, captura en device,
Ford Focus recién compartido, sin sesiones en el historial).

Dos hechos de la captura fijan el diagnóstico y descartan que sea jank o un problema del
`ModalBottomSheet` de la hoja de miembros:

1. El corte cae **a media card**, sin transición ni animación → es recorte de layout, no un frame
   perdido.
2. **`EmptyHistoryState` no se dibuja en ninguna parte**, y debería: el historial está vacío (no hay
   sección «Actividad», ni gráfico, ni barra de periodos). Su ausencia es la firma de un
   `weight(1f)` que recibió cero.

## Doctrina violada

Ninguna regla de las escritas en `CLAUDE.md` — es un fallo de composición. Pero sí viola el
contrato que el propio código se había puesto: `EmptyHistoryState` documenta en
`HistoryComponents.kt:56-60` que «el bloque se centra en el espacio que le da el caller (en
historial, `HistoryContent` le pasa `fillParentMaxSize` → queda encuadrado en el hueco entre la
hero card y la bottom nav)». Ese contrato daba por hecho que **siempre hay hueco**, y dejó de ser
verdad sin que nadie lo tocara.

Sí toca de refilón el ⛔ de mantener el Dev Catalog en sync: este estado concreto —coche compartido
**con** historial vacío— no existe en `StateGalleryScreen.kt`, que tiene seis variantes de
`SharedCarSection` y dos de `JoinSharedCarDialogContent`, pero ninguna dentro de `HistoryContent`.
Por eso el recorte no se vio nunca en el banco de pruebas.

## Señales / datos disponibles

`HistoryContent.kt:274-305`, rama `sessions.isEmpty()`:

```kotlin
item(key = "empty") {
    Column(
        modifier = Modifier
            .fillParentMaxSize()                                   // altura FIJA = viewport
            .padding(bottom = listPadding.calculateTopPadding()),
    ) {
        header?.invoke()                                           // hero card + SharedCarSection
        Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { EmptyHistoryState() }
    }
}
```

`fillParentMaxSize()` fija la altura al viewport (`min == max`), y encima se le descuenta la altura
de la cabecera con el `padding(bottom = …)`. El `header` va **dentro** de esa caja. Mientras el
header fue solo la hero card (~330 dp) el reparto funcionaba y `weight(1f)` centraba el bloque
vacío en el hueco. Al añadir `SharedCarSection` a ese mismo header
(`VehiclePageContent.kt:141`, VEH-SHARED-001) el header dejó de caber:

- lo que sobra se **recorta**, porque la caja no puede crecer;
- el `weight(1f)` reparte el sobrante, y el sobrante es cero → `EmptyHistoryState` mide 0 px.

La rama **con** historial (`HistoryContent.kt:307`) emite el header en su propio
`item(key = "header")`, sin altura impuesta, y por eso nunca se corta. Hacen falta las dos
condiciones a la vez —coche compartido **y** historial vacío—, que es exactamente el estado de una
cuenta que acaba de abrir el grupo.

## Diseño

El estado vacío deja de imponerle altura al header. Un contenedor propio,
`HeaderThenFillRest`, hace en una sola pasada de medida lo que la caja fija hacía a ciegas:

- mide el header **suelto** (altura natural, nunca recortado);
- mide el bloque de relleno con `minHeight = viewport − headerH` (acotado a ≥ 0) y
  `maxHeight = Infinity`.

Con eso los dos casos salen del mismo mecanismo, sin número mágico y sin estado intermedio:

| Caso | Resultado |
|---|---|
| El header cabe | sobra sitio → el bloque se estira hasta el mínimo y se centra en él. **Encuadre idéntico al de hoy.** |
| El header no cabe | `minHeight` cae a 0 → el bloque toma su altura natural, la lista crece y **scrollea**. Nada se recorta. |

El viewport se lee del `Box` que ya envuelve al `LazyColumn` (`HistoryContent.kt:263`), promovido a
`BoxWithConstraints` — no cuesta un layout nuevo.

⛔ Por qué no `heightIn(min = …)` a secas sobre la `Column`, que era lo primero que pedía el cuerpo:
dentro de un `item` de `LazyColumn` las constraints verticales llegan con `maxHeight = Infinity`, y
un `weight()` sin máximo acotado no tiene espacio que repartir — el hijo se queda en su altura
natural y el sobrante se va al final de la columna. Arreglaría el recorte y **rompería el
centrado**, que es la mitad que hoy funciona.

## Criterio de éxito

- Un coche compartido con historial vacío muestra la sección entera (código, rutas, aviso de
  emparejamiento y chips de alcance) y el estado vacío debajo, con scroll si no cabe.
- Un coche sin compartir con historial vacío conserva el encuadre actual: bloque vacío centrado en
  el hueco bajo la hero card, sin scroll.
- El caso entra en `StateGalleryScreen.kt` para que sea visible en el flavor mock.

## Verificación

⛔ **El testigo aquí es visual, no un test.** El proyecto no tiene infraestructura de test de
Compose UI —ni `compose.ui.test` en el catálogo ni un solo `createComposeRule` en el repo— y
montarla para este ticket sería otro encargo. Extraer la resta `restHeight − headerHeight` a una
función pura y testearla sería un test de coartada: probaría una resta, no que el layout deje de
recortar. La variante nueva de la galería ES el testigo, y por eso entra en esta misma tarea.

`./gradlew testAll` verde · `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` OK.

Medido en el **Oppo** (`LNRCMZ8H6HBITWNJ`, `app-mock-debug.apk` sha256 `846b217e15ffb550…c027ac1`,
hash device↔local idéntico), galería → *Vehicles* → «Compartido + historial vacío»:

- **Página compartida (Mi Seat).** Se dibuja entera: «Código para unirse» + `K7QP3M` + «Código
  nuevo» + el cuerpo de 2 líneas, el interruptor de rutas con su subtítulo de 3 líneas, y el aviso
  de emparejamiento. Scrolleando aparecen los chips «Todo el grupo» / «Solo yo» y **«Aún no has
  aparcado»** — el `EmptyHistoryState` que antes medía 0 px. Ninguna card partida.
- **No regresión (Toyota Corolla, sin compartir, mismo historial vacío).** Hero card + fila
  «Compartir este coche» + estado vacío **en una sola pantalla, sin scroll**: el encuadre centrado
  de siempre.

## iOS

Nada que abrir. El cambio vive entero en `commonMain` (Compose Multiplatform: `Layout`,
`BoxWithConstraints`, `Constraints`), así que iOS hereda el arreglo sin código propio — no hay
`expect/actual` de por medio ni superficie de plataforma que tocar.

## Consumidores auditados

`grep -rn "fillParentMax" shared/src app/src` → **un solo sitio de uso real**:

| Sitio | Veredicto |
|---|---|
| `HistoryContent.kt:284` | **la vía donde mordió** — sustituido por `HeaderThenFillRest`. |
| `HistoryContent.kt:279` | comentario que explica el `fillParentMaxSize`; se reescribe con el mecanismo nuevo. |
| `HistoryComponents.kt:58` | KDoc de `EmptyHistoryState` que documenta el contrato viejo («le pasa `fillParentMaxSize`»); se actualiza, o quedaría describiendo código que ya no existe. |
| `HistoryContent.kt:307` (rama con historial) | **exento** — emite el header en su propio item, sin altura impuesta. Es el comportamiento que esta tarea generaliza. |
