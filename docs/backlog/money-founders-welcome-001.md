# MONEY-FOUNDERS-WELCOME-001 · El Plus que te regalan tiene que decirte que te lo han regalado

**Estado:** ✅ Done (2026-11-20) · Bienvenida y aviso de caducidad en el cliente.

> Validado en Pixel 8 con «Plus de REGALO» del Dev Catalog. El ajuste final de la cresta se
> comprobó en el paywall mock tras compilar `prodDebug` y `mockDebug`.

## Problema

La promo «Fundadores» está diseñada para conceder el entitlement `plus` como **promocional** en
RevenueCat a las 2.000 primeras cuentas, durante 3 meses. El App User ID coincide con el uid de
Firebase. Antes de este cambio, cuando el SDK consultaba `CustomerInfo` y encontraba el regalo,
**al usuario le aparecía Plus y nadie se lo explicaba**.

Tres defectos, no uno:

1. **Nadie se entera.** El historial deja de recortarse a 7 días, las alertas de zona se pueden
   encender, cabe un segundo coche… y el usuario no sabe por qué. Un regalo que no se nombra no se
   agradece, y lo que es peor: no se puede echar de menos.
2. **Nadie sabe que se acaba.** A los tres meses el historial se recorta y las alertas se apagan
   solas. Sin aviso previo eso se lee como una avería de la app, no como el fin de una promo.
3. **El paywall se cierra en la cara de quien sí querría pagar.** `PaywallScreen` tiene
   `LaunchedEffect(state.isPlus) { if (state.isPlus) onClose() }` — «a un usuario Plus no le queda
   nada que venderle». Para un Plus **promocional que se termina** eso es falso: lo que le queda
   por vender es justo la continuación, y hoy no hay forma de llegar a esa pantalla. El carril del
   punto 2 sería un recordatorio sin salida.

### El lado SERVIDOR de la promo llega en otro ticket

La concesión server-side **no está en este repo todavía** —y no es que faltara, es que no se había
aplicado: el user la tiene en un parche, `founders-plus-promo.patch`, ticket
**MONEY-FOUNDERS-PLUS-PROMO-001**, que se abre en su propio worktree cuando este cierre (aplicar el
parche + lo que diga su doc: verificar el endpoint de RevenueCat, `npm test`, el secreto y el deploy
guiado). Medido el 20-11 antes de empezar: `functions/src/` no tiene nada de promo, los `export` de
`index.ts` son `onSpotPublished` · `onSharedCarParked` · `ingestOccupancy` · `mergeCell`,
`git log --all --grep=FOUNDERS` está vacío, y los tres documentos que la mencionan
(`docs/backlog/money-001.md:38`, `docs/ROADMAP-POST-PLUS.md:31,182`) la siguen poniendo fuera de
alcance.

**Por eso los dos lados son dos tickets y no uno**, y es la frontera limpia: el cliente no sabe
—ni debe saber— QUIÉN concedió el entitlement. Lee `EntitlementInfo.store`, y eso vale igual para
un grant de la Cloud Function, uno hecho a mano en el panel o uno de la API REST. Este ticket queda
correcto y verde con el parche sin aplicar, y empieza a tener efecto visible el día que la
concesión exista.

**Consecuencia para el corte:** mientras la concesión no esté desplegada, **esto no se anuncia**
fuera de la app (ni ficha de Play ni web) — `CLAUDE.md` § *la ficha describe el build PUBLICADO*. Y
es la razón por la que esta tarea **no toca la web**: no cambia ningún límite del plan free ni
ninguna feature que `paparcar.com` nombre.

### ⛔ Un CÓDIGO DE PLAY no es un fundador (decisión de producto del user, 20-11)

Los Plus que el user regale a conocidos salen con **códigos promocionales de Google Play**, no desde
el panel de RevenueCat, **justo para que a esa gente no le salga el mensaje de los 2.000**.

