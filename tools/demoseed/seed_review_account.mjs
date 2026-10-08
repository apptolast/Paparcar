#!/usr/bin/env node
/**
 * Siembra la cuenta de demo `paparcar.review@gmail.com` con tres meses de datos derivados del
 * corpus REAL de la cuenta de desarrollo. [DEMO-A-REVIEW-ACCOUNT-MUST-LOOK-LIVED-IN-001]
 *
 * Por que solo Firestore: `BootstrapUserDataUseCase` hace `syncFromRemote(uid)` de vehicles +
 * parkingHistory + zones tras cada login, y `getParkingHistory()` se trae la coleccion entera sin
 * ventana. Room es un espejo reconciliado, no una fuente de verdad, y la ruta del viaje viaja en
 * el propio doc de sesion (`routePolyline`). Sembrar Firestore ES sembrar la app.
 *
 * Por que la base `(default)`: `FirestoreDatabase.idFor(isDebugBuild)` manda los builds debug a la
 * base `debug`; un juez instala de Play, que es release, y lee `(default)`.
 *
 * IDEMPOTENTE: cada id se deriva de SEED con SHA-1, asi que volver a correrlo reescribe los mismos
 * documentos en vez de acumular duplicados, y borra los que sobran de una siembra anterior.
 *
 * Uso:
 *   node tools/demoseed/seed_review_account.mjs --dry-run   # escribe el plan a un JSON, no toca nada
 *   node tools/demoseed/seed_review_account.mjs             # aplica sobre `(default)` (= build de Play)
 *   node tools/demoseed/seed_review_account.mjs --database=debug   # la base que leen los prodDebug
 *
 * Credenciales: las del `firebase` CLI ya logueado. No hay secretos en este fichero.
 */

import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

// --- Configuracion ------------------------------------------------------------
const PROJECT = 'pap-26'
/**
 * `(default)` es lo que lee un build de release (el de Play, el que instalaria un juez);
 * `debug` es lo que leen los `prodDebug` que se instalan a mano. `FirestoreDatabase.idFor()`.
 */
const DATABASE = (process.argv.find((a) => a.startsWith('--database=')) || '--database=(default)').split('=')[1]
const SRC_UID = '0l7X59ptXeQ4WUMlpFOZHLPIJAI3' // rndeveloper11501@gmail.com - el corpus real
const DST_UID = 'bIx3P5psSQUzVVcGSdL33CZVOry2' // paparcar.review@gmail.com - la cuenta de demo
/** Semilla de los ids deterministas. Cambiarla reconstruye la cuenta entera con ids nuevos. */
const SEED = 'paparcar-demo-seed-v1'

const MS_DAY = 86400000
const MS_HOUR = 3600000

/** El Sandero que la cuenta de review ya tiene: se conserva su id para no romper su perfil. */
const VEHICLE_DAILY = 'b3b853e5-2a49-4ce1-a85f-f851513a347d'

/**
 * Los tres coches del garaje. Ninguno lleva `bluetoothDeviceId` a proposito: el resolver exige
 * emparejado Y conectado ahora mismo [DET-BT-CONNECTED-NOT-PAIRED-001], asi que un MAC sembrado
 * solo pintaria el coche de azul prometiendo una vigilancia que el movil del juez no tiene.
 */
