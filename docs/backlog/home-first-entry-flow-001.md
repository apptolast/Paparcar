# HOME-FIRST-ENTRY-FLOW-001 · Dar un primer uso guiado a Home sin encadenar diálogos

**Estado:** 🔵 En progreso · rama `feature/HOME-FIRST-ENTRY-FLOW-001-coordinated-home` · worktree `.worktrees/home-first-entry-flow-integrated`.

## Problema

La primera visita puede reunir la petición nativa de ubicación, la invitación a activar detección, la bienvenida de Plus por Black Friday, el consentimiento de medición de Meta y el tutorial de Home. Aunque cada pieza responde a una necesidad válida, hoy sus dueños independientes pueden hacer que el usuario reciba varias interrupciones antes de entender cómo aparcar y recordar su coche.

Home necesita un recorrido inicial coordinado que permita empezar a usar el mapa, dé protagonismo al regalo cuando se confirme, y lleve al usuario al primer gesto propio del producto: marcar su aparcamiento.

## Doctrina y decisiones de producto

- El mapa es el contexto de la petición de ubicación. Se mantiene el diálogo del sistema operativo y se deja que Home responda honestamente a una denegación.
- La bienvenida de Black Friday es noticia, no requisito. Se presenta en el primer espacio disponible tras confirmar RevenueCat, puede esperar mientras el usuario termina otra acción y nunca retrasa Home. La solicitud de la promo sigue ejecutándose en segundo plano al alcanzar Home.
- Activar la detección se ofrece después de la ubicación, con una salida «Ahora no». La invitación existente abre el flujo de permisos Producer y no se repite si se pospone.
- La primera visita ofrece directamente el editor real de aparcamiento como último paso del recorrido inicial. Se puede cerrar. Guardar completa el paso real del tutorial mediante la señal existente de sesión; cerrar conserva el paso pendiente en la tarjeta de First Steps.
- El consentimiento de Meta conserva su elección explícita e independiente, pero deja de competir con el primer uso. Se presenta en una visita posterior y la medición permanece desactivada mientras no exista consentimiento.
- Una sola superficie propia a la vez. Permiso nativo, bienvenida, pitch, editor y consentimiento tienen señales y decisiones diferentes, coordinadas por un estado de flujo explícito. Reentradas y respuestas asíncronas retoman desde el estado vigente sin duplicar una pregunta contestada.

## Flujo objetivo

1. Home muestra el mapa y dispara la petición inicial de ubicación cuando corresponda.
2. Al volver del permiso, si ya se confirmó la promoción Plus, Home presenta su bienvenida. Si sigue pendiente, no espera a RevenueCat y continúa con la invitación a detección.
3. La invitación ofrece activar detección o posponerla. Activar navega al flujo de permisos existente. El regreso a Home continúa el recorrido.
4. El editor existente `HomeMode.AddingParking` se abre automáticamente una vez para el usuario sin aparcamiento activo. Confirmar guarda primero la sesión local y vuelve a Browse; cerrar vuelve a Browse con el paso `MARK_PARKING` aún pendiente.
5. Browse conserva el tutorial de First Steps y la llamada a marcar aparcamiento si se pospuso. Al iniciar una visita posterior, Meta puede pedir su decisión si el carril está disponible.
6. Si Black Friday se confirma mientras el usuario responde a detección o coloca el pin, la bienvenida aparece al volver a Browse y no desplaza la acción en curso.

Las cuentas que ya tienen aparcamiento, completaron o cerraron su tutorial, no tienen vehículo registrado, no son elegibles para la promo, o usan un build sin carril Meta omiten solo los pasos que no les corresponden. El aviso de precisión GPS conserva su condición de detección completamente armada y no se convierte en parte del tour inicial.

## Diseño del sistema

