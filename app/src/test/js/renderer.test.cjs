const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/assets/visualization/renderer.js'), 'utf8');

function runtime() {
  const messages = [];
  const make = tag => ({ tag, textContent: '', children: [], style: {}, append(...nodes) { this.children.push(...nodes); }, appendChild(node) { this.children.push(node); }, replaceChildren() { this.children = []; } });
  const nodes = Object.fromEntries(['title', 'summary', 'chart'].map(id => [id, make('div')]));
  const window = { rendererEvents: { postMessage(raw) { messages.push(JSON.parse(raw)); } } };
  vm.runInNewContext(source, { window, document: { createElement: make, getElementById(id) { return nodes[id]; } }, requestAnimationFrame(callback) { callback(); } });
  window.bootstrap('native-token');
  return { window, nodes, messages };
}
const row = (overrides = {}) => ({ playerId: 'a', teamId: 'MEM', name: 'Player', points: 0, rebounds: null, fgMade: 2, fgAttempted: 5, fgMissed: 3, fgPercent: 40, ...overrides });
const payload = (players, template = 'playerStats', metric = 'points') => ({ spec: { template, metric }, players, weightedFgPercent: 40 });

test('labels remain literal text and native order/filter is preserved', () => {
  const r = runtime();
  const name = '<img src=x onerror=alert(1)>';
  r.window.render(payload([row({ name }), row({ name: 'Second', points: 9 })]), 'native-token', 1);
  assert.equal(r.nodes.chart.children.length, 2);
  assert.equal(r.nodes.chart.children[0].children[0].textContent, name + ' · MEM');
  assert.equal(r.nodes.chart.children[1].children[0].textContent, 'Second · MEM');
  assert.equal(r.messages.at(-1).kind, 'ready');
});
test('unknown statistics stay distinct from zero', () => {
  const r = runtime();
  r.window.render(payload([row({ points: null }), row({ points: 0 })]), 'native-token', 1);
  assert.equal(r.nodes.chart.children[0].children[2].textContent, 'Unknown');
  assert.equal(r.nodes.chart.children[0].children[1].children.length, 0);
  assert.equal(r.nodes.chart.children[1].children[2].textContent, '0');
});
test('field goals use native derived made/missed/percentage and weighted percentage', () => {
  const r = runtime();
  r.window.render(payload([row()], 'fieldGoals'), 'native-token', 1);
  assert.equal(r.nodes.chart.children[0].children[2].textContent, 'Made 2 · Attempts 5 · Missed 3 · FG% 40.0%');
  assert.equal(r.nodes.chart.children[0].children[1].children[0].style.width, '40%');
  assert.equal(r.nodes.chart.children[0].children[1].children[1].style.width, '60%');
  assert.match(r.nodes.summary.textContent, /Combined FG%: 40.0%/);
});
test('zero attempts show N/A including unavailable made; null counts show Unknown', () => {
  const r = runtime();
  const p = payload([row({ fgMade: null, fgAttempted: 0, fgMissed: null, fgPercent: null })], 'fieldGoals');
  p.weightedFgPercent = null;
  r.window.render(p, 'native-token', 1);
  assert.equal(r.nodes.chart.children[0].children[2].textContent, 'Made Unknown · Attempts 0 · Missed Unknown · FG% N/A');
  assert.equal(r.nodes.chart.children[0].children[1].children.length, 0);
  assert.match(r.nodes.summary.textContent, /Combined FG%: N\/A/);
  p.players = [row({ fgMade: null, fgAttempted: null, fgMissed: null, fgPercent: null })];
  r.window.render(p, 'native-token', 2);
  assert.match(r.nodes.chart.children[0].children[2].textContent, /FG% Unknown/);
  assert.match(r.nodes.summary.textContent, /Combined FG%: Unknown/);
});
test('stale or wrong-generation calls never replace the accepted chart', () => {
  const r = runtime();
  r.window.render(payload([row({ name: 'Accepted' })]), 'native-token', 2);
  r.window.render(payload([row({ name: 'Stale' })]), 'native-token', 1);
  r.window.render(payload([row({ name: 'Forged' })]), 'other-token', 3);
  assert.match(r.nodes.chart.children[0].children[0].textContent, /^Accepted/);
  assert.equal(r.messages.at(-1).sequence, 2);
});
test('both bounded templates render the eighty-player limit without HTML generation', () => {
  for (const template of ['playerStats', 'fieldGoals']) {
    const r = runtime();
    r.window.render(payload(Array.from({ length: 80 }, (_, i) => row({ name: `Player ${i}` })), template), 'native-token', 1);
    assert.equal(r.nodes.chart.children.length, 80);
    assert.equal(r.messages.at(-1).kind, 'ready');
  }
  assert.doesNotMatch(source, /innerHTML|outerHTML|eval\(|new Function/);
});
