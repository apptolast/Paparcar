# WEB-A-PLUS-BULLET-MUST-SELL-THE-MEMORY-TOO-001 · la web vendía el sujeto y se dejaba la memoria

**Estado:** ✅ **Done** (29-10-2026) · rama
`fix/WEB-A-PLUS-BULLET-MUST-SELL-THE-MEMORY-TOO-001-30-days` · worktree `../Paparcar-web30` — ambos
se retiran al cerrar.

⏳ **Queda del user**: `firebase deploy --only hosting` para que esto esté en vivo. El build ya está
hecho en `hosting/public/`, pero desplegar es suyo.

## Problema

Lo que Plus vende en gasolineras son **dos** cosas: el **sujeto** (la gasolinera que tú elegiste,
vigilada esté donde esté, sin pasar por delante) y la **memoria** (su serie de precios de 30 días,
frente a los 3 que guarda el carril free de lo que te rodea). La web nombraba sólo la primera:

> *Aviso cuando cambia el precio de una gasolinera que sigues, esté donde esté*

No era falso, era **incompleto**, y desde `PLAY-A-FREE-ALERT-READS-AS-PAID-001` quedó además
desparejado: la ficha de Play sí dice los 30 días y la web no, siendo las dos superficies públicas.

## Doctrina aplicada

⛔ **El copy no escribe cifras.** `web/lib/config.mjs` declara los números y el copy los pide por
marcador (`{{historyDays}}` y compañía), y `WebSiteParityGuardrailTest` compara cada uno contra la
constante que lo decide en la app. El comentario del propio guard lo dice: la primera versión
buscaba *«un 7 en alguna parte»* y no valía nada.

Así que **teclear «30 días» en los dos JSON era la salida equivocada**, aunque fuera lo que pedía el
encargo al pie de la letra: habría dejado en el copy público una cifra sin ninguna relación con
`FuelWatchRepository.RETENTION_DAYS`, que es justo la deriva que ese sistema existe para impedir.

## Diseño

⛔ **La cifra no entra en `FREE_LIMITS`**, cuyo nombre promete otra cosa: ésos son los topes que la
app impone sin pagar. Nace un bloque hermano, y el guard lo trata igual:

```js
export const PLUS_FIGURES = { fuelHistoryDays: 30 };   // web/lib/config.mjs
```

- `COPY_TOKENS` lo mezcla, así que el copy puede pedir `{{fuelHistoryDays}}` y el build ya rechazaba
  un marcador desconocido.
- El guard gana `PLUS_FIGURES = mapOf("fuelHistoryDays" to (FuelWatchRepository.kt to
  "RETENTION_DAYS"))` y su chequeo propio. La comparación que ya existía se extrae a
  `assertFiguresAgreeWithTheirSource(block, sources)` y se invoca dos veces — una por bloque — en vez
  de duplicarla.
- El chequeo de «el copy usa los marcadores» pasa a cubrir las claves de los dos bloques.

📌 Y de paso, su nombre y su mensaje decían *«the plan limits»* / *«stopped stating the free plan»*,
que con una cifra de Plus dentro ya mentía. Ajustados: **un guard que caza bien y acusa mal manda a
mirar al sitio equivocado.**

## Criterio de éxito

- [x] el bullet lo dice en **los dos idiomas**, y resuelto en las páginas construidas: *«…esté donde
      esté, con 30 días de histórico»* y *«…wherever it is, with 30 days of history»*, sin ningún
      `{{marcador}}` sin sustituir
- [x] **las dos mitades del guard falsificadas**, una a una:
  - poniendo `fuelHistoryDays: 20` en la web con el código en 30 → rojo
    *`the Plus figures the site states are the ones the app keeps`*
  - tecleando «30 días» a mano en `es.json` en vez del marcador → rojo
    *`every locale states its figures through the tokens, never as numerals`*, con el mensaje
    nombrando `[fuelHistoryDays]`
  - devolviendo cada una, verde
- [x] `node web/build.mjs --check` limpio y `./gradlew testAll --rerun-tasks` verde

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `web/content/es.json` · `en.json` | ✅ los dos, en la misma tarea, como manda la regla de la web |
| `web/lib/config.mjs` | ✅ la cifra y su marcador |
| `WebSiteParityGuardrailTest.kt` | ✅ la ata a `RETENTION_DAYS` y su mensaje ya no habla sólo del plan free |
| `.plus.lead` de los dos idiomas | ✅ **ya decía la memoria** en prosa (*«y para que recuerde más allá de la última semana»*); lo que faltaba era la cifra en el bullet. No se toca |
| Ficha de Play (`paste/`) | ✅ ya lo dice desde `a20f01898`. Las dos superficies quedan parejas |
| `hosting/public/` | ✅ reconstruido · ⏳ **sin desplegar**: es del user |
