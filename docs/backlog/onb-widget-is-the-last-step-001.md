# ONB-WIDGET-IS-THE-LAST-STEP-001 · El tutorial termina dejando el coche en tu pantalla de inicio

**Estado:** 📋 Abierto (2026-09-08) — idea del user al cerrar WIDGET-ACTIONS-002. Sin empezar.

## Problema

La checklist de primeros pasos enseña a usar la app y se despide sin dejar nada permanente. El
widget es el mejor cierre posible: el último paso te lleva el coche a la pantalla de inicio — un
recordatorio diario de la promesa ("encuentra tu coche") y la mejor vacuna contra la
desinstalación, colocado justo cuando el usuario acaba de entender para qué sirve.

## Diseño (a detallar al abrirlo)

- Paso nuevo en `FirstStep` (el enum de la checklist guiada
  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]) — el enum obliga a contestar sus
  preguntas exhaustivas, como `VehicleType`. **OPCIONAL explícito** (pedido del user 08-09): el
  cierre del tutorial no depende de él — es el "llévatelo puesto" de despedida, no un requisito.
- La acción del paso reutiliza `WidgetPinRequester.requestPin()` (ya existe desde
  WIDGET-ACTIONS-002; la fila de Ajustes queda como puerta permanente).
- **Completado medible**: `AppWidgetManager.getAppWidgetIds(provider)` no vacío = el widget está
  puesto — extender el puerto con `isWidgetPlaced` para que el paso se marque solo, sin fiarse de
  "pulsó el botón" (el pin sheet se puede cancelar).
- Launchers sin pin support → el paso no aparece (mismo gate `isPinSupported` que la fila de
  Ajustes): un tutorial nunca pide algo imposible en este móvil.
- Explainer del paso (página, como los demás), strings ×9, galería/escenarios mock del checklist
  al día en la misma tarea.

## Cuándo

Después de F3/F4 o al retomar onboarding — no bloquea la ola. El user decide el hueco.
