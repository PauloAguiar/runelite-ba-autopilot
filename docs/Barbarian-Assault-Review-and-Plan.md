# Barbarian Assault: code review and simplification plan

Reviewed 17 October 2026 against `2527f69` in `Z:\runelite\plugins\ba-autopilot`, using the attached reliability plan, the current source, 41 captured waves, and targeted snapshot probes. The working tree was clean at the start. The review phase added documentation and development-only comparison tooling. Subsequent runtime fixes for singleton release, trap-first cleanup, strict call freshness, and protected routes are documented in [next-run validation](Z:/runelite/plugins/ba-autopilot/docs/Next-Run-Validation.md); the findings below describe the original reviewed behavior.

## Recommendation

Keep the event tracker, immutable snapshots, pure role planners, and recorder. Simplify the decisions that sit on top of them. The main problems are priorities that can abandon essential work, assumptions that turn uncertainty into success, and advanced routines used as the beginner default. A wholesale rewrite or a larger state-machine framework would make this harder to validate.

Start with the Defender, call validity, and completion evidence. Then make healing preparation and recovery explicit. Keep speed tactics outside the beginner routine. The original plan is useful as a set of safety and progress requirements, but its complete action-controller specification is more machinery than this manually operated guidance plugin needs.

The original attachment is preserved as [Barbarian-Assault-Reliability-Plan.md](Z:/runelite/plugins/ba-autopilot/docs/Barbarian-Assault-Reliability-Plan.md). Exact game geometry and mechanics in that attachment have not been independently revalidated here. The findings below concern observable code behavior; proposed tactics still need live validation.

## Findings, in implementation order

### 1. P1: a final runner can be held indefinitely

[DefenderPlanner.java:192](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:192) permits release only for a stack of at least two with two charges. Its fallback at [line 246](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:246) tells the player to stand on the food and wait for two. There is no exit for the last single runner.

A snapshot with one remaining runner, a full trap, four known good bait, sufficient supplies, and the player on the bait produces `DEF_HOLD`. The same situation 20 ticks later still produces `DEF_HOLD`. The temporary probe reproduced this; none of the five captured Defender waves exercised the exact final-runner condition.

Change: ordinary kills should be the default. Wait beside the working trap, allowing runners to eat. Any optional hold must release a final singleton and have a bounded exit. Avoid imposing a second runner or a release timer as a prerequisite for progress.

### 2. P1: a straggler can take priority over the main trap

[DefenderPlanner.java:74](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:74) adds reluring before the opening. [Line 403](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:403) makes it a sticky `TEAM_BLOCKING` task. Repair uses that same tier, while the opening is lower. [Chooser.java:79](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Chooser.java:79) retains a sticky task against other tasks in its tier, even when another has an earlier deadline.

The probe kept `DEF_RELURE` with a broken trap next to the player and repair supplies available. Captures also contain 61 ticks of reluring while the opening, missing kill bait, or a broken trap required attention. The experimental trap-first policy replaces 20 of those ticks with repairs, 30 with bait drops, and 11 with bait collection. These are changed instructions on a historical trajectory, not prevented escapes.

Change: finish or recover the opening; restore the trap and kill bait; restore essential supplies; then consider an outlier. Defer remote runners while the main setup needs service. A local correction needs a small service area, a per-runner attempt allowance, and a return budget. Do not repeatedly renew that allowance every tick. If the safety conditions cannot be established, defer the correction.

### 3. P1: the planner resumes stale calls, and unknown bait becomes bad bait

[Planner.java:140](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Planner.java:140) suppresses an old incoming call only for the first 15 ticks. It then passes that old call back to the role planner. The late-call test explicitly expects feeding on it: [PlannerTest.java:1504](Z:/runelite/plugins/ba-autopilot/src/test/java/com/galesong/baautopilot/PlannerTest.java:1504).

