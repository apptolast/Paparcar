# UX-PARK-FLOW-001 — Regresión de flujo UI aparcar/desaparcar

> **Estado**: POR DEFINIR — placeholder decidido con el user 2026-07-16. Pendiente de análisis y
> definición (sesión de diseño propia antes de especificar). Prioridad: backlog.

## Motivo

El manejo aparcar/desaparcar resulta confuso para un usuario inexperimentado: qué significa el
vehículo activo, qué pasa al liberar una plaza, cuándo está la detección vigilando y cuándo no,
qué hace "Estoy conduciendo". Con VEH-ACTIVE-FENCE-001 el modelo de fondo queda coherente
(activo = declaración; liberar = voy a conducir); falta que la UI lo CUENTE.

## Alcance orientativo (a validar en el análisis)

- Recorrido completo del flujo: marcar aparcamiento manual, confirmación de detección, peek de
  sesión, liberar (con/sin publicar), multi-vehículo, sesión-zona aproximada (DET-HONEST-CLOSE-001).
- Los diálogos de consecuencia de VEH-ACTIVE-FENCE-001 (set-active, liberar activo/inactivo) son
  el punto de partida; este ticket evalúa el flujo entero alrededor.
- Estados visibles de la detección: qué ve el usuario cuando la app "está vigilando" vs "necesita
  que declares el coche".
- Copy siempre causa+consecuencia+remedio, sin mecánica interna [feedback_no_internals_in_user_copy].

## Entregable del análisis

Documento de flujo (pantalla × estado × acción) + propuesta de rediseño priorizada; después,
tickets de implementación separados. Sincronizar Dev Catalog/StateGallery con cada estado nuevo.
