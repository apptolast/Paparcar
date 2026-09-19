# MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001 · El coche aparcado dentro de una zona lleva su glifo

**Estado:** 🔵 En revisión · rama `feature/MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001` ·
worktree `../PaparcarKMP-issue32` · cierra la **issue #32**

## Problema

Pedido por el product owner (#32): cuando el coche está aparcado dentro del radio de una zona, los
iconos y el texto de la zona se quitan y el marcador del coche pasa a llevar, arriba a la derecha,
el icono de la zona y el candado si es privada — con el mismo diseño que las insignias de
interrogación y diana.

## Doctrina en juego

`UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001` modeló la esquina superior derecha como **excluyente**:
una sola palabra, y por eso `VehicleMarkerDoubt` es un enum y no dos booleanos. Añadir la zona ahí
reabre exactamente ese conflicto, así que **no se apila**:

- **Sin duda** → la zona toma la esquina derecha, que es donde se pidió.
- **Con duda** (`?` o diana) → la duda se queda la derecha —dice si el pin es real siquiera, que
  manda sobre dónde está— y la zona se refleja a la esquina **izquierda**.
- Nunca se pierde información, y nunca hay dos discos en un mismo rincón de 16 unidades, que a
  tamaño de mapa es un borrón.

## Diseño

- **Veredicto puro** `domain/zone/ZoneContainment.kt`: `List<Zone>.zoneContaining(lat, lon)`. No es
  un caso de uso — no sale en diagnósticos ni mueve un pin. Gana **la zona más pequeña** cuando
  solapan (el garaje dentro del barrio: el sitio útil es el específico) y el empate rompe por id,
  porque un marcador que alternara de zona re-rasterizaría su bitmap para siempre.
- **Insignia** `VehicleZoneMark(icon, isPrivate)` en `VehicleBadgeMarker`: mismo lenguaje que las
  otras (disco relleno + anillo del relleno de la etiqueta), en el color de identidad de la ZONA
  (`zoneIdentityColor`), que ya es neutro para privadas. El candado va como disco pequeño en la
  esquina del propio disco de zona — una insignia sobre la insignia.
- **La etiqueta de la zona se retira** cuando aloja un coche aparcado; **el anillo se queda**: el
  anillo es el área y el área sigue siendo verdad. Lo que sobraba era el rótulo encima del sujeto.
- **El id del bitmap lleva la zona** (`zoneKey`). Sin eso, kmpmaps cachea por `contentId` y entrar
  en una zona habría servido para siempre el marcador sin insignia.

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `vehicleHandlers` (marcador por vehículo) | ✅ recibe la marca y la mete en su id |
| Etiqueta de zona (`zones.forEach`) | ✅ se salta las zonas que alojan un coche aparcado |
| Anillo de radio de la zona | ✅ intacto — es el área, no el rótulo |
| `fallbackParkingContentId` (detalle de historial) | ❌ fuera: esa pantalla no dibuja zonas |
| Dev Catalog | ✅ cuatro variantes nuevas (pública, privada, privada+diana, pública+pregunta) |

## Criterio de éxito

- Coche aparcado dentro de una zona: el marcador lleva el glifo; la zona no se rotula; el anillo sí.
- Zona privada: candado en el disco.
- Con duda abierta, se ven las dos cosas, en esquinas distintas.

## Verificación

`ZoneContainmentTest` — 5 tests en verde · `:shared:compileDebugKotlinAndroid` y
`:app:compileMockDebugKotlin` ✅.

## Decisiones de la revisión (#38, rndevelo, 19-09)

> «¿Por qué se ve así? Revisa bien la implementación […] la opción de dejar el icono de zona y su
> candado arriba a la izquierda de la zona también lo veo bien y así evitamos tener que transformar
> el marker. Si no, déjalo como mejor veas que sea más intuitivo.»

**No era el diseño: era un fallo de CACHÉ, y la captura lo dice.** En el pantallazo el coche sale
como el pin rojo por defecto de Google, no como el marcador de Paparcar. Eso pasa cuando kmpmaps
recibe un `contentId` para el que **no hay handler registrado**: dibuja su marcador propio.

Causa exacta: la primera pasada metió la zona en la clave con un parámetro `zoneKey` **con valor por
defecto**. El mapa de handlers lo pasaba (`z<id>p`), la creación del `Marker` se lo dejaba (`nz`), y
las dos claves dejaron de casar **justo cuando el coche entraba en una zona** — el único caso que la
feature añadía, y por eso no se vio compilando. La zona sí se des-rotulaba (ese lado funcionaba), así
que en pantalla quedaba un pin rojo desnudo encima del sitio.

**Arreglo de sistema, no parche**: la clave la construye **un solo sitio**.
`vehicleBadgeContentId` recibe ahora `zones: List<Zone>` —**obligatorio, sin defecto**— y resuelve él
mismo la contención (`List<Zone>.zoneMarkKey`). Omitirla no compila, y los dos call sites no pueden
volver a discrepar. `MapMarkerIdsTest` gana el caso que faltaba: el mismo coche dentro y fuera de una
zona emite ids distintos, y una zona lejana no cambia nada.

**Sobre la alternativa (glifo arriba a la izquierda de la zona):** me quedo con la insignia en el
marcador, que es lo que pedía la issue #32 («el marcador del coche pasa a llevar el icono de la zona
y el candado»). El motivo por el que se veía mal ya no existe, y separar glifo y sujeto obliga al
usuario a unir dos cosas con la vista para responder «¿está en casa?». Si en device sigue sin
convencer, el cambio a la esquina de la zona es local a `zones.forEach` + `VehicleZoneMark` y lo hago
en un PR pequeño.
