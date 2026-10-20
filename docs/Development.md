# Development

Build with a JDK 11 (`JAVA_HOME`), RuneLite 1.13.2 and the Gradle wrapper. `gradlew test` runs the unit
tests and replays every recorded round; `gradlew run` starts a developer-mode client with BA Autopilot and
the debug recorder loaded.

## Layout

```
model/    Role, BaCall (every call, its horn option and item), WaveInfo (counts, healer HP, poison
          math, kill order), ArenaTiles (template tiles + food patterns), HealCode (parser + codes),
          PenanceType, BaIds
state/    BaTracker (client events and widgets -> RoundLogic -> Snapshot), RoundLogic (the round's
          client-free bookkeeping, also driven by the replay harness), CallClock (50-tick call
          cycle, re-anchored on HUD flips), ListenResolver, HealerLedger (spawn order + food per
          healer per call), DefenderArea (protected holding area), Snapshot and its views
plan/     Planner (pure: Snapshot + Settings -> Instruction) and one planner per role + the queen
overlay/  InstructionOverlay (the BA Autopilot overlay), SceneOverlay (target + plan tiles),
          ItemHighlightOverlay
tutorial/ The in-game tutorials: pages, lobby props (tutorial-layout.json), real overlay situations
ui/       BaAutopilotPanel (role settings, collapsed advanced options, BA levels and Honour points)
ui/kit/   The FreeArcanes UI kit components the panel uses, copied in (the Plugin Hub builds this
          repo on its own, so it can't depend on the separate ui-kit project)
```

The planner is pure and covered by unit tests (`gradlew test`). Run a dev client with the plugin
loaded via `gradlew run` (JDK 11).

A developer-mode client (`--developer-mode`) also reads the tutorial layout from
`~/.runelite/ba-autopilot/tutorial-layout.json`, created from the bundled copy the first time, and
places the props again whenever that file is saved. Other clients always use the bundled layout.