Eso convierte el `when` del mapeo en un requisito, no en un detalle de implementación, y lo
afortunado es que la discriminación ya cae del lado correcto sin tocar nada: un código de Play
**compra una suscripción de Play de verdad**, así que RevenueCat reporta `Store.PLAY_STORE` →
`PlusOrigin.PURCHASED` → ni bienvenida de fundador ni aviso de fin de promo. `Store.PROMOTIONAL` es
exclusivamente lo que se concede DENTRO de RevenueCat, que es lo que hará la Cloud Function.

⛔ **Y por eso el mapeo pasa a tener testigo.** No tenía ninguno —`RevenueCatEntitlementRepositoryImpl`
era la única clase de datos sin test— y es el ÚNICO sitio de la app que distingue los dos regalos:
por debajo de la capa de datos los dos son `Entitlement.PLUS`, y el dominio sólo ve `PURCHASED` o
`PROMOTIONAL`, así que un test de dominio no puede ver la diferencia ni aunque quiera.
`RevenueCatEntitlementRepositoryImplTest` + `FakeRevenueCatDataSource` construyen `CustomerInfo`
reales y fijan las dos filas que importan:

| Lo que dice la tienda | Origen | Qué ve el usuario |
|---|---|---|
| `PROMOTIONAL` (grant en RevenueCat) | `PROMOTIONAL` | bienvenida de fundador + aviso a D-7 |
| `PLAY_STORE` (código canjeado, también en periodo `INTRO`) | `PURCHASED` | **nada** — es una compra |
| cualquier otra tienda, `UNKNOWN_STORE` incluida | `PURCHASED` | nada |

Si esa segunda fila se rompe, **todos los conocidos que canjeen un código leen que son uno de los
2.000 primeros**, que es falso. Era una prohibición sin testigo y ahora no lo es.

## Doctrina violada

- **[MONEY-001] RevenueCat es la única fuente de verdad.** Se respeta y se refuerza: el origen y la
  caducidad **no se espejan en Firestore ni en Room**, se leen del `CustomerInfo` como todo lo demás.
  Lo único que se persiste en local es *«a esta cuenta ya se le dijo»*, que no es estado de
  suscripción.
- **[PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001].** El punto 3 es su cara B: un carril que cobra
  tiene que ser alcanzable. Un recordatorio de fin de promo con el paywall autocerrándose sería
  vender una puerta tapiada.
- **⛔ Nunca modal sobre modal** (memoria `feedback_no_modal_over_modal`). Una cuenta **nueva** es
  exactamente la que dispara a la vez la pregunta de medición de anuncios
  ([MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]) y esta bienvenida. No es un caso raro: es **el**
  caso. El orden tiene que estar escrito, no quedar al azar de la composición.
- **Copy sin mecánica interna.** El usuario no lee «promocional», «entitlement» ni «RevenueCat».
  Lee que es uno de los 2.000 primeros y hasta cuándo.
- **Soft paywall, precio honesto.** El recordatorio dice la fecha y abre la puerta; no cuenta atrás
  con prisa, no apaga nada antes de tiempo, no esconde el «ahora no».

## Señales / datos disponibles

`CustomerInfo.entitlements.active["plus"]` es un `EntitlementInfo` (purchases-kmp 3.14.0, leído del
`-sources.jar`) y ya trae todo lo que hace falta:

| Campo | Para qué |
|---|---|
| `store: Store` | `PROMOTIONAL` = nadie pagó por esto. Las tiendas de verdad son `PLAY_STORE`/`APP_STORE`/… |
| `expirationDateMillis: Long?` | Cuándo se acaba. `null` = sin fin conocido (vitalicio) |
| `periodType` / `willRenew` / `ownershipType` | **No se usan**, ver «Diseño» |

Y en local, `AppPreferences`, que ya tiene el molde de los dos mecanismos que necesito: un flag
«ya se dijo» (`hasSeenGpsAccuracyDisclaimer`) y un `Set<String>` persistido
(`observeDissolvedSharedCars`).

## Diseño

### 1 · El dominio gana el ORIGEN y la CADUCIDAD, no un `Store`

`Entitlement` (enum `FREE`/`PLUS`) sigue siendo la respuesta a *¿qué puede hacer este usuario?*, que
es lo único que preguntan los seis gates. La pregunta nueva es otra: *¿CÓMO llegó y hasta cuándo?*

