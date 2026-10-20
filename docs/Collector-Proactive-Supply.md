# Proactive red supply and local Collector movement

A large carried red reserve now adds loading value even when no escaping runner or recent
cannon shot is visible. Inventory and bag reds both count. The term is strategic supply,
not an assertion that an unseen runner is escaping; it retains the normal `col.load` task
and maintenance tier unless actual shooting/escape evidence warrants support.

The added score is `65 × fittingReds × shortage × reserve / (reserve + 1)`, where
`shortage = ((5 - loadedReds) / 5)²` and `reserve = (heldReds / 8)⁶`.
This sharply favours supplying a low-red cannon from a large reserve without making the
first few reds worth an immediate trip. Full red hoppers contribute zero. If the game
confirms all runners and healers are finished, the proactive term is zero; absence from
the visible scene alone does not establish that clear. Existing nearest-hopper selection,
inventory pressure, blue windows, travel, collection opportunity and self-shooting costs
still compete normally. The weights are tuning choices, not learned outcome predictions.

Ordinary egg pickups and clearing inventory for an egg are limited to **12 walking tiles**.
Within that range, an additional distance penalty begins beyond six tiles and smoothly
reduces the value of a long detour. Travel uses collision-based walking distance when
available, with the replay's existing straight-line fallback otherwise. Nearby target
continuity and call/despawn deadlines remain. Queen yellow eggs retain their separate
collection policy; wave-10 ordinary eggs use this same local rule.

## Short centre checks

After the opening, when useful ammo is loaded and runners/healers may remain, idle time
can suggest **Check a little toward the centre**. This competes with nearby pickups and
loading; it does not interrupt valuable early red collection. It moves toward the central
arena from the current position, with a maximum **six walking tiles** per check. A marker
whose walk exceeds that limit is rejected. Being within eight tiles of the central anchor
already provides sufficient positioning for this suggestion.

`col.scan` keeps its original marker while moving instead of moving the goal every tick.
Reaching within one tile records completion in `TaskMemory`. The completed check is retained
for the wave, with a **50-tick cooldown** before another is eligible. Ignored checks expire
after 16 ticks; an interruption longer than 12 ticks also releases the old marker. Both
cases retain the cooldown. Merely listing a check as an alternative does not start it or
its cooldown. The check is disabled during the opening, when useful ammo is absent, after
confirmed target completion, and during the Queen phase. It never claims full arena vision
or asks the Collector to chase an unobserved runner.

## Capture validation

All **289 tests pass across 63 saved captures**. New checks cover proactive bag/inventory
supply without visible Penance or recent shots, low-reserve red collection, full hoppers,
confirmed clears, long pickup/discard trips, collision walking costs, fixed check markers,
arrival/cooldown, ignored checks and yielding to nearby useful eggs. Human-labelled wave-2
ticks 569 (nearby reds with four held) and 682 (nearby blues rather than loading two greens)
still agree with their saved preferences.

Capture examples:

- `2026-10-18_13-26-38-wave2.jsonl`, tick 605: ten reds held, zero red ammo, no visible
  escaper and no recorded recent shot. Guidance now loads as `col.load / MAINTENANCE`.
- `2026-10-15_20-41-28-wave7.jsonl`, tick 1602: the previous 27-tile green pickup is rejected.
- `2026-10-15_20-26-34-wave1.jsonl`, tick 222: an idle check uses a six-tile marker.

Across **6,344 Collector arena decisions**, ordinary egg trips over twelve tiles fall from
37 to zero. Late pickups, unnecessarily far equivalent eggs, far shared hoppers and nearby
target switching remain zero. No centre check exceeds six tiles. Frozen summaries are
[before](review-evidence/collector-proactive-navigation-before.csv) and
[after](review-evidence/collector-proactive-navigation-after.csv).
The cannon audit still reports zero planner invariant violations. Large-reserve loads
(at least eight held reds, at most two loaded, no visible escape/recent shot) rise from 71
to 103 guidance ticks on the same observations. These are ticks, not distinct trips.

The replay preserves actual positions, inventory and later events. It does not execute a
suggested move, reveal NPCs outside the original view, or prove faster waves. The next live
run should check whether these bounded moves reveal useful threats and whether loading
frequency feels appropriate while retaining scarce early reds.

```powershell
.\gradlew.bat test jar recorderJar auditCollectorNavigation -PcapturePrefix=all '-Poutput=build/reports/collector-proactive/navigation-after' -PbaReviewJar=true -PbaReviewClassifier=collector-proactive -PbaReviewUiKitClassifier=collector-proactive --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
.\gradlew.bat auditCollector '-Poutput=build/reports/collector-proactive/after' -PbaReviewUiKitClassifier=collector-proactive --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages use `collector-proactive`; captures identify `collector-proactive-2026-10-18`.
The simulator on port 23771 has been restarted with this build. Restart/rebuild the game
client through the usual development launcher to load it. Queen batching, reserve release
and any-colour preload remain as documented in [Queen reserve](Collector-Queen-Reserve.md).
