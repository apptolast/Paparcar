/* ============================================================================
   Paparcar — el ARMAZÓN común: mapa, cromo flotante, hoja y navegación.

   v2 (27-10) — incorpora las siete decisiones de la revisión del user. Lo que
   cada alternativa aporta sigue siendo sólo DOS funciones:
     · `peekMine(s, ui)`   lo que la cabecera dice cuando hablas de LO TUYO
     · `bodyBlocks(s, ui)` lo que hay en el cuerpo ENCIMA de las pestañas
   Todo lo demás —cabecera del carril, peek de detalle, pestañas, carriles— es
   COMPARTIDO, porque son las decisiones que el user quiere iguales en los tres.
   ============================================================================ */

const ui = {
  lens: 'spots',
  /** El sujeto abierto en la hoja de detalle, o null. [P1 · P7 de la revisión] */
  detail: null,
  /** Qué sujeto describe la cabecera: 'mine' (tu coche y lo de aquí) o 'lane'
   *  (el carril que estás leyendo). Lo decide el SCROLL, no un latch. [P2] */
  headerMode: 'mine',
};

function bootPrototype(spec) {
  document.title = `Paparcar · ${spec.title}`;

  document.body.innerHTML = `
    <main class="stage">
      <section class="phone" aria-label="Prototipo de la pantalla principal de Paparcar">
        <div class="map" id="map"></div>
        <p class="demotag">Contenido de demostración</p>

        <div class="statusbar">
          <span>9:41</span>
          <span class="glyphs">${icon('bolt', 13)}<span>100%</span></span>
        </div>

        <div class="map-chrome" id="chrome">
          <div class="searchbar">
            ${icon('search', 20)}
            <span class="t-subtitle grow">Buscar una calle o un sitio</span>
            ${icon('layers', 20)}
          </div>
          <div class="zonechips" id="zonechips"></div>
        </div>

        <button class="locpill" id="locpill" type="button"></button>

        <div class="map-fabs" id="fabs">
          <button class="fab" type="button" id="fabreport"
                  aria-label="Avisar de una plaza libre">${icon('plus', 22)}</button>
          <button class="fab" type="button" id="fabloc"
                  aria-label="Centrar en mi ubicación">${icon('mylocation', 20)}</button>
        </div>

        <p class="prototoast" id="toast" role="status" aria-live="polite" hidden></p>

        <section class="sheet" id="sheet" aria-label="Panel de Paparcar">
          <div class="handle" id="handle"></div>
          <div class="sheet-body" id="body"></div>
        </section>

        <nav class="bottomnav" aria-label="Navegación principal">
          <button class="navitem" type="button" aria-current="page">${icon('home', 22)}<span>Inicio</span></button>
          <button class="navitem" type="button">${icon('car', 22)}<span>Vehículos</span></button>
          <button class="navitem" type="button">${icon('settings', 22)}<span>Ajustes</span></button>
        </nav>
      </section>

      <aside class="panel" id="panel" aria-label="Controles del prototipo"></aside>
    </main>`;

  const phone = document.querySelector('.phone');
  const handle = document.getElementById('handle');
  const body = document.getElementById('body');
  const sheetCtl = mountSheet(phone, { peek: spec.peekHeight || 88 });

  buildPanel(document.getElementById('panel'), spec);

  /* ── Avisos del prototipo ───────────────────────────────────────────────── */
  const toast = document.getElementById('toast');
  let toastTimer = null;
  function say(msg) {
    toast.textContent = msg;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 2400);
  }

  /* ── La cabecera sigue AL SCROLL ────────────────────────────────────────────
     [P2] «quizás cuando tengamos el scroll con el foco en la lista es cuando
     debería aparecer la info del sheet». Exacto: la cabecera describe lo que
     se está LEYENDO, y es reversible — al volver arriba vuelve tu coche. Por
     eso es predecible: nunca habla de algo que no esté debajo. */
  function resolveHeaderMode() {
    if (ui.detail) return 'detail';
    if (scn.laneHeader === 'never') return 'mine';
    // Una noticia del coche gana siempre: un viaje vivo, una pregunta con plazo
    // o una vigilancia muerta no se tapan con el índice de lo que hay cerca.
    if (carNewsOutranks(scn)) return 'mine';
    if (scn.laneHeader === 'always') return 'lane';
    const tabs = body.querySelector('.tabs');
    if (!tabs) return 'mine';
    return body.scrollTop >= tabs.offsetTop - 4 ? 'lane' : 'mine';
  }

  let rafPending = false;
  body.addEventListener('scroll', () => {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => {
      rafPending = false;
      const next = resolveHeaderMode();
      if (next !== ui.headerMode) { ui.headerMode = next; paintHandle(); }
    });
  });

  /* ── Controles del cromo del mapa ───────────────────────────────────────── */
  document.getElementById('locpill').addEventListener('click', () => {
    if (scn.loc === 'missing') { setScenario({ loc: 'ok' }); say('Ubicación concedida'); }
    else say('Precisión del fix actual — no modelado en el prototipo');
  });
  document.getElementById('fabloc').addEventListener('click', () => {
    if (scn.loc === 'missing') { setScenario({ loc: 'ok' }); say('Ubicación concedida'); }
    else say('Mapa centrado en tu posición');
  });
  document.getElementById('fabreport').addEventListener('click', () => {
    ui.lens = 'spots'; ui.detail = null; setScenario({}); sheetCtl.goTo('expanded');
    say('Avisar de una plaza — la acción vive al final del carril de plazas');
  });
  document.getElementById('chrome').addEventListener('click', (e) => {
    if (e.target.closest('.searchbar')) say('Buscador — no modelado en el prototipo');
    else if (e.target.closest('.chip')) say('Zonas — no modeladas en el prototipo');
  });
  document.querySelector('.bottomnav').addEventListener('click', (e) => {
    const item = e.target.closest('.navitem');
    if (item && item.getAttribute('aria-current') !== 'page') {
      say('Sólo la pantalla de Inicio entra en este prototipo');
    }
  });

  document.getElementById('detentseg').addEventListener('click', (e) => {
    const b = e.target.closest('button[data-detent]');
    if (!b) return;
    goTo(b.dataset.detent);
    document.querySelectorAll('[data-detent]').forEach((x) =>
      x.setAttribute('aria-pressed', String(x === b)));
  });

  /* ── Acciones ───────────────────────────────────────────────────────────── */
  function onClick(e) {
    const open = e.target.closest('[data-open]');
    if (open) {
      // [P1] El CUERPO de una tarjeta abre ESE item: cámara + hoja de detalle,
      // exactamente el mismo efecto que pulsar su fila en la lista.
      ui.detail = { kind: open.dataset.open, index: Number(open.dataset.i || 0) };
      ui.lens = open.dataset.lens || ui.lens;
      ui.headerMode = 'detail';
      setScenario({});
      goTo('peek');
      say('Mismo efecto que pulsar su fila: cámara al punto y hoja de detalle');
      return;
    }
    const more = e.target.closest('[data-list]');
    if (more) {
      // [P1] El PIE de la tarjeta abre SU LISTA. Dos destinos, dos controles.
      ui.detail = null; ui.lens = more.dataset.list;
      setScenario({});
      goTo('expanded');
      scrollToLane();
      return;
    }
    const tab = e.target.closest('[data-lens]');
    if (tab) { ui.lens = tab.dataset.lens; ui.detail = null; setScenario({}); return; }
    const act = e.target.closest('[data-act]');
    if (act) { handleAction(act.dataset.act, act.dataset.arg); }
  }
  body.addEventListener('click', onClick);
  handle.addEventListener('click', (e) => {
    if (e.target.closest('[data-open],[data-list],[data-lens],[data-act]')) {
      e.stopPropagation();
      onClick(e);
    }
  });

  function handleAction(act, arg) {
    switch (act) {
      case 'expand':   goTo('expanded'); break;
      case 'peek':     goTo('peek'); break;
      case 'closedetail': ui.detail = null; ui.headerMode = resolveHeaderMode(); setScenario({}); break;
      // El chip de aviso es la PUERTA de la fila de acción: cierra el detalle si
      // hay uno, despliega la hoja y deja la fila a la vista con su remedio.
      case 'showwatch': ui.detail = null; setScenario({}); goTo('expanded'); break;
      case 'lane':     ui.lens = arg; ui.detail = null; setScenario({}); goTo('expanded'); scrollToLane(); break;
      case 'mark':     setScenario({ car: 'parked', ask: 'none' }); goTo('peek'); break;
      case 'release':  setScenario({ car: 'unmarked', spots: Math.max(scn.spots, 4) }); break;
      case 'stop':     setScenario({ car: 'unmarked' }); break;
      case 'answered': setScenario({ ask: 'none', car: 'parked' }); break;
      case 'driving':  setScenario({ car: 'driving', ask: 'none' }); break;
      case 'fixwatch': setScenario({ watch: 'ok' }); break;
      case 'grantcore':setScenario({ perms: 'ok' }); break;
      case 'skiponb':  setScenario({ onboarding: false }); break;
      case 'nav':      say('Navegación externa — no modelada en el prototipo'); break;
      default: break;
    }
  }

  function scrollToLane() {
    const tabs = body.querySelector('.tabs');
    if (tabs) body.scrollTop = tabs.offsetTop;
    ui.headerMode = resolveHeaderMode();
    paintHandle();
  }

  /* ── Plegar: qué hace la cabecera con el carril leído ──────────────────────
     [P2] Tres políticas, y el prototipo deja compararlas. La recomendada es
     `reset`: plegar es un gesto de «ya está», así que la hoja vuelve a su
     inicio y el peek recupera tu coche. Nunca queda enseñando un sujeto que al
     reabrir no esté arriba. */
  function goTo(name) {
    if (name === 'peek' && !ui.detail) {
      switch (scn.latch) {
        // RECOMENDADA · plegar es un gesto de «ya está»: la hoja vuelve a su
        // inicio, el peek recupera tu coche, y al reabrir empiezas arriba. El
        // peek nunca enseña un sujeto que al desplegar no esté en pantalla.
        case 'reset':
          body.scrollTop = 0;
          ui.headerMode = resolveHeaderMode();
          break;
        // Lo que hace la app HOY (`laneHeaderLatched`): el peek mantiene el
        // sujeto que estabas leyendo, y al reabrir sigues ahí. Coherente, pero
        // en B el peek pierde «Aquí y ahora» hasta que vuelvas a subir.
        case 'keep':
          ui.headerMode = resolveHeaderMode();
          break;
        // ⚠️ INCOHERENTE a propósito, para poder verlo: el peek vuelve al coche
        // pero el scroll se queda dentro del carril, así que al desplegar la
        // cabecera hablaba de algo que no está arriba.
        case 'car':
        default:
          ui.headerMode = 'mine';
          break;
      }
      paintHandle();
    }
    sheetCtl.goTo(name);
  }

  /* ── Pintado ────────────────────────────────────────────────────────────── */
  function paintHandle() {
    const s = scn;
    handle.innerHTML = ui.headerMode === 'detail' ? detailPeek(s, ui)
      : ui.headerMode === 'lane' ? lanePeek(s, ui)
      : spec.peekMine(s, ui);
    // Las pestañas del cuerpo se quedan en su sitio pero INVISIBLES cuando la
    // cabecera ya las lleva: así el alto del cuerpo no cambia y nada salta.
    const bodyTabs = body.querySelector('.tabs');
    if (bodyTabs) bodyTabs.style.visibility = ui.headerMode === 'lane' ? 'hidden' : 'visible';
    sheetCtl.relayout(false);
  }

  function render() {
    const s = scn;
    document.getElementById('map').innerHTML = mapSvg(s, ui.lens, ui.detail);

    document.getElementById('zonechips').innerHTML = `
      <button class="chip" type="button" aria-pressed="false">${icon('home', 16)} Casa</button>
      <button class="chip" type="button" aria-pressed="false">${icon('place', 16)} Trabajo</button>
      <button class="chip" type="button">${icon('plus', 16)} Zona</button>`;

    const pill = document.getElementById('locpill');
    pill.dataset.warn = String(s.loc === 'missing');
    pill.innerHTML = s.loc === 'missing'
      ? `${icon('warn', 14)} Sin ubicación · activar`
      : `${icon('mylocation', 14)} Precisión ±8 m`;

    const keep = body.scrollTop;
    body.innerHTML = ui.detail ? '' : sharedBody(s, ui, spec);
    body.scrollTop = ui.detail ? 0 : keep;
    ui.headerMode = resolveHeaderMode();
    paintHandle();
  }

  onScenario(render);

  /* El auto-abrir. En la app esto ocurre UNA vez por arranque en frío; aquí se
     dispara además al cambiar el eje del panel, para poder verlo sin recargar.
     ⛔ La regla vive en `autoOpenAnchor()`, que es pura y testeable; esto sólo
     la obedece. */
  let lastLaunch = null;
  onScenario((s) => {
    if (s.launch === lastLaunch) return;
    lastLaunch = s.launch;
    const anchor = autoOpenAnchor(s);
    if (anchor) {
      sheetCtl.goTo(anchor);
      say('Hay novedad cerca: la hoja se abre a media altura, no entera');
    } else if (s.launch === 'news') {
      // Dicho en voz alta porque es la mitad interesante de la regla: cuándo NO.
      say(carNewsOutranks(s)
        ? 'Hay novedad, pero tu coche manda: la hoja no se abre sola'
        : 'Hay novedad, pero nada que mostrar: la hoja no se abre sola');
    }
  });

  window.addEventListener('resize', () => sheetCtl.relayout(false));
}

