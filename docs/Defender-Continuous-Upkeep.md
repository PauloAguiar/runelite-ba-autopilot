# Defender continuous trap upkeep

Continuous upkeep is now the default Defender strategy. Keep good food on the trap food tile,
keep repair tools available, repair the trap, then stand clear so runners can eat normally.
The guide's opening food pattern, horn calls, bait colours, required supplies and outlier
recovery remain available. There is no default instruction to build a runner stack, wait for
unseen runners to join it, or release on a call-timer mark.

## Repair and food sequence

1. If a repair tool is missing, fetch it first. When it is out of view, guidance identifies the
   missing tool and directs the player toward the known tool area rather than asking for Fix.
2. With tools ready, **Walk onto the marked food tile, then fix the trap** targets the food tile
   and displays **REPAIR SPOT** on the floor. This temporarily blocks runners for the repair.
3. On that tile, **Fix the trap** targets the trap and highlights its Fix option. The detail
   explicitly says to stay on the food tile while fixing it.
4. Once charges are restored, top up good food if needed. The base minimum is two; surplus
   called food may prepare up to four for an approaching group while retaining two in inventory.
5. **Step off the food: let runners eat** targets one tile north of the food. This leaves the
   food available without a timer or a group-size requirement. Once nearby and clear, the
   guidance watches for upkeep without repeatedly targeting the same standing tile.

Briefly occupying the food for a repair, a drop, or an empty-food wait for a fresh call is still
useful. It is not a multikill hold. The default never produces `DEF_HOLD`, `DEF_STEP` or the
timed `def.step` task. Routine reserves of an uncalled food type cannot interrupt local service
within five tiles of the trap; required called-food supplies and repair tools remain available.

A known broken trap takes precedence over food drops and maintenance, including sticky bait
tasks. This also resolves the repair/bait conflict identified in the previous wave review.
Calls and critical survival retain their global precedence. A worn trap can use a spare log
to restore full charges early; otherwise both charges are used before repairing, so the guide's
log allowance is not doubled unnecessarily.

## Optional advanced multikills

Enable **Defender → Advanced → Hold for timed multikills** to use the previous stacking
strategy. This section starts collapsed and the option defaults off. The setting is
`defenderMultiKill` in the live config and recorded planner settings. Old captures that lack
it use today's continuous default; an explicit recorded true value remains true.

Advanced mode retains arrived-group checks, two known charges for a multi-release, timed
release marks and the final-runner one-charge exception. Its repairs also use the explicit
repair-position phase and cannot lose priority to bait on a broken trap. Its post-repair text
keeps the player holding until a release prompt, rather than using the default's step-aside text.
The replay footer identifies the active Defender strategy.

## Capture validation

Nine completed captures from the latest 19:32–19:48 run (waves 1–9) were added to the fixtures.
All **323 tests pass across 84 capture fixtures**. A paired audit checks **4,892 Defender ticks
across 26 captures** under both modes, using independent instruction history on the same
recorded observations:

| Observation | Continuous default | Advanced multikills |
| --- | ---: | ---: |
| Holding prompts | 0 | 362 |
| Timed multi-release prompts | 0 | 3 |
| Repair-position prompts | 309 | 348 |
| Fix prompts while off the food tile | 0 | 0 |
| Other work while the trap is broken, excluding calls/survival | 0 | 0 |
| Routine reserve trips near the trap | 0 | 24 |

These are repeated prompt ticks, not unique clicks or predicted outcome improvements. The
latest wave-9 capture `2026-10-18_19-48-15-wave9.jsonl` now targets the repair tile at **1682**,
then the trap's Fix action at **1686**. In the earlier wave 6, **4199** repairs rather than
dropping bait onto a broken trap. Wave-2 **3634** watches the ready trap instead of detouring
for Worms. Wave-8 **4669** adds one Tofu to reach four good food under the default policy.

The [frozen comparison](review-evidence/defender-continuous-modes.csv) can be reproduced with:

```powershell
.\gradlew.bat compareDefenderModes '-Poutput=build/reports/defender-continuous/modes' -PbaReviewUiKitClassifier=defender-continuous --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Packages use `defender-continuous`; new captures identify `defender-continuous-2026-10-18`.
The simulator has been restarted with this build. Reload its tab. Rebuild/restart the game
client through the usual development launcher for live changes. Check the floor label before
Fix, completion of repairs before bait work, and that runners can eat without waiting for a
stack-release instruction.
