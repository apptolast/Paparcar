# ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001 · el onboarding vende media app: el mapa ya responde tres preguntas y solo se presenta una

**Estado:** ✅ Done — en master (el hash vive en `MEMORY.md`: este doc viaja DENTRO de su propio
commit, así que por construcción no puede citarlo). Visto en emulador Pixel 8 Pro; ⏳ sin ver en un
móvil físico.
· worktree `../Paparcar-onb-map-answers` · abierta desde
`HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001`, que es el ticket que hace visibles los
complementos y por tanto el que deja este desfase a la vista.

**Decidido por el user al abrir (10-09-2026):** entra **A + B**; el paso nuevo es el **quinto**, no
sustituye a `FIND_SPOT`; y la **fila del paywall** entra en este mismo ticket.

## Problema

Paparcar entró en producción respondiendo UNA pregunta: *¿hay una plaza libre que alguien acaba de
dejar?* Con F5 y F6 en master responde tres, y las otras dos no dependen de la comunidad:

| pregunta | fuente | ¿la presenta el onboarding? |
|---|---|---|
| ¿hay una plaza libre cerca? | comunidad (usuarios) | sí — es lo único que presenta |
| ¿dónde aparco pagando? (parking público) | OSM + adaptadores municipales | **no** |
| ¿dónde reposto y a cuánto? | Ministerio / OpenVan / prix-carburants | **no** |

Lo que el usuario nuevo ve hoy:

- `OnboardingScreen` — **2 páginas**: bienvenida + "cómo funciona" con tres líneas (la flecha que
  se mueve, el coche que descansa, el megáfono que avisa). Las tres hablan de detección y comunidad.
- `FirstStepsCard` en Home — **4 pasos** (`MARK_PARKING`, `UNDERSTAND_WATCH`, `FORTIFY_WATCH`,
  `FIND_SPOT`), otra vez todos de detección y comunidad.

Consecuencia medible: el día 1, en una zona sin usuarios, el mapa de un recién llegado está vacío
de plazas — y las dos capas que SÍ tienen dato mundial (parkings, gasolineras) existen, están a un
toque y nadie le ha dicho que están ahí. F6 se justificó en el roadmap precisamente como *"lo que
evita el mapa vacío el primer día"*, y ese argumento solo se cobra si el usuario sabe que puede
preguntarlo.

Y una tercera superficie lo repetía: el **paywall** compara siete filas (historial, cifras, coches,
zonas, widget, alertas, soporte) y **no menciona los complementos en ninguna**. La columna Free se
lee más pobre de lo que es, justo cuando la doctrina dice que VER nunca se gatea.

## Doctrina en juego

- ⛔ `ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001` retiró la 3.ª página del onboarding porque era
  una **lección de seguridad** antes de dejar entrar. Nada de página de catálogo: contar antes de
  que haga falta es el error de fondo, y repetirlo con otro tema sigue siendo el mismo error.
- ⛔ `ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001` — ningún paso nuevo bloquea.
- ⛔ `ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001` — un paso de CONOCIMIENTO se cierra al
  leerlo/abrirlo (`completesOnEngage`); uno de ACTO, solo haciéndolo. Este es conocimiento.
- ⛔ `COPY-SPOT-IS-NOT-A-PARKING-001` (+ la fila de `PARKINGS-ON-THE-MAP-001`): plaza ≠ aparcamiento
  ≠ parking público / **car park**. El copy nuevo nombra las tres cosas distinto, ×9 locales.
- ⛔ `FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001` — la línea de presentación de un carril no puede
  prometer lo que la fuente no da (ocupación solo donde el municipio la publica; precio con su hora).
- ⛔ Iconos: un glifo nuevo hereda el significado que ya tiene DENTRO de la app
  (`UI-ONBOARDING-STEP-ICONS-SAY-WHAT-THE-APP-SAYS-001`). La "P" ya está adjudicada al parking
  público como POI.

## Diseño

### A · La presentación vive en el carril, la primera vez que se entra

El gesto de `HomeNearbyLensRow` (Plazas · Parkings · Gasolineras) YA es "pregúntale al mapa otra
cosa". La primera visita a un carril **complementario** deja una `LaneNote` arriba que dice de dónde
sale el dato y qué no promete. Cero páginas nuevas, cero modales, y se cuenta exactamente cuando el
usuario ha demostrado interés — el mismo principio con el que se vendió la detección en vez de
exigirla.

