/* ============================================================================
   Verificador de los prototipos de la home.

       node design-prototypes/paparcar-home/verify.js

   Existe porque una prohibición sin testigo no es una prohibición. Los
   prototipos declaran reglas —«el badge que avisa no cede», «sin notables la
   sección no existe», «ningún botón decorativo»— y sin algo que las ejecute
   son comentarios.

   No necesita dependencias: `vm` + un DOM de pega bastan porque las funciones
   de render son puras (estado → string).
   ============================================================================ */

'use strict';
const fs = require('fs');
const vm = require('vm');
const path = require('path');

const DIR = __dirname;
const PAGES = { A: 'vehicle-first.html', B: 'smart-mobility.html', C: 'adaptive-home.html' };

/** Los 14 ejes del panel. El barrido recorre cada eje entero sobre la base, más
 *  combinaciones aleatorias para cazar interacciones entre ejes. */
const AXES = {
  car: ['parked', 'driving', 'unmarked'],
  watch: ['ok', 'fragile', 'interrupted'],
  ask: ['none', 'pending'],
  spots: [0, 4],
  fuel: ['prices', 'noprices', 'none'],
  parkings: [3, 0],
  loc: ['ok', 'missing'],
  cars: [1, 2],
  onboarding: [false, true],
  perms: ['ok', 'blocked'],
  fuelAge: ['fresh', 'stale'],
  clearance: ['known', 'unknown'],
  latch: ['reset', 'keep', 'car'],
  laneHeader: ['scroll', 'always', 'never'],
};

let checks = 0;
const failures = [];
function fail(msg) { failures.push(msg); }

/* ── DOM de pega: las funciones de render no lo tocan, pero el fichero sí en
      su nivel superior (y `bootPrototype` se sustituye antes de llamarlo). ── */
function fakeDom() {
  const el = () => ({
    dataset: {}, style: {}, innerHTML: '', textContent: '', hidden: false,
    tabIndex: 0, offsetHeight: 280, offsetTop: 400, scrollTop: 0, clientHeight: 844,
    addEventListener() {}, setAttribute() {}, getAttribute() { return null; },
    querySelector() { return null; }, querySelectorAll() { return []; },
    setPointerCapture() {}, closest() { return null; },
  });
  return {
    documentElement: { dataset: {} }, body: el(), title: '',
    getElementById: el, querySelector: el, querySelectorAll() { return []; },
  };
}

function loadPage(page) {
  const html = fs.readFileSync(path.join(DIR, page), 'utf8');
  const inline = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]).join('\n');
  const src = [
    fs.readFileSync(path.join(DIR, 'assets/proto.js'), 'utf8'),
    fs.readFileSync(path.join(DIR, 'assets/shell.js'), 'utf8'),
  ].join('\n')
    + '\nbootPrototype = function (s) { globalThis.__SPEC = s; };\n'
    + inline;

  const ctx = {
    console, globalThis: undefined, window: { addEventListener() {} },
    document: fakeDom(), requestAnimationFrame() {}, setTimeout() {}, clearTimeout() {},
  };
  ctx.globalThis = ctx;
  vm.createContext(ctx);
  vm.runInContext(src, ctx, { filename: page });
  const get = (n) => vm.runInContext(n, ctx);
  return { spec: ctx.__SPEC, get, ctx };
}

function cases() {
  const out = [];
  for (const k of Object.keys(AXES)) for (const v of AXES[k]) out.push({ [k]: v });
  for (let i = 0; i < 60; i++) {
    const c = {};
    for (const k of Object.keys(AXES)) c[k] = AXES[k][Math.floor(Math.random() * AXES[k].length)];
    out.push(c);
  }
  return out;
}

/* ══ 1 · Barrido de render ════════════════════════════════════════════════════
   Ninguna combinación puede lanzar, devolver algo que no sea un string, ni
   colar un `undefined` / `NaN` / `[object Object]` en la salida. */

const emittedActions = new Set();
const handledActions = new Set(
  [...fs.readFileSync(path.join(DIR, 'assets/shell.js'), 'utf8')
    .matchAll(/case '([a-z]+)':/g)].map((m) => m[1]),
);

