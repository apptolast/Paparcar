/**
 * Headless audit of the built site: horizontal overflow, console errors, touch-target sizes and
 * heading order, measured at real viewport widths.
 *
 * Screenshots show you a page looks right; they do not tell you the layout is 40px wider than the
 * phone it is being read on — that one hides as "the text is cut off" in a capture. This drives
 * Chrome over the DevTools Protocol and asks the live document.
 *
 *   node web/tools/audit.mjs [baseUrl] [--keep]
 *
 * Expects the preview server (`python web/tools/serve.py`) to be running.
 */
import { spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';

const BASE = process.argv[2]?.startsWith('http') ? process.argv[2] : 'http://localhost:5173';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 9333;

const WIDTHS = [320, 375, 390, 430, 768, 1024, 1366, 1440, 1920];
const PATHS = ['/', '/support', '/terms', '/en/', '/privacy-policy', '/delete-account'];

const chrome = spawn(
  CHROME,
  [
    '--headless=new',
    '--disable-gpu',
    `--remote-debugging-port=${PORT}`,
    '--remote-allow-origins=*',
    '--no-first-run',
    '--user-data-dir=' + process.env.TEMP + '/paparcar-audit-profile',
    'about:blank',
  ],
  { stdio: 'ignore' },
);

let ws;
let nextId = 1;
const pending = new Map();

function send(method, params = {}, sessionId) {
  const id = nextId++;
  ws.send(JSON.stringify({ id, method, params, sessionId }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

async function connect() {
  for (let i = 0; i < 60; i++) {
    try {
      const list = await fetch(`http://127.0.0.1:${PORT}/json/list`).then((r) => r.json());
      const target = list.find((t) => t.type === 'page');
      if (target) return target.webSocketDebuggerUrl;
    } catch {
      /* not up yet */
    }
    await sleep(250);
  }
  throw new Error('chrome devtools never came up');
}

/** Runs in the page: everything we want to know about one rendering. */
const PROBE = `(() => {
  const vw = window.innerWidth;
  const out = { vw, docWidth: document.documentElement.scrollWidth, offenders: [], small: [], headings: [] };
  if (out.docWidth > vw + 1) {
    for (const el of document.querySelectorAll('body *')) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      if (r.right > vw + 1 || r.left < -1) {
        const cls = (el.getAttribute('class') || '').split(' ').slice(0, 2).join('.');
        out.offenders.push(el.tagName + (cls ? '.' + cls : '') + ' [' + Math.round(r.left) + '→' + Math.round(r.right) + ']');
        if (out.offenders.length > 14) break;
      }
    }
  }
  for (const el of document.querySelectorAll('a[href], button, summary')) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    // WCAG 2.5.8 exempts a target that sits INSIDE a sentence: an inline link in a paragraph is
    // sized by the line box, and padding it out would wreck the prose it lives in. The rule is for
    // standalone controls — nav items, buttons, list links, disclosures.
    const inline = getComputedStyle(el).display === 'inline' &&
      /^(P|LI|TD|SPAN|STRONG|EM)$/.test(el.parentElement?.tagName || '');
    if (inline) continue;
    if (r.height < 32 || r.width < 24) {
      out.small.push((el.textContent || el.getAttribute('aria-label') || el.tagName).trim().slice(0, 34) +
        ' ' + Math.round(r.width) + 'x' + Math.round(r.height));
    }
  }
  let prev = 0;
  for (const h of document.querySelectorAll('h1, h2, h3, h4')) {
    const level = Number(h.tagName[1]);
    if (prev && level > prev + 1) out.headings.push('jump h' + prev + '→h' + level + ': ' + h.textContent.trim().slice(0, 40));
    prev = level;
  }
  out.h1 = document.querySelectorAll('h1').length;
  out.imgsNoAlt = [...document.images].filter((i) => !i.hasAttribute('alt')).length;
  out.lang = document.documentElement.lang;
  out.title = document.title.length;
  out.description = (document.querySelector('meta[name=description]') || {}).content?.length || 0;
  return out;
})()`;

async function main() {
  const url = await connect();
  ws = new WebSocket(url);
  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
      console.log(`    console error: ${msg.params.args.map((a) => a.value).join(' ')}`);
    } else if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') {
      console.log(`    page error: ${msg.params.entry.text}`);
    }
  });
  await new Promise((r) => ws.addEventListener('open', r, { once: true }));

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');

  let failures = 0;

  for (const path of PATHS) {
    console.log(`\n${path}`);
    for (const width of WIDTHS) {
      await send('Emulation.setDeviceMetricsOverride', {
        width, height: 900, deviceScaleFactor: 1, mobile: width < 700,
      });
      await send('Page.navigate', { url: BASE + path });
      await sleep(550);
      const { result } = await send('Runtime.evaluate', { expression: PROBE, returnByValue: true });
      const r = result.value;
      const bad = [];
      if (r.docWidth > r.vw + 1) bad.push(`overflow ${r.docWidth}>${r.vw} :: ${r.offenders.join(' | ')}`);
      if (r.small.length) bad.push(`small targets: ${r.small.join(', ')}`);
      if (r.headings.length) bad.push(r.headings.join('; '));
      if (r.h1 !== 1) bad.push(`h1 count ${r.h1}`);
      if (r.imgsNoAlt) bad.push(`${r.imgsNoAlt} img without alt`);
      if (!r.lang) bad.push('no lang attribute');
      if (r.title > 65) bad.push(`title ${r.title} chars`);
      if (r.description < 50 || r.description > 165) bad.push(`description ${r.description} chars`);

      if (bad.length) {
        failures++;
        console.log(`  ${String(width).padStart(4)}  ✗ ${bad.join('\n        ')}`);
      } else {
        console.log(`  ${String(width).padStart(4)}  ok`);
      }
    }
  }

  ws.close();
  chrome.kill();
  if (failures) {
    console.log(`\n${failures} viewport(s) with findings`);
    process.exitCode = 1;
  } else {
    console.log('\nall viewports clean');
  }
}

main().catch((err) => {
  console.error(err);
  chrome.kill();
  process.exitCode = 1;
});
