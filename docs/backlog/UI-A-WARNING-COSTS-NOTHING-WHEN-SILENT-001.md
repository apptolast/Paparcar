# UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001 · La cápsula de precisión deja de pagar alquiler en la cabecera

**Estado:** ✅ Done — en master (10-09-2026), verificado en device.

## Problema

Petición del user: «el chip de GPS que tenemos en Home, arriba a la izquierda, hace bastante bulto
junto a los demás componentes como el de zonas; ¿podríamos ponerlo abajo a la izquierda? te doy vía
libre para gestionar los FABs en general».

Detrás del bulto hay dos cosas distintas, y solo una se ve:

1. **Un carril de 34 dp reservado a tiempo completo para un mensaje que casi nunca habla.** Lo
   reservó [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] a propósito, y era lo correcto entonces: la
   cápsula entraba desde un `AnimatedVisibility` de altura cero, así que aparecer *empujaba* la
   cabecera entera hacia abajo y desaparecer la subía. Reservar la banda mató el empujón, pero pagó
   por él con 34 dp permanentes de cabecera — encima de la fila de búsqueda (56) y de los chips de
   zona (34 + 16 de aire). Tres carriles, uno de ellos vacío el 95 % del tiempo.

2. **Está condicionada al revés: se esconde justo cuando importa.** La cabecera se pinta con
   `overlayVisible` = hoja en peek ∧ nada seleccionado ∧ modo `Browse`. O sea que la cápsula
   desaparece en cuanto entras en modo pin — colocar un aparcamiento, avisar de una plaza, dibujar
   una zona. Un fix de ±80 m es *exactamente* el dato que decide si ese pin vale algo, y es el único
   momento en que no se puede leer. Los FABs de cámara no tienen ese problema: se pintan con
   `fabsVisible` = hoja en peek, y sobreviven a todos los modos a propósito
   («remain reachable in any modal state»).

Y un tercero, medido leyendo el `when`: con `accuracy == 0f` (fix inválido / sin dato de precisión)
la escalera caía en la rama `< 50f` → **cápsula ámbar por un cero**. Un aviso que no mide nada.

## Doctrina violada

- **«La altura se reserva por el caso peor»** ([UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001],
  [UI-STATS-HEADER-WRAPS-THE-WRONG-SIDE-001]) es la regla correcta para un carril *dentro de una
  columna que se mide*. La cápsula no tenía por qué estar en esa columna: en una capa flotante sobre
  el mapa, aparecer no empuja a nadie, y entonces no hay nada que reservar. El coste fijo salió de
  meterla en la caja equivocada, no de la regla.
- **Sistemas, no parches**: al bajarla nacía un TERCER `14.dp` suelto (cabecera, columna de FABs, y
  el nuevo carril). Es literalmente la trampa que cerró el ticket anterior — «tres 14 que coinciden
  no son una alineación, son una coincidencia». El inset del vecindario flotante pasa a vivir en
  UN sitio, con los tres consumidores leyéndolo.
- **Un nombre tiene que ser verdad**: `HomeGpsAccuracyBanner` en `sections/header/components/`. No
  es un banner (no ocupa el ancho, no está arriba) y ya no vive en la cabecera.

## Señales / datos disponibles

`HomeState.userGpsPoint.accuracy` (metros del último fix), ya proyectado a la capa de presentación.
Nada nuevo que medir ni que pedir al dominio: es una tarea de dónde se pinta y cuándo se calla.

## Diseño

**1 · La cápsula baja a la banda de los FABs (abajo a la izquierda).**
Mismo `align` + **el mismo lambda de offset** que la columna derecha, extraído a un solo sitio
(`fabBandOffset` en `HomeContent`): las dos capas flotantes ya no pueden derivar una respecto de la
otra porque no hay dos cuentas, hay una. Comparten suelo (borde inferior alineado), y suben y bajan
con el borde de la hoja igual que los FABs. La cabecera baja de tres carriles a dos.

**2 · Hereda la puerta de los FABs, no la de la cabecera.** `fabsVisible` en lugar de
`overlayVisible` → la cápsula se ve en modo pin, con una plaza seleccionada y durante el viaje.
Esto no es un efecto colateral de la mudanza: es la mitad del arreglo (problema 2).

**3 · Solo habla cuando el fix es malo de verdad (> 50 m).** Se retira el nivel ámbar (20–50 m),
decisión del user. El umbral queda uno, así que desaparecen el enum de tres niveles y la escalera
que hacía ámbar un cero: `accuracy > 50 m` o silencio. El `%1$d` de la copy no cambia — el string es
`GPS ±%1$dm` en los 9 locales, sin frase que ajustar.

