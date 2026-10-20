package com.bautopilot.baautopilot.state;

import com.bautopilot.baautopilot.model.BaCall;
import com.bautopilot.baautopilot.model.Role;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Works out the call you have been given from three sources and keeps the newest:
 * <ul>
 * <li>the horn's chat line, addressed to the RECEIVER: "Healer: Poison worms!" is the defender's
 * call to the healer, "Defender: Drop Tofu!" the healer's call to the defender;</li>
 * <li>the Horn of Glory interface (only for players who own that reward);</li>
 * <li>the role HUD's "listen" text.</li>
 * </ul>
 * A call counts as fresh only if one of the sources changed (or the chat line arrived) during the
 * current call period, so last period's food or arrows are never presented as the current call.
 */
public class ListenResolver
{
	public enum Source
	{
		NONE,
		HUD,
		HORN,
		CHAT
	}

	@Value
	public static class Result
	{
		BaCall call;
		boolean fresh;
		Source source;
		int heardTick;
	}

	/** "Healer: Poison worms!" and friends; group 1 = receiving role, group 2 = the call. */
	private static final Pattern HORN_LINE = Pattern.compile("^(Attacker|Collector|Defender|Healer): (.+)!$");

	private BaCall hudCall;
	private String hudText;
	private int hudTick = -1;
	private BaCall hornCall;
	private String hornText;
	private int hornTick = -1;
	private BaCall chatCall;
	private int chatTick = -1;

	public void reset()
	{
		hudCall = null;
		hudText = null;
		hudTick = -1;
		hornCall = null;
		hornText = null;
		hornTick = -1;
		chatCall = null;
		chatTick = -1;
	}

	public void hud(Role role, String text, int tick)
	{
		if (text == null || text.equals(hudText))
		{
			return;
		}
		hudText = text;
		hudCall = BaCall.parse(role, text);
		hudTick = hudCall == null ? -1 : tick;
	}

	public void horn(Role role, String text, int tick)
	{
		if (text == null || text.equals(hornText))
		{
			return;
		}
		hornText = text;
		hornCall = BaCall.parse(role, text);
		hornTick = hornCall == null ? -1 : tick;
	}

	/** Records a horn chat line if it is addressed to {@code role}; returns true when it was. */
	public boolean chat(Role role, String message, int tick)
	{
		Addressed line = addressed(message);
		if (line == null || line.receiver != role || line.call == null)
		{
			return false;
		}
		chatCall = line.call;
		chatTick = tick;
		return true;
	}

	/**
	 * @param periodStartTick tick the current call period began, or -1 when the clock isn't running
	 */
	public Result resolve(int periodStartTick)
	{
		Source best = Source.NONE;
		BaCall call = null;
		int tick = -1;
		// Ties go to chat, then the horn: they are only ever produced by a real call
		if (hudTick > tick)
		{
			best = Source.HUD;
			call = hudCall;
			tick = hudTick;
		}
		if (hornTick >= tick && hornTick >= 0)
		{
			best = Source.HORN;
			call = hornCall;
			tick = hornTick;
		}
		if (chatTick >= tick && chatTick >= 0)
		{
			best = Source.CHAT;
			call = chatCall;
			tick = chatTick;
		}
		boolean fresh = call != null && (periodStartTick < 0 || tick >= periodStartTick);
		return new Result(call, fresh, best, tick);
	}

	/** A parsed horn chat line. */
	public static final class Addressed
	{
		public final Role receiver;
		public final BaCall call;

		Addressed(Role receiver, BaCall call)
		{
			this.receiver = receiver;
			this.call = call;
		}
	}

	public static Addressed addressed(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher m = HORN_LINE.matcher(message.trim());
		if (!m.matches())
		{
			return null;
		}
		Role receiver = Role.valueOf(m.group(1).toUpperCase());
		return new Addressed(receiver, BaCall.parse(receiver, m.group(2)));
	}
}
