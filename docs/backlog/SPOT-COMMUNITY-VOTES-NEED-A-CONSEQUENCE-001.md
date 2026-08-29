# SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001 · Decir "ya no está" no le hace nada a la plaza

**Estado:** ✅ Done · mergeado en master (29-08-2026) · ⏳ sin ver en device
Descubierto durante `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001` (master `d0fb3427`).

## Problema

El peek de una plaza ofrece dos botones, "Sigue ahí" y "Ya no está" (`SendSpotSignalUseCase`), que
incrementan `acceptCount` / `rejectCount` en Firestore. Esos contadores alimentan
`communityConfidence()` → `Spot.confidence`.

**Y `Spot.confidence` ya no lo consume nadie.** Hasta este ticket, su único lector era la rampa de
color, que ahora se deriva de la EDAD. O sea: un usuario puede decir "ya no está", el voto se
guarda correctamente, y **la plaza sigue exactamente igual de verde para el siguiente**.

Antes tampoco funcionaba bien — el voto se mezclaba con el reloj dentro del mismo Float, así que
"tres personas dicen que no está" y "la plaza tiene 40 minutos" eran indistinguibles en el color.
Separarlos dejó el problema a la vista en lugar de crearlo.

## Doctrina violada

- **Al quitar un botón, borrar limpio; no plegar su conducta en otro control.** El inverso también
  vale: un botón que no hace nada es peor que no tenerlo. Hoy "Ya no está" es un placebo.
- **Fallo asimétrico: mejor falso negativo que falso positivo.** Un testigo presencial diciendo que
  la plaza está ocupada es la señal más fuerte que existe sobre esa plaza, y la estamos ignorando.

## Señales / datos disponibles

- `acceptCount` / `rejectCount` en `SpotDto`/`SpotEntity`, ya persistidos y sincronizados.
- `communityConfidence()` en `SpotDtoMapper`, ya calculado (ratio suavizado con Laplace,
  `MIN_VOTES_FOR_SIGNAL = 3`).
- `SpotStatus.RETRACTED` + `RETRACTION_GRACE_MS`: **ya existe el mecanismo** para retirar una plaza
  explicando por qué, sin que el marcador se esfume en la cara de quien iba de camino.
- `enRouteCount`: cuánta gente va hacia ella (a más gente en camino, más cara sale la mentira).

## Diseño — DECIDIDO (user, 29-08-2026)

Un rechazo de la comunidad no es "menos fresca", es **otra cosa**: la plaza está ocupada. Así que no
se tiñe — se usa la vía que ya sabe explicarse, `SpotStatus.RETRACTED` + `RETRACTION_GRACE_MS`, que
mantiene el documento vivo lo justo para decir POR QUÉ desapareció en vez de esfumar el marcador en
la cara de quien iba de camino.

**El testigo presencial es la autoridad. Y solo el testigo presencial.** Las tres decisiones son una
sola: la proximidad es lo que hace seguro que un único voto baste.

### D1 · Un solo "Ya no está" retira la plaza — **con proximidad medida**

Una persona plantada en la plaza es la evidencia más fuerte que existe sobre ella, más que cualquier
recuento. Y encaja con la doctrina de **fallo asimétrico**: retirar una plaza que seguía libre
cuesta una oportunidad perdida; dejar viva una plaza fantasma cuesta un viaje en balde, que es el
fallo que mata la confianza en la app.

`MIN_VOTES_FOR_SIGNAL = 3` **no se reutiliza aquí**: tres rechazos en una plaza que vive minutos es
un umbral que casi nunca se alcanza — la plaza caduca antes. Sigue rigiendo para
`communityConfidence()`, que es otra pregunta.

### D2 · "Sigue ahí" **rejuvenece** la plaza

Reinicia su edad. Es literalmente lo que el testigo acaba de observar, y es exactamente lo que la
edad mide desde `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`: una plaza roja que alguien confirma en
persona vuelve a ser verde, porque vuelve a ser cierto.

Con esto los dos botones dejan de ser un placebo **el mismo día**: uno retira, el otro rejuvenece.

⚠️ Ojo al bucle: rejuvenecer NO debe extender `expiresAt` indefinidamente. La edad (que colorea) y
la caducidad (que barre) son cosas distintas desde el ticket anterior y tienen que seguir siéndolo —
si no, una plaza confirmada en cadena se vuelve inmortal.

### D3 · Los botones solo existen cerca

Sin esto, cualquiera retira plazas desde el sofá. Umbral propuesto: **50 m** — más ceñido que el
`NEAR_CAR_MAX_METERS = 100.0` de detección, porque aquí el voto decide por TODOS y el GPS urbano
anda en 10-20 m. Constante nueva en la política de votos, **no se reutiliza la de detección**: son
dominios distintos y compartirla las ataría por accidente.

Sin ubicación disponible → los botones no se ofrecen. No se pregunta, no se adivina.

### D4 · Voto duplicado: en memoria, sin tabla nueva — **desviación del plan inicial**

