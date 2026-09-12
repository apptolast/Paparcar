# Prompt para arrancar MAP-A-REMEMBERED-PIN-LOOKS-LIKE-A-LIVE-ONE-001

> Copiar de la línea de abajo hasta el final y pegarlo tal cual en una sesión nueva.
> Autocontenido: no hace falta contexto previo.

---

Implementa `MAP-A-REMEMBERED-PIN-LOOKS-LIKE-A-LIVE-ONE-001` en Paparcar. La ficha está en
`docs/backlog/map-a-remembered-pin-looks-like-a-live-one-001.md` — **léela entera antes de tocar
nada**: trae el diseño propuesto, el criterio de éxito y la alternativa de no hacer nada.

**Empieza invocando la skill `nuevo-ticket`** (worktree aislado + rama + doc en tiempo real).
⛔ No commitees ni mergees sin permiso explícito de ese turno.

## El bug, en una frase

El mapa dibuja pines recordados del disco **sin decir de cuándo son**: un parking visto hace 5
minutos y uno de hace 3 días se ven exactamente igual.

⛔ **No falta ningún dato.** Desde `CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001` (`96f09b73`) el
disco alimenta por igual a la lista y al mapa. La diferencia es de ETIQUETA: la sección del sheet sí
dice *«sin conexión: estos son los parkings que vimos aquí hace 3 días»*; la capa del mapa no dice
nada.

**El caso que muerde es el 504 CON COBERTURA**: la banda roja de «You're offline» no sale (para el
sistema no hay problema de red), así que el usuario conduce hasta un pin que nadie comprobó hoy. Sin
cobertura ya se avisa; lo que falta ahí es la EDAD, no el aviso.

## Lo que hay ya (nada que instrumentar)

- `HomeParkingsLayerSlice.rememberedAtMs` y `HomeFuelLayerSlice.rememberedAtMs`: `null` = recién
  bajado, un valor = del disco y cuándo se preguntó por última vez. Ya viajan hasta el estado.
- `relativeTimeText` responde «cuánto de vieja es» en los 9 locales → cuesta **una** key, no un
  formateador de fechas por nueve.
- La banda de los FABs ya hospeda la cápsula de GPS (`HomeMapGpsLane`,
  `UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001`) y las píldoras comparten geometría desde
  `UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001`. Esto es **una cápsula más**, no un elemento nuevo.

## Reglas que no puedes romper

- ⛔ **Nada de modal ni superficie flotante nueva sobre el mapa.** Va en la banda de los FABs.
- ⛔ **Callada no cuesta nada**: sin nada que decir, cero píxeles y cero altura reservada
  (`UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001`). Una banda que reserva altura por si acaso es
  exactamente el fallo que ese ticket arregló.
- ⛔ **No sustituye a la banda de «You're offline»**: son dos frases distintas —«no tienes red» y
  «esto es de hace X»— y la segunda también sale con red. Que convivan sin repetirse.
- ⛔ Habla solo si hay **alguna capa encendida** y su `rememberedAtMs != null`.
- ⛔ **Strings en los 9 locales en la MISMA tarea** (`values` EN base, `-es -it -pt -fr -de -nl -pl
  -ro`). Faltar NO crashea: sale en inglés en silencio. `LocaleParityGuardrailTest` también caza keys
  muertas.
- ⛔ **Copy sin mecánica interna**: causa + consecuencia + remedio, sin jerga inventada. Y el
  vocabulario manda (`COPY-SPOT-IS-NOT-A-PARKING-001`): *plaza* ≠ *aparcamiento* ≠ *parking público /
  car park*.
- ⛔ **Sistema visual vigente** (`UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001`, `b967323b`): un solo
  esqueleto de fila (`PapListItem` en `PapOutlinedCard` con `PapIconTile`, **neutro por defecto**);
  el énfasis es una variable, no un contenedor nuevo; **presupuesto de dos verdes por pantalla** (la
  acción primaria y la identidad del coche vigilado); el **ámbar es solo caducidad** — y este aviso
  ES caducidad, así que es de los pocos sitios donde el ámbar está bien puesto; cuatro radios
  (`row 12` · `card 16` · `sheet 20` · `chip 999`).
- ⛔ **Dev Catalog en la misma tarea**, y la variante tiene que **ejercitar su caso**: la fecha se
  CALCULA, no se deja en el valor por defecto (`DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`).

## Verificación

```bash
./gradlew :shared:testDebugUnitTest --rerun-tasks   # ⛔ sin --rerun-tasks los guards Konsist salen UP-TO-DATE sin ejecutarse
./gradlew :app:assembleProdDebug :app:assembleMockDebug
```

Y **en device**: instalar el prodDebug y forzar el caso — fuente caída **con cobertura**. Los cuatro
criterios de éxito de la ficha son la lista de comprobación (callada con todo bien; habla con el 504;
convive con la banda roja sin repetirla; variante en la galería).

## Lo que tienes que preguntarme antes de darlo por cerrado

1. La ficha registra una **alternativa legítima: no hacer nada** (la sección ya lo dice, el peek de
   gasolinera ya da la hora de cada precio, y sin cobertura avisa la banda roja). Si al medirlo en
   device te parece que el descubierto no lo justifica, **dilo antes de implementar** — cerrar con
   una decisión escrita es un resultado válido.
2. El texto exacto de la cápsula, antes de traducirlo a los 9 locales.
