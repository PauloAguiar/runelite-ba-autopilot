# Defender trap holding and bad-food colours

Holding is now an [optional advanced strategy](Defender-Continuous-Upkeep.md). The default
keeps food and repairs ready and stands clear. The holding rules below apply when timed
multikills are enabled; purple bad-food colours apply in both modes.

An active hold on the trap food now takes precedence over routine bait reserves, spawn/trail/
safety maintenance, tool collection and relures. A remembered supply trip cannot keep pulling
the player away. The opening pattern can still continue during the first call while runners
are distant; once runners are within two tiles, or the opening call ends, standing on the food
with an approaching group establishes the hold. This is a decision prerequisite rather than
an extra weight: leaving that tile releases the runners.

Missing kill bait and necessary trap repairs remain exceptions. Good bait can be dropped on
the current tile without leaving. If none is held, the required supply trip is still offered.
A broken trap still needs repair; a two-runner stack at one charge requests repair or missing
repair tools rather than a multi-kill. Ordinary reserves can wait until the hold is over.

## Release and charge checks

A multi-kill needs exactly two known charges, enough good bait, and at least two runners
immediately beside the food. A visible runner more than one tile away is still arriving;
remembered runners out of view also prevent release. Southern outliers already deferred by
the existing strategy do not prevent the main group from completing. Once the group is ready,
the existing call-timer release marks apply. While waiting for that mark, the target remains
the food tile rather than the southward step tile.

Charge-specific holding text no longer tells the player to step on a timer when only one
charge is known. Unknown charges request confirmation. Conflicting nearby trap observations
use the lowest known charge count, independently of object-list order; the other trap is
excluded. The existing final-runner exception remains: one adjacent final main-group runner
with good bait may be released using one charge, without waiting for another runner or timer.

## Purple means bad food

Bad-food pattern markers remain purple after completion, with reduced opacity. Their target,
route, drop radius, food counts and instruction colour use the same purple. An opening horn
call can carry that purple work target while retaining its red calling headline. Inventory
BAD highlights continue to include both uncalled food types. The replay target and route also
use purple when the action requires bad food.

## Capture evidence

Ten completed Defender recordings from 17:57 through 18:18 on 2026-10-18 were added to the
fixtures. Together with older Defender captures, the audit evaluates **3,545 ticks across
17 captures**. The full suite passes **305 tests across 75 capture fixtures**.

| Audit observation | Before | After |
| --- | ---: | ---: |
| Multi-release with an arriving or unseen runner | 7 | 0 |
| On-food HOLD instruction targeting a different tile | 75 | 0 |
| Multi-release without exactly two charges | 0 | 0 |
| Routine reserve, spawn/safety bait or tool trips from the occupied food tile | 57 | 0 |

The last row covers the 17 reserve, 3 spawn-bait, 15 safety-bait and 22 routine-tool decisions
flagged in the baseline. The CSV's broader `routine_departures` column falls from 182 to 125;
its remaining entries include permitted early pattern trips and necessary repair/kill-bait
supplies, so it is not a count of errors. The baseline had no explicit one-charge multi-release,
but its generic holding text and premature step-tile target could still suggest one.

Specific regressions and simulator checks:

- Wave 2, tick **3624**: the old reserve trip becomes a hold while runners approach.
- Wave 5, tick **4054**: the old multi-release becomes a hold for the arriving/unseen runners.
- Wave 3, tick **3732**: a one-charge hold describes the charge restriction and targets the food.
- Wave 2, tick **3581**: the bad-food Trail action and carried opening-call target are purple.

Focused scenarios additionally cover a runner two tiles away, ambiguous trap observations,
missing tools, local bait drops, required supply trips and the final one-charge singleton.
These are fixed-observation decision checks, not a simulation of better completion times or
fewer escaped runners. Validate the actual release timing, charge updates and overlay colours
in the next game run.

Frozen summaries: [before](review-evidence/defender-trap-before.csv) and
[after](review-evidence/defender-trap-after.csv). Reproduce the audit with:

```powershell
.\gradlew.bat auditDefenderTrap '-Poutput=build/reports/defender-trap/after' -PbaReviewUiKitClassifier=defender-trap-guard --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

The plugin and recorder packages use `defender-trap-guard`; new captures identify
`defender-trap-guard-2026-10-18`. The simulator on port 23771 runs this build. Rebuild/restart
the development client through the usual launcher to load these changes for live validation.
