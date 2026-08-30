# I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001 · dos botones de la pantalla de permisos existen en 2 de los 9 idiomas

**Estado:** ✅ Done · mergeado a master (30-08-2026)

Detectado el 30-08-2026 auditando la paridad de keys durante
`COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001` — no salió de un informe de campo ni de un
usuario: en 7 idiomas nadie se quejó nunca, que es justo el problema de un fallo mudo.

## Problema

`permissions_btn_allow_background` y `permissions_btn_continue` existen **solo** en `values` (EN) y
`values-es`. Faltan en `-de`, `-fr`, `-it`, `-nl`, `-pl`, `-pt`, `-ro`.

Se renderizan en `PermissionsContent.kt:380` (CTA de fiabilidad) y `:388/:395` (el botón que cierra
el onboarding) — la pantalla por la que pasa **todo** usuario nuevo antes de llegar a Home. En 7 de
9 idiomas ese footer sale en inglés.

Entraron en `8815fe48` *fix(permissions): always-anchored footer + nav-bar inset + optional-tier CTA
[PERM-FOOTER-001]* (13-07-2026), un commit que tocó exactamente `values` y `values-es` y ningún otro
locale. **48 días sin que nada lo notase.**

El mismo commit añadió `permissions_reliability_reduced_callout`, y ese sí llegó después a los 9
(en `-it` está en la línea 616, al final del fichero, lejos de sus hermanos de la línea ~100). O sea:
el barrido de idiomas se hizo a mano, key a key, y se dejó 2 de 3.

## Doctrina violada

`CLAUDE.md` → *«Todo string nuevo se añade a los 9 locales en la MISMA tarea»*.

⚠️ **La razón que da la regla es falsa, y esa es la parte interesante.** `CLAUDE.md` dice que
Compose Resources *«crashea si falta en el locale activo»*. Leído el algoritmo de resolución de CMP
1.12 (`components-resources-1.12.0-sources.jar` →
`ResourceEnvironment.kt:182-195`, `filterByLocale`):

```kotlin
val withLanguage = filter { item -> item.qualifiers.any { it == language } }
if (withLanguage.isEmpty()) return noLocaleItems     // ← cae al default, no revienta
```

Hay **dos** fallos distintos, y solo uno crashea:

| Dónde falta la key | Comportamiento real | Gravedad |
|---|---|---|
| Falta en un locale de traducción, está en `values` | `filterByLocale` devuelve el item sin qualifier → **sale en inglés, en silencio** | bug mudo |
| Falta en `values` (aunque esté en `values-es`) | `noLocaleItems` vacío → `items.isEmpty()` → `error("Resource with ID='…' not found")` (`ResourceEnvironment.kt:105`) | **crash** |

Nuestro caso es el primero: **no crashea, se degrada a inglés sin decir nada**. Por eso sobrevivió
48 días — un crash se habría delatado solo. La regla de los 9 locales sigue siendo correcta; lo que
estaba mal era creer que el runtime la vigilaba por nosotros. Nadie la vigilaba.

## Señales / datos disponibles

Diff de conjuntos de keys entre los 9 ficheros (`<string>` + `<plurals>`):

- 548 keys en `values` y en `values-es`; 546 en los otros 7. Delta = exactamente las 2 del ticket.
- **Ninguna** key existe en un locale y no en `values` → hoy no hay ningún crash latente por esta vía.
- Sin keys duplicadas dentro de un mismo fichero.
- Paridad de placeholders (`%1$s`, `%d`…) correcta en las 544×8 comparaciones.
- Los 4 `<plurals>` existen en los 9 (ver *Fuera de alcance*).

## Diseño

Sistema, no parche. El parche son 14 líneas de XML; el sistema es **quién vigila el invariante**.

1. **Las 2 keys en los 7 locales que faltan**, colocadas junto a sus hermanas (detrás de
   `permissions_continue_with_core`, con el comentario `PERM-FOOTER-001` que las agrupa en la base),
   no apiladas al final del fichero.
2. **`LocaleParityGuardrailTest`** (`shared/src/androidUnitTest/.../architecture/`), junto a los
   guardarraíles ya existentes de color/tipografía/divisores. Parsea los `strings.xml` y afirma que
   el conjunto de nombres es **idéntico** en los nueve, distinguiendo los dos fallos de la tabla:
   - key en `values` y ausente en un locale → *fuga silenciosa a inglés*
   - key en un locale y ausente en `values` → **crash**, se reporta como tal
   - y que las carpetas de locale sean exactamente las 9 (un idioma nuevo entra vigilado desde el
     primer día, no cuando alguien se acuerde)
   Cubre `<string>`, `<plurals>` y `<string-array>` (hoy no hay arrays; la key nueva no debe poder
   colarse por un tipo de elemento no vigilado).
3. **Las DOS superficies de strings, no solo la que se rompió.** Al auditar consumidores apareció que
   `:app/src/main/res` tiene su propio juego de 9 ficheros (46 keys: canales y copy de
   notificaciones), tan visible para el usuario como el de `:shared` y con el mismo modo de deriva.
   Hoy está sano; no lo vigilaba nadie. El guardarraíl recorre las dos.
4. **Declarar los `strings.xml` como INPUT de los tests** (`shared/build.gradle.kts`). Ver *Falsación*:
   sin esto el guardarraíl es decorativo para `:app`.
5. **Corregir la justificación en `CLAUDE.md`**, que es lo que hacía razonable no comprobarlo: la
   regla se queda, el «crashea» se sustituye por lo medido.

El invariante vive en UN sitio (el test) y se comprueba en cada `:shared:testDebugUnitTest`, que es
lo que corre el CI.

