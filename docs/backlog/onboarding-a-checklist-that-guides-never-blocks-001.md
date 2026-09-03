# ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001 · Un permiso que el usuario no quiso dar le costaba el resto del tutorial

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> Incluye `ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001` y
> `ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001`.
>
> ⏳ Verificado en device (Redmi) salvo lo último: la pantalla de explicación se instaló y arranca,
> pero el user aún no ha confirmado que el botón respire bien en su pantalla.

> Esta rama integra además `ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001` y
> `ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001`: los tres tocan la misma tarjeta y la misma
> proyección, y separarlos solo producía conflictos artificiales. El del pin robado por la cámara
> (`PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001`) sigue en su rama propia.

## Problema

Reportado por el user en device (03-09): sin la exención de batería, el checklist **no le dejaba
pasar del paso 2**. El paso 3 era inalcanzable mientras no concediera ese permiso.

La causa, en una línea de `resolveParkedWatchBadge`:

```kotlin
!hasParkedSession    -> PARK_MY_VEHICLE
isBluetoothCovered   -> WATCHING
presence == Dead     -> WATCH_INTERRUPTED
isReliabilityReduced -> WATCHING_FRAGILE     // ← sin exención de batería
else                 -> WATCHING
```

El paso 2 se completaba con `isWatching`, y eso era **`WATCHING` exacto**. Sin exención salías
`WATCHING_FRAGILE` y el paso no se marcaba nunca; como `current` es «el primero no hecho», todo lo
que venía detrás quedaba fuera de alcance **para siempre**. Hay un segundo camino al mismo encierro
que el user no llegó a ver: soltar el aparcamiento (`PARK_MY_VEHICLE`) tenía el mismo efecto.

## Doctrina violada

- **Animar, no obligar** (formulación del user, 03-09). El checklist es una guía; no puede tomar
  como rehén el resto del producto por un permiso que el usuario tiene derecho a no dar.
- **El paso medía otra cosa de la que decía.** Se llama `UNDERSTAND_WATCH` —comprender— y su
  condición era el estado más estricto del sistema. El propio producto ya separa las dos preguntas
  por todas partes (tiers de permisos, `WATCHING` vs `WATCHING_FRAGILE`, la fila «fortify» con su
  copy propio); el checklist era el único sitio donde estaban fundidas.

## Señales / datos disponibles

- `DetectionUiState.isDetectionStopped` — «¿está encendida?», ya usada por el diálogo de liberación.
- `showBatteryOptimizationNudge` → `isReliabilityReduced` — «¿es frágil?», independiente de que haya
  coche aparcado, así que no reintroduce el encierro por otra vía.
- `BluetoothScanner.getBondedDevices()` — vincular un coche en Paparcar es **elegir un MAC ya
  emparejado en el móvil** (aclaración del user), no emparejar nada nuevo: es una acción de sofá.
- `HomeIntent.RequestBatteryExemption` y la ruta `BT_CONFIG/{vehicleId}` ya existen: el paso nuevo no
  inventa destinos, usa los que hay.
- `FirstStepsOwnership`, de `ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001`: el mecanismo
  para que una fila de la superficie se calle mientras el checklist hace su misma pregunta.

## Diseño

**La regla**, que es del user: *un paso que pide un PERMISO nunca bloquea; ofrece hacerlo o dejarlo
para luego.* No es una excepción para el paso 2, es cómo se comportan todos los pasos que piden algo
que no depende de la app.

**1 · El paso 2 mide lo que dice.** Se completa cuando hay un aparcamiento tuyo **y la detección está
encendida**. No exige robustez (eso es el paso siguiente) y no puede nacer ya marcado (sin
aparcamiento no hay nada que enseñar). El latch lo conserva si luego sueltas el aparcamiento.

**2 · Paso 2.5 `FORTIFY_WATCH`, condicional.** Existe solo si hay algo que reforzar, y lleva la cara
del coche activo:

| Cara | Cuándo | CTA |
|---|---|---|
| `BLUETOOTH` | el coche no tiene MAC vinculado y el móvil sí tiene dispositivos emparejados | «Vincular» → `BT_CONFIG/{id}` |
| `BATTERY` | no hay BT que vincular y la fiabilidad está reducida | «Activar» → `RequestBatteryExemption` |
| `NONE` | nada que hacer | el paso no existe |

Orden **no** arbitrario: un coche vinculado por BT lo vigila el receiver del manifest, sin proceso
residente, y por eso `resolveParkedWatchBadge` lo lee `WATCHING` sin mirar la batería. Pedir la
exención a quien puede vincular sería pedir algo que su vigilancia no usa. Un test lo fija — y de
hecho ese test encontró el fallo: la primera versión pedía batería a un coche ya vinculado.

**3 · «Aún no» junto a cada CTA que pide permiso**, con su latch **persistido y separado** de `done`.
Aplazar no completa: no se ha medido nada ni concedido nada. Persistido porque una postergación que
se olvida devuelve al usuario al mismo muro en el arranque siguiente — el bug otra vez. Y reversible
por construcción: si el permiso llega más tarde (desde Ajustes, desde la fila de detección), la señal
viva completa o retira el paso y la postergación deja de importar.

**4 · El contador cuenta los pasos APLICABLES.** Quien no tiene nada que reforzar va «de 3», no «de
4» con un paso que no verá nunca. Conceder el refuerzo no marca el paso: lo **retira**, y la lista se
acorta — que es la lectura honesta de «aquí ya no queda nada».

**5 · Cierre honesto.** Con algo aplazado, la tarjeta final no dice «Ya está todo listo» — eso sería
felicitar al usuario por algo que no hizo. Dice que ya sabe cómo funciona la app y que lo que dejó
sigue ahí, porque la superficie de detección vuelve a pedirlo en cuanto la tarjeta se va.

**6 · Una sola voz, otra vez.** Los pasos 2 y 2.5 dicen lo mismo que dos filas de la superficie de
detección, así que mientras el paso es la pregunta actual esa fila se calla (`DETECTION_OFF` →
`Inactive`, `WATCH_FRAGILE` → la línea frágil) y el paso **toma prestada su etiqueta** en vez de
inventar otra. Una acción, una etiqueta, un botón en pantalla. La vigilancia INTERRUMPIDA nunca se
cede: nombra su causa concreta y el paso solo podría decir algo más vago.

## Criterio de éxito

- `FirstStepsTest`: con la detección encendida y la exención denegada, el paso 2 se completa y el
  actual pasa a ser el de refuerzo — **el bug, en una aserción**.
- Aplazar avanza sin marcar hecho; con algo aplazado, `isComplete` es falso y `hasDeferrals` cierto.
- El paso de refuerzo no existe sin nada que reforzar (`total == 3`) y existe cuando lo hay (`4`).
- `resolveWatchReinforcement`: BT primero, batería solo si no hay BT que vincular, y **nada** si el
  coche ya está vinculado.
- `DetectionStoryTest`: cada fila se calla solo con SU ownership; la vigilancia interrumpida nunca.
- 9 keys nuevas × 9 locales (paridad verde) y las CTAs prestadas sin key nueva.
- Las cuatro caras nuevas en la galería mock y en los previews.
- En device: con la exención denegada, llegar al paso 3.

## Lo que encontró el device (03-09, tras el primer /run)

Tres defectos que solo se ven con el dedo encima, y una regla nueva del user.

**A · Qué pasa al decir «Aún no».** Primer intento: que la fila NO colapsara, para que el botón no
se fuera de debajo del dedo. Visto en device, peor: el paso aplazado se quedaba con su «Activar» y
había **dos pasos ofreciendo acción a la vez**, sin que se supiera cuál pedía la app (*«el usuario
ahora no sabe qué hacer»*).

