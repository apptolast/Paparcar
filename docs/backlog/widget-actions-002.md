# WIDGET-ACTIONS-002 · El widget gana manos, marca y una puerta desde dentro

**Estado:** ✅ Done — mergeado a master (2026-09-08). Visto en mano en el Pixel 8 (emulador) con
tres iteraciones de diseño del user: botones de texto → iconos discretos → pegados al borde
derecho (`fillMaxWidth` en la Row: en Glance una Row envuelve por defecto y los pesos no empujan).

Mini-fase pedida por el user el 08-09 tras ver A2 en el launcher, insertada entre F2 y F3.

## Problema

El widget enseña dónde está el coche pero no deja HACER nada (ni llegar a él ni soltarlo), no dice
que es de Paparcar, no aparece en la comparativa del paywall (un valor free invisible en el pitch),
y la única forma de ponerlo es bucear en el selector de widgets del launcher.

## Doctrina violada

Ninguna; tres que este ticket aplica a propósito:
- **"Me voy" NUNCA libera a un toque desde el launcher**: liberar publica una plaza a la comunidad
  y cierra la sesión — irreversible y hacia fuera. El botón abre la app CON el diálogo de liberar
  planteado (avisar / solo liberar / cancelar), un toque menos que hoy pero la decisión se toma con
  sus opciones delante.
- **El widget es FREE** (decisión user 08-09): es el presente, y el presente nunca se oculta —
  misma doctrina que la sesión activa en el gate del historial. La comparativa lo DICE: fila en
  Free, y Plus abre con "Todo lo de Free".
- **El tamaño NO se gatea**: redimensionar es del launcher (resizeMode ya lo permite a todos);
  los widgets Plus futuros serán de CONTENIDO (stats en F4, coche compartido en F8), no de tamaño.
- Regla editorial: colocar el widget es CONFIGURAR → la entrada vive en **Ajustes**, no en Home.

## Diseño

- **Acciones — v2 tras verlo en mano (08-09)**: los dos botones de texto resultaron invasivos y
  exigían estirar el widget. En su lugar, **dos iconos discretos en el borde derecho**, siempre
  visibles, sin fondo (`CircleIconButton` sin relleno, `onSurfaceVariant`): el MISMO glifo de
  direcciones que usa el peek de la app (`ic_widget_directions`, intent `geo:` directo a mapas) y
  **tres puntitos** (`ic_widget_more`) que abren la app **con la tarjeta del vehículo
  seleccionada** — todos los verbos (me voy, editar, cómo llegar) viven ALLÍ con sus
  confirmaciones. Sin `SizeMode` responsive: la tarjeta de 1 fila lo lleva todo.
- **El deep link del "más"**, por el patrón del nudge: `MainActivity` empuja el sessionId a un
  `ShowParkingAskEventBus` (Channel CONFLATED — sobrevive al cold start hasta que Home suscribe);
  `HomeViewModel` (34.º parámetro) valida que esa sesión SIGUE activa, la SELECCIONA
  (`HomeIntent.SelectItem(HomeSelection.Parking)`) y centra la cámara (`MoveCameraTo`). Sesión ya
  liberada → la petición muere en silencio (espera acotada de 5 s a que las sesiones aterricen en
  cold start).
- `ParkedWidgetState.Parked` gana `sessionId` (el resolver ya elegía la sesión; ahora la nombra).
- **Marca discreta**: el glifo `ic_notification_logo` pequeño, tintado con el primary del tema,
  presidiendo la fila del título. Nivel 2 (es un glifo tintable, no la ilustración de marca).
- **Paywall**: fila nueva en Free ("Widget de tu coche aparcado", icono Widgets) y primera línea
  de Plus "Todo lo de Free" (icono Check) — la comparativa dice explícitamente que Plus incluye
  lo free. ×9.
- **Colocar desde dentro**: puerto `domain/service/WidgetPinRequester` (`isPinSupported` +
  `requestPin`), impl en `:app` con `AppWidgetManager.requestPinAppWidget` (API 26+; algunos
  launchers no lo soportan → la fila solo aparece si el launcher puede). Fila en **Ajustes**
  (`canPinWidget` en el estado; el VM lo puebla del puerto, nullable para iOS/tests). ×9.
- Dev Catalog: variante de Ajustes con la fila visible; el widget en sí sigue exento (no es
  pantalla in-app).

## Criterio de éxito

Los dos iconos viven quietos en el borde derecho; el de direcciones abre mapas sin pasar por la
app; los tres puntitos abren la app con la tarjeta del vehículo seleccionada y la cámara en el pin
(tests del VM: selección+cámara solo si la sesión sigue activa); el logo se ve y no grita; la comparativa nombra el widget en Free y el
"todo lo de Free" en Plus; en Ajustes hay una fila que invoca el pin sheet del sistema. Suite +
Konsist + paridad ×9 (compose y app res) + assembles + klib iOS en verde.

## Consumidores auditados

- `resolveParkedWidgetState` → firma crece (sessionId); sus 6 tests se actualizan ✔
- `HomeReleaseDialogHost` → NO se duplica: el efecto reutiliza el MISMO estado local del Screen ✔
- `StartAddParkingEventBus` → patrón replicado, no tocado ✔
- Paywall FREE/PLUS_LINES → paridad de recuento e iconos revisada ✔
- Launchers sin pin support → la fila de Ajustes no aparece (isPinSupported) ✔
