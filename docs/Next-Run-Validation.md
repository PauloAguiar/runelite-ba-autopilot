# Next-run validation

Latest: [Healer reserve finishing](Healer-Reserve-Finishing.md). When every known healer is
covered and poison is at 4, spare food can finish a nearby wounded healer to open reserve slots.
New first tags and weak-poison refreshes still lead. Check that the finish target stays stable,
food remains for coverage, and finishing stops when enough slots open. Latest wave-10 ticks
3230–3235 now finish #1 instead of waiting; all 1,816 ticks of the ten-wave Healer run retain
clean coverage checks, with 84 reserve-finishing ticks. All **336 tests pass across 94 captures**.
Packages use `healer-reserve-pressure`, marker `healer-reserve-pressure-2026-10-18`. Reload the
simulator and rebuild/restart through the usual development launcher for live changes.

Latest: [continuous Defender upkeep](Defender-Continuous-Upkeep.md). Timed multikills now
default off under **Defender → Advanced**. Check the explicit REPAIR SPOT floor target before
Fix, food top-ups after repairs, and stepping clear to let runners eat. Required repairs must
not yield to bait or routine reserve trips. The paired 4,892-tick audit has zero default hold/
timed-release prompts, off-spot repair clicks, or broken-trap bait interruptions. Advanced
mode retains its stack releases with the same repair-position improvement. All **323 tests
pass across 84 captures**. Packages use `defender-continuous`, recorder marker
`defender-continuous-2026-10-18`. Reload the refreshed simulator; rebuild/restart via the usual
development launcher for live changes.

The [Defender run evaluation](Defender-Run-Evaluation.md) covers the latest waves 1–10 and
seven older captures. Repair/bait conflicts and short reserve detours remain follow-up items;
the evaluation does not change strategy priorities. The replay now has persistent **Arena
elements** filters, **Role focus**, and an **Unconfirmed information** tracker with last-seen
markers. Browser checks confirm that hiding eggs preserves the decision, saved filters survive
reload, and returning/cleared runners resolve their entries. All **314 tests pass**. The
simulator is refreshed; reload its tab for the display changes.

Latest: [Defender food identity, preparation and uncertain runners](Defender-Bait-Memory.md).
The wave-6 mainstack scene replacements at 4263 preserve original good food and drop time.
Wave-8 tick 4669 uses spare called bait to top up the trap while holding, keeping two in reserve.
Wave-9 runner timeouts are uncertain rather than counted kills; the false all-dead message at
5069 is removed and completion begins with the actual clear at 5138. All **313 tests pass across
75 captures**. Packages use `defender-bait-memory`, marker `defender-bait-memory-2026-10-18`.
The simulator is restarted on Defender wave 9. Reload it; rebuild/restart through the usual
development launcher for live validation. Earlier changes and their validation follow.

Latest: [Defender trap holding and bad-food colours](Defender-Trap-Holding.md).
Hold the trap food while runners are still approaching; routine reserves, maintenance and
tool trips must wait. Missing kill bait and required repairs remain exceptions. Multi-release
requires two known charges and an arrived group. Check the one-charge repair message, the
food-tile target while waiting for the timer, and purple bad-food routes/marks after completion.
The final adjacent runner may still use one charge. All **305 tests pass across 75 captures**;
the 3,545-tick audit removes all seven premature multi-release decisions and all 75 off-food
hold targets. Packages use `defender-trap-guard`, marker `defender-trap-guard-2026-10-18`.
The simulator has been restarted on Defender wave 9. Reload its tab to use the new build;
rebuild/restart via the usual development launcher for the live changes.

Latest: [Attacker opening and Queen readiness](Attacker-Opening-Queen.md).
Get arrows first, use the western route behind the hill, and defer wave-10 spikes until all
Fighters/Rangers are finished. After spiking/passing the Queen batch, check that guidance keeps
you ready to spam Omega when the Defender hands finished eggs to the Collector, with loaded
ammo as a fallback. The aiming highlight should persist between loads without requesting more
spikes. The directed hand-off cue needs the new role/interaction recording fields and a live
check; older captures can verify the ammo fallback. All **296 tests pass across 65 captures**.
Packages use `attacker-opening-queen`, marker `attacker-opening-queen-2026-10-18`.
The simulator has been restarted on the latest Attacker wave 10. Rebuild/restart via the usual
development launcher for the live changes. Earlier changes and validation results follow.