- Una proyección pura de primera visita resuelve el paso presentable a partir de señales observables, estado durable y superficie activa. No se marca un paso como hecho por pulsar un botón.
- Un único dueño de coordinación entrega a Home los candidatos elegibles en orden, y pausa la secuencia mientras haya un diálogo del sistema, permisos en curso, una edición de pin o una acción explícita del usuario. Mantiene los dueños existentes de permisos, consentimiento, tutorial y RevenueCat.
- El recuerdo de que ya se ofreció el editor inicial es propio y persistente, distinto de `hasConfirmedFirstPark`, `firstStepsDone` y el consentimiento de Meta. Un rechazo/cierre del editor no falsea el paso completado.
- El editor aprovecha `EnterAddParkingMode`, `ConfirmAddParking`, guardado local y recuperación de error existentes. No duplica un formulario ni crea una ruta de aparcamiento paralela.
- La bienvenida de Plus solo se presenta tras confirmación del entitlement, usando la elegibilidad ya resuelta por `PlusMoment`. La respuesta de Meta deja de servir como barrera para el regalo. Sin entitlement confirmado, no se inventa disponibilidad a partir del estado de la campaña.
- Cada transición se prueba con estados puros y con ViewModel/fakes. Prod y mock conservan grafos compatibles. Los recursos visibles se actualizan en los nueve idiomas.

## Criterios de éxito

- Como máximo una superficie de decisión de Paparcar a la vez. Home y el mapa siguen disponibles si ubicación, detección o Meta quedan pendientes o se rechazan.
- Una cuenta nueva puede ver primero la solicitud de ubicación. Si recibe Plus durante la secuencia, obtiene la bienvenida tan pronto como la superficie activa lo permita, antes de que el usuario abandone el flujo inicial.
- La invitación a detección ofrece aceptar o «Ahora no», y no se vuelve a imponer al posponerla.
- Un usuario sin aparcamiento activo recibe una sola apertura automática del editor. Confirmar completa `MARK_PARKING` mediante el estado real; cerrar conserva el paso pendiente y Browse utilizable, también tras recrear o reiniciar.
- Meta no aparece en la sesión inicial. En una visita posterior ofrece permitir y no permitir de forma simétrica. Hasta responder, no se activa medición publicitaria.
- Plus no se oculta esperando la respuesta de Meta. La bienvenida y el aviso de caducidad siguen respetando sus propios estados y deduplicación.
- Tests de resolver y secuencias para permiso concedido/denegado, detección aceptada/pospuesta, regalo temprano/tardío/no disponible, pin guardado/cancelado/fallido, cuenta existente y carril Meta ausente. Validar `:shared:testDebugUnitTest`, `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin`, además de los guardarraíles de documentación afectados.
- Revisar el recorrido final en dispositivo o emulador con estados de cuenta nuevos y existentes. No se declara validación visual solo por compilar.

## Implementación y consumidores auditados

- Base actual: `master 73c6aab5f`, después del rebase autorizado por el usuario. Incluye Fundadores y disponibilidad de parkings. El único conflicto del autostash fue el recuento del índice: 852 ficheros, 759 cerrados, 84 abiertos y 9 no tickets. El worktree anterior `.worktrees/home-first-entry-flow` conserva el borrador previo, no es el resultado de esta implementación.
- `App.kt` mantiene `onReachedHome` y el trabajo asíncrono existente de claim/refresh. Entrega los candidatos crudos a Home; se retira el gate de Plus por consentimiento de `AppState`.
- `HomeEntryPromptHost` presenta un solo candidato y conserva la pregunta activa frente a la llegada tardía de Plus. No presenta candidatos sobre selección, búsqueda, edición, liberación ni rutas ajenas a Home.
- `HomeViewModel`, `HomeState`, `HomeSlices`: esperan snapshots de preferencias, flota y sesiones antes de ofrecer el editor; ocultan el checklist hasta esa oferta. El guardado fallido conserva el editor, guardar o cerrar finaliza solo la oferta inicial.
- `AppPreferences`: memorias de inicio y cierre indexadas por cuenta en Android/iOS y fakes; se conservan al salir de sesión. No contienen ubicación. Una oferta iniciada e interrumpida se retoma mientras siga siendo pertinente.
- Permisos y aviso GPS: se reutilizan rutas existentes y se espera su regreso. La detección y sus decisiones de dominio no cambian.
- Meta: el ViewModel conserva si la oferta ya estaba resuelta al llegar (sobrevive rotación y se relee del almacenamiento en un proceso nuevo). La pantalla recuerda una salida real de Home después de completarla, por navegación o segundo plano; una composición nueva inicialmente inactiva no cuenta como salida. Cerrar el editor no abre Meta en esa misma visita. Responder sigue usando `AppViewModel` y `SetAdMeasurementConsentUseCase`.
- Dev Catalog: variantes de regalo + detección, detección sola, consentimiento posterior y editor inicial; presets del Home real con y sin regalo. Previews Android de las mismas superficies.
- Recursos: se reutilizan textos existentes en los nueve idiomas; no hay nuevas claves visibles de producto.
- Web: no cambian capacidades, límites de Plus, permisos concedidos ni promesas de detección. El orden de presentación es interno al primer uso.

