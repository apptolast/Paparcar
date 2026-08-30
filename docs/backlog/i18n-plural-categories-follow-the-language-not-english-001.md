# I18N-PLURAL-CATEGORIES-FOLLOW-THE-LANGUAGE-NOT-ENGLISH-001 · los plurales eslavos y rumanos declaran las categorías del inglés

**Estado:** ✅ Done · mergeado a master (30-08-2026) · abierto como follow-up de
`I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001`

## Problema

Los 4 `<plurals>` existen en los 9 idiomas — la *existencia* ya la vigila
`LocaleParityGuardrailTest`. Lo que no seguía al idioma eran las **categorías CLDR**: varias
traducciones declaraban solo `one`/`other`, que es la forma del inglés, no la suya.

Al medir apareció que el diagnóstico con el que se abrió el ticket **se quedaba corto en rumano y
largo en polaco**:

| Fichero · plural | Lo que decía el ticket | Lo que estaba pasando de verdad |
|---|---|---|
| `values-ro/home_feed_nearby_with_count` | «falta `few`» | El `other` **contenía la forma de `few`**. En rumano «de» aparece a partir de 20 (*20 **de** locuri*), no antes (*3 locuri*). Con 3 caía a `other` y acertaba por casualidad; **con 20+ decía «20 locuri libere», que está mal**. Añadir `few` sin tocar `other` habría dejado el fallo intacto. |
| `values-ro/history_weekly_subtitle` | «falta `few`» | Igual — pero la key **estaba MUERTA**, y con ella su título. **Borradas** (ver abajo), así que aquí no había nada que traducir. |
| `values-ro/home_vehicles_section_header` | «falta `few`» | La frase **no lleva numeral** («Vehiculele tale»), así que `few` y `other` coinciden. Cosmético. |
| `values-pl/home_vehicles_section_header` | «faltan `few`/`many`» | Sin numeral → el polaco usa el nominativo plural en las tres. Cosmético. |

O sea: **un solo fallo visible de verdad** (el feed rumano a partir de 20 plazas) y tres huecos que
no cambiaban un píxel. El bueno no era «falta una categoría» sino «la categoría que hay tiene el
texto de otra» — que ningún recuento de categorías habría encontrado.

## Doctrina violada

Ninguna escrita. `CLAUDE.md` exige que la *key* esté en los 9 locales, y eso ya se cumple y se
vigila. Nada decía que un plural deba declarar las categorías **de su idioma**, que es lo que
distingue una traducción de un calco del inglés.

## Medido antes de decidir (lo que el doc de apertura exigía)

CMP 1.12, `PluralStringResources.kt`:

```kotlin
val pluralRuleList = PluralRuleList.getInstance(environment.language, environment.region)
val pluralCategory = pluralRuleList.getCategory(quantity)
val str = item.items[pluralCategory]
    ?: item.items[PluralCategory.OTHER]            // ← cae a `other`, no revienta
    ?: error("Quantity string ID=`…` does not have the pluralization …")
```

1. **Sí** usa las reglas CLDR del idioma, no las del inglés.
2. Categoría ausente → **cae a `other` en silencio**. Solo revienta un `<plurals>` **sin `other`**.

Reglas que CMP trae (`plural/CLDRPluralRuleLists.kt`), leídas para los 9:

| | categorías declaradas por el idioma | alcanzables con cuentas reales |
|---|---|---|
| en, de, nl | one · other | one · other |
| es, it, pt, fr | one · **many** · other | one · other — `many` exige `i % 1000000 = 0` o notación compacta |
| pl | one · few · many · other | **las cuatro** (5 plazas ya es `many`) |
| ro | one · few · other | **las tres** (0 y 2-19 son `few`) |

`pt`/`fr` además meten el **0 en `one`** (`i = 0..1`), detalle que no muerde aquí pero conviene
saber.

## Diseño

1. **Las 4 correcciones**, la rumana reescribiendo `other` con «de», no solo añadiendo `few`.
2. **`LocaleParityGuardrailTest` gana un cuarto test**: cada `<plurals>` declara las categorías
   *alcanzables* de su idioma, con la tabla de arriba en el `companion object` citando la regla CLDR.
