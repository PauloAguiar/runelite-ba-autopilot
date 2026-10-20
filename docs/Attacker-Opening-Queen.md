# Attacker opening and Queen cannon readiness

The Attacker now takes the same western opening corridor as the Collector: west around the
cannon hill, then north behind it. For ranged, missing arrows take precedence. The route works
before an incoming call and can be carried underneath the opening horn call. It applies to
the initial approach from the southern dispenser corridor, before fighting starts. Reaching
the northern side records completion for the wave. Later movement, kills, an active engagement
or the end of the first call window do not restart this opening route. The intended benefit,
requested after the latest run, is to reduce pulling Penance Healers toward the left side;
that effect still needs live validation.

## Wave-10 phase ordering

Missing arrows are requested before the first spawn, including wave 10. Spikes are not a
candidate while any Fighter/Ranger remains or more are expected to spawn. Once the game's clear
messages or the wave's kill totals establish that both types are finished, spike preparation
can begin during downtime before the Queen. Ordinary fighting retains its existing call,
equipment, hold, target splitting and target continuity rules.

During the Queen phase, poisoned eggs are still spiked and spiked eggs are still passed first.
Confirmed own spiked hand-offs are counted. Consuming spikes for those eggs does not create a
new obligation to refill to eight while waiting: further spikes are requested when another
poisoned egg actually needs them. This prevents the previous return-to-mushroom instruction
from competing with cannon preparation after the Attacker has finished an egg batch.

## Readiness cue and Omega button

After the Attacker's current eggs are processed and passed, an observed final hand-off from
Defender to Collector can cue **Get ready at the cannon: spam Omega**. The observation requires
known roles, a Defender's directed use animation toward that Collector, and adjacent positions
in the Queen phase. RuneLite's public `HUMAN_PICKUPTABLE` animation is used for the reach;
roles, target and adjacency disambiguate it from an arbitrary nearby animation. This is a
readiness cue, not a direct reading or exact count of the Collector's inventory.

Loaded omega ammo is an independent fallback when the hand-off is out of view or lacks the
required context. Observed omega consumption or a hand-off keeps preparation active between
loads rather than returning to spikes/the pool. The aiming-window highlight points at
**Omega → Queen** even during that wait. If the cannon has no egg of any colour, the instruction
explains that a load must enable Shoot first. The Collector's existing any-colour preload
guidance supports pre-clicking before omegas are loaded.

The cue works with the normal/default Queen configuration; explicit team stacking also remains
available. Current egg work and critical survival still take precedence. Observed Queen death
ends the cue, and all progress resets at the next wave.

## Recording and replay validation

New player records add the observed role and anonymised interacting-player name alongside the
existing position/animation. Live tracking and replay feed the same hand-off detector. Older
captures omit those fields, so replay uses ammo evidence without inventing a transfer. Native
`SPAM` pass confirmations and `MESBOX` notices are treated as game messages, consistently in
live tracking and replay; public chat does not confirm an egg pass.

All **296 tests pass across 65 captures**. Focused checks cover both arena openings, arrows
before spikes, gaps between regular spawns, completion/reset, own egg work before cannon work,
hand-off prerequisites, ammo fallback and the aiming highlight between loads. A constructed
scenario adds explicit role/interaction observations to an old capture to verify the earlier
hand-off path; those observations are not claimed as part of the original recording.

The new fixtures are `2026-10-18_17-27-52-wave1.jsonl` and
`2026-10-18_17-51-27-wave10.jsonl`. At wave-10 tick **2422**, the old client requested spikes
without arrows; replay requests arrows. At **2918**, three spiked pass confirmations prevent
an unnecessary spike restock. At **2986**, loaded omegas cue the cannon, and **2989** retains
the cue after the ammo falls to zero. The wave-1 replay uses the opening route and does not
restart it after regular kills. The capture-wide invariants also pass.

```powershell
.\gradlew.bat test jar recorderJar -PbaReviewJar=true -PbaReviewClassifier=attacker-opening-queen -PbaReviewUiKitClassifier=attacker-opening-queen --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages use `attacker-opening-queen`; new captures identify
`attacker-opening-queen-2026-10-18`. The simulator on port 23771 has been restarted on the latest
Attacker Queen capture. Restart/rebuild the development client through the usual launcher to
load the route, cue and added recording fields. Validate the actual directed hand-off cue and
early pre-click timing next run; fixed-observation replay does not simulate improved outcomes.
