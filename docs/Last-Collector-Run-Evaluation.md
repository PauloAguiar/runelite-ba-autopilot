# Last Collector run: cannon interruptions

The previous support rule treated completed Attacker/Healer work and any targetable loose runner
or healer as a reason to supply the cannon. That was broader than the requested strategy. Its
loading task could also remain committed after the original support reason expired.

The clearest example is wave 10, **ticks 3226–3229**, in
`2026-10-17_20-10-05-wave10.jsonl`. The live client requested a hopper trip because an available
Attacker or Healer could help with strays. There were four healers, no escaping runner, no known
shot, no loaded reds and a fresh green call with more than three free slots. Updated guidance
continues collecting green eggs. Tick 3226 is now a saved replay regression and a useful starting
point for the [scenario simulator](Scenario-Simulator.md).

## Implemented rule

Aggressive cannon work now requires one of:

- A visible runner classified **ESCAPING**, in a known cannon's range. Use loaded ammunition to
  fire, or supply useful ammunition if needed. Loose, bad-bait-only, stacked and lured runners,
  ordinary healers, and role completion alone do not qualify.
- **Two or fewer reds loaded**, red eggs held in inventory/bag, and a confirmed red/green hopper
  decrease within the last **eight ticks (4.8 seconds)**. That ammunition consumption is the
  available evidence that someone is firing. An old rolling shot-rate estimate is insufficient.
  Green-only inventory cannot refill a red shortage.

The support obligation is distinct from routine loading and is rechecked each tick. It cannot
leave a normal loading commitment behind once its support evidence disappears. Calls, critical
survival and Queen work retain their existing precedence.

Routine blue-batch loads, blue shooting, full-inventory loads and near-full green loads remain.
Red collection and roomy green collection no longer turn an ordinary egg lull into a cannon
trip. A full hopper on those phases says to keep the eggs, with no cannon target.

## Replay evidence

Evaluated all ten waves of the latest completed Collector run, starting at 19:39:31 and ending
with the 20:10:05 wave-10 segment: **1,967 arena decision ticks**. The same settings, observed
player path, world and inputs feed both versions, with independent planner task histories.

| Guidance ticks | Previous source | Updated source |
|---|---:|---:|
| Load eggs | 389 | 207 |
| Fire cannon | 261 | 188 |
| Cannon interrupts at TEAM_BLOCKING priority | 345 | 274 |
| Those interrupts without either required condition | **186** | **0** |
| Collect red eggs | 289 | 362 |

These are tick counts, not unique trips. The updated rule also elevates real escaping-runner
responses, so total interrupts fall by 71 rather than 186. Wave 9 has 174 permitted interrupt
ticks, reflecting escaping runners on that recorded trajectory. No faster clear time is inferred.

Frozen per-wave summaries are [before](review-evidence/cannon-run-before.csv) and
[after](review-evidence/cannon-run-after.csv). Full local per-tick traces are in
`build/reports/cannon-run-before/decisions.csv` and `build/reports/cannon-run-after/decisions.csv`.
`auditCollector` reconstructs the shot evidence and independently checks the interrupt conditions.
The saved-capture invariant now checks the same requirement without calling the production support
predicate.

Three separate call-clock audit flags occurred before and after the change: wave 6 tick 2248
(16 ticks off rhythm), wave 7 tick 2428 (21), and wave 7 tick 2457 (-21). They are unchanged
replay clock discrepancies, not cannon violations. These newest wave-6/7 files remain local audit
inputs; the newest wave-10 file was added to the fixture set.

Verification: **169 tests pass**, including all **44 saved captures**, six new cannon scenarios,
the exact tick-3226 regression and ten simulator/catalog tests. Runtime and recorder packages use the
`cannon-evidence` classifier. New recordings identify `cannon-evidence-2026-10-17` and include
the last confirmed launcher shot tick. Restart through the usual development launcher to load
the client changes. Next run, verify ordinary leftover healers never override collection and
that a recently used low-red cannon or an escaping runner does.
