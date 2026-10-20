# Defender food identity, preparation and uncertain runners

## Mainstack food changing colour

The completed wave-6 capture `2026-10-18_18-08-35-wave6.jsonl` reproduces this at tick **4263**.
Worms dropped correctly at the mainstack during the Worms call (ticks 4161–4163) were later
removed and recreated among a large batch of scene-item events. The player was far south by
the dispensers, inventory did not change, and there was no Drop click. The replacement ground
items received new identities while the incoming call was Crackers. Treating every ItemSpawned
as a fresh drop incorrectly classified the two surviving Worms as bad and inflated landed-food
progress. This was a tracker error. Ground bait's original quality persists across calls, as
described in the [OSRS Wiki strategy guide](https://oldschool.runescape.wiki/w/Barbarian_Assault/Strategies).

Live tracking and replay now share a small replacement cache. A despawn followed by a matching
spawn in the same tick transfers the item's original quality and drop time, without counting
another landing. Matching requires the same item, tile and quantity and consumes each removed
entry once. The cache expires at the next event tick and resets with the wave. It does not
retain vanished food indefinitely. An observed Drop click near the player instead establishes
a genuinely new drop, including a pickup/re-drop during a different call. The two-tick click
window and two-tile allowance cover a queued drop while moving. Events cannot fully establish
the identity of an unobserved other player's simultaneous pickup and re-drop.

At tick 4263, replay now retains **2 good Worms**, original drop tick 4162, and the mainstack's
landed-good count remains four. Tests also preserve originally bad food through replacement,
reject stale/different-tile matches, reset on the next wave, and reclassify real new drops.

## Prepare trap food while holding

When standing on the trap food with two charges and runners still approaching, spare called
bait can prepare enough good food for that arriving group. This stays on the occupied tile,
keeps two of that bait in inventory, stops once the group is covered, and never creates a
supply trip just for preparation. Missing/expired calls suppress it. Ready multi-releases and
required repairs remain ahead of this optional work.

At wave-8 tick **4669** in `2026-10-18_18-13-19-wave8.jsonl`, the trap holds three good food,
five runners are approaching or unseen, and six Tofu remain in inventory. Replay now requests
**Top up trap food: drop 2 Tofu** rather than waiting. The target stays on the food tile.

## Wave-9 false completion

At tick **5069** in `2026-10-18_18-15-53-wave9.jsonl`, a runner's 60-tick out-of-view timeout
supplied the ninth inferred kill. The old client said **All runners are dead** through 5081.
That runner, index 63595, reappeared at **5082**. The actual clear message arrives at **5138**.

Out-of-view timeout guesses are now exposed separately as `uncertainRunners`, excluded from
`runnersKilled`, runner entries in `killsByType`, and wave-completion checks. At 5069 there are
six counted kills and three uncertain disappearances, including two earlier timeouts. At 5082
the returning runner reduces uncertainty to two without reducing the counted-kill number.
The clear message settles the count to nine and clears uncertainty. A later actual death also
settles a timed-out runner once. Nearby despawns retain their existing kill inference; this
change specifically prevents timeouts from establishing completion.

Uncertain absences still participate in spawn-accounting and final-group checks, so they do
not invent future cave spawns or stop a known final adjacent runner from eating. They do not
permit the all-dead support instruction. In the fixed replay, the trap remains active before
5138, including its existing one-charge final-runner exception.

## Validation and next run

All **313 tests pass across 75 capture fixtures**. The three reproduced cases are checked in
the running simulator as well as the replay suite. The 3,545-tick Defender trap audit retains
zero premature releases with arriving/unseen runners, zero multi-releases without two charges,
and zero on-food HOLD prompts pointing off the food. Its
[summary](review-evidence/defender-bait-memory-after.csv) is frozen separately from the earlier
trap-guard results. Fixed-observation replay verifies decisions, not alternate game outcomes.

```powershell
.\gradlew.bat test jar recorderJar auditDefenderTrap '-Poutput=build/reports/defender-bait-memory' -PbaReviewJar=true -PbaReviewClassifier=defender-bait-memory -PbaReviewUiKitClassifier=defender-bait-memory --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages use `defender-bait-memory`; new recordings identify `defender-bait-memory-2026-10-18`.
The simulator on port 23771 runs this build. Reload its tab. Rebuild/restart the client through
the usual development launcher for the next live run. Check mainstack colour after travelling
south, useful local top-ups during holds, and completion only after runner status is settled.
