/* ============================================================================
   Paparcar — motor compartido de los prototipos de home.

   Tres responsabilidades, y ninguna más:
     1. ESCENARIO — un único objeto de estado que las tres alternativas leen.
        Es lo que hace la comparación justa: las tres pintan los mismos datos.
     2. HOJA — arrastre y anclas (peek / half / expanded / full), con la misma
        geometría que HomeSheetPositioning.kt.
     3. PIEZAS — iconos, mapa ilustrativo y el panel de control del prototipo.

   ⛔ Todos los datos son de DEMOSTRACIÓN. Ninguna cifra sale de la app.
   ============================================================================ */

/* ── 1 · Escenario ────────────────────────────────────────────────────────── */

const DEFAULT_SCENARIO = {
  car: 'parked',       // parked | driving | unmarked
  watch: 'ok',         // ok | fragile | interrupted
  ask: 'none',         // none | pending   ("¿has aparcado?", con plazo)
  spots: 0,            // 0 | 4
  fuel: 'prices',      // none | noprices | prices
  parkings: 3,         // 0 | 3
  loc: 'ok',           // ok | missing
  cars: 1,             // 1 | 2
  onboarding: false,   // checklist de primeros pasos visible
  perms: 'ok',         // ok | blocked   (falta permiso CORE)
  net: 'ok',           // ok | offline

  // ── Ejes nuevos de la revisión del user (27-10) ──────────────────────────
  /** Edad del precio más barato. 'stale' = la fuente lo publicó hace demasiado,
   *  así que NO es afirmable como noticia y la gasolinera deja de ser notable. */
  fuelAge: 'fresh',    // fresh | stale
  /** ¿Sabemos el gálibo del parking más cercano? 'unknown' no es «no cabe». */
  clearance: 'known',  // known | unknown
  /** Qué hace la cabecera al PLEGAR la hoja con el scroll dentro del carril. */
  latch: 'reset',      // reset (recomendada) | keep (lo de hoy) | car (incoherente)
  /** Cuándo la cabecera pasa a hablar del carril. */
  laneHeader: 'scroll',// scroll (recomendada) | always (lo de hoy) | never

  // ── Ejes de la 5.ª vuelta (28-10) ──────────────────────────────────
  /**
   * ¿Ha MOVIDO hoy algún precio a tu alrededor? [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
   *
   * ⛔ No es «el precio es distinto»: `EvaluateDailyPriceMoveUseCase` sólo reclama
   * un movimiento cuando **las dos muestras llevan sello de la fuente Y los
   * sellos difieren** — un feed puede republicar el mismo precio con sello nuevo,
   * y en Italia el 69 % de los precios tienen de dos a siete días. Si no se puede
   * afirmar, el veredicto es «sin noticias», que **no es** «sin cambios».
   */
  fuelMove: 'none',    // none | drop | rise
  /**
   * Cómo se abre la app. `news` = hay algo que NO estaba la última vez que
   * miró — el disparador del auto-abrir no es «hay contenido» sino «hay novedad».
   */
  launch: 'quiet',     // quiet | news
};

const scn = Object.assign({}, DEFAULT_SCENARIO);
const listeners = [];

function onScenario(fn) { listeners.push(fn); fn(scn); }
function setScenario(patch) {
  Object.assign(scn, patch);
  listeners.forEach((fn) => fn(scn));
}

/* Datos de demostración — verosímiles y explícitamente falsos. */
const DEMO = {
  vehicles: [
    { id: 'v1', name: 'Ford Focus', watch: 'active', size: 'Mediano',
      addr: 'Calle de Embajadores, 118', ago: 'hace 2 h 10 min', dist: '180 m' },
    { id: 'v2', name: 'Vespa Primavera', watch: 'bt', size: 'Moto',
      addr: 'Plaza de Lavapiés', ago: 'hace 1 d', dist: '1,2 km' },
  ],
  spots: [
    { id: 's1', place: 'Calle del Olivar, 34', meta: 'Mediano · 120 m', age: '4 min', heat: 'fresh' },
    { id: 's2', place: 'Calle de la Fe, 12',   meta: 'Grande · 260 m',  age: '9 min', heat: 'fresh' },
    { id: 's3', place: 'Calle de Zurita, 7',   meta: 'Pequeño · 340 m', age: '17 min', heat: 'cooling' },
    { id: 's4', place: 'Calle de Argumosa, 3', meta: 'Mediano · 410 m', age: '24 min', heat: 'expiring' },
  ],
  // ⛔ Distancia y precio como NÚMEROS, no como texto: el standout de abajo se
  // CALCULA, no se afirma. Un prototipo que escribe a mano cuál es la más barata
  // no prueba que la regla funcione.
  // ⛔ **Sin `rate`, y es una corrección, no una simplificación.** Este prototipo mostraba
  // «2,10 €/h» y la app **no tiene ese dato de ninguna fuente**: `FeeInfo` es
  // `{ PAID, FREE, UNKNOWN }` — el `fee` de OSM es sí/no, no un precio. Un importe
  // inventado presentado como resultado es justo lo que el proyecto prohíbe, y encima
  // hizo que «standout en parkings» pareciera razonable cuando no lo era.
  // Lo único comparable que un parking sabe es si COBRA, y la fila ya lo dice.
  // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
  parkings: [
    { id: 'p1', name: 'Parking Embajadores', m: 240, fee: 'free',    fit: 'Cabe tu coche' },
    { id: 'p2', name: 'Aparcamiento Tirso',  m: 520, fee: 'unknown', fit: 'Gálibo sin dato' },
    { id: 'p3', name: 'Parking Mercado',     m: 780, fee: 'paid',    fit: 'Cabe tu coche' },
  ],
  fuel: [
    { id: 'f1', name: 'Repsol Embajadores', m: 300,  price: 1.489, follow: true },
    { id: 'f2', name: 'Cepsa Ronda',        m: 850,  price: 1.529, follow: false },
    { id: 'f3', name: 'Ballenoil Méndez',   m: 1400, price: 1.419, follow: false },
  ],
  fuelStamp: 'Precios publicados por la fuente a las 09:00',
  product: 'Gasolina 95',
};

