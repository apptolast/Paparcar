# MONEY-FOUNDERS-PLUS-PROMO-001 · El regalo lo concede el servidor, y sólo a quien llega al producto

**Estado:** ✅ Done (2026-11-21) · concesión server-side, cupo transaccional, devolución de plaza y
cierre el 30-11. ⏳ Sin desplegar y sin verse en un móvil: el gatillo es el build (la 1.6.0
publicada no lleva el claim).

## Problema

`MONEY-FOUNDERS-WELCOME-001` dejó el cliente listo para **contar** el regalo y **avisar** de su fin,
pero nadie lo **concede**. Medido el 20-11 en una auditoría end-to-end antes de anunciar la campaña:

```
$ grep -n "^export const" functions/src/index.ts
onSpotPublished · onSharedCarParked · ingestOccupancy · mergeCell     ← ninguna concede nada
$ firebase functions:list --project pap-26
las mismas 4
$ git log --all --oneline --grep=FOUNDERS -i
sólo los 3 commits de WELCOME
$ firestore promos/founders
{ active: true, cap: 2000, granted: 0, durationMonths: 3 }            ← configurado y sin lector
```

El 20-11, `promos/founders` estaba en producción con `active: true` y sin un disparador que lo
leyera. Esta observación pertenece a la auditoría previa al cambio.

### El parche que se daba por hecho NO existe

El doc de WELCOME (`money-founders-welcome-001.md:28-43`) decía que la concesión vivía en un parche,
`founders-plus-promo.patch`, en manos del user. **No está**, y la búsqueda es concluyente:

```
$ find /c/Users/rndev -iname "*.patch" -o -iname "*.diff"      → 4 resultados, ninguno es éste
$ grep -ril "grantFoundersPlus|revoke_promotionals|promos/founders" \
    ~/Downloads ~/Desktop ~/Documents ~/.android/studio/agent
→ 1 solo hit: .android/studio/agent/conversations/Paparcar-…/in_progress_steps.json
$ git stash list                                               → vacío
$ git fsck --lost-found | grep dangling   → 0 objetos con foundersPromo ni con el texto
```

Y ese único hit **no es el parche**: es una conversación donde se pega lo que dijo otra sesión
(*«Aplicar el parche `founders-plus-promo.patch`…»*) y un `grep` de ese mismo agente que encuentra
**sólo el doc de backlog**. La misma conversación dice *«la conexión con tu Chrome está caída»*.
El parche se describió en un navegador y nunca se escribió a disco.

**Consecuencia asumida:** la función se escribe aquí, desde cero. Y es mejor así, porque la
especificación cambió después (ver abajo): el parche habría sido incorrecto en los tres puntos.

## Doctrina violada

Ninguna, todavía — no hay código que la viole. Lo que sí hay son tres doctrinas que **gobiernan**
este diseño:

- **[MONEY-001] RevenueCat es la única fuente de verdad del entitlement.** Esta función escribe en
  RevenueCat y **Firestore sólo lleva la contabilidad del cupo**, nunca el permiso. El cliente no
  pregunta a Firestore si tiene Plus, y sigue sin hacerlo.
- **[DET-VERDICT-NOT-PREDICATE-001]** aplicada al servidor: el veredicto («esta cuenta reserva
  plaza / reanuda / llega tarde / no cabe») vive en una **función pura testeable**
  (`decideReservation`), no dentro del handler del trigger, que sólo hace I/O.
- **La ficha de Play y la web describen el build PUBLICADO.** Esta tarea **no las toca**: no cambia
  ningún límite del plan free ni ninguna feature que `paparcar.com` nombre. La página de condiciones
  de la promo y el enlace de campaña son una tarea aparte, y van **después** del despliegue.

## La especificación, con las tres decisiones del user (20-11)

| Qué | Decisión |
|---|---|
| **Cuándo se concede** | ⛔ **Al llegar a Home**, no al crear la cuenta. Un registro abandonado en el formulario del coche **no gasta plaza**. |
| **Cuenta borrada** | ⛔ **La plaza se devuelve.** El `uid` desaparece, el entitlement muere con él, y el cupo vuelve a tener ese hueco. |
| **Fin de captación** | **30/11/2026**, final del día en Madrid. El cierre exclusivo es **2026-11-30T23:00:00Z** (1 de diciembre a las 00:00 en Madrid). |

