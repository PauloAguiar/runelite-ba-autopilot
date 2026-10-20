package com.bautopilot.baautopilot;

import com.bautopilot.baautopilot.model.DefenderGuide;
import com.bautopilot.baautopilot.model.HealCodeSet;
import com.bautopilot.baautopilot.plan.AttackMethod;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BaAutopilotConfig.GROUP)
public interface BaAutopilotConfig extends Config
{
	String GROUP = "baautopilot";

	@ConfigSection(name = "Play", description = "How you play your role", position = 0)
	String strategySection = "strategy";

	@ConfigSection(name = "Display", description = "What the plugin draws", position = 1)
	String displaySection = "display";

	@ConfigItem(keyName = "queenStack", name = "Queen: stack at the cannon", position = 0, section = strategySection,
		description = "On: once your eggs are done, stand at the cannon and spam Fire while the collector loads omegas one by one."
			+ " Off: other roles prepare eggs and wait by the healer spring. The Collector's single-loading option is separate")
	default boolean queenStack()
	{
		return false;
	}

	@Range(min = 1, max = 8)
	@ConfigItem(keyName = "queenEggBatch", name = "Collector: Queen yellow batch", position = 0, section = strategySection,
		description = "Collect this many yellow eggs before passing the batch to the same healer. Pass sooner if inventory is full")
	default int queenEggBatch()
	{
		return 5;
	}

	@Range(min = 0, max = 8)
	@ConfigItem(keyName = "queenEggBuffer", name = "Collector: Queen reserve eggs", position = 0, section = strategySection,
		description = "Extra yellows to keep after the first batch. Pass them only after that batch's omegas are fired and the Queen survives")
	default int queenEggBuffer()
	{
		return 3;
	}

	@ConfigItem(keyName = "queenSingleLoads", name = "Collector: one omega at a time", position = 0, section = strategySection,
		description = "Stand two tiles south of the hopper, use one omega on it, then wait for the team to fire before loading another")
	default boolean queenSingleLoads()
	{
		return true;
	}

	@ConfigItem(keyName = "attackMethod", name = "Attacker weapon", position = 1, section = strategySection,
		description = "Ranged checks your equipped arrows; melee and magic name the style or spell to use")
	default AttackMethod attackMethod()
	{
		return AttackMethod.RANGED;
	}

	@ConfigItem(keyName = "healCodeSet", name = "Healer: timing guide", position = 2, section = strategySection,
		description = "Optional guide for equally urgent targets. Always feed unpoisoned healers first, then lowest poison below 4. Guide times never delay a needed feed")
	default HealCodeSet healCodeSet()
	{
		return HealCodeSet.REGULAR;
	}

	@ConfigItem(keyName = "defenderGuide", name = "Defender: guide", position = 3, section = strategySection,
		description = "Choose the Wiki or Gau Cho bait pattern and log counts. Safety-first variants start with bad bait south of the trap; no-safety variants omit it")
	default DefenderGuide defenderGuide()
	{
		return DefenderGuide.WIKI;
	}

	@ConfigItem(keyName = "defenderPattern", name = "Defender: food pattern", position = 3, section = strategySection,
		description = "Drop the guide's bait pattern during the first call, with any team")
	default boolean defenderPattern()
	{
		return true;
	}

	@ConfigItem(keyName = "defenderMultiKill", name = "Defender: advanced multikills", position = 3, section = strategySection,
		description = "Hold runners on the food and time stack releases. Off: keep food stocked and the trap repaired, then stand clear")
	default boolean defenderMultiKill()
	{
		return false;
	}