Dónde vive el invariante, en UN sitio:

- **`NearbyComplement` (domain)** — enumera los subjects que necesitan presentar su procedencia:
  `CAR_PARKS`, `FUEL`. ⛔ `SPOTS` no está, y esa ausencia ES la afirmación: la plaza de la comunidad
  es el producto, no un dato de un tercero del que haya que decir de dónde viene.
- **`HomeNearbyLens.complement`** — la ÚNICA traducción carril → complemento. Un `when` suelto en
  cada consumidor es como los dos se separan.
- **`AppPreferences.observeIntroducedComplements()` / `setIntroducedComplements()`** — el set
  persistido, con la misma forma que `observeFirstStepsDone()`. Va en `clearUserScopedState()`: lo
  que el usuario SABE es del usuario, no de este teléfono, igual que el tutorial.
- **`HomeState.nearbyIntro: NearbyComplement?`** — de sesión, recalculado en CADA cambio de carril:
  el complemento del carril nuevo si aún no estaba presentado, si no null. Un solo campo, así que
  entrar, salir y volver dentro de la misma sesión no puede re-enseñar la línea.

### B · `FirstStep.EXPLORE_NEARBY`, quinto y último

- `completesOnEngage = true` — es CONOCIMIENTO. ⛔ No se le puede exigir a nadie repostar ni entrar
  en un parking de pago para cerrar un paso.
- **Cinco pasos, no cuatro.** `FIND_SPOT` enseña la comunidad, que es el producto; el complemento no
  puede desplazarlo. Y en la práctica el checklist rara vez enseñará cinco: `FORTIFY_WATCH` se retira
  solo cuando no hay nada que reforzar, así que el usuario típico sigue viendo cuatro.
- **La señal viva es la MISMA que persiste A**: haber entrado en un carril complementario ES haber
  explorado. `hasExploredNearby = introducedComplements.isNotEmpty()`. Un solo hecho, dos lectores —
  no hay un segundo latch que pueda contradecir al primero.
- **El CTA apunta a PARKINGS, fijo.** No se elige "el complemento que tenga dato en esa zona" porque
  ese dato no existe cuando el botón se pulsa: un complemento solo tiene `count` DESPUÉS de que se
  le pregunte, y si ya se le preguntó el paso está cerrado. Elegir por un contador que siempre vale
  0 sería teatro; ⛔ y un `0` en un subject que nadie preguntó no es un veredicto sobre el mundo
  [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]. Parkings es el complemento más cerca de la
  pregunta del producto (dónde aparco) y desde su carril el tercero está a un toque.
- `HomeSheetAction.RevealFreeSpots` se generaliza a **`RevealNearbyLane(lens)`**: "llévame a un
  carril" era ya la acción, y tenerla solo para plazas obligaba a copiar sus tres líneas
  (poner el carril + expandir + scroll) para el paso nuevo.

### C · Paywall

Fila nueva en `TIER_ROWS`, con el mismo texto a ambos lados: parkings y gasolineras con precio son
**free completo**. La tabla no existe para enumerar lo que Plus quita, existe para decir qué cambia
— y "esto no cambia, es tuyo igual" es información que vende el eje real (*free pregunta / Plus
vigila y recuerda*) mejor que el silencio.

## Criterio de éxito

- Un usuario nuevo en una zona sin plazas de comunidad sabe, sin leer nada más, que el mapa puede
  responderle dónde hay parking público y a cuánto está el litro.
- Ningún paso nuevo bloquea el checklist ni exige un acto.
- El copy nombra plaza / aparcamiento / parking público con las tres palabras distintas, ×9 locales.
- La línea de presentación sale UNA vez por complemento y sobrevive a un cold start.

## Consumidores auditados

`grep` de todo lo que asumía cuatro pasos, un carril fijo o un `RevealFreeSpots`:

| sitio | qué asumía | cómo queda |
|---|---|---|
| `FirstStepsTest` (6 tests) | 3 señales = completo · `total` 3 y 4 · un solo paso de conocimiento | **cerrado**: los seis actualizados con su razón. El de `completesOnEngage` pedía a gritos ser borrado a propósito al añadir un quinto paso — se hizo, no se heredó |
| helper `resolve()` de ese test | 9 argumentos POSICIONALES | **cerrado**: pasa a nombrados. Meter un parámetro en medio re-ligaba todos los de después, y aquí solo cantó porque el nuevo era `Boolean` y el siguiente un enum |
| `HomeSheetAction.RevealFreeSpots` | el carril de destino, fijo en plazas | **cerrado**: `RevealNearbyLane(lens)`, con sus dos comentarios de `HomeSheetContent` corregidos |
| `SettingsViewModel.RestartFirstSteps` | limpia "las tres mitades" del tutorial | **cubierto por decisión, con testigo**: `introducedComplements` NO se limpia — un replay no desaparca el coche ni des-cuenta lo ya contado. Test propio |
| `clearUserScopedState()` ×3 (DataStore, iOS, fake) | el set nuevo no existía | **cerrado**: entra en los tres. Lo que una persona sabe es suyo, no de este teléfono |
| `App.kt` (`FirstStep.entries` por nombre) · `HomeScreen` (`current == MARK_PARKING`) | nada del paso nuevo | **exentos**: leen por nombre o preguntan por otro paso |
| `HomeSlicesTest`, `SplashViewModelTest` | `FirstStep.entries.toSet()` | **exentos**: se mueven con el enum, y siguen verdes |
| galería mock + `*Previews.kt` | 4 pasos, carriles sin presentación | **cerrado**: 2 variantes del paso 5 (una **ejercitando su señal real**, no metiéndolo en `done`), su explicador, y la primera visita de cada carril |

## Verificación

- `./gradlew :shared:testDebugUnitTest --rerun-tasks` → **2.674 tests, BUILD SUCCESSFUL** (incluidos
  los guards Konsist de tipografía, color, imports, divisores y **paridad de locales**).
- `:app:assembleProdDebug` + `:app:assembleMockDebug` → OK.
- Rebasada sobre `04f4747c` (master avanzó a mitad de tarea) y **reverificada entera** después.
- ✅ **VISTO EN DEVICE** (emulador Pixel 8 Pro, API 37, 10-09-2026). APK instalado con verificacion de
  sha256 EN el device (`a108e4b6…`), no por el «Success» del install:
  - **La prueba que la galeria no puede dar**: en la app real (flavor mock, escenario «Home todo OK»),
    entrar en *Car parks* por primera vez enseña la linea bajo la fila de pestañas y encima de las
    filas; salir a *Free spots* y VOLVER a entrar **ya no la enseña**. Eso ejercita el ViewModel y la
    preferencia, no solo el render.
  - Galeria: las dos variantes de primera visita (parkings y gasolineras) y las dos del paso 5. En
    gasolineras la presentacion convive con la hora del feed al pie sin pisarla.
  - Explicador del paso 5: dos glifos NEUTROS (etiquetan su fila, no son acciones), la nota como
    aviso, y dos verdes en pantalla — el tile del heroe y el CTA.
  - Checklist: «FIRST STEPS 3 of 4» con el paso de refuerzo retirado, CTA «Show me» + segunda puerta
    etiquetada «How it works».
  - Licencias: las CUATRO fuentes, con `data.economie.gouv.fr · Licence Ouverte 2.0` y el proposito de
    OSM ya nombrando parkings, gasolineras, lugares y calles.
  - Cero `FATAL` y cero `Resource with ID … not found` en logcat.
- ⏳ Sin ver en un movil FISICO ni con datos reales de Overpass (el emulador va con los fakes del
  flavor mock).

## La auditoría que viaja con este ticket

El user pidió en el mismo turno auditar qué otra información quedó desfasada, y decidió meterla
**aquí**. Ficha propia con el detalle: **`docs/backlog/legal-the-app-grew-three-data-sources-001.md`**.

En una línea: la app crecía tres fuentes de datos y una pasarela de pago sin decirlo. Se añade la
atribución de `data.economie.gouv.fr` (Licence Ouverte — el único hallazgo que podía ser
incumplimiento, no imprecisión), se corrige el propósito de OSM, y la política de privacidad gana una
§2.8 y cinco destinatarios en §4, en sus dos idiomas.

✅ **La web se desplegó el 10-09-2026** a petición del user, y se verificó en vivo contra
`paparcar.com`. El código de la política viaja igualmente en este commit: lo desplegado y lo
versionado tienen que poder compararse.
