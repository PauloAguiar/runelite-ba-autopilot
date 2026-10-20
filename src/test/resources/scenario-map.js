'use strict';

// Map marks stay compact. All descriptive text belongs to the hovered tile's popover.
let mapEntries = new Map();
const arenaLayers = {eggs:'Eggs', bait:'Food', tools:'Tools', objects:'Objects', fighters:'Fighters / rangers', runners:'Runners', healers:'Healers', teammates:'Teammates', unseen:'Last seen'};
let arenaVisibility = Object.fromEntries(Object.keys(arenaLayers).map(key => [key, true]));
const npcLayer = type => ({RUNNER:'runners', HEALER:'healers'})[type] || 'fighters';
const mapXY = p => [5 + (p.x & 63) * 10, 635 - (p.y & 63) * 10];
const npcColor = type => ({RUNNER: colors.DEFENDER, HEALER: colors.HEALER})[type] || colors.ATTACKER;

function initArenaFilters() {
 try {
  const saved = JSON.parse(localStorage.getItem('ba-replay-arena-layers') || '{}');
  for (const key of Object.keys(arenaLayers)) if (typeof saved[key] === 'boolean') arenaVisibility[key] = saved[key];
 } catch (_) { }
 const apply = () => {
  try { localStorage.setItem('ba-replay-arena-layers', JSON.stringify(arenaVisibility)); } catch (_) { }
  for (const input of $('arenaFilters').querySelectorAll('input')) input.checked = arenaVisibility[input.dataset.layer];
  if (current) map();
 };
 $('arenaFilters').innerHTML = Object.entries(arenaLayers).map(([key, label]) => `<label><input type="checkbox" data-layer="${key}"${arenaVisibility[key] ? ' checked' : ''}>${label}</label>`).join('');
 for (const input of $('arenaFilters').querySelectorAll('input')) input.onchange = () => { arenaVisibility[input.dataset.layer] = input.checked; apply(); };
 $('showAllArena').onclick = () => { for (const key of Object.keys(arenaLayers)) arenaVisibility[key] = true; apply(); };
 $('focusArena').onclick = () => {
  if (!current) return;
  const role = meta.capturedRole, queen = current.roles[role].input.queenAlive;
  arenaVisibility = {eggs:role === 'COLLECTOR' || queen, bait:role === 'DEFENDER', tools:role === 'DEFENDER', objects:true,
   fighters:role === 'ATTACKER' || role === 'COLLECTOR', runners:role === 'DEFENDER' || role === 'COLLECTOR',
   healers:role === 'HEALER' || role === 'COLLECTOR', teammates:role !== 'DEFENDER', unseen:true};
  apply();
 };
}

function npcName(npc) { return `${npc.type[0] + npc.type.slice(1).toLowerCase()} · NPC ${npc.npcIndex}`; }

function unconfirmedNpcs(s) {
 const seen = new Set((s.penance || []).map(n => n.npcIndex)), rows = [];
 for (const [npcs, timedOut] of [[s.unseenPenance || [], false], [s.uncertainPenance || [], true]]) for (const npc of npcs) {
  if (seen.has(npc.npcIndex) || (s.cleared || []).includes(npc.type)) continue;
  seen.add(npc.npcIndex);
  rows.push({...npc, timedOut, unseenTicks:Math.max(npc.unseenTicks || 0, s.tick - npc.observedTick)});
 }
 return rows.sort((a, b) => Number(b.timedOut) - Number(a.timedOut) || b.unseenTicks - a.unseenTicks);
}

