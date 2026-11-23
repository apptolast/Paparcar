# ONB-A-WATCH-NEEDS-A-CAR-PARKED-FIRST-001 · El primer acceso pide vigilar antes de haber marcado qué vigila

**Estado:** ✅ Done · pendiente de squash a master

> Decisión tomada y verificada: orden invertido (parking antes de detección), pitch solo tras
> confirmar el pin, copy nuevo en 9 locales. Ver «Recorrido visual» y «Consumidores auditados».

## Problema

Pregunta del user (23-11): *¿el diálogo de detección automática debería salir antes o después de
establecer el aparcamiento?* Y el porqué: *hay que dar a entender al usuario que para que funcione
la detección tiene que, al principio, marcar su aparcamiento y activar la detección automática
(que se hace con permisos)*.

Hoy, en la primera visita a Home, `resolveHomeEntryPrompt`
(`HomeFirstEntryFlow.kt:88-95`) resuelve en este orden:

```
PLUS → DETECTION → FIRST_PARKING → REFUEL → AD_MEASUREMENT
```

`DETECTION` es el diálogo-pitch («Lo mejor de Paparcar… Actívalo ahora y olvídate»,
`home_det_pitch_title/body`) que invita a conceder los permisos PRODUCER (actividad, ubicación en
segundo plano, notificaciones, Bluetooth). `FIRST_PARKING` es la apertura automática del editor real
(`HomeMode.AddingParking`) para que el usuario marque dónde ha dejado el coche AHORA.

Es decir: hoy se pide activar la vigilancia **antes** de que exista nada que vigilar. El pitch no
tiene un coche aparcado al que referirse — ni en los datos (`activeSessions` puede estar vacío) ni
en el copy (genérico: «recuerda dónde aparcaste», en abstracto). Si el usuario acepta, entra en el
flujo de permisos PRODUCER —el más invasivo del onboarding— sin haber hecho todavía ni un solo gesto
propio del producto.

## Doctrina y decisiones previas en juego

- **`ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001`** (09-09-2026, done) ya resolvió esto una vez a
  otro nivel: sacó el muro de permisos del arranque («el mapa pide, la app convence») y fijó el
  disparador actual — el pitch de detección se arma en cuanto se concede la ubicación nativa sobre
  el mapa, descrito como *«el momento de máximo compromiso»*. Esa heurística es anterior a que
  existiera el editor automático de aparcamiento en la primera visita (ese llegó con
  `HOME-FIRST-ENTRY-FLOW-001`, 22-11) y nunca se repesó contra él.
- **`HOME-FIRST-ENTRY-FLOW-001`** (22-11-2026, done) insertó `FIRST_PARKING` en la cadena, pero lo
  colocó DETRÁS de `DETECTION` sin discutir el orden relativo — el doc solo dice que el editor es
  «el último paso del recorrido inicial», heredando la posición que ya tenía el pitch.
- Ninguna de las dos decisiones pesó la pregunta de fondo: *¿tiene sentido pedir que se active una
  vigilancia antes de que el usuario haya dicho qué se vigila?*

## Señales / datos disponibles

- `shouldOfferInitialParkingEditor` (`HomeState.kt:731`) no depende de permisos: solo de que haya
  vehículo, no haya sesión activa y el paso `MARK_PARKING` sea el actual del checklist.
- `showDetectionPitch` se arma en `HomeViewModel.subscribePermissions` (`HomeViewModel.kt:2150-2154`)
  mediante el flag `pitchDetectionAfterGrant`, consumido al primer grant de ubicación — **no** mira
  si hay un aparcamiento activo.
- Doctrina rectora de detección: *el evento NOMINA, solo el movimiento MEDIDO confirma* — el arranque
  (`AR ENTER` o `GEOFENCE_EXIT`) puede darse sin que exista YA un aparcamiento previo marcado a mano
  (el primer aparcamiento de la cuenta puede confirmarse por el propio pipeline automático, en el
  próximo viaje). **No hay bloqueo técnico** que exija un `UserParking` existente para que la
  detección empiece a funcionar.
- Lo que sí falta sin un aparcamiento marcado es **algo concreto que mostrar**: el pitch de hoy no
  puede decir «tu coche está aquí», porque no lo sabe.

## Razonamiento