There is also a second path: [DefenderPlanner.java:434](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:434) selects any carried bait as bad when the incoming call is null. Meanwhile [RoundLogic.java:875](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/state/RoundLogic.java:875) labels bait good only with a fresh matching call; every other case becomes false/bad. The planner and tracker can therefore disagree about the bait they just recommended.

Change: never revalidate an incoming call because time passed. Preserve the old value for explanation only. Gate new attacks, poison uses, egg pickups, and bait classification on current evidence. Introduce `GOOD / BAD / UNKNOWN` for ground bait; preserve proven classifications across call changes. Missing instructions must still allow verified repairs, healing, refills, and already collected egg deposits. The replay experiment found three relure drop prompts without a fresh call; clearing the incoming value alone did not fix them, so the experimental policy also excludes those drops.

### 4. P1: disappearing NPCs become kills without death evidence

[RoundLogic.java:686](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/state/RoundLogic.java:686) counts a nearby despawn as death. [Line 821](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/state/RoundLogic.java:821) counts an unseen NPC dead after 60 ticks. `OutOfViewTest.outOfViewTooLongCountsAsDeadUntilItReturns` currently requires that behavior.

These inferred kills feed supply calculations and role completion. For example, [CollectorPlanner.java:377](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/CollectorPlanner.java:377) uses kill totals to decide all ordinary work is finished. Correcting a count when the NPC returns does not undo the intervening instructions to leave the job.

Change: separate confirmed kills, visible survivors, and unresolved survivors. Expiry should retire an exact position, not create a kill. Use the existing native all-killed messages and wave-result transition as confirmation. Do not invent a remaining-counter adapter until its availability has been demonstrated. Keep unresolved obligations when visibility is lost. Scope NPC identity and target memory to the wave and spawn generation; an index alone is insufficient.

### 5. P1: Queen appearance globally replaces unfinished ordinary work

[QueenPlanner.java:20](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/QueenPlanner.java:20) applies as soon as the Queen is present. [Planner.java:104](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Planner.java:104) then replaces role work, while [line 201](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Planner.java:201) suppresses calls. The tracker intentionally latches Queen presence at [RoundLogic.java:601](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/state/RoundLogic.java:601).

The probe returns `QUEEN_WAIT` for a Defender even with an ordinary runner and a broken trap still present.

Change: Queen visibility is an encounter fact, not proof that each role is free. Keep necessary healing, ordinary enemy work, and relevant calls active until their obligations are confirmed clear. Offer egg work when that role is available. Use inventory transformations to follow the chain, with explicit missing-recipient/full-inventory recovery; proximity alone is not a handoff.

### 6. P1: survival preparation is reactive and most work automatically delays retreat

[HealerPlanner.java:41](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/HealerPlanner.java:41) starts food work without a vial preparation requirement. Routine healing at [line 386](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/HealerPlanner.java:386) cannot obtain a missing vial. That recovery only appears in the critical path at [line 467](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/HealerPlanner.java:467). A teammate at 40% with no vial available still produces `HEAL_FEED` in the probe.

[Planner.java:92](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Planner.java:92) treats any non-wait work instruction as a reason to defer ordinary retreat, without knowing whether that interaction has actually started or is about to complete. With the normal 30% setting, forced retreat during work waits until below 15%.

Change: prepare a filled vial early, preserve it, and refill on safe return trips. Keep emergency healing ahead of calls and role work; give routine healing a real opportunity between poison applications. Only permit a bounded interaction to finish before retreat when there is evidence it is in progress. The original plan's 35% emergency / 60% routine healing margins are reasonable starting hypotheses for validation, not verified game constants. Avoid adding a complex damage forecast in the first patch.

### 7. P1: Attacker hold guidance does not explicitly stop continuous combat

[AttackerPlanner.java:77](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/AttackerPlanner.java:77) says not to start a new attack during the call change. Its missing-call branch at [line 47](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/AttackerPlanner.java:47) waits for a call. Neither supplies a clear manual disengagement step when the player is already attacking.