Latest: [proactive Collector supply and local movement](Collector-Proactive-Supply.md).
Large carried red reserves now favour loading low-red cannons even without a visible stray
or recent shot. After loading, idle time may suggest a fixed centre-check marker at most six
walking tiles away; arrival and a 50-tick cooldown prevent repeated waypoint prompts. Ordinary
egg trips are capped at twelve walking tiles, with additional penalties beyond six. Check
loading frequency, nearby pickup preference and whether centre checks reveal useful threats.
All **289 tests pass across 63 captures**; the 6,344-tick audit removes all 37 ordinary egg
trips over twelve tiles. Packages use `collector-proactive`, recorder marker
`collector-proactive-2026-10-18`. The simulator is refreshed; restart/rebuild the client via
the usual launcher for live changes. Earlier implementation notes follow.

Latest: [Collector Queen reserve and wave-10 ammunition](Collector-Queen-Reserve.md).
The initial five-yellow batch is followed by a configurable three-yellow reserve. Collect it
while the first volley is processed, hold it until that batch's omegas have been spent and the
Queen survives, then pass it to the same Healer. Set reserve eggs to zero to disable it.
Ordinary wave-10 eggs are collected for useful ammunition only: early red gathering should
remain worthwhile, stockpiling should stop, and any-colour ammo must be loaded before waiting
for the Queen so the team can pre-click Omega. Validate both the reserve gate and preload in
the next game run. Packages use `queen-reserve`, recorder marker
`queen-reserve-preload-2026-10-18`; **283 tests pass across 63 captures**.
The simulator has been restarted with this policy. Restart/rebuild the game client via the
usual launcher to load the new guidance and configuration.

Earlier changes and their original validation results follow.

The Collector's selected ground target now displays **PICK UP ×N**. The previous change added
the count to the instruction headline but left the scene badge on the static `PICK UP` tag.
Normal and Queen pickups now pass their safe quantity to the badge, including an opening call
carrying that target. The latest wave-1 ticks 799–804 are covered by replay-to-badge checks.
Packages use `collector-pickup-label`; the full suite passes 274 tests across 62 captures.

The [sidebar](Sidebar-Settings.md) now contains role settings and observed levels/Honour points.
Role-specific advanced settings and shared general/display settings start collapsed. Check that
changing the Queen/blue-call egg counts saves immediately and that expanding a role survives
subsequent ticks and configuration refreshes. Reference material and diagnostics have moved out
of the sidebar into the documentation. Packages for this change use the `role-settings` classifier.

Latest: [Collector navigation and Queen batches](Collector-Navigation.md). Yellow batching
defaults to five, with a stable Healer recipient until the batch is passed. Single omega
loading defaults on, with an explicit two-tiles-south position and a wait between loaded eggs.
Collector waits for yellows farther north rather than at the southern pool. Ordinary pickups
now include travel/call deadlines, stable nearby targets and stack quantities; shared hoppers
use the nearer route. Opening positioning is requested once per wave. Inventory and bag counts
are separate; rejected bag Empty attempts retain their contents. Red calls can destroy blue or
green inventory eggs to make room for nearby reds. Validate these behaviours next run.
All **268 tests pass across 61 captures**; the 1,914-tick ordinary navigation audit has zero
remaining flags in its four measured categories. The simulator is running the new build.

Wave-10 Healer care now uses weighted health, supply and egg choices. Carrying an egg
does not suppress healing or self-healing; waiting time can top up HP and the vial.
The latest capture's 38/85 teammate is healed before dunking, and the Healer at 29/77
is sent to the spring. Safe egg work still proceeds. See [Queen Healer care](Queen-Healer-Care.md)
for exact ticks, weights and the fixed-observation audit. Passing now beats a routine
48/85 heal at the user-labelled tick 3639. The replay shows ranked **Decision weights**
with expandable component tables. Pre-Queen top-up directions start after all ordinary
Penance are dead on wave 10; other completed waves wait for automatic healing.
Absolute HP now uses a quadratic danger curve, with missing HP a minor tie-breaker
and a bounded bonus for recent observed damage. A 12-point switching margin holds
meaningful current work through small changes. Healthy top-ups and routine vial
readiness have zero continuation value; low-HP care scales up to the 12-point maximum.
Ticks 3581 and 3585 keep dunking; danger and completed steps still release the action.
The earlier Healer validation passed 245 tests across the then-current 52 captures.

