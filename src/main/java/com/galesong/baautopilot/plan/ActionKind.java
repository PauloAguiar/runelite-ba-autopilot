package com.galesong.baautopilot.plan;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** What sort of thing an instruction asks for; drives its colour everywhere it is drawn. */
@Getter
@RequiredArgsConstructor
public enum ActionKind
{
	CALL(new Color(255, 196, 0)),
	GET(new Color(90, 170, 255)),
	USE(new Color(80, 225, 120)),
	FIGHT(new Color(255, 125, 55)),
	MOVE(new Color(70, 215, 230)),
	WAIT(new Color(175, 175, 175)),
	DANGER(new Color(255, 70, 70));

	private final Color color;
}