const VEHICLES = [
  {
    id: VEHICLE_DAILY,
    key: 'vehicle:daily',
    brand: 'Dacia', model: 'Sandero',
    vehicleType: 'CAR', sizeCategory: 'MEDIUM_SUV', carbodyType: 'HATCHBACK_MEDIUM',
    color: 'GRAY', fuelType: 'DIESEL', consumptionPer100: 4.9, energyLabel: 'ECO',
    isActive: true, share: 0.62,
  },
  {
    id: null, // derivado de SEED
    key: 'vehicle:electric',
    brand: 'Renault', model: 'Zoe',
    vehicleType: 'CAR', sizeCategory: 'MICRO_SMALL', carbodyType: 'HATCHBACK_SMALL',
    color: 'WHITE', fuelType: 'ELECTRIC', consumptionElectricPer100: 16.5, energyLabel: 'ZERO',
    isActive: false, share: 0.26,
  },
  {
    id: null,
    key: 'vehicle:moto',
    brand: 'Honda', model: 'PCX 125',
    vehicleType: 'MOTORCYCLE', sizeCategory: 'MOTORCYCLE', carbodyType: '',
    color: 'BLACK', fuelType: 'GASOLINE', consumptionPer100: 2.4, energyLabel: 'ECO',
    isActive: false, share: 0.12,
  },
]

/** Las tres ventanas temporales: la real, y dos clones adelgazados hacia atras. */
const WINDOWS = [
  { key: 'w0', shiftDays: 0, keep: 1.0 },
  { key: 'w1', shiftDays: -30, keep: 0.62 },
  { key: 'w2', shiftDays: -60, keep: 0.55 },
]

const ZONES = [
  { key: 'zone:home', name: 'Home', iconKey: 'home', radiusMeters: 220, isPrivate: true, notifyOnSpot: false, cluster: 0 },
  { key: 'zone:work', name: 'Work', iconKey: 'work', radiusMeters: 300, isPrivate: false, notifyOnSpot: true, cluster: 1 },
  { key: 'zone:downtown', name: 'Downtown', iconKey: 'favorite', radiusMeters: 350, isPrivate: false, notifyOnSpot: true, cluster: 2 },
]

// --- Determinismo -------------------------------------------------------------
const sha1 = (s) => createHash('sha1').update(s).digest('hex')

/** UUID estable a partir de una clave: mismo texto, mismo id, siempre. */
function stableUuid(key) {
  const h = sha1(SEED + '|' + key)
  const variant = ((parseInt(h[16], 16) & 0x3) | 0x8).toString(16)
  const v = h.slice(0, 12) + '5' + h.slice(13, 16) + variant + h.slice(17, 32)
  return [v.slice(0, 8), v.slice(8, 12), v.slice(12, 16), v.slice(16, 20), v.slice(20, 32)].join('-')
}

/** Real determinista en [0,1) a partir de una clave: el jitter tiene que ser reproducible. */
const stableUnit = (key) => parseInt(sha1(SEED + '|r|' + key).slice(0, 8), 16) / 0x100000000

// --- Autenticacion: las credenciales del firebase CLI ya logueado -------------
function firebaseToolsOAuthClient() {
  const candidates = [
    join(process.env.APPDATA || '', 'npm', 'node_modules', 'firebase-tools', 'lib', 'api.js'),
    join(homedir(), '.npm-global', 'lib', 'node_modules', 'firebase-tools', 'lib', 'api.js'),
    '/usr/local/lib/node_modules/firebase-tools/lib/api.js',
  ]
  for (const p of candidates) {
    let src
    try { src = readFileSync(p, 'utf8') } catch { continue }
    const id = src.match(/"FIREBASE_CLIENT_ID",\s*"([^"]+)"/)
    const secret = src.match(/"FIREBASE_CLIENT_SECRET",\s*"([^"]+)"/)
    if (id && secret) return { id: id[1], secret: secret[1] }
  }
  throw new Error('No encuentro el cliente OAuth de firebase-tools. Instalalo o exporta FIREBASE_TOKEN.')
}

async function accessToken() {
  const cfgPath = join(homedir(), '.config', 'configstore', 'firebase-tools.json')
  const cfg = JSON.parse(readFileSync(cfgPath, 'utf8'))
  const tokens = cfg.tokens || {}
  // El CLI cachea un access_token; si le queda mas de un minuto, se reusa.
  if (tokens.access_token && tokens.expires_at && tokens.expires_at - Date.now() > 60000) {
    return tokens.access_token
  }
  if (!tokens.refresh_token) throw new Error('El firebase CLI no tiene refresh token: `firebase login`.')
  const client = firebaseToolsOAuthClient()
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: client.id,
      client_secret: client.secret,
      refresh_token: tokens.refresh_token,
      grant_type: 'refresh_token',
    }),
  })
  if (!res.ok) throw new Error('refresh token rechazado (' + res.status + '): ' + (await res.text()))
  return (await res.json()).access_token
}

