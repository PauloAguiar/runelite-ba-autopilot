# Healer poison evidence and priority

The evidence fixes and coverage ordering below remain active. The blanket ban on feeding at
4 was superseded by [reserve finishing](Healer-Reserve-Finishing.md) on 2026-10-18: after all
coverage and refresh work is done, spare food may finish a nearby healer to open a reserve slot.
The results below describe the earlier poison-priority change.

The latest Healer captures are waves 8–10, starting at `21-54-13`, `21-58-22` and
`22-01-44` on 2026-10-17. They contain 1,171 arena decision ticks. The earlier seven
files in the copied run have other captured roles and are excluded from this Healer audit.

## Recorded failures

| Capture and tick | Previous guidance or state | Corrected replay |
|---|---|---|
| Wave 8, 3158–3164 | Refresh #5, with its health bar falling from 9/30 to 7/30. Undamaged #6 was incorrectly marked at 4 poison after animation 863. | Feed #6 first. Its food count and poison remain zero until supported by food consumption or poison damage. |
| Wave 10, 3835 | Animation 863 marked undamaged #3 poisoned without food or damage evidence. | Keep #3 unpoisoned and request its first feed. |
| Wave 10, 3853–3854 | Hold food for the guide while #1 was estimated below 4 and undamaged #3 was still incorrectly marked poisoned. | Continue covering #3. Once actual poison damage appears on #3 at 3862, select #1 for its refresh. |
| Wave 9, 3603 | Choose visible #8 over weaker poison on remembered healers. | Seek #6, whose poison is estimated expired. |

An absent health bar is treated as full health by the existing planner; it is not an exact HP
measurement. In the first two examples, the recorded damage total is also zero. In wave 10,
#3 has no damage hits between its spawn and the hit at 3862. Animation 863 alone therefore
cannot establish that food landed. It no longer increments food counts or starts a poison timer.

## Current rules

Choose active poison strength **0, then 1, then 2, then 3**. At 4, extra food now needs the
reserve-finishing conditions linked above; a guide food count alone cannot request it.
Zero includes both never-poisoned healers and poison estimated to have expired. An unseen
healer with weaker poison can take priority over a visible healer with stronger poison.

Within equal strength, prefer visible healers, then those above half health over wounded
ones. Retain the previous target within an equivalent group. A heal code can break remaining
ties when its entry is due; it cannot create a wait, skip weaker poison, or force its food count.
Calls, survival, emergency teammate healing and Queen work retain their existing precedence.

Own food counts still require an NPC-use click followed by correct-food inventory consumption.
Observed poison damage establishes poison independently, including when someone else fed the
healer. It does not invent another food count. A newer damage hit corrects older estimates; a
newer confirmed food immediately restores the estimate to 4. Outside view, decay remains an
estimate based on the last evidence and is updated when the healer is observed again.

The strategy matches the basic ordering in the [OSRS Wiki strategy guide](https://oldschool.runescape.wiki/w/Barb_assault_strats):
cover unpoisoned healers before refreshing those below 4.

## Verification

All **189 tests pass**, including **49 capture replays**. Regressions cover the exact ticks above,
healthy coverage replacing a wounded committed target, expired poison, strengths 0–3 across all
guide settings, equivalent-target stability, actual poison evidence, and the simulator's wave-8
decision. The food invariant now checks each observed healer against independently confirmed own
feeds; animations cannot supply an allowance for unexplained extra foods.

The latest three Healer captures have **472 feeding opportunities** under corrected evidence:
zero missed refreshes, weaker-poison bypasses, wounded-before-healthy unpoisoned choices, or
full-poison feed prompts. Before the fix, the old evidence model reported two waits and twenty
weaker-poison bypass ticks. Correcting false poison also changes which ticks qualify as feeding
opportunities, so these are decision checks, not a controlled estimate of wave-time improvement.

Frozen summary files are `review-evidence/healer-priority-before.csv` and
`review-evidence/healer-priority-after.csv`. Full per-tick traces are under
`build/reports/healer-priority-before` and `build/reports/healer-priority-after`. Input copies
are in `build/reports/latest-healer-input`.

The runtime and recorder packages use classifier `healer-poison-priority`; new captures identify
`healer-poison-priority-2026-10-17`. Rebuild and restart through the usual development launcher
to load the game-client changes. The local scenario server is restarted separately with the same
planner. Replay follows the recorded world and cannot establish faster kills or better scores.