for (const [id, page] of Object.entries(PAGES)) {
  const { spec, get } = loadPage(page);
  if (!spec) { fail(`${page}: bootPrototype no se llamó`); continue; }
  const ui = get('ui');
  const DEF = get('DEFAULT_SCENARIO');
  const mapSvg = get('mapSvg');
  const lanePeek = get('lanePeek');
  const detailPeek = get('detailPeek');
  const sharedBody = get('sharedBody');

  const details = [null, { kind: 'spot', index: 0 }, { kind: 'parking', index: 1 },
                   { kind: 'fuel', index: 2 }];

  for (const lens of ['spots', 'parkings', 'fuel']) {
    for (const detail of details) {
      ui.lens = lens; ui.detail = detail; ui.headerMode = detail ? 'detail' : 'mine';
      for (const c of cases()) {
        const s = Object.assign({}, DEF, c);
        const jobs = [
          ['peekMine', () => spec.peekMine(s, ui)],
          ['bodyBlocks', () => spec.bodyBlocks(s, ui)],
          ['why', () => spec.why(s)],
          ['lanePeek', () => lanePeek(s, ui)],
          ['sharedBody', () => sharedBody(s, ui, spec)],
          ['mapSvg', () => mapSvg(s, lens, detail)],
        ];
        if (detail) jobs.push(['detailPeek', () => detailPeek(s, ui)]);

        for (const [name, fn] of jobs) {
          checks++;
          let out;
          try { out = fn(); } catch (e) {
            fail(`${id}/${name}/${lens}: ${e.message} · ${JSON.stringify(c)}`);
            continue;
          }
          if (typeof out !== 'string') { fail(`${id}/${name}: no devuelve string`); continue; }
          const dirty = out.match(/.{0,50}(undefined|NaN|\[object Object\]).{0,50}/);
          if (dirty) fail(`${id}/${name}/${lens}: «${dirty[0].trim()}» · ${JSON.stringify(c)}`);
          for (const m of out.matchAll(/data-act="([a-z]+)"/g)) emittedActions.add(m[1]);
        }
      }
    }
  }
}

/* ══ 2 · Ningún botón decorativo ══════════════════════════════════════════════ */
checks++;
const dead = [...emittedActions].filter((a) => !handledActions.has(a));
if (dead.length) fail(`botones sin manejador: ${dead.join(', ')}`);

/* ══ 3 · EL BADGE DE VIGILANCIA: SÓLO EXISTE CUANDO AVISA ═════════════════════
   Dos direcciones, y las dos importan:

   (a) Un aviso **no cede** su hueco en ninguno de los tres modos de cabecera.
       Es la regla que faltaba y que el user cazó: leyendo el carril, el trailing
       lo ocupaba el nombre del carril y el badge DESAPARECÍA, incluso en pausa.

   (b) Sano **no hay badge**, y no es una omisión: el MÉTODO de vigilancia es un
       glifo y un color, nunca una etiqueta
       (`UI-VEH-STATUS-IS-A-GLYPH-NOT-A-LABEL-001`). Un chip verde permanente
       sería la app reclamando mérito en cada pantalla, y encima etiquetando el
       tier que esa doctrina prohíbe imprimir. */

{
  const { spec, get } = loadPage(PAGES.B);
  const ui = get('ui');
  const DEF = get('DEFAULT_SCENARIO');
  const lanePeek = get('lanePeek');
  const detailPeek = get('detailPeek');
  const watchAlert = get('watchAlert');

  const modes = [
    ['mine', (s) => { ui.detail = null; ui.headerMode = 'mine'; return spec.peekMine(s, ui); }],
    ['lane', (s) => { ui.detail = null; ui.headerMode = 'lane'; return lanePeek(s, ui); }],
    ['detail', (s) => {
      ui.detail = { kind: 'fuel', index: 0 }; ui.headerMode = 'detail';
      return detailPeek(s, ui);
    }],
  ];

  // (a) El aviso aparece, con su texto, en los tres modos.
  const alerts = [
    { patch: { watch: 'fragile' }, label: 'frágil' },
    { patch: { watch: 'interrupted' }, label: 'en pausa' },
    { patch: { perms: 'blocked' }, label: 'falta permiso' },
  ];
  for (const { patch, label } of alerts) {
    const s = Object.assign({}, DEF, patch);
    const a = watchAlert(s);
    checks++;
    if (!a) { fail(`watchAlert() no avisa con ${JSON.stringify(patch)}`); continue; }
    for (const [mode, render] of modes) {
      checks++;
      if (!render(s).includes(a.text)) {
        fail(`el aviso «${a.text}» (${label}) NO aparece en la cabecera en modo ${mode}`);
      }
    }
    // Y lleva la puerta a la fila que tiene el remedio.
    checks++;
    if (!modes[0][1](s).includes('data-act="showwatch"')) {
      fail(`el aviso «${a.text}» no abre la fila de acción que lo arregla`);
    }
  }

  // (b) Sano: ni chip, ni texto de tier, en NINGÚN modo.
  const healthy = [
    { patch: {}, label: 'asistida' },
    { patch: { cars: 2 }, label: 'con dos coches' },
  ];
  const forbidden = ['Vigilando', 'Bluetooth', 'Vigilancia frágil', 'Vigilancia en pausa'];
  for (const { patch, label } of healthy) {
    const s = Object.assign({}, DEF, patch);
    checks++;
    if (watchAlert(s) !== null) fail(`watchAlert() avisa sin motivo (${label})`);
    for (const [mode, render] of modes) {
      const out = render(s);
      for (const word of forbidden) {
        checks++;
        if (out.includes(word)) {
          fail(`vigilancia sana (${label}): la cabecera imprime «${word}» en modo ${mode} — `
             + 'el método es glifo y color, no etiqueta');
        }
      }
    }
  }
}