// --- Firestore REST -----------------------------------------------------------
/** Nombre de recurso de la base DESTINO: es lo que va en el campo `name` de cada escritura. */
const DOCS = 'projects/' + PROJECT + '/databases/' + DATABASE + '/documents'
/** La misma cosa como URL, que es lo que se llama por HTTP. */
const ROOT = 'https://firestore.googleapis.com/v1/' + DOCS
/**
 * ⛔ El corpus se lee SIEMPRE de `(default)`, nunca de la base destino. En `debug` la cuenta real
 * solo tiene el espejo puntual de 33 sesiones del 13-09, asi que leer de ahi produjo una cuenta de
 * demo distinta en cada base (71 sesiones en debug frente a 191 en `(default)`). Un corpus, una
 * cuenta derivada, la base solo decide DONDE se escribe.
 */
const SRC_ROOT = 'https://firestore.googleapis.com/v1/projects/' + PROJECT + '/databases/(default)/documents'
let TOKEN = null

async function api(path, init) {
  const opts = init || {}
  const url = path.startsWith('http') ? path : ROOT + path
  const res = await fetch(url, {
    ...opts,
    headers: { authorization: 'Bearer ' + TOKEN, 'content-type': 'application/json', ...(opts.headers || {}) },
  })
  if (!res.ok) throw new Error((opts.method || 'GET') + ' ' + path + ' -> ' + res.status + ': ' + (await res.text()))
  return res.json()
}

async function listCollection(docPath, collectionId, mask, base) {
  const out = []
  let token = null
  do {
    const q = new URLSearchParams({ pageSize: '300' })
    if (token) q.set('pageToken', token)
    for (const f of mask || []) q.append('mask.fieldPaths', f)
    const page = await api((base || ROOT) + '/' + docPath + '/' + collectionId + '?' + q)
    out.push(...(page.documents || []))
    token = page.nextPageToken || null
  } while (token)
  return out
}

/** Un commit aguanta 500 escrituras; se parte en lotes y se aplica en orden. */
async function commit(writes) {
  for (let i = 0; i < writes.length; i += 500) {
    const batch = writes.slice(i, i + 500)
    await api(':commit', { method: 'POST', body: JSON.stringify({ writes: batch }) })
    console.log('  . commit ' + (i + batch.length) + '/' + writes.length)
  }
}

// --- Helpers de valores tipados ----------------------------------------------
const int = (n) => ({ integerValue: String(Math.round(n)) })
const dbl = (n) => ({ doubleValue: n })
const str = (s) => ({ stringValue: s })
const bool = (b) => ({ booleanValue: b })
const nul = () => ({ nullValue: null })
const readInt = (f, k) => (f[k] && f[k].integerValue != null ? Number(f[k].integerValue) : null)

// --- Construccion de los documentos ------------------------------------------
function vehicleDoc(v, nowMs) {
  return {
    name: DOCS + '/users/' + DST_UID + '/vehicles/' + v.id,
    fields: {
      id: str(v.id),
      userId: str(DST_UID),
      name: nul(),
      brand: str(v.brand),
      model: str(v.model),
      vehicleType: str(v.vehicleType),
      sizeCategory: str(v.sizeCategory),
      carbodyType: str(v.carbodyType),
      color: str(v.color),
      fuelType: str(v.fuelType),
      energyLabel: str(v.energyLabel),
      bluetoothDeviceId: nul(),
      showBrandModelOnSpot: bool(false),
      isActive: bool(v.isActive),
      consumptionPer100: v.consumptionPer100 != null ? dbl(v.consumptionPer100) : nul(),
      consumptionElectricPer100: v.consumptionElectricPer100 != null ? dbl(v.consumptionElectricPer100) : nul(),
      heightMeters: nul(),
      fuelPricePerLiter: nul(),
      energyPricePerKwh: nul(),
      priceCurrency: nul(),
      sharedCarId: nul(),
      ownerUid: nul(),
      shareRoutesInGroup: bool(true),
      updatedAt: int(nowMs),
    },
  }
}

