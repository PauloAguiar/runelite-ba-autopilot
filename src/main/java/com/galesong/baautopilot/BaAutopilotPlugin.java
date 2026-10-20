package com.galesong.baautopilot;

import com.galesong.baautopilot.model.BaCall;
import com.galesong.baautopilot.model.BaIds;
import com.galesong.baautopilot.model.Role;
import com.galesong.baautopilot.overlay.InstructionOverlay;
import com.galesong.baautopilot.overlay.Palette;
import com.galesong.baautopilot.overlay.PenanceDebugOverlay;
import com.galesong.baautopilot.overlay.ItemHighlightOverlay;
import com.galesong.baautopilot.overlay.SceneOverlay;
import com.galesong.baautopilot.overlay.Scenes;
import com.galesong.baautopilot.overlay.PathOverlay;
import com.galesong.baautopilot.overlay.PriorityOverlay;
import com.galesong.baautopilot.overlay.RouteFinder;
import com.galesong.baautopilot.overlay.TintOverlay;
import com.galesong.baautopilot.overlay.TurretOverlay;
import com.galesong.baautopilot.plan.Instruction;
import com.galesong.baautopilot.plan.Planner;
import com.galesong.baautopilot.plan.Settings;
import com.galesong.baautopilot.plan.Step;
import com.galesong.baautopilot.plan.Target;
import com.galesong.baautopilot.state.ArenaPath;
import com.galesong.baautopilot.state.BaTracker;
import com.galesong.baautopilot.state.WalkCost;
import com.galesong.baautopilot.state.Snapshot;
import com.galesong.baautopilot.state.WaveStats;
import com.galesong.baautopilot.tutorial.LobbyRooms;
import com.galesong.baautopilot.tutorial.TutorialBoxOverlay;
import com.galesong.baautopilot.tutorial.TutorialCamera;
import com.galesong.baautopilot.tutorial.TutorialSceneOverlay;
import com.galesong.baautopilot.tutorial.TutorialSteps;
import com.galesong.baautopilot.tutorial.TutorialScene;
import com.galesong.baautopilot.ui.BaAutopilotPanel;
import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Barbarian Assault Autopilot: recognises your role from the BA HUD and, every tick, tells you the
 * one thing to do next. It never clicks for you.
 */
@Slf4j
@PluginDescriptor(
	name = "BA Autopilot",
	description = "Recognises your Barbarian Assault role and tells you the next action",
	tags = {"barbarian", "assault", "ba", "minigame", "penance", "attacker", "defender", "collector", "healer"}
)
public class BaAutopilotPlugin extends Plugin
{
	/** Owed calls this old (ticks) trigger the optional notification. */
	private static final int NOTIFY_AFTER_TICKS = 5;
	private static final Color HORN_HIGHLIGHT = new Color(0, 255, 120);

	@Inject
	private Client client;

	@Inject
	private BaAutopilotConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private net.runelite.client.game.SpriteManager spriteManager;

	@Inject
	private BaTracker tracker;

	@Inject
	private InstructionOverlay instructionOverlay;

	@Inject
	private SceneOverlay sceneOverlay;

	@Inject
	private ItemHighlightOverlay itemOverlay;

	@Inject
	private TurretOverlay turretOverlay;

	@Inject
	private TintOverlay tintOverlay;

	@Inject
	private PathOverlay pathOverlay;

	@Inject
	private PriorityOverlay priorityOverlay;

	@Inject
	private PenanceDebugOverlay penanceDebugOverlay;


	@Inject
	private RouteFinder routeFinder;

	@Inject
	private TutorialScene tutorialScene;

	@Inject
	private TutorialSteps tutorialSteps;

	@Inject
	private TutorialSceneOverlay tutorialSceneOverlay;

	@Inject
	private TutorialBoxOverlay tutorialBoxOverlay;

	@Inject
	private com.galesong.baautopilot.tutorial.TutorialMapOverlay tutorialMapOverlay;

	@Inject
	private com.galesong.baautopilot.tutorial.TutorialInventoryOverlay tutorialInventoryOverlay;

	@Inject
	private TutorialCamera tutorialCamera;

	/** The tutorial the panel's dropdown shows. */
	private com.galesong.baautopilot.tutorial.Tutorial panelTutorial = com.galesong.baautopilot.tutorial.Tutorial.INTRODUCTION;

	@Inject
	private com.galesong.baautopilot.tutorial.TutorialActions tutorialActions;

	@Inject
	private net.runelite.client.input.MouseManager mouseManager;