	@Range(min = 5, max = 80)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "retreatBelow", name = "Retreat below HP", position = 4, section = strategySection,
		description = "Non-healers are sent to a launcher hill below this share of max HP")
	default int retreatBelow()
	{
		return 30;
	}

	@Range(min = 10, max = 95)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "healTeammateBelow", name = "Healer: heal teammates below", position = 5, section = strategySection,
		description = "Teammates under half of this interrupt poisoning; under this, they're healed when idle")
	default int healTeammateBelow()
	{
		return 50;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "collectorLoadAt", name = "Collector: blue-call batch", position = 6, section = strategySection,
		description = "Red/green eggs held in inventory plus bag before a blue-call load, with at least three that fit. Reds are gathered; green calls load at three free slots or fewer. Cannon interrupts require escaping runners, or low reds with recent firing")
	default int collectorLoadAt()
	{
		return 5;
	}

	@ConfigItem(keyName = "skipCoveredCalls", name = "Skip calls a teammate made", position = 9, section = strategySection,
		description = "With two players in your role, don't ask you to call when the other one already made the right call")
	default boolean skipCoveredCalls()
	{
		return true;
	}

	@ConfigItem(keyName = "splitTargets", name = "Attackers: split targets", position = 9, section = strategySection,
		description = "When the team list shows two attackers and both run BA Autopilot, each gets its own share of"
			+ " fighters and rangers (same split on both clients, no connection needed)")
	default boolean splitTargets()
	{
		return true;
	}

	@ConfigItem(keyName = "colourEggs", name = "Colour egg names and spots", position = 8, section = strategySection,
		description = "Egg names in the right-click menu in their own colour (wrong colours greyed), and a dot on"
			+ " every egg of the called colour: green when a left-click takes it, yellow when you need to right-click"
			+ " the pile and choose Take")
	default boolean colourEggs()
	{
		return true;
	}

	@ConfigItem(keyName = "debugPriorities", name = "Show priorities (debug)", position = 0, section = displaySection,
		description = "Show planner priorities, runner state estimates, Healer HP/poison forecasts and the fixed 8x8 runner search zones")
	default boolean debugPriorities()
	{
		return false;
	}

	@ConfigItem(keyName = "compactHud", name = "Compact instruction box", position = 0, section = displaySection,
		description = "Keep the main instruction and calls; hide timers and stats lines")
	default boolean compactHud()
	{
		return false;
	}

	@ConfigItem(keyName = "inventoryTint", name = "Call reminder tint", position = 1, section = displaySection,
		description = "Tint the game view red only while the primary instruction is a horn call you owe."
			+ " The inventory itself isn't tinted")
	default TintMode inventoryTint()
	{
		return TintMode.FULL;
	}

	@Range(min = 5, max = 60)
	@Units(Units.PERCENT)
	@ConfigItem(keyName = "tintStrength", name = "Tint strength", position = 1, section = displaySection,
		description = "How strong the red tint is")
	default int tintStrength()
	{
		return 18;
	}

	@ConfigItem(keyName = "showPath", name = "Show walking route", position = 1, section = displaySection,
		description = "Draw the walking route to the current target, in the action's colour")
	default boolean showPath()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightTarget", name = "Highlight target", position = 1, section = displaySection,
		description = "Outline the NPC, object, tile or item the instruction points at")
	default boolean highlightTarget()
	{
		return true;
	}

	@ConfigItem(keyName = "hintArrow", name = "Hint arrow", position = 2, section = displaySection,
		description = "Point the game's hint arrow at the target")
	default boolean hintArrow()
	{
		return false;
	}

	@ConfigItem(keyName = "showMarks", name = "Show plan tiles", position = 3, section = displaySection,
		description = "Defender food pattern spots with their progress")
	default boolean showMarks()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightItems", name = "Highlight inventory items", position = 4, section = displaySection,
		description = "Keep current-call items highlighted alongside action items; label all Defender bait GOOD or BAD")
	default boolean highlightItems()
	{
		return true;
	}

	@ConfigItem(keyName = "showLureRange", name = "Defender: show bait range", position = 4, section = displaySection,
		description = "Outline each bait on the ground with the tiles where a runner notices it (4 at defender level 1, 5 from level 2): green good, purple bad")
	default boolean showLureRange()
	{
		return true;
	}

	@ConfigItem(keyName = "poisonOutline", name = "Healer: poison level outline", position = 4, section = displaySection,
		description = "Outline each penance healer by its poison's next hit: green 4, light green 3, yellow 2, orange 1, red none")
	default boolean poisonOutline()
	{
		return true;
	}

	@ConfigItem(keyName = "runnerOutline", name = "Defender: runner state outline", position = 4, section = displaySection,
		description = "Outline each runner by how well your food holds it: green stacked on the trap, light green lured to good bait, yellow bad bait only, orange loose, red escaping south")
	default boolean runnerOutline()
	{
		return true;
	}

	@ConfigItem(keyName = "baitCounts", name = "Defender: bait count per tile", position = 4, section = displaySection,
		description = "Label each tile with bait on it with how many are good (green) and bad (red)")
	default boolean baitCounts()
	{
		return true;
	}

	@ConfigItem(keyName = "hideBlockers", name = "Hide penance over your pickup", position = 5, section = displaySection,
		description = "While you're told to pick something up (logs, hammer, eggs), penance standing on or next to it aren't drawn, so the click reaches the item")
	default boolean hideBlockers()
	{
		return true;
	}

	@ConfigItem(keyName = "hideOffRoleAttack", name = "No Attack on penance off-role", position = 5, section = displaySection,
		description = "Remove 'Attack' on penance for collector, defender and healer (it only costs points)")
	default boolean hideOffRoleAttack()
	{
		return true;
	}

	@ConfigItem(keyName = "hornLeftClick", name = "Horn left-clicks the call", position = 5, section = displaySection,
		description = "Make the right Tell- option the horn's left-click, so one click makes the call")
	default boolean hornLeftClick()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightHorn", name = "Highlight correct horn option", position = 5, section = displaySection,
		description = "Colour the right Tell- option in the horn's menu while a call is owed")
	default boolean highlightHorn()
	{
		return true;
	}

	@ConfigItem(keyName = "liveCombat", name = "Live role numbers", position = 6, section = displaySection,
		description = "In the box: attacker damage per second and fighter/ranger HP; collector launcher eggs; defender trap charges, logs and bait per kind")
	default boolean liveCombat()
	{
		return true;
	}

	@ConfigItem(keyName = "waveInfo", name = "Wave info in the box", position = 6, section = displaySection,
		description = "Per penance type: how many are out, dead of the wave's total, and the reserves that only come after a kill (+N)")
	default boolean waveInfo()
	{
		return true;
	}

	@ConfigItem(keyName = "liveStats", name = "Live numbers in the box", position = 6, section = displaySection,
		description = "Your call speed and this wave's counters, graded green / amber / red")
	default boolean liveStats()
	{
		return true;
	}

	@ConfigItem(keyName = "colourByAction", name = "Colour by action", position = 6, section = displaySection,
		description = "Highlight targets in the action's colour (gold call, blue get, green use, orange fight)"
			+ " instead of the fixed target colour")
	default boolean colourByAction()
	{
		return true;
	}

	@ConfigItem(keyName = "healerTable", name = "Healer table", position = 6, section = displaySection,
		description = "As healer, list each penance healer's food and poison")
	default boolean healerTable()
	{
		return true;
	}

	@ConfigItem(keyName = "notifyCall", name = "Notify when a call is owed", position = 7, section = displaySection,
		description = "Desktop notification if the call isn't made within a few seconds")
	default boolean notifyCall()
	{
		return false;
	}

	@Alpha
	@ConfigItem(keyName = "highlightColor", name = "Target colour", position = 8, section = displaySection,
		description = "Colour for the current target")
	default Color highlightColor()
	{
		return new Color(0, 255, 180, 220);
	}

	@Alpha
	@ConfigItem(keyName = "urgentColor", name = "Urgent colour", position = 9, section = displaySection,
		description = "Colour for urgent instructions")
	default Color urgentColor()
	{
		return new Color(255, 80, 80, 230);
	}
}
