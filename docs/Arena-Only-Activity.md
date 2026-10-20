# Arena-only live guidance and idle work

Normal autopilot guidance is active only inside the Barbarian Assault arena. The lobby's
"Join a team" box is no longer rendered. The settings sidebar remains available.

Outside the arena, the live tracker returns a shared inactive snapshot. It does not count
inventory, read equipment/HP/egg/role varbits, scan players, rebuild scene views or create
snapshot collections. Ordinary game events return before their bookkeeping. A small presence
check remains so entering the arena automatically activates guidance. In BA's lobby, the
recruitment widget is also checked to retain the team roster needed for named Queen hand-offs.

The plugin skips settings construction, planning, pathfinding, notifications and recurring
sidebar updates while inactive. Arena overlays and the entity-render callback are unregistered,
so they do not receive frame/entity rendering work. On exit, tracking, routes, hidden NPCs and
hint arrows are cleared once. The settings sidebar retains its last observed levels/points;
those refresh when active tracking resumes. Wave history is finalised once, not on idle ticks.

Scene spawn events still accept BA NPCs/items/objects before the first arena GameTick. The
startup guard checks their template positions, so it does not discard early penance spawns.

## Tutorial exception

The tutorial remains available outside the arena under its existing lobby-room placement.
It must be explicitly enabled. Only an enabled, shown tutorial registers its overlays and
mouse listener or runs page animation/camera updates. Stopping it cleans up the scene, camera,
input listener and rendering callback once. A disabled tutorial does not allocate mouse
positions or repeatedly stop/reset animation on every client tick.

The instruction overlay has a separate non-live rendering path for tutorial examples, so the
live arena-only guard does not hide the tutorial's demonstration box.

## Validation

All **365 tests pass across 96 capture fixtures**. Added checks cover:

- 1,000 inactive game/client ticks without planner, renderer, camera or UI dependencies;
- shared idle snapshots with no inventory, HUD, varbit or player scan reads;
- unrelated instances remaining inactive and BA lobby reads limited to the recruitment widget;
- early arena NPC spawns retained before the first tick, and exit cleanup occurring once;
- the normal lobby box returning before graphics/sprite work;
- tutorial rendering and ticking outside an arena snapshot, with disabled/waiting tutorials idle.

These are work/call-count checks, not a measured CPU percentage. Lightweight location/event
guards remain, and an explicitly running tutorial intentionally performs its demonstration work.
The developer recorder is a separate plugin with its own capture workload.

The runtime jar uses classifier `arena-idle`. Rebuild/restart the client through the normal
development launcher to load it. No game client was restarted during this change.
