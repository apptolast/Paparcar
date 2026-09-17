/**
 * Full-page screenshots of the built site at real device widths, over the DevTools Protocol.
 *
 * `chrome --screenshot --window-size=390,…` does NOT give you a 390px-wide render on Windows: the
 * browser window has a minimum width and the capture is simply cropped, which reads as "the copy
 * is cut off" when the layout is in fact fine. Device-metrics emulation is the only way to see
 * what a phone sees.
 *
 *   node web/tools/shots.mjs <outDir> [baseUrl]
 */
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';

const OUT = process.argv[2];
const BASE = process.argv[3] ?? 'http://localhost:5173';
if (!OUT) throw new Error('usage: node web/tools/shots.mjs <outDir> [baseUrl]');

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 9334;

// dpr stays at 1 even for the phone shots: a full-page capture of a 15 000 px layout at dpr 2 is a
// 30 000 px PNG, and encoding it takes longer than the whole rest of the run. The layout under test
// is identical either way — what matters is the CSS width, which device emulation sets.
const SHOTS = [
  { name: 'home-390', path: '/', width: 390, dpr: 1, mobile: true },
  { name: 'home-768', path: '/', width: 768, dpr: 1, mobile: true },
  { name: 'home-1440', path: '/', width: 1440, dpr: 1, mobile: false },
  { name: 'en-390', path: '/en/', width: 390, dpr: 1, mobile: true },
  { name: 'support-390', path: '/support', width: 390, dpr: 1, mobile: true },
  { name: 'terms-1440', path: '/terms', width: 1440, dpr: 1, mobile: false },
  { name: 'privacy-390', path: '/privacy-policy', width: 390, dpr: 1, mobile: true },
  { name: 'privacy-1440', path: '/privacy-policy', width: 1440, dpr: 1, mobile: false },
  { name: 'delete-390', path: '/delete-account', width: 390, dpr: 1, mobile: true },
];

const chrome = spawn(
  CHROME,
  [
    '--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--hide-scrollbars', '--no-first-run',
    '--user-data-dir=' + process.env.TEMP + '/paparcar-shots-profile',
    'about:blank',
  ],
  { stdio: 'ignore' },
);

let ws;
let nextId = 1;
const pending = new Map();
const send = (method, params = {}) => {
  const id = nextId++;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
};

async function endpoint() {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await fetch(`http://127.0.0.1:${PORT}/json/list`).then((r) => r.json());
      const page = list.find((t) => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch { /* not up yet */ }
    await sleep(250);
  }
  throw new Error('devtools never came up');
}

try {
  await mkdir(OUT, { recursive: true });
  ws = new WebSocket(await endpoint());
  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    }
  });
  await new Promise((r) => ws.addEventListener('open', r, { once: true }));
  await send('Page.enable');

  for (const shot of SHOTS) {
    await send('Emulation.setDeviceMetricsOverride', {
      width: shot.width, height: 900, deviceScaleFactor: shot.dpr, mobile: shot.mobile,
    });
    await send('Page.navigate', { url: BASE + shot.path });
    await sleep(900);
    // Reveal-on-scroll only fires for what has been seen; a full-page capture has to walk the page
    // first or every section below the fold comes out blank.
    await send('Runtime.evaluate', {
      expression: `(async () => {
        const step = window.innerHeight * 0.8;
        for (let y = 0; y < document.body.scrollHeight; y += step) {
          window.scrollTo(0, y);
          await new Promise((r) => setTimeout(r, 40));
        }
        window.scrollTo(0, 0);
        // A full-page capture is one frame: anything the observer has not got round to yet would
        // photograph as an empty band. The reveal is decoration, so pinning it on for the capture
        // shows the page as a reader sees it, not as the scheduler happened to leave it.
        document.querySelectorAll('[data-reveal]').forEach((el) => el.classList.add('is-in'));
        // Screenshots below the fold are lazy-loaded on purpose, and the capture is a single
        // frame: without this they photograph as empty shells.
        // NOTE — no backticks inside this block: it is itself a template literal, and one
        // backtick in a comment ends the string and breaks the file.
        // img.decode() on an image the browser has not started fetching NEVER settles: it hangs
        // the whole run with no error. Force eager first, and race the wait against a deadline so
        // one stubborn image cannot take the tool down.
        document.querySelectorAll('img[loading="lazy"]').forEach((img) => { img.loading = 'eager'; });
        const deadline = new Promise((r) => setTimeout(r, 4000));
        await Promise.race([
          Promise.all([...document.images].map((img) => (img.complete ? null : img.decode().catch(() => {})))),
          deadline,
        ]);
      })()`,
      awaitPromise: true,
    });
    await sleep(500);
    const { data } = await send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: true, fromSurface: true,
    });
    await writeFile(join(OUT, `${shot.name}.png`), Buffer.from(data, 'base64'));
    console.log(`  ${shot.name}`);
  }
  ws.close();
} finally {
  chrome.kill();
}