RevenueCat concede `plus` promocional durante **tres meses naturales**, sin tarjeta y sin renovación.
Como máximo hay **2.000 plazas** y cada cuenta puede ocupar una. La hora de llegada que cuenta para
el cierre es el sello que Firestore pone al aceptar la solicitud. Una solicitud previa al cierre se
puede procesar después. La reserva fija la fecha de caducidad y los reintentos conservan esa fecha.

## Señales / datos disponibles

- La auditoría del 20-11 encontró el documento `promos/founders` sin `endsAt` y con
  `granted: 0`. El lanzamiento necesita `endsAt` como Timestamp de Firestore.
- ✅ **`endsAt` escrito en producción el 21-11** (autorizado por el user), sólo ese campo vía
  `updateMask` y con precondición de que el documento existiera. Estado actual de
  `pap-26/(default)/promos/founders`:
  `{ active: true, cap: 2000, granted: 0, durationMonths: 3, endsAt: 2026-11-30T23:00:00Z }`.
  Verificado **ejecutando el veredicto** con esos valores, no leyendo el JSON:
  `decideReservation` pasa de `inactive` a `reserve`, da `reserve` a las 22:59:59Z del 30-N y
  `closed` a las 23:00:00Z. `endsAtMillis == FOUNDERS_CLOSE_MILLIS == 1796079600000`, así que el
  techo del código lo acepta (la validación rechaza `endsAtMillis > FOUNDERS_CLOSE_MILLIS`).
- ⚠️ **Y es INERTE**: comprobado el 21-11 que `functions:list` no tiene ninguna de las dos
  funciones y que el ruleset VIVO no contiene el bloque `foundersClaims` —lo deniega el catch-all—,
  así que hoy nadie lee ese documento. El gatillo real es el **build**: la 1.6.0 publicada no
  lleva el código del claim, así que no puede pedir nada aunque todo lo demás estuviera puesto.
- La auditoría encontró una versión habilitada del secreto `REVENUECAT_SECRET_KEY` en `pap-26`.
  La concesión y la revocación reales quedan pendientes de verificar con esa integración.
- `RevenueCatEntitlementRepositoryImpl.kt:147` → `ENTITLEMENT_PLUS = "plus"`. El App User ID **es**
  el uid de Firebase, así que el cliente encuentra el grant sin que nadie le diga nada.
- Auth v1 `onDelete` sigue existiendo en `firebase-functions` 7.4.0 (`lib/v1/providers/auth.d.ts:53`,
  leído del tarball) — es el único trigger que ve desaparecer una cuenta.

## Diseño

### La puerta: una colección propia, escrita por el cliente al llegar a Home

```
foundersClaims/{uid}   ← Home crea { claimedAt: serverTimestamp } durante la captación
     │ onDocumentCreated
     ▼
grantFoundersPlus      ← comprueba la cuenta y la solicitud → reserva → concede en RevenueCat
     │
     ├─ grants/{uid}         ← contabilidad exclusiva del servidor
     └─ foundersClaims/{uid} ← el servidor registra el resultado y su dueño puede leerlo
```

Las reglas limitan la creación al dueño, con el sello del servidor, mientras la configuración está
activa y el cierre es válido y futuro. El servidor vuelve a validar las condiciones del reparto.
Si otro dispositivo ya creó la solicitud, Home observa el mismo documento. La confirmación de
concesión incluye la fecha de caducidad y dispara una lectura fresca de `CustomerInfo` para ese
usuario. El cliente reintenta durante la misma sesión si el regalo todavía no aparece. RevenueCat
sigue decidiendo si el usuario tiene Plus.

Tres alternativas descartadas, y por qué:

- **Auth `onCreate`** (lo que pedía la spec vieja): dispara al registrarse, que es justo lo que el
  user excluyó. Un abandono en el alta de coche gastaría plaza.
- **Un campo en `users/{uid}`** (`reachedHomeAt`) con `onDocumentWritten`: no haría falta regla
  nueva, pero `users/{uid}` es un documento **caliente** — `updateDetectionState` lo escribe varias
  veces por viaje —, así que costaría una invocación de función por publicación de estado de
  detección, de todo el mundo, para siempre, por una promo que acaba el 30/11.
- **Un `onCall` callable**: exigiría meter `dev.gitlive:firebase-functions` en el catálogo.
  Una colección de escrituras puntuales permite reutilizar el Firestore del cliente. Firebase
  puede entregar el evento de creación varias veces, y el servidor deduplica la concesión.

### Reserva y reintentos

