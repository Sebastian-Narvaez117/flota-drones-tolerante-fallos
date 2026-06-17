'use strict';

// ════════════════════════════════════════════════
//  ESTADO GLOBAL
// ════════════════════════════════════════════════
const state = {
  nodes:            {},   // nodeId -> status object
  decisionsByRound: {},   // round  -> {nodeId -> decision}
  cristian:         {},   // nodeId -> cristian status
  me:               {},   // nodeId -> me status
  coordinator:      {},   // info extendida del coordinador
  bullyEvents:      [],
  meHistory:        [],
  cristianHistory:  [],
  logEntries:       [],
  logFilter:        'all',
  openJsonNode: null   // id del nodo cuyo detalle está abierto (null = ninguno)
};

function toggleNodeJson(nodeId) {
  if (state.openJsonNode === nodeId) {
    state.openJsonNode = null;
  } else {
    state.openJsonNode = nodeId;
  }
  renderNodeCards(); // actualizar la vista para reflejar el cambio
}
// ════════════════════════════════════════════════
//  RELOJ LOCAL
// ════════════════════════════════════════════════
function updateClock() {
  document.getElementById('clock').textContent =
    new Date().toTimeString().slice(0, 8);
}
setInterval(updateClock, 1000);
updateClock();

// ════════════════════════════════════════════════
//  POLLING — llama a todos los endpoints en paralelo
// ════════════════════════════════════════════════
async function poll() {
  try {
    const [statusRes, cristianRes, meRes, bullyRes, coordRes] = await Promise.allSettled([
      fetch('/api/status'),
      fetch('/api/cristian'),
      fetch('/api/me'),
      fetch('/api/bully/events'),
      fetch('/api/coordinator'),
    ]);

    if (statusRes.status === 'fulfilled' && statusRes.value.ok) {
      const nodes = await statusRes.value.json();
      nodes.forEach(processNode);
    }
    if (cristianRes.status === 'fulfilled' && cristianRes.value.ok) {
      const list = await cristianRes.value.json();
      list.forEach(c => {
        const prev = state.cristian[c.nodeId];
        state.cristian[c.nodeId] = c;
        if (prev && c.online && c.clockOffset !== undefined
            && c.clockOffset !== null
            && prev.clockOffset !== c.clockOffset) {
          const rtt = c.lastRtt ?? '?';
          addLog('cristian', 'CRISTIAN',
            `D${c.nodeId} sync offset=${c.clockOffset}ms RTT=${rtt}ms`);
          state.cristianHistory.unshift({
            nodeId: c.nodeId, offset: c.clockOffset, rtt,
            ts: Date.now(),
          });
          if (state.cristianHistory.length > 40) state.cristianHistory.pop();
        }
      });
    }
    if (meRes.status === 'fulfilled' && meRes.value.ok) {
      const data = await meRes.value.json();
      (data.nodes || []).forEach(m => { state.me[m.nodeId] = m; });
      (data.history || []).forEach(ev => {
        const exists = state.meHistory.some(h => h.ts === ev.ts && h.nodeId === ev.nodeId);
        if (!exists) {
          state.meHistory.unshift(ev);
          addLog('me', 'MUTEX',
            `D${ev.nodeId} → ${ev.state} (Lamport=${ev.lamport})`);
        }
      });
      if (state.meHistory.length > 40) state.meHistory.splice(40);
    }
    if (bullyRes.status === 'fulfilled' && bullyRes.value.ok) {
      const events = await bullyRes.value.json();
      events.forEach(ev => {
        const exists = state.bullyEvents.some(e => e.ts === ev.ts && e.nodeId === ev.nodeId);
        if (!exists) state.bullyEvents.push(ev);
      });
      if (state.bullyEvents.length > 60) state.bullyEvents.splice(0, state.bullyEvents.length - 60);
    }
    if (coordRes.status === 'fulfilled' && coordRes.value.ok) {
      state.coordinator = await coordRes.value.json();
    }

    renderAll();
    document.getElementById('last-update').textContent =
      'Actualizado: ' + new Date().toLocaleTimeString();
  } catch (e) {
    addLog('error', 'SYS', 'Error al conectar con el backend Flask');
  }
}

