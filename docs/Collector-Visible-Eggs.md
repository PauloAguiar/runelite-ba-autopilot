# Collector visible eggs and retired centre checks

The 2026-10-19 Collector run showed two problems: a scored wait could beat a called egg
already on the ground, and centre checks added unhelpful movement. Centre checks have been
removed. The earlier [proactive supply guide](Collector-Proactive-Supply.md) remains relevant
for red-ammunition loading, but its centre-check behaviour is superseded by this change.

## Waiting is a fallback

The old `col.follow` score was zero during green/blue calls and 30% of marginal red value
during red calls. Travel, detours, crowding and right-click costs could put a feasible pickup
below that score. For example, latest wave 9 at tick 2482 offered a red pickup worth 5.69
while waiting for future reds scored 42.64. Wave 4 at tick 1301 offered a green pickup worth
-8.98, below the zero-valued wait.

The planner now caps a **COL_FOLLOW** wait below the best eligible pickup. The score breakdown
shows the adjustment as `visible egg before waiting`. Loading, shooting and inventory work
retain their existing weights and still compete with pickup value. The opening western route
and Queen preparation are separate actions and retain their existing behaviour.

Eligibility still requires the called colour, a fresh call, inventory space, at most twelve
walking tiles, and time for travel, reaction and pickup before call change or despawn. Pickup
quantity, nearest-pile value and target continuity remain. An egg being visible alone does
not make a wrong-colour, late, unreachable or unnecessary pickup eligible.

On wave 10, ordinary eggs are ammunition rather than points. Reds and greens retain their
small useful supply limits. A blue is eligible only when the cannon is empty and no held
inventory/bag egg can already enable preloading. This avoids converting the waiting fix into
pointless blue collection on the final wave.

## Centre checks removed

There is no `col.scan` candidate, centre marker, centre score, arrival condition or cooldown.
Old scan entries are dropped from `TaskMemory` rather than resumed. Idle fallback guidance
does not set a movement target. Proactive loading from a large red reserve remains available.

## Capture verification

The latest ten-wave run contains **2,480 Collector arena ticks**. On the same recorded inputs:

| Decision check | Before | After |
|---|---:|---:|
| Centre-check ticks | 108 | 0 |
| Waits while an eligible pickup is listed | 95 | 0 |
| Late pickups | 0 | 0 |
| Egg trips beyond twelve tiles | 0 | 0 |
| Nearby target switches | 0 | 0 |

Of the 95 previous waits, 66 become pickups, two become hopper loads and one becomes a
runner shot under the existing weights. The other 26 were unnecessary wave-10 blue pickups;
those are now ineligible, so waiting is appropriate. Counts are repeated guidance ticks,
not distinct trips or clicks. This replay follows the recorded inventory, player path and
later events; it does not prove a counterfactual completion time.

Exact regression examples:

- `2026-10-19_21-31-34-wave4.jsonl`: **1285** collects red, **1301** collects green.
- `2026-10-19_21-43-46-wave9.jsonl`: **2482** and **2555** collect red; **2507** collects
  a blue stack with a quantity prompt. These ticks previously said to wait near attackers.

All **355 tests pass across 96 capture fixtures**. The broader navigation audit covers
**6,809 Collector ticks**, with zero waits over eligible pickups, centre checks, late pickups,
over-range egg trips, far equivalent targets/hoppers or nearby target switches. Unit checks
also cover negative pickup scores, stale calls, wrong colours, full inventory, deadlines,
wave-10 blue limits and cleanup of old centre-marker history.

The separate cannon audit found one existing support-classification issue in the latest
wave 7 at **2048**. It already loaded on that tick before this change; it is outside this
waiting/centre fix. The loading and firing weights were not retuned here.

Frozen navigation summaries are `review-evidence/collector-visible-eggs-before.csv`,
`collector-visible-eggs-after.csv` and `collector-visible-eggs-all.csv`. Detailed latest-run
traces and input copies are under `build/reports/collector-local-before`, `collector-local-after`
and `collector-local-input`. Regenerate the full saved-library check with:

```powershell
.\gradlew.bat :test :auditCollectorNavigation -PcapturePrefix=all -Poutput=build/reports/collector-local-all-after --console=plain
```

The simulator uses the updated planner. Select the latest saved Collector wave 9 and tick
**2482** to inspect the red pickup and its competing scores. The source changes are ready for
the next development build; rebuild/restart the client to load live guidance.
