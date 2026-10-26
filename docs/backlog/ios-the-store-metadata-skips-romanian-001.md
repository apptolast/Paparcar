# IOS-THE-STORE-METADATA-SKIPS-ROMANIAN-001 · la ficha de App Store se salta el rumano, en silencio

**Estado:** ⚪ Pendiente, sin rama · abierto el 26-10-2026 desde
`RELEASE-THE-CUT-MUST-BE-ONE-COMMAND-001`, que tropezó con el mismo invariante por el otro lado.

## Problema

`iosApp/fastlane/tools/metadata_from_play.py` genera la ficha de App Store a partir del copy de Play
que ya está en el repo. De los nueve idiomas, **el rumano no llega**, y falla por **dos** sitios
independientes — los dos medidos el 26-10-2026:

1. **El parser de las notas exige región.** `release_notes()` usa
   `r"<([a-z]{2}-[A-Z]{2})>\n(.*?)\n</\1>"`, y el rumano en
   `docs/release/play-listing/paste/release-notes-v1.5.0.txt` es `<ro>`, **sin región**. Medido
   sobre ese fichero: el fichero declara **9** locales y esa regex lee **8**.
   ```
   tags en el fichero : en-US es-ES it-IT pt-PT fr-FR de-DE nl-NL pl-PL ro
   lo que lee el script: en-US es-ES it-IT pt-PT fr-FR de-DE nl-NL pl-PL
   ```
2. **Busca un fichero de ficha que no existe.** Su tabla `LOCALES` mapea `"ro-RO" -> "ro"`, así que
   abre `paste/play-ro-RO.txt`; el fichero real es **`play-ro.txt`**. Lo dice el propio script al
   correrlo:
   ```
   $ python iosApp/fastlane/tools/metadata_from_play.py --check
   ...
     · ro-RO: falta play-ro-RO.txt
   ```
   Es el **único** locale que falla por fuente: los otros ocho solo salen «desactualizado», que es
   otra cosa (nadie regeneró tras mover la ficha a 1.4.0).

Consecuencia: `iosApp/fastlane/metadata/ro/` tiene sus nueve ficheros, pero **ninguno se puede
regenerar**. Quedan congelados en lo que se escribiera una vez, mientras los otros ocho idiomas sí
se refrescan — y `deliver` sube lo que haya en la carpeta sin preguntar de cuándo es.

## Doctrina violada

⛔ **`ro` es el código de Play y no lleva región** [PLAY-A-LOCALE-CODE-IS-PLAYS-NOT-ANDROIDS-001].
Ese ticket ya pagó este mismo error en las notas de Play (`ro-RO` no existía allí tampoco); aquí
sobrevivió en un consumidor que nadie barrió.

Y el patrón de fondo: *faltar en un locale no crashea, y por eso es peor*. Igual que en
[I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001], el fallo no para nada — el script **imprime
el aviso y sigue**, y la ficha se publica con un idioma viejo.

## Señales / datos disponibles

- `tools/release/cut.py` (fase `notes`) ya resuelve el invariante bien: regex
  `<([a-z]{2}(?:-[A-Z]{2})?)>` y la lista de los 9 locales de Play con `ro` a secas, con su test
  `test_fails_on_romanian_written_as_play_does_not`. Aquí hace falta la misma verdad, no otra copia.
- `metadata_from_play.py --check` ya distingue «falta la fuente» de «desactualizado»: el arreglo se
  puede verificar con el propio script.

## Diseño

Dos líneas de arreglo y **una decisión de sistema**, que es lo que de verdad pide este ticket:

- la regex pasa a aceptar locale sin región, y `LOCALES` mapea `"ro" -> "ro"`;
- ⛔ **los nueve códigos de locale de Play viven hoy en TRES sitios** —
  `docs/release/play-listing/tools/make_paste.py` (`LOCALES`), `tools/release/cut.py` (`LOCALES`) y
  esta tabla de `metadata_from_play.py`— y el rumano es justo donde los tres se desvían. Mientras
  sean tres listas, un cuarto consumidor volverá a escribir `ro-RO`. El arreglo bueno es **un solo
  sitio** del que los tres lean.

## Criterio de éxito

- [ ] `python iosApp/fastlane/tools/metadata_from_play.py --check` no reporta nada de `ro`, y
      `metadata/ro/release_notes.txt` + `description.txt` se regeneran con el copy de la versión en
      curso
- [ ] un test que **falsifique** el caso: con `<ro>` en las notas, el parser lo lee; con `ro-RO`,
      avisa. Sin él esto vuelve en el siguiente idioma sin región
- [ ] los 9 códigos de Play se leen de **un** sitio, y los otros dos consumidores pasan a citarlo

## Prioridad — depende de una cosa que el user tiene que confirmar

⚠️ El 26-10-2026 el user dijo *«iOS en principio no tenemos consola»*. El repo dice otra cosa, y por
eso conviene zanjarlo antes de trabajar aquí: existen `iosApp/fastlane/{Appfile,Fastfile,Matchfile,
Snapfile}`, `metadata/` con **10** carpetas de idioma, `docs/release/IOS-FASTLANE-SETUP.md`, y los
comentarios del propio script citan medidas tomadas **contra la API de App Store Connect** (que
`en-GB` es el idioma principal de la ficha, comprobado el 18-09-2026) y una **subida real con
`deliver`** el 19-09-2026 que murió por no declarar categorías.

- Si **hay** cuenta de App Store Connect: esto es un idioma de la ficha que se publica viejo → se
  arregla antes de la siguiente subida.
- Si **no la hay** (o se dejó caer): es un bug latente, y lo que vale la pena es la decisión de
  sistema (una sola lista de locales), porque esa muerde también en Play.