// ════════════════════════════════════════════════
//  PROCESAR NODO (desde /api/status)
// ════════════════════════════════════════════════
function processNode(n) {
  const prev = state.nodes[n.nodeId];
  state.nodes[n.nodeId] = n;

  // Acumular decisiones
  if (n.decisionHistory) {
    Object.entries(n.decisionHistory).forEach(([round, decision]) => {
      if (!state.decisionsByRound[round]) state.decisionsByRound[round] = {};
      state.decisionsByRound[round][n.nodeId] = decision;
    });
  }

  if (!prev) {
    if (n.online) addLog('bft', 'SYS', `D${n.nodeId} conectado (${n.nodeIp})`);
    return;
  }

  if (n.currentRound > (prev.currentRound || 0)) {
    addLog('decision', 'BFT', `D${n.nodeId} completó ronda ${n.currentRound}`);
  }
  if (n.coordinatorId !== prev.coordinatorId && n.coordinatorId > 0) {
    addLog('bully', 'BULLY', `D${n.nodeId} reconoce nuevo coordinador: D${n.coordinatorId}`);
  }
  if (!prev.electionActive && n.electionActive) {
    addLog('bully', 'ELECT', `D${n.nodeId} inició elección Bully`);
  }
  if (!prev.online && n.online) {
    addLog('bft', 'SYS', `D${n.nodeId} volvió online`);
  }
  if (prev.online && !n.online) {
    addLog('error', 'SYS', `D${n.nodeId} se desconectó`);
  }
}

// ════════════════════════════════════════════════
//  RENDER PRINCIPAL
// ════════════════════════════════════════════════
function renderAll() {
  renderNodeCards();
  renderCoordPanel();
  renderCristianPanel();
  renderMePanel();
  renderConsensusTable();
  renderBullyPanel();
  renderLog();
  updateHeader();
}

// ─── NODOS ──────────────────────────────────────
function renderNodeCards() {
  Object.values(state.nodes).forEach(n => {
    const card = document.getElementById(`card-${n.nodeId}`);
    if (!card) return;

    const isCoord    = n.coordinatorId === n.nodeId;
    const isTraitor  = n.nodeRole === 'traidor';
    const isElecting = n.electionActive;
    const meData     = state.me[n.nodeId] || {};
    const isHeld     = meData.state === 'HELD';

    card.className = ['node-card',
      isCoord    ? 'coordinator' : '',
      isTraitor  ? 'traidor'     : '',
      !n.online  ? 'offline'     : '',
      isHeld     ? 'me-held'     : '',
    ].filter(Boolean).join(' ');

    // Ping dot
    const pingEl = document.getElementById(`ping-${n.nodeId}`);
    if (pingEl) pingEl.className = `node-ping ${n.online ? 'online' : 'offline'}`;

    // Badges
    const badgesEl = document.getElementById(`badges-${n.nodeId}`);
    if (badgesEl) {
      let b = n.online
        ? '<span class="badge badge-online">Online</span>'
        : '<span class="badge badge-offline">Offline</span>';
      b += isTraitor
        ? '<span class="badge badge-traitor">Traidor</span>'
        : '<span class="badge badge-loyal">Leal</span>';
      if (isCoord)    b += '<span class="badge badge-coord">Coordinador</span>';
      if (isElecting) b += '<span class="badge badge-election">⚡ Elección</span>';
      badgesEl.innerHTML = b;
    }

    setText(`round-${n.nodeId}`, n.currentRound || '—');
    setText(`coord-${n.nodeId}`, n.coordinatorId > 0 ? `D${n.coordinatorId}` : '—');

    // Cristian
    const cData = state.cristian[n.nodeId] || {};
    const offset = cData.clockOffset;
    setText(`cristian-offset-${n.nodeId}`,
      offset !== undefined && offset !== null ? `${offset > 0 ? '+' : ''}${offset}ms` : '—');
    setText(`cristian-rtt-${n.nodeId}`,
      cData.lastRtt !== undefined && cData.lastRtt !== null ? `${cData.lastRtt}ms` : '—');

    // Exclusión Mutua
    const meState = meData.state || '—';
    const meEl = document.getElementById(`me-state-${n.nodeId}`);
    if (meEl) {
      meEl.textContent = meState;
      meEl.className = `value mono me-state ${meState}`;
    }
    setText(`lamport-${n.nodeId}`, meData.lamportClock ?? '—');

    // Última decisión
    const decEl = document.getElementById(`decision-${n.nodeId}`);
    if (decEl) {
      const rounds = Object.keys(n.decisionHistory || {});
      if (rounds.length > 0) {
        const lastR   = Math.max(...rounds.map(Number));
        const lastDec = n.decisionHistory[lastR];
        decEl.textContent = `R${lastR}: ${lastDec}`;
        decEl.className   = `last-decision${isTraitor ? ' traitor-val' : ''}`;
      } else {
        decEl.textContent = 'Sin decisión aún';
        decEl.className   = 'last-decision';
      }
    }
        // --- Detalle JSON del nodo ---
    const jsonPre = document.getElementById(`json-content-${n.nodeId}`);
    if (jsonPre) {
      jsonPre.textContent = JSON.stringify(n, null, 2);
    }
    const jsonDetail = document.getElementById(`json-detail-${n.nodeId}`);
    if (jsonDetail) {
      if (state.openJsonNode === n.nodeId) {
        jsonDetail.classList.add('open');
      } else {
        jsonDetail.classList.remove('open');
      }
    }
  });
}