/**
 * Una sesion de la cuenta de demo derivada de una REAL: se copia el mapa de campos tipado tal cual
 * y se parchea solo lo que tiene que cambiar. Copiar el tipado de REST en bruto es lo que evita
 * equivocar `doubleValue` por `integerValue` en doscientos documentos.
 */
function sessionDoc(srcFields, opts) {
  const f = { ...srcFields }
  const ts = readInt(f, 'timestamp')
  const ended = readInt(f, 'endedAtMs')
  const v = opts.vehicle

  f.id = str(opts.id)
  f.userId = str(DST_UID)
  f.vehicleId = str(v.id)
  f.geofenceId = str(opts.id) // la geocerca se identifica por la sesion, como en produccion
  f.platform = str('android')

  if (opts.active) {
    // La sesion viva: aparcado hace unas horas, sin cerrar. Lo que Home tiene que ensenar.
    f.timestamp = int(opts.nowMs - 4 * MS_HOUR - Math.round(stableUnit(opts.id + '|active') * 90 * 60000))
    f.isActive = bool(true)
    f.endedAtMs = nul()
    f.publishedSpot = bool(false) // aun no se ha ido: no ha liberado nada
    f.retractedAtMs = nul()
    f.updatedAt = int(opts.nowMs)
  } else {
    f.timestamp = int(ts + opts.shiftMs)
    f.isActive = bool(false)
    f.endedAtMs = ended != null ? int(ended + opts.shiftMs) : int(ts + opts.shiftMs + 3 * MS_HOUR)
    f.updatedAt = int((ended != null ? ended : ts) + opts.shiftMs + 60000)
  }

  // La talla se captura al aparcar: tiene que ser la del coche al que se le asigna la sesion.
  f.sizeCategory = str(v.sizeCategory)
  f.carbodyType = v.carbodyType ? str(v.carbodyType) : nul()

  // Una moto no libera una plaza de coche.
  if (v.vehicleType !== 'CAR') {
    f.publishedSpot = bool(false)
    f.communitySpotId = nul()
  }

  // El tiempo de busqueda: se INVENTA solo donde el corpus real no lo tiene. Ver demoSearchTimeMs.
  if (!opts.active && readInt(f, 'searchTimeMs') == null) {
    const invented = demoSearchTimeMs(opts.id, readInt(f, 'timestamp'))
    if (invented != null) f.searchTimeMs = int(invented)
  }
  return { name: DOCS + '/users/' + DST_UID + '/parkingHistory/' + opts.id, fields: f }
}

/**
 * Un tiempo de busqueda PLAUSIBLE para los clones a los que el corpus real no les dio ninguno.
 *
 * ⛔ Esto es dato de DEMO y se declara como tal: no toca ninguna sesion real, solo rellena los
 * `searchTimeMs` que venian a null. Medido el 08-10 sobre las 282 sesiones del plan: 33 medidas
 * > 0, 32 medidas en 0 (llegada directa) y **148 a null**, que dejaban **23 de las 42 celdas** de
 * la rejilla dia x banda vacias (`ParkingHeatmap` exige >=2 muestras por celda). Una rejilla que
 * no se llena no dice nada, y la cuenta de review existe para que se vea lo que la app sabe hacer.
 *
 * ⛔ Con FORMA, no con ruido uniforme: el coste sube por la tarde y baja de madrugada, que es lo
 * que hace que el mapa de calor se lea como un hallazgo y no como un sorteo. Y una parte de las
 * llegadas sigue siendo DIRECTA (0), porque eso es un valor legitimo del dominio — `0` significa
 * «llego sin dar vueltas» y desde
 * [STATS-A-DIRECT-ARRIVAL-DOES-NOT-READ-AS-ZERO-MINUTES-001] la frase ya lo dice con palabras.
 *
 * ⚠️ La hora se toma en la zona LOCAL del proceso, que es la misma en la que la app agrupa la
 * rejilla (`toLocalDateTime`). Sembrar desde otra zona desplazaria las bandas.
 */
