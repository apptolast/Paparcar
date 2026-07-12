# DRIVE-PUCK-NATIVE-001 — Fork de kmpmaps para puck de conducción nativo

- **Rama:** `feature/DRIVE-PUCK-NATIVE-001-kmpmaps-fork`
- **Origen:** backlog de DET-PHASE-BANNER-001 (2026-07-01) + limitaciones de kmpmaps
  medidas en campo. Idea del usuario: clonar la librería y construir lógica propia.

## Por qué

kmpmaps (SW Mansion, 0.9.x) no expone control de marcadores en movimiento:

1. **Flicker estructural**: los markers se keyean por `hashCode` (incluye coords) — un
   marker que se mueve cada frame se destruye y recrea (teardown+recreate). Workaround
   actual: el puck se pinta como **overlay Compose centrado** + proyección Web Mercator
   + interpolación (PUCK-FLICKER-001, DET-PHASE-BANNER-001). Funciona, pero el puck no
   rota con el mapa, no respeta tilt, y vive fuera de la capa del mapa.
2. **Estado dim por hack**: la opacidad de marcadores se refresca codificando el estado
   en `contentId` (MAP-MARKERS-DIM-002).
3. Sin API de animación de cámara/marker sincronizada (follow suave nativo).

## Opciones (decidir al arrancar)

- **A · Fork propio**: clonar `software-mansion/kmp-maps`, añadir `AnimatedMarker`
  (keying estable por id + update de posición sin recreate) y publicar via JitPack o
  maven local. Control total; coste: mantener el fork al día.
- **B · Contribuir upstream**: PR con el keying estable — el fix real es pequeño
  (keyear por requestId en vez de hashCode). Menos control, cero mantenimiento.
- **C · Quedarnos en overlay** (status quo): aceptable hoy; el dolor crece con
  rotate/tilt y con iOS (Apple Maps).

## Alcance si se hace (A o B)

Puck nativo (posición+bearing animados), fin del hack de `contentId`, y evaluar el
follow de cámara nativo. La polilínea del trail ya es nativa ✓.

## Estado

Sin empezar. El overlay actual está validado en campo y no bloquea nada — prioridad
por detrás de la auditoría (infra/rules) y de DET-TIERS-001.