/* ════════════════════════════════════════════════════════════════════════════
   Piezas COMPARTIDAS — las decisiones que el user quiere iguales en los tres
   ════════════════════════════════════════════════════════════════════════════ */

/** ¿Hay una noticia del coche que gane al índice de lo que hay cerca? */
function carNewsOutranks(s) {
  return s.perms === 'blocked' || s.ask === 'pending'
    || s.watch === 'interrupted' || s.car === 'driving';
}

/* ── ¿Se abre la hoja SOLA al arrancar? ────────────────────────────────────────
   Idea del user (28-10): *«si tenemos cosas interesantes que mostrar en Aquí y
   ahora, al abrir la app, abrir modal para mostrar ese contenido»*.

   ⛔ **El disparador no es «hay contenido», es «hay NOVEDAD».** La diferencia es
   la que decide si esto es útil o es una app que grita: con «hay contenido» se
   abriría casi siempre —hay parkings en todas partes— y taparía el mapa cada
   vez. Con «hay algo que NO estaba la última vez que miraste», abrirse se gana
   el sitio: estás viendo algo que no sabías.

   ⚠️ **Y eso exige una marca de VISTO persistida** (qué novedad ya se le enseñó).
   Sin ella se reabre en cada arranque con la misma noticia, que es peor que no
   abrirse. Es la única pieza de esta idea que necesita un campo nuevo. */

