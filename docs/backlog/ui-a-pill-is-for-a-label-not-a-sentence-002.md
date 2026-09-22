# UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002 · El CTA dejó de ser una etiqueta y sus maquetas no se barrieron

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#116**: *«botón de "no voy en mi coche" corta información de "en ruta". He pensado que iría
mejor abajo `fillMaxWidth`, ya que el texto es largo»*.

En la card de coche único (`HomeVehicleCard`, rama `isDriving`) el pie es una fila:

```
[glifo ruta]  «En ruta · Calle Larga, 14»          [ No voy en mi coche ]
```

La cápsula es hija SIN peso de esa `Row`, así que se mide primero y a ancho intrínseco: se lleva su
etiqueta entera más 32 dp de padding. Lo que queda —el `weight(1f)` con el estado y el lugar— es el
resto, y el resto ya no da: la única información viva del viaje sale cortada por el botón que la
acompaña.

## Doctrina violada

La misma que `a8d4965b1` acaba de escribir para `RouteNoticePill` —
**[UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-001]**— en su segundo consumidor, que aquel ticket no
barrió. Y con ella, la regla operativa de
**[UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001]**: *el layout lo manda el label más largo de los 9
locales, no el inglés*.

Ese ticket midió, y por eso puso el CTA inline:

| | CTA máx en los 9 locales | Decisión |
|---|---|---|
| 01-09-2026, con «Stop» | **9** chars (PL «Zatrzymaj») | → INLINE |
| hoy, con «No voy en mi coche» | **23** chars (IT «Non sono nella mia auto», RO «Nu sunt în mașina mea» 21) | inline sin medir |

El label cambió en [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001] —«Parar» pasó a «No voy en
mi coche», que es una frase y describe el caso de verdad— y **ninguna de las maquetas que lo
alojaban se volvió a medir**. El comentario que defiende el inline en `HomeParkingRow.kt:422` sigue
diciendo *«a four-letter CTA does not earn a full-width band»*: una premisa que lleva muerta desde
que la etiqueta se convirtió en frase. 2,5× el ancho para el que se decidió.

## Señales / datos disponibles

Medido sobre `composeResources/values*/strings.xml`, `home_det_driving_stop_cta`:

```
EN 13  Not in my car        IT 23  Non sono nella mia auto
ES 18  No voy en mi coche   PT 20  Não vou no meu carro
FR 19  Pas dans ma voiture  DE 20  Nicht in meinem Auto
NL 17  Niet in mijn auto    PL 20  Nie jadę swoim autem
RO 21  Nu sunt în mașina mea
```

`PapCtaPill` pinta `maxLines = 1` + `Ellipsis`, así que la cápsula **no cede**: no envuelve ni se
encoge, y quien paga el ancho es siempre el texto de al lado.

## Diseño

**La cápsula del viaje se apila a ancho completo, en las DOS superficies.**

`HomeVehicleChip` ya lo hacía —su pie estrecho nunca tuvo hueco inline— y `HomeVehicleCard` era la
excepción. Con la etiqueta convertida en frase, la excepción se queda sin argumento: el mismo botón,
con el mismo texto, se coloca igual en las dos superficies donde vive. Una decisión, no dos.

⛔ **Apilar no reabre el problema de las esquinas de -001.** Ahí la cápsula reventaba porque el texto
pasaba a DOS líneas y el radio (la mitad del alto) se comía los extremos. A ancho completo la frase
más larga —23 chars— entra sobrada en una línea: la cápsula sigue siendo cápsula.

⛔ **El `bottom` de la fila se muda con el botón**, no se duplica: el pie deja de llevar el aire
inferior de la card cuando hay cápsula debajo, y lo lleva la cápsula. Si no, `CARD_V_PAD_DP` sale
dos veces y la card conduciendo crece 14 dp respecto a sí misma aparcada.

## Criterio de éxito

- Conduciendo, con el locale italiano y un nombre de calle largo, la línea «En ruta · X» se lee
  entera (envuelve o elide por su propio ancho, no por el del botón).