Lo que hace ahora, dicho por el user: el paso aplazado **se cierra como todos los demás** —una línea,
sin botones—, el checklist avanza al siguiente, y **tocar esa línea lo retoma**: vuelve a ser el paso
actual con su CTA. Un aplazado es el único caso en que el tap NO abre la explicación: quien vuelve
ahí va a hacer lo que dejó, no a que se lo cuenten otra vez.

**B · La sheet se abría tarde y por el medio.** Dos cosas distintas:
- El checklist no puede mostrarse hasta que cargan preferencias, vehículos y permisos, así que el
  anchor llega un instante después que Home — y encima animaba. El usuario veía la sheet cerrada,
  luego abriéndose despacio, y concluía que no había tutorial. La **primera** apertura ahora hace
  `snapTo`: la sheet ya está abierta en el frame en que el checklist existe. Las siguientes siguen
  animando, porque ahí lo que se comunica es un CAMBIO.
- Y abría sobre el scroll que el usuario hubiera dejado. Toda apertura AUTOMÁTICA rebobina la lista
  al tope ANTES de mover la sheet: lo que la app abre por su cuenta está arriba. Las aperturas del
  usuario no le tocan el scroll.

**C · La explicación no repite cabecera.** Metida en la sheet, traía su propio molde completo
(eyebrow + título + ×) justo debajo de la cabecera de Home: dos cabeceras apiladas y solapadas. Dentro
de la sheet va sin eyebrow y sin ×; el título sostiene la página y «Entendido» es la única salida. De
paso, un eyebrow en blanco deja de pintar una línea vacía con su espaciado en cualquier `PapSheet`.

**D · ⛔ Nunca una modal encima de una modal** (regla del user: *«en todo caso debería de ser la misma
modal»*). El explainer era un `ModalBottomSheet` propio lanzado SOBRE la sheet de Home, que ya es
modal: dos capas, y un toque perdido tapaba la pantalla con algo que nadie pidió. Ahora, mientras hay
un explainer abierto, **la lista de la sheet ES el explainer** y «Entendido» devuelve el checklist. El
wrapper modal se borra (nadie más lo usaba) y el fichero se renombra a `FirstStepExplainer.kt`,
porque ya no es una sheet.

> El otro infractor de la misma regla tiene ya su ticket:
> `UI-THE-PARKING-CONFIRMATION-MODAL-IS-UNREACHABLE-001` — y al documentarlo resultó que además esa
> modal no la levanta nadie. (`HomeReleaseDialog` es un diálogo, otro caso.)

**Pendiente decidido por el user (03-09):** al tocar el paso 2.5 sale la explicación del paso 2,
porque se reutilizó la del watch. Visto en pantalla no pega. Queda por decidir si el 2.5 tiene
explicación propia o no tiene ninguna.

## Consumidores auditados

- **`resolveFirstSteps`** — 4 llamadas (estado, tests, galería, previews); todas pasan los parámetros
  nuevos explícitamente. Los defaults son el lado conservador: sin refuerzo y sin aplazados.
- **`FirstStep.entries`** — barrido completo: quedaban 3 sitios que asumían «los pasos son los del
  enum» (el contador de la cabecera, el bucle de filas de la tarjeta, `isComplete`). Los tres pasan
  por `applicable`. El `when (step)` de `onStartStep` y el del explainer los encontró el compilador.
- **`AppPreferences`** — 4 implementaciones (DataStore, iOS, fake de tests, fake del catálogo mock);
  las cuatro implementan el par nuevo. La clave de DataStore es nueva y ausente = conjunto vacío.
- **`HomeViewModel`** — gana `BluetoothScanner` para UNA pregunta, leída una sola vez (es IPC, y la
  respuesta solo cambia si el usuario empareja algo en los ajustes del sistema). Koin actualizado:
  la construcción explícita del VM pasa a 31 `get()`.
- **Tests del contrato viejo del paso 2** — 4 tests asumían `isWatching` como su condición. No se
  borran: se reescriben al contrato nuevo diciendo qué miden ahora y por qué.
