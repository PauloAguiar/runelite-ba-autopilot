package com.galesong.baautopilot.plan;

import lombok.Getter;

/**
 * Every instruction type, with its colour group, the one-word tag shown on screen, and whether it
 * starts with clicking something in your inventory (the screen tint cue).
 */
@Getter
public enum Step
{
	NOT_IN_GAME(ActionKind.WAIT, ""),
	LOBBY(ActionKind.WAIT, ""),
	WAIT_ROLE(ActionKind.WAIT, "WAIT"),
	CALL(ActionKind.CALL, "CALL", true),
	RETREAT(ActionKind.DANGER, "RETREAT"),

	ATT_WAIT_CALL(ActionKind.WAIT, "WAIT"),
	ATT_POSITION(ActionKind.MOVE, "MOVE"),
	ATT_GET_ARROWS(ActionKind.GET, "TAKE"),
	ATT_EQUIP(ActionKind.USE, "EQUIP", true),
	ATT_HOLD(ActionKind.WAIT, "HOLD"),
	ATT_ATTACK(ActionKind.FIGHT, "ATTACK"),
	ATT_SPIKES(ActionKind.GET, "SPIKES"),
	ATT_SUPPORT(ActionKind.WAIT, "WAIT"),
	ATT_FIRE(ActionKind.FIGHT, "FIRE"),

	COL_WAIT_CALL(ActionKind.WAIT, "WAIT"),
	COL_POSITION(ActionKind.MOVE, "MOVE"),
	COL_BLUE(ActionKind.USE, "DESTROY", true),
	COL_DISCARD(ActionKind.USE, "DESTROY", true),
	COL_COLLECT(ActionKind.GET, "PICK UP"),
	COL_LOAD(ActionKind.USE, "LOAD"),
	COL_FIRE(ActionKind.FIGHT, "FIRE"),
	COL_FOLLOW(ActionKind.WAIT, "FOLLOW"),
	COL_QUEEN_READY(ActionKind.WAIT, "READY"),

	DEF_WAIT_CALL(ActionKind.WAIT, "WAIT"),
	DEF_TAKE_BAIT(ActionKind.GET, "TAKE"),
	DEF_DROP(ActionKind.USE, "DROP", true),
	DEF_HAMMER(ActionKind.GET, "HAMMER"),
	DEF_LOGS(ActionKind.GET, "LOGS"),
	DEF_REPAIR(ActionKind.USE, "REPAIR"),
	DEF_REPAIR_POSITION(ActionKind.MOVE, "REPAIR SPOT"),
	DEF_SERVICE(ActionKind.MOVE, "STEP ASIDE"),
	DEF_WATCH(ActionKind.WAIT, "READY"),
	DEF_STAND(ActionKind.MOVE, "STAND"),
	DEF_STEP(ActionKind.MOVE, "STEP"),
	DEF_HOLD(ActionKind.WAIT, "HOLD"),
	DEF_RELURE(ActionKind.USE, "RELURE", true),
	DEF_SUPPORT(ActionKind.WAIT, "WAIT"),

	HEAL_WAIT_CALL(ActionKind.WAIT, "WAIT"),
	HEAL_TAKE_FOOD(ActionKind.GET, "TAKE"),
	HEAL_FEED(ActionKind.USE, "FEED", true),
	HEAL_WAIT(ActionKind.WAIT, "HOLD"),
	HEAL_TEAMMATE(ActionKind.USE, "HEAL", true),
	HEAL_FILL_VIAL(ActionKind.GET, "FILL"),
	HEAL_SUPPORT(ActionKind.WAIT, "WAIT"),

	QUEEN_COLLECT(ActionKind.GET, "PICK UP"),
	QUEEN_LOAD_POSITION(ActionKind.MOVE, "MOVE"),
	QUEEN_PASS(ActionKind.USE, "PASS", true),
	QUEEN_DUNK(ActionKind.USE, "DUNK", true),
	QUEEN_LAVA(ActionKind.USE, "DUNK"),
	QUEEN_SPIKE(ActionKind.USE, "SPIKE", true),
	QUEEN_LOAD(ActionKind.USE, "LOAD"),
	QUEEN_LOAD_ONE(ActionKind.USE, "LOAD 1", true),
	QUEEN_FIRE(ActionKind.FIGHT, "FIRE"),
	QUEEN_WAIT(ActionKind.WAIT, "WAIT");

	private final ActionKind kind;
	private final String tag;
	private final boolean inventory;

	Step(ActionKind kind, String tag)
	{
		this(kind, tag, false);
	}

	Step(ActionKind kind, String tag, boolean inventory)
	{
		this.kind = kind;
		this.tag = tag;
		this.inventory = inventory;
	}
}