/* ── 2 · Iconos (Material Symbols Rounded, redibujados a mano) ───────────── */

const ICONS = {
  search: 'M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l252 252q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z',
  car: 'M240-200v32q0 17-11.5 28.5T200-128h-16q-17 0-28.5-11.5T144-168v-304l84-240q4-12 14.5-20t23.5-8h428q13 0 23.5 8t14.5 20l84 240v304q0 17-11.5 28.5T776-128h-16q-17 0-28.5-11.5T720-168v-32H240Zm6-320h468l-42-120H288l-42 120Zm-46 80v160h560v-160H200Zm110 120q21 0 35.5-14.5T360-370q0-21-14.5-35.5T310-420q-21 0-35.5 14.5T260-370q0 21 14.5 35.5T310-320Zm340 0q21 0 35.5-14.5T700-370q0-21-14.5-35.5T650-420q-21 0-35.5 14.5T600-370q0 21 14.5 35.5T650-320Z',
  moto: 'M160-200q-66 0-113-47T0-360q0-66 47-113t113-47q50 0 90 28t57 72h66l-80-120h-93v-80h120l100 150 100-150h160v80h-80l80 120h20q66 0 113 47t47 113q0 66-47 113t-113 47q-58 0-101-36t-55-92H316q-12 56-55 92t-101 36Zm0-80q25 0 44-15t26-37H160q-17 0-28.5-11.5T120-372q0-17 11.5-28.5T160-412h70q-7-22-26-37t-44-15q-33 0-56.5 23.5T80-384q0 33 23.5 56.5T160-304v24Zm640 0q33 0 56.5-23.5T880-360q0-33-23.5-56.5T800-440q-33 0-56.5 23.5T720-360q0 33 23.5 56.5T800-280Z',
  fuel: 'M240-120q-17 0-28.5-11.5T200-160v-640q0-17 11.5-28.5T240-840h280q17 0 28.5 11.5T560-800v280h40q33 0 56.5 23.5T680-440v220q0 25 17.5 42.5T740-160q25 0 42.5-17.5T800-220v-288q-9 5-19 6.5t-21 1.5q-50 0-85-35t-35-85q0-38 21.5-69t56.5-44l-83-83 34-34 136 133q14 14 23 33t9 40v524q0 48-33 81t-81 33q-48 0-81-33t-33-81v-220q0-17-11.5-28.5T560-440h-40v280q0 17-11.5 28.5T480-120H240Zm40-400h200v-240H280v240Z',
  parking: 'M360-280h80v-160h100q33 0 56.5-23.5T620-520v-80q0-33-23.5-56.5T540-680H360v400Zm80-240v-80h100v80H440ZM200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h560q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-560H200v560Z',
  spot: 'M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-160q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47Z',
  place: 'M480-480q33 0 56.5-23.5T560-560q0-33-23.5-56.5T480-640q-33 0-56.5 23.5T400-560q0 33 23.5 56.5T480-480Zm0 294q122-112 181-203.5T720-552q0-109-69.5-178.5T480-800q-101 0-170.5 69.5T240-552q0 71 59 162.5T480-186Zm0 106q-14 0-28-5t-25-15q-65-60-115-117t-83.5-110.5q-33.5-53.5-51-103T160-552q0-134 86.5-215T480-848q147 0 233.5 81T800-552q0 49-17.5 98.5t-51 103Q698-297 648-240T533-123q-11 10-25 15t-28 5Z',
  clock: 'M520-496v-144q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v155q0 8 3 15.5t9 13.5l132 132q11 11 28 11t28-11q11-11 11-28t-11-28L520-496ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-400Zm0 320q132 0 226-94t94-226q0-132-94-226t-226-94q-132 0-226 94t-94 226q0 132 94 226t226 94Z',
  shield: 'M480-80q-139-35-229.5-159.5T160-516v-184q0-25 14.5-45t37.5-29l240-90q9-3 18-3t18 3l240 90q23 9 37.5 29t14.5 45v184q0 152-90.5 276.5T480-80Zm0-84q104-33 172-132t68-220v-184l-240-90-240 90v184q0 121 68 220t172 132Zm0-316Z',
  warn: 'M109-120q-11 0-20-5.5T75-140q-5-9-5.5-19.5T75-180l371-640q6-10 15.5-15t19.5-5q10 0 19.5 5T516-820l371 640q6 10 5.5 20.5T887-140q-5 9-14 14.5t-20 5.5H109Zm69-80h604L480-720 178-200Zm302-40q17 0 28.5-11.5T520-280q0-17-11.5-28.5T480-320q-17 0-28.5 11.5T440-280q0 17 11.5 28.5T480-240Zm0-120q17 0 28.5-11.5T520-400v-120q0-17-11.5-28.5T480-560q-17 0-28.5 11.5T440-520v120q0 17 11.5 28.5T480-360Z',
  chevron: 'M504-480 320-664q-11-11-11-28t11-28q11-11 28-11t28 11l212 212q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L376-240q-11 11-28 11t-28-11q-11-11-11-28t11-28l184-184Z',
  close: 'm336-280 144-144 144 144 56-56-144-144 144-144-56-56-144 144-144-144-56 56 144 144-144 144 56 56ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Z',
  mylocation: 'M480-80q-17 0-28.5-11.5T440-120v-41q-114-14-194-94t-94-194h-41q-17 0-28.5-11.5T71-489q0-17 11.5-28.5T111-529h41q14-114 94-194t194-94v-41q0-17 11.5-28.5T480-898q17 0 28.5 11.5T520-858v41q114 14 194 94t94 194h41q17 0 28.5 11.5T889-489q0 17-11.5 28.5T849-449h-41q-14 114-94 194t-194 94v41q0 17-11.5 28.5T480-80Zm0-120q116 0 198-82t82-198q0-116-82-198t-198-82q-116 0-198 82t-82 198q0 116 82 198t198 82Zm0-120q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47Z',
  layers: 'M480-120 120-400l66-50 294 228 294-228 66 50-360 280Zm0-202L121-600l359-280 360 280-360 278Zm0-278Zm1 70 230-180-231-180-230 180 231 180Z',
  plus: 'M440-440H200v-80h240v-240h80v240h240v80H520v240h-80v-240Z',
  bolt: 'm422-232 207-248H469l29-227-185 267h139l-30 208ZM320-80l40-280H160l360-520h80l-40 320h240L400-80h-80Z',
  home: 'M240-200h120v-200q0-17 11.5-28.5T400-440h160q17 0 28.5 11.5T600-400v200h120v-360L480-740 240-560v360Zm-80 0v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H560q-17 0-28.5-11.5T520-160v-200h-80v200q0 17-11.5 28.5T400-120H240q-33 0-56.5-23.5T160-200Z',
  settings: 'm405-80q-15 0-26-9.5T366-114l-13-99q-13-5-24.5-12T306-241l-92 38q-14 6-28 1t-22-18L111-337q-8-13-5-28t15-24l80-59q-1-7-1-14v-36q0-7 1-14l-80-59q-12-9-15-24t5-28l53-97q8-13 22-18t28 1l92 38q11-8 23-15t24-12l13-99q2-15 13-24.5t26-9.5h150q15 0 26 9.5t13 24.5l13 99q13 5 24.5 12t22.5 15l92-38q14-6 28-1t22 18l53 97q8 13 5 28t-15 24l-80 59q1 7 1 14v36q0 7-1 14l80 59q12 9 15 24t-5 28l-53 97q-8 13-22 18t-28-1l-92-38q-11 8-23 15t-24 12l-13 99q-2 15-13 24.5T555-80H405Zm75-260q58 0 99-41t41-99q0-58-41-99t-99-41q-58 0-99 41t-41 99q0 58 41 99t99 41Z',
  star: 'm480-269 106 64q13 8 26-1t9-24l-28-121 94-82q12-10 7.5-25T676-494l-124-11-48-115q-6-14-22-14t-22 14l-48 115-124 11q-16 1-20.5 16t7.5 25l94 82-28 121q-4 15 9 24t26 1l104-64Z',
  route: 'M280-80q-83 0-141.5-58.5T80-280q0-83 58.5-141.5T280-480h320q50 0 85-35t35-85q0-50-35-85t-85-35H320v80l-120-120 120-120v80h280q83 0 141.5 58.5T840-600q0 83-58.5 141.5T640-400H280q-50 0-85 35t-35 85q0 50 35 85t85 35h440v-80l120 120-120 120v-80H280Z',
  trend: 'M136-240q-11-11-11-28t11-28l212-212q11-11 25.5-11t25.5 11l106 106 161-161h-66q-17 0-28.5-11.5T560-604q0-17 11.5-28.5T600-644h160q17 0 28.5 11.5T800-604v160q0 17-11.5 28.5T760-404q-17 0-28.5-11.5T720-444v-66L533-323q-11 11-25.5 11T482-323L376-429 192-240q-11 11-28 11t-28-11Z',
};

