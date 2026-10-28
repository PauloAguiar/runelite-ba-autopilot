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

The Java namespace is `com.bautopilot.baautopilot`, the Gradle group is `com.bautopilot`, and the
plugin's author label is `bautopilot`.

The sidebar's levels and available Honour points use a separate `state/RoleProgress` cache, so
they load after login even outside BA. It reads four level varbits and the eight point varbits
(remainder + 512 × multiplier, as in
[BA Minigame](https://github.com/SkylerPIlot/Ba-Minigame/blob/9204c0831b02c76eb50e3e9676306b3c15f3ccf3/src/main/java/begosrs/barbarianassault/Role.java#L184)).
Relevant varbit or backing-varp events mark this cache dirty; the next game tick batches those
changes into one read. Unchanged values reuse the same snapshot and do not schedule a Swing
update. Login/scene readiness refreshes the cache, and logout or hopping clears the displayed
account data. This does not enable arena scans, pathfinding or overlays outside the arena.

## Wave detection and Defender guidance

The tracker accepts the game's `---- Wave: N ----` line before checking arena position. Like
[RuneLite's BA plugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/barbarianassault/BarbarianAssaultPlugin.java),
it must receive that line while the scene is loading;
[BA Minigame](https://github.com/SkylerPIlot/Ba-Minigame/blob/9204c0831b02c76eb50e3e9676306b3c15f3ccf3/src/main/java/begosrs/barbarianassault/BaMinigamePlugin.java)
also documents that it arrives before the role HUD. Leaving the arena clears the wave number, so
the next role HUD supplies a fallback if the start message was missed. Other chat and active
tracking remain gated to the arena. `BaTrackerIdleTest` exercises consecutive waves on one live
tracker; starting each capture with a fresh `RoundLogic` alone cannot catch a stale wave number.

The Defender's **Bait guide** dropdown offers Wiki and Gau Cho patterns, each with safety-first
and no-safety variants. The default is **Wiki - safety first**: after stocking up, head to the
safety bait south of the trap, wait for a fresh call, drop one bad bait, then lay the trap food
and remaining pattern. A short remaining first call still prioritizes unfinished good-food
spots. The no-safety variants omit both the opening safety stop and its later upkeep. Old captures
retain their recorded `safetyBait` boolean.

Advanced multikills require two charges, enough good food, and at least two visible runners
beside the food. Recently unseen runners can delay a release for one runner cycle (10 ticks);
older unseen positions remain tracked as unconfirmed but cannot block the gathered batch.
If an arriving group stops growing for 10 ticks, release that batch at the next timing mark
instead of waiting indefinitely. A lone ready runner can also be released after this wait with
one charge. Once offered, the step-off prompt persists until the player moves; stay off the
food while that batch eats, then repair or prepare the next batch.

The plugin itself does no file I/O: it reads only bundled resources. In a developer-mode client
(`--developer-mode`) the debug recorder plugin hands the tutorial an editable layout,
`~/.runelite/ba-autopilot/tutorial-layout.json`, created from the bundled copy the first time; the
props are placed again whenever that file is saved (`dev/TutorialLayoutFile`).

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

- **Bounded multikill waits:** replay and sequence tests cover stale unseen runners, late arrivals,
  singleton batches, persistent release prompts and waiting beside the trap. Validate the updated
  release timing in the next live run; replay keeps the original player's movement.

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
  Stale calls clear these classifications. Calls remain visible in the compact BA Autopilot overlay.
- **Attacker call warning:** a gray DON'T ATTACK badge appears over your current enemy, or your
  last visible target when disengaged, while the incoming call is missing or stale. A fresh call
  clears it. The badge also replaces an old ATTACK marker during a HUD update.
- **Bait goodness:** bait is marked good if it matched the call when it appeared on the ground.
  Bait that was already on the ground when you arrived counts as bad.
- **Left-click eggs:** an egg is taken by a left-click when it is the top item on its tile
  (`GroundItemView.underneath` reads the tile's item layer); otherwise the dots and the step say to
  right-click and pick Take.
- **Menu option lists** drawn in the tutorial follow each item's inventory options in the game
  cache (checked 2026-10-27): eggs, the vial and poisoned food are Use, Destroy, Examine; spikes Use,
  Drop, Examine; arrows Wield, Use, Drop, Examine; the horns' Tell- order is pinned by `HornMenuTest`.

## Tools

Each runs against the recordings in `src/test/resources/recordings` unless `-Pdir=` names another
folder. They audit past decisions on captured waves; they don't simulate how the game would have
gone with a different choice.

| Task | What it does |
|---|---|
| `gradlew replay -Pfile=<file>` | Prints a recorded round tick by tick against the current planner (`-Pall` for every tick) |
| `gradlew metrics` | Guidance metrics for every recorded wave: step and target changes, call prompt timing |
| `gradlew compareStrategies` | Current against proposed strategy decisions across recorded waves |
| `gradlew simulateScenario` | A local page that replays the recorded role with the real planner; scrub ticks, change positions, calls and inventories, and inspect targets and priorities |
| `gradlew previewPanel` | Renders the side panel without a game client |
| `gradlew auditCollector`, `auditCollectorNavigation`, `calibrateCollector` | Collector cannon use, pickup timing and travel; fits scoring weights to labelled choices |
| `gradlew auditDefenderProximity`, `auditDefenderTrap`, `evaluateDefenderRun`, `compareDefenderModes` | Defender target stability, trap releases and upkeep modes |
| `gradlew auditHealerPriority`, `auditQueenHealer` | Healer poison coverage and Queen-phase healing |

The same priority diagnostics appear in game with **Show priorities (debug)** in the plugin's
Display settings. Notes and evidence from these audits stay in a local, ignored `docs/` folder;
only this file is published.
