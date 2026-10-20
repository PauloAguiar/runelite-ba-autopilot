package com.galesong.baautopilot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A healer "code": one line per call period, a dash-separated food count for healer #1, #2, ...
 * in spawn order. {@code 3(18)} = 3 food, not before 18 s into the wave; {@code 1[42]} = 1 food,
 * finish before 42 s; {@code 4/5} = alternatives (the first is used); {@code X} = spam until dead;
 * {@code 0} = leave alone this call. Lines that do not start with a count (Restock..., Expected...)
 * are notes.
 */
@Value
public class HealCode
{
	public static final int SPAM = -1;

	@Value
	public static class Feed
	{
		/** Food to give in this period, or {@link #SPAM}. */
		int count;
		/** Seconds into the wave before which this feed should wait, or -1. */
		int afterSeconds;
		/** Seconds into the wave by which this feed should be done, or -1. */
		int beforeSeconds;
	}

	String name;
	List<List<Feed>> periods;
	List<String> notes;

	private static final Pattern TOKEN = Pattern.compile(
		"^\\s*(X|\\d+)(?:/\\d+)*\\s*(?:\\((\\d+)\\))?\\s*(?:\\[(\\d+)\\])?\\s*(?:\\((\\d+)\\))?\\s*$",
		Pattern.CASE_INSENSITIVE);

	public static HealCode parse(String name, String text)
	{
		List<List<Feed>> periods = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		for (String raw : text.split("\n"))
		{
			String line = raw.replace("//", "").trim();
			if (line.isEmpty())
			{
				continue;
			}
			List<Feed> feeds = parseLine(line);
			if (feeds == null)
			{
				notes.add(line);
			}
			else
			{
				periods.add(Collections.unmodifiableList(feeds));
			}
		}
		return new HealCode(name, Collections.unmodifiableList(periods), Collections.unmodifiableList(notes));
	}

	private static List<Feed> parseLine(String line)
	{
		String[] tokens = line.split("-");
		List<Feed> feeds = new ArrayList<>(tokens.length);
		for (String token : tokens)
		{
			Matcher m = TOKEN.matcher(token);
			if (!m.matches())
			{
				return null;
			}
			int count = m.group(1).equalsIgnoreCase("X") ? SPAM : Integer.parseInt(m.group(1));
			String after = m.group(2) != null ? m.group(2) : m.group(4);
			feeds.add(new Feed(count,
				after != null ? Integer.parseInt(after) : -1,
				m.group(3) != null ? Integer.parseInt(m.group(3)) : -1));
		}
		return feeds;
	}

	/** Feed for healer #order (1-based) in call period (0-based); null when the code is silent. */
	public Feed feed(int period, int order)
	{
		if (period < 0 || period >= periods.size())
		{
			return null;
		}
		List<Feed> line = periods.get(period);
		return order >= 1 && order <= line.size() ? line.get(order - 1) : null;
	}

	/** The code for this wave in the chosen set; Regular where the set has no variant; null when off. */
	public static HealCode of(HealCodeSet set, int wave)
	{
		if (set == null || set == HealCodeSet.OFF)
		{
			return null;
		}
		String text = null;
		switch (set)
		{
			case ONE_OS:
				text = wave == 4 ? "2-5-4 //\n0-0-0-7"
					: wave == 5 ? "2-5-2(21)-3(30) //\n0-0-0-0-7"
					: wave == 6 ? "3(18)-5-2(21)-2 //\nRestock 3x + 1x reg\n0-0-0-1-9-11"
					: wave == 8 ? "1-9-1-1 //\n2-0-1-1(36)-2-1\nRestock 13/13 or 1-2x os //\n0-0-3-10"
					: wave == 9 ? "2-8(21)-1-1 //\n1-0-1(33)-1(45)-2(45)-1-1 //\nRestock 4x os\n0-0-3-5-X"
					: null;
				break;
			case TWO_OS:
				text = wave == 6 ? "3(18)-5-3-4 //\nRestock 3x os + 1x reg\n0-0-10-12"
					: wave == 7 ? "2(27)-6-6-1 //\nRestock 4x os\n0-0-0-1(39)-2-9-11"
					: wave == 8 ? "4(24)-9-1-1 //\n0-0-1-2(39)-1-1\nRestock 4x os + 1x reg\n0-0-3(51)-5(51)[57]-X"
					: wave == 10 ? "3(25)-5(27)-6-1 //\n0-0-0-2\nRestock 5x os\n0-8-8-10"
					: null;
				break;
			default:
				break;
		}
		return text == null ? regular(wave) : parse("W" + wave + " " + set.getLabel(), text);
	}

	/**
	 * The "Regular" code for each wave. Waves 4-10 are the community regular codes (as shipped by
	 * the BA Heal Codes and BA Healer Order plugins); waves 1-3 follow the wiki advice: poison every
	 * healer once, then spend extra food to speed up kills / pull the reserve.
	 */
	public static HealCode regular(int wave)
	{
		switch (wave)
		{
			case 1:
				return parse("W1", "2-2");
			case 2:
				return parse("W2", "2-1-1 //\nX-X-X");
			case 3:
				return parse("W3", "3-1 //\n0-0-X");
			case 4:
				return parse("W4 Reg", "2-5-1(24) //\n0-0-1-7");
			case 5:
				return parse("W5 Reg", "1-3(18)-2-2(27) //\n0-0-0-0-8");
			case 6:
				return parse("W6 Reg", "2/3(18)-4(24)-1-1 //\nRestock 3x os + 1x reg\n0-0-1-1-X-X");
			case 7:
				return parse("W7 Reg", "2(18)-4/5(27)-1-1\n1(33)-1/0-1(33)-1-4/5(48)-1\n0-0-0-0-1/0-5-X");
			case 8:
				return parse("W8 Reg", "2-4(21)-1/2(27)-1 //\n1-1[42]-1/0-1-3(42)-1/2 //\nRestock\n0-0-0-0-6/5-X");
			case 9:
				return parse("W9 Reg", "2(18)-4/5(18)-1-1 //\n1[54]-2/3-1(33)-1(42)-1-1-1 //\nRestock\n0-0-0-0-1[69]-2[69]-5[75]-X");
			case 10:
				return parse("W10 Reg", "2(21)-4/5(27)-1-1\n1(33)-1/0-1(33)-1-3(51)-2/1\n0-0-0-0-1-3-7");
			default:
				return null;
		}
	}
}