function icon(name, size = 20, cls = '') {
  const d = ICONS[name] || ICONS.spot;
  return `<svg class="${cls}" width="${size}" height="${size}" viewBox="0 -960 960 960"
            fill="currentColor" aria-hidden="true" focusable="false"><path d="${d}"/></svg>`;
}

/* ── 3 · Mapa ilustrativo ─────────────────────────────────────────────────── */
/* No es un mapa: es un decorado propio y estático. Sin clave de API, sin red.
   Pinta lo que el mapa real pinta SIEMPRE (tus coches, tus zonas, tu viaje) más
   la capa del carril activo — nunca marcadores de relleno.                     */

/** El decorado del mapa. [detail] resalta el pin del sujeto abierto, que es lo
 *  que hace visible que una tarjeta y una fila de la lista llevan AL MISMO
 *  sitio: la cámara va al punto y el pin se marca. */
function mapSvg(s, lens, detail) {
  const PINS = {
    spot:    [[118, 300], [268, 212], [206, 392], [316, 338]],
    parking: [[142, 258], [284, 330], [96, 398]],
    fuel:    [[150, 282], [296, 364], [210, 180]],
  };
  const sel = detail && PINS[detail.kind] ? PINS[detail.kind][detail.index % PINS[detail.kind].length] : null;
  const halo = sel ? `
    <g transform="translate(${sel[0]} ${sel[1]})">
      <circle r="30" fill="var(--green)" opacity=".14"/>
      <circle r="30" fill="none" stroke="var(--green)" stroke-width="2" opacity=".85"/>
    </g>` : '';

  const pin = (x, y, fill, ink, label) => `
    <g transform="translate(${x} ${y})">
      <path d="M0 0c0 0 13-16 13-25A13 13 0 1 0-13-25C-13-16 0 0 0 0Z"
            fill="${fill}" stroke="rgba(0,0,0,.35)" stroke-width="1"/>
      <text x="0" y="-21" text-anchor="middle" font-size="12" font-weight="700"
            fill="${ink}" font-family="system-ui">${label}</text>
    </g>`;

  const spotPins = (s.spots > 0 && lens === 'spots') ? `
    ${pin(118, 300, 'var(--spot-fresh)', '#10290a', '')}
    ${pin(268, 212, 'var(--spot-fresh)', '#10290a', '')}
    ${pin(206, 392, 'var(--spot-cooling)', '#2a1b00', '')}
    ${pin(316, 338, 'var(--spot-expiring)', '#2a0808', '')}` : '';

  const parkPins = (s.parkings > 0 && lens === 'parkings') ? `
    ${pin(142, 258, 'var(--car-park)', '#fff', 'P')}
    ${pin(284, 330, 'var(--car-park)', '#fff', 'P')}
    ${pin(96, 398, 'var(--car-park)', '#fff', 'P')}` : '';

  const fuelPins = (s.fuel !== 'none' && lens === 'fuel') ? `
    <g transform="translate(150 282)">
      <rect x="-30" y="-15" rx="8" width="60" height="24" fill="var(--amber)"/>
      <text x="0" y="2" text-anchor="middle" font-size="11" font-weight="800"
            fill="#2a1b00" font-family="system-ui">${s.fuel === 'prices' ? '1,489' : '· · ·'}</text>
    </g>
    <g transform="translate(296 364)">
      <rect x="-30" y="-15" rx="8" width="60" height="24" fill="var(--amber)"/>
      <text x="0" y="2" text-anchor="middle" font-size="11" font-weight="800"
            fill="#2a1b00" font-family="system-ui">${s.fuel === 'prices' ? '1,529' : '· · ·'}</text>
    </g>` : '';

  const identity = s.car === 'driving' ? 'var(--green)'
    : (DEMO.vehicles[0].watch === 'bt' ? 'var(--car-blue)' : 'var(--green)');

  const carMark = s.car === 'parked' ? `
    <g transform="translate(196 330)">
      <rect x="-15" y="-15" rx="6" width="30" height="30"
            fill="var(--container)" stroke="${identity}" stroke-width="2.5"/>
      <g transform="translate(-9 9) scale(.019)" fill="${identity}">
        <path d="${ICONS.car}"/>
      </g>
    </g>` : '';

  const trip = s.car === 'driving' ? `
    <path d="M86 470 Q150 420 182 372 T252 300 Q286 266 330 250"
          fill="none" stroke="${identity}" stroke-width="5" stroke-linecap="round" opacity=".92"/>
    <circle cx="86" cy="470" r="6" fill="${identity}" opacity=".6"/>
    <g transform="translate(330 250)">
      <circle r="22" fill="${identity}" opacity=".16"/>
      <circle r="11" fill="${identity}" stroke="var(--container)" stroke-width="3"/>
    </g>` : '';

  const puck = s.loc === 'ok' ? `
    <g transform="translate(196 452)">
      <circle r="26" fill="var(--green)" opacity=".13"/>
      <circle r="8" fill="var(--green)" stroke="var(--container)" stroke-width="3"/>
    </g>` : '';

  return `
  <svg viewBox="0 0 390 844" preserveAspectRatio="xMidYMid slice" role="img"
       aria-label="Mapa ilustrativo de demostración">
    <rect width="390" height="844" fill="var(--map-bed)"/>
    <path d="M-20 560 Q90 520 190 548 T420 520 L420 620 Q300 650 180 626 T-20 650 Z"
          fill="var(--map-water)" opacity=".8"/>
    <g stroke="var(--map-road-minor)" stroke-width="7">
      <path d="M-10 180 H400"/><path d="M-10 250 H400"/><path d="M-10 430 H400"/>
      <path d="M-10 500 H400"/><path d="M60 100 V760"/><path d="M330 100 V760"/>
    </g>
    <g stroke="var(--map-road)" stroke-width="15" stroke-linecap="round">
      <path d="M-10 320 H400"/>
      <path d="M196 90 V780"/>
      <path d="M-10 690 Q140 640 250 700 T400 660"/>
      <path d="M270 90 L270 320 L400 400"/>
    </g>
    <g stroke="var(--green)" stroke-width="1.5" fill="var(--green)" fill-opacity=".07"
       stroke-dasharray="5 4" opacity=".75">
      <circle cx="104" cy="214" r="48"/>
    </g>
    <text x="104" y="214" text-anchor="middle" font-size="10" font-weight="700"
          fill="var(--green)" font-family="system-ui" opacity=".9">CASA</text>
    ${halo}${spotPins}${parkPins}${fuelPins}${carMark}${trip}${puck}
  </svg>`;
}

