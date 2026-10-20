# Collector navigation, capacity and Queen batches

The October 18 Collector changes keep nearby pickup targets stable, account for the next call,
and remove repeated movement to the opening destination. Live and replay use the same planner.

Normal pickups require room in inventory and enough time for running at two tiles per tick,
three ticks of reaction/interaction margin, and one tick per egg. The earlier of the call change
and ground despawn limits a stack. The headline shows `×N` when multiple pickups fit that budget;
the detail also shows the full pile quantity. Full inventory still blocks collection even if
the collection bag has room.

Pickup quantities also reach the world badge through `Instruction.pickupCount`. Previously
only the instruction headline contained the quantity: `SceneOverlay` still drew the static
`PICK UP` tag. It now draws `PICK UP ×N` for ordinary and Queen egg stacks, using the selected
safe count. A single remaining pickup returns to `PICK UP`. The count survives an opening
call instruction carrying the pickup target. The latest fixture
`2026-10-18_15-32-31-wave1.jsonl`, ticks 799, 800, 803 and 804, verifies `PICK UP ×2` from the
same captured green piles that already had `×2` in their recorded instruction headlines.

Expiry contributes at most two score points. Travel costs 1.1 points per red/green tile and
0.65 per blue tile. Continuing the same colour and tile adds 18 points within two tiles, four
en route for red/green, and two en route for blue. A missing pile, changed call, or insufficient
time removes the candidate. Both hoppers receive the same supply-threat score because their
ammunition is shared, so distance selects the nearer hopper. A shooting rescue's credit decays
with travel (`exp(-walk/12)`); long Collector shooting trips lose to nearby work or waiting.

Opening guidance takes the western corridor around the west hill, then north behind it, even
before an egg call arrives. Reaching the northern area completes that objective for the wave.
After the first call, it expires anyway. Later waits have no fixed movement destination.

Inventory and bag counts are displayed separately by colour. Clicking Empty on the bag now
waits for actual inventory gains before subtracting bag contents; a rejected full-inventory
empty leaves them intact, and partial transfers subtract only the transferred colours. Bag
loading still uses the observed bag-first behaviour. A timestamp overflow that prevented
fallback colour attribution before any recorded Take was also corrected.

During a red call with two or fewer free slots, nearby reds that remain reachable after a
discard allow blue/green inventory disposal candidates. Their weights include inventory
pressure, red scarcity, the value of the discarded egg, and travel. Blues are discarded first
when both are present. Once room exists, gathering red normally wins. Bag-only greens cannot
create this action because deleting inventory eggs does not remove bag contents.

## Queen Collector

**Collector: Queen yellow batch** defaults to **5**, with a range of 1–8. Collect the batch
before passing; full inventory permits a smaller hand-off. Once passing starts, finish the
held batch without returning to gathering after each individual pass. A remembered recipient
keeps the same Healer across roster ordering changes and later batches. If that player no
longer has the Healer role, choose another known Healer deterministically.

Before and during the Queen, a Collector waiting for yellow drops uses the watch position
`(28,25)`, moved ten tiles south from the initial `(28,35)` at the user's request. Within two tiles it has no movement
target. This is a visibility aid, not a prediction of an unseen drop. Low-HP survival can still
interrupt. Receiving omegas takes precedence over gathering yellows.

**Collector: one omega at a time** defaults to **on**, independently of the existing team
stacking option. First stand exactly two tiles south of the nearest hopper. Use one omega on
the hopper from there, wait while one is loaded, then use the next after the loaded count falls
to zero. Omega inventory highlights remain through movement and waiting. Explicitly disabling
single loading preserves the old bulk-loading mode when team stacking is also off.

## Validation

**274 tests pass across 62 saved captures.** New tests cover target continuity/release,
call/despawn deadlines and stacked quantities, shared hoppers, distant shooting, opening
completion, colour-specific bag transfers, green disposal, Queen batch completion through
interruptions, recipient retention, and the loading position/wait/load sequence.

The fixed ordinary-wave audit uses the eight `2026-10-18_13-` fixtures for waves 1–8,
with 1,914 arena decisions. Frozen summaries are
[before](review-evidence/collector-navigation-before.csv) and
[after](review-evidence/collector-navigation-after.csv).

| Flagged guidance ticks | Before | After |
|---|---:|---:|
| Pickup too late for running plus margin | 110 | 0 |
| Equivalent egg over four tiles farther away | 12 | 0 |
| Shared hopper over two tiles farther away | 4 | 0 |
| Switching from a still-feasible pile within two tiles | 14 | 0 |

Exact regressions include wave 3 tick 917 (near egg), wave 5 ticks 1246–1248 (near hopper),
and wave 1 tick 365 (stack quantity). The new Queen fixture is
`2026-10-18_13-48-27-wave10.jsonl`: tick 3135 waits north, tick 3159 keeps gathering at 1/5
instead of passing the first yellow, and tick 3424 positions for single loading instead of bulk
loading. Synthetic state transitions verify finishing a five-egg pass and waiting for each shot.
The capture followed the old one-at-a-time pickup strategy, so replay does not invent a
five-egg inventory or its subsequent outcomes.

The ordinary captures used here have no zero-free-slot bag Empty example. That failure and
green disposal are covered by focused state tests, rather than claimed as observed recoveries.
The audit retains actual positions and world observations; it does not prove faster waves or
predict how many eggs the changed guidance would collect. Live collision distances can differ
from the replay's tile-distance fallback. Validate the western route, yellow visibility and
two-south loading position in the next client run.

```powershell
.\gradlew.bat test jar recorderJar auditCollectorNavigation -PbaReviewJar=true -PbaReviewClassifier=collector-pickup-label -PbaReviewUiKitClassifier=collector-pickup-label '-Poutput=build/reports/collector-navigation/after' --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages including the world pickup-label fix are `build/libs/ba-autopilot-0.1.0-collector-pickup-label.jar` and
`build/libs/ba-autopilot-0.1.0-collector-pickup-label-dev-recorder.jar`. New captures identify
`collector-pickup-label-2026-10-18` and record both new Queen settings. The simulator was restarted
on port 23771 with this build; restart/rebuild the game client through its usual launcher for
the overlay changes.