function unconfirmedInfo(s, role) {
 const rows = unconfirmedNpcs(s).map(npc => ({title:npcName(npc), status:npc.timedOut ? 'Timed out · unresolved' : 'Out of view',
  detail:`Last seen ${npc.unseenTicks} ticks ago (${(npc.unseenTicks * .6).toFixed(1)}s)${npc.tile ? ` at (${npc.tile.x & 63}, ${npc.tile.y & 63})` : ''}. Current position and survival unconfirmed.`}));
 const unnamed = (s.uncertainRunners || 0) - rows.filter(r => r.status === 'Timed out · unresolved').length;
 if (unnamed > 0) rows.push({title:`${unnamed} runner${unnamed > 1 ? 's' : ''}`, status:'Unresolved', detail:'Timed out without kill confirmation; last position unavailable.'});
 if (s.inArena && !s.queenAlive && (!s.listenCall || !s.listenFresh)) rows.push({title:'Incoming call', status:'Unconfirmed', detail:s.listenCall ? `Last heard ${s.listenCall}; awaiting a fresh call.` : 'No incoming call observed for this period.'});
 if (s.inArena && role === 'DEFENDER' && s.trapCharges < 0) rows.push({title:'Trap charges', status:'Not observed', detail:'No visible charge reading at this tick.'});
 return rows;
}

function renderUnconfirmed(card) {
 const rows = unconfirmedInfo(card.input, meta.capturedRole);
 $('unconfirmedCount').textContent = rows.length ? String(rows.length) : 'None';
 $('unconfirmedList').innerHTML = rows.length ? rows.map(row => `<div class="unconfirmed-row"><div><strong>${esc(row.title)}</strong><span class="badge">${esc(row.status)}</span></div><small>${esc(row.detail)}</small></div>`).join('')
  : '<p class="muted">No unresolved observations tracked at this tick.</p>';
}

function mapIcon(entry) {
 const color = entry.color || '#b7c6d0';
 if (entry.kind === 'egg') return `<ellipse cy="-.5" rx="3" ry="4" fill="${color}" stroke="#e9edf1" stroke-width=".6"/><path d="M-1.5-2.5l1-1" stroke="white" opacity=".7"/>`;
 if (entry.kind === 'bait') {
  const shape = entry.variant.includes('worm') ? '<path d="M-3-2Q0-5 1-2T0 1T2 3" fill="none" stroke="#e1b49c" stroke-width="1.8"/>'
   : entry.variant.includes('tofu') ? '<path d="M-3-2L0-4 3-2V2L0 4-3 2Z" fill="#e9dfbe"/><path d="M-3-2L0 0 3-2M0 0V4" stroke="#a8a08b" stroke-width=".6"/>'
   : '<rect x="-3.5" y="-3" width="7" height="6" rx="1" fill="#d4a65b"/><path d="M-2-1h4M-2 1h4" stroke="#886431" stroke-dasharray=".7 1"/>';
  return `<circle r="4.7" fill="#17202a" stroke="${color}" stroke-width="1.2"/>${shape}`;
 }
 if (entry.kind === 'logs') return '<path d="M-3 3L2-3" stroke="#8e603d" stroke-width="4"/><ellipse cx="-3" cy="3" rx="2" ry="1.5" fill="#d4aa72"/>';
 if (entry.kind === 'hammer') return '<path d="M-2 4L1-2" stroke="#bd9366" stroke-width="1.8"/><path d="M-2-3L3-1" stroke="#c3d1dd" stroke-width="3"/>';
 if (entry.kind === 'object') return `<rect x="-4" y="-4" width="8" height="8" rx="1" fill="#3c4c5b" stroke="#a3b4c2" stroke-width=".6"/><text y="2.2" text-anchor="middle" fill="#edf2f5" font-size="6" font-weight="700">${entry.symbol}</text>`;
 if (entry.kind === 'player') return `<path d="M0-5L4.5 0 0 5-4.5 0Z" fill="${color}" stroke="#eff6ff" stroke-width=".8"/><text y="2" text-anchor="middle" font-size="5.5" font-weight="800" fill="#10151b">${esc(entry.symbol)}</text>`;
 if (entry.kind === 'teammate') return `<circle r="4.6" fill="#17202a" stroke="${color}" stroke-width="1.2"/><text y="2" text-anchor="middle" fill="${color}" font-size="6" font-weight="700">${esc(entry.symbol)}</text>`;
 if (entry.kind === 'npc') return entry.unconfirmed
  ? `<circle r="4.5" fill="#17202a" stroke="${color}" stroke-dasharray="1.5 1"/><text y="2" text-anchor="middle" fill="${color}" font-size="6" font-weight="800">?</text>`
  : `<circle r="4.2" fill="${color}" opacity="${entry.dying ? '.4' : '1'}"/><text y="2" text-anchor="middle" fill="#10151b" font-size="6" font-weight="800">${esc(entry.symbol)}</text>`;
 return `<path d="M0-4L4 0 0 4-4 0Z" fill="${color}"/>`;
}