`decideReservation` evalúa la configuración, el sello de llegada y la concesión guardada. La
transacción comprueba que la solicitud sigue siendo la del evento y reserva la plaza al incrementar
el contador. Antes de contactar con RevenueCat, cada intento adquiere el derecho temporal a procesar
ese uid. Las entregas repetidas conservan la misma reserva y caducidad. El resultado sólo puede
cerrar el intento vigente.

Un rechazo que demuestra que la tienda no concedió el regalo permite marcar el fallo y devolver
la plaza en una transacción. Una respuesta de red ambigua mantiene la plaza reservada durante los
reintentos. Si se agotan, el estado `uncertain` sigue contando la plaza y el log
`founders promo: grant pending reconciliation` señala qué cuenta requiere contrastar el resultado
en RevenueCat antes de corregir la contabilidad.

### Borrado de cuenta

`releaseFoundersSlot` escucha el borrado individual de Firebase Auth en la primera generación.
Cierra la solicitud y coordina la revocación con cualquier concesión en curso. Cuando RevenueCat confirma
la revocación o un rechazo demuestra que la concesión en curso no se aplicó, el servidor devuelve
la plaza y borra el grant. Un fallo de la tienda
conserva el grant en `releasing` y la plaza queda pendiente de los reintentos de Auth. Un grant
fallido que ya devolvió su plaza se puede borrar sin otro descuento. La comprobación operativa está
en el [runbook de borrado manual](../legal/ACCOUNT-DELETE-RUNBOOK.md).

### Tres meses de calendario, con clamp

`addCalendarMonths` preserva la hora y **recorta el día al último del mes destino**. No es
cosmética: el 31-ago + 3 no es el 31-nov, y `setUTCMonth` desborda en silencio a diciembre. Y por
eso **no se usa `duration`** de la API de RevenueCat: está **deprecado** y su `three_month` son
**92 días fijos**, no tres meses.

## Criterio de éxito

- `npm test` en `functions/`. Verificar el cálculo de calendario, la aceptación antes del corte,
  el cupo, los reintentos y la devolución al borrar la cuenta.
- `npm test` en `firestore-rules-tests/` con el proyecto de emulación `demo-paparcar`. Las reglas
  protegen configuración y concesiones, y limitan la solicitud al dueño mientras la promo admite
  altas.
- `./gradlew testAll :app:compileProdDebugKotlin :app:compileMockDebugKotlin --console=plain` desde
  Bash. Incluye el caso de uso, el grafo mock y compilación de bibliotecas iOS. La prueba de iOS
  en dispositivo requiere un equipo Apple.

## Validación local

- `functions/`: `npm test` compiló TypeScript y ejecutó 98 pruebas, todas correctas con Node 24.
  El runtime declarado para Cloud Functions es Node 22.
- `firestore-rules-tests/`: `npm test` levantó el emulador con `demo-paparcar`. Pasaron 53 pruebas
  en siete archivos, incluidas las 16 de la promoción.
- Gradle: `testAll :app:compileProdDebugKotlin :app:compileMockDebugKotlin --offline --console=plain`
  pasó con 4.139 pruebas en `shared`, cuatro en `app` mock y compilación del klib iOS simulator arm64.
  La compilación de iOS no ejercita el enlace ni la app en un dispositivo.
- `:app:compileProdReleaseKotlin --offline --console=plain` pasó con un valor ficticio de Maps
  para compilar. La tarea no genera ni firma el AAB.

## Prueba sin consumir plazas de producción

Desde la raíz del worktree, ejecutar con el CLI local de `firestore-rules-tests`:

```powershell
.\firestore-rules-tests\node_modules\.bin\firebase.cmd emulators:exec --project demo-paparcar --config firebase.rules-tests.json --only 'auth,firestore' 'npm --prefix functions run test:founders-smoke'
```

La prueba usa la hora de los emuladores dentro de la ventana de la campaña. Auth y Firestore
son locales y RevenueCat está simulado. Comprueba el alta, el claim, el registro de una sola
concesión y el borrado con devolución de la plaza. Las pruebas de Kotlin cubren la llegada a Home,
la confirmación del regalo y la bienvenida. En un entorno de prueba con Firebase y RevenueCat
reales, usar proyectos separados de producción para verificar el SDK y ambas integraciones sin
conceder Plus a un usuario real.

## Lanzamiento