	/** The panel's tutorial button: the scene starts once you're in lobby room 1. */
	private volatile boolean tutorialOn;
	/** The tutorial scene is up: everyone else (players and penance) is hidden; the box shows the page's step. */
	@Getter
	private volatile boolean tutorialShown;
	/** The page whose camera tour is playing, and where the camera was when it opened. Client thread. */
	private com.galesong.baautopilot.tutorial.TutorialSteps.Page tourPage;
	private net.runelite.api.coords.LocalPoint tourFrom;
	private String tutorialStatus = "";

	@Inject
	private net.runelite.client.callback.RenderCallbackManager renderCallbacks;

	/**
	 * Penance standing on or next to the item you're told to pick up are not drawn, so the click
	 * reaches the item (as RuneLite's Entity Hider does it: a render callback).
	 */
	private volatile java.util.Set<Integer> hiddenNpcs = java.util.Collections.emptySet();
	private final net.runelite.client.callback.RenderCallback drawListener = new net.runelite.client.callback.RenderCallback()
	{
		@Override
		public boolean addEntity(net.runelite.api.Renderable renderable, boolean ui)
		{
			return shouldDraw(renderable, ui);
		}
	};

	private final Planner planner = new Planner();

	private BaAutopilotPanel panel;
	private NavigationButton navButton;

	@Getter
	private Snapshot snapshot;

	@Getter
	private Instruction instruction;

	/** The most recent finished wave, for the lobby box. */
	@Getter
	private WaveStats lastWave;

	/** The arena is about 60 tiles across. */
	private static final int WALK_COST_STEPS = 100;

	private Step lastStep;
	private int lastNpcTarget = -1;
	/** Tick the current instruction step began, for the call notification's delay. */
	private int stepSinceTick;
	private boolean notifiedThisStep;
	private boolean arenaRunning;
	private boolean arenaOverlaysVisible;
	private boolean tutorialOverlaysVisible;
	private boolean drawListenerRegistered;

