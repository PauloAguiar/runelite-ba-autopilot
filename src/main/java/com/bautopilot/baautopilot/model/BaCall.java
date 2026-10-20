package com.bautopilot.baautopilot.model;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * Every call in the minigame, keyed by the role that RECEIVES it (and so acts on it).
 * The horn option is what the partner clicks to give it.
 */
@Getter
@RequiredArgsConstructor
public enum BaCall
{
	// Collector -> Attacker: the style the penance fighters and rangers are weak to
	CONTROLLED(Role.ATTACKER, "Tell-controlled", ItemID.BARBASSAULT_BULLET_ARROW, "Controlled", "Bullet arrows", "Wind"),
	ACCURATE(Role.ATTACKER, "Tell-accurate", ItemID.BARBASSAULT_FIELD_ARROW, "Accurate", "Field arrows", "Water"),
	AGGRESSIVE(Role.ATTACKER, "Tell-aggressive", ItemID.BARBASSAULT_BLUNT_ARROW, "Aggressive", "Blunt arrows", "Earth"),
	DEFENSIVE(Role.ATTACKER, "Tell-defensive", ItemID.BARBASSAULT_BARBED_ARROW, "Defensive", "Barbed arrows", "Fire"),

	// Attacker -> Collector: the egg colour to pick up
	RED_EGG(Role.COLLECTOR, "Tell-red", ItemID.BARBASSAULT_EGG_02, "Red", "Red eggs", "red"),
	GREEN_EGG(Role.COLLECTOR, "Tell-green", ItemID.BARBASSAULT_EGG_01, "Green", "Green eggs", "green"),
	BLUE_EGG(Role.COLLECTOR, "Tell-blue", ItemID.BARBASSAULT_EGG_03, "Blue", "Blue eggs", "blue"),

	// Healer -> Defender: the bait runners will eat
	BAIT_TOFU(Role.DEFENDER, "Tell-tofu", ItemID.BARBASSAULT_RUNNER_FOOD2, "Tofu", "Tofu", "tofu"),
	BAIT_CRACKERS(Role.DEFENDER, "Tell-crackers", ItemID.BARBASSAULT_RUNNER_FOOD, "Crackers", "Crackers", "crackers"),
	BAIT_WORMS(Role.DEFENDER, "Tell-worms", ItemID.BARBASSAULT_RUNNER_FOOD3, "Worms", "Worms", "worms"),

	// Defender -> Healer: the poisoned food penance healers are weak to
	POISON_TOFU(Role.HEALER, "Tell-tofu", ItemID.BARBASSAULT_POISION_01, "Tofu", "Poisoned tofu", "tofu"),
	POISON_WORMS(Role.HEALER, "Tell-worms", ItemID.BARBASSAULT_POISION_02, "Worms", "Poisoned worms", "worms"),
	POISON_MEAT(Role.HEALER, "Tell-meat", ItemID.BARBASSAULT_POISION_03, "Meat", "Poisoned meat", "meat");

	private final Role receiver;
	private final String hornOption;
	private final int itemId;
	/** The word the HUD shows first. */
	private final String keyword;
	private final String itemName;
	/** Attacker spell element, or a short lowercase word for the rest. */
	private final String extra;

	/**
	 * Reads a HUD call text such as "Controlled/Bullet/Wind", "Red egg", "Pois. Worms" or "Poison meat".
	 * A dash or blank text means no call has been made yet.
	 */
	public static BaCall parse(Role receiver, String text)
	{
		if (receiver == null || text == null)
		{
			return null;
		}
		String t = text.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty() || t.startsWith("-"))
		{
			return null;
		}
		for (BaCall c : values())
		{
			if (c.receiver != receiver)
			{
				continue;
			}
			String k = c.keyword.toLowerCase(Locale.ROOT);
			if (t.startsWith(k) || t.contains(" " + k) || t.contains("." + k) || t.contains("/" + k))
			{
				return c;
			}
		}
		return null;
	}

	public static BaCall forItem(Role receiver, int itemId)
	{
		for (BaCall c : values())
		{
			if (c.receiver == receiver && c.itemId == itemId)
			{
				return c;
			}
		}
		return null;
	}

	public static boolean isItemOf(Role receiver, int itemId)
	{
		return forItem(receiver, itemId) != null;
	}

	/** What the receiver should do with it, for HUD text. */
	public String describe()
	{
		switch (receiver)
		{
			case ATTACKER:
				return keyword + " (" + itemName + " / " + extra + ")";
			default:
				return itemName;
		}
	}

	@Override
	public String toString()
	{
		return describe();
	}
}
