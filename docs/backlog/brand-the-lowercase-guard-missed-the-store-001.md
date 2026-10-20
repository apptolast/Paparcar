# BRAND-THE-LOWERCASE-GUARD-MISSED-THE-STORE-001 · el guard de la minúscula no miraba la tienda

**Estado:** ✅ Done

## Problema

Lo encontró **renderizar el banner y mirarlo**, no razonarlo. Al re-generar el gráfico de funciones
de Play con el imagotipo nuevo, el logo salía bien y debajo ponía:

> Park and forget it. **Paparcar** remembers.

[BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001] barrió 808 ocurrencias y **no llegó a los dos HTML de
`play-listing/assets/`**: `feature.html` (10) y `screenshot-frame.html` (18). Son los dos ficheros
que dibujan lo que ve alguien en la ficha de Play **antes** de instalar — o sea, lo primero que lee
de la marca.

## Doctrina violada

La regla que ese mismo ticket acababa de crear: *el nombre de la marca se escribe `paparcar`, en
todo texto que lea un usuario*. Un banner de tienda es texto que lee un usuario.

## El agujero, que es lo que de verdad hay que arreglar

`BrandNameLowercaseGuardrailTest` mira los dos árboles de strings, el `android:label`, el copy de la
web y las páginas publicadas. **No mira la tienda.** Así que el barrido podía dejarse 28
ocurrencias y la suite salía verde.

Y no es casualidad que fuese ahí: los dos HTML **no son plantillas**, llevan su copy dentro, y por
eso quedan fuera de todo lo que parece "copy" cuando uno piensa en dónde buscar. Es el mismo sitio
donde ya se escondió el dibujo de la marca —`feature.html` tenía los paths a mano— y el mismo donde
[PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001] encontró el nombre de la app duplicado.

⚠️ **Un guard que no cubre una superficie no "se olvidó" de ella: declara que no existe.** Por eso
el arreglo no es sustituir 28 cadenas, es que el guard lea la tienda y falle si vuelve a pasar.

## Diseño

1. **El copy**: `Paparcar` → `paparcar` en los valores de los dos HTML (los 9 idiomas de cada uno).
   Los comentarios no entran, igual que en el barrido anterior.
2. **El guard crece**: un cuarto chequeo que lee `docs/release/play-listing/assets/*.html`, la ficha
   VIVA que nombra `published-listing.txt` y los `paste/*.txt`. Con el mismo `\bPaparcar\b` que ya
   deja fuera los identificadores.
3. **Re-renderizar** el banner por idioma; el PNG EN es el único versionado.

## Fuera de alcance, dicho

Los **72 PNG de capturas** que salen de `screenshot-frame.html` no se re-generan aquí: necesitan los
raws de la sesión de Play y además ya estaban caducados por el traslado del mundo mock a Madrid
[MOCK-THE-DEMO-WORLD-MOVES-TO-SPAIN-001]. Lo que esta tarea garantiza es que **el molde** ya dice
`paparcar`, así que el próximo render sale bien.

## Criterio de éxito

- Cero `Paparcar` en los dos HTML y en la ficha viva; el guard lo prueba y se **falsifica**.
- `./gradlew testAll` verde.
- El banner re-renderizado, mirado, dice `paparcar`.

## Consumidores auditados

| Consumidor | Veredicto |
|---|---|
| `assets/feature.html` | **cerrado** — 10, los 9 taglines del banner más el visible |
| `assets/screenshot-frame.html` | **cerrado** — 18, los titulares EN/ES de las capturas |
| `LISTING-v1.4.0.md` (la viva) + `paste/*.txt` | **ya estaban** — los barrió el ticket anterior |
| `LISTING.md` · `LISTING-i18n.md` · `LISTING-v1.3.0.md` | **exentos** — fichas retiradas, son el acta |
| Comentarios de los dos HTML | **exentos** — hablan del proyecto |
| Los 72 PNG de capturas | **fuera de alcance, dicho arriba** — el molde ya dice `paparcar` |

## Verificación

- **28 ocurrencias** sustituidas, sólo en valores; los comentarios se quedan.
- **El chequeo nuevo, FALSIFICADO**: devolver la mayúscula a `feature.html` y a un `paste/*.txt`
  lo pone en rojo; restaurado, verde. Lee además la ficha VIVA a través de
  `published-listing.txt`, no por el nombre del fichero — que es el error que ya costeó un rojo
  en el ticket anterior.
- **Banner re-renderizado** en los 9 idiomas y MIRADO: dice `paparcar remembers`, y con el margen
  ya recortado el logo alinea con el tagline de debajo.
- `./gradlew testAll` + ambos flavors verde.
