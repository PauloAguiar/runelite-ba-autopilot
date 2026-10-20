# Barbarian Assault: reliability-first strategy and plugin decision specification

Prepared 17 October 2026; Defender policy clarified 17 October 2026. Scope: all four roles, waves 1–10, mixed player skill and role levels. This is a proposed strategy specification, not a tested plugin or a claim of guaranteed completions.

## 1. Purpose and implementation boundary

Optimise, in this order: team survival; valid calls and actions; a stable main setup for each role; eventual wave completion and recovery of deferred enemies; predictable workload. Accept an isolated runner escape when preventing it would disrupt the main setup. Completion time and point maximisation are secondary.

The plan describes explicit decisions, prerequisites, and confirmations so it can drive a player checklist or a strategy controller. Actual unattended clicks, movement, calling, combat, inventory use, and egg firing conflict with Jagex's macro rules [S1]. RuneLite specifically records removal of BA left-click calling [S2]. Automatic positional guidance can also be restricted in wave-based combat [S3]. A public RuneLite implementation must stay within reviewed capabilities; describing an action here is not permission to automate it. Use the full decision model as a strategy specification, with a separately reviewed presentation layer and player execution. Do not assume that an overlay is permitted merely because it does not click.

The policies and thresholds below are design recommendations. Source-backed mechanics are cited; proposed retry counts, healing thresholds, batch sizes, and priorities are not game constants.

## 2. Team contract and preparation

Use one Attacker, one Collector, one Defender, and two Healers as the conservative starting composition. More healing provides recovery time, but one Attacker must still be capable of clearing combat enemies. If the solo Attacker is consistently overwhelmed in practice, use two Attackers for ordinary waves and organise two Healers in the lobby before wave 10. Roles are assigned before entering a wave, never assumed changeable mid-wave. Establish this choice before starting.

For duplicate Healers:
- H1 owns survival checks and spring refills.
- H2 owns initial poison coverage and following new Penance Healers.
- H1 supplies the normal Defender call; H2 backs up a missed call using the same native information.
- H2 handles Queen egg processing while H1 keeps the team healthy.
- If H1 is absent or busy, H2 inherits healing immediately. Ownership never overrides an emergency.

Use comfortable defensive equipment on support roles, keep weight manageable, and favour an Attacker weapon setup with few switches. Ranged is a useful standard profile when the player's equipment can use the supplied BA arrows. A melee profile is acceptable only after verifying that the equipped weapon supplies every required style. Avoid a plan that needs a weapon switch every call. Turn Attacker auto-retaliate off so resuming the wrong style is less likely.

Before each wave, check role assignment, horn, required equipment, run energy, teammate readiness, and inventory room. Do not discard the horn during restocking. Support-role defence and healing reserves are preferable to artificially taking damage for points.

No skill profile should require tick-perfect movement. A player who misses a step must be able to resume from observed state.

## 3. Shared call protocol

The role relationships are:
| Local role | Calls to | Outgoing information | Incoming instruction |
|---|---|---|---|
| Attacker | Collector | Egg colour | Combat style / arrow / element |
| Collector | Attacker | Combat requirement | Egg colour |
| Defender | Healer | Poisoned food | Defender bait |
| Healer | Defender | Defender bait | Poisoned food |

Read outgoing information from the mouth/CALL field and incoming information from the ear/DO field [S4]. Never echo the incoming instruction as the outgoing call.

Implement calls as epochs, not just strings. The normal interval is 30 seconds, but the game interface and its reset/change signals should be authoritative. A timer estimates when attention is needed; it does not reveal the next valid instruction.

Call procedure:
1. At wave start or epoch change, identify the new outgoing value and call it.
2. Invalidate the previous incoming instruction for new call-dependent actions.
3. Wait for a native incoming instruction valid for this epoch.
4. Re-check the specific item or combat configuration immediately before committing.
5. Resume only actions whose prerequisites remain valid.

A repeated text value is not proof of a new call; two consecutive epochs may have the same instruction. Conversely, a changed outgoing value is not the sole means of detecting an epoch change. Store the wave identity, epoch, native reset/refresh evidence, and confidence separately. If the client cannot distinguish fresh from stale information, mark it UNKNOWN and ask for a fresh call rather than guessing.

A stale call pauses attacks, poison uses, fresh bait drops requiring classification, and normal egg pickups. It does not prevent healing, refilling, repairing, moving away from danger, or using already verified resources. Ground bait is a special case described below.

Call maintenance continues after one's enemies are finished, while partners still need instructions. A player watching a cannon must still notice calls.

## 4. Shared scheduling and recovery

Every decision cycle builds a fresh snapshot and chooses one highest-priority outstanding task:
1. Immediate survival response.
2. Required outgoing call.
3. Preserve or restore essential role infrastructure, especially the Defender trap and kill bait.
4. Essential supplies or restoring valid incoming information. A free cannon operator may independently rescue a reachable errant runner; the Defender only makes a bounded safe local correction while the main group remains active.
5. Primary role work.
6. Cannon assistance or Queen egg work when its prerequisites are met.
7. Optional collection or convenience work.

