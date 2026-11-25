package com.rndeveloper.paparcar.domain.preferences

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.attribution.PendingInstallAttribution
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.spot.VouchedSpot
import kotlinx.coroutines.flow.Flow

interface AppPreferences {

    /**
     * Drops every preference that belonged to the user who just signed out.
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * The third arm of `SESSION-ISOLATION-001`, which until now covered Room and the OS geofences
     * and left this store standing: the guided tutorial, the first-park nudge counters and the two
     * open questions ("where did you leave your car?", "did you park?") all survived a sign-out and
     * greeted the NEXT account — the last two pointing at a `vehicleId` the Room wipe had just
     * deleted.
     *
     * The line it draws, and the one thing to think about before adding a key to either side: a
     * preference that describes the USER goes; one that describes this DEVICE or this INSTALL
     * stays. So `onboarding_completed`, the GPS disclaimer, the location-permission-asked flag and
     * the ad-measurement consent are KEPT — they are facts about this phone, and wiping them would punish a
     * user who merely signed out — while theme, units, map type and the detection toggles are kept
     * for the same reason.
     *
     * What is lost stays lost: only the tutorial's dismissal comes back, and it comes back from
     * the account (`UserProfile.firstStepsDismissedAt`), not from here.
     */
    fun clearUserScopedState()

    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()

    val hasSeenGpsAccuracyDisclaimer: Boolean
    fun setGpsAccuracyDisclaimerSeen()

    /** Whether the Google Play Install Referrer has already been read and resolved to a channel
     *  for THIS install. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
     *
     *  Per-install, not per-account (two accounts on the same phone share the same referrer — it
     *  describes how the APK got here, not who is signed in), so it is NOT wiped on sign-out. The
     *  referrer can only be read successfully once per installation; this is what stops
     *  [com.rndeveloper.paparcar.domain.usecase.attribution.ResolveInstallAttributionUseCase] from
     *  re-reading it (and re-writing Firestore) on every cold start. A failed CONNECTION to the
     *  Play service does not set this — only a resolved answer (including "no referrer available")
     *  does, so the use case keeps retrying across app launches until it gets one. */
    val hasResolvedInstallAttribution: Boolean
    fun setInstallAttributionResolved()

    /** The classified install attribution awaiting a session to publish it to Firestore — Play
     *  answered successfully but there was no signed-in user at that instant.
     *  [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001]
     *
     *  Per-install like [hasResolvedInstallAttribution], not user-scoped — so NOT wiped on
     *  sign-out: it describes how the APK got here, from before we even knew who signed in.
     *  Cleared the moment `ResolveInstallAttributionUseCase` manages to publish it. */
    val pendingInstallAttribution: PendingInstallAttribution?
    fun setPendingInstallAttribution(pending: PendingInstallAttribution)
    fun clearPendingInstallAttribution()

    /** The user's answer about advertising measurement on this install — its own consent, asked
     *  once after sign-in and changeable in Settings. Per install, so NOT wiped on sign-out.
     *  Replaces the old `legal_consent_accepted` tick, which is deliberately not migrated: it was
     *  bundled with account creation, so it never was a valid yes to ad measurement.
     *  [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
    val adMeasurementConsent: AdMeasurementConsent
    fun setAdMeasurementConsent(consent: AdMeasurementConsent)

    /** True once we have ever fired the foreground-location request dialog. Lets the permissions
     *  screen tell a genuine first launch (offer the system dialog) apart from a permanently denied /
     *  revoked permission (jump straight to system settings). Android-only concept. [DET-READY-001m] */
    val hasRequestedLocationPermission: Boolean
    fun setLocationPermissionRequested()

