# PLAY-A-LISTING-MUST-MATCH-THE-BUILD-IN-PRODUCTION-001 · lo que se pega en el Console tiene que ser el copy vigente

**Estado:** ✅ Done (código y docs) · ⏳ queda **pegar en el Console** las 9 descripciones completas
· ⚠️ **y desde el 13-10 hay más que pegar**: con 1.4.0 publicada, la ficha debe moverse a describir
ese build (widget, gálibo, acierto), lo que cambia la fuente de la que se pega →
[PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001](play-the-listing-moves-to-the-published-build-001.md)
y confirmar la breve — eso no lo puede hacer el repo.

## Problema

La ficha viva en Play (1.3.0, vc 14, en producción) tiene el paso 1 de «CÓMO FUNCIONA» **viejo**:

> 1. Aparcas. Paparcar lo detecta y guarda el sitio. **También puedes marcarlo a mano con un toque.**

Ese «también» lee como un extra opcional, y la verdad es la contraria para el carril Coordinator:
sin Bluetooth **el primer aparcamiento lo tienes que marcar tú**, o no hay nada que vigilar ni que
liberar. `72e78f9bd` (25-09) lo corrigió en los 9 idiomas de `LISTING-v1.3.0.md` — y **nunca se
pegó en el Console**, porque nada avisa de que el copy corregido no ha salido del repo.

Debajo hay un defecto mayor, y es el que lo hizo posible: **`paste/*.txt` es la ficha del 29-08**,
dos generaciones atrás. Sigue diciendo *«Todos tus coches en un mismo garaje»* — exactamente la
promesa que `LISTING-v1.3.0.md` documenta como FALSA desde `MONEY-001` (`FREE_OWN_VEHICLE_LIMIT = 1`).
Esa línea **no** está viva en el Console (se pegó desde el `.md`), pero el fichero que existe para
ser pegado la lleva puesta: el siguiente que pegue desde `paste/` mete en producción una promesa que
se topa en un coche.

Verificado el 27-09 contra el texto real del Console (pegado por el user): la descripción completa
es la de v1.3.0 en todo menos el paso 1 — «Cada coche con su tamaño, su color y su Bluetooth» ya
está, así que el garaje no llegó a salir.

## Doctrina violada

**Sistemas, no parches**, en su forma de duplicación: el mismo copy vive en dos sitios
(`LISTING-v1.3.0.md` y `paste/`) y **nada los compara**, así que uno se quedó atrás sin que nadie lo
notara durante 29 días. Es la misma familia que [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001], que
ya se resolvió con el testigo correcto: `WebSiteParityGuardrailTest` compara la salida construida
contra el copy. Aquí falta ese testigo.

Y una segunda: **la ficha describe el build PUBLICADO, no master.** Desde el corte de 1.3.0 (17-09)
hay >100 commits con features visibles —widget del launcher, gálibo del coche contra el del parking,
acierto de la detección, el banner in-app— y **ninguna está en producción**. Anunciarlas hoy es
prometer lo que no se instala.

## Señales / datos disponibles

- `LISTING-v1.3.0.md` es la fuente de verdad del copy: 9 bloques de descripción completa en fences,
  tabla de descripción breve (con DOS variantes y sin registro de cuál se eligió).
- Los nombres de app estaban dispersos: en-US y es-ES en `LISTING.md`, los otros 7 en
  `LISTING-i18n.md`. Un generador no puede leer tres ficheros para armar uno.
- El bloque de Plus de la ficha es **correcto para 1.3.0**, comprobado uno a uno:
  - «Avisos cuando se libera una plaza en tu zona, con la app cerrada» → Plus, sí: el interruptor
    por zona (`HomeViewModel` `SetZoneNotify`) y el maestro de Ajustes
    (`SettingsViewModel` `ToggleZoneAlertsNotif`) abren el paywall siendo free, y
    `Zone.notifyOnSpot` nace en `false`.
  - «Avisos cuando baja el precio en una gasolinera que sigues» → implementado y Plus:
    `FuelPriceWatchWorker` + `RunFuelPriceWatchUseCase` (corta con `Skipped("not-plus")`) +
    `EvaluateCheaperStationNearbyUseCase`, mergeado el **15-09**, antes del corte.
    ⚠️ El 🔵 «en curso» de `plus-alerts-and-tiers-001.md` estaba desfasado y me hizo darlo por no
    implementado en la primera pasada.