A routine heal does not indefinitely postpone calling. An immediate lethal situation may interrupt anything. Treat pending actions as resumable rather than restarting an entire opening.

For every proposed action store:
`waveId, role, state, targetKey, callEpochIfNeeded, prerequisites, expectedEvidence, startTick, timeoutPolicy`.

Require observed progress. A click or menu selection indicates an attempt; it does not establish success. Examples:
- Movement: position changes and eventual arrival.
- Pickup: inventory/bag gain attributable to the attempt.
- Poison: appropriate food consumption plus target/effect evidence.
- Repair: trap state improves; resource change is supporting evidence.
- Handoff: inventory transformation or transfer evidence, not the partner standing nearby.
- Wave finish: native completion/result transition.

Only one unconfirmed discrete interaction should own the controller at once. Continuous combat is different: monitor an ongoing engagement instead of issuing a new attack every tick. A survival or call event can pre-empt it.

Suggested failure policy: if an action shows no progress within its operation-specific travel/interaction allowance, re-observe and retry once; after a second failure, enter recovery and expose the reason. Longer routes get longer allowances. Do not declare failure after two ticks merely because a distant target has not been reached.

Keep target identity stable during a valid engagement. Re-evaluate when it dies, becomes unreachable, leaves the allowed area, a higher-priority task appears, or the call invalidates. This avoids a controller oscillating between equally attractive targets.

## 5. Navigation and arena profiles

For ordinary openings, Attacker and Collector use the western approach; Healer and Defender use the eastern approach [S5]. Do not route everybody straight through the centre.

Define separate profiles for waves 1–9 and wave 10. Represent routes as named, validated landmarks, not screen pixels:
- role dispenser, healing spring, west cannon/hopper;
- east trap and its bait/holding/repair approach tiles;
- north log and hammer spawns;
- runner and healer interception areas;
- Queen poison crater, mushroom, lava crater, and handoff area.

All exact tiles must be captured in the actual arena before implementation. Use instance-aware coordinate conversion and verify landmark objects after loading [S11]. No absolute coordinates, object-ID sets, widget IDs, or release offsets in this document should be treated as a finished data pack.

Keep a small exclusion area around a Defender actively holding runners. Routes to heal, restock, and hand off should skirt it. Test pathing with run enabled and disabled: the same requested destination must not accidentally cross the holding tile.

## 6. Attacker

### Baseline routine

1. Call the Collector's egg colour.
2. Obtain required BA ammunition if using the ranged profile.
3. Take the western route and remain in the combat side of the arena.
4. Wait for a current incoming call and confirm the matching equipment/style.
5. Select an accessible ranger first, then a fighter. Retain that target until there is a reason to change.
6. On call invalidation, stop the ongoing engagement by disengaging; merely refraining from another attack click does not stop auto-attacks.
7. Apply and verify the new configuration before re-engaging.
8. When combat work is confirmed complete, continue calls and help with cannon cleanup.

Mapping [S5]:
| Native melee requirement | BA ranged ammunition | Magic element |
|---|---|---|
| Controlled | Bullet | Wind |
| Accurate | Field | Water |
| Aggressive | Blunt | Earth |
| Defensive | Barbed | Fire |

For ranged, validate the equipped BA arrow type; changing the bow's attack-mode button is not a substitute for the right arrows. For melee, verify the weapon's actual combat-style mapping rather than treating interface button positions as universal.

### State and recovery rules

| State | Enter when | Required outcome / next step |
|---|---|---|
| PREPARE | New wave | Horn and weapon profile valid; ammo available |
| WAIT_CALL | Instruction missing/stale | Native current instruction established |
| CONFIGURE | Requirement differs | Equipment/style matches requirement |
| ENGAGE | Valid call and target | Maintain combat while monitoring prerequisites |
| RECOVER | Wrong-hit feedback, unreachable target, low health | Stop engagement; fix cause; reassess |
| ASSIST | Native combat count confirms no remaining combat work | Maintain calls; assist accessible cleanup |

Wrong-style feedback immediately invalidates confidence in the current configuration. Check the epoch, arrow/equipped weapon, and style mapping before another engagement.

If the target is stuck, move to a validated reachable approach point on the west side. Do not chase it through the Defender holding area. If low on health, use the horn's Medic request and move toward the team's agreed healing rendezvous; the native request, rather than typing the word, is what alerts the Healer interface [S4].

With two Attackers, assign rangers to A1 and fighters to A2. Either can help after its class is cleared or in an emergency. Tie-breaking should be deterministic; avoid two controllers repeatedly switching to each other's target.

## 7. Collector

### Baseline routine

1. Call the combat requirement to the Attacker.
2. Use the western approach.
3. Wait for current egg-colour instructions.
4. Collect only matching normal eggs and only when capacity is confirmed.
5. Deposit useful eggs regularly; do not wait for the last available inventory slot.
6. Keep the cannon supplied while teammates perform their primary work.
7. Continue calling during collection, deposits, and cleanup.

