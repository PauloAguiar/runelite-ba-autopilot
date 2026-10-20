package com.bautopilot.baautopilot.overlay;

import com.bautopilot.baautopilot.BaAutopilotConfig;
import com.bautopilot.baautopilot.BaAutopilotPlugin;
import com.bautopilot.baautopilot.plan.Instruction;
import com.bautopilot.baautopilot.plan.Step;
import com.bautopilot.baautopilot.state.Snapshot;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class InstructionOverlayActivityTest
{
	private static InstructionOverlay overlay()
	{
		return new InstructionOverlay(new BaAutopilotPlugin(), new BaAutopilotConfig() { }, null, null, null, null);
	}

	@Test public void lobbyGuidanceReturnsBeforeAnyGraphicsOrSpriteWork()
	{
		assertNull(overlay().render(null, Instruction.builder().step(Step.LOBBY).headline("Join a team").build(),
			Snapshot.builder().inLobby(true).build(), true));
	}

	@Test public void anArenaSnapshotCannotBeAssumedForAnEnabledTutorialPreview()
	{
		BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			assertNotNull(overlay().render(graphics, Instruction.builder().step(Step.LOBBY).headline("Tutorial example").build(),
				Snapshot.builder().inLobby(true).build(), false));
		}
		finally { graphics.dispose(); }
	}
}