// ─── COORDINADOR ────────────────────────────────
function renderCoordPanel() {
  const c = state.coordinator;
  if (!c || c.coordinatorId <= 0) return;

  setText('coord-hero-id', `D${c.coordinatorId}`);
  setText('coord-hero-ip', c.coordinatorIp || '—');
  setText('coord-role', c.coordinatorRole || '—');
  setText('coord-round', c.currentRound ?? '—');
  setText('coord-online', c.online ? 'Online ✓' : 'Offline ✗');

  const dot = document.getElementById('coord-status-dot');
  if (dot) dot.className = `coord-status-dot ${c.online ? 'online' : 'offline'}`;

  const evEl = document.getElementById('coord-events');
  if (evEl) {
    const events = c.recentEvents || [];
    if (events.length === 0) {
      evEl.innerHTML = '<div class="empty-msg">Sin eventos aún.</div>';
    } else {
      evEl.innerHTML = events.slice(-8).reverse()
        .map(e => `<div class="coord-event-item">${escHtml(e)}</div>`)
        .join('');
    }
  }
}

// ─── CRISTIAN ───────────────────────────────────
function renderCristianPanel() {
  const el = document.getElementById('cristian-nodes');
  if (!el) return;

  const rows = Object.values(state.cristian);
  if (rows.length === 0) {
    el.innerHTML = '<div class="empty-msg">Esperando datos de sincronización...</div>';
    return;
  }

  // Calcular rango de offsets para la barra visual
  const offsets = rows
    .filter(r => r.clockOffset !== null && r.clockOffset !== undefined)
    .map(r => Math.abs(r.clockOffset));
  const maxOff = offsets.length ? Math.max(...offsets, 1) : 1;

  el.innerHTML = rows.map(r => {
    const nData   = state.nodes[r.nodeId] || {};
    const isCoord = nData.coordinatorId === r.nodeId;
    const offset  = r.clockOffset;
    const rtt     = r.lastRtt;
    const offStr  = offset === null || offset === undefined
      ? '—'
      : `${offset > 0 ? '+' : ''}${offset}ms`;
    const offClass = offset === null || offset === undefined ? ''
      : offset === 0 ? 'zero' : offset > 0 ? 'pos' : 'neg';
    const barPct = offset !== null && offset !== undefined
      ? Math.round((Math.abs(offset) / maxOff) * 100)
      : 0;

    return `<div class="cristian-row">
      <span class="cnode-id">D${r.nodeId}</span>
      <span class="cnode-role">${isCoord ? '⭐ coord' : 'cliente'}</span>
      <div class="cristian-bar"><div class="cristian-bar-fill" style="width:${barPct}%"></div></div>
      <span class="cristian-offset ${offClass}">${offStr}</span>
      <span class="cristian-rtt">${rtt !== null && rtt !== undefined ? `RTT:${rtt}ms` : '—'}</span>
    </div>`;
  }).join('');

  // Historial
  const histEl = document.getElementById('cristian-history');
  if (histEl) {
    if (state.cristianHistory.length === 0) {
      histEl.innerHTML = '<div class="empty-msg">Esperando sincronizaciones...</div>';
    } else {
      histEl.innerHTML = state.cristianHistory.slice(0, 10)
        .map(h => {
          const t = new Date(h.ts).toLocaleTimeString();
          return `<div class="cristian-history-item">`
            + `<span style="color:var(--muted)">${t}</span> `
            + `<span style="color:var(--accent)">D${h.nodeId}</span> `
            + `offset=<span style="color:var(--cristian)">${h.offset !== null ? (h.offset > 0 ? '+' : '') + h.offset + 'ms' : '—'}</span> `
            + `RTT=<span style="color:var(--text-dim)">${h.rtt !== null ? h.rtt + 'ms' : '—'}</span></div>`;
        }).join('');
    }
  }
}