    // ── Lo que ya se le DIJO a cada cuenta sobre su Plus de regalo.
    //    [MONEY-FOUNDERS-WELCOME-001] ──
    /**
     * Las cuentas a las que ya se felicitó por el Plus de la promo «Fundadores».
     *
     * ⛔ **Un conjunto de uids y no un booleano**, porque la pregunta es «¿se le dijo a ESTA
     * cuenta?». Con un flag único, dos cuentas en el mismo móvil —que es literalmente el setup de
     * field-test de este proyecto— compartirían la felicitación y la segunda no la vería nunca.
     *
     * ⛔ **NO entra en el barrido de [clearUserScopedState]**, y es la decisión fina de este
     * bloque. La regla de ese barrido es «lo que describe al USUARIO se va», y esto describe al
     * usuario… pero está INDEXADO por uid: quedarse no filtra nada a la cuenta siguiente (su uid
     * no está en el conjunto) y borrarse sí rompe el invariante — cerrar sesión y volver a entrar
     * re-felicitaría. Indexado por uid, es un hecho sobre lo que este INSTALL ya ha dicho.
     *
     * Reinstalar vuelve a felicitar. Asumido: la alternativa es un campo en el perfil de
     * Firestore, y mover ahí la frontera de [MONEY-001] por un caso que no duele no se paga.
     */
    val plusPromoWelcomedUids: Set<String>
    fun addPlusPromoWelcomedUid(uid: String)

    /**
     * Las cuentas a las que ya se avisó de que su Plus de regalo se acaba.
     *
     * ⛔ Un SEGUNDO conjunto, nunca fundido con [plusPromoWelcomedUids] — mismo patrón y misma
     * razón que `firstStepsDone` / `firstStepsDeferred`: son dos preguntas distintas, y hay un
     * caso real que marca una sin pasar por la otra (instalar con la promo a punto de acabarse se
     * salta la felicitación y recibe sólo el aviso). Un mapa uid→fase obligaría a inventar un
     * orden entre fases que no existe.
     */
    val plusPromoEndingNoticedUids: Set<String>
    fun addPlusPromoEndingNoticedUid(uid: String)

    /** Accounts whose founders receipt has been processed on this device.
     *  [MONEY-FOUNDERS-PLUS-PROMO-001] A grant receipt is processed after refreshing RevenueCat. */
    val foundersPromoClaimedUids: Set<String>
    fun addFoundersPromoClaimedUid(uid: String)

    /**
     * Las cuentas a las que **la campaña** concedió el regalo — las que de verdad son una de las
     * 2.000. [MONEY-FOUNDERS-PLUS-PROMO-001]
     *
     * ⛔ Es un CUARTO conjunto y no un `Boolean` dentro de los otros tres, porque contesta una
     * pregunta que ninguno contesta: «¿esta cuenta pasó por la promo?». [foundersPromoClaimedUids]
     * dice que se preguntó, no que se concediera — se marca igual cuando la respuesta es *no*.
     *
     * ⛔ Y existe porque sin él la bienvenida miente. `plusMomentFor` enseñaba «eres uno de los
     * 2.000 primeros» ante **cualquier** grant `PROMOTIONAL`, y un Plus concedido a mano en el
     * panel de RevenueCat —compensación de soporte, un amigo, un tester— es promocional y NO es un
     * fundador. La vía de los conocidos ya estaba resuelta con códigos de Play (`PLAY_STORE` →
     * `PURCHASED` → mudo); la del panel era la que quedaba abierta, y no caducaba con la campaña.
     *
     * Se escribe desde el recibo del servidor (`outcome == granted`), que es la única prueba que
     * el cliente puede tener. Reinstalar vuelve a leerlo del propio recibo, así que la felicitación
     * vuelve — mismo comportamiento asumido que [plusPromoWelcomedUids].
     */
    val foundersPromoGrantedUids: Set<String>
    fun addFoundersPromoGrantedUid(uid: String)

    /** First Home editor offer, remembered per account across sign-out. */
    val firstHomeParkingFlowStartedUids: Set<String>
    fun startFirstHomeParkingFlow(uid: String)
    val firstHomeParkingFlowCompletedUids: Set<String>
    fun completeFirstHomeParkingFlow(uid: String)