The Plugin Hub's review bot refuses more than 200,000 tokens of `src/main/java` with comments
removed (string literals count, resources and tests don't).

## Recording a round

The unit tests use hand-built snapshots. To check the plugin against the real game, record a wave
and replay it. The recorder is a separate debug plugin in **test sources only**
(`src/test/java/.../dev/BaRecorderPlugin`). It never goes into the hub jar, so keep it out of
`src/main`.

1. Start a dev client with the recorder loaded: `gradlew run` here, or the dev-launcher (it pulls
   the recorder in through the `devAudit` variant, like GPS's transport audit). "BA Autopilot
   Recorder (Debug)" shows in the plugin list and is on by default. It records only in a
   developer-mode client (`--developer-mode`; `gradlew run` passes it).
2. Play a wave. In the lobby and the arena it writes one JSON line per tick to
   `~/.runelite/ba-autopilot/recordings/<timestamp>-wave<N>.jsonl`. The file is closed when you
   leave the arena. Each line holds the raw inputs: role HUD and call texts, Horn of Glory texts,
   game messages and horn calls, penance NPCs, tracked ground items and objects, inventory, ammo,
   egg and exit varbits, HP and tile. It also logs clicks, spawns, deaths and healer poison
   graphics, plus what the live plugin showed. Player names become `Me` / `PlayerN`. Other
   players' words are never recorded: public chat and overhead text that isn't a horn call, and
   all clan, friends, private and trade chat, are left out.
3. Look at it: `gradlew replay -Pfile=path/to/file.jsonl` prints the call texts and chat
   tick by tick next to the current code's instruction, and what the live plugin showed where they
   differ. Add `-Pall` to print every tick.
4. Keep it: copy the file into `src/test/resources/recordings/`. `gradlew test` replays every file
   there through `RoundLogic` and the planner. `RecordedRoundsTest` then checks:
   - the role is detected within 2 ticks of its HUD appearing;
   - every horn chat line addressed to your role becomes your call on that tick;
   - each shout-text flip falls within ±2 ticks of wave message + 1 + 50k (this checks the call
     clock offset). The measured offsets are printed either way;
   - as healer, the ledger's food count matches the correct foods that left your inventory onto a
     healer, plus at most one per other poison graphic;
   - no instruction outlines an item of a different call than the one just heard.

Recordings made before 2026-10-19 were scrubbed the same way before the repository was published.

## Not yet verified in game

These are coded from the reference plugins and the wiki, but haven't been watched live:

- **Call clock:** the first call starts 1 tick after the `---- Wave: N ----` message. The clock
  re-anchors when the HUD's call-to-give text changes.
- **Your call:** the plugin takes the newest of three sources: the horn's chat line, which is
  addressed to the receiver ("Healer: Poison worms!" is the defender's call to the healer), the
  Horn of Glory interface, and the role HUD. Right after a call change, until a new call is heard,
  call-dependent actions wait for a fresh instruction. There is no timed fallback to the old call.
  Diagnostics shows which source was used.
- **Healer food counting:** your own feeds are counted from a use-on-healer click plus the item
  leaving your inventory. Actual poison damage establishes poison separately, including poison
  applied by a teammate. Spot-animation 863 alone never counts as food or active poison.
- **Persistent highlights:** current-call food, arrows and eggs remain highlighted during other
  actions. Defender bait is labeled GOOD (green) or BAD (purple), including both uncalled types.
  Stale calls clear these classifications. Calls remain visible in the compact instruction box.
- **Attacker call warning:** a gray DON'T ATTACK badge appears over your current enemy, or your
  last visible target when disengaged, while the incoming call is missing or stale. A fresh call
  clears it. The badge also replaces an old ATTACK marker during a HUD update.
- **Bait goodness:** bait is marked good if it matched the call when it appeared on the ground.
  Bait that was already on the ground when you arrived counts as bad.

## Reliability review and strategy evaluation

The [code review and simplification plan](docs/Barbarian-Assault-Review-and-Plan.md) identifies current
reliability issues. Run `gradlew compareStrategies` to compare experimental decisions across captured
waves; see [capture-based evaluation](docs/Capture-Strategy-Evaluation.md) for results and limitations.
These comparisons audit historical decisions; they do not simulate changed game outcomes.
The [next-run validation notes](docs/Next-Run-Validation.md) describe the implemented fixes and what
to check after restarting the development client.

Run `gradlew simulateScenario` for a local capture library, filtered by wave, recorded role and
recorded role level. It evaluates only the role captured, with observed teammates shown as
positional context. Scrub ticks, change positions/calls, inventories or shared world inputs,
and inspect targets, candidate priorities and pending tasks. Role colors match the game. See the
[simulator guide](docs/Scenario-Simulator.md) for the full launch command and limitations.
The replay map has hover-only tile details, counted stacks, recognizable food/egg/tool symbols,
quantity badges and 1×/2×/4× zoom. Its optional fixed 8×8 grid and NPC diagnostics show runner
state estimates and wave-based healer HP/poison forecasts. The same diagnostics appear in-game
under **Show priorities (debug)**; see [their interpretation and sources](docs/Penance-Diagnostics.md).
The [last Collector run evaluation](docs/Last-Collector-Run-Evaluation.md) covers the stricter
cannon interrupt rule and its before/after capture evidence.
The [weighted Collector guide](docs/Weighted-Collector.md) supersedes those phase priorities:
early red scarcity, diminishing reserve value, batches, travel and runner threats now compete on
scores. Shooting has low value. Replay can collect your preferences on close egg/loading choices
and fit a weight proposal against those labelled capture states.
The [Defender target-stability evaluation](docs/Defender-Target-Stability.md) covers committed
pattern stops and tools, including the captured Safety and hammer proximity switches.

Unfinished role tasks now retain their progress through calls, healing, and supply interruptions.
The planner still shows one primary action and rechecks its prerequisites each tick. With heal
codes enabled, guide entries only break ties between equally urgent targets: feed unpoisoned
healers first, including those out of view, then refresh poison at 1, 2, and 3 in that order.
Animation alone never counts as a feed; poison damage corrects estimates. Guide times cannot
delay needed food or force full-strength feeds. See the [latest Healer evaluation](docs/Healer-Poison-Priority.md).
Team-list order no longer assigns a duo spammer. The priorities
debugger shows each pending task's status,
and new debug captures record those statuses alongside the selected task.