// ─── EXCLUSIÓN MUTUA ────────────────────────────
function renderMePanel() {
  const el = document.getElementById('me-states');
  if (!el) return;

  const rows = Object.values(state.me);
  if (rows.length === 0) {
    el.innerHTML = '<div class="empty-msg">Sin datos de exclusión mutua.</div>';
    return;
  }

  el.innerHTML = rows.map(m => {
    const s       = m.state || 'RELEASED';
    const pending = (m.pendingReplies || []).length;
    const defer   = (m.deferredQueue || []).length;
    return `<div class="me-row ${s}">
      <span class="me-node-id">D${m.nodeId}</span>
      <span class="me-state-badge ${s}">${s}</span>
      ${pending > 0
        ? `<span class="me-pending">⏳ ${pending} pend.</span>`
        : defer > 0
          ? `<span class="me-pending" style="color:var(--me-wanting)">⏸ ${defer} dif.</span>`
          : ''}
      <span class="me-lamport">λ=${m.lamportClock ?? 0}</span>
    </div>`;
  }).join('');

  // Historial
  const histEl = document.getElementById('me-history');
  if (histEl) {
    if (state.meHistory.length === 0) {
      histEl.innerHTML = '<div class="empty-msg">Sin accesos aún.</div>';
    } else {
      histEl.innerHTML = state.meHistory.slice(0, 10)
        .map(h => {
          const t = new Date(h.ts).toLocaleTimeString();
          return `<div class="me-history-item ${h.state}">`
            + `<span style="color:var(--muted)">${t}</span> `
            + `<span style="color:var(--accent)">D${h.nodeId}</span> → ${h.state} `
            + `(λ=${h.lamport})</div>`;
        }).join('');
    }
  }

  // Estado global en header
  const anyHeld = rows.some(m => m.state === 'HELD');
  const anyWanting = rows.some(m => m.state === 'WANTING');
  const gme = document.getElementById('global-me-state');
  if (gme) {
    if (anyHeld) {
      gme.textContent = 'OCUPADO';
      gme.style.color = 'var(--me-held)';
    } else if (anyWanting) {
      gme.textContent = 'DISPUTADO';
      gme.style.color = 'var(--me-wanting)';
    } else {
      gme.textContent = 'LIBRE';
      gme.style.color = 'var(--ok)';
    }
  }
}

// ─── CONSENSO BFT ───────────────────────────────
function renderConsensusTable() {
  const tbody = document.getElementById('consensus-body');
  const rounds = Object.keys(state.decisionsByRound).map(Number).sort((a, b) => b - a);

  if (rounds.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty-cell">Esperando rondas...</td></tr>';
    return;
  }

  tbody.innerHTML = rounds.slice(0, 10).map(round => {
    const decisions = state.decisionsByRound[round];
    // Nodos leales: todos excepto el marcado como traidor
    const loyalIds = [1, 2, 3, 4].filter(id => {
      const n = state.nodes[id];
      return n && n.nodeRole !== 'traidor';
    });
    const loyalDecs = loyalIds.map(id => decisions[id]).filter(Boolean);
    const ic1 = loyalDecs.length >= 2 && loyalDecs.every(d => d === loyalDecs[0]);

    return `<tr>
      <td style="color:var(--muted)">${round}</td>
      ${[1, 2, 3, 4].map(id => {
        const dec     = decisions[id] || '—';
        const isFalso = dec.startsWith('FALSO');
        const isN     = state.nodes[id];
        const isTrait = isN && isN.nodeRole === 'traidor';
        const cls     = isFalso || (isTrait && dec !== '—') ? 'mismatch' : (dec !== '—' ? 'match' : '');
        return `<td class="${cls}">${dec.length > 16 ? dec.slice(0, 14) + '…' : dec}</td>`;
      }).join('')}
      <td class="${ic1 ? 'match' : 'mismatch'}">${ic1 ? '✓ OK' : '✗ FALLO'}</td>
    </tr>`;
  }).join('');
}