```kotlin
// domain/model/PlusGrant.kt
data class PlusGrant(val origin: PlusOrigin, val expiresAtMillis: Long?)
enum class PlusOrigin { PURCHASED, PROMOTIONAL }
```

- `EntitlementRepository` gana **un** miembro: `observePlusGrant(): Flow<PlusGrant?>` (null = free).
- ⛔ **`observeEntitlement()` pasa a ser su PROYECCIÓN, no un segundo stream.** En el impl hay un
  solo `MutableStateFlow<PlusGrant?>` y el enum sale de un `.map`. Dos flujos independientes sobre
  el mismo hecho divergen el día que uno se actualice y el otro no; así es imposible por
  construcción, y ningún gate existente se entera del cambio.
- **Dos valores en `PlusOrigin`, no tres.** `PURCHASED` cubre todo lo que una tienda vende —el
  trial de 7 días incluido, que es un plan de pago en su primer periodo— y `PROMOTIONAL` cubre lo
  que nadie pagó. Un `TRIAL` tercero no tendría consumidor: la bienvenida pregunta «¿lo regalamos
  nosotros?», y a esa pregunta el trial de la tienda responde no.

### 2 · El veredicto es UNA función pura que devuelve UN momento

`domain/entitlement/PlusMoment.kt`, al lado de los gates del área, que ya son funciones puras y no
clases inyectadas (`ZoneEntitlementGate`, `HistoryEntitlementGate`, `VehicleEntitlementGate`).

```kotlin
sealed interface PlusMoment {
    data class FoundersWelcome(val expiresAtMillis: Long?) : PlusMoment
    data class PromoEnding(val expiresAtMillis: Long) : PlusMoment
}

fun plusMomentFor(grant, uid, welcomedUids, endingNoticedUids, nowMs): PlusMoment?
```

⛔ **Los dos casos llevan la FECHA y ninguno un contador de días.** Un «te quedan N días» necesita
singular y plural en nueve idiomas y Compose Resources no tiene plurales aquí, así que el primer
locale que se despistara enviaría «1 días». La fecha es además el dato que el usuario usa.

⛔ **Un momento, no dos booleanos.** Dos predicados independientes pueden ser verdad a la vez y eso
es justo lo prohibido: dos modales. Un `sealed` con un solo valor hace que «los dos» no sea
representable. Es el mismo argumento que puso icono y frase en un solo `ReasonLine` en el paywall.

Las reglas, todas en esa función y todas con test:

| Caso | Momento |
|---|---|
| Plus promocional, cuenta sin felicitar | `FoundersWelcome` |
| Plus promocional, ya felicitada, > 7 días para el fin | — |
| Plus promocional, ya felicitada, ≤ 7 días y sin avisar | `PromoEnding` |
| Plus promocional **sin felicitar** y ya a ≤ 7 días del fin | `PromoEnding` ⚠️ |
| Plus comprado | — |
| Free / sin sesión | — |

⚠️ La cuarta fila es la que no se ve venir: alguien que instala con la promo a punto de acabarse.
Felicitarle por «3 meses gratis» cuando le quedan cinco días sería una frase falsa, así que se salta
la bienvenida y se le da el aviso.

⛔ **Al cerrar un momento se apunta SÓLO su propia memoria**, no las dos. Dentro de la ventana del
aviso la función no consulta `welcomedUids`, así que un aviso ya leído ya silencia a la bienvenida
sin necesidad de marcarla — y si la promo se EXTENDIERA (el panel de RevenueCat lo permite),
marcarla habría enterrado para siempre una felicitación que volvería a ser verdad. Una escritura
por momento, decidida por su tipo.

**El veredicto lleva la fecha dentro.** La UI no vuelve a derivar nada del grant: recibe el momento
y lo pinta. Un `daysLeft` recalculado en el call site es un segundo cálculo del mismo hecho.

### 3 · Dónde y cuándo se ve: la raíz, cuando el entitlement llegue

`AppViewModel` + `App.kt`, exactamente donde ya vive la pregunta de medición de anuncios, y por sus
mismas tres razones:

