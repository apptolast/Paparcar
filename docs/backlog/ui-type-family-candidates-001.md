# UI-TYPE-FAMILY-CANDIDATES-001 · Plus Jakarta Sans habla por Paparcar, y las métricas de la fuente viven con la fuente

**Estado:** ✅ **Done** (29-08) — mergeado a master tras verlo corriendo en el Redmi y medir en
píxeles las dos alineaciones.

⏳ **Vivo, deliberadamente fuera de este cierre:**
- **Retirar Outfit, Inter y Barlow del repo** y con ellos el selector del laboratorio. Mientras
  sigan, el APK carga cinco familias para usar una. Es la tarea que cierra de verdad la adopción.
- Sólo se han visto en device **Home, Vehículos y Ajustes**. Faltan onboarding, permisos, registro
  de vehículo y los peeks del mapa.
- Modo claro, Oppo, y los 9 idiomas con las etiquetas nuevas de dos palabras (alemán y neerlandés
  son los candidatos a desbordar).

## Problema

Cerrado `UI-TYPE-TWO-VOICES-ONE-ROW-001`, quedaba viva la pregunta de fondo del user: **¿sobran
familias?** La app usaba tres caras de esqueletos distintos — Outfit (geométrica), Inter
(neo-grotesca), Barlow Condensed (grotesca estrecha) — y eso se nota aunque cada una esté en su
sitio.

## Doctrina

- **`CLAUDE.md` § Tipografía** — el sistema habla de VOCES (marca / lectura / cifra). Que las voces
  se resolvieran a familias concretas dentro de `rememberPaparcarType()` hacía que responder a la
  pregunta obligara a editar la tabla de roles. Ahora un `PapFontSet` dice qué fuente pone cada voz:
  **cambiar de familia no toca ni un rol ni un call site.**
- **`feedback_systems_not_patches`** — la altura de mayúscula estaba cableada en una pantalla
  (`BARLOW_CAP_HEIGHT_EM`) cuando es un dato de la FUENTE. Al cambiar de familia el icono se
  despegaba de los dígitos y nada lo avisaba.

## Diseño

### D.1 · Las voces se resuelven en un `PapFontSet`
`PaparcarTheme` lee un override opcional de `LocalPapFontSet`; en producción es `null` y todo queda
igual. El flavor `mock` gana un selector en el Dev Catalog que alcanza **el grafo real** (Home,
Vehículos, Ajustes, onboarding), no sólo una fila de laboratorio: ver una candidata en una fila
aislada no dice si aguanta una pantalla de Ajustes entera.

### D.2 · Adoptada: Plus Jakarta Sans en las tres voces
Decisión del user (29-08) tras verla corriendo en el Redmi contra Outfit+Inter+Barlow y contra
Archivo. Una familia, un fichero, y el carácter redondeado que la marca ya tenía con Outfit — que es
lo que Archivo, siendo el sistema más limpio sobre el papel, se llevaba por delante.

**Las voces siguen existiendo aunque las tres apunten a la misma fuente**: son las que deciden peso,
tamaño, y cuándo un texto es un nombre, una cifra o prosa. Compartir familia no las fusiona.

### D.3 · Las métricas de la fuente viven con la fuente
`PapFontSet` lleva `figureCapHeightEm`, `figureAscentEm` y `figureDescentEm`, leídos de las tablas
`OS/2` y `hhea` de cada `.ttf`:

| Familia | cap | ascent | descent |
|---|---|---|---|
| Barlow Condensed | 0.700 | 1.000 | 0.200 |
| **Plus Jakarta Sans** | **0.745** | **1.038** | **0.222** |
| Archivo | 0.686 | 0.878 | 0.210 |

Con eso, dos alineaciones dejan de estar calibradas a mano para una familia concreta:
- **El icono de cada stat** se centra sobre la banda de dígitos usando el cap-height del tema.
- **El contador del sheet** (`6 / LIBRES`) se sube por el hueco muerto que el dígito no usa arriba
  menos el que las mayúsculas no usan abajo. `Trim.Both` NO sirve para esto: recorta el exceso de
  `lineHeight`, no el ascent de la fuente. Verificado midiendo píxeles: el trim solo dejó el bloque
  exactamente igual.

## Criterio de éxito

1. ✅ `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin`.
2. ✅ **Contador del sheet centrado**, medido en píxeles sobre la captura: de 47/33 (desviación
   −14 px) a 41/39 (**−2 px**).
3. ✅ **Stats alineadas**, medido: el centro del número y el de su etiqueta difieren **±1 px** en las
   tres celdas, y las tres están equiespaciadas.
4. ⏳ Ver el resto de pantallas en device (onboarding, permisos, registro, peeks del mapa).
5. ⏳ Modo claro, Oppo, y los 9 idiomas con las etiquetas nuevas.

## Consumidores auditados

- `ui/theme/PapFontSet.kt` (nuevo) · `ui/theme/PaparcarType.kt` · `ui/theme/Theme.kt`
- `presentation/vehicles/VehiclePageContent.kt` — cap-height del tema; `textAlign` y `maxLines = 2`
  en la etiqueta.
- `presentation/home/.../PapSheet.kt` — corrección óptica del contador.
- `app/src/mock/.../dev/` — `DevFontChoice`, selector en el catálogo, provider en `DevRoot`.

### Copy: las tres stats pasan a dos palabras
`SESIONES` → `SESIONES TOTALES` · `ÚLTIMA` → `ÚLTIMA SESIÓN` · `PLAZAS CEDIDAS` (sin cambio).
Actualizado en los **9 locales**. Dos motivos: la fila queda simétrica (las tres etiquetas ocupan
dos líneas), y `ÚLTIMA` era un adjetivo suelto que no decía de qué. Antes de esto,
`PLAZAS CEDIDAS` se partía en dos líneas con `maxLines = 1` y **perdía la segunda palabra sin
parecer roto**.

## Fuera de alcance

- **Retirar Outfit, Inter y Barlow del repo.** Siguen dentro porque el laboratorio los usa para
  comparar. Mientras no se retiren, el APK carga cinco familias para usar una: al confirmar la
  adopción, salen en su propia tarea junto con el selector del catálogo.
- La higiene abierta en `ui-type-system-hygiene-001.md`.