/* ── 4 · La hoja — misma geometría que HomeSheetPositioning.kt ───────────── */

function mountSheet(phone, opts) {
  const sheet = phone.querySelector('.sheet');
  const handle = phone.querySelector('.handle');
  const nav = phone.querySelector('.bottomnav');
  const chrome = phone.querySelector('.map-chrome');
  const fabs = phone.querySelector('.map-fabs');
  const locpill = phone.querySelector('.locpill');

  const NAV = 64;
  const container = phone.clientHeight - NAV;
  const state = { h: 0, detents: null };

  function detents() {
    const peek = handle.offsetHeight || opts.peek || 88;
    const expanded = Math.round(container * 0.58);   // EXPANDED_MAP_VISIBLE_FRACTION 0.42
    const half = Math.round((peek + expanded) / 2);
    return { peek, half, expanded, full: container };
  }

  function apply(h, animate = true) {
    const d = state.detents;
    state.h = Math.max(d.peek, Math.min(d.full, h));
    sheet.style.transition = animate ? 'transform .3s cubic-bezier(.4,0,.2,1)' : 'none';
    sheet.style.transform = `translateY(${container - state.h}px)`;

    const beyondHalf = state.h > (d.half + d.expanded) / 2;
    const navHidden = state.h > d.half;
    nav.dataset.hidden = String(navHidden);
    sheet.style.bottom = navHidden ? '0px' : NAV + 'px';
    chrome.dataset.hidden = String(beyondHalf);
    const base = (navHidden ? 0 : NAV) + state.h + 12;
    fabs.style.bottom = base + 'px';
    if (locpill) locpill.style.bottom = base + 'px';
    fabs.style.opacity = beyondHalf ? '0' : '1';
    fabs.style.pointerEvents = beyondHalf ? 'none' : 'auto';
    if (locpill) locpill.style.opacity = beyondHalf ? '0' : '1';
    phone.dataset.sheet = nearest(state.h);
  }

  function nearest(h) {
    const d = state.detents;
    return Object.keys(d).reduce((best, k) =>
      Math.abs(d[k] - h) < Math.abs(d[best] - h) ? k : best, 'peek');
  }

  function relayout(animate = false) {
    state.detents = detents();
    apply(state.h || state.detents.peek, animate);
  }

  /* Arrastre */
  let drag = null;
  handle.addEventListener('pointerdown', (e) => {
    if (e.target.closest('button, a, [role="button"]')) return;
    drag = { y: e.clientY, h0: state.h, moved: false, t: Date.now() };
    handle.dataset.grabbing = 'true';
    handle.setPointerCapture(e.pointerId);
  });
  handle.addEventListener('pointermove', (e) => {
    if (!drag) return;
    const dy = drag.y - e.clientY;
    if (Math.abs(dy) > 4) drag.moved = true;
    apply(drag.h0 + dy, false);
  });
  handle.addEventListener('pointerup', () => {
    if (!drag) return;
    const d = state.detents;
    if (!drag.moved) {
      apply(state.h <= d.peek + 4 ? d.expanded : d.peek);
    } else {
      apply(d[nearest(state.h)]);
    }
    handle.dataset.grabbing = 'false';
    drag = null;
  });
  handle.addEventListener('pointercancel', () => { drag = null; handle.dataset.grabbing = 'false'; });

  /* Teclado — la hoja tiene que ser alcanzable sin ratón. */
  handle.tabIndex = 0;
  handle.setAttribute('role', 'button');
  handle.setAttribute('aria-label', 'Desplegar o plegar el panel');
  handle.addEventListener('keydown', (e) => {
    const d = state.detents;
    const order = ['peek', 'half', 'expanded', 'full'];
    const i = order.indexOf(nearest(state.h));
    if (e.key === 'ArrowUp')   { apply(d[order[Math.min(order.length - 1, i + 1)]]); e.preventDefault(); }
    if (e.key === 'ArrowDown') { apply(d[order[Math.max(0, i - 1)]]); e.preventDefault(); }
    if (e.key === 'Enter' || e.key === ' ') {
      apply(state.h <= d.peek + 4 ? d.expanded : d.peek); e.preventDefault();
    }
  });

  sheet.style.height = container + 'px';
  requestAnimationFrame(() => relayout(false));

  return {
    relayout,
    goTo: (name) => { state.detents = detents(); apply(state.detents[name]); },
    current: () => nearest(state.h),
  };
}

