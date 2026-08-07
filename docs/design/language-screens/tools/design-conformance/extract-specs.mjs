// Extract the EXACT computed layout of every rev5 design frame — browser-computed,
// not eyeballed. Output: docs/design/language-screens/specs/<id>.json (the golden specs
// that drive building AND automated conformance verification).
//
// Run:  cd docs/design/language-screens/tools/design-conformance && node extract-specs.mjs
// (needs playwright + chromium: `npm i playwright && npx playwright install chromium`)
//
// WHY: implementations kept diverging from rev5 (blue-not-green cards, square-not-round
// rows, wrong margins) because specs were eyeballed off the picture. The browser computes
// the real layout — every position, size, margin, padding, radius, colour — so the values
// are exact and nothing is missed. This is the "correctly extract the design specs" step
// of the zero-touch design-conformance architecture.

import { chromium } from 'playwright';
import { writeFileSync, mkdirSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dir = dirname(fileURLToPath(import.meta.url));
const SPEC = join(__dir, '..', '..', 'language-screens-spec.html');
const OUT = join(__dir, '..', '..', 'specs');

// every dv-opt frame group in the spec
const FRAMES = ['15a','15b','16a','17a','17b','17c','17d','18a','18b',
  '19a','19b','19d','19f','19g','19h','19m','19n',
  '20a','20b','20c','20d','20e','20f','21a','21b','21c'];

// runs IN the page for one frame id -> its exact element spec (identical logic to the
// browser-MCP prototype that was validated on 20d).
function extractInPage(id) {
  const group = document.querySelector('[id="' + id + '"]');
  if (!group) return null;
  const groupEls = [...group.querySelectorAll('*')];
  // Device frame = the LIGHT device screen: the largest element with a non-transparent
  // background + a border-radius + overflow:hidden (the screen fill that clips its content).
  // The side-by-side light/dark WRAPPERS are transparent; nested cards are smaller; light
  // precedes dark in DOM so the strict `>` keeps the light frame on an area tie.
  let frame = group, best = 0;
  for (const el of groupEls) {
    const cs = getComputedStyle(el), r = el.getBoundingClientRect();
    if (cs.overflow !== 'hidden' || cs.borderRadius === '0px' || cs.backgroundColor === 'rgba(0, 0, 0, 0)') continue;
    const area = r.width * r.height;
    if (area > best && r.width > 100 && r.height > 80) { best = area; frame = el; }
  }
  const fr = frame.getBoundingClientRect();
  // Walk the FRAME's descendants only — the light-frame content. This excludes the dark-twin
  // frame and the caption labels, which are SIBLINGS of the frame, not descendants.
  const all = [...frame.querySelectorAll('*')];
  const px = v => Math.round(v * 10) / 10;
  const hex = c => { if (!c || c === 'rgba(0, 0, 0, 0)') return null; const m = c.match(/[\d.]+/g); if (!m) return null; return '#' + m.slice(0, 3).map(n => Math.round(+n).toString(16).padStart(2, '0')).join(''); };
  const spx = s => s.replace(/(\d+(\.\d+)?)px/g, (_, n) => Math.round(+n));
  const out = [];
  for (const el of all) {
    const r = el.getBoundingClientRect(); if (r.width < 1 || r.height < 1) continue;
    const cs = getComputedStyle(el), leaf = el.children.length === 0;
    const txt = leaf ? el.textContent.trim() : '';
    const bg = hex(cs.backgroundColor);
    const rad = cs.borderRadius !== '0px' ? spx(cs.borderRadius) : null;
    const pad = cs.padding !== '0px' ? spx(cs.padding) : null;
    const gap = (cs.gap && cs.gap !== 'normal' && cs.gap !== '0px') ? spx(cs.gap) : null;
    const mt = parseFloat(cs.marginTop), ml = parseFloat(cs.marginLeft), mr = parseFloat(cs.marginRight), mb = parseFloat(cs.marginBottom);
    const m = (mt || ml || mr || mb) ? `${Math.round(mt)} ${Math.round(mr)} ${Math.round(mb)} ${Math.round(ml)}` : null;
    if (!txt && !bg && !rad && !gap) continue;
    const rec = { t: txt.slice(0, 40) || undefined, x: px(r.left - fr.left), y: px(r.top - fr.top), w: px(r.width), h: px(r.height),
      bg, fg: leaf && txt ? hex(cs.color) : undefined, r: rad || undefined, p: pad || undefined, g: gap || undefined,
      f: leaf && txt ? `${parseFloat(cs.fontSize)}/${cs.fontWeight}` : undefined, m: m || undefined,
      ls: cs.letterSpacing !== 'normal' ? cs.letterSpacing : undefined };
    out.push(Object.fromEntries(Object.entries(rec).filter(([, v]) => v !== undefined)));
  }
  out.sort((a, b) => a.y - b.y || a.x - b.x);
  return { id, frame: { w: px(fr.width), h: px(fr.height) }, n: out.length, el: out };
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1700, height: 1400 } });
  await page.goto('file://' + SPEC, { waitUntil: 'load' });
  await page.waitForTimeout(1200); // let the bundler template render into the DOM
  mkdirSync(OUT, { recursive: true });
  let ok = 0;
  for (const id of FRAMES) {
    const spec = await page.evaluate(extractInPage, id);
    if (!spec) { console.log(id.padEnd(4), 'NOT FOUND'); continue; }
    writeFileSync(join(OUT, id + '.json'), JSON.stringify(spec, null, 1));
    console.log(id.padEnd(4), String(spec.n).padStart(3), 'elements ·', spec.frame.w + '×' + spec.frame.h);
    ok++;
  }
  await browser.close();
  console.log('\nwrote', ok, 'golden specs to', OUT);
})();