## Diseño

**Un solo sitio tiene el copy, y `paste/` se DERIVA de él.**

1. `LISTING-v1.3.0.md` recoge también los **9 nombres de app** (consolidados desde `LISTING.md` y
   `LISTING-i18n.md`, con el aviso de it-IT a 30/30 intacto) y **declara qué variante de descripción
   breve es la vigente**, que hasta hoy no estaba escrito en ningún sitio.
2. `tools/make_paste.py` genera los 9 `paste/play-<locale>.txt` y `paste/play-listing-import.md`
   desde ese único fichero. Nadie vuelve a copiar copy a mano.
3. `PlayListingPasteParityGuardrailTest` compara lo commiteado en `paste/` contra lo que el `.md`
   dice, y **falla** si divergen. Es el testigo que faltaba: sin él, esto se repite.

⛔ **Lo que este ticket NO hace**: añadir a la ficha el widget, el gálibo ni el acierto de la
detección. Son posteriores al 17-09, no están en producción, y entran en la pasada de ficha de
1.4.0 — con sus notas de versión.

## Criterio de éxito

- `paste/` regenerado lleva el paso 1 corregido en los 9 idiomas y ni rastro de la promesa del garaje.
- El guard falla si alguien edita el `.md` y no regenera (falsificado a mano).
- El user tiene el texto exacto que hay que sustituir en el Console, por idioma.

## Consumidores auditados

| Sitio que repite el copy de la ficha | Estado |
|---|---|
| `paste/play-*.txt` (9) | ✅ generados desde el `.md` |
| `paste/play-listing-import.md` | ✅ generado |
| `LISTING.md` / `LISTING-i18n.md` (razonamiento + nombres) | ✅ los nombres se consolidan; el razonamiento se queda donde está |
| `README.md` (estado de los campos) | ✅ dice qué está vivo y qué falta pegar |
| `web/content/{es,en}.json` | ✅ auditado: no repite el paso 1 ni la línea del garaje, y `WebSiteParityGuardrailTest` ya lo vigila |
| ficha del Console | ⏳ **paso del user**: pegar el paso 1 corregido en los 9 idiomas |

---

## Verificación (27-09)

- `./gradlew testAll --rerun-tasks` → **3.796 tests, 376 clases, 0 fallos**.
  ⚠️ Sin `--rerun-tasks` la suite sale verde en 24 s **sin ejecutar nada**: este guard lee ficheros y
  no toca bytecode, así que Gradle lo da por UP-TO-DATE. Es la trampa que ya avisa `CLAUDE.md`, y
  aquí muerde igual.
- `PlayListingPasteParityGuardrailTest` falsificado **tres veces**, una por chequeo:
  1. editar un pegable a mano (`Spritpreisen` → `Benzinpreisen` en de-DE) → falla la paridad;
  2. borrar el banner de generado de it-IT → falla, diciendo que se editó a mano;
  3. renombrar la cabecera de la descripción completa en el `.md` → **fallan los tres tests**,
     incluido el de suelos, que es el que existe para que un parser que no encuentra nada no deje
     la clase en verde.
- `node web/build.mjs --check` limpio, y la web auditada a mano: ya nombra el carril BT
  («si tu coche no tiene Bluetooth, ese primer aparcamiento lo marcas tú») y no repite la línea del
  garaje, así que no entra en esta tarea.
- Los 9 pegables comprobados sin `\'` ni `%%`: son texto plano para el Console, no Compose
  Resources, pero la costumbre de escapar viaja con el copiar-pegar.

### Lo que se aprendió por el camino

⛔ **El generador escribió en su primera pasada exactamente el texto que venía a borrar.** `_section`
cortaba en la siguiente cabecera de nivel 2, y la variante DESCARTADA de la descripción breve es un
`### ` dentro de la misma sección: entraba en la tabla y ganaba por ser la última. Salió porque se
miraron los 9 ficheros generados uno a uno, no porque nada fallara — el generador no tenía forma de
saber que esa tabla no era suya. Ahora ambos, tool y guard, cortan en el **siguiente marcador**, y la
tabla descartada lleva su `<!-- paste-source: none -->`.

Es la misma lección que este ticket ya tenía escrita en el problema, aplicada a sí mismo: **dos
copias del mismo copy y nada que las compare**. Aquí las dos tablas estaban en el MISMO fichero y
ninguna decía cuál manda. Eso ya está escrito.