// ─── BULLY ──────────────────────────────────────
function renderBullyPanel() {
  const el = document.getElementById('election-flow');
  if (!el) return;

  if (state.bullyEvents.length === 0) {
    el.innerHTML = '<div class="empty-msg">Sin eventos de elección aún.</div>';
    return;
  }

  el.innerHTML = [...state.bullyEvents].reverse().slice(0, 8).map(ev => {
    const typeClass = {
      election:    'type-election',
      ok:          'type-ok',
      coordinator: 'type-coordinator',
      heartbeat:   'type-heartbeat',
    }[ev.type] || 'type-heartbeat';
    const t = ev.ts ? new Date(ev.ts).toLocaleTimeString() : '';
    return `<div class="flow-step ${ev.type === 'coordinator' ? 'active' : ''}">
      <span class="step-type ${typeClass}">${ev.type.toUpperCase()}</span>
      <span style="color:var(--text-dim)">D${ev.nodeId}</span>
      <span style="flex:1;color:var(--text)">${escHtml(ev.msg)}</span>
      <span style="color:var(--muted);font-size:9px">${t}</span>
    </div>`;
  }).join('');
}

// ─── LOG ────────────────────────────────────────
function renderLog() {
  const el = document.getElementById('log-scroll');
  if (!el) return;

  const filter = state.logFilter;
  const entries = state.logEntries.slice(-80).reverse();
  el.innerHTML = entries.map(e => {
    const hidden = filter !== 'all' && e.type !== filter ? ' hidden' : '';
    return `<div class="log-entry ${e.type}${hidden}">
      <span class="ts">${e.time}</span>
      <span class="tag">[${e.tag}]</span>
      <span class="msg"> ${escHtml(e.msg)}</span>
    </div>`;
  }).join('');
}

// ─── HEADER ─────────────────────────────────────
function updateHeader() {
  // Coordinador global
  const nodes  = Object.values(state.nodes);
  const coords = nodes.filter(n => n.online && n.coordinatorId > 0).map(n => n.coordinatorId);
  const coord  = coords.length > 0 ? coords[0] : '—';
  setText('global-coordinator', coord !== '—' ? `D${coord}` : '—');

  // Ronda máxima
  const rounds   = nodes.filter(n => n.online).map(n => n.currentRound || 0);
  const maxRound = rounds.length > 0 ? Math.max(...rounds) : '—';
  setText('global-round', maxRound);

  // Offset Cristian promedio
  const offsets = Object.values(state.cristian)
    .map(c => c.clockOffset)
    .filter(o => o !== null && o !== undefined);
  const avgOffset = offsets.length
    ? Math.round(offsets.reduce((a, b) => a + b, 0) / offsets.length)
    : null;
  const offEl = document.getElementById('global-offset');
  if (offEl) {
    offEl.textContent = avgOffset !== null ? `${avgOffset > 0 ? '+' : ''}${avgOffset}ms` : '—';
    offEl.style.color = avgOffset !== null && Math.abs(avgOffset) > 500
      ? 'var(--traitor)'
      : 'var(--text)';
  }
}

// ════════════════════════════════════════════════
//  HELPERS
// ════════════════════════════════════════════════
function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function addLog(type, tag, msg) {
  const last = state.logEntries[state.logEntries.length - 1];
  if (last && last.msg === msg) return; // evitar duplicados consecutivos
  state.logEntries.push({ type, tag, msg, time: new Date().toLocaleTimeString() });
  if (state.logEntries.length > 300) state.logEntries.shift();
}

function clearLog() {
  state.logEntries = [];
  renderLog();
}

function setFilter(f) {
  state.logFilter = f;
  document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.filter === f);
  });
  renderLog();
}

// ════════════════════════════════════════════════
//  ARRANQUE
// ════════════════════════════════════════════════
poll();
setInterval(poll, 2000);