Use a capacity margin: return to the hopper with two ordinary inventory spaces remaining, or sooner when a useful deposit is possible. This margin is a proposed setting, not a game requirement. Track bag and inventory separately and read the actual role level; do not assume maximum bag capacity.

A normal egg pickup requires a valid colour call, matching ground item, available capacity, reachable location, and the same wave/epoch at commitment. Wrong or over-capacity pickups can explode [S4]. Ground-item colour names/IDs are safer identifiers than visual colour alone.

### Supply and cannon policy

Maintain a small, known supply instead of maximising carried eggs. Prefer loading red when available; use green as a fallback. Blue is not a damage fallback. Anyone can operate a supplied launcher; its range and ammo availability still matter [S8].

The collection call limits what can be picked up now, not which colour of already collected egg may be loaded. A player can retain useful earlier eggs across a call change. Do not deliberately collect a wrong colour to satisfy the preferred red-egg policy.

Suggested cannon target order:
1. An escaping runner that the launcher can actually reach.
2. A remaining enemy whose assigned role is stalled.
3. A Penance Healer or combat enemy requiring cleanup.
Do not repeatedly select an out-of-range runner. On wave 10, the lack of an east cannon makes east-trap rescue by cannon unsuitable as a planned dependency.

Conversion is optional and outside the baseline. Its success depends on role level, so verify the resulting eggs and never make a conversion assumption the only runner-recovery path.

If the hopper is full, retain useful eggs and allow firing to create room. Pause collection rather than repeatedly attempting full-capacity pickups. Normal resource disposal must exclude the horn, bag, and every Queen-stage egg.

## 8. Defender

### Operating rule: preserve the main setup

The Defender's first responsibility is to keep the east-trap setup handling the main group. Do not abandon a working or repairable setup to save one runner. A single deliberate escape is acceptable when chasing it would allow several others to wander or escape. This is a strategy choice that accepts some point loss in exchange for stability; it does not mean escapes are free or remove the runner from the wave.

Use three phases:
- OPENING: establish the marked route and obtain repair supplies.
- MAIN_GROUP: service the trap and local bait while runners are still arriving.
- CLEANUP: handle deferred runners once the main group is dealt with and remaining spawn obligations are reconciled.

An outlier does not interrupt OPENING or MAIN_GROUP unless a brief local intervention passes the safety checks below. A cannon operator can help independently.

### Bait model and named locations

Use the east trap. Track each ground drop as:
`tile, dropSequence, itemType, quantity, waveId, dropEpoch, classification=GOOD|BAD|UNKNOWN`.

Good/bad is determined when bait is dropped and persists across call changes [S6]. Preserve working ground piles. UNKNOWN bait is not trusted kill bait. The current incoming instruction determines how to classify a new drop, not an old one.

Use this marker vocabulary for waves 1–9. “North” and “west” mean map directions regardless of camera rotation.

| Marker | Location | Action and purpose |
|---|---|---|
| T | East trap object/tile | Read charges; repair from a reachable adjacent position |
| K | One tile immediately north of T | Main kill-bait pile: good bait; also the calibrated holding tile when blocking |
| G | Marked southern guard point below T | One bad bait during opening; catches some runners passing south |
| M | Marked point between the three large spikes | One bad bait during opening; supports the lure route |
| N | Tile immediately west of the northern large spike | Four good bait during opening; northern lure, separate from K |
| L | Marked bait point by the north log spawns | One bad bait during opening |
| S | Actual log and hammer pickup locations | Gather repair supplies; never confuse L's bait with log pickups |
| R | T itself, one tile south of K | Ordinary-wave release position after a validated hold |
| C | Accessible cannon/hopper | Teammate assistance during the main phase; Defender use during safe cleanup |

K and N are the two ordinary-wave GOOD locations. They have different jobs: K is the continuously maintained kill pile; N is primarily an opening lure. Do not leave K unserviced merely to rebuild N.

These landmarks follow the published opening [S5]. “Below the trap,” “between spikes,” and “by logs” are landmark descriptions, not sufficient final coordinates. Capture exact G/M/L markers and collision-safe approaches from the arena's validated tile-marker pattern before implementation. Do not invent an offset or infer the target from the nearest similarly named object.

### Opening order: waves 1–9

Execute this route in order:
1. Call the Healer's poisoned food and obtain Defender bait.
2. Go to G: drop ONE bad bait.
3. Go to K: drop FOUR good bait.
4. Go to M: drop ONE bad bait.
5. Go to N: drop FOUR good bait.
6. Go to L: drop ONE bad bait.
7. Pick up planned logs and a hammer at S, respecting inventory room.
8. Return directly to the east-trap service area and inspect T and K.

The exact opening locations and quantities above are the standard pattern [S5]. Do not scatter extra bait while travelling between them. In particular, do not add an improvised good-bait pile farther south.

