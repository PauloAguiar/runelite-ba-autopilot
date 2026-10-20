# Defender targets stay with unfinished work

The Defender's broad task was already sticky, but the stop inside that task was recomputed from
position each tick. Crossing the two-tile "passing bad bait" boundary could turn Trap → Safety →
Trap without any bait being placed. The tool trip could similarly alternate between the hammer
and logs, or between two logs, when their relative distances changed.

The planner now preserves the selected stop within the task:

- A selected pattern spot remains the destination until its required bait count is observed.
  Arrival alone does not complete it. Nearby spots influence the first choice, rather than
  diverting an already selected trip.
- A chosen hammer or individual log remains selected while it is needed and visible. Picking it
  up or losing that ground item releases the choice. Another nearby item does not.
- The opening tool trip and a chosen bad-bait stop no longer displace one another as the player
  crosses the passing-distance boundary. A selected log-respawn wait persists until a log appears
  or the requirement changes.
- Waiting for the first call, an owed call, or a completed pattern can preserve the same destination
  through the transition. Broken traps, releasing a stack, survival and call requirements retain
  priority. The short-call rule can still skip unfinished bad bait to reach the remaining good bait.

## Capture evidence

Two exact regressions are saved:

- Wave 8, `2026-10-17_20-42-18-wave8.jsonl`, ticks **6324–6326**. Live guidance changes to
  **Drop 1 bad at Safety** at tick 6325 as the player passes within two tiles, then returns to
  Trap. Updated replay keeps the trap-food destination throughout.
- Wave 9, `2026-10-17_20-47-14-wave9.jsonl`, ticks **6851–6852**. Live guidance changes from
  hammer to logs as the player walks between them. Updated replay keeps the same hammer.

Evaluated the latest nine Defender waves, **2,463 arena ticks**. A separate proximity probe takes
each moving tick with a pattern/tool target, changes only the player's position to the next
recorded position, and carries the selected instruction's task and target forward. Inventory,
bait, item availability, clock, trap state and every other observation stay fixed.

| Decision measure | Before | After |
|---|---:|---:|
| Position probes | 241 | 241 |
| Target changes when only position changes | 24 | 1 |
| Avoidable changes within the same task | **23** | **0** |
| Short target flip-backs on the actual recorded trajectory | 25 | 13 |
| Short step flip-backs on the actual recorded trajectory | 19 | 14 |

The remaining position-triggered change is intentional: wave 9 tick **6964**, walking onto the
trap food changes the tools prompt to **Step one tile south: let them eat**. That higher-priority
trap action is preserved. Other flip-backs can reflect real calls, item pickups and trap changes;
the change does not suppress those observations or predict different wave outcomes.

Frozen summaries: [before](review-evidence/defender-proximity-before.csv) and
[after](review-evidence/defender-proximity-after.csv). Exact position probes are
[before](review-evidence/defender-proximity-changes-before.csv) and
[after](review-evidence/defender-proximity-changes-after.csv).

```powershell
.\gradlew.bat auditDefenderProximity '-Pdir=build/reports/defender-proximity-captures' '-Poutput=build/reports/defender-proximity-after' --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Verification: **179 tests pass**, including all **46 saved captures**, eight controlled Defender
target scenarios and the two exact capture regressions. Runtime and recorder packages use the
`defender-targets` classifier. New captures identify `defender-targets-2026-10-17`. Restart through
the usual development launcher to load the client changes; the scenario server uses the new
planner immediately after its restart.