function tileEntries(card) {
 const entries = new Map(), debug = new Map((card.npcDebug || []).map(n => [n.npcIndex, n]));
 const add = entry => {
  if (!entry.tile || entry.layer && arenaVisibility[entry.layer] === false || entry.unconfirmed && !arenaVisibility.unseen) return;
  const key = `${entry.tile.x},${entry.tile.y},${entry.tile.plane}`;
  if (!entries.has(key)) entries.set(key, []);
  entries.get(key).push(entry);
 };
 const items = new Map();
 for (const item of card.mapItems || []) {
  const key = `${item.tile.x},${item.tile.y},${item.tile.plane},${item.id},${item.baitStatus}`;
  if (items.has(key)) items.get(key).quantity += item.quantity;
  else items.set(key, {...item});
 }
 for (const item of items.values()) {
  const color = item.category === 'egg' ? ({red:'#ff6262', green:'#63d977', blue:'#70a2ff', yellow:'#ffe04d', omega:'#ffe04d'})[item.variant] || '#ffe04d'
   : item.category === 'bait' ? ({good:'#76dc98', bad:'#d3a0f3'})[item.baitStatus] || '#c3c9cf' : '#c8b28d';
  add({...item, kind:item.category, layer:item.category === 'egg' ? 'eggs' : item.category === 'bait' ? 'bait' : 'tools', color, name:item.name, detail:item.category === 'bait' ? `${item.baitStatus === 'unknown' ? 'Unclassified bait' : item.baitStatus === 'good' ? 'GOOD bait' : 'BAD bait'} · item ${item.id}` : `Item ${item.id}`});
 }
 const seenObjects = new Set();
 for (const object of card.mapObjects || []) {
  const key = `${object.tile.x},${object.tile.y},${object.id}`;
  if (seenObjects.has(key)) continue;
  seenObjects.add(key);
  const symbol = object.name.startsWith('Trap') ? 'T' : object.name.includes('machine') ? 'M'
   : object.name.includes('cannon') ? 'C' : object.name.includes('hopper') ? 'E'
   : object.name.includes('spring') ? 'S' : object.name.includes('converter') ? '↻' : '▪';
  add({...object, kind:'object', layer:'objects', symbol, detail:`Object ${object.id}`});
 }
 for (const npc of card.input.penance || []) {
  const diagnostic = debug.get(npc.npcIndex);
  add({tile:npc.tile, kind:'npc', layer:npcLayer(npc.type), color:npcColor(npc.type), symbol:npc.type === 'RANGER' ? 'A' : npc.type[0], dying:npc.dying,
   name:`${npc.type[0] + npc.type.slice(1).toLowerCase()}${npc.order > 0 ? ' #' + npc.order : ''}`,
   detail:$('showNpcDebug').checked && diagnostic ? `${diagnostic.title}\n${diagnostic.detail}` : npc.dying ? 'Dying' : 'Enable NPC debug for state / HP and poison estimate'});
 }
 for (const npc of unconfirmedNpcs(card.input)) add({tile:npc.tile, kind:'npc', layer:npcLayer(npc.type), unconfirmed:true, color:npcColor(npc.type),
  name:`${npcName(npc)} · last seen`, detail:`${npc.timedOut ? 'Timed out; unresolved' : 'Out of view'} · last seen ${npc.unseenTicks} ticks ago (${(npc.unseenTicks * .6).toFixed(1)}s). Position and survival unconfirmed.`});
 for (const player of current.observedPlayers || []) add({tile:player.tile, kind:'teammate', layer:'teammates', color:colors[player.role] || '#a7b5c1', symbol:player.role?.[0] || '?', name:player.name, detail:`Observed teammate · ${player.role || 'role unknown'}`});
 add({tile:card.input.playerTile, kind:'player', color:colors[meta.capturedRole], symbol:meta.capturedRole[0], name:`You · ${meta.capturedRole.toLowerCase()}`, detail:'Captured player position'});
 const target = card.decision.target;
 if (target?.tile) add({tile:target.tile, kind:'target', color:card.decision.badBait ? '#be78ff' : colors[meta.capturedRole], name:'Decision target', detail:card.decision.headline});
 return entries;
}