function demoSearchTimeMs(id, tsMs) {
  if (tsMs == null) return null
  const band = Math.floor(new Date(tsMs).getHours() / 4) // 6 bandas de 4 h, como ParkingHeatmap
  // Minutos tipicos y probabilidad de llegada directa, por banda:
  //            00-04  04-08  08-12  12-16  16-20  20-24
  // ⛔ Las madrugadas se midieron primero a 1,5–1,8 min con 50–55 % de directas, y ocho celdas
  // quedaban con media POR DEBAJO del minuto: `durationText` divide entre 60.000, asi que habrian
  // escrito «0 min» igual. Suben lo justo para que ninguna celda baje de ~1,5 min sin perder que
  // de noche se aparca mas facil que a las 19 h.
  const TYPICAL = [2.4, 2.6, 3.4, 2.8, 6.2, 4.4]
  const DIRECT = [0.28, 0.26, 0.22, 0.24, 0.1, 0.15]
  if (stableUnit('direct|' + id) < DIRECT[band]) return 0
  // +-55 % reproducible alrededor del tipico de su banda.
  const spread = 1 + (stableUnit('span|' + id) - 0.5) * 1.1
  const minutes = TYPICAL[band] * spread
  // Suelo en MIN_SEARCH_MS (90 s): por debajo, el dominio lo llamaria directo y seria un 0 con
  // disfraz. Techo holgado bajo MAX_PLAUSIBLE_SEARCH_MS (45 min).
  return Math.max(90000, Math.min(20 * 60000, Math.round(minutes * 60000)))
}

function zoneDoc(z, center, nowMs) {
  const id = stableUuid(z.key)
  return {
    name: DOCS + '/users/' + DST_UID + '/zones/' + id,
    fields: {
      id: str(id),
      userId: str(DST_UID),
      name: str(z.name),
      lat: dbl(center.lat),
      lon: dbl(center.lon),
      iconKey: str(z.iconKey),
      radiusMeters: dbl(z.radiusMeters),
      isPrivate: bool(z.isPrivate),
      notifyOnSpot: bool(z.notifyOnSpot),
      createdAt: int(nowMs - 80 * MS_DAY),
      updatedAt: int(nowMs - 80 * MS_DAY),
    },
  }
}

/** Las zonas no se inventan: se ponen donde el corpus aparca de verdad, por frecuencia. */
function clustersFrom(sessions) {
  const buckets = new Map()
  for (const s of sessions) {
    const lat = s.fields.latitude && s.fields.latitude.doubleValue
    const lon = s.fields.longitude && s.fields.longitude.doubleValue
    if (lat == null || lon == null) continue
    const k = lat.toFixed(3) + ',' + lon.toFixed(3)
    const b = buckets.get(k) || { n: 0, lat: 0, lon: 0 }
    b.n++
    b.lat += lat
    b.lon += lon
    buckets.set(k, b)
  }
  return [...buckets.values()]
    .sort((a, b) => b.n - a.n)
    .map((b) => ({ lat: b.lat / b.n, lon: b.lon / b.n, n: b.n }))
}

