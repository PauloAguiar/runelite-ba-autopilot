package com.bautopilot.baautopilot.tutorial;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * The tutorial's pages, walked with Back / Next: the text in the box, which props are in the
 * room, what is marked, who says what, what plays out, and where the camera looks.
 */
@Singleton
public class TutorialSteps
{
	public enum Style
	{
		/** The model's outline. */
		OUTLINE,
		/** A ring on the floor under it, like the game's team markers. */
		CIRCLE,
		/** The plugin's step target on a ground item: its tile filled, the label as the plugin's badge. */
		TARGET,
		/** The plugin's dot on a called egg, in the mark's colour; no label. */
		DOT,
		/**
		 * The plugin's step target on a Penance or teammate: a thick outline in the mark's colour and
		 * the label as the plugin's badge, with the page step's item icon.
		 */
		NPC_TARGET,
		/** A thin outline with no label, like the plugin's poison and runner outlines. */
		THIN
	}

	/** A looping little scene played on the page (see TutorialActions). */
	public enum Action
	{
		NONE,
		/** The attacker shoots arrows at the fighter. */
		SHOOT_FIGHTER,
		/** The attacker shoots arrows at the ranger. */
		SHOOT_RANGER,
		/** The runner eats the bait on the trap, the trap springs and kills it. */
		TRAP_RUNNER,
		/** The healer feeds the penance healer poisoned food; poison hits it. */
		POISON_HEALER,
		/** The penance healer heals the runner. */
		HEAL_RUNNER,
		/** The collector loads the hopper, the gunner fires the cannon, the egg hits the Queen. */
		CANNON_QUEEN,
		/** The same in an ordinary wave: an egg fired at a runner. */
		CANNON_PENANCE,
		/** The collector picks up the called egg. */
		COLLECT_EGGS,
		/** The healer fills the vial at the spring, heals a teammate, drinks from the spring. */
		SPRING,
		/** C-H-A-D-C: the Queen's egg worked and passed down the team. */
		EGG_CHAIN,
		/** The leader holds out the scroll to you: an invite. */
		INVITE,
		/** C-H-A-D-C round the loop: each player works the egg and passes it to the next. */
		EGG_PASS,
		/** The players blow their horns in turn. */
		HORNS,
		/** The Queen rages: her attacks, one after another. */
		QUEEN_RAGE,
		/** Each of the page's users reaches into what it uses, on a loop. */
		USE,
		/** The broken trap repaired, over and over. */
		TRAP_REPAIR,
		/** The attacker kills a ranger, which drops an egg on its tile; it comes back for the next loop. */
		EGG_DROP
	}

	/** Mark a prop (layout key) and label it. */
	@Value
	public static class Mark
	{
		String key;
		String label;
		Color color;
		Style style;
	}

	/** Outline a real object in the lobby (the nearest to room 1 with this id) and label it. */
	@Value
	public static class ObjectMark
	{
		int objectId;
		String label;
		Color color;
	}

	/** An arrow on the floor from one prop to another (who passes to whom). */
	@Value
	public static class Arrow
	{
		String from;
		String to;
		Color color;
	}

	/** A stand-in for the game's role panel: the call to make this round and the one heard. */
	@Value
	public static class Hud
	{
		com.bautopilot.baautopilot.model.Role role;
		/** What you call to your partner this round. */
		String call;
		/** What your partner called to you. */
		String heard;
	}

	/**
	 * BA Autopilot's own box for a moment in the arena: the plugin's planner turns the situation into
	 * its instruction and its box draws it, so the page always shows what the plugin really says.
	 * {@code expect} is the step the page is about (TutorialPilotTest holds every page to it).
	 */
	@Value
	public static class Pilot
	{
		com.bautopilot.baautopilot.plan.Step expect;
		com.bautopilot.baautopilot.state.Snapshot situation;
	}

	/**
	 * A right-click menu open on an inventory item, as the game draws it: the options top to bottom
	 * (the first is the left-click), the one to pick flashing.
	 */
	@Value
	public static class Menu
	{
		int itemId;
		List<String> options;
		/** Index into options of the one the page is about. */
		int pick;
	}

	/** Highlight a ground tile, by its key in tutorial-layout.json. */
	@Value
	public static class TileMark
	{
		String key;
		String label;
		Color color;
	}

