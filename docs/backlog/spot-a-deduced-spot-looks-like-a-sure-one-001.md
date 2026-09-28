# SPOT-A-DEDUCED-SPOT-LOOKS-LIKE-A-SURE-ONE-001 · una plaza deducida se ve igual que una medida

**Estado:** ✅ Done (código, tests y docs) · ⏳ **hay que verlo** · rama `feature/SPOT-A-DEDUCED-SPOT-LOOKS-LIKE-A-SURE-ONE-001-puck-glyph`
· worktree `../Paparcar-row-badge`

## Problema

Ni la **fila** ni el **marcador del mapa** distinguen una plaza cuya salida se DEDUJO de una que se
midió. Las dos se ven exactamente igual: mismo puck, mismo anillo, mismo megáfono. Sólo lo dice el
peek, al abrirlo, con `home_spot_unconfirmed_note`.

Y el KDoc de `SpotStatus.PROVISIONAL` promete lo contrario: *«Shown as unconfirmed and ranked below
confirmed spots»*. Lo segundo es verdad; lo primero no, desde `UI-SPOT-ROW-SAYS-WHAT-DECIDES-001`.

⚠️ **Esto NO es un olvido, y la petición original («pon el badge en la fila») iba contra una decisión
escrita.** Aquel ticket retiró el badge de texto a propósito:

> *«`SIN CONFIRMAR` sale. El modal lo cuenta entero y con los dos botones que lo resuelven. En la fila
> era una palabra larga sin salida.»*

Y su objeción está **medida**: la etiqueta ocupa 11–15 caracteres según el idioma (`Niepotwierdzone`),
frente a los 7–10 del badge de *Saliendo*, que es el único texto que la fila lleva hoy en su hueco
trailing — hueco que además está ocupado por la píldora de edad, que es el dato que decide si ir.

Lo que quedó de aquella retirada es un residuo: `home_spot_unconfirmed_badge` sigue en los 9 idiomas y
**importado sin usar** en `HomeSpotRows.kt`.

## Doctrina

La regla del ticket que lo retiró sigue en pie y esta solución la respeta: **la fila dice lo que hace
falta para DECIDIR; lo que describe la plaza una vez elegida vive en su modal.** Que la salida se dedujo
SÍ decide —una plaza deducida puede no estar—, así que entra; pero entra **como glifo**, que es
literalmente lo que aquel ticket dejó escrito al salvar la gente-en-camino: *«como glifo cuesta un
tercio de lo que costaba escrita»*.

Decisión del user (28-09), sobre cuatro opciones medidas: **glifo en el puck.**

## Diseño

El puck ya tiene un **par de glifos** y el KDoc de `CommunitySpotOverlay` dice cómo se usa:

> *«The whole glyph pair changes, not just the colour: colour alone would make this read as a fourth
> shade of "free", which is the one reading that costs a stranger a wasted drive.»*

- **Glifo principal** = qué hay ahí: el hueco (`PaparcarIcons.CommunitySpot`) o el coche, si sigue dentro.
- **Disco del badge** = qué clase de noticia es: el megáfono que ANUNCIA, o la salida (`Logout`) si el
  coche se está yendo.

Una plaza deducida **sí es un hueco** —a diferencia de una RELEASING—, así que el glifo principal no se
toca. Lo que cambia es la segunda pregunta: no la anunciamos, la **suponemos**. El disco pasa a
`Icons.AutoMirrored.Rounded.HelpOutline`.

⛔ **Descartadas dos alternativas, y por qué:**
- **Rayar el anillo de frescura.** El discontinuo ya significa otra cosa en esta familia: es la órbita
  exterior del pin de *gente en camino* (`drawEnRoutePin`). Dos significados con un recurso.
- **Teñirlo** (ámbar). La rampa de frescura es **exclusiva** de la caducidad (`CLAUDE.md`
  ⛔ UI-COLOR-DOCTRINE-001), y un ámbar aquí se leería como un peldaño de la rampa — exactamente el
  «cuarto tono de libre» que el KDoc de arriba prohíbe.

✅ **Y lo hereda el mapa**, que es lo que hace que ésta sea la solución y no un parche de fila: el
marcador y la fila comparten el puck, así que la plaza deducida deja de mentir en las dos superficies a
la vez.