La pregunta no es técnica (la detección arma igual con o sin `UserParking` previo) — es de
**claridad y coste percibido**:

1. **El pitch de hoy vende algo abstracto.** «Automatic detection remembers where you parked» no
   tiene antecedente: el usuario aún no le ha dicho a la app dónde ha aparcado nada. Pedirle el
   permiso más invasivo del onboarding (PRODUCER) para una promesa sin objeto es exactamente el
   mismo error que `ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001` ya corrigió una vez —pedir antes
   de mostrar valor—, un piso más abajo.
2. **Invertir el orden da al pitch un antecedente real.** Si `FIRST_PARKING` ocurre primero, el
   usuario ya ha marcado su coche en el mapa — ha hecho el gesto central del producto — antes de que
   se le pida nada. El pitch puede entonces decir algo concreto («ya sabemos dónde está tu coche;
   actívalo para no tener que repetirlo la próxima vez») en vez de una promesa en el aire. Esto es
   literalmente lo que pide el user: marcar el aparcamiento primero, activar la detección después.
3. **El argumento a favor del orden actual es un solo dato**: el pitch se dispara justo tras un «sí»
   del usuario (el permiso de ubicación), aprovechando inercia. Pero esa inercia conecta dos
   peticiones de PERMISO sin relación temática entre sí (ubicación del mapa → vigilancia del coche).
   Encadenar «marcaste tu coche» → «vigílalo automáticamente» es una secuencia con relación causal
   real, que debería producir una conversión igual o mejor sin pagar el coste de pedir sin motivo.
4. **No hay coste de abandonar la heurística del «momento de compromiso» ligada a la ubicación**:
   esa heurística nace de necesitar ANCLAR el pitch a algún evento observable. El cierre/confirmación
   del editor de aparcamiento es un evento igual de observable y más pertinente.

## Recomendación

Invertir el orden: `FIRST_PARKING` antes de `DETECTION`. El pitch de detección deja de dispararse al
grant de ubicación y pasa a dispararse al cerrar el editor inicial de aparcamiento (confirmado o
cerrado sin guardar — ver pregunta abierta debajo).

## Preguntas para el user — resueltas (23-11)

1. ~~¿El pitch solo se ofrece si el usuario CONFIRMÓ el pin, o también si cerró el editor sin
   guardar?~~ **Solo si confirmó.** Un usuario que cierra sin guardar no ve el pitch en esta
   visita — lo recupera el banner/checklist más tarde, como ya pasa con `MARK_PARKING` pospuesto.
2. ~~¿Tocamos el copy para nombrar el aparcamiento recién marcado, o dejamos el texto genérico?~~
   **Se nombra.** `home_det_pitch_body` pasa a referenciar el coche recién marcado, en los 9
   idiomas.
3. ~~¿Se abandona la heurística «momento de máximo compromiso»?~~ **Aclarado**: no tiene nada que
   ver con el permiso de ubicación en primer plano (ese sigue pidiéndose exactamente igual, antes
   de cualquier diálogo propio de la app — sin él no hay mapa ni cámara para colocar el pin).
   Se refería solo al disparador interno del pitch de detección, que queda documentado como
   superado específicamente en este flujo, sin tocar nada más.

## Diseño — implementado

- `HomeFirstEntryFlow.kt`: `resolveHomeEntryPrompt` reordenado →
  `PLUS, FIRST_PARKING, DETECTION, REFUEL, AD_MEASUREMENT` (ambas ramas del `when`, más el enum
  por legibilidad). KDoc actualizado con el porqué.
- `HomeViewModel`: `pitchDetectionAfterGrant` retirado por completo (campo + los dos sitios que lo
  armaban/consumían). El pitch arma ahora dentro de `confirmAddParking().onSuccess`, solo cuando
  `state.value.initialHomeParkingEditorActive` es cierto (viene de la oferta de primera visita, no
  de un aparcamiento cualquiera) y `!permissionManager.permissionState.value.hasProducerPermissions`.
  `ExitAddParkingMode` (cerrar sin guardar) sigue llamando a `completeFirstHomeParkingFlow()` pero
  nunca arma el pitch — por construcción nunca sale sin una sesión activa detrás.
- Copy: `home_det_pitch_body` reescrito en los 9 locales para nombrar el coche recién marcado
  (título y CTA sin cambios).