## Hallazgos de la demostración

- El preset de primer acceso heredaba un aparcamiento activo del segundo coche y un historial de demo; Home omitía correctamente el editor. `MockScenario.firstHomeEntry` hace que ambos presets nuevos partan sin sesiones sembradas, manteniendo el guardado real del fake.
- La bienvenida podía perderse cuando el grant confirmado llegaba antes que el recibo de Fundadores y no cambiaba en el refresh. Se reprodujo con una prueba que fallaba. `AppViewModel` vuelve a evaluar la misma elegibilidad al terminar la reconciliación, además de observar grant y usuario; no crea un entitlement ni una bienvenida sin prueba.
- Regresión añadida para el grant sin cambios y pruebas de coherencia del preset (cuenta nueva vacía, guardado visible y demo habitual conservada).

## Validación

- `:shared:compileDebugKotlinAndroid`: correcto.
- En el pase posterior al rebase y a los hallazgos visuales, los grupos implicados suman 251 pruebas sin fallos: 200 de `HomeViewModelTest`, 39 de `AppViewModelTest`, 10 de `HomeFirstEntryFlowTest` y 2 de `FirstHomeMockScenarioTest`. Incluyen guardado/cancelación/error, reentrada por cuenta y proceso, snapshots pendientes, permisos, regalos tardíos y grant ya confirmado antes del recibo.
- Pase final: `testAll :app:compileProdDebugKotlin :app:assembleMockDebug --console=plain`: **BUILD SUCCESSFUL**, 56 s, después de las correcciones encontradas en el Pixel. Se ejecutaron tests shared y del grafo mock, compilación prod/mock, empaquetado del APK mock y compilación klib de iOS Simulator. La klib no es enlace ni prueba en dispositivo Apple.
- `git diff --check`: correcto. El guard del backlog pasó dentro de la suite agregada, con 852 ficheros, 759 cerrados, 84 abiertos y 9 no tickets.
- Recorrido visual realizado en el AVD conectado **Pixel 8 Pro** (`Pixel_8_Pro`, Android): instalación actualizada del APK mock con `adb install -r` → `Success`; apertura de `DevMainActivity`. Se vio bienvenida → editor automático; al cerrar sin guardar, Browse y tutorial `0 of 5` con `MARK_PARKING` pendiente; al abrirlo desde el tutorial y guardar, sesión visible y tutorial `2 of 5` (permisos ya concedidos). Volver a entrar conserva la sesión y no repite la bienvenida ni el editor inicial. Capturas mostradas en la sesión.
- Galería `Home · inicio secuencial`: invitación a detección con `Not now` operativa y consentimiento posterior con ambas decisiones visibles; se cerró con `Don't allow` en el estado de demo. El mock carece de carril Meta real: no se validó envío publicitario. El preset concedía permisos, por lo que esta demostración no valida la concesión nativa ni los tiempos de red de RevenueCat.
- El lanzador Android CLI falló por la política de Control de aplicaciones de Windows; se usó `adb` existente. El render de previews del IDE no reconoce el módulo del worktree, pero el APK se compiló y se revisó en el emulador.
- Implementación y revisión visual de demo completadas sin commit ni merge; el ticket permanece abierto hasta su integración. El emulador queda en Home del mock, con el aparcamiento de demo guardado y el tutorial en el paso de zona privada.

## El spotlight apunta, la hoja habla (22-11)

Revisando la cadena completa en el Pixel 8 el user vio la pantalla decir lo mismo dos veces: el
cartel del spotlight («El pin no se mueve: arrastra el MAPA…») y, cuatro dedos más abajo y a la vez
en pantalla, la línea de la hoja («Centra el pin donde dejaste el coche»). Dos textos sobre el mismo
gesto no es énfasis, es ruido.