✅ **La palabra sobrevive donde una palabra sirve**: `home_spot_unconfirmed_badge` pasa a ser el
`contentDescription` del glifo. El lector de pantalla la dice; la fila no la escribe. El string deja de
ser huérfano sin borrar la intención de nadie.

## Criterio de éxito

Una plaza `PROVISIONAL` se distingue de una `CONFIRMED` de un vistazo, en la fila **y** en el mapa, sin
una palabra y sin tocar el hueco de la píldora de edad.

⏳ **Y esto hay que verlo**: el puck de la fila mide `BADGE_DP`, así que el glifo del disco a ese tamaño
es una comprobación de ojo que no se puede hacer desde aquí.

## Consumidores auditados — todo lo que dibuja un puck de plaza

| Superficie | Estado |
|---|---|
| `CommunitySpotOverlay` | ✅ el disco pasa a tres noticias; **lo comparten las dos superficies**, así que un solo cambio llega a fila y mapa |
| `SpotPuckIcon` (fila, peek, cabecera de hoja) | ✅ parámetro nuevo, pasado desde los tres call sites |
| `FreeSpotMarker` (mapa) | ✅ ídem |
| `PapSheetLead.CommunitySpot` | ✅ el lead lleva el campo: la cabecera plegada de la hoja dibuja el mismo puck |
| `freeSpotContentId` + el registro de bitmaps | ✅ segmento `u_` en la clave **y** en el registro. ⛔ El registro itera el producto COMPLETO, combinaciones imposibles incluidas (una RELEASING no puede ser deducida, ni un reporte manual): `put` registra una **fábrica**, no un bitmap —se rasteriza al pedirla—, así que registrar de más cuesta una entrada de mapa y registrar de MENOS **pinta nada, en silencio** [UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001] |
| `MapMarkerIdsTest` | ✅ el corpus cubre la duda, + dos tests propios: que el id CAMBIA, que las cuatro combinaciones de las dos dudas son cuatro, y que la rama de en-route la ignora a propósito |
| `ReportCenterPin` | ✅ exento: es el pin de reportar a mano, y un reporte manual es `CONFIRMED` por definición |
| `home_spot_unconfirmed_badge` | ✅ deja de ser huérfano **sin volver a la fila**: es el `contentDescription` del glifo. El import muerto de `HomeSpotRows.kt` se va |
| `SpotStatus.PROVISIONAL` KDoc | ✅ *«Shown as unconfirmed»* vuelve a ser verdad, y ahora en las dos superficies |

## Verificación

- `testAll --rerun-tasks`: ver el resumen del commit. `assembleMockDebug` + `compileProdDebugKotlin` OK.
- Fila nueva en la galería de marcadores: FRESH, RECENT, seleccionado **y una con las dos dudas a la
  vez** — que es el par que hay que mirar junto, porque son dudas distintas y tienen que poder leerse
  sin confundirse.

⏳ **Esto no se puede cerrar desde aquí, y es la parte que importa:** el puck de la FILA mide
`BADGE_DP`, y el glifo del disco a ese tamaño es una comprobación de ojo. Los dos glifos que ya viven
en ese disco (megáfono y salida) comparten caja de 18 unidades; la tinta de un `HelpOutline` —un
círculo con un interrogante dentro— la llena distinto, así que puede pedir su propio número. La
disciplina de este fichero es explícita: *«la caja se re-mide cada vez que cambia el glifo y nunca se
hereda»*. **No la he re-medido porque no puedo renderizar.**

## 🔵 Follow-up que NO se abre aquí

**Ningún test comprueba que un id emitido tenga dibujo registrado.** `MapMarkerIdsTest` verifica que
los ids son distintos y que rutean a su destino al tocarlos, pero el mapa id→dibujo vive dentro de un
composable de `PaparcarMapView.kt` y es inalcanzable desde un test unitario. Ese hueco es exactamente
el que hizo posible `UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001`, y hoy lo único que lo cubre es que el
bucle de registro itere el producto completo — una disciplina, no un testigo. Cerrarlo pide extraer ese
mapa a una función pura, y eso es su propio ticket en un fichero de 2.700 líneas.