Call changes pause only the next drop until its correct item is established. Keep completed drops and resume the next unfinished step. If a drop quantity is uncertain, reconcile it before adding more; do not restart the route and duplicate all piles.

If one early runner misses the route, record it as deferred and finish the opening. Do not pursue it across the arena. An available cannon operator can attempt the rescue while the Defender continues.

If supplies cannot be collected as planned, obtain enough for the immediate repair and return; do not wait for an optional spare while the main setup needs service. If the opening was missed badly, prioritise a working T, known good bait at K, and repair supplies, then stabilise arrivals. Rebuilding all distant markers is secondary.

### Main-group service order

Repeat these checks in order, after survival and calling:
1. **Broken or uncertain trap:** inspect and restore T. When a validated holding position can stop runners consuming bait, establish it during repair.
2. **Kill bait absent or uncertain:** establish known good bait at K. Do not release a held group without it.
3. **Working trap and bait:** let arriving runners reach K. Watch actual kills and trap state.
4. **Repair supply shortage:** make a necessary supply trip when safe; it outranks an optional straggler chase.
5. **Nearby outlier:** attempt the bounded local recovery below only if the setup can remain safe.
6. **Far outlier:** defer to cannon help or later cleanup.
7. **Distant lure maintenance:** revisit N/M/L only if observations show it is necessary and the main setup can safely be left; not on a repeating timer.

The order of restoring trap versus bait is contextual: a usable trap with no kill bait needs bait, while bait at a broken trap needs repair. When both are missing, hold safely if possible, restore both, then release. Never satisfy one prerequisite and assume the other.

Track trap charges as 2, 1, 0, or UNKNOWN. A restored trap has two charges [S5]. Prefer direct object/state evidence; a runner disappearing does not prove a trap charge was spent.

Use ordinary kills as the baseline. If holding at K, a validated ordinary-wave release moves one tile south to R. Confirm the trap is restored and known good bait remains first. A full-stack kill is optional, and survivors remain tracked. Keep the normal repair budget even if some stack kills reduce actual log usage.

A teammate crossing an active holding tile can disrupt the block. Re-establish it if necessary and verify behaviour. Do not stand on K indefinitely when intending to allow individual kills: the holding position can prevent bait consumption.

### Supplies

Minimum log planning for ordinary kills, assuming two starting charges, no extra early repair, and the published runner totals [S5]:

| Wave | Runners | Repair logs |
|---|---:|---:|
| 1 | 2 | 0 |
| 2 | 3 | 1 |
| 3 | 4 | 1 |
| 4 | 4 | 1 |
| 5 | 5 | 2 |
| 6 | 6 | 2 |
| 7 | 6 | 2 |
| 8 | 7 | 3 |
| 9 | 9 | 4 |
| 10 | 6 | 2 |

Dynamic planning: `ceil(max(0, remainingRunners - currentCharges)/2)`. Treat unknown charges conservatively. A spare log is useful when practical, but waiting for it must not undermine a ready main setup. Preserve the hammer and horn; count inventory slots before supply pickups.

At K, replenish in small confirmed batches when the pile is depleted or insufficient for the next observed arrivals. The opening's four bait is not a permanent minimum that must be restored after every single consumption. Leave still-good bait alone across calls. Northern lure bait is not automatically replenished just because K was replenished.

### Classifying errant runners

An errant runner is one that has visibly bypassed the main approach, is stuck away from it, or is moving south without a plausible route back to K. Temporary idle movement or a short side-step is not enough to trigger a chase.

Assign one of these statuses:

| Situation | Defender response during OPENING / MAIN_GROUP | Assistance |
|---|---|---|
| Near K and plausibly returning | Maintain T/K; observe | Usually none needed |
| Just south, near existing G or service route | One bounded local correction if safe | Cannon if readily available |
| Far south / near exit | Defer; do not follow | Existing cannon operator targets it if in range |
| Far west or otherwise remote | Defer; retain last seen location | Cannon if in range |
| Stuck at an obstacle | Record for cleanup unless reachable incidentally | Cannon if accessible |
| Already escaped | Record escape event; prepare for return | Do not waste shots on a departed target |
| Several runners drifting away | Repair the common trap/bait/lure failure | Avoid chasing individuals |

A single outlier is a local loss. Several outliers are evidence of a systemic setup issue: inspect charges, K bait, route placement, call validity, and blocking. Fix that shared cause before pursuing individual runners.

### Bounded local recovery

During the main phase, a local correction is allowed only when ALL of the following hold:
- T is known usable and K has known good bait.
- The Defender is not needed to maintain a holding block.
- No approaching group or urgent repair/supply task requires immediate attention.
- The proposed movement stays inside a small, manually validated service area.
- The route and interaction fit within the configured round-trip budget.
- This runner has not already consumed its local-correction allowance.

Proposed conservative budget: one extra bad-bait drop and at most FOUR game ticks, including travel out and back. This is an adjustable design limit, not a runner-mechanic timing requirement. If the implementation cannot assess the safety conditions, skip the intervention.