function map() {
 hideMapTooltip();
 const svg = $('map'), card = current.roles[meta.capturedRole], zoom = +$('mapZoom').value;
 renderUnconfirmed(card);
 const [px, py] = mapXY(card.input.playerTile), span = 640 / zoom;
 const vx = Math.max(0, Math.min(640 - span, Math.floor((px - span / 2) / 10) * 10)), vy = Math.max(0, Math.min(640 - span, Math.floor((py - span / 2) / 10) * 10));
 svg.setAttribute('viewBox', `${vx} ${vy} ${span} ${span}`);
 let out = '<defs><pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse"><path d="M10 0H0V10" fill="none" stroke="#293641" stroke-width=".45"/></pattern></defs><rect width="640" height="640" fill="url(#grid)"/>';
 if ($('showZones').checked) for (let x = 0; x < 8; x++) for (let y = 0; y < 8; y++)
  out += `<rect x="${x * 80}" y="${560 - y * 80}" width="80" height="80" fill="none" stroke="#8da8bd" stroke-width="${1 / zoom}" stroke-dasharray="${5 / zoom} ${5 / zoom}" opacity=".65"/><text x="${x * 80 + 3 / zoom}" y="${560 - y * 80 + 12 / zoom}" fill="#bacbda" font-size="${10 / zoom}">Z${x},${y}</text>`;
 const target = card.decision.target.tile, targetColor = card.decision.badBait ? '#be78ff' : colors[meta.capturedRole];
 if (target) {
  const [tx, ty] = mapXY(target);
  out += `<path d="M${px} ${py}L${tx} ${ty}" fill="none" stroke="${targetColor}" stroke-width="${1.5 / zoom}" stroke-dasharray="${5 / zoom} ${5 / zoom}" opacity=".75"/>`;
 }
 mapEntries = tileEntries(card);
 for (const [key, entries] of mapEntries) {
  const [x, y] = mapXY(entries[0].tile), occupants = entries.filter(e => e.kind !== 'target'), hasTarget = entries.some(e => e.kind === 'target');
  const visible = (occupants.length ? occupants : entries).slice().sort((a, b) => (a.kind === 'player' ? -1 : b.kind === 'player' ? 1 : 0));
  // Counted stacks show the different occupants without painting them over one another.
  let mark = hasTarget ? `<rect x="-5" y="-5" width="10" height="10" rx="1" fill="none" stroke="${targetColor}" stroke-width=".8" stroke-dasharray="1.5 1"/>` : '';
  if (visible.length === 1) {
   mark += mapIcon(visible[0]);
   if (visible[0].quantity != null) mark += quantityBadge(visible[0].quantity);
  } else {
   mark += '<rect x="-4.8" y="-4.8" width="9.6" height="9.6" rx="1.2" fill="#17202a" stroke="#c1ccd4" stroke-width=".6"/>';
   visible.slice(0, 4).forEach((entry, i) => { mark += `<g transform="translate(${i % 2 ? 2.3 : -2.3},${i < 2 ? -2.3 : 2.3}) scale(.44)">${mapIcon(entry)}</g>`; });
   mark += `<rect x="1" y="-6" width="5.8" height="4.8" rx="1.3" fill="#e0e9f0"/><text x="3.9" y="-2.4" text-anchor="middle" fill="#111923" font-size="3.8" font-weight="800">${visible.length}</text>`;
  }
  if (visible.length > 1 && visible.some(e => e.kind === 'player')) mark += `<rect x="-5" y="-5" width="10" height="10" rx="1.2" fill="none" stroke="${colors[meta.capturedRole]}" stroke-width="1.3"/>`;
  const label = `Tile ${entries[0].tile.x & 63}, ${entries[0].tile.y & 63}: ${entries.map(e => e.name + (e.quantity != null ? ' × ' + e.quantity : '')).join('; ')}`;
  out += `<g class="map-hit" data-map-key="${key}" transform="translate(${x},${y})" tabindex="0" role="img" aria-label="${esc(label)}">${mark}<rect x="-5" y="-5" width="10" height="10" fill="transparent"/></g>`;
 }
 svg.innerHTML = out;
 const xmin = Math.floor(vx / 10), xmax = Math.ceil((vx + span) / 10) - 1, ymin = 64 - Math.ceil((vy + span) / 10), ymax = 63 - Math.floor(vy / 10);
 $('mapExtent').textContent = `X ${xmin}–${xmax} · Y ${ymin}–${ymax}${zoom > 1 ? ' · centered on you' : ' · full arena'}`;
}