El criterio original decía "un mismo usuario no puede votar dos veces la misma plaza", y lo escribí
yo pensando en un registro persistido. Al implementarlo se ve que **ese registro no guardaría
nada**: no existe almacén de preferencias en el proyecto (sólo Room), así que costaría una tabla,
un DAO y una subida de versión de esquema — justo después del reset a Room v1.

Y no hace falta, porque el diseño ya es idempotente por otras razones:
- un segundo "Ya no está" sobre una plaza **ya retractada** no hace nada;
- un "Sigue ahí" repetido **no puede alargar la vida** de la plaza, porque rejuvenecer no toca
  `expiresAt` (D2); el techo lo pone la TTL;
- `inFlightSpotSignals` ya impide el doble toque.

Lo único que quedaba era cosmético: no volver a ofrecer los botones sobre una plaza que acabas de
votar. Eso es `HomeState.votedSpotIds`, **en memoria**. Coste cero, y sin esquema que mantener para
proteger unos contadores que hoy no lee ningún píxel.

Limitación asumida y consciente: reinstalar o reiniciar la app devuelve los botones. Con el techo
de la TTL, el daño posible es que una plaza confirmada en cadena se vea fresca hasta que caduque.

### Preguntas que quedan fuera de este ticket

- ¿Puede el autor de la plaza retirarla él mismo? Hoy no, y no lo abrimos aquí.
- Anti-abuso de servidor (reglas Firestore que aten el voto a una posición). La puerta de
  proximidad es de CLIENTE: un APK modificado se la salta. Es el techo real de este ticket.

## Criterio de éxito

- ✅ Decir "Ya no está" a menos de 50 m retira la plaza para todos, con su nota explicando por qué.
- ✅ Decir "Sigue ahí" devuelve la plaza a verde de forma observable.
- ✅ Ninguno de los dos botones aparece sin ubicación o a más de 50 m.
- ✅ Rejuvenecer no alarga la vida total de una plaza más allá de su TTL.
- ✅ No se re-ofrecen los botones sobre una plaza ya votada en esta sesión (ver D4).
- ⏳ Sin ver en device.

## Consumidores auditados

| Sitio | Cambio | Estado |
|---|---|---|
| `domain/model/SpotVote.kt` | **nuevo** — `SpotVoteOutcome` + `SpotVotePolicy` (50 m, veredicto puro) | ✅ |
| `domain/usecase/spot/SendSpotSignalUseCase.kt` | de reenviar el voto a aplicar su consecuencia; devuelve `Result<SpotVoteOutcome>` | ✅ |
| `domain/repository/SpotRepository` · `data/repository/SpotRepositoryImpl` | `refreshSpot(spotId)` — Firestore only, Room llega por el listener [SPOT-FLICKER-001] | ✅ |
| `data/datasource/remote/FirebaseDataSource(+Impl)` | `refreshSpot(spotId, reportedAt)` — escribe SÓLO `reportedAt` | ✅ |
| `presentation/home/HomeViewModel` | mide la distancia ÉL, desde el estado que tiene fix y plaza — el gate no depende de que la UI lo pase | ✅ |
| `presentation/home/HomeEffect` · `HomeScreen` | `SpotSignalSent(outcome)` + un mensaje por consecuencia | ✅ |
| `presentation/home/HomeState` · `HomeSlices` · `HomePeekHandle` · `SpotPeek` | `votedSpotIds` hasta el peek; los botones sólo si `canVote && !alreadyVoted` | ✅ |
| Los 9 `strings.xml` | `home_spot_signal_sent` ("Thanks for the update!", igual para ambos votos) retirada → `home_spot_vote_retracted` / `_refreshed` | ✅ |
| `HomeViewModelTest` | ⚠️ 2 tests asumían el contrato viejo y votaban sobre una plaza que el VM no conocía; uno pasaba **en falso** | ✅ reescritos + 2 casos nuevos |
| Fakes (`FakeSpotRepository` ×2, `FakeFirebaseDataSource` ×2) | `refreshSpot` con registro para aserciones | ✅ |
| `app/src/mock/.../StateGalleryScreen.kt` | ⚠️ **toda plaza de FakeData está a 119-441 m de `sampleGps`** → los botones habrían sido invisibles en TODO el catálogo. 3 variantes nuevas (encima / lejos / ya votado) | ✅ |

## Verificación

- `:shared:testDebugUnitTest` verde, incluyendo `SpotVotePolicyTest` (9 casos: fronteras del radio,
  sin fix, ambos sentidos ignorados de lejos) y `SendSpotSignalUseCaseTest` (7 casos: retracta,
  rejuvenece, no escribe NADA de lejos, y no aplica la consecuencia si el voto no llegó a escribirse).
- `:app:assembleMockDebug` + `:app:compileProdDebugKotlin` verdes.

## Relación con otros tickets

- Lo destapa `SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001`, que sacó el tiempo de `confidence` y dejó
  a los votos sin consumidor.
- Es también el "marcar plaza como ocupada" que ese ticket dejó anotado: **son el mismo problema**,
  visto desde el botón y desde el dato.