    val autoDetectParking: Boolean
    fun setAutoDetectParking(enabled: Boolean)
    /** Reactive view of [autoDetectParking] so Home's detection banner and the Android arming
     *  orchestration update live when the user flips the Settings toggle. [DET-TOGGLE-001] */
    fun observeAutoDetectParking(): Flow<Boolean>

    // ── First-park nudge (cold-start reminder). [DET-TOGGLE-002] ──
    /** How many cold-start nudges have been shown — capped so we never nag. */
    val firstParkNudgeCount: Int
    fun setFirstParkNudgeCount(count: Int)
    /** Epoch millis of the last cold-start nudge (0 = never). Cooldown anchor. */
    val lastFirstParkNudgeAtMillis: Long
    fun setLastFirstParkNudgeAt(millis: Long)
    /** True once the user has had a first parking confirmed — auto-disables the cold-start nudge. */
    val hasConfirmedFirstPark: Boolean
    fun setHasConfirmedFirstPark()

    // ── Guided first steps. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──
    /**
     * Steps the guided checklist has already banked, so a completed step never walks backwards when
     * the state that completed it goes away (releasing a parking must not un-teach step 1).
     *
     * ⛔ Deliberately NOT expressed through [hasConfirmedFirstPark]. That flag is the arming
     * condition of the cold-start notification (`FirstParkNudgeWorker` → `isFirstParkNudgeSpent`),
     * so clearing it to replay the tutorial from Settings would wake a machine that has nothing to
     * do with onboarding. Two questions, two flags.
     */
    fun observeFirstStepsDone(): Flow<Set<FirstStep>>
    fun setFirstStepsDone(steps: Set<FirstStep>)

    /** The user skipped the checklist, or acknowledged its closing card. Replaying from Settings
     *  clears this together with [setFirstStepsDone]. */
    fun observeFirstStepsDismissed(): Flow<Boolean>
    fun setFirstStepsDismissed(dismissed: Boolean)

    /**
     * Steps the user answered with "not yet" — a permission they chose not to grant.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     *
     * A SECOND set, never folded into [observeFirstStepsDone]: deferred is not done. Banking a
     * declined step as completed would make the checklist claim the user did something they
     * explicitly refused, and the closing card would congratulate them for it.
     *
     * Persisted for the reason the whole ticket exists: a deferral that only lived in memory would
     * put the user back on the same wall at the next cold start. Replaying from Settings clears it
     * with the rest.
     */
    fun observeFirstStepsDeferred(): Flow<Set<FirstStep>>
    fun setFirstStepsDeferred(steps: Set<FirstStep>)

    /**
     * The complements whose provenance has already been said — where their data comes from and what
     * it does not promise. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * ONE fact with TWO readers, and that is the point of it living here rather than as a flag per
     * lane: the sheet asks it to decide whether the lane still owes its line, and the checklist asks
     * it to decide whether [FirstStep.EXPLORE_NEARBY] is done. A second latch for the step could
     * claim the user had been told something the lane never said.
     *
     * User-scoped, like the tutorial: what somebody KNOWS is theirs, not this phone's. Unknown names
     * are dropped on read, so retiring a complement reads as "never introduced" instead of crashing.
     */
    fun observeIntroducedComplements(): Flow<Set<NearbyComplement>>
    fun setIntroducedComplements(complements: Set<NearbyComplement>)

    // ── Coches cuyo grupo se disolvió sin que el usuario hiciera nada.
    //    [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] ──
    /**
     * Las filas que eran PRESTADAS y han dejado de serlo porque el dueño cerró el grupo — borrando
     * el coche o su cuenta. Esperan una decisión del usuario, y por eso son estado y no un evento:
     * el grupo puede disolverse con la app cerrada, y la pregunta tiene que seguir ahí al abrirla.
     *
     * ⛔ Un CONJUNTO, no un slot: se puede estar en varios grupos y cerrarse dos el mismo día.
     *
     * ⛔ Entra **toda** fila que deja de ser prestada, la haya soltado quien la haya soltado: que el
     * dueño cierre el grupo y que te salgas tú tienen la misma consecuencia —la fila se queda y
     * pasa a ocupar plaza de coche— y ninguna de las dos la cuenta nadie. Salir es una decisión
     * sobre el GRUPO, no sobre el coche. Lo que NO entra es `stopSharing`: ahí la fila es del
     * propio dueño y ya contaba antes.
     */
    fun observeDissolvedSharedCars(): Flow<Set<String>>
    fun addDissolvedSharedCar(vehicleId: String)
    fun clearDissolvedSharedCar(vehicleId: String)