Use an existing validated southern guard marker or another prevalidated local recovery tile. Do not choose arbitrary drops from a simplistic “nearest bait wins” model. After the one attempt, immediately resume trap service; do not wait beside the outlier for it to eat.

Abort the intervention on a broken trap, insufficient K bait, call uncertainty affecting a new drop, new incoming group, or expiring budget. Expiry means DEFERRED, not an immediate second attempt. Keep that lockout through MAIN_GROUP so a controller cannot spend four ticks on the same runner every decision cycle. Re-enable targeted recovery during CLEANUP or after clear observed reintegration.

No long southern bait trail is part of the default main-group routine. A trail is a cleanup tool when the main setup is secure, not an obligation whenever one runner escapes the lure.

### Cannon ownership

The preferred rescue operator is a Collector already near a loaded cannon or a teammate who has completed primary work. The Defender stays with the trap while regular arrivals or a held group still need it.

Cannon support requires an actual reachable target and useful ammunition. Do not assume that a cannon can reach every part of the arena. Prefer red eggs; green can help when red is absent; blue is not the normal rescue option [S8].

If no operator, ammo, or range is available, accept the escape when the alternative is disrupting the main setup. Do not pull a busy Healer from urgent healing or another role from essential work solely to save one runner.

An assistance request is advisory bookkeeping: it does not mark the runner rescued. Track target status until observed killed, escaped, reintegrated, or uncertain. Any player-facing communication remains manual unless separately authorised.

### Accepting an escape and later cleanup

Escaped runners return to the encounter [S5]. Record the event, accept its point cost, and keep the trap ready for their return. Do not treat an escape as a kill or blindly decrement the native remaining count.

Enter CLEANUP only when the main group is cleared and expected further arrivals are reconciled with native counters and spawn state. No visible runners nearby is not sufficient.

Cleanup order:
1. Keep the east trap working and K supplied for any returning runner.
2. Let an available cannon operator kill reachable leftovers.
3. For a returned runner already on the normal approach, keep servicing the trap.
4. For a stuck or remote survivor, perform a deliberate targeted recovery while no main group is endangered. A short bad-bait recovery trail may now be used in the southern area.
5. Confirm movement after each small correction. Bad bait is not an unconditional northward command when the runner is already far north [S6].
6. Stop extending the trail once the runner rejoins the normal route.
7. Resolve every survivor against native remaining-runner evidence before marking Defender complete.

If native state cannot reliably establish that arrivals are finished, stay with trap service and defer remote pursuit. The cost of briefly waiting for a return is preferable to recreating the same escape repeatedly.

### Wave-10 locations and order

Use a separate marker set; do not reuse ordinary-wave K or R offsets.

| Order / marker | Wave-10 location | Action |
|---|---|---|
| G10 | South of the large spike below the east trap | One bad bait |
| K10 | Immediately west of the east trap | Four good bait; principal kill/holding pile |
| N10 | East of the large spike between runner and healer caves | Four good bait |
| L10 | Marked point by logs | One bad bait |
| S10 | Log and hammer spawns | Obtain two planned repair logs and hammer |
| T10 | East trap | Return and run the same maintenance-first loop |

These opening landmarks follow the wave-10 pattern [S5]. A normal validated hold at K10 releases one tile SOUTH of K10, not automatically onto T10. Verify the exact approach/collision geometry in that arena before relying on it.

Use the same defer-versus-local-recovery policy. Wave 10 has no east cannon [S5], so a runner stranded by the east trap must not be assigned to an imaginary east-cannon rescue. Check actual west-cannon range for other targets; otherwise defer until the main group is secured, then recover the survivor directly.

Clear remaining runners before leaving for Queen egg duties. The Queen appearing does not cancel cleanup.

### Defender decision policy

```text
after survival and required outgoing call:
    if phase == OPENING:
        continue next valid opening step
        record outliers; do not chase
    else if trap/bait/block needs service:
        service the main setup
    else if essential repair supplies are missing:
        restore supply readiness
    else if main-group obligations remain or are uncertain:
        if nearby outlier passes ALL local safety gates
           and its one-attempt budget is unused:
            make bounded local correction; return
        else:
            defer outlier; allow independent cannon assistance
    else:
        enter CLEANUP
        preserve a working trap
        resolve deferred and returning runners
    complete only on confirmed remaining-runner clearance
```

Example: three runners are approaching K while one is nearly at the southern exit and T is broken. Repair T and preserve the three-runner group. Let a cannon operator try the southern runner; if that is unavailable, accept its escape and handle its return. Following the lone runner would turn one outlier into a potentially broken entire setup.



## 9. Healer

### Baseline routine

