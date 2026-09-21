# HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001 · volver a Home vuelve a donde mirabas

**Estado:** ✅ Done · rama `bugfix/HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001` · issue #95

## Problema
Reporte de campo (parte 3 de #87, sacada a parte a propósito): al salir de Home a otra pestaña y
volver, **se recuerda el carril** (plazas / parkings / gasolineras) y **la cámara vuelve a tu
ubicación**. La lista habla de un sitio y el mapa de otro.

## Doctrina violada
La misma discrepancia que [HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001] tapó para el otro
sentido. Allí la lista dejó de mentir sobre dónde miras; aquí la cámara deja de olvidar dónde
mirabas. **Recordar medio par no es recordar.**

## Causa
Asimetría de ciclo de vida, no de lógica:

| | Dónde vive | ¿Sobrevive a salir de Home? |
|---|---|---|
| `nearbyLens` (el carril) | `HomeState`, en el ViewModel | **sí** |
| la cámara | `HomeUiController`, `remember`-eado en el composable | **no** |
| `userMovedCameraManually` | `HomeUiController` | **no** |

El ViewModel ya guardaba `lastCameraLat/lastCameraLon` —los usa para decidir recargas— así que el
sitio nunca se perdió del todo. Lo que faltaba era **restaurarlo**, y sobre todo restaurar con él el
guard de «no me reencuadres»: sin eso, el primer fix devuelve la cámara a tu ubicación y el arreglo
habría durado exactamente un fix.

## Diseño
- ⛔ **Se recuerda el sitio que ELEGISTE, no el último que la cámara tocó.** Una cámara que fue sola
  a un sitio no es un recuerdo; restaurarla congelaría para siempre el primer encuadre automático de
  la app. Por eso `chosenView()` devuelve null mientras no haya habido un gesto.
- `HomeIntent.UserMovedCameraByHand` — un gesto no trae coordenadas, sólo dice **de quién es la
  cámara** desde ahora. Las coordenadas ya las escribe el settle que existía.
- La bandera vive en el ViewModel y **no en `HomeState`**: cambia con un gesto, no la pinta nadie, y
  meterla en el estado recompondría todo Home por un dato que sólo se lee al volver a entrar.
- `HomeUiController.restoreChosenView(lat, lon)` posa la cámara **y re-arma
  `userMovedCameraManually`**.
  ⚠️ No es `goToPlace`: aquél es una petición nueva —suelta el follow y sube el zoom si hace falta—
  y esto es una restauración. **Restaurar no es ir**, así que no trae zoom propio.

## Criterio de éxito
4 testigos, **falsificados por separado** (quitando cada mitad cae el suyo):
- el destino restaurado es el elegido, y el primer fix **no** se lo lleva;
- una restauración no cambia el zoom;
- `chosenView()` es null si el usuario nunca posó la cámara — *moverse no es elegir*;
- …y devuelve el sitio en cuanto hubo gesto.

## Consumidores auditados
`grep` de `userMovedCameraManually` — 5 sitios, los cinco guards de reencuadre automático
(`centerInitialFocus`, `centerOnParkingWithoutLocation`, `centerOnZoneWithoutLocation`,
`refocusOnParkingArrival`, y el propio `onUserMapGesture`). Todos leen la MISMA bandera, así que
re-armarla los cubre a los cinco de una vez — no hay un sexto camino que re-encuadre por su cuenta.
`frameTheAsk` es la excepción **ya documentada y deliberada**: una pregunta nueva es un evento nuevo
y no honra el guard. Se deja: volver a Home no crea preguntas.