**4 · Higiene de la columna derecha: la CAPA no es un control de cámara.** El toggle de gasolineras
comparte columna con coche / ruta / mi-ubicación con el mismo aire de 10 dp entre los cuatro, así que
se lee como un cuarto control de cámara. Pasa a ser su propio grupo, separado del trío por un hueco
mayor. Un solo `Column` anidado; el orden y la altura no cambian.

**5 · La invitación de zona respira.** Visto en device con el build de esta rama: sus dos líneas
(12sp en caja de 16dp = 32dp macizos) dejaban 5 dp arriba y abajo dentro de una píldora de 42, y a
ras de una forma con los extremos redondos eso se lee apretado. Sube a 7 dp → 46 dp. Solo lo paga
el estado EXPANDIDO — el padding vive dentro del `AnimatedVisibility` de la invitación, así que
colapsada sigue siendo el círculo exacto de `ZONE_CHIP_HEIGHT` sobre el que está construida la fila.

Fuera de alcance por decisión del user: el reparto de los dos toggles de CAPA en dos esquinas (tipo
de mapa arriba en la cabecera, gasolineras abajo en la columna). Queda apuntado en
`docs/backlog/ui-map-layer-toggles-live-in-two-corners-001.md`.

## Criterio de éxito

- La cabecera de Home tiene **dos** carriles; ninguna altura reservada para la cápsula.
- Con `accuracy = 30 m` la cápsula NO existe en pantalla; con `120 m` sale roja abajo a la izquierda.
- Con la hoja en peek y modo `AddingParking` activo, la cápsula sigue visible (antes desaparecía).
- Cápsula y FAB inferior comparten suelo y se mueven juntos al arrastrar la hoja hasta el ancla
  media.
- Galería del Dev Catalog en paridad: un solo nivel (mala) + su entrada/salida.
- `:shared:testDebugUnitTest` verde (incluye guardarraíles Konsist) + `compileMockDebugKotlin` +
  `compileProdDebugKotlin`.

## Verificación en device (Redmi `5f8991cb`, 10-09-2026)

APK `prodDebug` con sha256 comprobado en el propio móvil, arranque sin `FATAL`. Lo medido sobre las
capturas, no estimado a ojo:

- **La cápsula sale donde debe**: el fix estaba degradado de verdad (±92 m) y la píldora roja apareció
  abajo a la izquierda sin forzar nada.
- **Suelo compartido**: última fila roja de la cápsula en `y=1743`; borde inferior del FAB de
  mi-ubicación en `y=1744,5` (derivado de sus bounds de accesibilidad). 1,5 px de antialiasing — que
  es lo que garantiza el lambda único, no una coincidencia afortunada.
- **Los dos grupos de la columna**: centro a centro, ⛽→🚗 = 198 px y dentro del trío = 159,5 px. A
  2,75x son exactamente los 24 dp entre grupos y los 10 dp dentro.
- **El lado silencioso**: en una captura posterior el fix ya había recuperado, y la cápsula no está —
  sin hueco, sin carril vacío y sin que se mueva un pixel de la cabecera. Es el caso que justifica
  toda la tarea y el que nunca se ve en un screenshot preparado.
- **La invitación de zona** (punto 5): franja del chip `y=312..413` antes → `y=308..417` después.

## Consumidores auditados

`grep -rn "GpsAccuracyBanner|gpsAccuracy|HEADER_SIDE_INSET|end = 14.dp"` sobre `shared/src` +
`app/src` (excluido `domain/`, donde `gpsAccuracy` es otra cosa: el scoring de confianza de
detección, que no toca esta tarea):

| Sitio | Qué asumía | Cierre |
|---|---|---|
| `HomeSlices.kt` — `HomeHeaderSlice.gpsAccuracy` | la precisión es dato de CABECERA | movido a `HomeFabsSlice` |
| `HomeSlicesTest.kt:451` | proyecta al header slice | reescrito contra el fabs slice |
| `HomeHeaderSection.kt:110-123` | dueño del carril reservado | carril eliminado |
| `HomeHeaderSection.kt:175` — `HEADER_SIDE_INSET_DP` | inset privado de la cabecera | consume `MAP_FLOATING_SIDE_INSET_DP` |
| `HomeMapSection.kt:161` — `end = 14.dp` | literal suelto de la columna | idem |
| `HomeGpsAccuracyBanner.kt` | vive en `sections/header/components/`, se llama banner, 3 niveles | → `sections/map/components/HomeGpsAccuracyPill.kt`, 1 umbral |
| `StateGalleryScreen.kt:637-651` | dos niveles, «vive en la cabecera» | un nivel + nueva ubicación |
| `HomeScreen.kt:753-765` | el offset de la banda lo sabe solo la columna derecha | `fabBandOffset` compartido |
| `docs/detection/PARKING-DETECTION.md` | — | no menciona la cápsula (su `gpsAccuracy` es el del scoring) |
