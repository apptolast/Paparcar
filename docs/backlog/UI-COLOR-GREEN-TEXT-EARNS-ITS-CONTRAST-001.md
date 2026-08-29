# UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001 · rellenar y escribir no son el mismo trabajo

**Estado:** ✅ **Done** — validado en device (Oppo, mock, 29-08)

Sale de las dos deudas que [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001] dejó anotadas y aceptadas.

---

## Problema

Un color que **rellena** una forma y un color que **escribe** una palabra tienen suelos de contraste
distintos: 3:1 el objeto gráfico, 4.5:1 el texto pequeño. En el tema claro, los verdes lo bastante
vivos para verse bien como relleno se quedaban cortos como letra:

| Texto | Medía | Pide |
|---|---|---|
| `FIABLE` | 2.34:1 | 4.5 |
| `MEDIA` | 2.85:1 | 4.5 |
| Verde de marca (links, cifras) | 3.01:1 | 4.5 |

No es un bug funcional: se lee bien en interior con buena vista, y por eso pasó desapercibido. Se
nota al sol o con vista reducida. El del verde de marca **lleva meses shippeando**; sólo se había
medido por primera vez en el ticket anterior.

## Doctrina violada

Ninguna nueva: es el mismo principio del ticket padre — *el color lo decide el TRABAJO* — llevado un
paso más allá. Rellenar y escribir son dos trabajos, así que pueden necesitar dos valores.

## Diseño

Cada historia gana una **pierna de texto**, y sólo en tema claro: sobre fondo casi negro un color
vivo ya pasa 4.5:1, así que no hay nada que pagar.

| Rol | Relleno / glifo (vivo) | Texto (legible) |
|---|---|---|
| Marca | `PapGreenLight` `#009F5E` *(intacto)* | `PapGreenTextLight` `#237A46` — 5.32:1 |
| Plaza fresca | `PapSpotFreshLight` `#5FBF1F` | `PapSpotFreshDeep` `#398701` — 4.53:1 |
| Enfriándose | `PapSpotCoolingLight` `#E08200` | `PapAmberLight` — 4.53:1 |
| Caducando | `PapSpotExpiringLight` `#E0322F` | `PapRedLight` — 6.46:1 |

- Rol nuevo `PapColor.actionText`: en oscuro **es** `action`; en claro, la pierna legible.
- `SpotStateColors` gana un tercer campo `text`. `ReliabilityMeter` sigue con `.bg` (es relleno);
  `HomeSpotRows` y `PeekShared` pasan a `.text` (pintan letras, pese a llamarse `badgeBg`).

### Lo que NO se toca
El verde corporativo `#009F5E` sigue intacto en botones, logo, glifos y marcador — es la línea roja
del user, fijada en device. Y el lima vivo sigue en el medidor de fiabilidad y en el chip.

### El puck sube de color
`PapSpotFreshPuck` `#4FA80A` (L\* 49.7 → 61.4). La "P" es un glifo **grande**, suelo 3:1, y queda en
3.03:1 — más holgado que el puck ámbar vecino, que lleva tiempo a 2.85:1. Corrige además una
incoherencia que la rampa ya tenía: ámbar y rojo eran vivos y sólo el verde estaba apagado.

⚠️ Esto vuelve a separar en dos lo que se había colapsado en un token: es correcto, porque los
requisitos dejaron de coincidir (texto 4.5 vs glifo grande 3.0). El token de texto y el del puck
tienen historias distintas y ya no comparten valor.

## Criterio de éxito
1. Los tres textos ≥ 4.5:1 sobre su cama real. ✅ medido en device: 4.53 / 4.53 / 5.32.
2. Ningún relleno, glifo, logo ni marcador cambia de color salvo el puck, a más brillo. ✅
3. Tests verdes + ambos flavors. ✅
4. Sin tokens duplicados sin declarar. ✅ (guardarraíl del ticket padre)

## Consumidores auditados
- **17 call sites** de verde de marca migrados a `PapColor.actionText`.
- **2 falsos positivos cazados y NO migrados**: `VehiclePageContent:300` es un spinner y
  `PaparcarBottomActionBar:47` es el relleno de un `Surface`. Migrarlos a ciegas habría oscurecido
  cosas que no son letra.
- `tint =` de iconos: **fuera de alcance a propósito** — un icono es objeto gráfico, suelo 3:1.
- 3 duplicados evitados por colapso en vez de inventando valores: el ámbar y el rojo del ramp
  reutilizan los del tema **sólo como texto**, porque a esa oscuridad convergen de verdad.

---

## El rebase, y el badge

Rebasado sobre master **sin un solo conflicto** — mejor de lo anunciado. El aviso que esta ficha
llevaba sobre un conflicto peligroso con la rama de tipografía nació obsoleto: esa rama se mergeó
(`80c00faf` + `8dfd5563`) mientras la tarea estaba aparcada.

Lo que el refactor de texto dejó, comprobado en master:

- **`FIABLE` desapareció de la fila** — decisión suya: *«el color del puck ya la dice, y el modal la
  explica con su medidor»*. Consecuencia: `ReliabilityPalette` se construía, se pasaba como
  parámetro y **no se leía ni un campo**. Borrada entera, junto con `palette()`. Recolorear código
  muerto habría sido peor que no tocarlo.
- **El badge que sobrevive es `SpotAgeIndicator`**, la píldora «Hace N min». Es lo único de la fila
  que lleva color además del puck, y es el que recibe el tema nuevo.

### El badge, con sus piernas claras

| Píldora | Cama | Texto | Contraste |
|---|---|---|---|
| Fresca | `PapSpotFreshContainerLight` `#DEF5C7` | `PapOnSpotFreshContainerLight` `#2E6E01` | 5.38:1 |
| Media | `PapAmberContainerLight` | `PapOnAmberContainerLight` | 11.05:1 |
| Caducada | `PapSpotExpiringContainerLight` `#FFDAD6` | `PapRedLight` | 5.00:1 |

La de "media" **reutiliza a propósito** los tokens de ámbar del tema en vez de tener los suyos: una
cama ámbar pálida es una cama ámbar pálida, y acuñar un token casi idéntico para que el nombre
dijera "spot" sería la enfermedad de los hex duplicados con mejores modales.

Con esto la rampa **se lee como rampa** en tema claro. Antes las tres píldoras eran la misma
pastilla negruzca, porque la píldora nunca fue theme-aware.

## Follow-ups
`UI-SPOT-AGE-PILL-IGNORES-THE-THEME-001` — ✅ **resuelto dentro de este ticket**: darle a la píldora
sus tonos por tema ES el arreglo, así que no necesitaba rama propia.

Sigue abierto del ticket padre: migrar los ~30 call sites que aún leen `colorScheme.primary` para
rellenos y glifos a los roles de `PapColor`. Los roles documentan la intención pero no la fuerzan.
