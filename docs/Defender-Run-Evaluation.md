# Defender run evaluation — 2026-10-18

Update: [continuous upkeep](Defender-Continuous-Upkeep.md) now replaces stacking by default
and resolves the repair/bait conflict and local reserve detours below. This report records the
previous policy's evaluation; the new guide contains the paired mode audit and current results.

The early waves were comparatively clean. The strongest remaining guidance problems are
unnecessary reserve detours just after stepping off the food and bait work interrupting a
known broken-trap repair. Wave 6 is the clearest example of the latter. The recent hold,
charge, food-identity and timeout fixes address separate reproduced problems; passing their
checks does not establish that the whole strategy is optimal.

## Scope and observed results

The audit replays **1,918 regular-phase ticks across the latest ten Defender waves**, and
**3,545 ticks across 17 Defender captures** including historical cross-checks. The current
planner sees the original player path, observations and recorded settings. No counterfactual
movement, kills or completion times are simulated.

The first capture's filename ends in `wave10`, but its actual HUD/round is wave 1:
`2026-10-18_17-57-26-wave10.jsonl`. The others are the completed 18:02 through 18:18 captures.

| Wave | Runner clear, seconds from wave clock | Distinct runners seen far south | Assessment |
| --- | ---: | ---: | --- |
| 1 | 38.4 | 0 | No trap-service failure identified. |
| 2 | 48.6 | 0 | Unnecessary Worms-reserve detour at 3634 after leaving the holding tile. |
| 3 | 74.4 | 0 | Same reserve detour at 3744–3747; trap bait takes over again at 3748. |
| 4 | 49.8 | 0 | One empty-food tick immediately answered with bait. |
| 5 | 68.4 | 0 | Arriving-group protection and preparation apply; two empty-food ticks request bait. |
| 6 | 111.6 | 2 | Largest remaining repair/bait conflict; also contains the now-fixed mainstack identity bug. |
| 7 | 111.6 | 1 | Broken-trap intervals consistently request repairs. An escaped outlier still needs cleanup. |
| 8 | 61.8 | 0 | Preparation at 4669 is useful. Final empty-food tick occurs during a call transition just before clear. |
| 9 | 163.8 | 1 | Timeout completion fixed; repair/bait conflicts remain. |
| 10 | 72.6 | 0 | Regular phase only. Repair work or owed calls cover every broken-trap tick. |

The southbound column deduplicates NPC identities last seen below the safety area when
despawning. It is evidence of runners getting through, not the game's penalty count: the
wave-complete `teamRunnersPassed` value was not captured. Generic replay metrics count some
of these runners twice as they leave view and disappear again near the southern exit.
Runner-clear times above are not total wave times or performance predictions.

## Remaining decision problems

**Broken trap versus empty bait.** At wave-6 ticks **4195–4202**, the planner changes from
repair to **Drop Tofu on the trap food tile** while charges remain zero and tools are held.
At 4199 the player is on the food tile and the trap is adjacent, yet bait remains selected.
The actual repair completes at 4203. Wave 9 repeats this at **4927–4933** and **4960–4964**.
A repair should take precedence over routine extra bait when the broken trap is within reach;
both missing prerequisites need a coherent sequence rather than a short target switch.
This strategy change is identified for follow-up, not implemented in this evaluation.

Across the latest run's 160 broken-trap ticks, 130 request repair work or its missing tools,
10 lead with an owed call, and 20 request other work: eight in wave 6 and twelve in wave 9.
These are prompt durations on the recorded path, not proven avoidable game time.

**Reserves immediately after a hold.** At wave-2 tick **3634** and wave-3 ticks **3744–3747**,
routine **Take-Worms** reserves become primary immediately after the player leaves the food
tile. The current good call is Tofu, with seven/eight held. Within one/four ticks, trap bait
takes over again. There are **22–30 ticks left in the call**; this is not an imminent-call-change
problem. Extending trap-service continuity across the release/repair step would address these
unnecessary machine detours.

**Uncertain runner status.** Only wave 9's latest recorded client declared support before the
actual runner-clear message: 13 ticks at 5069–5081. Current replay removes all 13; no current
pre-clear support prompt occurs in any of the 17 audited captures. Other waves also contain
timed-out runners, so the new uncertainty tracker is useful beyond wave 9.

**Target stability.** There are zero position-only target changes in 274 probes across the
latest ten captures. The two historical switches both enter the occupied food tile and
correctly activate holding. Short target changes still occur through horn calls, tool pickups,
charge/bait updates and the reserve detours above. A flip count alone is not an error count.

## Reproduction and evidence

```powershell
.\gradlew.bat evaluateDefenderRun '-Poutput=build/reports/defender-run-evaluation/evaluation' -PbaReviewUiKitClassifier=defender-run-review --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
.\gradlew.bat auditDefenderProximity '-Poutput=build/reports/defender-run-evaluation/proximity' -PbaReviewUiKitClassifier=defender-run-review --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Frozen evidence: [latest run](review-evidence/defender-latest-run-summary.csv),
[all captures](review-evidence/defender-run-history-summary.csv),
[position probes](review-evidence/defender-run-proximity-summary.csv), and
[review ticks](review-evidence/defender-run-review-ticks.csv).
The tool also writes the full per-tick timeline and contiguous flagged episodes in build
reports. All **314 tests pass across 75 capture fixtures**. No strategy priorities were changed
as part of this evaluation.
