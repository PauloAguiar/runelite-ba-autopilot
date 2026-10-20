# Wave-10 Healer care

Absolute HP drives the current nonlinear healing score. Missing HP has only a minor
effect. Healthy top-ups have little value during an egg trip, while a dangerous
health score can interrupt it.

| Component | Contribution |
|---|---|
| Low HP buffer | 0.5 × max(0, 50 − HP)²: 4.5 at 47 HP, 12.5 at 45, 72 at 38, 220.5 at 29 |
| Missing HP | 8 × (1 − HP/maxHP)², a minor tie-breaker |
| Recent damage pressure | At most 8 points, scaled down at healthier HP and zero at 55 HP or above |
| Spare healing time | 6 points when waiting for eggs, allowing nearby top-ups during idle time |
| Continue current action | Egg work: 12 points. Low-HP care: up to 12, scaled by danger. Healthy top-ups and routine vial readiness: zero. |

Recent pressure uses mean observed HP loss per tick in a rolling 20-tick window.
Each player has independent history. Healing and quiet intervals count as zero loss;
gaps and maximum-HP changes do not invent hits. New waves reset the history. This is
net observed loss: healing and damage in the same tick can mask part of a hit. It
does not predict exact future hits or time to death.

The switching margin applies to egg work and necessary low-HP care, including a refill
needed for that care. Its care value is the smaller of 12 and half the low-HP buffer
score. Filler work receives no commitment bonus, including the 96% teammate top-up at
tick 3575 and ordinary vial readiness. A different action needs a substantial score
advantage to interrupt meaningful ongoing work. Moving teammates
retain their identity by name. A selected valid poison crater remains the destination
when walking nearer another crater. Completed actions and unavailable targets lose
the bonus: a poisoned egg no longer continues dunking, a healthy teammate no longer
needs healing, and a filled vial no longer continues refilling.

Dunking is worth 24 and passing 42 before travel. Vial readiness is worth eight per
missing charge. Travel costs at most 16, normal care interactions cost four, and an
empty-vial care task has a further three-point refill cost. Self-healing has a small
weakness-scaled reserve bonus and a near-spring opportunity bonus. All contributions,
including recent damage pressure and continuation, appear in the simulator's
expandable **Decision weights** tables.

After all ordinary Penance are confirmed dead on wave 10, preparation provides direct
healing/refill targets and a -24 cost for waiting injured. Small top-ups can use that
time. Queen stacking does not send the Healer to the cannon during preparation.
Completed waves 1–9 instead wait for automatic HP restoration.

Regression cases from 2026-10-18_12-33-39-wave10.jsonl:

| Tick | Current guidance |
|---|---|
| 3575 | Healthy 96% teammate top-up and routine vial readiness both have zero continuation value |
| 3576–3585 | Keep dunking through HP drops from 64 to 54 to 47 |
| 3581 | Dunk: 29.6 points; drinking: -5.6 points |
| 3585 | Dunk at 47/77 HP with the existing crater and continuation bonus |
| 3586 | Pass when the egg becomes poisoned; no inherited dunk bonus |
| 3639 | Pass before the routine 48/85 teammate top-up |
| 3741 | Heal teammate at 40/88 before dunking |
| 3764–3767 | Heal teammate at 38/85 before dunking |
| 3786, 3790 | Drink at own 29/77 HP |

All **245 tests pass across 52 capture fixtures**. They cover nonlinear absolute HP,
small-drop hysteresis, emergency interruption, moving healing targets, completed or
unavailable actions, filler yielding to an egg, bounded damage pressure, observed histories, pre-Queen preparation
and existing role rules.

Across 335 fixed Queen observations, both the previous linear-health build and this
version have 34 primary step changes overall. The targeted dunk-trip reversal and
filler commitment are corrected. Neither version prompts egg work with the Healer
or a living teammate at 26 HP or less. Current counts are 81 egg, 96 self-healing,
42 teammate-healing and 116 vial prompts. These reuse recorded HP and movement;
following different guidance would change subsequent observations. They are decision
checks, not predicted wave outcomes.

The [continuity comparison](review-evidence/queen-healer-continuity-summary.csv) and
[earlier comparison](review-evidence/queen-healer-summary.csv) remain available.
Current traces are under build/reports/queen-healer/continuity/. The build command
is in [Next-run validation](Next-Run-Validation.md).

Rebuild/restart through the normal development launcher for live guidance. New
captures identify queen-healer-continuity-2026-10-18. The simulator reconstructs
pressure from existing captures. Advanced role JSON accepts selfDamagePerTick and
the name-keyed teammateDamagePerTick map. Editing HP alone retains captured damage
pressure unless explicitly overridden.