/* ══ 4 · Sin notables, la sección NO existe ═══════════════════════════════════ */
{
  const { get } = loadPage(PAGES.B);
  const DEF = get('DEFAULT_SCENARIO');
  const notables = get('notables');
  const nowCards = get('nowCards');

  const empties = [
    { spots: 0, parkings: 0, fuel: 'none' },
    { spots: 0, parkings: 0, fuel: 'noprices' },
    { spots: 0, parkings: 0, fuel: 'prices', fuelAge: 'stale' },
  ];
  for (const patch of empties) {
    checks++;
    const s = Object.assign({}, DEF, patch);
    if (notables(s).length !== 0) fail(`notables() debería estar vacío con ${JSON.stringify(patch)}`);
    if (nowCards(s) !== '') fail(`«Aquí y ahora» se renderiza con ${JSON.stringify(patch)}`);
  }

  // Conduciendo NO se filtra nada — decisión del user (27-10): repostar se hace
  // conduciendo, así que las gasolineras no se apagan ahí.
  checks++;
  const driving = Object.assign({}, DEF, { car: 'driving', spots: 4, parkings: 3, fuel: 'prices' });
  const kinds = notables(driving).map((n) => n.kind);
  if (!kinds.includes('fuel')) fail('conduciendo debería seguir ofreciendo gasolineras');
  if (kinds.length !== 3) fail(`conduciendo deberían salir los 3 sujetos, salen ${kinds.length}`);

  // Y el orden es FIJO: plaza · parking · gasolinera, el mismo que las pestañas.
  checks++;
  const order = notables(Object.assign({}, DEF, { spots: 4, parkings: 3, fuel: 'prices' }))
    .map((n) => n.kind).join(',');
  if (order !== 'spot,parking,fuel') fail(`el orden de los notables no es fijo: ${order}`);
}

/* ══ 5 · La dirección no aparece en las dos líneas ════════════════════════════ */
{
  const { get } = loadPage(PAGES.B);
  const DEF = get('DEFAULT_SCENARIO');
  const subjectLines = get('subjectLines');
  const samples = [
    ['spot', { place: 'Calle del Olivar, 34', size: 'Mediano', far: '120 m', age: '4 min', heat: 'fresh' }],
    ['parking', { name: 'Parking Embajadores', far: '240 m', rate: '2,10', fitLabel: 'Cabe tu coche' }],
    ['fuel', { name: 'Ballenoil Méndez', street: 'Calle de Méndez Álvaro', far: '1,4 km', price: '1,419' }],
  ];
  for (const [kind, item] of samples) {
    checks++;
    const L = subjectLines(kind, item, Object.assign({}, DEF));
    if (L.where && L.name && L.where === L.name) {
      fail(`${kind}: la línea 1 y la 2 dicen lo mismo («${L.name}»)`);
    }
    if (!L.name) fail(`${kind}: la línea 1 no tiene nombre ni tipo`);
  }

  // Sin fix no hay distancia: ni «—» ni 0.
  checks++;
  const noFix = subjectLines('spot', samples[0][1], Object.assign({}, DEF, { loc: 'missing' }));
  if (noFix.far) fail('sin ubicación la distancia no debería existir');
}