- La cápsula dice su frase completa, en una línea, en los 9 locales.
- Chip y card colocan el stop de la misma forma.
- Captura del estado final en la PR [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Consumidores auditados

`grep PapCtaPill(` — los 6 call sites, y qué etiqueta puede caerles:

| Call site | Colocación | Etiqueta más larga | Veredicto |
|---|---|---|---|
| `HomeParkingRow` · `HomeVehicleChip` | apilada full-width | `home_det_driving_stop_cta` (23) | **ya correcta** — sin cambios |
| `HomeParkingRow` · `HomeVehicleCard` | **inline** | `home_det_driving_stop_cta` (23) | **el bug** → pasa a apilada |
| `HomeDetectionSurface` · trailing inline | inline | ver abajo | auditado |
| `HomeDetectionSurface` · `primaryStacksBelow` | apilada full-width | `home_det_watching_fortify_cta` | correcta por construcción |
| `HomeDetectionSurface` · par sí/no | dos, `weight(1f)` | respuestas cortas | correcta — [DET-ASK-STATE-001] |

**La vía inline de `HomeDetectionSurface`** es la puerta por la que volvería a entrar el mismo
fallo: cualquier fila con UN solo CTA y sin `primaryStacksBelow` lo pinta a la derecha. Medidos sus
labels en los 9 locales, **había un segundo caso vivo**:

| Fila | CTA más largo (9 locales) | Antes | Ahora |
|---|---|---|---|
| `AwaitingFirstPark` | **25** · FR «Marquer mon stationnement» | **inline** siempre que `allowDrivingDetection == false` | apilada |
| `NoVehicle` | 11 · DE «Hinzufügen» | inline | inline |
| `Inactive` / `BlockedCore` | 11 · NL «Inschakelen» | inline | inline |
| `WATCHING_FRAGILE` / `WATCH_INTERRUPTED` | 10 / 16 | apilada (por su SUBTÍTULO: 78 y 126 chars) | igual |
| `AwaitingAnswer`, `PendingAsk` | par sí/no | dos `weight(1f)` | igual |

## Resolución

**La colocación la decide el LABEL, no un booleano.** Es la misma lección que `-001` sacó de
`RouteNoticePill` —*«la condición ya distingue etiqueta de frase sin un parámetro que alguien tenga
que acordarse de pasar»*— aplicada al sitio donde el parámetro ya había fallado.

1. **`ctaFitsInline(label)`** (`ui/components/CtaPlacement.kt`), con su presupuesto de
   `CTA_INLINE_MAX_CHARS = 14` razonado sobre el ancho real de la fila (~300 dp útiles; un CTA de
   más de ~135 dp deja al título sin su segunda línea). Ningún label vivo cae entre 12 y 17, así que
   el valor no arbitra nada hoy: está para que el PRÓXIMO copy caiga solo del lado correcto.
2. **`HomeDetectionSurface`** deriva `stacksPrimary = primaryStacksBelow || !ctaFitsInline(...)`.
   `primaryStacksBelow` sobrevive porque dice otra cosa —*la FILA lo pide, por su subtítulo*— y las
   dos razones son legítimas y distintas. Esto solo ya arregla `AwaitingFirstPark`.
3. **`HomeVehicleCard`** apila el stop del viaje **incondicionalmente**, igual que el chip.
   ⛔ Y NO pasa por el predicado, a propósito: éste gobierna el hueco *trailing* de una fila, donde
   el vecino es copy acotado que puede envolver. Aquí el vecino es una sola línea con el nombre de
   una calle, que no tiene tope — un botón al lado se la come mida lo que mida.
4. El `start = 14.dp, end = 12.dp` estaba escrito a mano en tres sitios y la cápsula iba a ser el
   cuarto → `CARD_PAD_START_DP` / `CARD_PAD_END_DP`.

## Testigo

`CtaPlacementGuardrailTest` (androidUnitTest, junto a `LocaleParityGuardrailTest` y con su misma
forma de leer los 9 XML). No puede ver dónde acaba pintado un botón —este repo no tiene tests de
Compose UI, y `-001` ya dejó escrito que el único testigo posible era una captura— pero sí ve **la
entrada de la decisión**: la longitud del label en los 9 locales. Que es el dato que nadie volvió a
mirar.

⚠️ **Falsificado**: acortado `home_det_awaiting_cta_primary` en `values-fr`, el test falla.

🟢 **Y cazó un error mío antes que nadie**: la primera versión metía `home_det_driving_stop_cta` en
la tabla de expectativas, y el test señaló que en inglés mide 13 chars y «cabría» — o sea, que la
tabla estaba escribiendo que la maqueta del stop depende del idioma, cuando por diseño no depende.
Salió de la tabla, y esa ausencia quedó documentada como la regla que es.

## Pendiente

⏳ **Captura del estado final** para la PR [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]: la card del coche
conduciendo, en español, con la cápsula abajo y la calle entera. No hay móvil conectado en esta
sesión.