/* ── 5 · Panel de control del prototipo ─────────────────────────────────────*/

const CONTROLS = [
  { key: 'car',  label: 'Coche', opts: [['parked','Aparcado'],['driving','En ruta'],['unmarked','Sin marcar']] },
  { key: 'watch',label: 'Vigilancia', opts: [['ok','Viva'],['fragile','Frágil'],['interrupted','Interrumpida']] },
  { key: 'ask',  label: 'Pregunta con plazo', opts: [['none','Ninguna'],['pending','¿Has aparcado?']] },
  { key: 'spots',label: 'Plazas de la comunidad', opts: [[0,'Ninguna'],[4,'4 cerca']] },
  { key: 'fuel', label: 'Gasolineras', opts: [['prices','Con precios'],['noprices','Sin precios'],['none','Ninguna']] },
  { key: 'parkings', label: 'Parkings públicos', opts: [[3,'3 cerca'],[0,'Ninguno']] },
  { key: 'loc',  label: 'Ubicación', opts: [['ok','Concedida'],['missing','Sin permiso']] },
  { key: 'cars', label: 'Vehículos', opts: [[1,'Uno'],[2,'Dos']] },
  { key: 'onboarding', label: 'Usuario nuevo (checklist)', opts: [[false,'No'],[true,'Sí']] },
  { key: 'perms', label: 'Permiso CORE', opts: [['ok','Concedido'],['blocked','Falta']] },
  { key: 'fuelAge', label: 'Edad del precio más barato', opts: [['fresh','De hoy'],['stale','Demasiado viejo']] },
  { key: 'clearance', label: 'Gálibo del parking', opts: [['known','Conocido'],['unknown','Sin dato']] },
  { key: 'laneHeader', label: 'Cabecera del carril', opts: [['scroll','Al leerlo (recom.)'],['always','Siempre (hoy)'],['never','Nunca']] },
  { key: 'latch', label: 'Al plegar con el carril leído', opts: [['reset','Volver arriba ✓'],['keep','Mantener sujeto (hoy)'],['car','Sólo el coche']] },
  { key: 'fuelMove', label: 'Precio movido hoy cerca', opts: [['none','Sin noticias'],['drop','Bajó 4 cts'],['rise','Subió 3 cts']] },
  { key: 'launch', label: 'Al abrir la app', opts: [['quiet','Sin novedad'],['news','Con novedad → auto-abrir']] },
];

