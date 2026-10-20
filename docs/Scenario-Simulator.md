# Capture scenario simulator

Collector centre checks are removed, and waiting near attackers yields to eligible pickups.
See [visible eggs](Collector-Visible-Eggs.md). The saved Collector wave-9 capture
`2026-10-19_21-43-46-wave9.jsonl`, tick **2482**, now picks up red instead of waiting;
tick **2507** picks up a blue stack. Calls, pickup deadlines and wave-10 supply limits remain.

The planner includes [Healer reserve finishing](Healer-Reserve-Finishing.md). Select the latest
Healer wave-10 capture (`2026-10-18_20-38-20-wave10.jsonl`) and tick **3230** to inspect the
`heal.reserve` decision: finish healer #1 while three reserves still need kills. Coverage and
weak-poison refreshes still preempt finishing. The capture library also includes waves 1–9 of
that Healer run. Replay evaluates decisions on the recorded path, not counterfactual kills.

Defender now defaults to [continuous trap upkeep](Defender-Continuous-Upkeep.md). Old captures
without `defenderMultiKill` use that default; explicit true settings select advanced stacking.
The footer identifies the active strategy.

**Arena elements** toggles eggs, food, tools, objects, each Penance group, teammates and
last-seen markers. **Role focus** selects useful layers for the captured role; **Show all**
restores every layer. Preferences persist in the browser. Filters change the map, shared-tile
counts and hover list; all captured inputs still reach the planner.

**Unconfirmed information** lists unseen NPCs and unresolved timed-out runners with their last
observed tile and observation age. Dashed question-mark markers show those last positions.
Returning NPCs and the game's clear message resolve entries. Missing incoming calls and
unobserved Defender trap charges are listed too. Timed-out runners are not confirmed kills;
their diagnostic history does not add another live target to the planner.

Latest Collector examples and validation are in [Collector navigation](Collector-Navigation.md).
For the Queen, open `2026-10-18_13-48-27-wave10.jsonl`: tick **3159** gathers at **1/5** rather
than passing immediately, and **3424** shows the two-tiles-south omega loading position.
Old captures without the new settings use today's defaults: five yellows per batch and single
omega loading enabled. Their recorded `queenStack` setting still controls team stacking.

Collector candidates now show weighted totals and score components. The **Tune Collector choices**
panel saves your egg-versus-loading preference for a captured tick; **Next close choice** jumps
to comparisons with similar scores. Reset scenario edits before labelling. See
[weighted Collector tuning](Weighted-Collector.md) for the calibration command and evidence.

The captured role card shows a **Decision weights** section whenever candidates are
scored, for both Collector and wave-10 Healer. It lists every scored choice in order,
marks the winner, shows the gap to each alternative and opens the winner's component
table. Expand another choice to compare its contributions. Scenario edits update the
scores. Roles using tier priorities do not display invented numerical weights.

Wave-10 Healer decisions expose weighted health, supplies and egg candidates.
Open `2026-10-18_12-33-39-wave10.jsonl` at tick **3764** for healing before dunking,
**3639** for passing before a routine 48/85 top-up, or **3786** for self-healing.
Ticks **3581–3585** keep the dunk trip through the recorded HP drops. Score tables
show the up-to-12-point continuation margin, nonlinear HP value and small recent
damage incentive. Filler top-ups and routine vial readiness have zero continuation
value; tick **3575** includes the 96% teammate example. HP loss averages use consecutive observations in the previous
20 ticks. Advanced role JSON accepts `selfDamagePerTick` and the name-keyed
`teammateDamagePerTick` map. Editing HP alone retains captured damage pressure.
Advanced JSON accepts observed `playerTiles` by name for
positional care comparisons. See [Queen Healer care](Queen-Healer-Care.md).

`simulateScenario` serves a local capture library and runs the current `Planner` for **the role
actually recorded**. Other observed players appear on the map as positional context; no decisions
or private inputs are synthesized for them. The tool lives in test sources and is excluded from
both plugin and recorder packages.

From `Z:\runelite\plugins\ba-autopilot`:

```powershell
.\gradlew.bat simulateScenario -PbaReviewUiKitClassifier=collector-wait-south --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Open the printed `http://127.0.0.1:<port>/` address. Add `-Pport=23771` for a fixed port.
The library includes saved examples in `src/test/resources/recordings` and local recordings in
`~/.runelite/ba-autopilot/recordings`, deduplicated by filename. Add `'-Pdir=another/folder'` for an
additional folder. **Refresh list** discovers captures from new runs without restarting the server.
Empty lobby-only files are omitted.