function autoOpenAnchor(s) {
  // Sin novedad no se abre. Un estado conocido no justifica tapar el mapa.
  if (s.launch !== 'news') return null;
  // ⛔ Nada de esto por encima de una avería o una pregunta con plazo: esas ya
  // mandan en la cabecera, y abrir la hoja encima las esconde tras su propio
  // contenido. Cubre también el viaje vivo — conduciendo la hoja NUNCA se abre
  // sola, que es lo único de mi propuesta anterior que el user no contradijo.
  if (carNewsOutranks(s)) return null;
  // Y si no hay nada que mostrar, no hay nada que abrir.
  if (!notables(s).length) return null;
  // ⛔ A MEDIA hoja, nunca desplegada: el mapa conserva su mitad. Abrirse al 58 %
  // al arrancar es lo que hace que una app se sienta como un folleto.
  return 'half';
}

/** La identidad de un coche: su MÉTODO de vigilancia, nunca su estado. */
function identityOf(v, s) {
  if (v.watch === 'bt') return 'bt';
  if (s.perms === 'blocked' || s.watch === 'interrupted') return 'off';
  return 'active';
}
function identityColour(id) {
  return id === 'bt' ? 'var(--car-blue)' : id === 'off' ? 'var(--on-muted)' : 'var(--green)';
}

/* ⛔ Aquí había un `watchBadge()` que devolvía CINCO estados, dos de ellos el
   TIER con su nombre: «Bluetooth» y «Vigilando tu sitio». Borrado, no
   desactivado, porque eran dos cosas distintas mal juntadas:

   · el **método** (asistida / Bluetooth / sin vigilar) ya lo dicen el glifo de
     vigilancia y el color de identidad — `UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`
     dice que **ninguna superficie lo imprime como texto**;
   · la **salud** es lo único que merece palabras, y sólo cuando falla.

   Lo que queda es `watchAlert()`, más abajo. Un resolver que devolvía el tier
   como etiqueta era la puerta por la que esa doctrina se habría perdido. */

/* ── El badge de vigilancia: NO existe cuando todo va bien ──────────────
   Dos doctrinas que YA existen en la app y que mi primera versión violaba:

   1. `UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001` — el MÉTODO de vigilancia
      (asistida / Bluetooth / sin vigilar) **es un glifo y un color, nunca una
      etiqueta**. Las tres cadenas sobreviven sólo como `contentDescription`.
      Mi badge imprimía «Bluetooth»: eso es etiquetar el tier. Fuera.

   2. La SALUD de la vigilancia no es un chip en la app: es una **fila de acción**
      (`HomeDetectionSurface`) con causa, consecuencia y remedio — «Tu móvil la
      puso en pausa. Seguimos comprobando en segundo plano, pero tu salida puede
      detectarse tarde» + «Reactivar». Un chip no cabe el remedio.

   De ahí la regla, que es MÁS SIMPLE que la que tenía:

   ⛔ **El badge de vigilancia SÓLO EXISTE cuando algo no se cumple.**
   Sano = no hay badge. El método ya lo dicen el glifo y el color de identidad, y
   el estado ya está escrito en palabras. Es `carCardSaysTheWatch` llevado al
   peek: la fila calmada se aparta porque la cabecera ya dice lo mismo.

   Y cuando avisa, el chip es la **puerta** de esa fila, no su sustituto: se ve en
   reposo — que es lo que faltaba — y al pulsarlo se despliega la hoja sobre la
   fila que lleva el texto entero y el botón que lo arregla. */

/** El aviso de vigilancia, o **null** cuando no hay nada que avisar.
 *  ⛔ Nunca devuelve el tier: eso lo dice el glifo. */
function watchAlert(s) {
  if (s.perms === 'blocked') return { tone: 'error', text: 'Falta permiso', icon: 'warn' };
  if (s.watch === 'interrupted') return { tone: 'error', text: 'Vigilancia en pausa', icon: 'warn' };
  if (s.watch === 'fragile') return { tone: 'warn', text: 'Vigilancia frágil', icon: 'warn' };
  return null;
}

