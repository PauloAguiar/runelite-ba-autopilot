# Evaluating strategy changes with captures

The captures can evaluate decisions and expose regressions now. They cannot by themselves establish how often a different strategy would complete a wave, because the player's actions and the subsequent world state are fixed by the recording.

The initial comparison below predates the runtime fixes. Its CSV evidence remains frozen. Running
the harness now evaluates the updated runtime policy; see [next-run validation](Z:/runelite/plugins/ba-autopilot/docs/Next-Run-Validation.md) for the implementation results.

## What is implemented

`gradlew compareStrategies` replays all captures through the current policy and four experimental alternatives. Each replay maintains its own previous recommendation, task, and target. Candidate history is not borrowed from the baseline. The same recorded events, inventory changes, positions, and NPC behavior are supplied to each policy.

The alternatives isolate three hypotheses:

| Policy | Difference from current decisions |
|---|---|
| strict-calls | Never authorize call-dependent work from an old incoming value; also exclude unclassified relure bait drops |
| trap-first | Exclude a relure recommendation during the opening or when the trap is broken/unknown or known good kill bait is absent; choose another existing candidate |
| ordinary-kills | Replace mandatory on-bait holds with waiting beside a usable, baited trap; step off when standing on its bait |
| combined | Apply all three changes |

These policies are experiments under `src/test`, not the completed redesign. In particular, they do not fix inferred kills, Queen scheduling, healer preparation, navigation, or general recovery. The ordinary-kill experiment reuses the existing south-of-food tile; its geometry still requires live validation in both arena profiles.

Run from `Z:\runelite\plugins\ba-autopilot`:

```powershell
.\gradlew.bat test compareStrategies --console=plain
```

Optional arguments are `-Pdir=path/to/captures` and `-Poutput=path/to/results`. Defaults are `src/test/resources/recordings` and `build/reports/strategy-comparison`.

On this machine the normal build initially failed because the UI kit jar was locked. Verification succeeded by writing that dependency to an alternate jar name, without stopping the running client or editing the UI kit source:

```powershell
.\gradlew.bat test compareStrategies --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

Existing `gradlew replay -Pfile=...` and `gradlew metrics -Pdump=...` remain useful for detailed timelines and comparisons across actual production-code revisions.

## Initial comparison, 17 October 2026

41 captures, **10,633 in-arena decision ticks per policy**. These counts refer to ticks, not unique incidents or players.

| Policy | Changed decisions | Work without fresh call | Chase while main setup at risk | Step flip-backs | Target flip-backs |
|---|---:|---:|---:|---:|---:|
| Current | 0 | 3 | 61 | 63 | 68 |
| Strict calls | 3 | 0 | 59 | 64 | 68 |
| Trap first | 61 | 1 | 0 | 63 | 69 |
| Ordinary kills | 40 | 3 | 61 | 63 | 67 |
| Combined | 102 | 0 | 0 | 64 | 68 |

The trap-first changes replace 61 historical relure instructions with **20 repairs, 30 bait drops, and 11 bait-supply instructions**. Of the changed ticks, 25 had a known broken trap. This is direct evidence that the current priority system can recommend a chase while the setup needs work.

The combined proposal removes the audited stale-work and unsafe-chase prompts, but target flip-backs remain unchanged and step flip-backs increase by one. It therefore supports the priority changes without showing an overall guidance-stability improvement. Eliminating a guard violation is partly true by construction: it is a policy-conformance result, not independent proof that the policy is strategically superior.

No capture triggers the exact last-single-runner hold metric. The synthetic probe does reproduce it, and the ordinary-kill proposal releases it. This is why captures and targeted scenarios are both needed.

All changed decisions in this first experiment are Defender decisions. The observed call-fallback bug for Healer/Attacker is not exercised by these captured trajectories; absence of a changed instruction in those roles is not evidence that the bug is harmless.

Frozen evidence for this review:

- [Per-capture summary](Z:/runelite/plugins/ba-autopilot/docs/review-evidence/summary.csv): five policies per recording, changes, risk flags, flip-backs, and first changed tick.
- [Changed decisions](Z:/runelite/plugins/ba-autopilot/docs/review-evidence/changed-decisions.csv): exact client ticks, before/after instructions and targets, fresh-call status, trap charges, and known good kill bait.
- [Verification transcript](Z:/runelite/plugins/ba-autopilot/docs/review-evidence/verification.txt): build, test, and targeted probe results.

For example, the wave-2 capture beginning `2026-10-16_13-14-01` changes from chasing to obtaining kill bait at client tick 565 / wave tick 55. Inspect the CSV and replay the surrounding window before deciding whether that specific recommendation is appropriate.

## Metric definitions and limits

**Work without a fresh call** counts attack/equip, poison, normal egg pickup, bait drop, and relure instructions without a fresh incoming instruction, including a missing instruction or an old known value. A relure without an outlined bait is excluded. This is an instruction audit, not a count of actions the player executed or penalties actually incurred.

**Main setup at risk** means the opening's first 50 ticks, trap charges zero/unknown, or no observed good bait on the kill tile. This is a conservative strategy rule. It does not prove an escape would occur, and it does not capture every unsafe chase, such as leaving an approaching group with an otherwise usable trap.

**Flip-backs** use the existing metrics definition: returning to the previous step/target after a short intervening choice. A useful emergency interruption may legitimately increase this count. Fewer switches alone is not a success metric.

**Changed decision** compares step, target identity, and outlined inventory items; a wording-only change is not counted. `first_changed_tick` identifies where a policy first diverges. All later observations remain conditional on the historical trajectory, which includes actions the candidate may never have recommended.

Other limitations of the current captures/replayer:

- The replayer feeds actual historical clicks and inventory changes into the tracker even after policy divergence. It does not synthesize new actions or their effects.
- Old captures lack collision maps, run energy, and role-level inputs needed to evaluate some travel and lure assumptions. Replay walk cost falls back to geometric distance rather than the live collision search.
- Self/team identity and coordination inputs are incomplete in the current replay path. Attacker splitting and duo-healer ownership need separate coverage before their replay results are treated as representative.
- NPC visibility, poison, and completion estimates are rebuilt by the current tracker. A planner comparison cannot independently certify those estimates. Tracker changes also need raw-event checks and saved before/after traces across revisions.
- The corpus is small and uneven: 25 Attacker, 10 Collector, five Defender, one Healer. It is not 41 independent complete-round trials.
- A runner's response to different bait, future pathing, damage rolls, teammate reactions, and Queen transfers cannot be recovered from the single observed history.

Do not present the fixed recorded escape/death counts as a candidate's results. Do not turn these flags into a weighted completion score or a claimed percentage improvement.

## How to evaluate each real patch

1. Save a baseline report and per-tick trace before changing runtime behavior. Record the code revision, settings, recorder version, and capture list. Preserve this review's evidence as a dated baseline.
2. State the hypothesis and expected tradeoff: for example, protect the working group even if one isolated runner escapes. Change one policy at a time before testing combinations.
3. Run unit/replay invariants and compare decisions. Inspect the first divergence and representative later windows. Verify outgoing calls, survival, primary work, and resource checks still have the intended priority.
4. Label important windows independently: required repair, permissible wait, unsafe chase, stale-call work, justified heal, legitimate handoff wait. Keep some new captures unseen while developing the rule. Generated risk flags help find windows; they are not the ground truth.
5. Exercise missing cases with targeted snapshots: final singleton, delayed/repeated calls, unknown bait, long unseen NPC, missing vial, manual deviation, and Queen plus surviving ordinary enemies. Check every pending task has a recovery exit.
6. Record new live runs using the changed advice. Compare matched waves, roles, role levels, team composition, and reasonably comparable players; alternate old/new guidance where practical. Record instruction followed/ignored and manual interventions so player deviation is distinguishable from policy failure.
7. Judge actual results from native completion/failure evidence: full-round and wave completion, deaths, escapes, wrong actions, recovery success, and manual intervention. Report the denominator and uncertainty. Separate full rounds from interrupted or partial captures.

For the next recorder version, prioritize role levels, run energy, equipped weapon/style, anonymized self/team identities and roles, outcome transitions, and an explicit indication of whether the recommendation was followed. Add collision/path context where a travel hypothesis needs it. Preserve backward compatibility and keep unavailable fields unknown.

A calibrated encounter simulator could eventually explore alternative outcomes, but it would need validated bait responses, movement/collision, trap behavior, spawn obligations, poison/damage, and teammate policies. Building that now would introduce another substantial system to debug. Use replay audits, focused scenarios, and new live captures first.