/* ══ 6 · Enlaces internos ═════════════════════════════════════════════════════ */
for (const f of fs.readdirSync(DIR).filter((x) => x.endsWith('.html'))) {
  const html = fs.readFileSync(path.join(DIR, f), 'utf8');
  for (const m of html.matchAll(/href="([^"#:]+)"/g)) {
    const href = m[1];
    if (href.startsWith('http') || href.startsWith('//')) continue;
    checks++;
    if (!fs.existsSync(path.join(DIR, href))) fail(`${f}: enlace roto a ${href}`);
  }
}

/* ══ 7 · Fidelidad: el prototipo no puede poder MÁS que el destino ═══════════ */
{
  const css = fs.readFileSync(path.join(DIR, 'assets/base.css'), 'utf8');
  checks++;
  // CMP 1.12 no aplica `fontFeatureSettings`, así que el CSS tampoco debe.
  const decls = css.split('\n').filter((l) => /font-variant-numeric/.test(l) && !/^\s*\/[/*]/.test(l));
  if (decls.length) fail(`el CSS aplica cifras tabulares, que CMP 1.12 no puede: ${decls.length} decl.`);
  checks++;
  // El atajo `font` exige una familia real: `inherit` descarta la declaración.
  const bad = [...css.matchAll(/font:\s*[^;{}]*?\binherit\b/g)];
  if (bad.length) fail(`${bad.length} declaraciones \`font: … inherit\` (gramática inválida)`);
}

/* ══ 8 · LOS STANDOUTS Y SUS NEGATIVAS ════════════════════════════════════════
   «Mostrar cuáles son mejores» no es poner una estrella: es una lista de cosas
   que el badge se NIEGA a afirmar. Cada negativa tiene su motivo medido en
   `FuelStandouts.kt`, y aquí se ejecuta. */

{
  const { get } = loadPage(PAGES.B);
  const DEF = get('DEFAULT_SCENARIO');
  const standouts = get('standouts');
  const standoutBadge = get('standoutBadge');
  const standoutRank = get('standoutRank');
  const notables = get('notables');
  const DEMO = get('DEMO');

  const F = (id, m, price) => ({ id, m, price });

  // (1) Ningún superlativo sobre un solo candidato.
  checks++;
  const one = standouts([F('a', 300, 1.5)]);
  if (one.cheapest.size || one.nearest) fail('un solo candidato no puede llevar superlativo');

  // (2) Un empate COMPARTE el badge, no lo mata.
  checks++;
  const tie = standouts([F('a', 300, 1.809), F('b', 700, 1.809), F('c', 900, 1.9)]);
  if (!(tie.cheapest.has('a') && tie.cheapest.has('b') && tie.cheapest.size === 2)) {
    fail('un empate al mínimo debe dar el badge a TODAS las que lo cotizan');
  }

  // (3) El empate UNIVERSAL sí se calla: no distingue nada.
  checks++;
  const flat = standouts([F('a', 300, 1.5), F('b', 700, 1.5)]);
  if (flat.cheapest.size) fail('si todas cobran lo mismo, «la más barata» no distingue nada');
  checks++;
  if (flat.nearest !== 'a') fail('la más cercana debe sobrevivir al empate de precios');

  // (4) Sin «más barata» entre monedas distintas; los metros siguen siendo metros.
  checks++;
  const mixed = standouts([F('a', 300, 1.65), F('b', 700, 1.42)], 2);
  if (mixed.cheapest.size) fail('no hay «más barata» con dos monedas en la lista');
  checks++;
  if (mixed.nearest !== 'a') fail('la más cercana sobrevive a la mezcla de monedas');

  // (5) La más cercana es ÚNICA: si empatan, se cae.
  checks++;
  const sameM = standouts([F('a', 300, 1.5), F('b', 300, 1.6)]);
  if (sameM.nearest !== null) fail('con la distancia empatada, ninguna es la más cercana');

  // (6) Una fila lleva UN badge, nunca dos.
  checks++;
  const both = standouts([F('a', 300, 1.4), F('b', 900, 1.9)]);
  const badge = standoutBadge(both, 'a');
  if (!badge || badge.label !== 'MÁS BARATA') {
    fail('la que es las dos cosas lleva sólo «MÁS BARATA»');
  }

  // (7) Sólo la más barata sube; la más cercana NO reordena la respuesta.
  //     ⚠️ Hacen falta DOS con precio para que haya «más barata» a la que no
  //     adelantarse: con una sola, el superlativo no existe y los rangos empatan
  //     —que es lo correcto, no un fallo—. La primera versión de este testigo lo
  //     montó con una sola y se cayó sola; es exactamente para lo que está.
  checks++;
  const noPrice = standouts([F('a', 100, null), F('b', 900, 1.4), F('c', 950, 1.9)]);
  if (noPrice.nearest !== 'a') fail('la más cercana se mide sobre TODAS, no sólo las que cotizan');
  checks++;
  if (!noPrice.cheapest.has('b')) fail('con dos precios distintos sí hay «más barata»');
  checks++;
  if (standoutRank(noPrice, 'a') <= standoutRank(noPrice, 'b')) {
    fail('la más cercana sin precio no debe adelantarse a la más barata');
  }

  // (7b) Y con UNA sola cotizando, el superlativo no existe y nada se mueve.
  checks++;
  const lonePrice = standouts([F('a', 100, null), F('b', 900, 1.4)]);
  if (lonePrice.cheapest.size) fail('no hay «más barata» con una sola que cotice');
  checks++;
  if (standoutRank(lonePrice, 'a') !== standoutRank(lonePrice, 'b')) {
    fail('sin «más barata» nada debe reordenarse');
  }

  // (8) La tarjeta de «Aquí y ahora» nombra el MISMO item que lleva el badge.
  //     Es lo que impide que el primer nivel y la lista discrepen.
  checks++;
  const s = Object.assign({}, DEF, { fuel: 'prices', fuelAge: 'fresh' });
  const card = notables(s).find((n) => n.kind === 'fuel');
  const items = DEMO.fuel.map((f, i) => ({ ...f, i }));
  const so = standouts(items);
  if (!card) {
    fail('no hay tarjeta de gasolinera con precios frescos');
  } else {
    const picked = items[card.index];
    if (!so.cheapest.has(picked.id)) {
      fail(`la tarjeta nombra «${picked.name}», que NO es la que lleva el badge de más barata`);
    }
    checks++;
    if (!card.standout || card.standout.label !== 'MÁS BARATA') {
      fail('la tarjeta debe llevar el mismo chip que su fila en la lista');
    }
  }

  // (9) ⛔ Una PLAZA nunca lleva standout: señalar «la mejor» entre cuatro manda a
  //     todo el mundo a la misma, y la app ya cuenta quién va de camino.
  checks++;
  const withSpots = Object.assign({}, DEF, { spots: 4 });
  const spotCard = notables(withSpots).find((n) => n.kind === 'spot');
  if (spotCard && spotCard.standout) {
    fail('una plaza no puede llevar «la mejor»: crearía una carrera por la misma plaza');
  }

  // (10) ⛔ Un PARKING tampoco, y por tres motivos distintos: «más barato» es imposible
  //      (`FeeInfo` no tiene importe), «más cercano» es redundante (la lista se ordena sólo
  //      por distancia) y «gratis» ya lo dice la fila.
  checks++;
  const parkCard = notables(Object.assign({}, DEF, { parkings: 3 }))
    .find((n) => n.kind === 'parking');
  if (!parkCard) fail('con parkings cerca debería haber tarjeta de parking');
  else if (parkCard.standout) {
    fail('un parking no lleva standout: no hay importe que comparar y el orden ya dice cuál es el más cercano');
  }

  // (11) ⛔ Y un parking con la tarifa SIN DATO no dice «gratis». Es el 71 % de los
  //      parkings del mundo: a quien le dijimos «gratis» y se encontró una barrera le
  //      mintió la app, no OpenStreetMap.
  const subjectLines = get('subjectLines');
  for (const [fee, expected] of [['free', 'Gratuito'], ['paid', 'De pago'], ['unknown', null]]) {
    checks++;
    const L = subjectLines('parking',
      { name: 'Parking X', far: '240 m', fee, fitLabel: 'Cabe tu coche' },
      Object.assign({}, DEF));
    if ((L.tail ?? null) !== expected) {
      fail(`tarifa ${fee}: la fila debería decir ${expected === null ? 'NADA' : expected}, dice ${L.tail}`);
    }
    checks++;
    if (L.figure != null) fail(`tarifa ${fee}: un parking no tiene cifra, no hay importe en los datos`);
  }
}

/* ══ 9 · EL AUTO-ABRIR: CUÁNDO SÍ Y, SOBRE TODO, CUÁNDO NO ═══════════════════
   La mitad interesante de esta regla son sus negativas: una hoja que se abre
   sola tapa el mapa, así que tiene que ganárselo. */

{
  const { get } = loadPage(PAGES.B);
  const DEF = get('DEFAULT_SCENARIO');
  const autoOpenAnchor = get('autoOpenAnchor');

  const S = (patch) => Object.assign({}, DEF, patch);
  const rich = { spots: 4, parkings: 3, fuel: 'prices', fuelAge: 'fresh' };

  // Sin novedad no se abre, aunque haya de todo alrededor.
  checks++;
  if (autoOpenAnchor(S({ launch: 'quiet', ...rich })) !== null) {
    fail('sin novedad la hoja no debe abrirse sola');
  }

  // Con novedad y algo que mostrar: a MEDIA hoja, nunca desplegada.
  checks++;
  const anchor = autoOpenAnchor(S({ launch: 'news', ...rich }));
  if (anchor !== 'half') fail(`con novedad debe abrir a 'half', abrió a '${anchor}'`);

  // ⛔ Las cuatro negativas, una por una.
  const refusals = [
    ['conduciendo', { launch: 'news', ...rich, car: 'driving' }],
    ['pregunta con plazo', { launch: 'news', ...rich, ask: 'pending' }],
    ['vigilancia en pausa', { launch: 'news', ...rich, watch: 'interrupted' }],
    ['falta permiso', { launch: 'news', ...rich, perms: 'blocked' }],
    ['nada que mostrar', { launch: 'news', spots: 0, parkings: 0, fuel: 'none' }],
  ];
  for (const [label, patch] of refusals) {
    checks++;
    if (autoOpenAnchor(S(patch)) !== null) {
      fail(`la hoja no debe abrirse sola con ${label}`);
    }
  }
}

/* ══ 10 · EL CAMBIO DE PRECIO GANA AL NIVEL, Y SÓLO CUANDO ES AFIRMABLE ══════ */

{
  const { get } = loadPage(PAGES.B);
  const DEF = get('DEFAULT_SCENARIO');
  const notables = get('notables');

  const S = (patch) => Object.assign({}, DEF, patch);

  // Con movimiento, la tarjeta de gasolinera habla del CAMBIO.
  for (const dir of ['drop', 'rise']) {
    checks++;
    const card = notables(S({ fuel: 'prices', fuelAge: 'fresh', fuelMove: dir }))
      .find((n) => n.kind === 'fuel');
    if (!card || !card.move) fail(`con fuelMove=${dir} la tarjeta debe contar el movimiento`);
    else {
      checks++;
      if (card.move.down !== (dir === 'drop')) fail(`la dirección de ${dir} está invertida`);
    }
  }

  // Sin movimiento, vuelve al nivel (la más barata).
  checks++;
  const level = notables(S({ fuel: 'prices', fuelAge: 'fresh', fuelMove: 'none' }))
    .find((n) => n.kind === 'fuel');
  if (!level || level.move) fail('sin movimiento la tarjeta debe volver al nivel');

  // ⛔ Y un movimiento NO resucita un precio que no se puede afirmar: con el
  // precio demasiado viejo, o sin precios, no hay tarjeta de gasolinera.
  for (const patch of [{ fuel: 'prices', fuelAge: 'stale', fuelMove: 'drop' },
                       { fuel: 'noprices', fuelMove: 'drop' },
                       { fuel: 'none', fuelMove: 'drop' }]) {
    checks++;
    if (notables(S(patch)).some((n) => n.kind === 'fuel')) {
      fail(`un movimiento no puede saltarse el umbral: ${JSON.stringify(patch)}`);
    }
  }

  // ⛔ Sigue habiendo UNA tarjeta por sujeto: el movimiento no añade una cuarta.
  checks++;
  const all = notables(S({ spots: 4, parkings: 3, fuel: 'prices', fuelAge: 'fresh',
                           fuelMove: 'drop' }));
  if (all.length !== 3) fail(`el movimiento no debe añadir una tarjeta: salen ${all.length}`);
  checks++;
  if (all.map((n) => n.kind).join(',') !== 'spot,parking,fuel') {
    fail('el orden fijo debe aguantar con el movimiento puesto');
  }
}

/* ── Informe ──────────────────────────────────────────────────────────────── */
console.log(failures.length
  ? failures.map((f) => `  ✗ ${f}`).join('\n')
  : '  ✓ todas las reglas declaradas se cumplen');
console.log(`\n${checks} comprobaciones · ${failures.length} fallos`);
process.exit(failures.length ? 1 : 0);