/** El chip del aviso, o cadena vacía. Es la PUERTA de la fila de acción, así que
 *  lleva su propia acción: pulsarlo despliega la hoja sobre ella.
 *  ⛔ No tiene variante «compacta»: un aviso sin palabras no es un aviso. */
function badgeMarkup(s) {
  const a = watchAlert(s);
  if (!a) return '';
  return `<button class="badge" type="button" data-tone="${a.tone}" data-act="showwatch"
                  style="border:0;cursor:pointer;font:inherit"
                  aria-label="${esc(a.text)} — ver qué pasa">${icon(a.icon, 13)} ${esc(a.text)}</button>`;
}

/** Las palabras del estado del coche. Se ESCRIBEN, no se tiñen. */
function carStatus(s) {
  if (s.ask === 'pending') return { eyebrow: '¿Has aparcado?', line: 'Preguntado hace 2 min · quedan 13' };
  if (s.car === 'driving') return { eyebrow: 'En ruta', line: 'Ronda de Valencia' };
  if (s.car === 'unmarked') return { eyebrow: 'Sin aparcamiento marcado', line: 'No sabemos dónde lo has dejado' };
  return { eyebrow: 'Aparcado', line: DEMO.vehicles[0].addr };
}

/* ── La fila de DOS líneas, la misma para cualquier sujeto ──────────────────
   [P6 · P7] línea 1 = nombre · calificador · línea 2 = dónde · a cuánto.
   La cifra del sujeto va al trailing. La dirección sólo aparece en UNA línea. */

function twoLineRow(kind, item, s, opts = {}) {
  const L = subjectLines(kind, item, s);
  const tone = opts.tone || 'var(--on)';
  // El chip de standout vive DEBAJO de la cifra, en la misma columna del borde
  // derecho: una afirmación sobre un número se lee debajo de él.
  const chip = opts.standout
    ? `<span class="standout" data-claim="${opts.standout.claim}">${esc(opts.standout.label)}</span>`
    : '';
  // Un sujeto puede traer una CIFRA (precio, edad) o una COLA de texto (la tarifa
  // declarada de un parking: «Gratuito» / «De pago»). ⛔ Lo segundo no es una cifra
  // disfrazada: `FeeInfo` no tiene importe, y `UNKNOWN` se pinta como nada.
  const tail = L.tail
    ? `<span class="t-label muted" style="white-space:nowrap">${esc(L.tail)}</span>` : '';
  const fig = (L.figure || chip || tail) ? `
    <span class="trail" style="text-align:right">
      ${L.figure ? `<span class="figureblock" style="justify-content:flex-end">
        <span class="t-counter" style="color:${tone}">${esc(L.figure)}</span>
        ${L.unit ? `<span class="t-counterUnit muted">${esc(L.unit)}</span>` : ''}
      </span>` : ''}
      ${tail}${chip}
    </span>` : (opts.chevron === false ? '' : `<span class="trail">${icon('chevron', 20)}</span>`);

  return `
    <span class="tile" data-identity="${opts.identity || kind}">${icon(opts.icon || kind, 20)}</span>
    <span class="grow">
      <span class="sline1">
        <span class="name t-rowName" style="color:${opts.nameColour || 'var(--on)'}">${esc(L.name)}</span>
        ${L.qual ? `<span class="dot">·</span><span class="qual t-rowTitle">${esc(L.qual)}</span>` : ''}
      </span>
      <span class="sline2">
        ${L.where ? `<span class="where t-caption">${esc(L.where)}</span>` : ''}
        ${L.far ? `<span class="dot">·</span><span class="far t-caption">a ${esc(L.far)}</span>` : ''}
      </span>
    </span>
    ${fig}`;
}

/* ── La cabecera cuando lo que se lee es el CARRIL ──────────────────────────
   [P2] Lleva las PESTAÑAS, no una frase sobre ellas: lo que fija la cabecera
   es el control con el que el usuario eligió el sujeto, así que volver a
   «Plazas» desde el fondo de una lista no cuesta ni un scroll. */

function lanePeek(s, ui) {
  const v = DEMO.vehicles[0];
  const id = identityOf(v, s);
  const st = carStatus(s);
  return `
    <button class="row" type="button" data-act="peek"
            style="padding:0 0 10px;min-height:0" aria-label="Volver a tu coche">
      <span class="tile" data-identity="${id}" style="width:30px;height:30px">
        ${icon(v.size === 'Moto' ? 'moto' : 'car', 17)}
      </span>
      <span class="grow">
        <span class="t-caption" style="display:block;color:${identityColour(id)}">
          ${esc(v.name)} <span style="color:var(--on-muted)">· ${esc(st.eyebrow.toLowerCase())}</span>
        </span>
      </span>
      <!-- ⛔ Aquí ponía el NOMBRE del carril, y era dos errores en uno: dejaba
           sin hueco al badge de vigilancia (que así desaparecía incluso muerto)
           y repetía lo que la fila de pestañas de debajo ya dice y además marca.
           El trailing es del badge. -->
      <span class="trail">${badgeMarkup(s)}</span>
    </button>
    ${tabsRow(s, ui.lens, laneCols(ui.lens), { inHandle: true })}`;
}

function laneName(lens) {
  return lens === 'spots' ? 'Plazas' : lens === 'parkings' ? 'Parkings' : 'Gasolineras';
}
/** Las pestañas se reponderan: las plazas son el producto, las otras dos son
 *  complementos. Es la jerarquía que el KDoc de la app ya declara. */
function laneCols() { return '1.25fr 1fr 1fr'; }

/* ── El peek de DETALLE — la misma gramática para los tres sujetos ──────────
   [P7] «actualmente mostramos 3 líneas y las 2 últimas muestran dirección y a
   veces es repetitivo». Aquí son DOS: nombre · calificador, y dónde · a cuánto.
   La cifra propia del sujeto va al trailing, nunca en una tercera línea. */

