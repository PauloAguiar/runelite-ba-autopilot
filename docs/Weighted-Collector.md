# Weighted Collector decisions

Latest: [proactive supply and local movement](Collector-Proactive-Supply.md) adds large-reserve
red loading without visible threats, short occasional centre checks and bounded ordinary egg
trips. [Queen reserves](Collector-Queen-Reserve.md) documents the wave-10 ammo and reserve policy.
The original comparison counts below remain historical.

The latest [navigation and capacity update](Collector-Navigation.md) adds call deadlines,
bounded expiry scores, stronger continuity within two tiles, nearer shared hoppers, and green
disposal to make room for reds. It also documents the new Queen batch settings. Historical
counts below remain frozen; call safety now skips pickups that cannot finish before a change.

Collector work now compares scores for every feasible pickup, hopper load, shot, blue deletion
and positioning fallback. A higher score wins across the old tiers. History adds only a small
same-target bonus en route and a larger one within two tiles; it cannot force a loading trip or alternate shooting/loading because of a
sticky task. Other roles retain their existing chooser. Owed calls, survival, fresh-call validity,
inventory capacity, cannon range and Queen eligibility remain constraints.

The next red's value falls continuously as inventory plus bag reserves grow. It also falls
gradually across call periods, giving the first and second calls more stockpiling value without
an opening/later priority switch. The reserve term currently uses
`12 + 230 × (1 + 0.8 × exp(-0.7 × period)) / (1 + heldRed / 3.5)²`.
Travel, nearby Penance and time before an egg vanishes also affect its score. Green eggs have
secondary reserve value; blues have a small spare-work value.

Loads gain value from fitting batch size, inventory pressure, a blue window, runner escape
severity, and evidence of a recent shooter. A batch preference setting supplies a smooth curve,
not a minimum-count cutoff. Full hoppers and empty inventory cannot create a load. Walking and
interacting cost value, and leaving red collection has an opportunity cost. Escape severity
increases with a visible runner's distance south of the trap, within that cannon's range.
Shooting has a substantial action cost and an additional cost while someone is already firing.
It can still win for a serious reachable threat, but ordinarily leaves the Collector gathering.

The regression scenario holds early reds and a runner south of the trap: a low red reserve
collects; eleven held reds can make loading win. After the load, gathering wins instead of
immediately shooting and loading again. These are guidance values, not damage probabilities or
predicted wave scores. Constants are initial tuning weights, not learned game mechanics.

## Capture review and labels

In a Collector replay, **Tune Collector choices** displays the best egg and load scores with
their components. **Next close choice** jumps to comparisons within 15 points, spacing examples
at least ten ticks apart. Use **Prefer eggs**, **Prefer loading**, or **Unsure / skip**. Labels
are saved by capture and tick in `docs/review-evidence/collector-preferences.json`, survive a
server restart, and can be revised. Labelling is disabled while scenario edits are active or
when either alternative is unavailable. Labels describe the captured baseline, not a hypothetical
future. Choosing Unsure does not add a training preference.

Run:

```powershell
.\gradlew.bat calibrateCollector -PbaReviewUiKitClassifier=collector-navigation --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

The calibrator compares labelled snapshots, searches red-reserve, escape and batch multipliers,
and penalizes unnecessary changes from the current values. Its proposal and per-label margins
are saved to `build/reports/collector-weight-proposal.json`. It reports how many usable labels
exist and does not silently modify runtime weights. More labels across multiple captures are
needed before treating a fitted proposal as general. There is no held-out outcome validation.

## Captured evidence

The two latest completed Collector waves have 372 arena decisions. Before this change, the live
wave-2 client spent **35 of 47 second-call red ticks** loading or firing with inventory room.
Weighted replay collects reds on all 47. At tick **569**, it chooses reds with four held and
24 free slots; that reported preference is saved as a label.

The second confirmed label is wave-2 tick **682**: during blue, with ten greens held and only
two fitting in the hopper, collect nearby blues and stay near the attackers. Current weighted
replay agrees: it selects a blue pickup that moves toward the attacker area instead of loading.

| Guidance ticks on the same captured observations | Before | Weighted |
|---|---:|---:|
| Red collection, both waves | 58 | 108 |
| Loading | 74 | 31 |
| Shooting | 55 | 13 |
| Opening red cannon detours | 35 | 0 |
| Loads with just one useful held egg | — | 0 |

Inputs are `2026-10-18_11-41-21-wave1.jsonl` and `2026-10-18_11-46-01-wave2.jsonl`. Per-tick traces
are in `build/reports/red-window/before` and `build/reports/red-window/weighted`. These are tick
counts, not distinct trips. Playback retains the original player path and subsequent world;
the comparison does not establish faster waves or more eggs actually collected.

Use **Show priorities (debug)** in-game for score totals. Replay score details and human review
work without a client restart. The game client must rebuild/restart to load `weighted-collector`
classes. New recordings identify `weighted-collector-2026-10-18`.
