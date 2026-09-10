# LEGAL-THE-APP-GREW-THREE-DATA-SOURCES-001 · la app habla con cinco terceros más que cuando se escribió lo que dice que hace

**Estado:** ✅ Done — en master dentro de `ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001` (mismo commit) por decisión del user el 10-09-2026 · ✅ **web DESPLEGADA el 10-09-2026**
(`firebase deploy --only hosting`, proyecto `pap-26`) y verificada EN VIVO contra `paparcar.com`, no
contra `pap-26.web.app`: fecha 10-09-2026 y las cinco filas nuevas presentes en las dos mitades.

Abierta al auditar, a petición del user, qué información quedó desfasada con F5, F6 y MONEY-001.
**Todo lo de abajo se verificó leyendo el código y el HTML publicado, no se dedujo.**

## Problema

El mismo desfase que el ticket de onboarding, en las dos superficies donde cuesta más caro: la
pantalla de licencias y la política de privacidad. La app creció tres fuentes de datos y una pasarela
de pago; lo que la app DICE de sí misma se quedó en la versión anterior.

### 1 · Atribución de fuentes — `LicensesScreen.kt` (`DATA_SOURCES`)

Tres filas: OpenVan.camp (CC BY 4.0), OpenStreetMap (ODbL 1.0), Ministerio para la Transición
Ecológica (datos abiertos). Dos cosas mal:

- **Faltaba `data.economie.gouv.fr` entera.** Francia entró en master (`37c4ef48`) y su feed no
  estaba atribuido en ningún sitio de la app. Es dato público del Estado francés: la Licence Ouverte
  exige citar la fuente. Era el único hallazgo de esta ficha que podía ser un **incumplimiento de
  licencia**, no solo una imprecisión.
- **La fila de OSM mentía por omisión**: su propósito decía *"Fuel stations and nearby places"*, y OSM
  sirve además **los parkings públicos** (`PARKINGS-ON-THE-MAP-001`) y la red viaria
  (`OverpassRoadNetworkDataSource`).

### 2 · Política de privacidad — `hosting/public/privacy-policy.html`

La tabla **§4 "Who we share it with"** tenía tres filas: otros usuarios, Google Firebase y Google
Maps / Play Services. Le faltaban **cuatro destinatarios que reciben tu posición aproximada** cada vez
que se abre un carril complementario o se mueve la cámara — espejos de Overpass, el Ministerio,
`data.economie.gouv.fr` y OpenVan — y un quinto que no es de ubicación: **RevenueCat**. Verificado en
`RevenueCatDataSourceImpl.logIn(userId)`: se le manda el **UID de la cuenta**. La política no
mencionaba ni compras ni suscripciones en ninguna línea, con Plus publicado desde MONEY-001.

## Qué se ha hecho

**App** (strings en los 9 locales, como manda la regla):
- Fila nueva `data.economie.gouv.fr` · *Licence Ouverte 2.0* · «Precios de surtidor en Francia».
  Se cita el **PORTAL**, no el ministerio detrás: es lo que un lector puede buscar y lo que cita la
  fórmula habitual «source :», el mismo criterio con el que las licencias se escriben como ellas se
  llaman.
- El propósito de OSM pasa a nombrar los cuatro sujetos que sirve: parkings, gasolineras, lugares y
  calles.
- Dos comentarios del fichero que contaban «tres fuentes, dos licencias obligan» quedaban falsos con
  la cuarta fila — corregidos, porque un comentario que miente cuesta una auditoría.

**Web** (`privacy-policy.html`, **las dos mitades, EN y ES**; fecha → 10-09-2026):
- **§2.8 nueva**, *Map queries and Plus subscription*: qué sale del móvil al preguntar por una zona
  («se envía la zona y nada más: ni cuenta, ni identificador, ni historial») y qué recibe RevenueCat.
  Sin jerga: ni bbox, ni Overpass, ni espejos.
- **§4**: cinco filas nuevas, cada una diciendo QUÉ recibe ese tercero, no solo que existe.
- **§5**: cuánto dura el estado de la suscripción, y que las consultas al mapa **no las guardamos
  nosotros** — se cachean en tu propio móvil (`CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001`).

Verificado: HTML equilibrado (18 `<tr>`, 20 `<ul>`, 51 `<li>`, sin etiquetas sueltas) y simétrico en
los dos idiomas; suite y ambos APK verdes.

## Lo que queda en tus manos

1. ~~Desplegar `paparcar.com`~~ — **hecho el 10-09-2026** a peticion del user (CLI; el
   `firebase_deploy` de MCP sigue siendo un no-op silencioso).
2. **Revisar el formulario Data Safety de Play** contra la §4 nueva (ver
   `ONB-DISCLOSURE-MATCHES-DATA-SAFETY-001`). Ninguno de los cinco terceros nuevos recibe datos que
   no estuvieran ya declarados como recogidos, pero el formulario pregunta también por *compartición*.
3. **Firmar la redacción legal** — pendiente de tu OK, y ya PUBLICADA: si quieres cambiar una
   palabra se edita y se vuelve a desplegar, que cuesta segundos. Lo que hay que leer son tres
   bloques: la §2.8 nueva, las cinco filas de §4 y la línea añadida a §5. El documento es tuyo.

## Follow-up deliberado, FUERA de esta tarea

⛔ **`DATA_SOURCES` sigue sin testigo.** Su KDoc dice *«A dataset added to the app is added HERE in
the same task»* — y eso es exactamente lo que falló con Francia: una regla escrita en un comentario
no es un chequeo. Un guard de verdad exige acoplar cada credit a la constante `SOURCE` del feed
(`FranceFuelStationsSource.SOURCE = "fr-prix-carburants"`, `"es-minetur"`, `"osm"`), y hoy esa
convención **no la cumplen todos** (OpenVan no declara ninguna), así que un test montado sobre ella
daría falsa confianza justo en la fuente que no cubre. Es rediseño, no barrido: merece su propio
ticket y su decisión.