- Tests: `HomeFirstEntryFlowTest` — 2 aserciones actualizadas al orden nuevo + 1 test nuevo
  (`should_offerParkingBeforeDetection_when_bothAreEligible`) que fija FIRST_PARKING < DETECTION
  explícitamente. `HomeViewModelTest` — los 2 tests del disparo viejo por grant se reescriben a 3
  tests del disparo nuevo (confirmar arma, cerrar sin guardar no arma, permisos ya concedidos no
  arma).
- Dev Catalog: sin cambios — el pitch sigue sin estar en la galería (interacción pura); el preset
  mock usa el mismo `HomeViewModel`, así que hereda el disparo nuevo sin tocarlo.
- Web/ficha de Play: no aplica.

## Recorrido visual (23-11, emulador `emulator-5554`, flavor mock)

No había móviles físicos conectados (`adb devices -l` solo listaba el emulador); el user eligió
seguir con el emulador. `assembleMockDebug` instalado y verificado por sha256 device↔local
(`021cc8f4…`). Preset `Primer Home · cadena completa` (`DevCatalogScreen.kt:322`), que arranca sin
permisos para que el «sí» del diálogo nativo dispare la cadena real.

Secuencia observada, leída con `uiautomator dump` tras cada paso:

1. Diálogo nativo de ubicación → «Mientras se usa la aplicación».
2. Bienvenida de Plus/Fundadores («Eres de los 2.000 primeros») → «¡Vamos!».
3. **Editor de aparcamiento, automático** — «Arrastra el mapa para centrar el pin en tu coche» /
   «Aparcar aquí». Ni rastro del pitch de detección todavía.
4. Confirmar → diálogo «¿Aparcar aquí?» (`UI-A-PLACED-PIN-ASKS-BEFORE-IT-STICKS-001`) → aceptar.
5. **Pitch de detección, justo después**, con el copy nuevo: *«Acabas de marcar dónde está tu
   coche. Activa la detección automática y Paparcar se dará cuenta cuando te vayas — liberando la
   plaza para otra persona, sin que tengas que hacer nada.»*
6. «Ahora no» → vuelve a Home; «Mi coche · aparcado hace &lt;1m» visible; checklist de primeros
   pasos presente. `logcat` sin `FATAL`/`AndroidRuntime` de la app.

Confirma en hardware (emulado) exactamente el orden y el antecedente que pedía el user: primero el
aparcamiento, después la invitación a vigilarlo.

## Criterio de éxito

- Una cuenta nueva con vehículo ve primero el editor de aparcamiento (si el checklist lo tiene como
  paso actual) y el pitch de detección llega después, nunca antes.
- El pitch, cuando se ofrece, no puede ya nacer antes de que `FIRST_PARKING` se haya resuelto en esa
  misma visita.
- Tests de `HomeFirstEntryFlowTest` y `HomeViewModelTest` reescritos al contrato nuevo, verdes.
- `./gradlew testAll`, `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` verdes.
- Recorrido visual en device/emulador con una cuenta nueva: se confirma el orden en pantalla, no solo
  en test.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `HomeFirstEntryFlow.kt` (`resolveHomeEntryPrompt` + enum) | ✅ reordenado |
| `HomeViewModel` (`pitchDetectionAfterGrant` retirado, disparo movido a `confirmAddParking`) | ✅ |
| `HomeEntryPromptHost.kt` (comentario sobre el orden) | ✅ actualizado |
| `HomeState.kt` (doc de `showDetectionPitch`) | ✅ actualizado |
| `HomeFirstEntryFlowTest` | ✅ reescrito (2 aserciones + 1 test nuevo) |
| `HomeViewModelTest` (2 tests del pitch, de `HOME-FIRST-ENTRY-FLOW-001`) | ✅ reescritos a 3 tests |
| `home_det_pitch_body` ×9 locales | ✅ |
| Dev Catalog / `StateGalleryScreen` | ✅ sin cambio necesario (pitch fuera de la galería) |
| Web / ficha de Play | ✅ no aplica |
| `./gradlew testAll` | ✅ verde |
| `:app:compileProdDebugKotlin` / `:app:compileMockDebugKotlin` | ✅ compilan |
| Recorrido visual en emulador | ✅ verificado (23-11) |