**La frase se queda donde es SIEMPRE cierta.** `home_add_parking_helper_primary_create` pasa a
«Arrastra el mapa para centrar el pin en tu coche» en los nueve idiomas: lleva el objetivo y el
gesto en una línea, y la lee todo el mundo cada vez que marca un aparcamiento, no sólo el primero.
Su hermano de edición ya estaba escrito así («Arrastra el mapa al punto correcto»), de modo que
ahora los dos modos del editor hablan igual. `PapSpotlight` pierde su `caption`, su columna y su
ancho máximo: su trabajo es el agujero — decir QUÉ pin —, y para eso no hacen falta palabras.
`first_steps_spotlight_caption` está borrada de los nueve locales.

### ⛔ La trampa que descubrió aquel cartel, que el token ya no puede guardar
Sin consumidor, el rol `PapColor.onScrim` muere con él (y su fila en `COLOR-SYSTEM.md`): un token
huérfano es peor que ninguno. Pero lo que midió sigue siendo cierto para cualquier texto futuro
sobre un velo, así que vive aquí:

`colorScheme.inverseOnSurface` **no** es «el color que se lee contra el velo». El par inverso se lee
contra `inverseSurface`, que no es el velo; y en este tema `inverseOnSurface` **sólo está declarado
en el esquema CLARO** (`Theme.kt`), así que en oscuro caía al defecto de Material 3 — un color
oscuro — sobre un velo negro al 72 %. Medido en el Pixel 8: texto casi invisible, y precisamente en
la primera entrada. Si vuelve a hacer falta texto sobre un velo, el valor no se le pregunta al tema:
`colorScheme.scrim` es `#000000` en los dos esquemas, así que lo que se lee encima tampoco cambia.

## El héroe de la bienvenida deja de ser un emblema (22-11)

Petición del user: *«una ilustración chula de paparcar black friday para que el diálogo quede más
personalizado»*. El héroe había pasado ya por `PapPremiumMark` a 30 dp (donde el metal no llega a
dibujarse) y por `PapPremiumCrest` (donde sí, pero dice lo mismo que el título). Lo que faltaba no
era material, era la **ocasión**.

`FoundersGiftIllustration` (Nivel 3, Compose Canvas, `ui/illustrations/`, variante oscura por
parámetro como sus cuatro hermanas) dice tres cosas sin una palabra —requisito, porque una
ilustración con texto no viaja a nueve idiomas—: **regalo** (caja y lazo), **Black Friday** (la caja
es negra; sin porcentaje, que envejecería y chocaría con el «2.000» del título) y **Paparcar** (el
regalo está aparcado dentro de una plaza pintada en el suelo, y el lazo lleva el verde de marca).
No lleva el isotipo: la marca se GENERA, no se dibuja a mano.

`PapAlertDialog` gana `heroIllustration`, que **sustituye el plato circular** en vez de meter algo
dentro de él — una ilustración trae su propio suelo y su propio aire, así que el círculo sería un
segundo marco alrededor de algo ya enmarcado. `heroContent` sigue intacto para los glifos
(`PromoEndingDialog` y el pictograma de vehículo). Con la cresta fuera de este diálogo,
`HERO_CREST_DP` desaparece.

### Dos fallos de GEOMETRÍA que sólo se vieron en el device
Ninguno era de color, y ninguno se habría visto leyendo el código:
1. Las líneas de la plaza, puestas de pie a los lados, se leían como **las patas de un caballete**:
   la línea del fondo quedaba tapada por la caja, así que de la plaza sólo asomaban las diagonales.
   Se arregló **pintando el asfalto**: con suelo relleno debajo, las mismas líneas son pintura.
2. El lazo, alto y estrecho, se leía como **un brote de planta saliendo de una maceta**. Va ancho y
   bajo.

⚠️ Y una trampa de herramienta, por tercera vez en este repo: el parche que rehacía la ilustración
ancló en `drawShadowEllipse(70f, 106f, 40f, 6f, dark)`, que **`EmptySpotsIllustration` tiene
idéntica**, y el corte se llevó por delante dos ilustraciones vivas. Lo cazó el compilador al
instante y `git checkout --` lo deshizo, pero la regla sigue siendo la misma: **anclar por el
símbolo, no por una línea de dibujo que puede repetirse**.

Visto en el Pixel 8 en los **dos temas** (`G4-regalo-v3.png` oscuro, `G6-regalo-claro.png` claro).
