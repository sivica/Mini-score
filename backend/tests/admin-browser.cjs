// Real-browser regressions against isolated SQLite fixtures, never Railway.
const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const {mkdtemp, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const {createServer} = require('node:net');
const {chromium} = require('playwright');

let browser;
before(async () => {
  browser = await chromium.launch({headless: true, executablePath: process.env.CHROME_PATH || undefined});
});
after(async () => {await browser?.close();});

async function fixture(t) {
  const dir = await mkdtemp(join(tmpdir(), 'miniscore-admin-test-'));
  const socket = createServer();
  await new Promise(done => socket.listen(0, '127.0.0.1', done));
  const port = socket.address().port;
  await new Promise(done => socket.close(done));
  const program = `
import uvicorn
import backend.app as module
def imported(previous, today, *, finished_only):
    assert finished_only is True
    game = next(g for g in previous['games'] if g['id'] == previous['defaultGameId'])
    game['venue'] = 'New provider arena'
    game['away']['players'][0]['stats']['rebounds'] += 1
    game['away']['players'].reverse()
    return previous
module.build_feed = imported
uvicorn.run(module.create_app(scheduler=False), host='127.0.0.1', port=${port}, log_level='warning')
`;
  const server = spawn(process.env.PYTHON || 'python', ['-c', program], {
    cwd: resolve(__dirname, '../..'),
    env: {...process.env, DATABASE_URL: `sqlite:///${join(dir, 'test.db')}`,
      ADMIN_USERNAME: 'review', ADMIN_PASSWORD: 'isolated-browser-test-password'},
    stdio: ['ignore', 'ignore', 'pipe'],
  });
  let logs=''; server.stderr.on('data', chunk => logs += chunk);
  const context = await browser.newContext({httpCredentials: {username: 'review', password: 'isolated-browser-test-password'}});
  t.after(async () => {
    await context.close();
    server.kill('SIGTERM');
    if(server.exitCode===null) await new Promise(done => server.once('exit', done));
    await rm(dir, {recursive:true, force:true});
  });
  const url = `http://127.0.0.1:${port}`;
  let ready=false;
  for(let attempt=0;attempt<100;attempt++) {
    if(server.exitCode!==null) throw Error(logs || 'Local server exited');
    try {ready=(await fetch(`${url}/health`)).ok;} catch {}
    if(ready) break;
    await new Promise(done => setTimeout(done, 50));
  }
  assert.ok(ready, logs);
  const page=await context.newPage(), errors=[];
  page.on('pageerror', error => errors.push(error.message));
  t.after(() => assert.deepEqual(errors, []));
  await page.goto(`${url}/admin`);
  await page.waitForFunction(() => document.querySelector('#message').textContent.startsWith('Ready'));
  const state=async () => (await context.request.get(`${url}/admin/state`)).json();
  const waitMessage=async text => page.waitForFunction(text => document.querySelector('#message').textContent.startsWith(text), text);
  return {page, context, url, state, waitMessage};
}

test('default and clear warn about unsaved edits, including invalid numbers', async t => {
  const {page,state,waitMessage}=await fixture(t), initial=await state();
  await page.locator('#field-venue').fill('Unsaved arena');
  // Dirty detection must work even when collect() would reject an empty score.
  await page.locator('#field-away-score').fill('');
  const dialogs=[];
  const cancel=async dialog => {dialogs.push(dialog.message());await dialog.dismiss();};
  page.on('dialog',cancel);
  await page.click('#default');
  await page.waitForFunction(() => !document.querySelector('#default').disabled);
  await page.getByText('Manual corrections', {exact:true}).click();
  await page.click('#clear');
  await page.waitForFunction(() => !document.querySelector('#clear').disabled);
  assert.equal((await state()).revision, initial.revision);
  assert.equal(await page.locator('#field-venue').inputValue(),'Unsaved arena');
  assert.equal(await page.locator('#field-away-score').inputValue(),'');
  assert.equal(dialogs.length,2);
  assert.ok(dialogs.every(text => /unsaved/i.test(text)));
  page.off('dialog',cancel);
  page.once('dialog',dialog => dialog.accept());
  await page.click('#default'); await waitMessage('Default game updated.');
  assert.equal((await state()).revision,initial.revision+1);
  assert.notEqual(await page.locator('#field-venue').inputValue(),'Unsaved arena');
});

test('recover saved edits after import without replacing new stats or roster order', async t => {
  const {page,state,waitMessage,url}=await fixture(t), initial=await state();
  const gid=initial.published.defaultGameId, old=initial.published.games.find(g=>g.id===gid), pid=old.away.players[0].id;
  await page.locator('#field-venue').fill('Saved arena');
  const assist=page.locator('input[aria-label="'+old.away.players[0].name+' AST"]');
  await assist.fill(String(old.away.players[0].stats.assists+1));
  await page.click('#save');await waitMessage('Draft saved.');
  assert.deepEqual((await state()).published,initial.published);
  await page.click('#import');await waitMessage('Stats imported.');
  assert.equal(await page.locator('#field-venue').inputValue(),'New provider arena');
  await page.click('#review-draft');
  assert.match(await page.locator('#draft-comparison').textContent(),/Saved arena/);
  assert.match(await page.locator('#draft-comparison').textContent(),/New provider arena/);
  await page.click('#recover-draft');
  assert.equal(await page.locator('#field-venue').inputValue(),'Saved arena');
  await page.click('#save');await waitMessage('Draft saved.');
  const after=await state(), saved=after.drafts[gid].game, current=after.published.games.find(g=>g.id===gid);
  assert.deepEqual(saved.away.players.map(p=>p.id),current.away.players.map(p=>p.id));
  const player=saved.away.players.find(p=>p.id===pid);
  assert.equal(player.stats.assists,old.away.players[0].stats.assists+1);
  assert.equal(player.stats.rebounds,old.away.players[0].stats.rebounds+1);
  assert.equal(current.venue,'New provider arena');
  // A concurrent publication must still reject a recovered editor.
  const response=await page.request.post(`${url}/admin/default`,{data:{gameId:gid,revision:after.revision},headers:{'X-Miniscore-Admin':'1'}});
  assert.equal(response.status(),200);
  await page.click('#publish');await waitMessage('The games changed.');
  assert.equal(await page.locator('#field-venue').inputValue(),'Saved arena');
  assert.equal((await state()).published.games.find(g=>g.id===gid).venue,'New provider arena');
});

test('legacy drafts are recoverable and accidental replacement can be cancelled', async t => {
  const {page,waitMessage}=await fixture(t);
  await page.locator('#field-venue').fill('Legacy saved arena');
  await page.click('#save');await waitMessage('Draft saved.');
  await page.route('**/admin/state',async route => {
    const response=await route.fetch(), data=await response.json();
    for(const draft of Object.values(data.drafts)) delete draft.base;
    await route.fulfill({response,json:data});
  });
  await page.click('#default');await waitMessage('Default game updated.');
  page.once('dialog',dialog => dialog.dismiss());
  await page.click('#save');
  await page.waitForFunction(() => !document.querySelector('#save').disabled);
  await page.click('#review-draft');
  assert.match(await page.locator('#draft-note').textContent(),/no base snapshot/);
  assert.match(await page.locator('#draft-comparison').textContent(),/Legacy saved arena/);
  await page.click('#recover-draft');
  assert.equal(await page.locator('#field-venue').inputValue(),'Legacy saved arena');
  await page.click('#publish');await waitMessage('Published.');
});

test('two editor tabs cannot overwrite a draft and the rejected tab keeps its edits', async t => {
  const {page,state,waitMessage,url}=await fixture(t);
  const other=await page.context().newPage();
  await other.goto(`${url}/admin`);
  await other.waitForFunction(() => document.querySelector('#message').textContent.startsWith('Ready.'));
  await page.locator('#field-venue').fill('First saved venue');
  await other.locator('#field-venue').fill('Other unsaved venue');
  await page.click('#save');await waitMessage('Draft saved.');
  const before=await state();
  for(const action of ['save','publish']) {
    await other.click('#'+action);
    await other.waitForFunction(() => document.querySelector('#message').textContent.includes('saved draft changed'));
    await other.waitForFunction(() => !document.querySelector('#save').disabled);
    assert.equal(await other.locator('#field-venue').inputValue(),'Other unsaved venue');
    assert.deepEqual(await state(),before);
  }
  await other.reload();
  await other.waitForFunction(() => !document.querySelector('#save').disabled);
  assert.equal(await other.locator('#field-venue').inputValue(),'First saved venue');
  await other.locator('#field-venue').fill('Reviewed new venue');
  await other.click('#save');
  await other.waitForFunction(() => document.querySelector('#message').textContent.startsWith('Draft saved.'));
  assert.equal((await state()).drafts[before.published.defaultGameId].game.venue,'Reviewed new venue');
});

test('recovery reports removed players and retains new roster members', async t => {
  const {page,state}=await fixture(t), initial=await state();
  const current=structuredClone(initial.published.games.find(g=>g.id===initial.published.defaultGameId));
  const base=structuredClone(current), draft=structuredClone(base), removed=base.away.players[0].id;
  draft.away.players[0].stats.assists+=1;
  current.away.players.shift();
  const incoming=structuredClone(current.away.players[0]); incoming.id='new-player'; current.away.players.push(incoming);
  const recovered=await page.evaluate(({current,base,draft})=>recoverDraft(current,{base,game:draft}),{current,base,draft});
  assert.deepEqual(recovered.game,current);
  assert.equal(recovered.changes.length,1);
  assert.equal(recovered.changes[0].applicable,false);
  assert.ok(recovered.changes[0].path.includes(removed));
});
