# PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001 · un solo hecho por superficie, el de mejor evidencia

**Estado:** ✅ Done · rama `feature/PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001-ladder` · mergeado a master
**Sale de:** [`data-providers-001.md`](data-providers-001.md) **§F.1** (la escalera, decidida) y
**§F.4** (es el **ticket 4**). La cifra ya llega: la pone
[`parkings-live-occupancy-001.md`](parkings-live-occupancy-001.md) (ticket 3, ✅ verificado en vivo).

## Problema

**La ocupación de Madrid está en la respuesta y la app la TIRA.** Medido en vivo el 03-11: la celda
de Sol trae 8 parkings con `availability` (Plaza del Carmen 153, Recoletos 67, Barceló 79,
**Benavente 0**) y el cliente la pierde en el mapeo — `ParkingsApiEntry.toPublicParking()` no copia
el campo porque `PublicParking` **no lo tiene**.

Así que hoy el conductor ve exactamente lo de antes: el nombre, la distancia, el tipo y «Gratuito» /
«De pago» en el 29 % de los casos. La pregunta que el producto existe para contestar —*¿queda sitio?*—
está respondida en el servidor y no llega a la pantalla.

## Doctrina violada

Ninguna rota; **una decidida y sin implementar** (§F.1), y dos que mandan sobre cómo:

1. ⛔ **Una ranura, un hecho, el de mejor evidencia** — y el **mismo** en la fila, el marcador y el
   peek. Hoy el color ya cumple eso (*«el pin del mapa y su fila son la MISMA cosa»*); a partir de
   aquí tiene que cumplirlo también el HECHO.
2. ⛔ **La rampa 🟢🟡🔴 es EXCLUSIVA de la caducidad de plazas** [UI-COLOR-DOCTRINE-001]. Un parking
   lleno **no es una alerta**: es un dato. Nada de teñir la cifra.
3. ⛔ **El 71 % tiene que poder no decir NADA.** `FeeInfo.UNKNOWN` ya se dibuja como silencio, y eso
   no se toca: *«un conductor al que le dijimos gratuito y se encontró una barrera fue engañado por
   nosotros, no por OpenStreetMap»*.

## Señales / datos disponibles

- **La escalera ya está decidida** (§F.1): `42 libres` / `Completo` → `desde 8 € (2 h)` →
  `De pago`/`Gratuito` → **nada**.
- **Lo que de verdad llega hoy** [MEDIDO en vivo 03-11]: `availability.free`, su `updatedAt` **sellado
  por la fuente**, `source`, `sourceKind`, `licence`, y `mergedFrom` cuando hubo fusión.
- ⛔ **Lo que NO llega, y cambia el diseño del escalón 1**: **Madrid no publica el AFORO**
  (`total` ausente en las 24 medidas). Así que **«42 libres» no puede decir «de 587»**. El `capacity`
  de OSM lo trae en el 3,2 % y mezclar las dos fuentes en una frase sería atribuir a Madrid un número
  que no ha dado.
- **Las tres superficies y sus ranuras**, ya existentes:
  - fila → `trailing` de `PapListItem` (hoy `feeLabel(...)`), en `HomePublicParkingRows.kt:83`
  - marcador → `PublicParkingMarker(feeLabel = …)` desde `PaparcarMapView.kt:1575`, cápsula con
    **primaria + cualificador**; sin frase, **disco pelado**
  - peek → su `eyebrow` y su cuerpo, en `PublicParkingPeek.kt`
- **El escalón 2 (`desde 8 €`) no existe todavía**: es el ticket 5 y está bloqueado. La escalera lo
  declara y lo deja sin rellenar.

## Diseño

### D.1 La cifra entra en el dominio, que es de donde no debió salir

`PublicParking` gana `availability: ParkingAvailability?`, y `ParkingsApiEntry.toPublicParking()`
deja de tirarla. ⛔ Es el dominio y es Kotlin puro: el tipo **no** lleva `licence` ni `sourceKind` como
texto suelto que la UI tenga que interpretar.

### D.2 La escalera es UNA función pura, no tres `when`

En `domain/parkings/`, al lado de `PublicParking`, con el precedente explícito de `FuelStandouts`:
*«una función pura y no un caso de uso a propósito — su resultado no aparece nunca en un diagnóstico
y no cambia nunca un pin: sólo decide qué dice una fila de sí misma»* [DET-VERDICT-NOT-PREDICATE-001].

Devuelve un tipo sellado `ParkingHeadline`, y **los strings los resuelve presentación** (el dominio
no conoce recursos), igual que `publicParkingKindLabel`.

### D.3 ⛔ El invariante nuevo: una cuenta viva CADUCA, y al caducar BAJA la escalera

Un número viejo **no se enseña viejo: se deja de enseñar**, y la superficie cae al escalón de la
tarifa. Dos cosas que lo hacen honesto:

- la frescura se mide contra el **`updatedAt` de la FUENTE** — nunca contra cuándo lo pedimos —, y
  cuando la fuente no sella su dato (`updatedAtIsFetch`) **el umbral tiene que ser más corto**,
  porque ahí ni sabemos de cuándo es;
- el peek sigue diciendo **«hace X min»**, que es lo único honesto cuando el dato ya no manda.

Umbral: **10 min** para un dato sellado (3× la cadencia de Madrid) [SUPUESTO declarado en §F.1].

## Criterio de éxito

1. **Las tres superficies dicen el MISMO hecho** para el mismo parking, demostrado por un test que
   las compare — no por mirarlas.
2. **La caducidad falsificada**: con el umbral quitado, el test de la cifra rancia se pone rojo.
3. ⛔ **El cero se pinta «Completo»**, no «0 libres». `Benavente: 0` es un caso real, no inventado.
4. **Nada teñido**: ningún color nuevo para la cifra.
5. **Strings en los 9 locales** y **Dev Catalog** con el estado nuevo, en esta misma tarea.
6. `./gradlew testAll` verde + captura en la PR (es un cambio visible).

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `PublicParking` (dominio) | ⏳ gana `availability?` |
| `ParkingsApiEntry.toPublicParking()` | ⏳ deja de tirar el campo |
| `PublicParkingSnapshotEntry` (el fichero) | ✅ **sin cambios**: el snapshot es la base, no lleva ocupación nunca |
| `HomePublicParkingRows` · `PaparcarMapView` · `PublicParkingPeek` | ⏳ los tres leen **la misma** función de escalera |
| `PublicParkingFilter` | ⏳ entra **un** chip: «Con plazas libres», que **excluye lo desconocido** |
| `NearbyOrdering.publicParkings` | ✅ **el orden NO cambia** (§F.2): nada se adelanta hasta medir la distancia al más cercano con cuenta |
| `mockkit` / Dev Catalog | ⏳ el fake gana parkings con cifra, con cero y con cifra rancia |
| 9 locales | ⏳ `Completo`, «N libres», «hace X min» |
| `docs/backlog/README.md` | ⏳ recuento al cerrar |
