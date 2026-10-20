# Role settings sidebar

The sidebar now contains four always-visible role sections, in game order and colours:
Attacker red, Defender blue, Collector yellow, Healer green. Each shows its observed role level
and available Honour points. Missing readings display a dash. These are BA levels and points;
the panel does not label Honour points as skill XP.

| Section | Main settings | Advanced, collapsed initially |
|---|---|---|
| Attacker | Attack method | Split targets with the other Attacker |
| Defender | Bait guide | Opening pattern, safety bait, pattern tiles, bait range, runner outlines, tile bait counts |
| Collector | Blue-call batch preference; Queen yellow batch; Queen reserve eggs | Single omega loading, wrong-egg click prevention, egg colours |
| Healer | Ordinary-wave teammate healing threshold | Timing guide, poison outlines, Penance Healer table |

**General & display** is also collapsed initially. It contains retreat HP, covered calls, Queen
team stacking, horn left-click, off-role Attack removal, pickup blockers, compact instructions,
walking routes, target/inventory highlights and debug overlays. Other display details remain
available in the normal RuneLite plugin configuration.

Collector counts are editable directly. Queen yellows default to five before starting the
hand-off, with three extra yellows held until that volley is spent and the Queen survives.
The reserve is configurable from zero to eight. Blue-call batch is a weighted preference, not a hard minimum. See
[Queen reserve and wave-10 ammunition](Collector-Queen-Reserve.md),
[Collector navigation and Queen batches](Collector-Navigation.md) and
[weighted Collector decisions](Weighted-Collector.md). The Healer percentage is for ordinary
waves; Queen care uses the [weighted HP and egg policy](Queen-Healer-Care.md).

Every control writes the existing `baautopilot` configuration key through `ConfigManager`.
Config refreshes populate the controls under a synchronization guard, so they cannot echo writes
back. Controls are built once: updates preserve expanded sections and do not rebuild the view.
Number fields use the same ranges as the plugin configuration. Levels and points come from
`Snapshot.roleLevels` and `rolePoints`, populated by the game's role and Honour-point varbits.

## Information removed from the sidebar

The former panel mixed strategy references and development measurements with settings. It
showed preparation advice, a per-wave Healer timing table, personal/team performance grades,
session history, wave progress and raw tracker diagnostics. Those sections and their unused
view classes have been removed. Their purpose and measurements are recorded here; strategy
and evaluation details remain in the linked documents.

- Preparation notes described Attacker equipment and free inventory space for the other roles,
  including the Defender's optional carried hammer. Arena dispensers supply role items.
- The timing table came from `HealCode.of(codeSet, wave)`. Each line corresponded to one call,
  with entries in Healer spawn order. Guide times resolve equally urgent refreshes; current
  poison evidence takes precedence. See [Healer poison priority](Healer-Poison-Priority.md).
- Personal performance used `WaveStats`: duration, mean/worst own call reaction, missed/wrong
  calls, plus role counters for pickups, loads, shots, feeding, kills and blocked attacks. Call
  reactions run from the observed call change to the horn click, in 600 ms game ticks. The old
  call grade used green at or below 1.5 seconds, amber through 3 seconds and red above that.
- Team performance came from the wave-complete widget: escaped runners, failed attacks, wrong
  poison, eggs and HP healed. Partner delays measured when the partner's call was received.
- Session history displayed finished `WaveStats` newest first and the best mean call reaction
  for each role. Wave progress combined the local clock and kill counters with `WaveInfo`
  totals and observed trap charges.
- Diagnostics displayed HUD call text and parsing source/freshness, outgoing-call confirmation,
  call-clock position, visible Penance counts, ammunition and tracked item/object counts. These
  were observations for debugging; the sidebar did not control the planner's inputs.

Recording, tracker and planner data remain available to the
[capture replay and comparison tools](Capture-Strategy-Evaluation.md) and
[scenario simulator](Scenario-Simulator.md). See also
[Defender target stability](Defender-Target-Stability.md) and
[Penance diagnostics](Penance-Diagnostics.md) for the corresponding investigations and limits.

## Verification and preview

The panel checks exercise config-to-control refresh without writes, saving an edited egg count,
initially collapsed advanced sections, retaining expansion through refresh and observed/missing
level/point readings. The real Swing panel is painted at RuneLite's 225 px sidebar width using
the RuneLite look and feel. Preview levels/points are sample values, not live account readings.

```powershell
.\gradlew.bat test jar recorderJar previewPanel -PbaReviewJar=true -PbaReviewClassifier=queen-reserve -PbaReviewUiKitClassifier=queen-reserve --init-script docs/review-evidence/alternate-ui-kit-jar.init.gradle --console=plain
```

The previews are `build/reports/role-settings/collapsed.png` and `collector-advanced.png`.
Packages are `build/libs/ba-autopilot-0.1.0-queen-reserve.jar` and
`ba-autopilot-0.1.0-queen-reserve-dev-recorder.jar`. Restart/rebuild the development client
through the usual launcher to load the new sidebar.
