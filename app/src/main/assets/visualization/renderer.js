"use strict";
(() => {
  let sessionToken = "";
  let sequence = 0;
  const send = kind => {
    if (window.rendererEvents && sessionToken) {
      window.rendererEvents.postMessage(JSON.stringify({kind, token: sessionToken, sequence}));
    }
  };
  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  const display = value => value === null || value === undefined ? "Unknown" : String(value);
  window.bootstrap = token => { sessionToken = token; sequence = 0; send("pageReady"); };
  window.render = (payload, token, currentSequence) => {
    if (token !== sessionToken || !Number.isInteger(currentSequence) || currentSequence <= sequence) return;
    sequence = currentSequence;
    try {
      const fieldGoals = payload.spec.template === "fieldGoals";
      document.getElementById("title").textContent = payload.spec.title || (fieldGoals ? "Field goals" : "Player statistics");
      const allAttemptsKnown = payload.players.every(p => p.fgAttempted !== null);
      const totalAttempts = allAttemptsKnown ? payload.players.reduce((sum, p) => sum + p.fgAttempted, 0) : null;
      const combinedPercent = totalAttempts === 0 ? "N/A" : payload.weightedFgPercent === null ? "Unknown" : payload.weightedFgPercent.toFixed(1) + "%";
      document.getElementById("summary").textContent = fieldGoals ? "Mint: made · Lavender: missed. Unknown values have no bar. Combined FG%: " + combinedPercent + " (total made ÷ total attempts)." : "Bar length shows " + payload.spec.metric + ". Unknown values have no bar.";
      const chart = document.getElementById("chart");
      chart.replaceChildren();
      const rows = payload.players;
      if (!rows.length) chart.appendChild(element("p", "No players match these filters."));
      const scale = Math.max(1, ...rows.map(p => fieldGoals ? (p.fgAttempted || 0) : (p[payload.spec.metric] || 0)));
      for (const row of rows) {
        const block = element("section", undefined, "player");
        block.appendChild(element("div", row.name + " · " + row.teamId, "name"));
        const track = element("div", undefined, "track");
        if (fieldGoals && row.fgMade !== null && row.fgMissed !== null) {
          const made = element("div", undefined, "made");
          const missed = element("div", undefined, "missed");
          made.style.width = String(100 * row.fgMade / scale) + "%";
          missed.style.width = String(100 * row.fgMissed / scale) + "%";
          track.append(made, missed);
        } else if (!fieldGoals && row[payload.spec.metric] !== null) {
          const bar = element("div", undefined, "bar");
          bar.style.width = String(100 * row[payload.spec.metric] / scale) + "%";
          track.appendChild(bar);
        }
        block.appendChild(track);
        const percent = row.fgAttempted === 0 ? "N/A" : row.fgPercent === null ? "Unknown" : row.fgPercent.toFixed(1) + "%";
        block.appendChild(element("div", fieldGoals ? "Made " + display(row.fgMade) + " · Attempts " + display(row.fgAttempted) + " · Missed " + display(row.fgMissed) + " · FG% " + percent : display(row[payload.spec.metric]), "detail"));
        chart.appendChild(block);
      }
      requestAnimationFrame(() => requestAnimationFrame(() => send("ready")));
    } catch (_) { send("error"); }
  };
})();
