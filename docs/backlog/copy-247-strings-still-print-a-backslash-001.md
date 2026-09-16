# COPY-247-STRINGS-STILL-PRINT-A-BACKSLASH-001 · 247 cadenas siguen llevando `\'` y se leen con la barra

**Estado:** 🔵 Abierto, sin empezar · sin rama
**Sale de:** `IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001` (16-09), al espejar diez claves `notif_*` a
`composeResources` y tener que decidir cómo escribir el apóstrofo.

## Problema

`COPY-APOSTROPHE-IS-NOT-ESCAPED-001` dejó la regla escrita en `CLAUDE.md`: **Compose Resources NO
desescapa `\'`**, así que un apóstrofo escapado sale impreso con la barra («Paparcar\'s»). La regla
está; el barrido no llegó a todo. Contado hoy en
`shared/src/commonMain/composeResources/*/strings.xml`:

| locale | claves con `\'` |
|---|---|
| `values` (EN) | 49 |
| `values-fr` | **107** |
| `values-it` | **64** |
| `values-nl` | 6 |
| `values-de` | 5 |
| `values-es` · `values-pt` · `values-pl` · `values-ro` | 4 cada uno |
| **total** | **247** |

⚠️ **No es solo iOS.** `composeResources` es de donde lee TODA la UI de producto en las dos
plataformas — `app/src/main/res` solo sirve a las notificaciones de Android. Estas 247 cadenas se
están leyendo con la barra **en la app publicada**, y las que más duelen son justo las lenguas donde
el apóstrofo es elisión y no contracción: FR e IT se llevan 171 de las 247.

Ejemplos reales, tal cual salen hoy:
- `home_spot_unconfirmed_note` (FR): *«Nous n\'avons pas encore vérifié…»*
- `home_spot_retracted_note` (IT): *«Chi l\'ha segnalato…»*
- `onboarding_cta_start` (DE): *«Los geht\'s»*
- `first_steps_explain_watch_manual_body` (ES): usa comillas simples rectas para entrecomillar un
  botón, que es un problema distinto y de estilo, no de escape.

## Doctrina violada

`COPY-APOSTROPHE-IS-NOT-ESCAPED-001`, literalmente. Y una de segundo orden: **una regla sin guard es
una regla que se vuelve a romper**.

## Por qué ningún test lo ha visto

`NotificationCopyParityTest` compara los dos árboles de recursos y **normaliza `\'` → `'` en los
dos** antes de comparar, porque ahí la diferencia es legítima (Android res exige la barra, Compose
Resources la prohíbe). Esa normalización, que es correcta para su pregunta, es exactamente lo que
hace que no pueda ver esta. Y `LocaleParityGuardrailTest` mira qué claves existen, no qué dicen.

## Diseño

1. Barrido: `\'` → `'` en los nueve `composeResources/values*/strings.xml`, y **solo ahí**.
   ⛔⛔ `app/src/main/res` tiene la regla CONTRARIA: un `'` crudo NO COMPILA. Los dos árboles no se
   tocan con el mismo `sed`.
2. Guard nuevo (Konsist o un test de fichero, como el de paridad): **cero `\'` en
   `composeResources`**, y su testigo de población al lado para que un árbol vacío no pase por
   limpio.
3. De paso, el `%` : `COPY-PERCENT-IS-NOT-ESCAPED-001` es la misma familia y el mismo guard puede
   contar los `%%`.

## Criterio de éxito

El guard en rojo antes del barrido y en verde después, y una captura en FR de
`home_spot_unconfirmed_note` sin barra.
