# I18N-PLURAL-CATEGORIES-FOLLOW-THE-LANGUAGE-NOT-ENGLISH-001 · los plurales eslavos declaran las categorías del inglés

**Estado:** 🟡 Abierto, sin rama · detectado como daño colateral de
`I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001` (30-08-2026)

## Problema

Los 4 `<plurals>` existen en los 9 idiomas — la *existencia* está cubierta y vigilada. Lo que no
sigue al idioma son las **categorías CLDR**: varias traducciones declaran solo `one`/`other`, que es
la forma del inglés, no la suya.

| Fichero | Plural | Declara | Necesita (CLDR) |
|---|---|---|---|
| `values-pl` | `home_vehicles_section_header` | `one`, `other` | `one`, `few`, `many`, `other` |
| `values-ro` | `home_vehicles_section_header` | `one`, `other` | `one`, `few`, `other` |
| `values-ro` | `home_feed_nearby_with_count` | `one`, `other` | `one`, `few`, `other` |
| `values-ro` | `history_weekly_subtitle` | `one`, `other` | `one`, `few`, `other` |

Los otros tres plurales de `values-pl` sí declaran las cuatro categorías, y
`values-ro/history_activity_noun` sí declara `few` — o sea, no es una decisión, es un descuido
desigual.

Consecuencia: en polaco, 3 coches leen la forma de "muchos" en vez de la de `few`; en rumano, 3–19
unidades leen la forma de `other` (que en rumano exige "de": *20 de locuri*). Es gramática mal
puesta, no un crash — CMP cae a `other` cuando la categoría pedida no está.

## Doctrina violada

Ninguna escrita. `CLAUDE.md` exige que la *key* esté en los 9 locales, y eso ya se cumple y se
vigila. Nada dice que un plural deba declarar las categorías **de su idioma**, que es lo que
distingue una traducción de un calco del inglés.

## Diseño (propuesto, sin decidir)

Extender `LocaleParityGuardrailTest` con una tabla `locale → categorías CLDR requeridas` (PL:
one/few/many/other · RO: one/few/other · el resto: one/other) y afirmar que cada `<plurals>` declara
las suyas. La tabla es pequeña y cerrada: son 9 idiomas fijos, no hace falta traerse ICU.

⚠️ Antes de implementarlo, **medir** que CMP 1.12 resuelve las categorías con las reglas CLDR del
idioma y no con las del inglés — hay `plural/CLDRPluralRuleLists.kt` en el jar de
`components-resources`, así que la pinta es buena, pero eso es una pinta, no una medición.

## Criterio de éxito

- Las 4 filas de la tabla declaran sus categorías, con traducción real (no la forma de `other`
  copiada).
- El guardarraíl falla si un plural nuevo llega a PL/RO con solo `one`/`other`, **y se ha visto
  fallar**.

## Por qué no se hizo en su ticket de origen

Aquel arreglaba un fallo de **existencia** (la key no está). Este es de **categoría** (la key está,
la gramática no). Meterlos juntos habría mezclado un bug medido con un barrido de traducción que
necesita decidir 4 frases nuevas en dos idiomas que no hablamos.
