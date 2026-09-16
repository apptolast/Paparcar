# TEST-RULES-ARE-EXECUTED-NOT-READ-001 · Las reglas se ejecutan, no se leen

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** 4.ª vuelta de la revisión independiente, punto 2. Y la causa raíz de cinco fallos de
reglas en un solo día.

## Problema

Septiembre trajo tres cambios de `firestore.rules`, todos verificados **leyendo** el código contra
la regla. Dos trajeron regresiones que solo se vieron en la revisión siguiente:

- disolver un grupo dejó de poder borrar `state/current` — la última posición del coche quedaba
  huérfana;
- expulsar a alguien dejó de quitarle el nombre de la tarjeta, deshaciendo lo que el commit anterior
  acababa de arreglar.

Las dos **fallaban en silencio**: las dos escrituras van envueltas en `runCatching`. Y las dos las
encontró un revisor que tampoco pudo ejecutar el emulador — o sea que se detectaron por el mismo
método que las produjo, con la misma suerte.

## Lo que se montó

`firestore-rules-tests/`: `@firebase/rules-unit-testing` + vitest sobre el emulador, **30 tests**
que cubren `state`, `spots`, `zoneAlertSubs`, pertenencia, sesiones e invitaciones.

Tres decisiones que no son obvias:

1. ⛔ **El emulador lee `firestore.rules`, no una copia.** El primer intento puso un `firebase.json`
   dentro del subproyecto y el CLI lo rechazó («outside of project directory»); la salida fácil
   habría sido copiar el fichero antes de cada test, que es exactamente cómo se construye un guard
   que aprueba una versión vieja. Se resolvió con `firebase.rules-tests.json` **en la raíz**.
2. ⛔ **`smoke.test.ts` se queda.** Falló la primera vez porque el emulador arrancó **sin cargar
   ninguna regla** y el anónimo pudo leerlo todo. Ese test es el canario: si un día vuelve a pasar
   verde lo que debería denegarse, es que la suite no está mirando las reglas de nadie.
3. ⛔ **Los ficheros corren EN SERIE** (`fileParallelism: false`). Comparten un emulador y cada
   suite hace `clearFirestore()`: en paralelo una borra la semilla de otra a media prueba, y el rojo
   resultante no es un fallo de reglas sino una carrera — la peor clase, porque se arregla sola al
   reintentar.

Y `WRITERS.md` + `RulesWriterInventoryGuardrailTest`: cada escritura a Firestore aparece con el test
que la cubre, y el guard falla si alguien añade un `.set(`/`.update(`/`.delete(` sin tocar la lista.
Una suite de reglas que no crece con el código es la misma trampa un año después.

## Lo que esto NO cubre

⛔ **El emulador no exige índices compuestos.** Una consulta sin índice pasa aquí en verde y revienta
en producción. Eso lo cubre `ops-an-index-is-verified-against-production-001`.

## Cola

- El job de CI (`.github/workflows/rules-tests.yml`) **no se añade todavía**: el CI de la
  organización lleva caído por facturación desde el 14-09, y un workflow que nadie ejecuta es otro
  guard que no mira. Entra cuando el CI vuelva.
- `diagnostics`, `diagnostics_reports` y `users/**` siguen sin tests: reglas simples de «solo el
  propio uid», sin cambios este mes. Entran cuando alguien las toque.