function detailPeek(s, ui) {
  const d = ui.detail;
  const item = detailItem(s, d);
  if (!item) return `<p class="t-caption muted">—</p>`;
  const tones = { spot: 'var(--spot-fresh)', parking: 'var(--car-park)', fuel: 'var(--amber)' };

  // ⛔ Con un sujeto abierto la cabecera es del sujeto y el coche no sale… PERO
  // un badge que AVISA no cede en ningún modo. Si la vigilancia está frágil,
  // muerta o sin permiso, el aviso se queda aquí también: enterarse de que la
  // app dejó de vigilar tu coche no puede depender de cerrar una ficha primero.
  const alert = watchAlert(s) !== null;

  return `
    ${alert ? `<div style="padding:0 0 8px">${badgeMarkup(s)}</div>` : ''}
    <div class="row" style="padding:0 0 4px;min-height:0;cursor:default">
      ${twoLineRow(d.kind, item, s, { tone: tones[d.kind], chevron: false })}
      <button class="fab" type="button" data-act="closedetail" aria-label="Cerrar"
              style="width:34px;height:34px;flex:0 0 auto;margin-left:8px;background:none;
                     border-color:var(--divider);box-shadow:none;backdrop-filter:none">
        ${icon('close', 18)}
      </button>
    </div>
    ${detailStamp(s, d)}
    <div style="display:flex;gap:8px;flex-wrap:wrap;padding:12px 0 14px">
      <button class="pill small" type="button" data-kind="primary" data-act="nav">Cómo llegar</button>
      <button class="pill small" type="button" data-kind="quiet"
              data-act="lane" data-arg="${d.kind === 'spot' ? 'spots' : d.kind === 'parking' ? 'parkings' : 'fuel'}">
        Ver ${laneName(d.kind === 'spot' ? 'spots' : d.kind === 'parking' ? 'parkings' : 'fuel').toLowerCase()}
      </button>
    </div>`;
}

function detailItem(s, d) {
  if (d.kind === 'spot') {
    const sp = DEMO.spots[d.index] || DEMO.spots[0];
    return { place: sp.place, size: sp.meta.split(' · ')[0], far: sp.meta.split(' · ')[1],
             age: sp.age, heat: sp.heat };
  }
  if (d.kind === 'parking') {
    const p = DEMO.parkings[d.index] || DEMO.parkings[0];
    return { name: p.name, far: farText(p.m), fee: p.fee,
             fitLabel: s.clearance === 'known' ? p.fit : 'Gálibo sin dato' };
  }
  const f = DEMO.fuel[d.index] || DEMO.fuel[0];
  return { name: f.name, street: 'Calle de Méndez Álvaro', far: farText(f.m),
           price: priceText(f.price) };
}

/** La línea de procedencia. Un dato de fuente externa dice CUÁNDO se midió, o
 *  no se afirma. Un sujeto propio (una plaza) no tiene fuente que citar. */
function detailStamp(s, d) {
  if (d.kind === 'fuel') {
    return `<p class="t-caption muted" style="margin:0;padding:2px 0 0">${
      s.fuel === 'prices'
        ? (s.fuelAge === 'fresh'
            ? 'Precio publicado por la fuente a las 09:00'
            : '⚠ El último precio publicado aquí es de hace 13 días')
        : 'Esta fuente no publica precios aquí'
    }</p>`;
  }
  if (d.kind === 'parking') {
    return `<p class="t-caption muted" style="margin:0;padding:2px 0 0">${
      s.clearance === 'known' ? 'Gálibo 2,10 m · publicado por el municipio'
                              : 'El municipio no publica el gálibo de este parking'
    }</p>`;
  }
  return `<p class="t-caption muted" style="margin:0;padding:2px 0 0">
    Avisada por un conductor al irse · caduca en 11 min</p>`;
}

/* ── El cuerpo: bloques propios de cada alternativa + pestañas + carril ───── */

function sharedBody(s, ui, spec) {
  let out = spec.bodyBlocks(s, ui);
  out += tabsRow(s, ui.lens, laneCols(ui.lens));
  out += laneContent(s, ui.lens);
  out += `<div style="height:24px"></div>`;
  return out;
}

/** Cabecera de PROVINCIA — se la gana quien NOMBRA algo que el bloque no dice. */
function provinceHead(title, side) {
  return `
    <div class="provincehead" style="padding:16px 16px 10px">
      <span class="t-sectionHeader">${esc(title)}</span>
      ${side ? `<span class="side t-caption">${esc(side)}</span>` : ''}
    </div>`;
}

/* ── Las pestañas de sujeto ─────────────────────────────────────────────────*/

function tabsRow(s, active, cols, opts = {}) {
  const t = (key, label, ic, count) => `
    <button class="tab" type="button" role="tab" data-lens="${key}"
            aria-selected="${active === key}">
      <span class="tabtop">
        ${count !== null ? `<span class="count">${count}</span>` : icon(ic, 18)}
        <span class="t-label">${label}</span>
      </span>
      <span class="ind"></span>
    </button>`;

  // ⛔ Un contador sólo existe donde se sabe. Las plazas son suscripción viva;
  // los complementos no saben su cifra hasta que se entra en su carril.
  const fuelN = (active === 'fuel' && s.fuel !== 'none') ? DEMO.fuel.length : null;
  const parkN = (active === 'parkings' && s.parkings > 0) ? s.parkings : null;

  return `
    <div class="tabs" role="tablist" style="grid-template-columns:${cols}${
      opts.inHandle ? ';position:static;background:none' : ''}">
      ${t('spots', 'Plazas', 'spot', s.spots > 0 ? s.spots : null)}
      ${t('parkings', 'Parkings', 'parking', parkN)}
      ${t('fuel', 'Gasolineras', 'fuel', fuelN)}
    </div>
    <hr class="divider">`;
}

/* ── Los carriles ───────────────────────────────────────────────────────────*/

function laneContent(s, lens) {
  if (lens === 'spots') return spotsLane(s);
  if (lens === 'parkings') return parkingsLane(s);
  return fuelLane(s);
}