1. Call Defender bait.
2. Stock poisoned food and keep the healing vial.
3. Fill the vial at the spring before starting the conservative opening.
4. Move east and begin covering Penance Healers.
5. Apply current valid poisoned food to each unconfirmed or unpoisoned enemy before spending many applications on one.
6. Interrupt poisoning to address survival.
7. Refresh weak or expired poison and finish enemies as food allows.
8. Restock based on actual stock; refill the vial on safe return trips.
9. Continue health checks and calls until wave completion.

Correct poisoned food deals direct damage and starts or refreshes poison. Poison weakens over time [S7]. Therefore one food application is not a universal guarantee of a kill. The plan deliberately does not use a single timing script or fixed food count for every wave.

### Target tracking

Per Penance Healer, record:
`waveId, npcIndex, spawnGeneration, firstSeenTick, lastPosition, lastConfirmedApplication, latestObservedPoisonDamage, confidence`.

NPC indexes can be reused. A new spawn at an old index must not inherit the old creature's poisoned status. Losing visibility makes state uncertain; it does not prove death or poison expiration.

Poison priority:
1. Newly spawned enemy with no confirmed application.
2. Previously seen enemy with unknown/expired poison.
3. Enemy showing weak poison.
4. A reachable surviving enemy to finish when coverage and supplies are healthy.

Keep a stable coverage queue. A Healer that runs after one distant monster can miss several newcomers. Cover reachable newcomers, record the distant one, and pursue it when coverage and team health permit. Never cross an active Defender holding tile for a slightly shorter route.

With two Healers, maintain primary target assignments but treat team poison state as uncertain unless confirmed observations are available. It is better to use an occasional redundant correct food than leave an enemy unpoisoned because the other player was assumed to handle it.

### Healing policy

These are proposed conservative settings:
| Condition | Response |
|---|---|
| Below 35% of maximum HP or rapid damage with little margin | Emergency: interrupt other tasks |
| Below 60% of maximum HP | Heal at next safe opportunity |
| Medic request | Inspect immediately; heal according to actual risk |
| Returning from refill | Top up nearby injured teammates |
| Queen phase | Aim for at least 50 HP where maximum HP allows; otherwise near full |

Use damage trend and distance to help as well as HP percentage. A remote teammate at 55% under attack may be less safe than a nearby one at 45% with no current threat. Ties: immediate danger, then lower survival margin, then reachable target. Once safe, resume the interrupted call or role task.

Maintain a dose reserve based on the actual vial state. Never mark a teammate healed because the vial was clicked. Verify the health change or dose use in context. If healing is interrupted, re-evaluate recipient identity and distance before retrying.

Only Healers can use the spring for their own direct recovery [S5]. A solo Healer must plan its own return trips; two Healers can cover each other. Do not assume every role has access to every teammate's exact HP: the native Healer interface is the intended source, and unavailable values must be UNKNOWN.

Restock when the current correct food is low, the next coverage cycle would exhaust supplies, or several types have depleted. Keep a whitelist of disposable ordinary poisoned food; preserve horn, vial, and Queen eggs. After restocking, recount items rather than assume a full inventory transaction succeeded.

## 10. Cannon assistance and completion

Cannon assistance is secondary to active calls, survival, and role obligations. Red provides direct damage; green provides a smaller direct hit and poison; blue stuns rather than serving as normal damage cleanup [S8]. Prefer red, use green if needed, and leave blue firing out of the baseline.

A helper at a launcher should:
1. Check reachable target and ammo.
2. Select a target that addresses the largest current failure.
3. Confirm ammo use/projectile or damage evidence.
4. Reassess instead of repeating clicks against a disappeared or out-of-range entry.
5. Yield promptly when a call or survival task requires attention.

Do not equate a temporary lack of visible enemies with completion. Some enemies may be out of view, waiting to spawn, or returning after an escape. Use native remaining counters and the actual end-of-wave transition. No role independently advances the team just because its local task list is empty.

## 11. Wave 10 and the Penance Queen

### Separate encounter states

Track `queenPresent` separately from `regularPenanceComplete`. The Queen can appear after four minutes even while ordinary enemies remain; her appearance is not proof of the launch prerequisite. Normal Penance must be cleared before omega firing [S4]. Queen spawns are separate entities, not regular-wave counter substitutes.

Use states:
`REGULAR_WORK -> QUEEN_VISIBLE_WITH_WORK | QUEEN_PREPARATION -> EGG_CYCLE -> LAUNCH -> VERIFY_DEAD`.

If she arrives early, H1 prioritises survival while every role finishes its regular work. Continue using native instructions for regular enemies wherever required. If a required instruction becomes unavailable, treat that as a recovery condition; do not invent a correct style.

Before egg work, replenish health, fill healing supplies, clear ordinary work, and confirm the wave-10 landmark profile. H1 remains the survival owner through the entire fight.

### Egg pipeline

| Owner | Input | Transformation | Recipient |
|---|---|---|---|
| Collector | Queen's yellow egg | Collect | H2 / designated Healer |
| Healer | Yellow | Use on poison crater | Designated Attacker |
| Attacker | Poisoned | Add mushroom spikes | Defender |
| Defender | Spiked/poisoned | Use on lava crater | Collector |
| Collector | Omega | Load hopper | Designated launcher operator |

