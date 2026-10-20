# Guidance for AI coding agents

BA Autopilot is a RuneLite Plugin Hub plugin that coaches Barbarian Assault: it reads the game,
plans the next step for the player's role and draws it. Read [Development](docs/Development.md)
for the layout, recordings and tools.

## Shape

- `state/BaTracker` turns client events into an immutable `Snapshot`; `plan/Planner` (pure, no
  client) turns a `Snapshot` and `Settings` into one `Instruction`; overlays only draw it.
- Planner changes come with unit tests built from `Snapshot.builder()`, and must keep the replay of
  every recording in `src/test/resources/recordings` passing (`gradlew test`).
- Debug and analysis code (recorder, replay, simulator, audits) lives in `src/test` only.

## Build

- JDK 11 (`JAVA_HOME`), Gradle wrapper, RuneLite 1.13.2. `gradlew test` before every commit.
- A running development client can lock `build/` outputs; build in a scratch copy if so.

## Plugin Hub rules

- The hub's review bot refuses more than 200,000 o200k tokens of `src/main/java` with comments
  removed. String literals count; resources, tests and comments don't. Keep long text (tutorial
  pages) and data in `src/main/resources` where possible.
- No `java.lang.reflect` in `src/main`, no third-party runtime dependencies, and the repository
  builds on its own: UI kit components are copied into `ui/kit`, not pulled from another project.
- No file I/O in `src/main` (`java.io.File`, `java.nio.file`, `RuneLite.RUNELITE_DIR`): the hub
  holds such plugins for extra review. Read bundled resources; file tools belong in `src/test`
  (the editable tutorial layout is `dev/TutorialLayoutFile`). If data must be saved, use the
  plugin's `getPluginDirectory()` Filepath API.
- The plugin guides; it never clicks, types or automates input. Menu changes are limited to the
  existing three (horn left-click, off-role Attack removed, the step's dispenser/vial option as
  left-click). Don't add menu reordering or deprioritising: egg reordering was removed for review.
- Bump the version in `build.gradle` and `runelite-plugin.properties` together.

## Privacy

- Recordings keep only game messages and horn calls, with players renamed `Me` / `PlayerN`. Never
  commit other players' chat, overhead text or names. The recorder runs only in developer mode.
- `docs/` is ignored except this guide's companion `docs/Development.md`: keep notes and evidence
  there locally and don't force-add them.

## Tutorial and overlay text

- Tutorial pages that show the overlay use `pilot(Step, Situations...)`: the real planner and the
  real overlay render them. Never hand-write overlay text; `TutorialPilotTest` checks every page.
- Write complete, plain sentences. Say exactly what to click and where, for example "Click the BA
  Autopilot button in RuneLite's sidebar, open the Collector section and turn on ...". Never leave
  a step implicit.
- Call it the "BA Autopilot overlay", never a box. The overlay shows no explanation lines; anything
  worth keeping on screen is a persistent, styled row.
- The Introduction and Basics tutorials are curated by the maintainer: change them only when the
  result is clearly better.

## Planning conventions

- One step at a time. `plan/Chooser` keeps a committed task until a higher tier appears; weighted
  roles (the Collector) carry that continuity in their scores instead.
- When order doesn't matter, the nearer task first; finish a trip instead of flip-flopping.
- Prefer a fixed, bounded constant over calibration machinery.
- Game mechanics come from recordings and the OSRS Wiki; list anything not yet seen in game under
  "Not yet verified in game" in `docs/Development.md`.
