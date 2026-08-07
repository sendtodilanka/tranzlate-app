// Reconcile the MEASURED specs (specs/*.json, from extract-specs.mjs) against the STATED
// design-system VALUES (stated-spec.json). Owner rule (2026-08-07): the SSOT states the
// values -> use them; measure only the unstated; where measured conflicts with stated, the
// STATED value wins. This script REPORTS the divergences, so a build never bakes in a
// mockup-rendering artifact (e.g. a 24sp title where the SSOT states 22sp) as if it were spec.
//
// Run:  cd docs/design/language-screens/tools/design-conformance && node reconcile.mjs [frameId]
//
// This is a DIAGNOSTIC, not the gate. Honest limits (co-verify #350):
//  - FONT: sizes outside the 4 stated roles are listed, but SOME are legitimate UNSTATED
//    roles (detail-pane headline/display, metric numbers) and SOME are genuine mockup
//    imprecision (14.5, 12.5). This script cannot tell which — it is role-blind. The gate
//    (Component 2) does the role-precise check. Material-Symbols icon glyphs (`ic:1`) are
//    excluded here (their size is not type-scale text).
//  - COLOUR: specs colours are getComputedStyle() CSS values (exact, not AA-sampled), so a
//    colour >tol from EVERY token in `colors`+`extensions` is a real divergence. Ad-mock
//    greys (21a/21c) will surface — ads (#139) are out of scope.
//  - ROW-HEIGHT: the `w > frame.w*0.55` heuristic only sees full-width rows; on list-DETAIL
//    frames (17d/20d) the list pane is ~30% wide, so its rows are NOT caught here. Real row
//    heights on those frames are the gate's job, not this heuristic's.

import { readFileSync, readdirSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dir = dirname(fileURLToPath(import.meta.url));
const stated = JSON.parse(readFileSync(join(__dir, 'stated-spec.json'), 'utf8'));
const SPECS = join(__dir, '..', '..', 'specs');
const only = process.argv[2];

const STATED_FONTS = [stated.type.appBarSp, stated.type.rowSp, stated.type.bodySp, stated.type.overlineSp];
const STATED_ROWS = [stated.metrics.compactRowHeightDp, ...stated.metrics.listRowHeightDp]; // 48, 56, 60
const COLOUR_TOL = 6; // RGB-sum; absorbs hex rounding only (values are exact CSS, not AA pixels)

// Flatten the full palette (every ColorScheme role, both themes, + extensions) to {hex, label}
const palette = [];
for (const theme of ['light', 'dark']) {
  for (const [role, hex] of Object.entries(stated.colors[theme])) {
    if (role.startsWith('_')) continue;
    palette.push({ hex: hex.toLowerCase(), label: `${role} ${theme}` });
  }
}
for (const [role, byTheme] of Object.entries(stated.extensions || {})) {
  if (role.startsWith('_')) continue;
  for (const [theme, hex] of Object.entries(byTheme)) palette.push({ hex: hex.toLowerCase(), label: `${role} ${theme} (ext)` });
}
const toRgb = (h) => { h = h.replace('#', ''); if (h.length === 3) h = h.split('').map((c) => c + c).join(''); return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)); };
const dist = (a, b) => { const x = toRgb(a), y = toRgb(b); return Math.abs(x[0] - y[0]) + Math.abs(x[1] - y[1]) + Math.abs(x[2] - y[2]); };
const nearest = (hex) => palette.reduce((best, p) => { const d = dist(hex, p.hex); return d < best.d ? { ...p, d } : best; }, { d: 1e9 });
const nearestFont = (n) => STATED_FONTS.reduce((best, s) => { const d = Math.abs(n - s); return d < best.d ? { s, d } : best; }, { d: 1e9 });

const files = readdirSync(SPECS).filter((f) => f.endsWith('.json') && (!only || f === only + '.json')).sort();
let totFont = 0, totColour = 0, totRow = 0;

for (const f of files) {
  const spec = JSON.parse(readFileSync(join(SPECS, f), 'utf8'));
  const fontConf = new Map();   // measured size -> {ex, near}
  const colourConf = new Map(); // measured hex  -> {ex, near}
  const rowH = new Map();       // row-candidate height -> count
  for (const el of spec.el) {
    if (el.f && !el.ic) { // exclude Material-Symbols icon glyphs — their size is not type-scale
      const sz = parseFloat(el.f.split('/')[0]);
      const nf = nearestFont(sz);
      if (nf.d > 0.01 && !fontConf.has(sz)) fontConf.set(sz, { ex: el.t, near: nf.s });
    }
    for (const key of ['bg', 'fg']) {
      const c = el[key];
      if (!c) continue;
      const n = nearest(c.toLowerCase());
      if (n.d > COLOUR_TOL && !colourConf.has(c.toLowerCase())) colourConf.set(c.toLowerCase(), { ex: el.t || `(${key})`, near: n });
    }
    // row-candidate = wide, short, carries text or a fill (see header: misses list-detail panes)
    if ((el.t || el.bg) && el.w > spec.frame.w * 0.55 && el.h >= 20 && el.h < 100) rowH.set(el.h, (rowH.get(el.h) || 0) + 1);
  }
  const rowConf = [...rowH.entries()].filter(([h]) => !STATED_ROWS.includes(h)).sort((a, b) => b[1] - a[1]);
  if (!fontConf.size && !colourConf.size && !rowConf.length) continue;

  console.log(`\n=== ${spec.id}  (${spec.frame.w}x${spec.frame.h}, ${spec.n} el) ===`);
  if (fontConf.size) {
    console.log(`  FONT — non-icon sizes not in stated {${STATED_FONTS.join(', ')}}sp (role-blind — some are legit unstated roles):`);
    for (const [sz, v] of [...fontConf].sort((a, b) => b[0] - a[0])) { console.log(`    ${sz}sp -> nearest stated ${v.near}sp   e.g. "${(v.ex || '').slice(0, 30)}"`); totFont++; }
  }
  if (colourConf.size) {
    console.log(`  COLOUR — measured hexes >${COLOUR_TOL} from every token in colors+extensions:`);
    for (const [hex, v] of colourConf) { console.log(`    ${hex} -> nearest ${v.near.label} ${v.near.hex} (d=${v.near.d})   e.g. "${(v.ex || '').slice(0, 24)}"`); totColour++; }
  }
  if (rowConf.length) {
    console.log(`  ROW-HEIGHT full-width candidates not in stated {${STATED_ROWS.join(', ')}}dp:`);
    for (const [h, c] of rowConf) { console.log(`    ${h}dp x${c}`); totRow++; }
  }
}
console.log(`\n---\nDivergences: ${totFont} font, ${totColour} colour, ${totRow} row-height  across ${files.length} frame(s).`);
console.log('Rule: STATED wins. Build to stated-spec.json; measure only the unstated. (Diagnostic — the gate does the role-precise check.)');