Collector decisions now use [weighted values and capture labels](Weighted-Collector.md). Early
reds have the greatest marginal value while reserves are low; each additional red is worth less.
Useful batches and escaping runners can outweigh the twelfth red, and shooting is strongly
deprioritized so teammates use the cannon. The latest wave-2 red detours fall from 35 to zero.
Replay offers **Tune Collector choices** with saved egg/loading preferences and close cases.
The earlier fixed Collector phase rules below are historical; the weighted guide supersedes them.

Enable **Show priorities (debug)** to see inferred runner states, wave-based Healer HP and
conditional poison-only kill forecasts, plus the fixed **8×8 zones**. Check live label placement
and zone boundaries on the next run. In replay, enable **NPC debug** and hover a marker for those
details; text no longer covers the map. Shared tiles have counted stacks with a per-occupant
hover list, food/eggs show quantities and distinct symbols, and 1×/2×/4× zoom is available. See
[Penance diagnostics](Penance-Diagnostics.md) for estimates, uncertainty and sources. Diagnostics
never change planner choices. The replay server has been restarted; only the game overlay needs
a client rebuild/restart through the usual development launcher.

Current-call inventory highlights now remain alongside the primary action's items. As Healer,
the correct food stays green while the vial is highlighted for healing. As Defender, the called
bait is green and labeled GOOD; both other bait types are purple and labeled BAD, including
during repair and tool trips. The compact instruction box retains the call row and its keyword.
When the incoming call is stale, missing or changing on the HUD, food/arrow classifications clear.

Attacker targets now get a gray **DON'T ATTACK** badge when the call is missing or stale. The
current interacting enemy takes precedence; a visible suggested target is also warned, and the
last live visible target is used when neither is available. Fresh calls clear the warning. It
survives calling, arrow collection, waiting and retreat, and replaces old ATTACK badges during
the brief HUD/snapshot mismatch. It uses the existing **Highlight target** option and excludes
dead or unseen enemies, other roles, and Queen work.

On the next run, check healing with correct food and a vial held, both bad bait types while
dropping or repairing, and the moment a call changes. For Attacker, check an engaged enemy and
the same enemy after disengaging, then confirm the gray warning clears on a fresh call.

Healer poison tracking now ignores animation 863 as evidence of food. Confirmed own inventory
consumption counts foods; actual poison hits establish and update poison independently. Guidance
uses unpoisoned first, then poison at 1, 2, and 3, with no code wait or remembered-target override
of weaker poison. The exact latest wave-8 tick-3158 case now targets undamaged #6 before wounded
#5. See the [latest Healer evaluation](Healer-Poison-Priority.md) for the wave-8–10 evidence.

Defender targets now stay with the selected unfinished pattern spot, hammer or individual log.
Walking past Safety or nearer another tool does not change that target. Bait placement, pickup,
item disappearance and higher-priority trap work still advance or interrupt it. The first-call
destination survives waiting for a call, and the short-call good-bait deadline still applies.
The [target-stability evaluation](Defender-Target-Stability.md) covers the exact wave-8 Safety and
wave-9 hammer regressions. In 241 fixed-observation movement probes, avoidable within-task changes
fell from **23 to zero**. The intentional step-off prompt on entering the trap food remains.

The red screen tint now appears only for a primary horn-call instruction. Feeding, equipment,
blue deletion, bait dropping and other inventory actions no longer tint the screen. The existing
Off/Full/Edges mode and tint strength settings still apply, under **Call reminder tint**.

Collector priorities now follow the called phase:

| Phase or situation | Expected guidance |
|---|---|
| Red, with inventory room | Gather as many reachable reds as possible. A configured batch size, nearby hopper, empty cannon, firing rate, or remembered loading trip does not stop collection. At zero free slots, load fitting eggs to make room. |
| Blue, with a good batch | Load when held red/green eggs reach the configured **Collector: blue-call batch** size, counting inventory and bag. At least three eggs must fit. The threshold is held eggs, so a setting of 15 works even though only ten can fit across both hopper colours. Finish a started trip while the blue window remains usable. |
| Green | Continue collecting with four or more free inventory slots. At **three or fewer free slots**, load fitting eggs to make room. |
| Escaping runner in a known cannon's range | Interrupt to fire at it using loaded ammunition, or supply useful ammunition if needed. Ordinary leftover healers and loose/stacked/lured runners do not create this interrupt. |
| Low reds and active cannon use | With **two or fewer loaded reds**, red eggs held, and a confirmed red/green ammo decrease in the last **eight ticks**, interrupt to refill reds. Role completion, an old rolling shot rate and green-only inventory do not qualify. |
| Empty hopper by itself | Continue the phase's collection plan. Empty ammunition without either condition does not create a support interrupt. |
| Nothing useful to load or shoot during blue | Collect blues, then delete them when appropriate. Expiring blues do not take priority over useful cannon work. |

Support work is separate from routine phase loading. When its evidence expires, it clears without
leaving a normal hopper-trip commitment. A routine loading task can remain pending through
red/green collection and resume during its next usable window. Owed calls, survival and Queen
work retain priority.

On the next Collector run, check a full red gathering window, a blue batch at the configured
threshold, and the green boundary at four versus three free slots. Check both an empty cannon
without recent firing, ordinary leftover healers, a real escaping runner and a recently used
low-red cannon. Full hoppers must not trigger impossible loads. Across the latest run's 1,967
decision ticks, unsupported cannon interrupts fell from **186 to zero** and red collection ticks
rose from 289 to 362. See [the run evaluation](Last-Collector-Run-Evaluation.md) for evidence and
three unchanged call-clock audit flags. Earlier phase and blue-only audits remain frozen.

The Healer now uses coverage and poison strength before optional heal-code guidance:

| Situation | Expected guidance |
|---|---|
| A call, emergency heal, or restock interrupts unfinished work | Return to that work once its prerequisites are valid. Supply, repair, opening bait, hopper load, and initial poison obligations can remain pending together; only one primary action is shown. |
| You complete part or all of an obligation while another prompt is shown | Update progress from inventory, bait, trap, and confirmed feeding observations. Completed work disappears; a suggested action alone does not complete it. |
| Any known Penance Healer is untagged and correct food is available | Tag visible newcomers first, then seek untagged healers last seen out of view. Do this before refreshing anyone already poisoned, in every timing-guide setting. |
| The target is already at 4 poison | Fixed code quantities cannot force more food. Extra feeding now needs the reserve-finishing conditions above: coverage secure, a slot still needed, a nearby target and spare food beyond coverage. |
| All known healers are tagged and a healer's poison is below 4 | Refresh once, choosing lowest poison first. Guide times cannot delay it. Code entries and target memory only break ties between equivalent targets. |
| A healthy unpoisoned healer appears beside a wounded unpoisoned target | Prefer the healthy newcomer; commitment cannot hold the wounded one ahead of it. |
| Another Healer is on the team | Apply the same coverage rule. Actual poison hits establish poison without inventing another food count or an assumed assignment. |
| Enough food is held for upcoming first tags and current needed refreshes | Stay with the healers. The reserve no longer budgets two foods for every healer that has not spawned. |

The wave-8 capture `2026-10-17_18-24-13-wave8.jsonl`, tick **2033**, originally demonstrated
an unjustified guide-quantity feed at 4 poison. The current reserve-aware replay now justifies
an extra food independently: two reserves still need kills, seven foods are held and six are
protected for coverage. The earlier wave-10 tick-3502 first-feed regression is retained.
Calls and emergency healing still interrupt role work.

For the next run, watch an untagged newcomer beside already poisoned healers, an untagged healer
that walks out of view, and a completed refresh. A target displayed at 4 must not receive another
feed prompt. Check with a second Healer too. Enable the priorities debugger to see pending work
survive calls or healing. Memory clears on leaving the arena, changing role, or changing wave.