    // ── Pending "where did you leave your car?" nudge. [DET-NUDGE-PERSIST-001] ──
    /** The unanswered mark-parking nudge, or null. Single slot: a new set replaces the previous
     *  one (there is only ever one lost car to ask about). Written by the notification adapter at
     *  the SAME choke point that posts the nudge, so the question survives as app state (field
     *  2026-07-25: the notification alone was slept through and the session was lost). */
    /**
     * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The plaza published on a boarding that has not yet
     * become a departure. Read to bound the withdrawal; cleared when the departure promotes it.
     */
    fun pendingSpotRelease(): PendingSpotRelease?
    fun setPendingSpotRelease(release: PendingSpotRelease)
    fun clearPendingSpotRelease()

    fun observePendingParkNudge(): Flow<PendingParkNudge?>
    fun setPendingParkNudge(nudge: PendingParkNudge)
    fun clearPendingParkNudge()

    // ── Open "did you park?" question. [DET-ASK-STATE-001] ──
    /** The prompt currently posted on the confirmation notification channel, or null. Single slot:
     *  that channel only ever holds one question. Written and cleared by the notification adapter at
     *  the same two verbs that post and close the notification, so the in-app row and the tray can
     *  never disagree about whether there is something to answer. */
    fun pendingPromptWindow(): PendingPromptWindow?
    fun stillParkedQuestion(): com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?
    fun observeStillParkedQuestion(): Flow<com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?>
    fun setStillParkedQuestion(question: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?)

    fun observePendingPromptWindow(): Flow<PendingPromptWindow?>
    fun setPendingPromptWindow(window: PendingPromptWindow)
    fun clearPendingPromptWindow()

    /** Gate for the INFORMATIVE parking notifications (e.g. "parking saved"). Safety asks —
     *  "did you park?", the revertable auto-confirm, "still parked?" — are never gated by this:
     *  they are the anti-false-positive mechanism. [SETTINGS-AUDIT-REMEDIATION-001] */
    val notifyParkingDetected: Boolean
    fun setNotifyParkingDetected(enabled: Boolean)

    /** Master gate for the Plus zone-alert notifications ("free spot in your zone"). ON by
     *  default — subscribing a zone already expressed the wish; this switch exists so Settings
     *  can silence the channel without touching any zone's own opt-in. [MONEY-001] */
    val notifyZoneSpots: Boolean
    fun setNotifyZoneSpots(enabled: Boolean)

    /** Reactive twin of [notifyZoneSpots]. Needed since the push index must be rewritten the
     *  moment the channel is silenced — a snapshot read would leave the server pushing.
     *  [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001] */
    fun observeNotifyZoneSpots(): Flow<Boolean>

    /** Master gate for the Plus fuel-price alerts (a cheaper pump nearby, and what a followed
     *  station did overnight). Sibling of [notifyZoneSpots] and ON by default for the same reason:
     *  following a station already expressed the wish, and this switch exists so Settings can
     *  silence the channel without the user having to unfollow anything.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    val notifyFuelPrices: Boolean
    fun setNotifyFuelPrices(enabled: Boolean)

    /**
     * Master gate for the Plus month recap — the one thing the app says entirely on its own.
     * [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] [STATS-PLUS-THE-RECAP-KNOCKS-001]
     *
     * ⛔ It did not exist, and its absence was TWO defects wearing one silence: a Plus account
     * could not turn the recap off while it could silence its three siblings, and a free account
     * had no surface anywhere naming a feature it does not get — no card (the panel simply does
     * not compose one), no notification (`RunMonthRecapUseCase` skips it), and no mention. A row
     * that does not exist cannot be soft-gated, and soft gating is the only way this plan sells.
     *
     * ON by default, like its siblings: the recap is a once-a-month knock, not a channel somebody
     * opted into.
     */
    val notifyMonthRecap: Boolean
    fun setNotifyMonthRecap(enabled: Boolean)

