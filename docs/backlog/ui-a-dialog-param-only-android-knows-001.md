# UI-A-DIALOG-PARAM-ONLY-ANDROID-KNOWS-001 · un parámetro que solo Android conoce dejó iOS sin compilar

**Estado:** ✅ Done (04-09-2026) · mergeado a master con squash · ⏳ declarado: el veredicto REAL
lo da el job `apple` de CI tras el push (K/N no compila en Windows); tras su verde, la rama iOS
(PR #3) se rebasea para heredar el fix.

## Problema

`699faf34` (03-09, SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001) escribió en **commonMain**
(`PapAlertDialog.kt:142`):

```kotlin
properties = DialogProperties(decorFitsSystemWindows = false),
```

`decorFitsSystemWindows` existe solo en el constructor **Android** de `DialogProperties`; el común
de Compose Multiplatform no lo tiene. Android compila; Kotlin/Native no:
`e: PapAlertDialog.kt:142:39 No parameter with name 'decorFitsSystemWindows' found.`

**El job `apple` de CI lleva rojo desde ese commit — 5+ pushes de master seguidos** (el job Android
pasa, así que nadie lo vio). Lo destapó el PR draft #3 de la rama iOS, que hereda el error por
rebase y muere en commonMain antes de llegar a `iosMain`.

## Doctrina violada

- «Si una API es Android-only, su uso vive en androidMain» (regla escrita en
  IOS-RESURRECT-001 tras la resurrección de junio — este es exactamente el drift que predijo).
- El job `apple` existía para cazar esto y lo cazó; lo que falló es que un rojo de CI en master no
  detiene nada. (Fuera de alcance aquí; el PR #3 ahora hace de testigo visible.)

## Señales / datos disponibles

- El parámetro NO es capricho: su comentario documenta que la ventana del diálogo se traga los
  insets del IME y sin el opt-out `imePadding()` es no-op — **medido en el Oppo**: con el campo del
  informe lleno, el teclado tapaba «Cancel». Borrarlo a secas re-rompería ese arreglo en Android.
- Único call site en commonMain (grep `DialogProperties` → solo `PapAlertDialog.kt`).
- Patrón ya establecido para UI expect/actual en `ui/components/`: `GlassBlur.kt` +
  `.android.kt`/`.ios.kt`.

## Diseño

El invariante — *el opt-out de decor-fits es un hecho de plataforma, no del diálogo* — vive en un
helper expect/actual junto al resto de UI por plataforma:

- `ui/components/ImeAwareDialogProperties.kt` (commonMain):
  `expect fun imeAwareDialogProperties(): DialogProperties`, con el KDoc del contrato (qué promete:
  un diálogo cuyo contenido puede reaccionar al IME con `imePadding()`).
- `.android.kt`: `DialogProperties(decorFitsSystemWindows = false)` — conserva el arreglo del Oppo.
- `.ios.kt`: `DialogProperties()` — el concepto no existe; los insets los gestiona la plataforma.
- `PapAlertDialog.kt` llama al helper. Ningún otro fichero de commonMain puede volver a nombrar el
  parámetro (si reaparece, vuelve a romper el job `apple`, que ahora sí tiene testigo: el PR #3).

## Criterio de éxito

- `:shared:testDebugUnitTest` + `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin`
  verdes en Windows.
- Tras mergear a master: el job `apple` de CI vuelve a VERDE (primera verificación iOS real — en
  Windows no se puede compilar K/N).
- El comportamiento Android no cambia: mismo `DialogProperties` efectivo en el APK.

## Consumidores auditados

- `grep DialogProperties shared/src/commonMain` → 1 solo call site (`PapAlertDialog.kt:142`),
  migrado al helper. `grep decorFitsSystemWindows` en todo `shared/src` y `app/src` → solo ese.
- Todos los diálogos de la app pasan por `PapAlertDialog` (centralización que su propio KDoc
  reivindica) → heredan el helper sin tocarlos.