An optional `'-Pfile=path/to/capture.jsonl'` selects the initial capture and also includes captures
from its directory. Stop with Ctrl+C. The init script avoids the UI-kit jar held open by a running
development client. The simulator does not require a game client restart.

1. Filter by **wave**, **recorded role**, and **recorded role level**, then choose a capture and
   press **Open capture**. Levels come from the recorded role-level varbits, not filenames.
   Older recordings without those inputs are labelled **unknown**. Replays restore recorded
   levels into the planner inputs. Switching captures resets scenario edits.
2. Play, step, drag the timeline, or enter a game tick. The Collector wave-10 failure in
   `2026-10-17_20-10-05-wave10.jsonl` is tick **3226**. The wave-8 Healer example in
   `2026-10-17_18-24-13-wave8.jsonl` includes tick **2033**.
3. Inspect the captured role's instruction, target, tier, candidate priorities and pending tasks.
   A changed historical client instruction appears beneath the current decision.
4. Role colors are **Attacker red, Defender blue, Collector yellow, Healer green** on cards,
   player markers and target lines. Observed teammates are outlined circles, colored when their
   role is identified in the captured roster and gray otherwise. They have no decision cards.
5. Edit the captured player's position, calls, call freshness, HP, equipment or free slots.
   Clicking an empty map tile moves that player. Inspecting an occupied tile leaves the scenario
   unchanged. **Apply changed fields** overrides only fields you changed.
   Loaded ammunition, last-shot tick and trap charges affect the scenario's shared world.
6. **Assume stocked** is an explicit example inventory for the captured role. It does not invent
   calls or equip the Attacker's arrows. Set the incoming call/freshness and ammo ID when needed.
7. Advanced JSON supports inventory, bag contents, role levels, teammate HP, NPCs, ground items,
   objects, bait history, damage and kill information. Read the current Snapshot in the role card
   and copy its field format. Item IDs are `inventory` map keys; ground items use `itemId`,
   `quantity`, `tile` and `spawnTick`. Tiles use full template
   `{ "x": 1877, "y": 5483, "plane": 0 }` coordinates, not region-local coordinates.
   Arrays and inventory maps replace entire fields. Bait-history maps with tile keys use arrays
   of `[tile, count]` pairs. Only the recorded role's private overrides are accepted.
8. Edits start at the first edited tick and persist as fixed assumptions on later ticks. Rewinding
   before that tick uses unmodified inputs. Reset edits to move the starting point. Export/import
   JSON to save the selected recording, tick and overrides. Import opens the matching library
   capture automatically.

Each evaluation reconstructs the recorded role's own instruction history, including pending tasks
and last NPC target. Rewinding and switching recordings cannot leak scenario state. Without edits,
the decision matches ordinary `RoundReplay`. Regression tests cover the Collector tick 3226 and
Healer first-feed tick 3502, position-dependent targets, call validation, rejecting uncaptured role
decisions, observed-player context, and rewinding before the first edit. Empty override lists are
JSON arrays, avoiding reflection into private JDK collection classes. Catalog tests cover metadata,
duplicates, refreshed recordings, unknown levels and rejecting arbitrary file paths.

The map offers **1×** for the full arena and **2× / 4×** centered on the captured player. Eggs use
their game colors; bait has worm, tofu and cracker shapes, with green GOOD or purple BAD rims
when the captured Defender knows its classification. Individual item marks show **×quantity**.
Tools, machines, traps, cannons, hoppers, players and NPCs have distinct symbols. Shared tiles
show a counted stack of small symbols; hover it for each occupant and its own item quantity.
Details appear only while hovering or keyboard-focusing a marker, and clear on leaving it.
Markers no longer carry permanent debug text across the arena.

Enable **8×8 zones** for the fixed runner search grid, labelled `Zx,y`. **NPC debug** adds runner
state and healer HP/poison forecasts to the hover details. Runner `?` states and candidate bait
are inferred, not exposed AI targets/timers. Healer health uses wave maximum HP and observed
health-bar bounds; poison-only kill estimates show a tick range, survival deficit, or uncertainty.
They assume no further feeding, cannon damage, healing or stun. Old recordings lack overhead
speech, so eating may lack GOOD/BAD confirmation. These diagnostics never change planner choices.
See [Penance diagnostics](Penance-Diagnostics.md) for sources and interpretation.

Collision maps were not captured, so walking uses the existing tile-distance fallback. Map lines
indicate targets, not traversable routes. Captures only contain observations available to that
client. Advancing playback follows the original observed world: suggested actions do not move
players, kill NPCs or alter later observations. This evaluates decision rules; it cannot establish
improved wave times, scores or success rates.

The server binds only to loopback and has no CORS access. It serves recordings from its indexed
library and does not interact with the game client. The UI has no external resources.