**Lo que NO se comprueba, a propósito:** keys duplicadas dentro de un mismo fichero. El plugin de
Compose Resources ya falla `convertXmlValueResourcesForCommonMain` con *"Duplicated key '…'"* antes
de que los tests compilen — medido al falsificar este guardarraíl. Un segundo dueño para un
invariante que ya tiene uno es ceremonia, no seguridad.

## Criterio de éxito — verificado

- ✅ Las 9 carpetas de `:shared` tienen el mismo conjunto de 548 keys; los dos botones salen
  traducidos (bytes UTF-8 comprobados en PL/RO/FR: `dzia\xc5\x82anie`, `Continu\xc4\x83`,
  `arri\xc3\xa8re`).
- ✅ `:shared:testDebugUnitTest` verde: **157 clases, 1.797 tests, 0 fallos**.
- ✅ `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` y `:app:assembleMockDebug` verdes.
- ✅ **Los 9 idiomas vistos en pantalla** (ver abajo).

### Verificado en device — los 9 footers

APK `mockDebug` en emulador con la geometría del **Pixel 8** (`wm size 1080x2400`, `wm density 420`
= 411 dp de ancho; solo hay AVD de Pixel 8 **Pro**, 448 dp, y el estrecho es el caso apretado).
Dev Catalog → galería de estados → *Permisos · Críticos concedidos*, que es el único estado donde
salen los DOS botones (`requiredComplete && batteryPending`, `PermissionsContent.kt:376-392`).

Navegación por texto con `uiautomator dump` — las etiquetas del Dev Catalog están cableadas en
español, así que no se mueven al cambiar de idioma — y **el script afirma que la etiqueta esperada
está en pantalla antes de guardar la captura**. No es adorno: en la primera pasada, sin esa
comprobación, dos capturas salieron de la pantalla equivocada (una era la lista de la galería, otra
mostraba holandés bajo el nombre `fr`) y habrían pasado por buenas.

Medido el texto blanco dentro del botón relleno (botón = 953 px):

| | EN | ES | IT | PT | FR | DE | NL | PL | RO |
|---|---|---|---|---|---|---|---|---|---|
| ancho del texto (px) | 495 | 491 | 448 | **504** | 462 | 455 | 481 | 462 | 312 |
| margen por lado (px) | 229 | 231 | 252 | **224** | 245 | 249 | 236 | 245 | 320 |

Ninguno pasa del **53 %** del botón; alto del texto 30-37 px en los nueve = **una línea**, sin
elipsis ni salto. El peor caso es PT, no PL como suponía por contar caracteres. Diacríticos
correctos en pantalla: `ł`/`ó` (PL), `î`/`ă` (RO), `è` (FR).

### Falsación — el guardarraíl se ha visto en ROJO

Un test de paridad que nunca se ha visto fallar es un test que siempre pasa. Cuatro mutaciones:

| Mutación | Resultado |
|---|---|
| Quitar `permissions_btn_continue` de `values-de` | 🔴 *"key missing from a locale — silently renders in English"*, nombrando fichero y key |
| Quitar `permissions_btn_grant` solo de `values` | 🔴 *"…CRASHES in every locale that lacks it"*, listando los 8 locales huérfanos |
| Crear `values-ca` | 🔴 *"locale folders drifted from the 9"* |
| Duplicar una key en `values-es` | 🔴 pero **no por el test**: falla antes `convertXmlValueResourcesForCommonMain` del plugin de Compose → el test sobra y se retiró |

**Y una quinta que no falló, que es la que importa:** mutar `app/src/main/res/values-it` dejó el
build en VERDE, con `:shared:testDebugUnitTest` **UP-TO-DATE**. Gradle no sabía que esos ficheros son
entrada del test. Que la superficie de `:shared` sí despertase era casualidad — sus recursos alimentan
la compilación, así que el classpath cambiaba. Arreglado declarando ambas carpetas como `inputs.dir`
en `shared/build.gradle.kts`; repetida la mutación, 🔴 nombrando `app/src/main/res/values-it`.

## Consumidores auditados

Barrido de todo lo que asume «la key existe en mi idioma»:

| Sitio | Estado |
|---|---|
| `PermissionsContent.kt:380,388,395` (los 3 usos de las 2 keys) | ✅ cerrado — keys en los 9 |
| Resto de `:shared/composeResources` (548 keys × 9 locales) | ✅ cubierto — diff de conjuntos limpio + guardarraíl |
| `:app/src/main/res` — 9 ficheros, 46 keys (canales y copy de notificaciones) | ✅ medido sano (0 huecos) y **ahora vigilado**; antes no lo estaba |
| Paridad de placeholders (`%1$s`, `%d`) | ✅ 544×8 comparaciones sin discrepancia |
| `<plurals>` (4) | ✅ existen en los 9 · categorías CLDR → *Fuera de alcance* |
| `<string-array>` | ✅ exento — no hay ninguno; el guardarraíl ya los vigila para cuando lo haya |

## Fuera de alcance (follow-up)

Los `<plurals>` existen en los 9 idiomas, pero sus **categorías CLDR** no están completas en las
lenguas eslavas/rumanas: `values-pl/home_vehicles_section_header` solo declara `one`/`other` (el
polaco necesita `few` y `many`), y en `values-ro` tres de los cuatro plurales no declaran `few`.
No es este ticket (aquí el fallo es de *existencia*, no de *categoría*) y no crashea — cae a `other`.
→ ticket propio: `I18N-PLURAL-CATEGORIES-FOLLOW-THE-LANGUAGE-NOT-ENGLISH-001`.