- Es **el único ViewModel vivo toda la sesión de UI**, en cualquier pestaña. `combine(grant, uid)`
  reacciona cuando el SDK obtiene el grant, incluso después del primer frame. Una concesión de
  servidor requiere que el SDK vuelva a consultar `CustomerInfo`. La prueba de alta del ticket
  de servidor debe comprobar si la bienvenida aparece en la primera sesión o al reabrir.
- `currentTabRoute in BOTTOM_NAV_ROUTES` ya es la condición que **no interrumpe** onboarding,
  permisos ni alta de vehículo. No hace falta inventar nada: la celebración espera a que el usuario
  esté en una pestaña.
- **El orden con la pregunta de anuncios se escribe en el estado**, no en la composición:
  `AppState.plusMomentToShow` devuelve null mientras `askAdMeasurementConsent` esté abierta. Así es
  testeable y no depende de quién se componga primero. La pregunta va delante porque es una
  pregunta —exige respuesta y bloquea el toque fuera—; la celebración es una noticia y espera.

Diálogo y no bottom sheet: en Home la sheet YA es una superficie modal, y abrir otra encima es la
infracción exacta de la memoria. `PapAlertDialog` es el molde de la app, flota sobre cualquier
pestaña, y su `heroContent` admite la cresta de metal.

### 4 · Una sola vez por cuenta: `Set<String>` de uids, y NO se borra al salir

```kotlin
val plusPromoWelcomedUids: Set<String>;      fun addPlusPromoWelcomedUid(uid: String)
val plusPromoEndingNoticedUids: Set<String>; fun addPlusPromoEndingNoticedUid(uid: String)
```

- **Un `Set` de uids y no un booleano**, porque la pregunta es «¿se le dijo **a esta cuenta**?». Con
  un booleano, dos cuentas en el mismo móvil —que es literalmente el setup de field-test de este
  proyecto— compartirían la felicitación: la segunda no la vería nunca.
- ⛔ **NO entra en `USER_SCOPED_KEYS`** (el barrido de `clearUserScopedState` al cerrar sesión), y
  es la decisión fina de este bloque. La regla de ese barrido es *«lo que describe al USUARIO se
  va»*… y esto describe al usuario. Pero está **indexado por uid**, así que quedarse no filtra nada
  a la cuenta siguiente —la suya no está en el set— y borrarse sí rompe el invariante: cerrar sesión
  y volver a entrar re-felicitaría. Un flag indexado por uid es un flag de este INSTALL sobre lo
  que ya dijo.
- **Dos sets, no un mapa uid→fase.** Son dos preguntas distintas y la cuarta fila de la tabla de
  arriba necesita poder marcar una sin pasar por la otra. Es el patrón ya establecido por
  `firstStepsDone` / `firstStepsDeferred` («deferred is not done», dos sets, nunca fundidos).
- Reinstalar vuelve a felicitar. Asumido: el encargo dice que la persistencia local por uid es
  suficiente, y la alternativa (un campo en el perfil de Firestore) cambiaría la frontera de
  [MONEY-001] por un caso que no duele.

### 5 · Al caducar: el aviso suave, y el paywall que por fin se deja abrir

- **D-7, una vez, mismo molde que la bienvenida.** Dice la fecha, qué deja de estar, y ofrece
  continuar. El CTA primario es **«Ahora no»**: el recordatorio existe para que nadie se lleve una
  sorpresa, no para cobrar hoy.
- **`PaywallScreen` deja de autocerrarse cuando el Plus se termina.** La condición pasa de
  `isPlus` a `isPlus && !plusEnds`: si hay algo que vender, la pantalla se queda. Se cierra sola
  igual que siempre en cuanto el grant pasa a `PURCHASED` (la compra entra por el stream, como
  cualquier otra).
- Y **el paywall lo dice**: una línea sobre los planes con la fecha en la que acaba el Plus de
  fundador. Abrir una pantalla que grita «Consigue Plus por 13,99 €» a quien ya lo tiene, sin
  nombrar que se le acaba, es el tipo de pantalla que enseña a desconfiar de la siguiente.