function spotsLane(s) {
  if (s.loc === 'missing' && s.spots === 0) {
    return `<p class="emptyline t-caption">Busca una calle o activa la ubicación para ver las
      plazas libres de ahí.</p>${reportCard(s)}`;
  }
  if (s.spots === 0) {
    return `
      <div class="emptyline">
        <svg class="art" width="92" height="56" viewBox="0 0 92 56" aria-hidden="true">
          <rect x="4" y="30" width="84" height="2" rx="1" fill="currentColor" opacity=".35"/>
          <rect x="10" y="12" width="26" height="18" rx="4" fill="none"
                stroke="currentColor" stroke-width="2" stroke-dasharray="4 4" opacity=".55"/>
          <rect x="44" y="12" width="26" height="18" rx="4" fill="none"
                stroke="currentColor" stroke-width="2" stroke-dasharray="4 4" opacity=".55"/>
          <circle cx="23" cy="44" r="3" fill="currentColor" opacity=".3"/>
          <circle cx="57" cy="44" r="3" fill="currentColor" opacity=".3"/>
        </svg>
        <span class="t-caption">Aún no hay plazas reportadas aquí</span>
      </div>
      ${s.parkings > 0 ? altCard(s) : ''}
      ${reportCard(s)}`;
  }
  return rows(DEMO.spots.slice(0, s.spots), (sp, i) => `
    <button class="row" type="button" data-open="spot" data-i="${i}" data-lens="spots">
      ${twoLineRow('spot', { place: sp.place, size: sp.meta.split(' · ')[0],
                             far: sp.meta.split(' · ')[1], age: sp.age, heat: sp.heat }, s,
                   { tone: `var(--spot-${sp.heat})` })}
    </button>`) + reportCard(s);
}

/** La alternativa útil cuando el carril del producto está vacío. UNA, nombrada
 *  por lo que es, y sólo si se puede ofrecer. */
function altCard(s) {
  return `
    <div style="padding:4px 16px 0">
      <button class="row card" type="button" data-list="parkings" style="border-style:dashed">
        <span class="tile" data-identity="park">${icon('parking', 20)}</span>
        <span class="grow">
          <span class="t-rowTitle" style="display:block">Mira los parkings públicos de aquí</span>
          <span class="t-caption muted">${s.parkings} en esta zona, el más cercano a 240 m</span>
        </span>
        <span class="trail">${icon('chevron', 20)}</span>
      </button>
    </div>`;
}

function reportCard(s) {
  const can = s.loc === 'ok';
  return `
    <div style="padding:12px 16px 4px">
      <div class="card" style="padding:14px 16px;display:flex;gap:12px;align-items:center">
        <span class="tile" data-identity="active">${icon('plus', 20)}</span>
        <span class="grow">
          <span class="t-rowTitle" style="display:block">Avisar de una plaza libre</span>
          <span class="t-caption muted">${can
            ? 'Cuando te vayas, tu aparcamiento libera una plaza para otros.'
            : 'Necesitamos tu ubicación para saber que estabas allí.'}</span>
        </span>
        <button class="pill small" type="button" data-kind="${can ? 'secondary' : 'quiet'}"
                ${can ? '' : 'disabled'} ${can ? 'data-act="peek"' : ''}>Avisar</button>
      </div>
    </div>`;
}

function parkingsLane(s) {
  if (s.parkings === 0) {
    return `<p class="emptyline t-caption">Ningún parking público en esta zona.</p>`;
  }
  // Los parkings llevan standout por decisión del user (28-10), con las MISMAS
  // negativas que las gasolineras. ⛔ Y la que más manda aquí: con la tarifa sin
  // publicar — que es lo normal — **no hay «más barato» que decir**, y queda sólo
  // «MÁS CERCANO». Esa negativa es la que lo hace honesto en la mayoría de zonas.
  // ⛔ Sin standout y sin reordenar: la lista ya viene **sólo por distancia**, así que
  // un chip «MÁS CERCANO» rotularía lo que la primera posición ya dice.
  const list = DEMO.parkings.slice(0, s.parkings).map((p, i) => ({ ...p, i }));
  return rows(list, (p) => `
    <button class="row" type="button" data-open="parking" data-i="${p.i}" data-lens="parkings">
      ${twoLineRow('parking', { name: p.name, far: farText(p.m), fee: p.fee,
                                fitLabel: s.clearance === 'known' ? p.fit : 'Gálibo sin dato' }, s,
                   { tone: 'var(--car-park)', identity: 'park' })}
    </button>`);
}

function fuelLane(s) {
  if (s.fuel === 'none') {
    return `<p class="emptyline t-caption">Ninguna gasolinera a menos de 5 km.</p>`;
  }
  // ⛔ El standout se CALCULA sobre la respuesta, con las negativas de la app.
  // Y sólo la más barata sube: la más cercana se queda donde esté.
  const items = DEMO.fuel.map((f, i) => ({ ...f, i,
    price: s.fuel === 'prices' ? f.price : null }));
  const so = standouts(items);
  const ordered = items.slice().sort((a, b) => standoutRank(so, a.id) - standoutRank(so, b.id));
  const list = rows(ordered, (f) => `
    <button class="row" type="button" data-open="fuel" data-i="${f.i}" data-lens="fuel">
      ${twoLineRow('fuel', { name: f.name, street: 'Calle de Méndez Álvaro',
                             far: farText(f.m),
                             price: f.price != null ? priceText(f.price) : null }, s,
                   { tone: 'var(--amber)', standout: standoutBadge(so, f.id) })}
    </button>`);

  // ⛔ Tres sentencias distintas, nunca una degradación de otra.
  const note = s.fuel === 'prices'
    ? (s.fuelAge === 'fresh'
        ? `Precios publicados por la fuente a las 09:00 · cotizado para ${DEMO.product}`
        : `El precio más reciente que publica esta fuente aquí es de hace 13 días`)
    : `Esta fuente no publica precios aquí. Las gasolineras sí están.`;
  return list + `<p class="emptyline t-caption" style="padding:12px 16px">${esc(note)}</p>`;
}

/* ── La tira de coches ──────────────────────────────────────────────────────*/

function vehiclesStrip(s) {
  const list = DEMO.vehicles.slice(0, s.cars);
  return `
    <div style="display:flex;gap:10px;overflow-x:auto;padding:0 16px 4px;scrollbar-width:none">
      ${list.map((v, i) => {
        const id = identityOf(v, s);
        const colour = identityColour(id);
        const isDriving = i === 0 && s.car === 'driving';
        const st = i === 0 ? carStatus(s) : { eyebrow: 'Aparcado', line: v.addr };
        return `
        <button class="card" type="button" data-act="expand"
                style="flex:0 0 auto;width:200px;padding:12px;text-align:left;
                       border-color:color-mix(in srgb, ${colour} 45%, transparent);
                       border-width:${isDriving ? '2px' : '1px'};background:var(--high);cursor:pointer">
          <span style="display:flex;gap:10px;align-items:center;margin-bottom:10px">
            <span class="tile" data-identity="${id}">${icon(v.size === 'Moto' ? 'moto' : 'car', 20)}</span>
            <span style="min-width:0">
              <span class="t-rowName" style="display:block;color:${colour}">${esc(v.name)}</span>
              <span class="t-meta muted">${esc(v.size)}</span>
            </span>
          </span>
          <span style="display:flex;gap:8px;align-items:flex-start;color:var(--on-muted)">
            ${icon(isDriving ? 'route' : (st.eyebrow === 'Sin aparcamiento marcado' ? 'warn' : 'place'), 16)}
            <span style="min-width:0">
              <span class="t-caption" style="display:block;color:var(--on)">${esc(st.eyebrow)}</span>
              <span class="t-caption muted">${esc(st.line)}</span>
            </span>
          </span>
          ${isDriving
            ? `<span class="pill small block" data-kind="quiet" data-act="stop"
                     style="margin-top:10px">Dejar de seguir</span>`
            : (st.eyebrow === 'Sin aparcamiento marcado'
              ? `<span class="pill small block" data-kind="secondary" data-act="mark"
                       style="margin-top:10px">Marcar aparcamiento</span>` : '')}
        </button>`;
      }).join('')}
    </div>`;
}

