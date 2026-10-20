# AUTH-THE-TAGLINE-MUST-SPEAK-THE-PRODUCTS-VOCABULARY-001 · el tagline del acceso llamaba «tu plaza» a lo tuyo

**Estado:** ✅ Done

## Problema

El user lo ve al mirar la cabecera de acceso ya arreglada: *«el subtítulo aún queda muy pequeño,
busca un slogan mejor, más acorde con la marca ahora, para que quede algo más largo»*. El motivo
visual es real —el imagotipo ocupa 240 dp y debajo había una línea corta— pero al leerlo aparecen
tres cosas peores que la longitud.

**«Encuentra tu plaza, comparte tu espacio»** / *«Find your spot, share your space»*:

1. ⛔ **Rompe el vocabulario del propio proyecto.** CLAUDE.md es explícito: *plaza* es de la
   comunidad, *aparcamiento* es tuyo. El tagline dice «**tu** plaza» —la palabra de lo ajeno para lo
   tuyo— que es exactamente el error que [COPY-SPOT-IS-NOT-A-PARKING-001] existe para cerrar.
2. ⛔ **Inventa una tercera palabra.** «Espacio» / «space» no está en la tabla y nombra lo mismo que
   «plaza», así que el usuario lee dos nombres para una cosa en siete palabras.
3. ⛔ **Es imperativo: le pide trabajo al usuario.** «Encuentra», «comparte». El eje de la marca es
   justo el contrario —*tú preguntas y la app responde; la app te avisa y recuerda por ti*— y el
   producto entero pivotó a que la detección trabaje sola. El tagline vendía la versión del producto
   en la que el usuario hace todo a mano.

Y de rebote, **ni siquiera era consistente en registro**: el tagline alemán trataba de *Sie*
(«Finden Sie Ihren Platz») mientras el resto del copy alemán tutea, y el portugués decía *vaga*
donde la tabla de vocabulario del proyecto dice *lugar*.

## Doctrina violada

[COPY-SPOT-IS-NOT-A-PARKING-001] en los dos sentidos: usar la palabra comunitaria para lo propio, y
añadir un sinónimo para un concepto que ya tiene nombre.

## Diseño

Elegido por el user entre tres opciones:

> **«Recordamos dónde aparcaste. La plaza que dejas, otro la encuentra.»**
> *«We remember where you parked. The spot you leave, someone else finds.»*

Por qué éste:

- **Dos tiempos, las dos mitades del producto**: lo que la app hace por ti, y lo que tu salida le da
  a otro. El anterior sólo contaba la mitad comunitaria.
- **Vocabulario correcto y a la vez**: *aparcaste* (lo tuyo, verbo de tu sesión) y *la plaza que
  dejas* (lo comunitario). Es además la fórmula que ya usa `permissions_subtitle`, que llevaba razón
  desde el principio.
- **No pide nada.** El sujeto es la app.
- **«Recordamos», no «paparcar recuerda»**: el logo está justo encima diciendo el nombre, así que la
  primera persona lo toma como sujeto y evita repetirlo dos centímetros más abajo.

⛔ **El registro de cada idioma NO se elige aquí**: se toma de `permissions_subtitle`, que es el
string hermano —misma promesa, misma voz— y el que marca cómo habla la app hoy. Por eso el alemán
pasa a tutear y el portugués a *lugar*: no es una decisión nueva, es dejar de ser la excepción.

## Criterio de éxito

- Los 9 locales, con el registro y el sustantivo de su `permissions_subtitle`.
- Se ve en device, en dos líneas bajo el imagotipo, sin cortes raros.
- `./gradlew testAll` verde.

## Consumidores auditados

`auth_header_tagline` tiene **un solo call site**: `PaparcarAuthHeader`. La web y la ficha de Play
tienen sus propios lemas y no leen esta key — comprobado con `grep`.

## Verificación

- Los **9 locales** escritos, 66-89 caracteres cada uno; apóstrofos **crudos y rectos** (Compose
  Resources no desescapa `'`, y el resto del francés de la app usa el recto).
- **Visto en device**: dos líneas bajo el imagotipo, alineadas con él, sin cortes raros.
- `./gradlew testAll` + ambos flavors verde.

⚠️ **El script de sustitución reventó a mitad la primera vez** y conviene que quede escrito: un
`print` del TEXTO mata el proceso con `UnicodeEncodeError` en la consola cp1252 de Windows al llegar
al polaco — **después** de haber escrito siete ficheros, que es la peor forma de fallar. Es la misma
trampa que `export_surfaces.py` ya documenta para los emoji. Se imprime el locale y la longitud, no
el texto.