1. **Cupo: ya configurado** (ver §Señales). ⚠️ `active` se quedó en **`true`**, no en `false` como
   decía la primera versión de este paso, y la razón es que bajarlo añade un «acuérdate de
   subirlo» que es su propio modo de fallo — una promo que nunca arranca y nadie se entera. Lo que
   de verdad la mantiene cerrada es que **no hay build que sepa pedirla**: el gatillo es el
   versionCode nuevo, no este campo.
   Queda por verificar que la cuenta de servicio de `grantFoundersPlus` tiene
   `firebaseauth.users.get` y que ambas funciones acceden a `REVENUECAT_SECRET_KEY` (v3 ENABLED; su
   validez contra la API **sigue sin probarse**, sólo su existencia).
   ⛔ **Y el orden del despliegue no es indiferente: función ANTES que reglas.** Con las reglas
   puestas y la función ausente, un cliente crea `foundersClaims/{uid}` y nadie le contesta — se
   come el timeout de 20 s y reintenta en la siguiente entrada a Home. Al revés es inofensivo: sin
   la regla nadie puede crear el claim, así que la función queda esperando sin trabajo.
2. Cortar un AAB con un `versionCode` superior al mayor usado en Play. La versión 1.6.0 (17)
   se documentó en producción el 10-11 y es anterior a la solicitud desde Home. El `prodDebug`
   habitual usa la base `debug` y la clave de Test Store. Seguir el
   [proceso de release](../release/RELEASE-PROCESS.md) para el canal interno y la distribución.
3. Cuando el nuevo build esté disponible al público previsto y la integración se haya verificado,
   poner `active: true` para abrir la promo. Publicar las condiciones y el enlace de campaña en su
   tarea de lanzamiento.

## Revisión del 21-11 — cuatro defectos de la primera pasada

La primera pasada quedó a medias (se agotó el crédito a mitad de la verificación). Al medirla
entera salieron cuatro cosas, ninguna de ellas en el informe de cierre:

| # | Defecto | Arreglo |
|---|---|---|
| 1 | `testAll` **rojo**: `ArchitectureTest` → `AppViewModelTest.kt → data.repository.RevenueCatEntitlementRepositoryImpl`. El guard lee el paquete **en todos los source sets** a propósito (`PRESENTATION_PACKAGE_FLOOR = 80 // measured 163`) | El test de presentación vuelve a su capa: usa `FakeEntitlementRepository` y comprueba el **cableado** (pedir → refrescar una vez → felicitar una vez). Que una lectura fresca devuelva de verdad un grant `PROMOTIONAL` ya lo prueba `RevenueCatEntitlementRepositoryImplTest` en SU capa, así que no se pierde cobertura |
| 2 | `claim()` esperaba el `outcome` **sin tope**. Con la función sin desplegar —el estado de hoy— la corrutina se queda suspendida con un listener abierto toda la sesión de UI, sin un solo error | `withTimeoutOrNull(20 s)` → fallo transitorio. ⛔ No `withTimeout`: su excepción es una `CancellationException` y el `catch` de arriba la reenvía, así que se habría propagado como si el ViewModel hubiera muerto |
| 3 | **Bucle permanente tras el 30/11**: las reglas deniegan el `create` con la promo cerrada, y el caso de uso sólo marcaba el uid al tener éxito ⇒ toda cuenta nueva intentaba una escritura denegada en **cada** entrada a Home, para siempre | `PERMISSION_DENIED` se lee como respuesta **definitiva** (`NotGranted`), no como avería: el uid se marca y no se vuelve a preguntar. Se conserva el corte en la puerta, que es barato y falla cerrado |
| 4 | El smoke test **no lo ejecutaba nadie**: el glob de `npm test` es `lib/*.test.js` y no alcanza `lib/test/`, y su script propio lo lanzaba sin emuladores, así que fallaba siempre (`GCLOUD_PROJECT` vacío) | `npm test` encadena `test:emulated`, que lo corre bajo `emulators:exec --only auth,firestore`. Un testigo que no se ejecuta no es un testigo |

Medido después: **4144 Kotlin / 0 fallos** (`--rerun-tasks`), **98 + 1 smoke** en `functions`,
**53** de reglas. `ArchitectureTest` 6/6.

### 🟢 Cerrado el 21-11: `PROMOTIONAL` no es prueba de ser fundador

`plusMomentFor` enseñaba `FoundersWelcome` ante **cualquier** grant `PROMOTIONAL`, y eso sobrevivía
al cierre de la campaña: un Plus concedido a mano en el panel de RevenueCat —compensación de
soporte, un amigo, un tester— leía «eres uno de los 2.000 primeros», que es falso.