Change: distinguish maintaining a valid engagement from stopping one. Tell the player to disengage when the incoming instruction becomes invalid, then confirm the new configuration before suggesting another attack. Preserve existing stable targeting. Prefer a simple ranger-first fresh pick for beginners; keep wave-specific alternation and coordinated NPC-index splits optional.

### 8. P2: commitment has no general no-progress exit

[Candidate.java:7](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Candidate.java:7) defines completion as the planner ceasing to list a task. [BaAutopilotPlugin.java:225](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/BaAutopilotPlugin.java:225) carries the previous task and target forward, but there is no shared attempt age, progress evidence, or recovery reason. Individual tell/feed confirmations already exist and should be retained.

Change: add a small observational progress record for the current recommendation: wave, task, target identity, start tick, last observed progress, and reason blocked. Movement, pickups, repairs, and transformations need different evidence and travel allowances. Do not expire legitimate waiting or force a player who deliberately deviated back into an old step. After repeated observed failures, explain the blocker and offer one safe recovery; avoid endlessly repeating an impossible instruction. Keep this as guidance bookkeeping, not a general click-execution engine.

### 9. P2: the default still asks beginners to execute advanced tactics

[Settings.java:24](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/Settings.java:24) defaults to regular heal codes and target splitting. Defender hold/release timing remains mandatory even with the opening pattern disabled. [DefenderPlanner.java:642](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:642) can also wait at a log respawn during the opening instead of responding to the current trap situation.

Change: one beginner routine per role. Make heal-code timing, coordinated target splits, and timed stack kills explicit optional tactics. Do not reintroduce coordinated/pick-up mode switching. Use one validated Defender opening and an ordinary-kill repair budget; return with enough resources for the immediate obligation instead of waiting indefinitely for a preferred full stock.

### 10. P2: collection and documentation obscure the basic routine

[CollectorPlanner.java:196](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/CollectorPlanner.java:196) combines measured firing rate, run-speed assumptions, deadlines, proximity, capacity, and commitment to schedule deposits. Collection at [line 158](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/CollectorPlanner.java:158) waits until zero ordinary free slots before capacity alone forces a return. This is more optimization than a beginner needs.

Change: collect a current valid colour, keep a small capacity margin, load useful held eggs when worthwhile, and assist only when primary work allows it. Keep real hopper capacity and launcher range checks. Defer conversion, expiry racing, point maximization, and firing-rate prediction from the default decision path. Preserve separate bag/inventory accounting.

The README still describes Auto/Coordinated/Pick-up modes and fallback behavior inconsistent with recent intent. Rewrite it against the final implementation after the behavior changes. The primary instruction should say what to do now, where, and why; retain raw priorities and timing diagnostics in the developer view.

## Smaller proposed design

Retain the existing `BaTracker -> RoundLogic -> Snapshot -> Planner -> Instruction` boundary. Replace a competition among many tactical candidates with a short ordered role policy, using commitment only for a valid bounded trip or interaction. Keep `Chooser` where it remains useful during migration; do not delete it in a large rewrite.

The shared order is: immediate survival; owed outgoing call; essential role infrastructure; essential supplies or current instruction; primary role work; optional assistance. Missing information blocks only the decisions that need it. Queen work participates in this order rather than becoming an unconditional global override.

| Role | Beginner routine | Recovery |
|---|---|---|
| Attacker | Prepare ammunition; obtain a fresh call; verify equipment; retain an accessible target | Explicitly disengage on call invalidation; obtain missing supplies; defer unreachable targets |
| Collector | Current-colour pickups; capacity margin; useful deposits | Keep useful eggs when the hopper is full; fire only at reachable targets with ammunition |
| Defender | Ordered opening; service trap and kill bait; ordinary kills; eventual cleanup | Recover the essential setup after a missed opening; defer outliers; bounded local correction only when safe |
| Healer | Prepare vial and food; heal; cover reachable unconfirmed enemies; refresh weak poison | Refill before the next emergency; resume coverage after healing; retain unseen targets as unresolved |
| Queen work | Confirm the role is available; process the held egg; verify transformation or transfer | State which prerequisite or recipient is missing; retain healing and unfinished ordinary obligations |

