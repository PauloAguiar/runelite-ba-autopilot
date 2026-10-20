# Healer reserve finishing

The 2026-10-18 Healer run (waves 1–10, starting at `20-13-12`) showed the limitation in
the previous rule: once every known healer was at 4 poison, guidance always stopped feeding.
The planner saved food for reserves but never spent spare food to bring those reserves out.

The [OSRS Wiki strategy guide](https://oldschool.runescape.wiki/w/Barb_assault_strats) describes
using extra food on one healer when coverage is secure to release reserves sooner. Correct
food deals 4 direct damage even when poison is already at 4. This change adds that tactic;
the existing poison-evidence and initial-coverage fixes remain in place.

## Decision order

1. Keep calls, self-survival and emergency teammate healing ahead of ordinary poison work.
2. Tag every unpoisoned healer, including remembered ones outside view. Refresh poison below
   4, lowest strength first, using the existing visibility/health/continuity tie-breakers.
3. If all coverage is secure and reserves still need kills to spawn, spend spare called food
   on one nearby healer. The instruction explicitly says **Finish healer #N** and explains
   the reserve count and protected food budget. This is direct kill damage, not a poison refresh.
4. Otherwise retain existing support/restock guidance. A guide's food quantity cannot create
   extra feeding by itself. Waves 1–2 have no reserves and do not use this tactic.

Reserve pressure is `total - initial - kills`, reduced by already observed reserve spawns
when those prove more progress than the kill counter. A healer guaranteed by its visible
health bar to die to the next 4-poison hit also supplies an imminent slot. Dying NPCs are
already included in the kill count and are not counted twice. The tactic stops once enough
kills have opened every reserve slot, even if the next spawn has not appeared yet.

Save one first tag for each healer still to spawn and one refresh for each current healer
whose remaining poison will not finish it. Only food above that budget can fund finishing.
This conservative budget can leave a wait when food is needed for coverage. It does not
assume that the next call will provide usable stock.

## Target and interruption behaviour

Only a visible target within eight walking tiles qualifies. Selection compares the wave-based
upper HP estimate plus travel cost. It usually picks the lowest-health healer; a substantially
closer one can be quicker to finish. Health bounds reuse the diagnostics' quantised health-bar
calculation. An unknown bar uses the wave's full HP rather than treating the NPC as wounded.

The same target gets one food's worth of continuity, including after a call interruption.
This keeps small bar changes from shifting the highlight, while allowing a much better target
to replace it. A new unpoisoned healer or weaker-poison refresh always preempts finishing.
The candidate is recomputed each tick; it has no unconditional sticky commitment. It also
requires travel/click time plus a two-tick margin before a known call change.

## Capture review

The latest ten captures contain **1,816 arena ticks** and **826 ordinary feeding opportunities**.
The revised planner replaces **84 support/wait ticks** with reserve finishing, while retaining
zero missed first tags/refreshes, weaker-poison bypasses, or unjustified full-poison feeds.

| Wave | Reserve-finishing ticks |
|---|---:|
| 1–2 | 0 |
| 3 | 19 |
| 4 | 8 |
| 5 | 10 |
| 6 | 18 |
| 7 | 11 |
| 8–9 | 0 |
| 10 | 18 |

Examples from the latest run:

| Capture and tick | Previous instruction | Revised instruction |
|---|---|---|
| Wave 5, 2199–2202 | All healers poisoned: let it work | Keep finishing #2 as its health falls; retain two foods for coverage. One reserve still needs a kill. |
| Wave 6, 2333 | All healers poisoned: let it work | Finish #2; two reserves still need kills, with two foods protected. |
| Wave 10, 3230–3235 | All healers poisoned: let it work | Continue on #1 at roughly 20 HP, falling to roughly 16. Three reserves still need kills; retain three foods. The capture includes the player's extra feeds on this target. |
| Wave 10, 3211 | Wait/support immediately before the call changes | Do not start extra feeding with one tick left on the call. |

These counts describe decisions on the **recorded world and player path**. Repeated prompts
are not additional clicks. They do not establish how many seconds a different sequence would
save or when counterfactual reserves would spawn. Validate those outcomes in the next live run.

## Verification and replay

All **336 tests pass across 94 captures**. Regressions cover reserve release counts, protecting
food, first-tag/refresh interruptions, low-HP finishing, no-reserve waves, imminent poison death,
call deadlines, unseen/distant targets, focus continuity across calls and the captured examples.
The broader Healer audit checks **5,147 ticks across 17 Healer captures**; all full-poison feeds
are explicitly classified as reserve finishing, and coverage checks remain clean.

Frozen summaries:

- `review-evidence/healer-reserve-before.csv`
- `review-evidence/healer-reserve-after.csv`
- `review-evidence/healer-reserve-all.csv`

Full traces are in `build/reports/healer-reserve-before`, `healer-reserve-after` and
`healer-reserve-all`. Regenerate the latest-run audit with:

```powershell
.\gradlew.bat :auditHealerPriority -Pdir=build/reports/healer-reserve-input -Poutput=build/reports/healer-reserve-after -PbaReviewUiKitClassifier=healer-reserve-pressure --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle
```

The simulator uses the same planner. Select `2026-10-18_20-38-20-wave10.jsonl`, tick **3230**,
to see the change. Both runtime/recorder packages use classifier `healer-reserve-pressure`;
new captures identify `healer-reserve-pressure-2026-10-18`. Rebuild/restart through the usual
development launcher to load live guidance. Reload the simulator separately.
