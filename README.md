# BA Autopilot

A Barbarian Assault coach for RuneLite. BA Autopilot reads your role from the game and, every game
tick, shows you the next thing to do. The step appears in the BA Autopilot overlay. Its target is
highlighted in the world, the item to use is outlined in your inventory, and your horn is set to
the right call.

BA Autopilot guides your actions one step at a time. **It never clicks or does anything
automatically. You are always the one who plays.**

> **Beta (0.1-beta).** BA Autopilot is still being tested. There may be bugs, and some mechanics
> may not work reliably yet. Please join the **[Discord](https://discord.gg/7VAbrPsUzT)** to share
> feedback and suggestions.

## Features

- **The next step for every role.** The BA Autopilot overlay shows your role, the wave and the call
  timer, then one step with an icon, such as "Take Red egg", "Load the hopper", "Use Poisoned worms
  on the Penance Healer" or "Drop Tofu". When something needs your attention right away, the step
  turns red.
- **Calls made easy.** The overlay shows the call you must give and the call you heard. When the
  call changes, the right `Tell-` option becomes your horn's left-click, so a single click on the
  horn makes the call. A reminder tint (optional) tells you a call is owed.
- **Highlights.** The target of the step (a Penance, an egg pile, a dispenser, a launcher, the
  trap) is highlighted in the scene. The item you need is outlined in your inventory, and a walking
  route can be drawn to it.
- **Role guidance:**
  - **Attacker:** which arrows to wield for the called style, which Penance Fighter or Ranger to
    attack, and when to stop. With two attackers using the plugin, targets are split between you.
  - **Collector:** which eggs to pick up and when to load them. Eggs of the called colour get a
    green dot when a left-click takes them, or a yellow dot when another item is on top and you
    need to right-click and pick Take. Nearby left-click eggs are preferred. You are also told when
    to preload a launcher, when to make room in your inventory, and when to fire at an escaping
    Runner.
  - **Healer:** which poisoned food to use and on which Penance Healer, when to restock at the
    dispenser, and which teammate to heal with the vial. The Penance Healers can be outlined by
    poison level.
  - **Defender:** the opening bait pattern, collecting the logs and hammer, repairing the trap and
    restocking bait. Bait on the ground is labelled good or bad, and the bait range can be drawn.
  - **Wave 10 and the Queen:** the omega egg chain for every role, with the Collector's yellow egg
    batch and reserve.
- **Wave info.** Per Penance type: how many are out, how many are dead, and the reserves left.
- **In-game tutorial.** Six tutorials (Introduction, Basics, Collector, Healer, Attacker and
  Defender) are staged in the Barbarian Assault lobby with models of the Penance, eggs, launchers
  and dispensers. The role tutorials show the real BA Autopilot overlay and highlights for each
  situation, so they always match what the plugin does.

## Getting started

1. Install **BA Autopilot** from the Plugin Hub.
2. Click the **BA Autopilot** button in RuneLite's sidebar to open the panel.
3. To learn the minigame and the plugin, pick a tutorial in the **Tutorial** list (start with
   **Introduction**), click **Start tutorial**, and go to room 1 of the Barbarian Assault lobby.
   Use **Next** and **Back** in the tutorial box to move through the pages.
4. Join a team and play. The BA Autopilot overlay appears as soon as your role is known. Hold
   **Alt** and drag it to move it.

## Settings

The panel has a section for each role with its main options and an **Advanced** part. All options
are also in the plugin's configuration (the cog next to BA Autopilot in the plugin list), under
**Play** and **Display**. Among them:

| Setting | What it does |
|---|---|
| Attack method | Ranged, melee or magic: which equipment or spell the Attacker steps name |
| Bait guide, opening bait pattern, safety bait | How the Defender's opening and bait are planned |
| Blue-call batch, Queen yellow batch, Queen reserve eggs | How many eggs the Collector gathers before loading or passing them |
| Healer: heal teammates below | When a teammate is low enough for a vial heal |
| Retreat below HP | When you are sent to safety |
| Horn left-clicks the call | Makes the right `Tell-` option the horn's left-click |
| No Attack on penance off-role | Removes the Attack option on Penance for the Collector, Defender and Healer (it only costs points) |
| Colour egg names and spots | Colours egg names in menus by egg colour, grey when it is the wrong colour now |
| Display options | Compact overlay, target highlight, hint arrow, walking route, inventory outlines, wave info, live numbers, call notification, colours |

## Feedback & community

Join the **[Discord](https://discord.gg/7VAbrPsUzT)**. The link is also at the top left of the
BA Autopilot panel. Bug reports and suggestions are welcome there or as
[GitHub issues](https://github.com/PauloAguiar/runelite-ba-autopilot-plugin/issues).

## Credits

Game data comes from the [OSRS Wiki](https://oldschool.runescape.wiki/w/Barbarian_Assault) and
from these open-source Barbarian Assault plugins:

- BA Minigame (begosrs)
- BA Healer Order (tcourter1): wave tables, beginner tiles, regular heal codes
- BA Heal Codes (evaan)
- BA Call Highlight (rugg0064)
- BA Tiles (96jonesa)
- RuneLite's core Barbarian Assault plugin

The panel components come from the FreeArcanes UI kit.

## Privacy

BA Autopilot makes no network requests. Nothing leaves your client.

## Development

Build with JDK 11. `gradlew test` runs the unit tests and replays every recorded round in
`src/test/resources/recordings` through the planner. `gradlew run` starts a developer-mode client
with BA Autopilot and its debug recorder, which saves rounds to
`~/.runelite/ba-autopilot/recordings` (game messages and horn calls only, never other players'
chat).

## License

BSD 2-Clause, see [LICENSE](LICENSE).