	@Provides
	BaAutopilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaAutopilotConfig.class);
	}

	@Override
	protected void startUp()
	{
		tracker.reset();
		eventBus.register(tracker);
		tutorialSceneOverlay.setShown(() -> tutorialShown);
		tutorialBoxOverlay.setShown(() -> tutorialShown);
		tutorialMapOverlay.setShown(() -> tutorialShown);
		tutorialInventoryOverlay.setShown(() -> tutorialShown);

		panel = new BaAutopilotPanel();
		panel.setOnOption((key, value) -> configManager.setConfiguration(BaAutopilotConfig.GROUP, key, value));
		panel.setOnTutorialChoice(t -> clientThread.invoke(() -> tutorialSteps.select(t)));
		panel.setOnTutorial(on -> clientThread.invoke(() ->
		{
			tutorialOn = on;
			updateTutorial();
		}));
		// Finish on the last page ends the tutorial, as the panel's Stop button does
		tutorialBoxOverlay.setOnFinish(() -> clientThread.invoke(() ->
		{
			tutorialOn = false;
			updateTutorial();
		}));
		pushOptions();
		navButton = NavigationButton.builder()
			.tooltip("BA Autopilot")
			.icon(BaAutopilotPanel.navIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		// The game's own BA icon (minigame teleports) replaces the painted one once it loads
		spriteManager.getSpriteAsync(com.galesong.baautopilot.tutorial.BaIcon.SPRITE, 0, icon ->
			SwingUtilities.invokeLater(() -> useBaIcon(icon)));
	}

	private void useBaIcon(java.awt.image.BufferedImage icon)
	{
		BaAutopilotPanel p = panel;
		if (p == null || icon == null || navButton == null)
		{
			return;
		}
		p.setBaIcon(icon);
		clientToolbar.removeNavigation(navButton);
		navButton = NavigationButton.builder()
			.tooltip("BA Autopilot")
			.icon(BaAutopilotPanel.scaled(icon, 16))
			.priority(8)
			.panel(p)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(tracker);
		arenaRunning = false;
		tutorialShown = false;
		updateOverlayState();
		hiddenNpcs = java.util.Collections.emptySet();
		tutorialOn = false;
		tutorialShown = false;
		clientThread.invoke(() ->
		{
			tutorialActions.stop();
			tutorialScene.clear();
			tutorialCamera.release();
		});
		routeFinder.clear();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		client.clearHintArrow();
		snapshot = null;
		instruction = null;
		tracker.reset();
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		Snapshot fresh = tracker.snapshot();
		if (!fresh.isInArena())
		{
			stopArena();
			if (tutorialOn || tutorialShown) updateTutorial();
			return;
		}
		arenaRunning = true;
		WorldPoint lastTarget = instruction == null ? null : instruction.getTarget().getTile();
		// Remember the last NPC target across steps without one (a call or a hold mid-fight)
		if (instruction != null && instruction.getTarget().getKind() == Target.Kind.NPC)
		{
			lastNpcTarget = instruction.getTarget().getId();
		}
		snapshot = fresh.toBuilder().lastTargetTile(lastTarget).lastTargetNpc(lastNpcTarget)
			.lastTask(instruction == null ? null : instruction.getTask())
			.lastStep(instruction == null ? null : instruction.getStep())
			.lastActionTarget(instruction == null ? null : instruction.getTarget())
			.tasks(instruction == null ? com.galesong.baautopilot.plan.TaskMemory.empty() : instruction.getTasks())
			.walkCost(fresh.isInArena() ? walkCost(fresh) : null)
			.build();
		instruction = planner.plan(snapshot, settings());

		int tick = snapshot.getTick();
		if (instruction.getStep() != lastStep)
		{
			lastStep = instruction.getStep();
			stepSinceTick = tick;
			notifiedThisStep = false;
		}
		if (config.notifyCall() && lastStep == Step.CALL && !notifiedThisStep && tick - stepSinceTick >= NOTIFY_AFTER_TICKS)
		{
			notifiedThisStep = true;
			notifier.notify(instruction.getHeadline());
		}
		updateHintArrow();
		hiddenNpcs = blockers();
		if (tutorialOn || tutorialShown) updateTutorial();
		updateOverlayState();
		if (config.showPath())
		{
			routeFinder.update(instruction, snapshot);
		}
		else
		{
			routeFinder.clear();
		}
		pushToPanel();
	}

	/** Run once on exit, rather than rebuilding lobby guidance and Swing updates every tick. */
	private void stopArena()
	{
		if (!arenaRunning && snapshot == null && instruction == null) return;
		Snapshot progress = snapshot;
		arenaRunning = false;
		snapshot = null;
		instruction = null;
		lastStep = null;
		lastNpcTarget = -1;
		notifiedThisStep = false;
		hiddenNpcs = java.util.Collections.emptySet();
		routeFinder.clear();
		client.clearHintArrow();
		List<WaveStats> done = tracker.getPerformance().history();
		lastWave = done.isEmpty() ? null : done.get(done.size() - 1);
		updateOverlayState();
		// Keep the last observed levels/points in the settings sidebar without polling while idle.
		BaAutopilotPanel p = panel;
		if (p != null && progress != null) SwingUtilities.invokeLater(() -> p.updateProgress(progress));
	}

	/** No live overlays or per-entity render callback are registered while outside the arena. */
	private void updateOverlayState()
	{
		boolean live = arenaRunning;
		boolean tutorial = tutorialOn && tutorialShown;
		boolean boxWasVisible = arenaOverlaysVisible || tutorialOverlaysVisible;
		if (live != arenaOverlaysVisible)
		{
			setOverlay(sceneOverlay, live);
			setOverlay(itemOverlay, live);
			setOverlay(turretOverlay, live);
			setOverlay(tintOverlay, live);
			setOverlay(pathOverlay, live);
			setOverlay(priorityOverlay, live);
			setOverlay(penanceDebugOverlay, live);
			arenaOverlaysVisible = live;
		}
		if (tutorial != tutorialOverlaysVisible)
		{
			setOverlay(tutorialSceneOverlay, tutorial);
			setOverlay(tutorialBoxOverlay, tutorial);
			setOverlay(tutorialMapOverlay, tutorial);
			setOverlay(tutorialInventoryOverlay, tutorial);
			if (tutorial) mouseManager.registerMouseListener(tutorialBoxOverlay.mouseListener);
			else mouseManager.unregisterMouseListener(tutorialBoxOverlay.mouseListener);
			tutorialOverlaysVisible = tutorial;
		}
		if (boxWasVisible != (live || tutorial)) setOverlay(instructionOverlay, live || tutorial);
		boolean drawing = tutorial || live && config.hideBlockers();
		if (drawing != drawListenerRegistered)
		{
			if (drawing) renderCallbacks.register(drawListener);
			else renderCallbacks.unregister(drawListener);
			drawListenerRegistered = drawing;
		}
	}

	private void setOverlay(net.runelite.client.ui.overlay.Overlay overlay, boolean shown)
	{
		if (shown) overlayManager.add(overlay);
		else overlayManager.remove(overlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			if (arenaRunning)
			{
				client.clearHintArrow();
				hiddenNpcs = java.util.Collections.emptySet();
				routeFinder.clear();
			}
			arenaRunning = false;
			updateOverlayState();
			if (tutorialOn || tutorialShown) updateTutorial();
		}
	}

	/** The tutorial camera glides every client tick; it follows you again once the tutorial ends. */
	@Subscribe
	public void onClientTick(ClientTick e)
	{
		if (!tutorialOn || !tutorialShown)
		{
			return;
		}
		tutorialSteps.tick();
		com.galesong.baautopilot.tutorial.Tutorial running = tutorialSteps.tutorial();
		if (running != panelTutorial && panel != null)
		{
			panelTutorial = running;
			BaAutopilotPanel p = panel;
			SwingUtilities.invokeLater(() -> p.showTutorial(running));
		}
		com.galesong.baautopilot.tutorial.TutorialSteps.Page page = tutorialSteps.current();
		tutorialActions.update(page, tutorialSteps.elapsed());
		java.util.Set<String> visible = new java.util.HashSet<>(page.getProps());
		visible.removeAll(tutorialActions.hidden());
		tutorialScene.showOnly(visible);
		if (!page.getTour().isEmpty())
		{
			// One continuous glide through the stops (rooms that can't be placed are skipped), from
			// wherever the camera was when the page opened (reported 2026-10-19: the Queen's glide
			// started from a fixed spot instead of room 9, where the camera was)
			if (page != tourPage)
			{
				tourPage = page;
				tourFrom = tutorialCamera.here();
			}
			java.util.List<net.runelite.api.coords.LocalPoint> stops = new java.util.ArrayList<>();
			if (tourFrom != null)
			{
				stops.add(tourFrom);
			}
			for (String stop : page.getTour())
			{
				net.runelite.api.coords.LocalPoint at = tutorialScene.focus(stop);
				if (at != null)
				{
					stops.add(at);
				}
			}
			if (!stops.isEmpty())
			{
				tutorialCamera.follow(com.galesong.baautopilot.tutorial.TutorialPath.along(stops, tutorialSteps.tourProgress()));
			}
			return;
		}
		String key = page.getCamera();
		net.runelite.api.coords.LocalPoint focus = key == null ? null : tutorialScene.focus(key);
		if (key == null || focus != null)
		{
			// A stop that can't be placed (room not found) keeps the camera where it is
			tutorialCamera.update(focus);
		}
	}

	/** Walking distances from you over the scene's collision map, for the planner's cost ranking. */
	private WalkCost walkCost(Snapshot fresh)
	{
		Player me = client.getLocalPlayer();
		int[][] flags = routeFinder.navigationFlags(fresh, false);
		if (me == null || me.getLocalLocation() == null || flags == null)
		{
			return null;
		}
		LocalPoint start = me.getLocalLocation();
		int[][] dist = ArenaPath.distances(flags, start.getSceneX(), start.getSceneY(), WALK_COST_STEPS);
		return tile ->
		{
			LocalPoint lp = Scenes.toLocal(client, tile);
			return lp == null ? -1 : ArenaPath.stepsTo(dist, lp.getSceneX(), lp.getSceneY());
		};
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (BaAutopilotConfig.GROUP.equals(e.getGroup()))
		{
			pushOptions();
			clientThread.invoke(this::updateOverlayState);
		}
	}

	/** The panel's play options follow the config (changed there or in the settings window). */
	private void pushOptions()
	{
		BaAutopilotPanel p = panel;
		if (p == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> p.setOptions(config));
	}

	private void pushToPanel()
	{
		BaAutopilotPanel p = panel;
		if (p == null)
		{
			return;
		}
		Snapshot s = snapshot;
		SwingUtilities.invokeLater(() -> p.updateProgress(s));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		if (!arenaRunning || snapshot == null || instruction == null || !snapshot.isInArena())
		{
			return;
		}
		MenuEntry entry = e.getMenuEntry();
		if (config.highlightHorn() && (instruction.getStep() == Step.CALL || instruction.getAlsoCall() != null) && snapshot.getShoutCall() != null
			&& Text.removeTags(entry.getOption()).equalsIgnoreCase(snapshot.getShoutCall().getHornOption()))
		{
			entry.setOption(ColorUtil.wrapWithColorTag(Text.removeTags(entry.getOption()), HORN_HIGHLIGHT));
		}
		if (config.colourEggs())
		{
			colourEggName(entry, isWrongEgg(entry));
		}
	}

	/**
	 * The current call's Tell- option becomes the horn's left-click (moved to the top of the menu,
	 * as Menu Entry Swapper does). Re-telling a call already made is harmless, so this holds for
	 * the whole call; not once calls stop mattering (wave 10 cleared, the Queen).
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort e)
	{
		if (!arenaRunning || snapshot == null || !snapshot.isInArena() || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		// Off-role "Attack" on penance only ever costs points: gone for collector, defender and
		// healer (as the BA Minigame plugin does). Healers keep "Use <food> -> Penance Healer"
		if (config.hideOffRoleAttack() && snapshot.getRole() != null && snapshot.getRole() != Role.ATTACKER)
		{
			MenuEntry[] kept = java.util.Arrays.stream(entries)
				.filter(m -> !("Attack".equals(Text.removeTags(m.getOption()))
					&& (Text.removeTags(m.getTarget()).contains("Penance") || Text.removeTags(m.getTarget()).contains("Queen spawn"))))
				.toArray(MenuEntry[]::new);
			if (kept.length != entries.length)
			{
				entries = kept;
				client.getMenu().setMenuEntries(entries);
			}
		}
		entries = promoteStepOption(entries);
		if (!config.hornLeftClick() || snapshot.getShoutCall() == null || !snapshot.isShoutSettled() || Planner.callsOver(snapshot))
		{
			return;
		}
		String want = snapshot.getShoutCall().getHornOption();
		for (int i = 0; i < entries.length - 1; i++)
		{
			if (Text.removeTags(entries[i].getOption()).equalsIgnoreCase(want))
			{
				MenuEntry tell = entries[i];
				// A low-priority item op can't be the left-click even at the top (BA Minigame does the same)
				if (tell.getType() == MenuAction.CC_OP_LOW_PRIORITY)
				{
					tell.setType(MenuAction.CC_OP);
				}
				System.arraycopy(entries, i + 1, entries, i, entries.length - 1 - i);
				entries[entries.length - 1] = tell;
				client.getMenu().setMenuEntries(entries);
				return;
			}
		}
	}

	/** Penance within a tile of the item to pick up (not the step's own NPC target). */
	private java.util.Set<Integer> blockers()
	{
		if (!config.hideBlockers() || !snapshot.isInArena() || instruction.getTarget().getKind() != Target.Kind.GROUND_ITEM
			|| instruction.getTarget().getTile() == null)
		{
			return java.util.Collections.emptySet();
		}
		WorldPoint item = instruction.getTarget().getTile();
		java.util.Set<Integer> out = new java.util.HashSet<>();
		for (com.galesong.baautopilot.state.PenanceView p : snapshot.getPenance())
		{
			if (p.getTile() != null && com.galesong.baautopilot.model.ArenaTiles.distance(p.getTile(), item) <= HIDE_RADIUS)
			{
				out.add(p.getNpcIndex());
			}
		}
		return out;
	}

	/** Tiles around the item whose penance are hidden. */
	private static final int HIDE_RADIUS = 1;

	private boolean shouldDraw(net.runelite.api.Renderable renderable, boolean drawingUI)
	{
		if (tutorialOn && tutorialShown && (renderable instanceof NPC
			|| (renderable instanceof Player && renderable != client.getLocalPlayer())))
		{
			return false;
		}
		return !(renderable instanceof NPC) || !hiddenNpcs.contains(((NPC) renderable).getIndex());
	}

	/**
	 * Tutorial (proof of concept): on and in lobby room 1, everyone else is hidden and the props
	 * are placed around you; anywhere else it waits. Client thread.
	 */
	private void updateTutorial()
	{
		if (!tutorialOn && !tutorialShown) return;
		boolean wasShown = tutorialShown;
		Player me = client.getLocalPlayer();
		boolean ready = tutorialOn && client.getGameState() == GameState.LOGGED_IN
			&& me != null && !client.getTopLevelWorldView().isInstance()
			&& LobbyRooms.inRoomOne(me.getWorldLocation());
		if (!ready)
		{
			if (wasShown)
			{
				tutorialCamera.release();
				tutorialActions.stop();
				tourPage = null;
			}
			tutorialScene.clear();
		}
		else if (tutorialScene.isStale())
		{
			// Layout file saved: place the props again, same page
			tutorialActions.stop();
			tutorialScene.show();
		}
		else if (!tutorialScene.isShown() && tutorialScene.show())
		{
			tutorialSteps.reset();
		}
		tutorialShown = tutorialScene.isShown();
		updateOverlayState();
		String status = !tutorialOn ? ""
			: tutorialShown ? "Running: everyone else is hidden."
			: ready ? "Loading the scene..."
			: "Go to lobby room 1 to begin.";
		if (status.equals(tutorialStatus))
		{
			return;
		}
		tutorialStatus = status;
		BaAutopilotPanel p = panel;
		boolean on = tutorialOn;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setTutorial(on, status));
		}
	}

	/**
	 * The step needs exactly one option on its target (one bait kind, the vial): make it the
	 * left-click, so the machine is one click instead of right-click + pick.
	 */
	private MenuEntry[] promoteStepOption(MenuEntry[] entries)
	{
		Instruction ins = instruction;
		if (ins == null || ins.getLeftClick() == null
			|| (ins.getTarget().getKind() != Target.Kind.OBJECT && ins.getTarget().getKind() != Target.Kind.GROUND_ITEM))
		{
			return entries;
		}
		for (int i = 0; i < entries.length - 1; i++)
		{
			MenuEntry m = entries[i];
			if (m.getIdentifier() == ins.getTarget().getId()
				&& Text.removeTags(m.getOption()).equalsIgnoreCase(ins.getLeftClick()))
			{
				System.arraycopy(entries, i + 1, entries, i, entries.length - 1 - i);
				entries[entries.length - 1] = m;
				client.getMenu().setMenuEntries(entries);
				return entries;
			}
		}
		return entries;
	}

	/** "Take Red egg": the egg name in its own colour, or grey when it is the wrong colour now. */
	private static void colourEggName(MenuEntry entry, boolean wrong)
	{
		Color colour = Palette.egg(entry.getIdentifier());
		String target = Text.removeTags(entry.getTarget());
		if (colour == null || !target.toLowerCase().contains("egg"))
		{
			return;
		}
		entry.setTarget(ColorUtil.wrapWithColorTag(target, wrong ? Palette.MUTED : colour));
	}

	/** A Take on a red/green/blue egg that isn't the collector's freshly heard call. */
	private boolean isWrongEgg(MenuEntry entry)
	{
		BaCall call = snapshot.getListenCall();
		if (snapshot.getRole() != Role.COLLECTOR || call == null || !snapshot.isListenFresh()
			|| !"Take".equals(Text.removeTags(entry.getOption())))
		{
			return false;
		}
		int id = entry.getIdentifier();
		boolean egg = id == BaIds.EGG_RED || id == BaIds.EGG_GREEN || id == BaIds.EGG_BLUE;
		return egg && id != call.getItemId();
	}

	/** The planner settings from the config: the live box's, and the tutorial's stand-ins. */
	public Settings settings()
	{
		return Settings.builder()
			.attackMethod(config.attackMethod())
			.retreatBelow(config.retreatBelow() / 100.0)
			.healTeammateBelow(config.healTeammateBelow() / 100.0)
			.collectorLoadAt(config.collectorLoadAt())
			.healCodeSet(config.healCodeSet())
			.defenderGuide(config.defenderGuide())
			.safetyBait(config.safetyBait())
			.defenderPattern(config.defenderPattern())
			.defenderMultiKill(config.defenderMultiKill())
			.splitTargets(config.splitTargets())
			.skipCoveredCalls(config.skipCoveredCalls())
			.queenStack(config.queenStack())
			.queenEggBatch(config.queenEggBatch())
			.queenEggBuffer(config.queenEggBuffer())
			.queenSingleLoads(config.queenSingleLoads())
			.build();
	}

	private void updateHintArrow()
	{
		if (!config.hintArrow() || instruction == null || !snapshot.isInArena())
		{
			if (client.hasHintArrow())
			{
				client.clearHintArrow();
			}
			return;
		}
		Target t = instruction.getTarget();
		if (t.getKind() == Target.Kind.NPC)
		{
			NPC npc = tracker.getNpcs().get(t.getId());
			if (npc != null)
			{
				client.setHintArrow(npc);
				return;
			}
		}
		// Tile targets, and NPCs out of view (their last seen tile)
		if (t.getTile() != null)
		{
			WorldPoint world = Scenes.toWorld(client, t.getTile());
			if (world != null)
			{
				client.setHintArrow(world);
				return;
			}
		}
		client.clearHintArrow();
	}
}