/* ── Primeros pasos — ahora CON su cabecera de provincia [P3] ───────────────*/

function firstStepsBlock(done, total) {
  return provinceHead('Primeros pasos', `${done} de ${total}`) + `
    <div style="padding:0 16px">
      <div class="card" style="padding:12px 16px;display:flex;gap:12px;align-items:center">
        <span class="tile" data-identity="active">${icon('shield', 20)}</span>
        <span class="grow">
          <span class="t-rowTitle" style="display:block">Marca dónde has dejado el coche</span>
          <span class="t-caption muted">Es lo que nos deja avisarte cuando te vayas</span>
          <span style="display:block;height:4px;border-radius:2px;margin-top:8px;
                       background:color-mix(in srgb, var(--on) 12%, transparent)">
            <span style="display:block;height:4px;border-radius:2px;width:${(done / total) * 100}%;
                         background:var(--green)"></span>
          </span>
        </span>
        <button class="pill small" type="button" data-kind="quiet" data-act="expand">Ver</button>
        <button class="pill small" type="button" data-kind="quiet" data-act="skiponb"
                aria-label="Cerrar los primeros pasos">${icon('close', 16)}</button>
      </div>
    </div>`;
}

/* ── Una fila que PIDE algo ─────────────────────────────────────────────────*/

function actionRow({ tone = 'ok', ic = 'warn', title, sub, cta, act, cta2, act2 }) {
  return `
    <div style="padding:12px 16px 0">
      <div class="actionrow" data-tone="${tone}">
        <span class="tile" data-identity="${tone === 'error' ? 'off' : 'active'}">${icon(ic, 20)}</span>
        <span class="grow">
          <span class="t-rowTitle" style="display:block">${esc(title)}</span>
          <span class="t-caption muted">${esc(sub)}</span>
          ${cta ? `<span style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap">
            <button class="pill small" type="button" data-kind="primary" data-act="${act}">${esc(cta)}</button>
            ${cta2 ? `<button class="pill small" type="button" data-kind="quiet" data-act="${act2}">${esc(cta2)}</button>` : ''}
          </span>` : ''}
        </span>
      </div>
    </div>`;
}

/** La salud del SISTEMA — repercute a todos los coches, así que vive fuera de
 *  la card de uno. Sólo aparece cuando dice algo que la cabecera no dice ya. */
function systemHealthRow(s) {
  if (s.perms === 'blocked') {
    return actionRow({
      tone: 'error', ic: 'warn',
      title: 'Sin ubicación no hay detección automática',
      sub: 'No sabremos cuándo te vas, así que no guardaremos dónde dejaste el coche ni '
         + 'publicaremos la plaza que liberas.',
      cta: 'Dar permiso', act: 'grantcore', cta2: 'Más tarde', act2: 'peek',
    });
  }
  if (s.watch === 'interrupted') {
    return actionRow({
      tone: 'error', ic: 'warn',
      title: 'La vigilancia se ha parado',
      sub: 'El sistema cerró el servicio. Hasta que se reactive no detectaremos tu salida.',
      cta: 'Reactivar', act: 'fixwatch',
    });
  }
  if (s.watch === 'fragile') {
    return actionRow({
      tone: 'warn', ic: 'shield',
      title: 'La vigilancia aguanta mal en este móvil',
      sub: 'El sistema puede cerrarla mientras conduces. Con la excepción de batería deja de pasar.',
      cta: 'Quitar la restricción', act: 'fixwatch',
    });
  }
  return '';
}

/* ── «Aquí y ahora» — las tarjetas de DOS destinos [P1 · P4] ────────────────*/

function nowCards(s) {
  const list = notables(s);
  if (!list.length) return '';   // ⛔ Sin notables la sección NO EXISTE.

  const tones = { spot: 'var(--spot-fresh)', parking: 'var(--car-park)', fuel: 'var(--amber)' };
  const ids = { spot: 'spot', parking: 'park', fuel: 'fuel' };
  const icons = { spot: 'spot', parking: 'parking', fuel: 'fuel' };

  return list.map((n) => {
    const L = subjectLines(n.kind, n.item, s);
    // ⛔ El pie dice CUÁNTAS hay, no a dónde va: el subject ya lo dice el eyebrow
    // de arriba («GASOLINA 95 MÁS BARATA»), así que lo que el usuario no sabe —y
    // lo que decide si merece la pena pulsar— es el número.
    // ⚠️ Pero «Ver las 3» a solas no es un nombre accesible: fuera de contexto no
    // dice de qué. El texto visible lleva la cifra y el `aria-label` el sujeto.
    const subject = laneName(n.lens).toLowerCase();
    const moreText = n.count === 1 ? 'Ver la única' : `Ver las ${n.count}`;
    return `
    <div class="opcard">
      <button class="opcard-main" type="button"
              data-open="${n.kind}" data-i="${n.index}" data-lens="${n.lens}">
        <span class="opcard-eyebrow">
          <span class="tile" data-identity="${ids[n.kind]}" style="width:28px;height:28px">
            ${icon(icons[n.kind], 16)}
          </span>
          <span class="t-eyebrow muted">${esc(n.eyebrow)}</span>
        </span>
        ${n.move ? `
          <!-- El CAMBIO manda sobre el nivel: la cifra grande es el delta, y el
               precio al que deja la gasolinera baja a la línea de abajo. -->
          <span class="figureblock">
            <span class="t-counter" style="color:${n.move.down ? 'var(--green)' : 'var(--amber)'}">
              ${esc(n.move.text)}</span>
          </span>`
        : L.figure ? `
          <span class="figureblock">
            <span class="t-counter" style="color:${tones[n.kind]}">${esc(L.figure)}</span>
            ${L.unit ? `<span class="t-counterUnit muted">${esc(L.unit)}</span>` : ''}
          </span>` : ''}
        <span class="t-rowName" style="display:block;margin-top:${L.figure ? '6px' : '0'}">
          ${esc(L.name)}</span>
        <span class="t-caption muted" style="display:block">
          ${n.move && L.figure ? `${esc(L.figure)} ${esc(L.unit || '')} · ` : ''}${
            esc(L.where || '')}${L.far ? ` · a ${esc(L.far)}` : ''}
        </span>
        ${n.stamp ? `<span class="t-meta muted" style="display:block;margin-top:4px">${esc(n.stamp)}</span>` : ''}
        ${n.standout ? `
          <!-- El MISMO chip que lleva su fila en la lista. Es lo que cierra el
               lazo: al abrir «Ver las 3» se reconoce cuál era. -->
          <span class="standout" data-claim="${n.standout.claim}">${esc(n.standout.label)}</span>`
          : ''}
      </button>
      <button class="opcard-more" type="button" data-list="${n.lens}"
              aria-label="${esc(moreText)} ${esc(subject)}">
        <span>${esc(moreText)}</span>
        ${icon('chevron', 16)}
      </button>
    </div>`;
  }).join('');
}