No era la vía principal. Un usuario que instale en diciembre no recibe nada (la regla deniega el
`create`, cubierto por `{ endsAt: new Date(0) }`), y los regalos a conocidos van por **códigos de
Play** → `PLAY_STORE` → `PURCHASED` → mudo. Era la puerta del panel, y no caducaba nunca.

**Ahora la frase exige prueba.** `foundersPromoGrantedUids` es la cuarta clave de preferencias de
esta familia, y se escribe por **una sola puerta**: el recibo del servidor con `outcome == granted`.
`plusMomentFor` gana `foundersUids` y la bienvenida la pide.

⛔ **Y es un conjunto propio, no un `Boolean` dentro de los otros tres**, porque contesta una
pregunta que ninguno contestaba: `foundersPromoClaimedUids` dice que se *preguntó*, no que se
concediera — se marca igual cuando la respuesta es «no».

⚠️ **Asimetría deliberada: el aviso de fin NO la exige.** «Tu Plus de regalo se acaba el 15» es
verdad para cualquier regalo con fecha, venga de donde venga, y callárselo a quien recibió uno del
panel sería dejarle sin aviso justo antes de que se le recorte el historial — el defecto que ese
aviso vino a arreglar. Lo que exige prueba es la frase que **afirma un hecho sobre la campaña**.

⛔ **Y la palanca del Dev Catalog habría quedado muda.** «Plus de REGALO (promo Fundadores)»
enciende un grant promocional, no una concesión de la campaña, así que con el gate puesto y el fake
contestando `NotGranted` la palanca se quedaría encendida **sin efecto visible** — leído como que
la feature está rota. `FakeFoundersPromoRepository` pasa a ser scenario-aware: el recibo lo decide
la palanca y el resto del camino es el de producción (el caso de uso real apunta, refresca y
felicita). Verificado con `assembleMockDebug`.

**Consumidores barridos:** `AppPreferences` + sus 4 implementaciones (Android DataStore, iOS
`NSUserDefaults`, fake de `commonTest`, fake de `:mockkit`), `plusMomentFor`, `AppViewModel`,
`ClaimFoundersPromoUseCase`, `MockModule`. `USER_SCOPED_KEYS` **exento a propósito**, como sus tres
hermanas: la clave está indexada por uid, así que no contamina a la cuenta siguiente.

**Testigos, y falsificados:** `PlusMomentTest` 14 → **17** (el grant del panel, el fundador que es
otra cuenta, y la asimetría del aviso) y `ClaimFoundersPromoUseCaseTest` 8 → **11** (se apunta sólo
al conceder, y se apunta **antes** de reconciliar para que un RevenueCat lento no borre la prueba).
Quitando la línea del gate, los dos primeros se ponen **rojos** — comprobado, no supuesto.

## Consumidores auditados

`grep -rln ": AppPreferences"` → la clave nueva obliga a las cuatro implementaciones, igual que las
dos de WELCOME:

| Sitio | Estado |
|---|---|
| `AndroidDataStoreAppPreferences` | **implementa** (`stringSetPreferencesKey` + `addToSet`) |
| `IosAppPreferences` | **implementa** (`stringArrayForKey`, persistido) |
| `shared/commonTest/fakes/FakeAppPreferences` | **implementa** |
| `mockkit/.../FakeOtherDataSources` | **implementa** |
| `USER_SCOPED_KEYS` (Android + iOS) | **exento a propósito**: la clave está indexada por uid, así que no contamina a la cuenta siguiente — misma razón que las dos de WELCOME |

Quien decide con el cupo o con el regalo:

| Sitio | Veredicto |
|---|---|
| RevenueCat | Actualiza `CustomerInfo` tras la concesión y sigue dando el entitlement y su origen |
| Gates y `ObserveIsPlusUseCase` | Usan el mismo entitlement Plus |
| `AppViewModel` | Conecta la llegada a Home con la cuenta actual y la solicitud |
| `PlusMoment` y `PaywallViewModel` | Consumen el regalo cuando RevenueCat lo confirma, como en WELCOME |
| `DeleteAccountUseCase` | **sin tocar** — la plaza la devuelve el trigger de Auth, no el cliente: un borrado desde la Console o desde el runbook tiene que contar igual |
| `ACCOUNT-DELETE-RUNBOOK.md` | **entra**: hay dos documentos nuevos que el borrado manual debe dejar al trigger, y hay que decir que NO se borran a mano |
| web / ficha de Play | **exentos y justificados**: ningún límite free cambia y la promo no está publicada |