3. **Se exigen también donde las formas coinciden** (cabeceras sin numeral). El test no puede saber
   si la frase lleva cifra al lado: `history_activity_noun` **no tiene placeholder** y sin embargo se
   pinta como `append("$total ")` + el plural (`HistoryWeeklyChart.kt:142`). Un guardarraíl que
   intentara adivinarlo por el `%1$d` habría eximido justo al que sí la necesita. Duplicar dos items
   idénticos es el precio honesto de no poder saberlo, y obliga al siguiente traductor a pensarlo.
4. `many` de es/it/pt/fr **fuera** de lo exigido, con la regla anotada: pedirlo solo compraría items
   duplicados para un caso (un millón de plazas) que no existe.

## Criterio de éxito — verificado

- ✅ Los 4 plurales declaran las categorías alcanzables de su idioma en los 9 ficheros.
- ✅ **Los dos lados de la frontera CLDR, vistos en device** (emulador a 411 dp, locale `ro-RO`,
  Dev Catalog → galería):
  - 6 plazas → «**6 LOCURI LIBERE** ÎN APROPIEREA TA» (`few`, sin «de»)
  - 24 plazas → «**24 DE LOCURI LIBERE** ÎN APROPIEREA TA» (`other`, con «de»)

  El caso de 20+ **no se alcanza con los fakes** (`FakeData.nearbySpots` tiene 6), así que se forzó
  con una variante temporal de 24 en `StateGalleryScreen`, ya revertida — nunca se commiteó. Sin
  ella, la captura habría enseñado justo el caso que ya funcionaba por casualidad y habría dado el
  fix por bueno sin probarlo.
- ✅ `:shared:testDebugUnitTest` verde.
- ✅ **Visto fallar** (falsación): quitado el `few` nuevo de `values-ro` y el `many` que ya tenía
  `values-pl/home_feed_nearby_with_count`, el test se pone rojo nombrando fichero, plural y
  categoría — 2 violaciones, ni una de más:
  ```
  - …/values-pl/strings.xml · home_feed_nearby_with_count declares [few, one, other], missing [many]
  - …/values-ro/strings.xml · home_feed_nearby_with_count declares [one, other], missing [few]
  ```

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `home_feed_nearby_with_count` — `HomeSheetContent.kt:276` | ✅ corregido (el único fallo visible) |
| `home_vehicles_section_header` — `HomeSheetContent.kt:166` | ✅ categorías completas (sin delta visual: no hay numeral) |
| `history_activity_noun` — `HistoryWeeklyChart.kt:142,203` | ✅ ya estaba bien en PL y RO; es el modelo que confirmó el patrón del «de» |
| `history_weekly_subtitle` + `history_weekly_title` | 🗑️ **borradas de los 9** — sin call site (ver abajo) |
| `<plurals>` en `app/src/main/res` | ✅ exento — no hay ninguno (medido en los 9 ficheros) |

## Borrado de la cabecera semanal muerta (decidido por el user, 30-08)

`history_weekly_subtitle` no la leía nadie: cero apariciones en `.kt`, solo las 9 declaraciones.
Al barrer las **45 keys `history_*`** para comprobarlo aparecieron **dos** muertas, no una: el
plural y su **título hermano `history_weekly_title`** («Weekly activity»). Son las dos mitades de
la misma cabecera, sustituida en su día por el `ActivityCardTitle` de `HistoryWeeklyChart`. Borrar
solo el subtítulo habría dejado un título huérfano, así que se van las dos, de los 9 ficheros
(−45 líneas).

Sobrevivían porque **los barridos de i18n miran paridad entre idiomas, nunca uso**: una key muerta
está muerta *en los nueve*, así que la paridad la da por sana y encima obliga a traducirla en cada
idioma nuevo que se añada.

## Fuera de alcance — hay 23 keys más sin referencia

El mismo barrido, extendido a todo el set: **22 keys de `composeResources`** (de 542) y **1 de
`app/src/main/res`** (de 46) no aparecen en ningún `.kt` — entre ellas el grupo entero de
`home_peek_spot_compat_*` (7) y los `home_vehicle_chip_*` (4).

⚠️ Es búsqueda por subcadena: una key compuesta dinámicamente daría falso positivo. **No se borra
ninguna aquí** — hay que mirarlas una a una, y este ticket es de gramática, no de limpieza.
→ ticket propio: `I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001`.