- **Ajustes dice lo mismo en frío**: la fila de Plus tiene ahora **tres** estados y no dos —gratis,
  Plus comprado, y Plus de REGALO, que es el único que trae fecha y por eso el único que la dice—,
  con galón `REGALO` y chevron a la vez (el galón dice qué tiene, el chevron que se puede entrar).
  Hoy `onClick = if (isPlus) null` la apaga: correcto para quien paga, equivocado para quien se
  queda sin.
  ⛔ Y **vuelve a ser puerta durante toda la promo, no sólo en los últimos 7 días**. El umbral es
  del aviso, que INTERRUMPE; una fila de Ajustes a la que el usuario ha ido por su cuenta no tiene
  por qué volverse pulsable de repente un martes, y un segundo sitio donde vive ese umbral es un
  segundo sitio donde se puede desincronizar.

### 6 · La lista de ventajas **no se escribe aquí**

Se pinta desde `PLUS_BENEFITS` (`presentation/paywall/PaywallScreen.kt`), que es la lista que ya
sostiene el paywall y que `PaywallBenefitsCoverageTest` obliga a cubrir cada `PaywallReason`. Una
segunda lista escrita a mano sería la misma avería que ese test existe para cazar
([PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]: `ROUTE_CLOUD` cobrado once días sin que nadie lo
vendiera), pero al revés: una promesa que la app ya no cumple seguiría viva en la bienvenida.
Y **una lista, dos lecturas**, que es lo que hace que el aviso de fin de promo no necesite ni una
línea de copy nueva: la **bienvenida** enseña sólo los TÍTULOS (no hay nada que comparar cuando lo
tienes todo) y el **aviso** enseña además la NOTA de cada fila —«Gratis: los últimos 7 días»,
«Gratis: un coche»—, que es literalmente la respuesta a la única pregunta que ese aviso levanta.
Para eso `PlusBenefit`, `PLUS_BENEFITS` y la fila salen de `PaywallScreen.kt` a
`presentation/paywall/PlusBenefits.kt`, **movidos y no copiados**, y la fila gana un `showNote`.

### 6b · La bienvenida tiene UN botón, y no es el paywall

El segundo CTA obvio —«ver qué incluye Plus»— apuntaría a la pantalla que VENDE Plus a alguien a
quien se le acaba de regalar, el día uno. La lista de lo que incluye está en el propio diálogo, que
es la forma honesta de atender ese deseo; la puerta a los planes es del aviso de fin de promo, donde
sí hay algo que decidir. Soft paywall significa que lo abre el GATE, no el regalo.

### 7 · La fecha, en el formato que la app ya sabe decir en nueve idiomas

El encargo propone «DD/MM/AAAA». No: ese orden es falso en en-US y el repo ya tiene resuelto el
problema con `MONTH_SHORT_RES` (12 claves × 9 locales) — así se escribe la fecha en el historial y
en el detalle de un aparcamiento. Sale un `shortDateText(epochMs)` a
`presentation/util/TimeStringComposables.kt` **y se barre el consumidor que ya lo hacía en línea**
(`ParkingHistoryDetailScreen.DateTimeRow`): dos sitios construyendo `"$día $mes $año"` a mano es
como divergen.

### 8 · El set de pruebas mock, en la misma tarea

- **Galería de estados**: grupo «Plus de regalo · momentos» con las tres variantes que cambian de
  veredicto —bienvenida con fecha, bienvenida sin fecha, aviso a 7 días—, y una nota al cerrarlas
  para que la galería no se quede en blanco.
- **Dev Catalog (flujo vivo)**: palanca «Plus de REGALO (promo Fundadores)» —que enciende el Plus de
  paso, porque «es de regalo» sobre una cuenta sin Plus no es un estado alcanzable— más tres chips
  de «le quedan» (3 meses · 5 días · sin fecha). Las dos escriben en `MockScenario` y el grant sale
  del `FakeEntitlementRepository` de `:mockkit`, así que el momento aparece por el grafo REAL.
- ⛔ La caducidad del escenario se calcula **al emitir, desde días**: una fecha absoluta grabada en
  un preset caduca sola y la palanca dejaría de enseñar lo que dice su nombre.