function buildPanel(root, meta) {
  root.innerHTML = `
    <h1>${meta.title}</h1>
    <p>${meta.idea}</p>
    <div class="note">${meta.note}</div>
    <div class="navlinks">
      <a href="index.html">Índice</a>
      <a href="vehicle-first.html" ${meta.id === 'A' ? 'aria-current="page"' : ''}>A · Vehicle-first</a>
      <a href="smart-mobility.html" ${meta.id === 'B' ? 'aria-current="page"' : ''}>B · Smart mobility</a>
      <a href="adaptive-home.html" ${meta.id === 'C' ? 'aria-current="page"' : ''}>C · Adaptive home</a>
    </div>
    <h2>Tema</h2>
    <div class="seg" id="themeseg">
      <button type="button" data-theme-set="dark">Oscuro</button>
      <button type="button" data-theme-set="light">Claro</button>
    </div>
    <h2>Estados — cámbialos y mira la pantalla</h2>
    <div id="controls"></div>
    <div class="why" id="why"></div>
    <h2>Anclas de la hoja</h2>
    <div class="seg" id="detentseg">
      <button type="button" data-detent="peek">Peek</button>
      <button type="button" data-detent="half">Media</button>
      <button type="button" data-detent="expanded">Desplegada</button>
      <button type="button" data-detent="full">Completa</button>
    </div>
    <p class="t-caption">Arrastra la cabecera de la hoja, o pulsa en ella. Con teclado: Tab hasta
    la cabecera y ↑ / ↓.</p>`;

  const box = root.querySelector('#controls');
  box.innerHTML = CONTROLS.map((c) => `
    <div class="seg" data-key="${c.key}">
      <span>${c.label}</span>
      ${c.opts.map(([v, l]) => `<button type="button" data-val="${v}">${l}</button>`).join('')}
    </div>`).join('');

  box.addEventListener('click', (e) => {
    const b = e.target.closest('button[data-val]');
    if (!b) return;
    const key = b.closest('[data-key]').dataset.key;
    let v = b.dataset.val;
    if (v === 'true') v = true; else if (v === 'false') v = false;
    else if (/^\d+$/.test(v)) v = Number(v);
    setScenario({ [key]: v });
  });

  root.querySelector('#themeseg').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-theme-set]');
    if (!b) return;
    const t = b.dataset.themeSet;
    document.documentElement.dataset.theme = t;
    root.querySelectorAll('[data-theme-set]').forEach((x) =>
      x.setAttribute('aria-pressed', String(x.dataset.themeSet === t)));
  });
  document.documentElement.dataset.theme = 'dark';
  root.querySelector('[data-theme-set="dark"]').setAttribute('aria-pressed', 'true');

  onScenario((s) => {
    box.querySelectorAll('[data-key]').forEach((seg) => {
      const cur = String(s[seg.dataset.key]);
      seg.querySelectorAll('button').forEach((b) =>
        b.setAttribute('aria-pressed', String(b.dataset.val === cur)));
    });
    root.querySelector('#why').innerHTML = meta.why(s);
  });

  return root;
}

/* Util — formatea una lista de filas con sus divisores internos. */
function rows(items, render) {
  return items.map((it, i) =>
    render(it, i) + (i < items.length - 1 ? '<hr class="divider inset">' : '')).join('');
}

