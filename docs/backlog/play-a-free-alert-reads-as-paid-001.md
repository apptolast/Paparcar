# PLAY-A-FREE-ALERT-READS-AS-PAID-001 · la ficha vendía como Plus un aviso que ya es gratis

**Estado:** ✅ **Done** (27-10-2026) · rama `fix/PLAY-A-FREE-ALERT-READS-AS-PAID-001-listing-150` ·
worktree `../Paparcar-listing` — ambos se retiran al cerrar.

⏳ **Queda del user, en el Console**: pegar los nueve idiomas desde `paste/`. Después,
`python tools/release/cut.py --phases verify` lo comprueba contra la tienda en 30 s.

## Problema

Es el movimiento de la ficha a **1.5.0 (vc 16)**, que está en producción — verificado el 27-10
contra la Play Developer API, no deducido. Pero lo que lo hace urgente no es una función nueva: es
que **una frase publicada se ha vuelto falsa**. El bloque de Plus dice hoy en la tienda:

> *Gratis, tú preguntas y la app responde. Con Plus, la app vigila mientras tú no miras:*
> • Avisos cuando baja el precio en una gasolinera que sigues.

Desde `FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001`, que viaja **en 1.5.0**, el plan gratuito recibe avisos
de gasolinera: lo que sube o baja a su alrededor, **por notificación y con la app cerrada**. Así que
ese encabezado cobra por un trabajo que la app ya hace gratis, y un usuario free que lea la ficha
concluye que no tiene avisos de combustible — teniéndolos.

📌 Y el dato que más duele: **la web lo decía bien desde el 14-10**, en los dos idiomas
(*«Lo de alrededor es gratis. Lo que tú eliges, Plus»*, con sus dos avisos separados). La tienda era
la única superficie que seguía mintiendo, trece días.

## Doctrina violada

⛔ **La ficha describe el build PUBLICADO** (`docs/release/play-listing/README.md`). Aquí no es que
la ficha se quede corta: es que **describe mal lo que la gente ya tiene instalado**, que es peor que
no nombrar una función nueva.

Y una segunda, la que explica por qué nadie lo vio: *la web es un consumidor más de la verdad del
producto* obliga a mover `web/content/{es,en}.json` en la misma tarea que el producto — y se hizo.
**La ficha de Play no estaba en esa lista de consumidores.** Un cambio de marco público entró en la
web y no en la tienda.

## Diseño — tres reescrituras, las mismas en nueve idiomas

| Dónde | Antes | Ahora |
|---|---|---|
| Bloque de gasolineras | «Todo gratis: **ver** lo que tienes alrededor nunca está detrás de una suscripción» | «Todo gratis, **avisos incluidos**: lo que sube o baja a tu alrededor te llega con la app cerrada» |
| Encabezado de Plus | «Gratis, tú preguntas y la app responde. Con Plus, la app vigila mientras tú no miras» | «Lo que tienes alrededor es gratis. Plus vigila lo que tú eliges, y lo recuerda» |
| Bullet de Plus | «Avisos cuando baja el precio en una gasolinera que sigues» | «La gasolinera que sigues, vigilada esté donde esté, y 30 días de histórico» |

El encabezado nuevo es el marco que ya usa `paparcar.com`; y el bullet pasa a vender lo que Plus
vende de verdad ahí: el **sujeto** (la gasolinera que tú elegiste, vigilada sin pasar por delante) y
la **memoria** (su serie de 30 días), no el aviso.

⛔ **El carril free de avisos de PLAZA no se toca, y la asimetría es deliberada.** El aviso free de
gasolinera es una notificación con la app cerrada; el de plaza sigue siendo in-app y sólo con el mapa
delante. `CLAUDE.md` lo tiene asumido y prohíbe unificarlos «por coherencia» sin el user, así que la
frase de «avisos incluidos» vive en el bloque de **gasolineras** y el bullet de Plus de la plaza en
tu zona se queda **intacto**.

## El tope de 4000 — medido ANTES, y esta vez salió gratis

La regla que dejó 1.4.0 es *medir en FR y DE, nunca en en-US*. Medido antes de escribir: **fr-FR 14
libres, de-DE 9**. Añadir una frase era imposible tal cual.

No hubo que recortar ninguna promesa: **el encabezado nuevo de Plus es más corto que el que
sustituye en los nueve idiomas** —decir «lo de alrededor es gratis, lo que eliges es Plus» cuesta
menos que explicar quién trabaja— y eso paga la ampliación de la línea de gratis.