function quantityBadge(amount) {
 const label = `×${amount}`, width = 2 + label.length * 2.7;
 return `<rect x="${4.8 - width}" y="1" width="${width}" height="4.8" rx="1" fill="#0b1119" stroke="#8b9ba6" stroke-width=".35"/><text x="${3.9 - width / 2}" y="4.65" text-anchor="middle" fill="#fff4cc" font-size="4" font-weight="800">${label}</text>`;
}

function hideMapTooltip() { $('mapTooltip').hidden = true; }

function showMapTooltip(hit, clientX, clientY) {
 const entries = mapEntries.get(hit.dataset.mapKey);
 if (!entries) return hideMapTooltip();
 const tooltip = $('mapTooltip'), tile = entries[0].tile;
 tooltip.innerHTML = `<div class="map-tip-heading">Tile (${tile.x & 63}, ${tile.y & 63})${entries.length > 1 ? ' · ' + entries.length + ' entries' : ''}</div>` + entries.map(entry =>
  `<div class="map-tip-row"><svg viewBox="-7 -7 14 14" aria-hidden="true">${mapIcon(entry)}</svg><div><strong>${esc(entry.name)}</strong><small>${esc(entry.detail).replace(/\n/g, '<br>')}</small></div>${entry.quantity != null ? `<b class="map-tip-quantity">×${entry.quantity}</b>` : ''}</div>`).join('') +
  (entries.some(e => e.kind === 'npc') && $('showNpcDebug').checked ? '<div class="map-tip-note">? = inferred runner state. Poison forecast assumes no further feeding, damage, healing or stuns.</div>' : '');
 tooltip.hidden = false;
 const margin = 12, rect = tooltip.getBoundingClientRect();
 let x = clientX + 16, y = clientY + 16;
 if (x + rect.width > innerWidth - margin) x = clientX - rect.width - 16;
 if (y + rect.height > innerHeight - margin) y = clientY - rect.height - 16;
 tooltip.style.left = `${Math.max(margin, x)}px`;
 tooltip.style.top = `${Math.max(margin, y)}px`;
}

function initMap() {
 initArenaFilters();
 const svg = $('map');
 svg.addEventListener('pointermove', event => {
  const hit = event.target.closest('.map-hit');
  if (hit) showMapTooltip(hit, event.clientX, event.clientY); else hideMapTooltip();
 });
 svg.addEventListener('pointerleave', hideMapTooltip);
 svg.addEventListener('focusin', event => {
  const hit = event.target.closest('.map-hit');
  if (hit) { const rect = hit.getBoundingClientRect(); showMapTooltip(hit, rect.right, rect.bottom); }
 });
 svg.addEventListener('focusout', hideMapTooltip);
 svg.addEventListener('keydown', event => { if (event.key === 'Escape') hideMapTooltip(); });
 svg.onclick = event => {
  if (!current || event.target.closest('.map-hit')) return;
  const rect = svg.getBoundingClientRect(), view = svg.viewBox.baseVal;
  $('x').value = Math.max(0, Math.min(63, Math.floor((view.x + (event.clientX - rect.left) / rect.width * view.width) / 10)));
  $('y').value = Math.max(0, Math.min(63, 63 - Math.floor((view.y + (event.clientY - rect.top) / rect.height * view.height) / 10)));
  $('apply').click();
 };
 for (const id of ['showZones', 'showNpcDebug', 'mapZoom']) $(id).onchange = () => { if (current) map(); };
 window.addEventListener('scroll', hideMapTooltip, true);
 window.addEventListener('resize', hideMapTooltip);
}