## Lo que salió de verlo en el emulador (Pixel 8, 20-11)

Commit `d54b7ea98`, flavor `mock`, sha256 device↔local `425bb0ef…` idéntico. Lo que el banco de
tests no podía contestar:

- ✅ **La bienvenida sale por el grafo REAL** al entrar con la palanca puesta, con la fecha bien
  calculada (90 días → «Gratis hasta el 18 feb 2027») y las seis ventajas del paywall.
- ✅ **Una sola vez**, y esta vez medido bien: cerrar → volver al catálogo → entrar otra vez, **sin
  matar el proceso**, y no vuelve. ⚠️ El primer intento fue un falso verde: hice `force-stop`, y eso
  resetea también `MockScenario`, así que el diálogo no salía por falta de GRANT, no por la memoria.
  En el mock las dos cosas viven en RAM y se pierden juntas — un «no sale» tras reiniciar no prueba
  nada.
- ✅ **La asimetría de la cuarta fila, en device**: cuenta nueva **sin felicitar** + 5 días para el
  fin → sale el AVISO, no la bienvenida.
- ✅ **El paywall se queda abierto** para ese usuario (que ya es Plus) y dice «Tu Plus de fundador es
  gratis hasta el 25 nov 2026. Si continúas, no cambia nada.» Era el bloqueo central del ticket.
- ✅ Ajustes: «Gratis como fundador, hasta el 18 feb 2027» + galón `REGALO`.

Y **dos defectos que solo se ven en píxeles**, los dos corregidos:

1. ⛔ **La cabecera del aviso se contradecía con su propia lista.** Decía «LO QUE TE DA EL PLAN
   GRATIS» encima de filas cuya línea en NEGRITA es la promesa de Plus («Zonas sin límite») y cuya
   segunda línea, en gris, es el límite free («Gratis: 2 zonas»). Quien escanea las negritas leía
   una cabecera que dice lo contrario de su lista. Ahora es **«Qué cambia»**, que es cierto de las
   dos mitades de cada fila y sigue sin ser «lo que pierdes». 9 locales.
2. ⚠️ **El metal NO se lee en la cresta del diálogo.** Las seis baldosas sí lo llevan y se ve; el
   héroe sale **verde plano**. La razón estaba escrita en el propio componente y no la leí:
   `PapPremiumMark` tiene su acabado verificado **a 72 dp**, y el círculo del molde de diálogo da
   para 30. Agrandarlo sería darle a UN diálogo su propio héroe, peor trato que una cresta que se
   lee como la marca a secas — así que lo que se corrige es **la afirmación del KDoc**, que prometía
   metal donde no lo hay.

### Dos decisiones del user tomadas MIRANDO la pantalla (20-11)

1. **En el aviso de fin, «Ver planes» es el botón RELLENO y «Ahora no» el outlined de debajo.**
   Salió al revés en el primer build, con el argumento de que un recordatorio es una cortesía y no
   un reclamo. El user lo invirtió, y la razón se sostiene sola: el sentido del aviso es que
   TODAVÍA puedes hacer algo con esa fecha, y enterrar la única acción que hace algo debajo del
   descarte convierte el diálogo en un callejón sin salida — el mismo defecto que el paywall que
   se autocerraba, una pantalla antes. El gating sigue siendo blando: «Ahora no» es un botón a
   todo el ancho, del mismo tamaño, ni enlace ni gris ni segunda capa.