Current verification: **218 tests passed**, including nine highlight and missing-call warning
scenarios and all **51 saved capture replays**, the latest
three Healer waves, exact healthy-coverage and animation regressions, poison evidence, strength
ordering, guide waits, equivalent-target stability, Defender target scenarios, cannon evidence,
and simulator/catalog checks. The latest three Healer captures contain 1,171 decision ticks and
472 feeding opportunities, with no missed refreshes, weaker-poison bypasses or full-strength feeds.
Food counts now describe confirmed own feeds, while observed damage establishes poison separately.

In the earlier Healer-only change audit across **11,557 recorded decision ticks**, full-strength food prompts fell from **149 to
0** (68 to 0 in the new wave-8 fixture). The prior source already had zero measured tagged-target
feeds bypassing a known untagged healer in these fixtures; the widened rule is additionally
covered by unit cases with untagged healers out of view and an assumed duo assignment. Short
step flip-backs rose from 65 to 74 and target flip-backs from 77 to 81 as feeds stop at confirmation.
These are guidance audits on the recorded player path, not predictions of faster waves or fewer
failures. See `docs/review-evidence/healer-coverage-metrics.csv` and `healer-coverage-verification.txt`;
full traces are in `build/reports/healer-coverage-before/traces` and `healer-coverage-after/traces`.

The following four fixes are now in runtime source, alongside the earlier route-line smoothing:

| Situation | Expected guidance |
|---|---|
| One runner remains at the main trap, including when other survivors are deferred outliers | Step off known good bait when the trap has at least one charge. Stay beside it afterward; no second runner or timer mark is required. |
| A runner is south while the trap is broken, bait is missing, supplies are needed, or the main group is still arriving | Service the main setup. A previously suggested chase does not keep priority. Cleanup resumes once those obligations are ready and arrivals are accounted for. |
| The incoming call is late or missing, including more than nine seconds into the period | Keep call-dependent actions blocked. Continue required outgoing calls, repairs, healing, and safe use of existing good bait. An engaged Attacker is explicitly told to disengage. |
| A teammate is occupying the holding tile | Other roles' suggested walks detour around the tile and a one-tile margin. Healing/player interactions may approach an adjacent tile. The Defender can still service its own setup. |

The latest Healer run identifies the earlier `cannon-evidence-2026-10-17` build. Close the old client
and rebuild/start through the usual dev launcher to load the updated classes. The distinct
packages are `build/libs/ba-autopilot-0.1.0-weighted-collector.jar` and the matching
`ba-autopilot-0.1.0-weighted-collector-dev-recorder.jar`, including the earlier Healer, Defender, Collector and task-continuity changes.
They can also be used directly by a launcher configured for those artifacts. The existing client's
loaded classes have not changed.

Keep the debug recorder enabled for the next run. Watch the final-runner case, a trap interruption,
a naturally delayed call, and a route near a held stack. For routes, follow the displayed detour;
clicking a distant target still lets the game's own pathfinder choose its route. Check the detour
and the one-tile-south release separately in the ordinary and wave-10 arenas when available.
New captures include `recorderBuild: collector-navigation-2026-10-18`, the last confirmed cannon shot tick,
selected task and pending task
statuses, and all current decision settings,
including covered calls, target splitting, and Queen stacking, to identify this validation build.

The route exclusion uses an observed player on the known holding tile; it clears when that player
leaves or the native runner-clear message arrives. It does not infer a holding player who is out
of view. Captures without collision and player-context inputs cannot validate this visual change.

The historical review, earlier task-continuity evidence, and their original CSV files remain frozen.
The route exclusion still needs your live check.

Verification command, including the alternate package for a client holding the old jar open:

```powershell
.\gradlew.bat test jar recorderJar -PbaReviewJar=true -PbaReviewClassifier=collector-pickup-label -PbaReviewUiKitClassifier=collector-pickup-label --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

The [scenario simulator](Scenario-Simulator.md) has a capture picker filtered by wave, recorded
role and recorded role level. Only the captured role receives a decision; observed teammates
remain positional context. Position, calls, inventory and shared arena inputs are editable.
