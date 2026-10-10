"use strict";
let state, selected, editing, baseRevision, baseDraftVersion, editorSnapshot, recoveredDraft=false;
const $ = id => document.getElementById(id);
const stats = {points:"PTS", rebounds:"REB", assists:"AST", fgMade:"FGM", fgAttempted:"FGA", minutes:"MIN", seconds:"SEC", steals:"STL", blocks:"BLK", turnovers:"TO"};
const copy = value => structuredClone(value);
function node(tag, text, className) { const el = document.createElement(tag); if(text !== undefined) el.textContent=text; if(className) el.className=className; return el; }
function message(text, error=false) { $("message").textContent=text; $("message").classList.toggle("error",error); }
function formSnapshot() {
  return JSON.stringify(Array.from(document.querySelectorAll("#fields [data-path]"),field=>
    [field.dataset.path,field.type==="checkbox"?field.checked:field.value]));
}
function hasUnsavedChanges() {return editing && formSnapshot()!==editorSnapshot;}
function confirmDiscard(text="Discard unsaved changes?") {return !hasUnsavedChanges() || confirm(text);}
async function api(path, data) {
  const response=await fetch(path,{credentials:"same-origin",headers:data?{"Content-Type":"application/json","X-Miniscore-Admin":"1"}:{},method:data?"POST":"GET",body:data?JSON.stringify(data):undefined});
  const result=await response.json(); if(!response.ok) throw Error(result.detail || "Request failed"); return result;
}
async function busy(action) {
  document.querySelectorAll("button").forEach(b=>b.disabled=true);
  try { await action(); } catch(error) {message(error.message,true);} finally {document.querySelectorAll("button").forEach(b=>b.disabled=false);}
}
async function load() {
  state=await api("/admin/state");
  $("status").textContent=`Revision ${state.revision} · Published ${new Date(state.updatedAt).toLocaleString()} · ${state.lastImport ? "Last import " + new Date(state.lastImport).toLocaleString() : "Bootstrap snapshot; no successful import yet"}${state.importing ? " · Import running" : ""}`;
  renderGames(); renderHistory();
  if(selected && state.published.games.some(g=>g.id===selected)) openGame(selected);
  else openGame(state.published.defaultGameId);
}
function renderGames() {
  const terms=$("search").value.trim().toLowerCase().split(/\s+/).filter(Boolean); $("games").replaceChildren();
  for(const game of state.published.games) {
    const text=`${game.away.name} ${game.home.name} ${game.date} ${game.status}`.toLowerCase(); if(!terms.every(t=>text.includes(t))) continue;
    const button=node("button",undefined,"game"+(selected===game.id?" selected":""));
    button.append(node("strong",`${game.away.shortName || game.away.name} at ${game.home.shortName || game.home.name}`),node("small",`${game.date} · ${game.status}${game.id===state.published.defaultGameId?" · Default":""}`));
    button.onclick=()=>{if(!confirmDiscard())return;openGame(game.id);}; $("games").append(button);
  }
}
function input(label,value,path,type="text",wide=false) {
  const wrap=node("div",undefined,wide?"wide":""); const field=node("input"); field.type=type; field.value=value ?? ""; field.dataset.path=JSON.stringify(path); field.id=`field-${path.join("-")}`;
  const title=node("label",label);title.htmlFor=field.id;wrap.append(title,field);return wrap;
}
function openGame(id, recovered) {
  selected=id; const current=state.published.games.find(g=>g.id===id); const draft=state.drafts[id];
  baseDraftVersion=state.draftVersions[id] || 0;
  const validDraft=draft && draft.revision===state.revision;
  editing=copy(recovered || (validDraft ? draft.game : current));baseRevision=state.revision;recoveredDraft=!!recovered; $("editor").hidden=false;
  $("title").textContent=`${current.away.shortName || current.away.name} at ${current.home.shortName || current.home.name}`;
  $("badge").textContent=Object.keys(state.corrections[id] || {}).length ? "Manual corrections" : "Provider data";
  $("draft-status").textContent=recovered ? "Draft edits loaded against the latest game. Review before saving or publishing." : validDraft ? "Saved draft loaded; it is not published yet." : draft ? "An older draft exists. Review it to recover your edits; the latest published game is shown." : "";
  $("review-draft").hidden=!draft || validDraft;
  const fields=$("fields");fields.replaceChildren();const grid=node("div",undefined,"grid");
  grid.append(input("Game date (NBA Eastern)",editing.date,["date"],"date"),input("Tipoff UTC",editing.tipoffUTC,["tipoffUTC"]),input("Competition",editing.competition,["competition"]),input("Venue",editing.venue,["venue"]));
  const status=node("select");status.dataset.path=JSON.stringify(["status"]);status.id="game-status";
  for(const value of ["SCHEDULED","LIVE","FINISHED","POSTPONED","CANCELLED"]){const option=node("option",value);option.value=value;status.append(option);}status.value=editing.status;
  const statusWrap=node("div");const label=node("label","Game status");label.htmlFor=status.id;statusWrap.append(label,status);grid.append(statusWrap);fields.append(grid);
  for(const side of ["away","home"]) {
    const team=editing[side], section=node("fieldset");section.append(node("legend",team.name));const teamGrid=node("div",undefined,"grid");
    teamGrid.append(input("Score",team.score,[side,"score"],"number"),input("Quarter scores, comma separated",team.quarters?.join(", ") || "",[side,"quarters"]),input("Display name",team.name,[side,"name"]),input("Short name",team.shortName,[side,"shortName"]),input("Team logo URL",team.logoUrl,[side,"logoUrl"],"url",true));section.append(teamGrid);
    if(team.players.length) {
      const scroll=node("div",undefined,"table-scroll"),table=node("table"),head=node("tr");for(const title of ["Player","#","Starter",...Object.values(stats)])head.append(node("th",title)); const thead=node("thead");thead.append(head);table.append(thead);const body=node("tbody");
      team.players.forEach((player,index)=>{
        const row=node("tr");
        for(const key of ["name","number","starter",...Object.keys(stats)]) {
          const cell=node("td"),field=node("input");const path=[side,"players",index,...(key in stats ? ["stats",key] : [key])];field.dataset.path=JSON.stringify(path);
          field.setAttribute("aria-label",`${player.name} ${key in stats ? stats[key] : key}`);
          if(key==="starter"){field.type="checkbox";field.checked=player.starter;}
          else {field.type=key in stats ? "number" : "text";field.value=key in stats ? player.stats[key] : player[key];field.className=key==="name"?"name":key==="number"?"jersey":"";if(key in stats){field.min="0";field.step="1";field.max=key==="seconds"?"59":key==="points"?"200":"100";}}
          cell.append(field);row.append(cell);
        }body.append(row);
      });table.append(body);scroll.append(table);section.append(scroll);
      const photos=node("details");photos.append(node("summary","Player photos"));team.players.forEach((p,i)=>photos.append(input(p.name,p.portraitUrl,[side,"players",i,"portraitUrl"],"url")));section.append(photos);
    } else section.append(node("p","No player stats yet. Import after the game starts."));
    fields.append(section);
  }
  renderGames();
  editorSnapshot=formSnapshot();
}
function leafValues(value,path=[],result=new Map()) {
  if(value && typeof value==="object" && !Array.isArray(value)) {
    for(const [key,child] of Object.entries(value)) leafValues(child,[...path,key],result);
  } else if(Array.isArray(value) && path.at(-1)==="players") {
    for(const player of value) leafValues(player,[...path,player.id],result);
  } else result.set(JSON.stringify(path),value);
  return result;
}
function recoverDraft(current,draft) {
  // Older saved drafts have no base snapshot. Their differences are shown
  // explicitly for review instead of silently replacing the current game.
  const base=leafValues(draft.base || current), saved=leafValues(draft.game), latest=leafValues(current);
  const game=copy(current),changes=[];
  for(const key of new Set([...base.keys(),...saved.keys()])) {
    const path=JSON.parse(key),before=base.get(key),value=saved.get(key);
    if(["id","code","color"].includes(path.at(-1)) || JSON.stringify(before)===JSON.stringify(value))continue;
    let target=game;
    for(const part of path.slice(0,-1)) target=Array.isArray(target)?target.find(p=>p.id===part):target?.[part];
    const applicable=!!target;
    changes.push({path,value,current:latest.get(key),applicable});
    if(applicable) {if(value===undefined)delete target[path.at(-1)];else target[path.at(-1)]=copy(value);}
  }
  return {game,changes};
}
function reviewDraft() {
  const draft=state.drafts[selected],current=state.published.games.find(g=>g.id===selected);
  const {changes}=recoverDraft(current,draft);
  $("draft-note").textContent=`Saved ${new Date(draft.savedAt).toLocaleString()}. ${draft.base ? "Only your saved edits will be loaded onto the latest game." : "This older draft has no base snapshot. All differences below will be loaded; review stats carefully."} Edits for players no longer in the roster remain visible here and cannot be applied.`;
  const table=node("table"),head=node("tr");for(const label of ["Field","Latest value","Saved edit"])head.append(node("th",label));table.append(head);
  for(const change of changes) {
    const row=node("tr"),label=change.path.map((part,index)=>change.path[index-1]==="players" ? draft.game[change.path[0]].players.find(p=>p.id===part)?.name || part : part).join(" / ");
    row.append(node("td",label+(change.applicable?"":" (player no longer present)")),node("td",change.current===undefined?"—":JSON.stringify(change.current)),node("td",change.value===undefined?"(removed)":JSON.stringify(change.value)));table.append(row);
  }
  $("draft-comparison").replaceChildren(changes.length?table:node("p","No saved edits differ from the original game."));
  $("draft-dialog").showModal();
}
function integer(text) {if(!/^\d+$/.test(text.trim()))throw Error("Stats and scores must be non-negative whole numbers.");return Number(text);}
function collect() {
  const game=copy(editing);
  document.querySelectorAll("#fields [data-path]").forEach(field=>{
    const path=JSON.parse(field.dataset.path);let target=game;for(const key of path.slice(0,-1))target=target[key];const key=path.at(-1);
    let value=field.type==="checkbox"?field.checked:field.type==="number"?integer(field.value):field.value.trim();
    if(key==="quarters")value=value?value.split(",").map(integer):[];
    if(["portraitUrl","logoUrl","venue","tipoffUTC"].includes(key) && value===""){delete target[key];return;}target[key]=value;
  });return game;
}
function preview() {
  const game=collect(),content=$("preview-content");content.replaceChildren(node("p",`${game.competition} · ${game.date} · ${game.status}`));const score=node("div",undefined,"scoreboard");
  for(const side of ["away","home"]){const team=game[side],box=node("div");if(team.logoUrl){const img=node("img");img.src=team.logoUrl;img.alt=team.name;box.append(img);}box.append(node("strong",String(team.score)));score.append(box);}content.append(score);
  for(const side of ["away","home"]){content.append(node("h3",game[side].name));for(const player of game[side].players){const row=node("div",undefined,"player-preview"),name=node("div",player.name);name.append(node("small",`${player.stats.minutes}:${String(player.stats.seconds).padStart(2,"0")} · ${player.starter?"Starter":"Substitute"}`));row.append(name,node("span",`${player.stats.points} PTS · ${player.stats.rebounds} REB · ${player.stats.assists} AST`));content.append(row);}}
  $("preview-dialog").showModal();
}
function renderHistory() {$("history").replaceChildren();for(const item of state.history){const b=node("button",`Restore ${item.revision} · ${item.source} · ${new Date(item.updatedAt).toLocaleString()}`);b.onclick=()=>busy(async()=>{if(!confirm("Restore this publication? This replaces the current published games and corrections."+(hasUnsavedChanges()?" Unsaved edits will be discarded.":"")))return;await api("/admin/restore",{targetRevision:item.revision,revision:state.revision});await load();message("Earlier publication restored.");});$("history").append(b);}}
$("search").oninput=renderGames;
$("fields").onsubmit=event=>event.preventDefault();
$("preview").onclick=()=>{try{preview();}catch(error){message(error.message,true);}};
$("close-preview").onclick=()=>$("preview-dialog").close();
for(const id of ["save","publish"]){$(id).onclick=()=>busy(async()=>{const game=collect(),draft=state.drafts[selected];if(draft && draft.revision!==state.revision && !recoveredDraft && !confirm("Replace the older saved draft with these values? Cancel to review and recover it first."))return;await api("/admin/game",{game,revision:baseRevision,draftVersion:baseDraftVersion,publish:id==="publish"});await load();message(id==="publish"?"Published. The app will receive these stats on its next refresh.":"Draft saved. Published stats have not changed.");});}
$("default").onclick=()=>busy(async()=>{if(!confirmDiscard("Changing the default reloads the editor. Discard unsaved changes?"))return;await api("/admin/default",{gameId:selected,revision:state.revision});await load();message("Default game updated.");});
$("clear").onclick=()=>busy(async()=>{if(!confirm("Clear manual corrections for this game?"+(state.drafts[selected]?" This also deletes this game's saved draft.":"")+(hasUnsavedChanges()?" Unsaved edits will be discarded.":"")))return;await api("/admin/corrections/clear",{gameId:selected,revision:state.revision});await load();message("Provider stats restored for this game.");});
$("import").onclick=()=>busy(async()=>{if(!confirmDiscard("Importing reloads the editor. Discard unsaved changes?"))return;message("Fetching and validating game stats…");await api("/admin/import",{});await load();message("Stats imported. Manual corrections were preserved.");});
$("review-draft").onclick=reviewDraft;
$("close-draft").onclick=()=>$("draft-dialog").close();
$("recover-draft").onclick=()=>{if(!confirmDiscard("Loading saved edits replaces unsaved changes. Continue?"))return;const current=state.published.games.find(g=>g.id===selected),{game,changes}=recoverDraft(current,state.drafts[selected]);openGame(selected,game);$("draft-dialog").close();message(changes.some(c=>!c.applicable)?"Draft edits loaded. Some players are no longer present; their saved edits remain in the draft review.":"Draft edits loaded. Review, then save or publish.");};
busy(async()=>{await load();message(state.importError || "Ready. Select a game to edit.",!!state.importError);});
