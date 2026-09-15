# UI-MAP-LAYER-TOGGLES-LIVE-IN-TWO-CORNERS-001 · Las dos capas del mapa se piden desde esquinas distintas

**Estado:** ✅ **Cerrado sin rama propia — lo resolvió `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001`**
(`b967323b`, 10-09) y lo remató `HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001`
(`fc683c38`). Ya no hay dos esquinas porque **ya no hay dos controles**: la banda de FABs perdió el
grupo de capas y el mapa pinta el complemento del CARRIL que miras en la sheet. Verificado en código
el 15-09 — `HomeMapFab.kt` lo dice en su cabecera (*"It used to carry a second group… That group is
gone"*) y no queda ningún toggle de gasolineras.
La cabecera conserva el `MapTypeToggle`, pero ese no cambia **qué** se pinta sino **sobre qué** —que
era justo la distinción que este ticket pedía.

> Estado original: 🟡 Abierto, sin empezar · descubierto durante
> [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001] y dejado fuera de alcance por decisión del user.

## Problema

Home tiene exactamente dos controles que cambian **qué se pinta en el mapa**, y están en esquinas
opuestas, cada uno en compañía de cosas de otra familia:

| Control | Dónde | Con quién comparte sitio |
|---|---|---|
| Tipo de mapa (plano ↔ híbrido) | cabecera, arriba a la derecha | el buscador |
| Capa de gasolineras | banda de FABs, abajo a la derecha | los controles de cámara |

Ninguno de los dos está donde está por diseño: el de gasolineras acabó abajo porque **medido**, un
tercer círculo de 56 dp en la fila de cabecera estrujaba el buscador a dos líneas
([UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]). O sea que la fila de arriba no puede crecer, y la
consecuencia fue partir la familia en vez de mudarla entera.

UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001 cerró la mitad barata del asunto: en la columna derecha
el toggle de gasolineras dejó de compartir el ritmo de 10 dp con el trío de cámara y pasa a ser su
propio grupo. Eso dice «no soy un control de cámara»; **no** dice dónde se piden las capas.

## Doctrina en juego

- *Un mismo tipo de acción se pide en un mismo sitio.* Las dos son la misma pregunta: qué quiero ver
  encima del mapa.
- ⛔ Contra la opción del popup pesa una decisión **escrita a propósito** en el KDoc de
  `MapTypeToggle`: «no hay tercera opción y no hay popup — el botón muestra el estilo en el que
  ESTÁS, y tocarlo lo cambia; cambiar cuesta un toque». Fundirlas en un menú convierte un toque en
  dos. Revertirlo exige argumento, no gusto.

## Opciones sobre la mesa (planteadas al user el 10-09-2026)

1. **Bajar el tipo de mapa a la columna**, junto al de gasolineras → el buscador queda a ancho
   completo (hoy va justo) y las capas quedan juntas. Contra: con coche aparcado y GPS, la columna
   llega a 5 círculos ≈ 320 dp de pared en el borde derecho — el bulto se muda, no desaparece.
2. **Un único botón «Capas»** que abre las dos (y las que vengan) → columna más corta, buscador a
   ancho completo, escala. Contra: dos toques donde hoy hay uno, y revierte la decisión citada.
3. **Dejarlo así**, asumiendo que son dos controles y no una familia.

El user eligió no decidirlo dentro de aquella tarea. Sin datos de uso (¿cuántas veces al día se
cambia de tipo de mapa?) la elección entre 1 y 2 es preferencia, y por eso esto no se ejecuta solo.

## Criterio de éxito

Que un usuario que quiere cambiar lo que ve en el mapa mire a **un** sitio. Y que la elección quede
escrita con su porqué en el KDoc del control resultante, sustituyendo a la frase que hoy prohíbe el
popup — no dejándola contradicha.