This is the CHAD handoff order [S9, S10]. With duplicate roles, use a named primary recipient rather than the nearest player wearing the right role icon.

Baseline batch size is one egg. After the team can execute that loop consistently, increase to two; do not require everyone to process a large batch before any feedback is available. Use one regular shooter and plan enough eggs for the ordinary kill, commonly around eight, rather than rely on the simultaneous-firing shortcut [S9]. Keep the Healer healing while others handle firing.

For each stage:
1. Confirm the input item is actually present.
2. Confirm the relevant object or recipient.
3. Perform one transformation or handoff.
4. Observe the new stage item or transfer evidence.
5. Resume only after the state is reconciled.

Give the Healer a reachable agreed handoff point near the healing route. The Collector brings eggs to that point rather than making the survival Healer chase the Collector toward the Queen. Avoid lingering beside the Queen for extra eggs when health is falling.

The Attacker pre-collects spikes only once regular combat work and survival allow. The Defender does not leave unfinished runners to wait for eggs.

### Handoff and launch failures

| Failure | Recovery |
|---|---|
| Recipient has no space | Recipient clears only expendable normal supplies; sender retains egg |
| Expected egg absent after a handoff attempt | Inspect current inventory stage and recipient evidence; do not fabricate receipt |
| Transformation interrupted | Check whether stage changed before retrying |
| Egg chain stalls | Locate its last confirmed owner/stage and resume there |
| Hopper interaction fails | Check range, capacity and actual omega count |
| Firing unavailable | Recheck ordinary Penance completion, ammo, interface and target |
| Queen remains alive | Produce another egg; visible survival/death evidence decides |
| Health deteriorates | Pause egg work and restore the healing plan |

Use ordinary sequential firing. The strategy does not depend on team-leader firing quirks, spam timing, or multiple hits from one egg. Do not stop maintaining healing because the estimated last egg is loaded.

## 12. Suggested controller architecture

Use five independent modules:
- Observation adapter: native interface, inventory/equipment, scene entities, messages, game state.
- Encounter model: wave, role, call epochs, tracked enemies/resources, Queen stage.
- Policy engine: deterministic priority decisions with explicit uncertainty.
- Progress monitor: expected evidence, timeouts, interruption and recovery.
- Presentation adapter: only capabilities separately reviewed for RuneLite/Jagex compliance.

Compute stable snapshots on GameTick rather than assuming render frames equal server progress. RuneLite documents GameTick as running after packet processing, with a tick approximately 0.6 seconds [S12]. Event callbacks should update observations; the decision cycle reconciles them.

Conceptual pseudocode; it deliberately has no input-generation adapter:

```text
onGameTick:
    snapshot = observeAvailableNativeState()
    model.reconcile(snapshot)

    if snapshot.waveChanged or snapshot.loading or snapshot.loggedOut:
        discardInvalidTargetsAndPendingDecisions()
        return status("Re-establish encounter state")

    if model.encounterIdentityUnknown:
        return status("Re-establish wave and role identity", reason)

    if survivalEmergency(model):
        return plan(survivalResponse(model))

    if outgoingCallDue(model) and outgoingNativeValueKnown(model):
        return plan(callTask(model.outgoingNativeValue))

    if pendingTask:
        if prerequisitesInvalid(pendingTask, model):
            cancelOrSuspend(pendingTask)
        else if successEvidence(pendingTask, model):
            complete(pendingTask)
        else if progressObserved(pendingTask, model):
            return status("Waiting for progress")
        else if timeoutReached(pendingTask):
            return plan(recoveryTask(pendingTask, model))
        else:
            return status("Waiting for confirmation")

    task = rolePolicy.highestPriorityEligibleTask(model)
    return plan(task, reason, prerequisites, completionEvidence)
```

A CALL attempt needs special handling: if native evidence cannot prove recipient receipt, record SENT_UNCONFIRMED rather than claiming acknowledgement. Do not block all local play forever waiting for an acknowledgement the API does not expose. Use observed call feedback where available and a bounded manual recall/recovery workflow otherwise.

Never implement UNKNOWN as zero, false, dead, full, or correct. Each would produce a different class of silent failure. Apply uncertainty gates to the tasks that need the missing observation: an unknown incoming call must not suppress a valid emergency heal or trap repair.

## 13. Reliability profiles

All profiles use the same mechanics and call validation. They differ in workload margins:
| Setting | Beginner default | Practised optional |
|---|---|---|
| Team healing | Two Healers | One only after demonstrated stability |
| Defender | Individual kills and normal repair budget | Validated holding / multi-kill |
| Healer | Early vial preparation; generous health margin | Fewer unnecessary return trips |
| Collector | Two-slot inventory margin; frequent deposits | Larger batches when capacity is known |
| Attacker | One weapon profile; stable targeting | Coordinated split targets |
| Queen | One egg per cycle; one ordinary shooter | Two-egg batches |
| Failure handling | Stop and explain after repeated failure | Same stopping rules |