/** Reparto por cuotas estable: la misma sesion cae siempre en el mismo coche. */
function vehicleFor(key) {
  const u = stableUnit('veh|' + key)
  let acc = 0
  for (const v of VEHICLES) {
    acc += v.share
    if (u < acc) return v
  }
  return VEHICLES[0]
}

// --- Main ---------------------------------------------------------------------
const DRY = process.argv.includes('--dry-run')

TOKEN = await accessToken()
const nowMs = Date.now()

for (const v of VEHICLES) if (!v.id) v.id = stableUuid(v.key)

console.log('> Leyendo el corpus real de ' + SRC_UID + ' en (default)...')
const src = await listCollection('users/' + SRC_UID, 'parkingHistory', null, SRC_ROOT)
src.sort((a, b) => (readInt(a.fields, 'timestamp') || 0) - (readInt(b.fields, 'timestamp') || 0))
const day = (ms) => new Date(ms).toISOString().slice(0, 10)
console.log('  ' + src.length + ' sesiones, de ' + day(readInt(src[0].fields, 'timestamp')) +
  ' a ' + day(readInt(src[src.length - 1].fields, 'timestamp')))

// La mas reciente es la que se convierte en la sesion VIVA de la cuenta de demo.
const activeSource = src[src.length - 1]
const writes = []
const newIds = new Set()

for (const v of VEHICLES) writes.push({ update: vehicleDoc(v, nowMs) })

for (const w of WINDOWS) {
  let n = 0
  for (const s of src) {
    const srcId = s.name.split('/').pop()
    if (w.keep < 1 && stableUnit('keep|' + w.key + '|' + srcId) > w.keep) continue
    const id = stableUuid(w.key + '|' + srcId)
    const isActive = w.key === 'w0' && s === activeSource
    // Jitter reproducible de +-5 h, para que dos meses clonados no calquen la hora exacta.
    const jitter = w.shiftDays === 0
      ? 0
      : Math.round((stableUnit('jit|' + w.key + '|' + srcId) - 0.5) * 10 * MS_HOUR)
    writes.push({
      update: sessionDoc(s.fields, {
        id,
        vehicle: isActive ? VEHICLES[0] : vehicleFor(w.key + '|' + srcId),
        shiftMs: w.shiftDays * MS_DAY + jitter,
        nowMs,
        active: isActive,
      }),
    })
    newIds.add(id)
    n++
  }
  console.log('  ventana ' + w.key + ' (' + w.shiftDays + ' d): ' + n + ' sesiones')
}

const clusters = clustersFrom(src)
for (const z of ZONES) {
  const c = clusters[z.cluster]
  if (!c) {
    console.log('  ! sin cluster ' + z.cluster + ' para la zona ' + z.name + ': se omite')
    continue
  }
  writes.push({ update: zoneDoc(z, c, nowMs) })
  console.log('  zona ' + z.name + ' -> ' + c.lat.toFixed(5) + ',' + c.lon.toFixed(5) +
    ' (' + c.n + ' aparcamientos dentro)')
}

// Lo que sobra de siembras anteriores (o de la cuenta original) se borra: idempotencia.
const existing = await listCollection('users/' + DST_UID, 'parkingHistory', ['timestamp'])
const stray = existing.map((d) => d.name.split('/').pop()).filter((id) => !newIds.has(id))
for (const id of stray) writes.push({ delete: DOCS + '/users/' + DST_UID + '/parkingHistory/' + id })
console.log('  ' + stray.length + ' sesiones previas a borrar . ' + writes.length + ' escrituras en total')

if (DRY) {
  const out = join(process.cwd(), 'demoseed-plan.' + DATABASE.replace(/[()]/g, '') + '.json')
  writeFileSync(out, JSON.stringify(writes, null, 2), 'utf8')
  console.log('= dry-run: plan escrito en ' + out + '. No se ha tocado Firestore.')
} else {
  console.log('> Escribiendo en ' + PROJECT + ' / ' + DATABASE + '...')
  await commit(writes)
  console.log('= Hecho.')
}
