# Collector Queen reserve and wave-10 ammunition

The initial Queen yellow batch remains configurable, default **5**. **Queen reserve eggs**
adds a second configurable count, default **3**, range **0–8**; zero disables the reserve.
Both controls are in the Collector's main sidebar section and the plugin configuration.

Finish passing the initial batch to the remembered Healer, then collect the reserve yellows
while the first batch moves through the egg chain. Keep those extras as yellow eggs. Passing
them is enabled only after the initial batch's omega ammunition has been spent, at least three
game ticks have elapsed since the latest observed shot, and the Queen has not been observed
dead. A partly passed batch resumes after interruptions. Carrying eight yellows does not
request passing all eight: the initial hand-off remains five, leaving three in reserve.
Receiving processed omegas still takes precedence and retains the two-tiles-south, one-at-a-time
loading instructions. Once all configured initial and reserve yellows have been passed, the
Collector waits for processed omegas rather than collecting unlimited additional batches.

## Observed progress

`QueenProgress` counts the Collector's confirmed yellow hand-offs from the pass message while
yellow eggs are held. It tracks omega ammunition consumption using ammo decreases and settled
omega-load confirmations. Load messages settle for two ticks so a delayed ammo increase does
not look like an immediate shot. Independent consumption estimates are combined as lower
bounds, not added, preventing the same egg being counted twice. A load and shot entirely
between sampled ticks can still be detected from a settled load confirmation and empty ammo.
All counters reset for each wave; the lobby's ammo reset does not count as a shot.

These are **eggs spent**, not individual simultaneous player hits. Multiple players may hit
the Queen from one loaded egg. Death/zero-HP observations stop further collection and passing;
the Queen leaving visibility alone does not count as death. The short settling pause gives
the final projectile time to resolve; it is not a prediction of unseen HP or guaranteed death.

## Ordinary eggs on wave 10

Ordinary egg guidance uses ammunition value without an egg-points incentive. Red value drops
quickly as held plus loaded reds increase. Early nearby reds can beat a premature preload trip,
then useful red batches compete with collection on travel, supply and remaining threats. Large
ground piles request only enough to bring held plus loaded reds to a hopper-sized supply (five),
or greens to two; blue pickup is at most one and normally loses to useful work. Loaded ammo
being consumed makes replenishment useful again. Other waves retain their stockpiling policy.

An empty cannon creates a preload alternative using the nearest shared hopper and any held
ordinary colour, including blue. Its weight is modest during regular fighting, rising when all
regular Penance are cleared. Before waiting for the Queen, the Collector must load an egg;
if none is held, guidance requests one safely called ordinary egg. Existing call freshness,
travel and expiry checks still apply. Any already loaded red, green, blue or omega satisfies
the aiming-interface requirement. After the regular clear, loaded ammunition stops additional
ordinary egg collection. If the Queen appears with an empty cannon and an ordinary egg held,
the preload instruction takes precedence over yellow gathering. Omega loading itself remains
the first action when processed omegas are held.

## Validation

The full suite passes **283 tests across 63 saved captures**. Focused checks cover reserve
collection limits, disabled/custom reserves, initial hand-offs with extra yellows held, the
five-shot gate and settling pause, Queen death, ammo/chat double-counting, between-tick shots,
all three preload colours, bag-only ammo, safe one-egg collection after the clear, early red
collection and reduced stockpiling even with target continuation.

The latest fixture, `2026-10-18_16-01-00-wave10.jsonl`, contains five yellow hand-off
confirmations and two spent omega eggs. A third omega remains loaded through the last arena
tick; resetting to the lobby must not make it a third shot. Replay never releases the reserve
in that recording. Tick 2969 keeps collecting the early reds despite holding green eggs with
an empty cannon. Reserve release after five spent eggs is covered with explicit state inputs,
since this capture does not contain that situation.

The Collector audit reports zero planner invariant violations across the capture library.
It preserves recorded player movement, inventory and world observations; it does not apply
counterfactual actions or prove faster Queen kills. Validate reserve collection/pass timing,
preload availability and the live ammo readings in the next game run.

```powershell
.\gradlew.bat test jar recorderJar auditCollector -PbaReviewJar=true -PbaReviewClassifier=queen-reserve -PbaReviewUiKitClassifier=queen-reserve '-Poutput=build/reports/queen-reserve-collector' --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages are `build/libs/ba-autopilot-0.1.0-queen-reserve.jar` and the matching
`ba-autopilot-0.1.0-queen-reserve-dev-recorder.jar`. New captures identify
`queen-reserve-preload-2026-10-18` and record the reserve setting. Restart/rebuild the development
client through the usual launcher to load these changes. The simulator on port 23771 uses
this policy and rebuilds progress from the raw capture events.