Increasing skill never enables guessing calls, assuming transfers, discarding resource checks, or skipping end-of-wave confirmation.

Upgrade roles as an optional aid rather than a prerequisite for following the strategy. In particular, the Defender lure-range change at level 2 changes placement tolerance [S6]. Re-test at the lowest level the implementation claims to support.

## 14. Validation and acceptance

No live game run or plugin implementation was performed for this document. Validate observational adapters and geometry before claiming automatic or guided execution works.

Test in this order:
1. Record manual ordinary-wave runs; verify wave/role/call parsing and entity identities.
2. Validate landmark templates separately for ordinary waves and wave 10.
3. Exercise each role with observation only and compare its decisions to actual outcomes.
4. Run full groups of mixed skill, with no speed target.
5. Add optional conveniences only after the baseline survives errors.

Required scenarios:
- Delayed and missing partner calls; repeated call text in a new epoch.
- Call change during movement, item selection, and continuous combat.
- Food depletion of one type while other types remain.
- Collector level-1 bag and nearly full inventory.
- NPC death, out-of-view movement, and reused NPC index.
- Defender call change with old good bait still present.
- Broken/one-charge/unknown trap; no spare log.
- Another teammate crosses a holding tile.
- Missed opening and a runner already south.
- One distant runner while the trap is broken and a main group is approaching: repair and defer, never chase.
- A local recovery budget expires: mark deferred and prevent repeated immediate attempts.
- Cannon assistance is unavailable or out of range: accept one escape and service its return.
- Several outliers caused by empty kill bait: fix the common setup instead of chasing each.
- No red eggs; no eggs at all; full hopper; cannon target out of range.
- Empty healing vial, Healer self-damage, remote injured teammate.
- New/reserve Penance Healer after initial coverage.
- Queen appears before ordinary enemies finish.
- Full recipient inventory, interrupted transformation, and egg handoff uncertainty.
- Region reload, reconnect, manual deviation, and changed UI layout.

Suggested release gate: at least three complete consecutive 1–10 runs in each claimed team/profile configuration, including deliberate recovery drills, with no unresolved deadlock and an explanation for every incorrect action. This is an initial acceptance threshold, not statistical proof of reliability. A larger sample is needed before estimating completion probability.

Measure: wave completion; deaths; runner escapes; wrong-call-dependent actions; time without a valid call; unsuccessful interaction retries; unconfirmed handoffs; recovery success; manual intervention count. Record completion time for diagnosis, not as the primary score.

The implementation is ready for broader use only after every pending state has an exit path, all required observations have been proven available, and unobservable cases produce an explicit recovery response.

## Sources

[S1] Jagex, Macro and client features not permitted:
https://legal.jagex.com/docs/rules/macro-and-client-features-not-permitted

[S2] RuneLite, Rejected or Rolled Back Features:
https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

[S3] Jagex, Third Party Client Guidelines:
https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1

[S4] OSRS Wiki, Barbarian Assault:
https://oldschool.runescape.wiki/w/Barbarian_Assault
Retrieved through indexed page content, including the Barbarian_assault_points alias.

[S5] OSRS Wiki, Barbarian Assault/Strategies:
https://oldschool.runescape.wiki/w/Barbarian_Assault/Strategies
Retrieved through indexed content, including the Barb_assault_strats alias.

[S6] OSRS Wiki, Penance Runner mechanics, indexed mirror:
https://osrsindex.com/wiki/penance-runner?site=osrs_wiki
Canonical article: https://oldschool.runescape.wiki/w/Penance_Runner

[S7] OSRS Wiki, Captain Cain's mechanics notes:
https://oldschool.runescape.wiki/w/User:Captain_Cain/Sandbox
Used for poison behaviour; fixed damage budgets and timing scripts are deliberately not required by this plan.

[S8] OSRS Wiki, Egg launcher, indexed mirror:
https://osrsindex.com/wiki/egg-launcher?site=osrs_wiki
Canonical article: https://oldschool.runescape.wiki/w/Egg_launcher

[S9] OSRS Wiki, Omega egg, indexed mirror:
https://osrsindex.com/wiki/omega-egg?site=osrs_wiki
Canonical article: https://oldschool.runescape.wiki/w/Omega_egg

[S10] OSRS Wiki, Poisoned egg:
https://oldschool.runescape.wiki/w/Poisoned_egg

[S11] RuneLite API, WorldPoint:
https://static.runelite.net/runelite-api/apidocs/net/runelite/api/coords/WorldPoint.html

[S12] RuneLite API, GameTick:
https://static.runelite.net/runelite-api/apidocs/net/runelite/api/events/GameTick.html

Research limitations: several Wiki pages were accessible through search-indexed content rather than direct live fetches. Some indexed material is older than this document. Exact game data mappings, trap geometry, and call-refresh semantics require live validation against the supported game/client build. RuneScape 3 sources were excluded from the strategy.