    /**
     * The closed month already told, as `yyyymm` — null until the first one is.
     * [STATS-PLUS-THE-RECAP-KNOCKS-001]
     *
     * ⛔ The MONTH and not a boolean: the app can wake seven days running inside the window, and a
     * flag would either repeat September every day or, once cleared, have nothing to say about
     * October. A month number answers both questions with one value.
     */
    val lastMonthRecapAnnounced: Int?
    fun setLastMonthRecapAnnounced(yearMonth: Int)

    /** [PRIV-A-TRACE-IS-NOT-A-STAT-001] Whether the ROUTE GEOMETRY (polyline + inferred spans) may
     *  leave the device. Opt-in and Plus-gated in Settings, default **OFF**: the trip's FIGURES
     *  (distance, search time) always sync -- they are numbers; the trace itself (where you drove)
     *  only with this consent. USER-scoped: consent belongs to the account, cleared on sign-out. */
    val syncRouteGeometry: Boolean
    fun setSyncRouteGeometry(enabled: Boolean)

    /**
     * La última plaza por la que el usuario dio fe estando en ella («sigue libre», voto aceptado y
     * dentro del filtro de proximidad). Null si no hay ninguna viva.
     * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
     *
     * ⛔ **Sólo se guarda la ÚLTIMA, no una lista.** Dar fe de dos plazas y aparcar en la primera es
     * un caso que existe y que esto no verá — y perderlo sólo hace la cifra más pequeña, que es el
     * lado correcto de equivocarse. Una lista añadiría una poda, una caducidad y un tamaño máximo
     * para ganar un caso raro.
     *
     * ⚠️ Vive aquí y no en Room porque tiene que sobrevivir a la muerte del proceso —entre el voto
     * y el aparcamiento pueden pasar minutos y el servicio de detección va por su cuenta— y porque
     * es UN registro, no una tabla.
     */
    val vouchedSpot: VouchedSpot?
    fun setVouchedSpot(spot: VouchedSpot?)

    /**
     * Fingerprint of the detection state last published to the account's Firestore doc, or null if
     * nothing has been published yet. [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
     *
     * ⛔ This is what makes the feature affordable. `PermissionManager.permissionState` is refreshed
     * on every resume, so publishing on observation would mean one Firestore write per app opening
     * per user. Comparing against this makes the write happen when the state CHANGES instead — and
     * it has to be PERSISTED rather than a `distinctUntilChanged`, which forgets across processes
     * and would put the storm back on every cold start.
     *
     * An opaque string on purpose: its only operation is equality, and giving it structure would
     * invite someone to parse it back into state it is not.
     *
     * USER-scoped — cleared on sign-out, so the next account publishes its own snapshot instead of
     * inheriting the previous one's silence.
     */
    val lastPublishedDetectionState: String?
    fun setLastPublishedDetectionState(fingerprint: String)

    val themeMode: ThemeMode
    fun setThemeMode(mode: ThemeMode)

    val useImperialUnits: Boolean
    fun setUseImperialUnits(enabled: Boolean)

    /** Stores map type as a plain string ("NORMAL" | "HYBRID"); "TERRAIN" and "SATELLITE" are legacy values still read, never written. */
    val defaultMapType: String
    fun setDefaultMapType(type: String)

    // ⛔ There are no map-LAYER preferences here any more (`showFuelStationsLayer`,
    // `showPublicParkingsLayer`). What the map paints of fuel and car parks is decided by the sheet's
    // lens — where the user is LOOKING — not by a switch that outlives the session. A subject visible
    // in one surface and hidden in the other was two controls for one concept, and the pair could
    // also stack two label layers over the spot pins that are the product.
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
}