/* ── La cabecera del COCHE, en DOS líneas [P6] ──────────────────────────────
   Decisión: «hace 2 h» y «a 180 m» NO son el mismo dato y no caben en la misma
   línea. Se reparten por la PREGUNTA que contestan:

     línea 1 → Ford Focus · Aparcado hace 2 h          ¿qué / cuándo?
     línea 2 → Calle de Embajadores, 118 · a 180 m     ¿dónde / cuán lejos?

   ⛔ Y la distancia NO trunca: es el dato más corto y el que más cambia, así
   que lleva ancho propio al final de su línea; lo que se recorta es la calle.
   ⛔ Sin fix no hay «de ti» que decir, así que la distancia no existe — no se
   escribe un «—» ni un 0.

   Lo que se descartó, y por qué:
   · meterlo en la línea 1 («Aparcado hace 2 h · a 180 m») → la línea 1 pasa a
     contestar dos preguntas y se queda sin sitio para nombres largos;
   · una tercera línea → es justo lo que el user quiere quitar;
   · reservarlo sólo para la sección del coche → la distancia es el dato más
     accionable cuando vas ANDANDO al coche, y es cuando el peek está cerrado. */

function carTwoLines(s, opts = {}) {
  const v = DEMO.vehicles[0];
  const id = identityOf(v, s);
  const colour = identityColour(id);
  const st = carStatus(s);
  const compact = opts.compact === true;

  // El «cuándo» sólo existe cuando hay sesión aparcada que fecharlo.
  const qual = s.car === 'parked' ? `${st.eyebrow} ${v.ago}` : st.eyebrow;
  const far = (s.car === 'parked' && s.loc === 'ok') ? v.dist : null;

  return `
    <div class="row" style="padding:0;min-height:0;cursor:${opts.onTap === false ? 'default' : 'pointer'}"
         ${opts.onTap === false ? '' : 'data-act="expand"'}>
      <span class="tile" data-identity="${id}"
            style="width:${compact ? 34 : 42}px;height:${compact ? 34 : 42}px">
        ${icon(v.size === 'Moto' ? 'moto' : 'car', compact ? 19 : 23)}
      </span>
      <span class="grow">
        <span class="sline1">
          <span class="name ${compact ? 't-rowName' : 't-cardTitle'}"
                style="color:${colour}">${esc(v.name)}</span>
          <span class="dot">·</span>
          <span class="qual ${compact ? 't-caption' : 't-rowTitle'}">${esc(qual)}</span>
        </span>
        <span class="sline2">
          <span class="where ${compact ? 't-caption' : 't-body'}">${esc(st.line)}</span>
          ${far ? `<span class="dot">·</span><span class="far t-caption">a ${esc(far)}</span>` : ''}
        </span>
      </span>
      ${opts.badge === false ? '' : `
        <span class="trail">${badgeMarkup(s)
          || (opts.onTap === false ? '' : icon('chevron', 20))}</span>`}
    </div>`;
}

/** Las acciones del momento: una principal, una quieta. Nunca tres. */
function carActions(s) {
  if (s.perms === 'blocked') return `
    <button class="pill small" data-kind="primary" data-act="grantcore" type="button">Dar permiso</button>
    <button class="pill small" data-kind="quiet" data-act="expand" type="button">Por qué</button>`;
  if (s.ask === 'pending') return `
    <button class="pill small" data-kind="primary" data-act="answered" type="button">Sí, he aparcado</button>
    <button class="pill small" data-kind="quiet" data-act="driving" type="button">Sigo conduciendo</button>`;
  if (s.watch === 'interrupted') return `
    <button class="pill small" data-kind="primary" data-act="fixwatch" type="button">Reactivar vigilancia</button>
    <button class="pill small" data-kind="quiet" data-act="expand" type="button">Ver detalle</button>`;
  if (s.car === 'driving') return `
    <button class="pill small" data-kind="secondary" data-act="expand" type="button">Ver el viaje</button>
    <button class="pill small" data-kind="quiet" data-act="stop" type="button">Dejar de seguir</button>`;
  if (s.car === 'unmarked') return `
    <button class="pill small" data-kind="primary" data-act="mark" type="button">Marcar aparcamiento</button>
    <button class="pill small" data-kind="quiet" data-act="driving" type="button">Voy conduciendo</button>`;
  return `
    <button class="pill small" data-kind="secondary" data-act="nav" type="button">Llévame al coche</button>
    <button class="pill small" data-kind="quiet" data-act="release" type="button">Me voy</button>`;
}

/** La pista de que la hoja sigue abajo. Responde a «si no llega hasta abajo no
 *  sabe qué quiere decir el sheet»: el corte dice que continúa. */
function scrollCue(text) {
  return `<p class="scrollcue">${icon('chevron', 14, 'rot90')} ${esc(text)}</p>`;
}
