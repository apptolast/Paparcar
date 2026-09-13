# ONBOARDING-SELLS-THE-NEW-PRODUCT-001 · El onboarding cuenta el producto de hoy: tu coche vigilado, en cifras, y la plaza que liberas

**Estado:** 🟡 En revisión · PR desde rama `feature/ONBOARDING-SELLS-THE-NEW-PRODUCT-001` ·
GitHub issue [#7](https://github.com/apptolast/Paparcar/issues/7)

## Problema
Issue #7: «Vender la detección como servicio para evaluar datos de conducción y aparcamiento de tu
coche como los que tenemos ya en la app, guardar tus aparcamientos y liberar plazas libres a
usuarios. Con complementos de parkings y gasolineras. Enfocar el onboarding a la nueva definición de
producto».

El onboarding (2 páginas) seguía contando la app de la primera versión:
- Página 1: «La comunidad que libera plazas de aparcamiento. Cuando tú te vas, los demás encuentran
  la tuya…». Solo comunidad.
- Página 2 «Cómo funciona»: *Detectamos que conduces · Detectamos que aparcas · Compartimos la
  plaza*. MECÁNICA interna (qué hace el detector), no qué gana el usuario.
- Ni una palabra de las cifras (gasto aproximado, CO₂, hábitos — `PeriodStats`), del historial de
  aparcamientos, ni de parkings y gasolineras.

## Doctrina en juego
- ⛔ [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] y [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]:
  **nada de página de catálogo**. Se respeta: siguen siendo 2 páginas; los complementos entran como
  UNA tarjeta más, no como página. Su explicación de procedencia sigue viviendo en el carril.
- ⛔ [COPY-SPOT-IS-NOT-A-PARKING-001]: plaza (comunidad) ≠ aparcamiento (tuyo) ≠ parking público
  (car park). Cada tarjeta usa la palabra de su concepto, ×9 locales. «Tu plaza» desaparece: al irte,
  tu aparcamiento libera UNA plaza.
- ⛔ «No copy con mecánica interna» (CLAUDE.md): «Detectamos que conduces» era mecánica.
- ⛔ [UI-ONBOARDING-STEP-ICONS-SAY-WHAT-THE-APP-SAYS-001]: los glifos heredan su significado en la
  app. 1-3 se quedan (flecha = en ruta, coche = tu aparcamiento, megáfono = plaza que se avisa);
  el nuevo es `PaparcarIcons.CarParkMark`, el mismo del carril de parkings.
- Plus vs free: el onboarding no promete nada que no exista. Las cifras existen (free ve una, Plus
  todas — gating soft); ver parkings y gasolineras es free completo.

## Diseño
Mismas dos páginas, mismo `OnboardingScreen`, sin ViewModel nuevo:
- **Página 1 · qué es Paparcar** (`onboarding_page1_subtitle`): «Paparcar cuida de tu coche: guarda
  dónde aparcas, convierte tus trayectos en cifras y, cuando te vas, libera la plaza para quien busca
  cerca.»
- **Página 2 · qué hace por ti** (`onboarding_page2_title`, antes «Cómo funciona»), cuatro tarjetas:
  1. *Detección automática* — «Detecta sola tus trayectos y aparcamientos y te enseña gasto, CO₂ y
     hábitos». La detección vendida como servicio: lo que mide es lo que luego enseña.
  2. *Tu aparcamiento, guardado* — «Encuentra tu coche sin pensar y repasa dónde has aparcado».
  3. *Una plaza para otro* — «Cuando te vas, la plaza aparece libre para quien busca cerca».
  4. *También parkings y gasolineras* (NUEVA) — «El mapa te enseña parkings públicos y el precio del
     combustible cerca de ti».
- Se reutilizan las keys existentes (cambia su texto) y se añaden `onboarding_step4_title/_desc`,
  ×9 locales. Registro por locale alineado con el resto de la app (PT «tu», NL «je»).
- Con 4 tarjetas la página 2 ya no cabe en un móvil pequeño encima del CTA: pasa a scroll, y sigue
  centrada cuando cabe (`heightIn(min = viewport)`).
- `OnboardingScreen(initialPage)` para que galería y previews enseñen la página 2.

## Criterio de éxito
- ✅ Visto en emulador (Pixel 8 Pro, flavor mock, preset «Onboarding», EN): las dos páginas con el
  copy nuevo; la página 2 cabe con las cuatro tarjetas encima del CTA en ese tamaño.
- Un usuario nuevo sale del onboarding sabiendo las cuatro cosas que la app hace por él.
- Ninguna frase promete algo que la app no haga hoy; plaza / aparcamiento / parking con palabras
  distintas en los 9 locales. `LocaleParityGuardrailTest` verde.
- Página 2 legible en 360×640 (preview «small phone»).
- Galería: «Onboarding · qué hace por ti». `compileMockDebugKotlin` + `compileProdDebugKotlin` verdes.

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `OnboardingScreen` (pager, 2 páginas) | página 2 con 4ª tarjeta y scroll; `initialPage` opcional |
| `App.kt` (`onComplete` → vehículo/Home) | sin cambio |
| `OnboardingPreviews.kt` | + página 2 normal y en móvil pequeño |
| `StateGalleryScreen` grupo «Onboarding» | + variante página 2 |
| `FirstSteps*` (checklist de Home) | exento — ya cubre complementos (`EXPLORE_NEARBY`) y detección |
| Paywall `TIER_ROWS` | exento — ya dice que parkings y combustible son free |
| `docs/release/play-listing/*` | fuera de alcance — la ficha de Play debería contar lo mismo; follow-up |

## Preguntas abiertas (para la revisión)
1. **Copy**: es propuesta. En particular «cuida de tu coche» y «convierte tus trayectos en cifras» —
   ¿es el tono que queréis para la nueva definición de producto?
2. La tarjeta 1 menciona gasto, CO₂ y hábitos, que en free se ven parcialmente (una cifra) y
   completos en Plus. ¿Se nombran así o preferís algo neutro («tus datos de conducción»)?
3. ¿Hace falta ilustración nueva (Nivel 3) para la página 2? Se mantiene `illus_onb_how`.
4. ¿Actualizamos también la ficha de Google Play con esta definición? (no incluido).
