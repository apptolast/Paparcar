# MAP-A-REMEMBERED-PIN-LOOKS-LIKE-A-LIVE-ONE-001 · El mapa dibuja pines recordados sin decir de cuándo son

**Estado:** 📋 Backlog — diseño propuesto, **sin decidir por el user**. Prioridad baja.
Sale de [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001] (`96f09b73`), que dejó el dato listo y la
decisión de superficie fuera de alcance a propósito.

## ⛔ Lo que NO es (para no releer esto y entenderlo al revés)

**No falta ningún dato en el mapa.** Desde `96f09b73` el disco alimenta a las dos superficies por
igual: sin cobertura, la lista sale con sus filas **y** el mapa sale con sus pines. Misma consulta,
mismo resultado, los dos llenos.

La diferencia es de **etiqueta**, no de contenido:

| | ¿salen los parkings / gasolineras? | ¿dice de cuándo son? |
|---|---|---|
| Sección del sheet | ✅ sí | ✅ sí — *«No connection: these are the car parks we saw here 5 min ago»* |
| Capa del mapa | ✅ sí | ❌ **no** — los pines están, y nada más |

Un pin de hace 5 minutos y uno de hace 3 días se ven **exactamente igual**.

## El ejemplo que lo hace concreto

> **Lunes, 9:00 — Chamberí.** Abres la app con la capa de parkings encendida. Overpass contesta, el
> mapa dibuja 54 marcadores «P» y todos se guardan en Room con su disco.
>
> **Jueves, 19:00 — la misma calle, tres días después.** Vuelves a abrir la app. Esta vez Overpass
> devuelve un 504 (son servidores donados; pasa). **Tu móvil tiene cobertura perfecta**, así que la
> banda roja de «You're offline» **no aparece**: para el sistema no hay ningún problema de red.
>
> El mapa dibuja los mismos 54 pines. Idénticos a los del lunes. Tú conduces hasta uno de ellos.
>
> Si en esos tres días ese parking cerró, pasó a ser solo de residentes o cambió la barrera, el mapa
> te mandó allí **afirmando algo que nadie comprobó el jueves**. Y si despliegas la sección, ahí sí
> lo pone: *«…these are the car parks we saw here 3 days ago»*. Pero solo si la despliegas.

Ese es el hueco entero: **el caso en que hay red pero la fuente falla.**

## Por qué es pequeño (y por eso está aquí y no en curso)

Tres cosas lo acotan, y conviene tenerlas escritas para no sobredimensionarlo:

1. **Sin cobertura ya se avisa.** La banda roja de «You're offline» sale arriba del todo. El usuario
   no está a ciegas; lo que le falta es la EDAD, no el aviso. El caso descubierto es solo el de
   red-buena-fuente-caída.
2. **El gálibo de la caducidad es largo.** Un parking público es geografía: rara vez desaparece en
   tres días. Por eso su retención son 30 días y no 30 minutos.
3. **En gasolineras el mapa ya se describe a medias**: al tocar un pin, el peek enseña **la hora de
   cada precio** [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001], que es el dato de verdad
   perecedero. El peek de un parking, en cambio, no tiene ninguna fecha que enseñar.

O sea: el hueco muerde sobre todo en **parkings**, y sobre todo en **el 504 con cobertura**.

## Señales ya disponibles

Nada que instrumentar. `96f09b73` ya dejó el dato viajando hasta el estado, en las dos capas y a
propósito simétricas:
- `HomeParkingsLayerSlice.rememberedAtMs`
- `HomeFuelLayerSlice.rememberedAtMs`

Ambos: `null` = recién bajado; un valor = del disco, y **cuándo se preguntó por última vez**.

## Diseño propuesto

**Reutilizar la banda de los FABs**, donde ya vive la cápsula de GPS
[UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]. Esa banda existe exactamente para esto: un aviso que
**no cuesta nada cuando está callado** y solo habla cuando tiene algo que decir. Y desde
[UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] las píldoras comparten geometría, así que esto es **una
cápsula más**, no un elemento nuevo.

- Habla solo si hay **alguna capa encendida** Y su `rememberedAtMs != null`.
- Dice la **edad**, no la fecha, vía `relativeTimeText` — ya traducido a los 9 locales, así que
  cuesta una key.
- ⛔ **No es un modal ni una superficie flotante nueva.** Inventar una para esto rompería
  la regla de no apilar superficies sobre el mapa.
- ⛔ **No sustituye a la banda de «You're offline»**: son dos frases distintas —«no tienes red» y
  «esto que ves es de hace X»— y la segunda también sale con red.

## Criterio de éxito

1. Con red y fuente OK: **la cápsula no existe**. Cero píxeles, cero altura (lo que exige
   `UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001`).
2. Forzando un fallo de la fuente **con cobertura**: el mapa sigue enseñando los pines y la cápsula
   dice desde cuándo.
3. Sin cobertura: la cápsula convive con la banda roja sin repetir su mensaje.
4. Variante en la galería del Dev Catalog, **con fecha calculada**, no con el valor por defecto
   [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001].

## Alternativa considerada

**Dejarlo como está.** Es defendible: la sección lo dice, el peek de gasolinera ya da la hora de
cada precio, y sin cobertura la banda roja avisa. El único descubierto es el 504-con-red, sobre un
dato que envejece despacio. Si el user decide esto, no queda deuda — queda una decisión, y este doc
la registra.