2. ⛔⛔ **La cresta del paywall pasa a ser un DISCO de metal con el isotipo recortado en `surface`**
   (`PapPremiumCrest`), en vez de la marca fundida en metal (`PapPremiumMark`). Lo que gana es
   ÁREA: el barrido necesita superficie que cruzar, y un glifo suelto a 72 dp se la da justa — es
   el mismo motivo por el que a 30 dp, en el héroe del diálogo, el metal no se lee en absoluto.

   **Lo que cuesta hay que decirlo entero**: esto **reinstala un disco bajo la marca**, y
   `CLAUDE.md` § iconos lo declaraba MUERTO sin excepciones [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001,
   19-10] — y esta cresta era, literalmente, el último disco que esa regla mató. La distinción
   que sí existe: el disco muerto era una **plancha oscura** que sostenía una marca que no se
   sostenía sola; éste es el **material de la marca** con el símbolo quitado de dentro, que es un
   lockup y no una muleta. El user autorizó documentar esta **única excepción** en `CLAUDE.md`;
   la prohibición general de discos detrás de la marca sigue vigente.

   Ajuste visual posterior: el isotipo pasa de 0,58 a **0,63 del diámetro** del disco para ganar
   presencia, sin cambiar el tamaño del disco ni la cresta de los diálogos.

   ⚠️ Nota de alcance: el héroe del DIÁLOGO sigue con `PapPremiumMark` — el encargo decía «la
   pantalla de paywall». Si se quiere ahí también, es cambiar una línea, y además arregla el
   defecto 2 de la sección anterior (el metal plano a 30 dp). Sin pedirlo, no se toca.

## Criterio de éxito

- `PlusMomentTest`: las seis filas de la tabla, más la asimetría de la cuarta, más «sin uid no hay
  momento» y «sin caducidad no hay aviso».
- `AppViewModelTest`: el momento aparece cuando el grant llega **después** del arranque; no aparece
  dos veces; no aparece mientras la pregunta de anuncios está abierta; al descartarlo se persiste.
- `PaywallViewModelTest`: un Plus promocional que acaba **no** dispara el autocierre; uno comprado sí.
- `LocaleParityGuardrailTest` y `ComposeResourcesEscapeGuardrailTest` verdes con los strings nuevos.
- `./gradlew testAll` verde (incluido `MockKoinModuleVerifyTest`, que es de `:app`).
- En device (Dev Catalog): palanca de escenario que enciende el Plus promocional con fecha, y el
  diálogo en la galería de estados.

## Consumidores auditados

`grep -rln EntitlementRepository` → el miembro nuevo del interfaz obliga a:

| Sitio | Estado |
|---|---|
| `RevenueCatEntitlementRepositoryImpl` | **implementa**; `observeEntitlement` pasa a ser proyección |
| `shared/commonTest/fakes/FakeEntitlementRepository` | **implementa** + `emitGrant()` para los tests |
| `mockkit/.../FakeEntitlementRepository` | **implementa**, atado a `MockScenario` |
| 6 gates + `ObserveIsPlusUseCase` + `GetPlusOffers`/`Purchase`/`Restore` | **sin tocar** — siguen leyendo el enum |
| `SplashViewModel` (`logIn`/`logOut`) | **sin tocar** |

`grep -rln ": AppPreferences"` → las tres implementaciones del store:

| Sitio | Estado |
|---|---|
| `AndroidDataStoreAppPreferences` | **implementa** (`stringSetPreferencesKey`) |
| `IosAppPreferences` | **implementa** (`stringArrayForKey`, persistido — no en memoria) |
| `shared/commonTest/fakes/FakeAppPreferences` | **implementa** |
| `USER_SCOPED_KEYS` (Android + iOS) | **exento a propósito**, ver §4 |

Quien ya decide con `isPlus` y podría querer el origen:

| Sitio | Veredicto |
|---|---|
| `PaywallScreen` autocierre | **arreglado** (§5) — era el bloqueo real |
| `SettingsScreen` fila de Plus | **movido** (§5) |
| `HistoryEntitlementGate` y los otros cinco gates | **exentos**: un Plus regalado da acceso idéntico. El origen no es un permiso |
| `ObserveZoneSpotAlertsUseCase`, `RunMonthRecapUseCase`, `RunFuelPriceWatchUseCase` | **exentos**, misma razón |
| web / ficha de Play | **exentos y justificados**: ningún límite free cambia y la promo no está publicada |

## Seguimiento fuera de este ticket

- La concesión automática y la prueba con una cuenta nueva corresponden a
  `MONEY-FOUNDERS-PLUS-PROMO-001`.
- 📸 Si se abre una PR, adjuntar la captura del paywall final
  ([PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]).
