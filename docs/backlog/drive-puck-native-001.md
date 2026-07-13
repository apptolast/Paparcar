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

## Decisión (2026-07-13)

**A + B**: fork propio para desbloquear ya **y** PR upstream con el fix aislado.

## Raíz confirmada (kmp-maps v0.9.1)

- `core/commonMain/MapTypes.kt` → `Marker.getId() = "marker_${hashCode()}"`. `Marker` es
  `data class`, así que `hashCode` incluye `coordinates` → al moverse cambia el id.
- `core/androidMain/Map.kt` → `key(marker.getId(), contentId) { … }`: al cambiar el id se
  **destruye y recrea** el marker (aunque ya existe un `LaunchedEffect(coordinates)` que
  reposiciona en su sitio — nunca sobrevivía a la recreación). Eso es el flicker.

## El fix (fork + contenido del PR)

1. `Marker.id: String? = null` opcional; `getId()` lo prefiere (`id=null` → hashCode, 100%
   retrocompatible). Con id estable el marker se reposiciona en su sitio (no se recrea).
2. Android: `key(getId())` estable + `contentId` pasado como *snapshot key* de
   `MarkerComposable` → el bitmap se re-renderiza in-place al cambiar contenido (dim/heading)
   sin cambiar identidad. Fin del hack de recrear-por-contentId.
3. `AndroidMarkerOptions.rotation` + `flat` → rotación nativa del marker. Un solo bitmap
   norte-arriba rotado al heading (el mapa es siempre norte-up) en vez de un bitmap por bucket.

## Estado (2026-07-13)

- **Fork** en `../kmp-maps` (clon de `software-mansion/kmp-maps` en `v0.9.1`):
  - Rama consumo `paparcar/0.9.1-puck`: fix keying + rotación + tweak build (foojay para
    auto-provisionar JDK 17). Compila. Publicado a **mavenLocal** como
    `com.swmansion.kmpmaps:core:0.9.1-puck-SNAPSHOT` (snapshot ⇒ vanniktech omite firma).
  - Rama PR `upstream-pr/animated-marker` (sobre `origin/main`, solo los 2 commits feat, SIN
    build tweak). Compila sobre main. Borrador en `../kmp-maps/PR_DRAFT_animated_marker.md`.
- **Paparcar** (esta rama, worktree `Paparcar-puck`): `PaparcarMapView` reescrito — el puck es
  un `Marker` nativo con `id` estable + rotación nativa; overlays Compose (centrado + proyección
  Web Mercator) ELIMINADOS; `contentId` del puck sin heading (un bitmap por carbody×color).
  `settings.gradle.kts` + `libs.versions.toml` apuntan a mavenLocal. Compila mock + prod.
  Commit marcado **WIP / NO MERGE**.

## Pendiente

- **Bloqueado (necesita usuario)**: forkear `software-mansion/kmp-maps` en GitHub + push de
  `upstream-pr/animated-marker` + abrir el PR (no hay `gh` CLI en el entorno).
- Publicar el fork a repo compartido (JitPack por tag) y repuntar `kmp-maps` en libs a esa
  coordenada (hoy es SNAPSHOT solo-mavenLocal → rompería CI/otros equipos si se mergea).
- **Validación en DEVICE**: movimiento sin flicker, rotación (ancla/tilt), interpolación entre
  fixes, y follow de cámara (el puck nativo va donde apunta la cámara; `centerDrivingPuck` quedó
  como señal de follow, revisar si hace falta afinar).
- iOS: rotación solo cableada en Android; el `id` estable ya lo heredan iOS/JVM.