| | fr-FR | de-DE | nl-NL | it-IT | es-ES | pt-PT | ro | pl-PL | en-US |
|---|---|---|---|---|---|---|---|---|---|
| libres antes | 14 | 9 | 28 | 73 | 96 | 95 | 121 | 167 | 202 |
| **libres ahora** | **9** | **18** | **18** | **48** | **70** | **70** | **139** | **149** | **180** |

El alemán **respira más que antes**. El techo sigue siendo fr-FR con 9, y nl-NL se le ha puesto al
lado con 18: la próxima función que se quiera nombrar vuelve a exigir condensar antes de escribir.

## Criterio de éxito

- [x] `published-listing.txt` apunta a `LISTING-v1.5.0.md` y `paste/` regenerado — banner incluido
      (*«GENERADO … desde LISTING-v1.5.0.md»*)
- [x] **El guard falsificado**: con el puntero en 1.5.0 y `paste/` restaurado al de 1.4.0,
      `PlayListingPasteParityGuardrailTest` se puso **rojo en 2 de sus 4 chequeos**, y con el
      mensaje correcto — *«play-en-US.txt does not say it was generated from LISTING-v1.5.0.md —
      either it was hand-edited … or the pointer moved»*. Tras regenerar, verde

- [x] los nueve idiomas **bajo 4000**, medidos con el mismo parser que lee los pegables
- [x] `./gradlew testAll --rerun-tasks` verde

## ⛔ Trampa medida: escribir el fichero desde Python lo dejó en CRLF, y el guard lee crudo

La primera falsificación **dio un rojo que me creí, y era mentira**. Tres chequeos en rojo diciendo
*«locales parsed out of LISTING-v1.5.0.md expected: 9 but was: 0»* — cero bloques, no un desajuste
de copy.

La causa no tenía nada que ver con la ficha. En Windows, `pathlib.Path.write_text` convierte los
saltos a **CRLF**; y al releer, Python los vuelve a traducir, así que mi propio parser veía los nueve
bloques. La JVM lee el fichero crudo: el regex del guard pide el cierre de la valla de código
inmediatamente después de un salto, y con CRLF se encuentra un retorno de carro donde espera el
salto. Cero coincidencias.

📌 Dos cosas que recordar, porque volverán:

- **Un guard de Kotlin que parsea texto no tolera CRLF**, y git normaliza **al commitear**: el
  fichero del repo queda bien y el del árbol de trabajo —que es el que el test lee— no. Al escribir
  ficheros del repo desde Python en esta máquina, `newline="\n"` o normalizar después.
- Y la lección de método: **un rojo no es una falsificación**. Hay que leer el mensaje, no el color.
  El mío decía «0 locales», que no es lo que falla cuando `paste/` está viejo — el mensaje correcto
  nombra el banner y el puntero, y es el que salió al repetirla bien.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `published-listing.txt` → `paste/` | ✅ movido y regenerado. Es la única cadena viva; `LISTING-v1.4.0.md` queda histórico |
| `web/content/{es,en}.json` | ✅ **ya estaba bien**: dice el marco nuevo y separa los dos avisos de gasolinera desde el 14-10. Nada que tocar — y es la prueba de que el agujero era la ficha, no el producto |
| Bullet de Plus de la plaza en tu zona | ✅ intacto a propósito: ese aviso **sigue siendo Plus** |
| Bloque de ubicación en segundo plano | ✅ no se toca. Es texto que Play exige |
| `LISTING.md`, `LISTING-i18n.md`, `LISTING-v1.3.0.md`, `LISTING-v1.4.0.md` | ✅ históricos, auditados como tal en 1.4.0 |

## Follow-ups

- 🔵 **La web no nombra la mitad de la memoria.** Su bullet de Plus es *«Aviso cuando cambia el
  precio de una gasolinera que sigues, esté donde esté»*: el sujeto sí, los **30 días de histórico**
  no. No es falso, es incompleto, y ahora la ficha lo dice y la web no. Sin hacer: es ampliar el
  encargo, y el user decide si la web gana esa frase.
- 🔵 **La ficha de Play debería estar en la tabla de consumidores de `CLAUDE.md`** («La web es un
  consumidor más de la verdad del producto»). Hoy esa tabla manda mover la web cuando cambia un
  límite del plan free o una feature que nombra, y la ficha —que promete lo mismo a más gente— no
  aparece. Es justo el agujero por el que pasó esto.