function esc(str) {
  return String(str).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

/* ════════════════════════════════════════════════════════════════════════════
   6 · v2 — las dos reglas que salen de la revisión del user (27-10)
   ════════════════════════════════════════════════════════════════════════════ */

/* ── 6.1 · La gramática de DOS líneas, igual para cualquier sujeto ──────────
   línea 1 = NOMBRE del sujeto · su calificador
   línea 2 = DÓNDE · a CUÁNTO de ti
   la cifra propia del sujeto va al TRAILING.

   ⛔ La dirección aparece en UNA línea, nunca en las dos. Si el sujeto no tiene
   nombre propio —una plaza no lo tiene—, la línea 1 es su TIPO + calificador y
   la 2 la dirección. Eso es lo que quita la repetición que el user vio.

   ⛔ Y la distancia NO trunca: es el dato más corto y el más volátil, así que
   vive en el trailing de su línea con ancho propio, y lo que se recorta cuando
   no cabe es la calle. */

function subjectLines(kind, item, s) {
  const far = s.loc === 'ok' ? item.far : null;   // sin fix no hay «de ti» que decir
  switch (kind) {
    case 'car': {
      // Decisión del user: «hace 2 h» responde CUÁNDO → línea 1, junto al estado.
      // «a 180 m» responde DÓNDE → línea 2, junto a la calle. Dos líneas, dos preguntas.
      const st = carStatus(s);
      const when = s.car === 'parked' ? `${st.eyebrow} ${item.ago}` : st.eyebrow;
      return { name: item.name, qual: when, where: st.line, far, figure: null };
    }
    case 'spot':
      // Una plaza NO tiene nombre: su línea 1 es el tipo + para quién cabe.
      return { name: 'Plaza libre', qual: item.size, where: item.place, far,
               figure: item.age, unit: null, tone: item.heat };
    case 'parking':
      // ⛔ El trailing no es un importe: es si COBRA, con su propia doctrina — `UNKNOWN`,
      // que es el **71 %** de los parkings del mundo, se pinta como **nada**, nunca como
      // «gratis». A un conductor al que le dijimos «gratis» y se encontró una barrera le
      // mintió la app, no OpenStreetMap.
      return { name: item.name, qual: item.fitLabel, where: 'Parking público', far,
               figure: null, unit: null,
               tail: item.fee === 'free' ? 'Gratuito' : item.fee === 'paid' ? 'De pago' : null };
    case 'fuel':
      return { name: item.name, qual: s.fuel === 'prices' ? DEMO.product : null,
               where: item.street, far,
               figure: s.fuel === 'prices' ? item.price : null, unit: '€/L' };
    default:
      return { name: '—', qual: null, where: null, far: null, figure: null };
  }
}

/* ── 6.2 · Qué entra en «AQUÍ Y AHORA» ──────────────────────────────────────
   Un item por sujeto COMO MÁXIMO, en orden FIJO (plaza · parking · gasolinera),
   y sólo si pasa su umbral. Tres decisiones, cada una con su motivo:

   · **Uno por sujeto**, no «los más llamativos»: dos plazas en el primer nivel
     son una lista, y la lista ya existe un nivel más abajo.
   · **Orden fijo**, no por «cuán notable es»: reordenar el primer nivel según
     los datos del momento es justo lo que hace impredecible una home. El orden
     es el mismo que el de las pestañas, así que no puede contradecirlo.
   · **Umbral por sujeto**, porque «notable» no significa lo mismo para los tres.

   ⛔ Si ninguno pasa, la sección NO EXISTE — ni título, ni hueco, ni caja que
   diga que no hay nada. Eso ya lo dice cada carril en su sitio. */

function notables(s) {
  const out = [];

  // Una plaza libre de la comunidad es noticia por existir: es el producto, y
  // caduca. No necesita más umbral que estar ahí.
  if (s.spots > 0) {
    const sp = DEMO.spots[0];
    out.push({
      kind: 'spot', lens: 'spots', count: s.spots, index: 0,
      eyebrow: 'Plaza libre más cerca',
      item: { place: sp.place, size: sp.meta.split(' · ')[0],
              far: sp.meta.split(' · ')[1], age: sp.age, heat: sp.heat },
      standout: null,   // ⛔ una plaza NO lleva standout — ver el comentario de abajo
      why: 'Una plaza existe y caduca: basta con que esté.',
    });
  }

  // Un parking es notable si está cerca. ⛔ «Sin dato de gálibo» NO es «no cabe»:
  // se ofrece igual y se dice que no lo sabemos.
  //
  // ⛔ **Y NO lleva standout.** Las tres formas posibles se caen al mirar los datos:
  // «más barato» es imposible (no hay importe, sólo `FeeInfo`), «más cercano» es
  // redundante (la lista se ordena **sólo** por distancia, así que el más cercano es la
  // primera fila) y «gratis» **ya lo dice la fila** en su trailing.
  // [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
  if (s.parkings > 0) {
    const list = DEMO.parkings.slice(0, s.parkings).map((x, i) => ({ ...x, i }));
    const pick = list[0];          // el más cercano: la lista ya viene ordenada así
    const known = s.clearance === 'known';
    out.push({
      kind: 'parking', lens: 'parkings', count: s.parkings, index: pick.i,
      eyebrow: 'Parking público más cerca',
      item: { name: pick.name, far: farText(pick.m), fee: pick.fee,
              fitLabel: known ? pick.fit : 'Gálibo sin dato' },
      standout: null,
      why: known
        ? 'Hay parking cerca y sabemos que cabe.'
        : 'Hay parking cerca; el gálibo no se publica, y eso se dice — no se supone.',
    });
  }

  // ⛔ Una gasolinera sólo es notable si hay PRECIO para tu producto Y su hora es
  // admisible. Sin precio no hay noticia (la lista sigue estando, con sus
  // gasolineras sin figura); con un precio demasiado viejo, tampoco: una cifra
  // que no puede decir cuándo se midió no se puede afirmar.
  if (s.fuel === 'prices' && s.fuelAge === 'fresh') {
    const list = DEMO.fuel.map((x, i) => ({ ...x, i }));
    const so = standouts(list);

    /* ⛔ **El CAMBIO gana al NIVEL, en el mismo hueco.** Un precio que lleva
       meses igual es un hecho; que haya bajado 4 céntimos HOY es noticia, y es
       lo que mueve a alguien a desviarse. Así que la tarjeta de gasolinera
       prefiere el movimiento cuando existe — no se añade una cuarta tarjeta ni
       una sección de «últimas actualizaciones»: el orden fijo se queda intacto.

       Los datos ya existen y son GRATIS: `RunFuelPriceWatchUseCase` evalúa cada
       día dos carriles en un tick — `followedMoves` (Plus) y **`nearbyMoves`**
       (free) — anclándose en **dónde ESTÁS**, con radio
       `AROUND_FOLLOWED_RADIUS_M = 1.000 m` y un tope de las más cercanas.
       ⛔ El radio no me lo invento: es el que ya está en el código. */
    if (s.fuelMove !== 'none') {
      const moved = list[0];              // la del movimiento, con su propio sujeto
      const down = s.fuelMove === 'drop';
      out.push({
        kind: 'fuel', lens: 'fuel', count: list.length, index: moved.i,
        eyebrow: down ? 'Bajó hoy cerca de ti' : 'Subió hoy cerca de ti',
        item: { name: moved.name, street: 'Calle de Embajadores',
                far: farText(moved.m), price: priceText(moved.price) },
        standout: standoutBadge(so, moved.id),
        move: { down, text: down ? '−4 cts hoy' : '+3 cts hoy' },
        stamp: 'medido ayer y hoy por la fuente',
        why: down
          ? 'Un precio que BAJÓ hoy a tu alrededor: free, radio 1 km, y es lo que mueve a desviarse.'
          : 'También sube: callar las subidas sería contar sólo la mitad.',
      });
      return out;
    }
    // ⛔ El sujeto de la tarjeta es **la que la función pura dice que es la más
    // barata**, no una escrita a mano. Si la regla cambia, la tarjeta cambia con
    // ella — que es lo que impide que la tarjeta y su lista discrepen.
    const pick = list.find((x) => so.cheapest.has(x.id)) || list[0];
    out.push({
      kind: 'fuel', lens: 'fuel', count: list.length, index: pick.i,
      eyebrow: `${DEMO.product} más barata`,
      item: { name: pick.name, street: 'Calle de Méndez Álvaro',
              far: farText(pick.m), price: priceText(pick.price) },
      standout: standoutBadge(so, pick.id),
      stamp: 'dato de las 09:00',
      why: 'Hay precio para tu producto y su hora es admisible.',
    });
  }

  return out;
}

/* ⛔ **Por qué una PLAZA no lleva «la mejor», y es la negativa más importante de
   todas.** «La más barata» y «la más cercana» son comprobables y no tienen coste:
   la gasolinera no se agota. Una plaza sí. Señalar «la mejor plaza» entre cuatro
   manda a todo el mundo a la misma, y la app **ya cuenta** cuánta gente va de
   camino a una (`Spot.enRouteCount`): convertiría esa cifra en una carrera que
   nosotros habríamos empezado. Lo que una plaza lleva es su **frescura** (rampa de
   color) y su **edad**, que son hechos sobre ella y no un veredicto comparativo.

   Por eso el standout es de los COMPLEMENTOS, no del producto. */

/** Por qué NO hay sección, dicho con el motivo de cada sujeto. Sólo para el
 *  panel del prototipo: en la app esto no se le cuenta a nadie. */
function notablesWhyEmpty(s) {
  const r = [];
  if (s.spots === 0) r.push('ninguna plaza de la comunidad');
  if (s.parkings === 0) r.push('ningún parking público');
  if (s.fuel === 'none') r.push('ninguna gasolinera');
  else if (s.fuel === 'noprices') r.push('gasolineras sin precio (la fuente no lo publica aquí)');
  else if (s.fuelAge === 'stale') r.push('el precio más barato es demasiado viejo para afirmarlo');
  return r;
}

/* ══════════════════════════════════════════════════════════════════════════════
   7 · Cuál de una lista merece que se la señale — los STANDOUTS

   ⛔ **Esto NO lo inventé yo: lo copié.** Existe en la app desde
   `FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001` (`domain/fuel/FuelStandouts.kt`),
   el user ya lo revisó dos veces, y mi prototipo se lo había dejado fuera. Lo que
   sigue reproduce la función pura y, sobre todo, **lo que se NIEGA a afirmar**.
   ═════════════════════════════════════════════════════════════════════════════ */

/**
 * @param items filas con `{ id, m, price }` — `price` null = sin precio.
 * @param currencies monedas distintas presentes en la lista (para el caso frontera).
 * @returns `{ cheapest: Set<id>, nearest: id|null }`
 *
 * Lo que se niega a afirmar, y cada negativa tiene su motivo medido:
 *
 * · **Nada con un solo candidato.** Con una gasolinera no hay «la más barata»: hay
 *   una gasolinera. Un superlativo sobre uno es ruido.
 * · **Un empate COMPARTE el badge, no lo mata.** La primera versión exigía ser
 *   estrictamente la más barata, y en un mapa real eso borraba el badge casi
 *   siempre — los surtidores se copian el precio a la milésima. La verdad
 *   comprobable en el surtidor es que **las dos son las más baratas**.
 * · **Lo que sí se calla: el empate UNIVERSAL.** Si todas cobran lo mismo, «la más
 *   barata» no distingue nada: es ruido en todas las filas, no información.
 * · **Sin «más barata» entre monedas distintas.** 1,42 £/l no es más barato que
 *   1,65 €/l porque el número sea menor. La más cercana sí aguanta: los metros son
 *   metros.
 * · **La más cercana es ÚNICA.** Las distancias no empatan en la práctica (metros de
 *   un fix); si empataran, ninguna es la más cercana y el badge se cae.
 */
function standouts(items, currencies = 1) {
  if (items.length < 2) return { cheapest: new Set(), nearest: null };

  const priced = items.filter((x) => x.price != null || x.rate != null);
  const valueOf = (x) => (x.price != null ? x.price : x.rate);
  const amounts = priced.map(valueOf);
  const distinct = new Set(amounts);

  let cheapest = new Set();
  if (currencies === 1 && priced.length >= 2 && distinct.size >= 2) {
    const min = Math.min(...amounts);
    priced.forEach((x) => { if (valueOf(x) === min) cheapest.add(x.id); });
  }

  // La más cercana se mide sobre TODAS, no sólo las que tienen precio: en un país
  // cuyo feed no publica ninguno — que es la mayoría del mundo, y la razón por la
  // que el user pidió esto — la realmente más cercana queda al fondo de la lista.
  // El badge nombra lo que el ORDEN no puede decir.
  const minM = Math.min(...items.map((x) => x.m));
  const closest = items.filter((x) => x.m === minM);
  const nearest = closest.length === 1 ? closest[0].id : null;

  return { cheapest, nearest };
}

/** Qué lleva una fila, o null. **Un badge por fila, nunca dos**: una fila es un
 *  nombre, no un marcador. */
function standoutBadge(so, id) {
  const isCheap = so.cheapest.has(id);
  const isNear = so.nearest === id;
  // ⛔ La combinada dice SÓLO «MÁS BARATA», y no es un recorte por espacio: la que es
  // las dos cosas es por construcción la PRIMERA de la lista — la más barata se
  // adelanta — y sus metros están escritos dos palabras más allá. La afirmación
  // fuerte, la que no se deduce mirando, es el precio.
  if (isCheap) return { label: 'MÁS BARATA', claim: 'price' };
  if (isNear) return { label: 'MÁS CERCANA', claim: 'distance' };
  return null;
}

/** El orden: **sólo la más barata sube**; el resto no se mueve.
 *  ⛔ La más CERCANA no sube, y es deliberado: la lista pone las que tienen precio
 *  primero, y la más cercana es muy a menudo una sin precio — subirla dejaría la
 *  lista encabezada por una fila sin cifra, que es lo que ese orden evita. */
function standoutRank(so, id) { return so.cheapest.has(id) ? 0 : 1; }

/** Metros → texto, con el corte a km de la app. */
function farText(m) {
  return m >= 1000 ? (m / 1000).toFixed(1).replace('.', ',') + ' km' : m + ' m';
}
/** Precio → texto con coma decimal. */
function priceText(v) { return v.toFixed(3).replace('.', ','); }
