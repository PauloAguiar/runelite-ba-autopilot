package com.galesong.baautopilot.tutorial;

import com.galesong.baautopilot.overlay.Palette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/** The current tutorial page's marks in the world: outlined, labelled props and ground tiles. */
public class TutorialSceneOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 3;
	private static final int OUTLINE_FEATHER = 2;
	/** The plugin's step-target outline (SceneOverlay's OUTLINE_WIDTH). */
	private static final int TARGET_OUTLINE_WIDTH = 4;
	/** Labels float this far above a model's top. */
	private static final int LABEL_LIFT = 30;
	private static final int TILE_FILL_ALPHA = 50;
	private static final Color CALL_TINT = new Color(255, 40, 40, 46);
	/** Arrows stop this share of the way short of each end, clear of the players. */
	private static final double ARROW_GAP = 0.22;
	private static final int ARROW_HEAD = 14;
	/** Labels on real objects (a table) float this high. */
	private static final int OBJECT_LABEL_HEIGHT = 120;
	/** The game's overhead chat yellow. */
	private static final Color SPEECH = new Color(255, 255, 0);
	/** Overhead text floats this far above a label. */
	private static final int SPEECH_LIFT = 35;
	/** Ring under a team member: just inside its tile (128 local units across). */
	private static final int RING_RADIUS = 56;
	private static final int RING_POINTS = 32;

	private final Client client;
	private final ModelOutlineRenderer outlines;
	private final TutorialScene scene;
	private final TutorialSteps steps;
	private final TutorialActions actions;
	private final SpriteManager sprites;
	/** The page's step, for the item icon on a target's badge. */
	private final TutorialPilots pilots;
	private final ItemManager itemManager;
	/** Whether the tutorial scene is up (set by the plugin). */
	private BooleanSupplier shown = () -> false;

	@Inject
	TutorialSceneOverlay(Client client, ModelOutlineRenderer outlines, TutorialScene scene, TutorialSteps steps,
		TutorialActions actions, SpriteManager sprites, TutorialPilots pilots, ItemManager itemManager)
	{
		this.client = client;
		this.outlines = outlines;
		this.scene = scene;
		this.steps = steps;
		this.actions = actions;
		this.sprites = sprites;
		this.pilots = pilots;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void setShown(BooleanSupplier shown)
	{
		this.shown = shown;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!shown.getAsBoolean())
		{
			return null;
		}
		TutorialSteps.Page page = steps.current();
		if (page.isTint())
		{
			// The plugin's call reminder: the whole view tinted red at 18%
			g.setColor(CALL_TINT);
			g.fillRect(client.getViewportXOffset(), client.getViewportYOffset(),
				client.getViewportWidth(), client.getViewportHeight());
		}
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		for (TutorialSteps.TileMark t : page.getTiles())
		{
			drawTile(g, scene.tile(t.getKey()), t.getLabel(), t.getColor());
		}
		String speaker = actions.speaker();
		for (TutorialSteps.Arrow a : page.getArrows())
		{
			if (speaker == null || speaker.equals(a.getFrom()))
			{
				arrow(g, scene.prop(a.getFrom()), scene.prop(a.getTo()), a.getColor());
			}
		}
		for (TutorialSteps.ObjectMark m : page.getObjects())
		{
			TileObject o = scene.object(m.getObjectId());
			if (o != null)
			{
				outlines.drawOutline(o, OUTLINE_WIDTH, m.getColor(), OUTLINE_FEATHER);
				label(g, o.getLocalLocation(), m.getLabel(), m.getColor(), OBJECT_LABEL_HEIGHT);
			}
		}
		for (TutorialSteps.Mark m : page.getMarks())
		{
			for (TutorialScene.Prop p : scene.getProps())
			{
				if (p.getKey().equals(m.getKey()))
				{
					drawProp(g, p, m);
				}
			}
		}
		page.getSpeech().forEach((key, text) ->
		{
			if (speaker == null || speaker.equals(key))
			{
				speech(g, scene.prop(key), text);
			}
		});
		for (TutorialActions.Splat s : actions.splats())
		{
			splat(g, scene.prop(s.getKey()), s);
		}
		return null;
	}

	/** Overhead text the way the game shows a shout: yellow, over the head, above any label. */
	private void speech(Graphics2D g, TutorialScene.Prop p, String text)
	{
		if (p == null || !p.getObject().isActive())
		{
			return;
		}
		label(g, p.getObject().getLocation(), text, SPEECH, top(p) + LABEL_LIFT + SPEECH_LIFT);
	}

	/** A hitsplat at mid height: the game's sprite with the number on it. */
	private void splat(Graphics2D g, TutorialScene.Prop p, TutorialActions.Splat s)
	{
		BufferedImage image = s.getSprite() == TutorialActions.HEAL_SPLAT ? healSplat() : sprites.getSprite(s.getSprite(), 0);
		if (p == null || image == null || !p.getObject().isActive())
		{
			return;
		}
		Point at = Perspective.localToCanvas(client, p.getObject().getLocation(),
			client.getTopLevelWorldView().getPlane(), top(p) / 2);
		if (at == null)
		{
			return;
		}
		int x = at.getX() - image.getWidth() / 2;
		int y = at.getY() - image.getHeight() / 2;
		g.drawImage(image, x, y, null);
		g.setFont(FontManager.getRunescapeSmallFont());
		java.awt.FontMetrics fm = g.getFontMetrics();
		int tx = at.getX() - fm.stringWidth(s.getText()) / 2;
		int ty = at.getY() + (fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(s.getText(), tx + 1, ty + 1);
		g.setColor(Color.WHITE);
		g.drawString(s.getText(), tx, ty);
		g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
	}

	/**
	 * A heal splat: the game's own hitsplat sprite (the green poison one) recoloured pink, so it
	 * has the same shape, size and centre as the other splats.
	 */
	private BufferedImage healSplat()
	{
		if (healSplat == null)
		{
			BufferedImage base = sprites.getSprite(TutorialActions.POISON_SPLAT, 0);
			if (base == null)
			{
				return null;
			}
			BufferedImage image = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < base.getHeight(); y++)
			{
				for (int x = 0; x < base.getWidth(); x++)
				{
					int argb = base.getRGB(x, y);
					int alpha = argb >>> 24;
					// Keep each pixel's brightness, give it the heal pink
					int r = (argb >> 16) & 0xff;
					int gr = (argb >> 8) & 0xff;
					int b = argb & 0xff;
					float light = Math.max(r, Math.max(gr, b)) / 255f;
					int pr = Math.round(HEAL_PINK.getRed() * light);
					int pg = Math.round(HEAL_PINK.getGreen() * light);
					int pb = Math.round(HEAL_PINK.getBlue() * light);
					image.setRGB(x, y, (alpha << 24) | (pr << 16) | (pg << 8) | pb);
				}
			}
			healSplat = image;
		}
		return healSplat;
	}

	private BufferedImage healSplat;
	private static final Color HEAL_PINK = new Color(235, 100, 210);

	/**
	 * The model's height above the floor, as first drawn and then kept: the animated model's
	 * height changes every frame (a bow drawn, a death), which made labels and calls bob.
	 */
	private int top(TutorialScene.Prop p)
	{
		Integer height = heights.get(p.getObject());
		if (height == null)
		{
			Model model = p.getObject().getModel();
			if (model == null)
			{
				return 0;
			}
			height = model.getModelHeight();
			heights.put(p.getObject(), height);
		}
		return height;
	}

	/** First-drawn heights; the objects go away with the scene. */
	private final java.util.Map<net.runelite.api.RuneLiteObject, Integer> heights = new java.util.WeakHashMap<>();

	private void drawProp(Graphics2D g, TutorialScene.Prop p, TutorialSteps.Mark mark)
	{
		String label = mark.getLabel();
		Color color = mark.getColor();
		if (!p.getObject().isActive())
		{
			return;
		}
		if (mark.getStyle() == TutorialSteps.Style.TARGET || mark.getStyle() == TutorialSteps.Style.DOT)
		{
			// Drawn the way the plugin marks eggs in a wave (SceneOverlay): the target's tile and badge,
			// a dot on every called egg
			LocalPoint at = p.getObject().getLocation();
			int plane = client.getTopLevelWorldView().getPlane();
			if (mark.getStyle() == TutorialSteps.Style.TARGET)
			{
				java.awt.Polygon tile = Perspective.getCanvasTilePoly(client, at);
				if (tile != null)
				{
					OverlayUtil.renderPolygon(g, tile, color, Palette.fill(color, 70), new BasicStroke(3));
				}
				Point text = Perspective.getCanvasTextLocation(client, g, at, label, 60);
				com.galesong.baautopilot.overlay.SceneOverlay.badge(g, text, label, color, null);
			}
			else
			{
				Point dot = Perspective.localToCanvas(client, at, plane, 20);
				if (dot != null)
				{
					com.galesong.baautopilot.overlay.SceneOverlay.eggDot(g, dot, color);
				}
			}
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
			return;
		}
		if (mark.getStyle() == TutorialSteps.Style.THIN)
		{
			// As the plugin outlines Penance Healers by poison (and Runners by state)
			outlines.drawOutline(p.getObject(), 2, color, 2);
			return;
		}
		if (mark.getStyle() == TutorialSteps.Style.NPC_TARGET)
		{
			// As the plugin marks the step's NPC target: a thick outline and its badge with the item to use
			outlines.drawOutline(p.getObject(), TARGET_OUTLINE_WIDTH, color, OUTLINE_FEATHER);
			TutorialPilots.View view = pilots.current();
			int key = view == null ? -1 : com.galesong.baautopilot.overlay.InstructionOverlay.keyItem(view.getInstruction(), view.getSnapshot());
			Point text = Perspective.getCanvasTextLocation(client, g, p.getObject().getLocation(), label, top(p) + 40);
			com.galesong.baautopilot.overlay.SceneOverlay.badge(g, text, label, color, key > 0 ? itemManager.getImage(key) : null);
			g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
			return;
		}
		if (mark.getStyle() == TutorialSteps.Style.CIRCLE)
		{
			ring(g, p.getObject().getLocation(), color);
		}
		else
		{
			outlines.drawOutline(p.getObject(), OUTLINE_WIDTH, color, OUTLINE_FEATHER);
		}
		LocalPoint at = p.getObject().getLocation();
		int height = p.getLabelHeight() >= 0 ? p.getLabelHeight() : top(p) + LABEL_LIFT;
		label(g, at, label, color, height);
	}

	/** An arrow on the floor from one prop towards another, stopping short of both. */
	private void arrow(Graphics2D g, TutorialScene.Prop from, TutorialScene.Prop to, Color color)
	{
		if (from == null || to == null || from.getObject().getLocation() == null || to.getObject().getLocation() == null)
		{
			return;
		}
		LocalPoint a = from.getObject().getLocation();
		LocalPoint b = to.getObject().getLocation();
		int plane = client.getTopLevelWorldView().getPlane();
		Point start = Perspective.localToCanvas(client, TutorialPath.along(java.util.Arrays.asList(a, b), ARROW_GAP), plane);
		Point end = Perspective.localToCanvas(client, TutorialPath.along(java.util.Arrays.asList(a, b), 1 - ARROW_GAP), plane);
		if (start == null || end == null)
		{
			return;
		}
		double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
		int[] xs = {
			end.getX(),
			(int) Math.round(end.getX() - ARROW_HEAD * Math.cos(angle - 0.45)),
			(int) Math.round(end.getX() - ARROW_HEAD * Math.cos(angle + 0.45))};
		int[] ys = {
			end.getY(),
			(int) Math.round(end.getY() - ARROW_HEAD * Math.sin(angle - 0.45)),
			(int) Math.round(end.getY() - ARROW_HEAD * Math.sin(angle + 0.45))};
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.BLACK);
		g.drawLine(start.getX(), start.getY(), end.getX(), end.getY());
		g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(color);
		g.drawLine(start.getX(), start.getY(), end.getX(), end.getY());
		g.fillPolygon(xs, ys, 3);
	}

	/** A ring on the floor around a tile's centre, like the game's markers under teammates. */
	private void ring(Graphics2D g, LocalPoint at, Color color)
	{
		if (at == null)
		{
			return;
		}
		int plane = client.getTopLevelWorldView().getPlane();
		Polygon poly = new Polygon();
		for (int i = 0; i < RING_POINTS; i++)
		{
			double a = 2 * Math.PI * i / RING_POINTS;
			LocalPoint edge = new LocalPoint(at.getX() + (int) (RING_RADIUS * Math.cos(a)),
				at.getY() + (int) (RING_RADIUS * Math.sin(a)), at.getWorldView());
			Point p = Perspective.localToCanvas(client, edge, plane);
			if (p == null)
			{
				return;
			}
			poly.addPoint(p.getX(), p.getY());
		}
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), TILE_FILL_ALPHA));
		g.fill(poly);
		g.setColor(color);
		g.setStroke(new BasicStroke(2f));
		g.draw(poly);
	}

	private void drawTile(Graphics2D g, LocalPoint at, String label, Color color)
	{
		if (at == null)
		{
			return;
		}
		Polygon poly = Perspective.getCanvasTilePoly(client, at);
		if (poly != null)
		{
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), TILE_FILL_ALPHA));
			g.fill(poly);
			g.setColor(color);
			g.setStroke(new BasicStroke(2f));
			g.draw(poly);
		}
		label(g, at, label, color, 0);
	}

	private void label(Graphics2D g, LocalPoint at, String text, Color color, int lift)
	{
		if (at == null || text == null)
		{
			return;
		}
		Point p = Perspective.getCanvasTextLocation(client, g, at, text, lift);
		if (p != null)
		{
			OverlayUtil.renderTextLocation(g, p, text, color);
		}
	}
}