Only the Defender needs the explicit phases `OPENING`, `MAIN_GROUP`, and `CLEANUP` initially. Other roles can use ordered predicates rather than a new state enum for every intermediate action. Existing instruction `Step` values are presentation outputs, not necessarily persistent states.

For the Defender, use the attachment's opening as the starting proposal: ordinary waves `G -> K -> M -> N -> L -> tools -> trap`; wave 10 gets its own profile. The current implementation reorders spots by route name and proximity at [DefenderPlanner.java:524](Z:/runelite/plugins/ba-autopilot/src/main/java/com/galesong/baautopilot/plan/DefenderPlanner.java:524), so it is not that fixed checklist. Validate the chosen route against the actual arena before changing coordinates. Keep confirmed completed drops when interrupted. If the opening is no longer recoverable, restore the trap, kill bait, and immediate repair supplies rather than rebuilding every distant marker.

Simplify the original plan too: keep its uncertainty gates, main-setup rule, observed confirmations, and recovery requirements. Defer its full action-record schema, comprehensive healer forecasts, extensive coordination protocol, and four-tick relure budget until the simpler routine has been measured. Do not require every teammate to run the plugin or enforce a particular team composition as a condition of useful guidance.

## Delivery sequence and acceptance

1. **Establish the evaluation baseline.** Preserve the current captures and comparison output. Label a small set of decision windows independently of implementation. Add captures of ordinary Healer waves, Defender waves 8–10, low role levels, and deliberate recovery drills. The test-only harness added in this review is the starting point.
2. **Fix progress and evidence errors.** Address findings 1–5 and explicit Attacker disengagement in small patches. Include a final single runner, repair during a committed chase, repeated/missing calls, an NPC unseen for longer than 60 ticks, and Queen appearance before ordinary clearance as regression scenarios. Update tests that currently assert the behavior being removed.
3. **Make survival and recovery explicit.** Prepare the vial, make healing opportunities predictable, and add bounded no-progress recovery. Verify a missed click, unavailable object, missing supplies, full recipient inventory, manual movement, and interrupted handoff. Emergency healing must still interrupt everything relevant.
4. **Remove tactical complexity from defaults.** Switch to ordinary Defender kills and poison coverage, simplify Collector deposits and fresh Attacker selection, and move optional speed tactics out of the default path. Rewrite the README and reduce the main instruction's competing cues.
5. **Validate real outcomes.** Replay each patch before live use, then record complete runs with comparable teams and role levels. Track wave/round completion, deaths, escapes, wrong-call actions, recovery success, and manual interventions. Use the attachment's three consecutive complete runs per claimed setup as an initial functional gate, not an estimate of completion probability.

Do not combine all stages into a single rewrite. A patch is ready for live validation when its targeted failure scenarios pass, existing unrelated replay invariants still hold, and every changed recommendation has a reason that can be inspected.

## Verification and capture-based evaluation

All **111 existing tests pass**. The captures contain 25 Attacker waves, 10 Collector waves, five Defender waves, and one Healer wave (wave 10). Two captures explicitly record an early end caused by a teammate death; the existence of these captures does not establish that plugin instructions caused those deaths. There is no ordinary-wave Healer coverage and no Defender wave-10 capture in this corpus.

Seven targeted observations reproduced current behavior, including the final-runner hold, stale food use, chase over repair, Queen override, and missing-vial preparation. Three experimental-policy checks then produced the expected wait, release, and repair recommendations. No game session was run.

See [Capture-based strategy evaluation](Z:/runelite/plugins/ba-autopilot/docs/Capture-Strategy-Evaluation.md) for the actual comparison results, commands, interpretation limits, and how to use captures to assess subsequent changes. The changes in this review affect development tooling only; the experimental policies are under `src/test` and do not ship in the plugin jar.