	@Value
	@Builder(toBuilder = true)
	public static class Page
	{
		String title;
		/** The page's text; a blank line ("\n\n") starts a new paragraph. */
		String body;
		/** Props in the room on this page (layout keys); everything else is hidden. */
		@Singular
		List<String> props;
		@Singular
		List<Mark> marks;
		@Singular
		List<TileMark> tiles;
		/** Real lobby objects to outline (the scroll table). */
		@Singular
		List<ObjectMark> objects;
		@Singular
		List<Arrow> arrows;
		/** Prop or layout tile key the camera pans to; null: the camera stays on you. */
		String camera;
		/** Keys the camera glides through in one continuous move (tourMs), ending on the last; overrides camera. */
		@Singular("stop")
		List<String> tour;
		/** Overhead text, like the game's: prop key to what it says. */
		@Singular("says")
		Map<String, String> speech;
		@Builder.Default
		Action action = Action.NONE;
		/** EGG_CHAIN on one C-H-A-D-C step only (0-4); null plays the whole chain. */
		Integer chainStep;
		/** Once its camera tour has played, go on to the next page by itself (the first time only). */
		boolean autoNext;
		/** How long the tour takes. */
		@Builder.Default
		long tourMs = TOUR_MS;
		/** A role panel drawn under the box; null for none. */
		Hud hud;
		/** Who uses what, on a loop (a player at a dispenser): from = the player, to = the thing. */
		@Singular
		List<Arrow> uses;
		/** An arena map drawn over the screen: "1-9" or "10"; null for none. */
		String map;
		/** The autopilot box's step on this page (the plugin's own box shows it); null for none. */
		Pilot pilot;
		/** The page talks about the autopilot box: it pulses a gold outline. */
		boolean highlightBox;
		/**
		 * Your inventory on this page, drawn over the real one: the box's situation's items (outlined
		 * as the plugin would), or these for pages without a box. Item id to count.
		 */
		@Singular("item")
		Map<Integer, Integer> items;
		/** A right-click menu open on one of the inventory's items; null for none. */
		Menu menu;
		/** The red call tint over the game view, as the plugin shows it while a call is owed. */
		boolean tint;
	}

	/** How long the camera takes to glide through a whole tour. */
	public static final long TOUR_MS = 6000;
	/** A finished tour holds on its last stop this long before an auto-next page moves on. */
	private static final long TOUR_HOLD_MS = 700;

	private final TutorialLibrary library;

	@Inject
	public TutorialSteps(TutorialLibrary library)
	{
		this.library = library;
	}

	/** Tour pages already played through in this run of the tutorial. */
	private final java.util.Set<Page> toured = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** The tutorial picked in the panel. */
	private volatile Tutorial tutorial = Tutorial.INTRODUCTION;
	/** Changed by Back / Next on the input thread, read while drawing. */
	private volatile int index;
	/** When the current page opened, for camera tours and scenes. */
	private volatile long pageSince = System.currentTimeMillis();

	public Tutorial tutorial()
	{
		return tutorial;
	}

	/** Switches tutorial, from its first page. */
	public void select(Tutorial tutorial)
	{
		this.tutorial = tutorial;
		toured.clear();
		go(0);
	}

	public List<Page> pages()
	{
		return library.pages(tutorial);
	}

	public Page current()
	{
		List<Page> pages = pages();
		return pages.get(Math.min(index, pages.size() - 1));
	}

	public int index()
	{
		return index;
	}

	/** Milliseconds since the current page opened. */
	public long elapsed()
	{
		return System.currentTimeMillis() - pageSince;
	}

	/**
	 * Client tick: a tour seen through to the end counts as seen (coming back to its page shows
	 * the end, without the glide); the first time, an auto-next page then moves on by itself.
	 */
	public void tick()
	{
		Page page = current();
		if (page.getTour().isEmpty() || toured.contains(page) || elapsed() < page.getTourMs() + TOUR_HOLD_MS)
		{
			return;
		}
		toured.add(page);
		if (page.isAutoNext() && hasNext())
		{
			next();
		}
	}

	/** How far along its tour the camera is, 0 to 1: easing in at the start and out at the end. */
	public double tourProgress()
	{
		if (toured.contains(current()))
		{
			return 1;
		}
		double t = Math.min(1, elapsed() / (double) current().getTourMs());
		return t * t * (3 - 2 * t);
	}

	/** Another page, or another tutorial before this one's first page. */
	public boolean hasBack()
	{
		return index > 0 || tutorial.previous() != null;
	}

	/** "< Back", or the previous tutorial's name on the first page ("< Introduction"). */
	public String backLabel()
	{
		Tutorial before = tutorial.previous();
		return index == 0 && before != null ? "< " + before.getLabel() : "< Back";
	}

	/** Another page, or another tutorial after this one's last page. */
	public boolean hasNext()
	{
		return index < pages().size() - 1 || tutorial.next() != null;
	}

	/** The very last page of the last tutorial: Next becomes Finish. */
	public boolean isLast()
	{
		return index >= pages().size() - 1 && tutorial.next() == null;
	}

	/** "Next >", the next tutorial's name on the last page ("Basics >"), or "Finish" at the very end. */
	public String nextLabel()
	{
		if (isLast())
		{
			return "Finish";
		}
		Tutorial after = tutorial.next();
		return index >= pages().size() - 1 && after != null ? after.getLabel() + " >" : "Next >";
	}

	/** The page before; on a tutorial's first page, the previous tutorial's last page. */
	public void back()
	{
		Tutorial before = tutorial.previous();
		if (index == 0 && before != null)
		{
			select(before);
			go(library.pages(before).size() - 1);
			return;
		}
		go(Math.max(0, index - 1));
	}

	public void next()
	{
		if (index >= pages().size() - 1 && tutorial.next() != null)
		{
			select(tutorial.next());
			return;
		}
		go(Math.min(pages().size() - 1, index + 1));
	}

	public void reset()
	{
		toured.clear();
		go(0);
	}

	/** Opens a page of the current tutorial (a page dot's click). */
	public void open(int page)
	{
		go(Math.max(0, Math.min(page, pages().size() - 1)));
	}

	private void go(int page)
	{
		index = page;
		pageSince = System.currentTimeMillis();
	}
}
