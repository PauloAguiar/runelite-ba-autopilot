package com.galesong.baautopilot.tutorial;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

/**
 * Pans the camera to what the tutorial page is about, without moving you: the camera is detached
 * (free camera mode) and its focal point glides to the target; with no target, or when the
 * tutorial ends, the camera goes back to following you. Client thread, every client tick.
 */
@Slf4j
@Singleton
public class TutorialCamera
{
	/** Camera modes: follow the player, or free (detached focal point). */
	private static final int FOLLOW = 0;
	private static final int FREE = 1;
	/** Glide length: this long at least, plus this much per local unit (a room is about 1000), at most this. */
	private static final long GLIDE_MIN_MS = 450;
	private static final double GLIDE_MS_PER_UNIT = 0.8;
	private static final long GLIDE_MAX_MS = 2800;

	private final Client client;
	private boolean detached;
	/**
	 * Which focal point axis runs north-south: the engine's 3D axes have Y as height and Z as
	 * north-south, but rather than trust that, it's the one closer to your own local y when the
	 * camera detaches (it's centred on you then).
	 */
	private boolean northIsZ = true;
	/** The glide in progress: where to, from where, since when and for how long. */
	private LocalPoint glideTo;
	private float fromX;
	private float fromNorth;
	private long glideStart;
	private long glideMs;

	@Inject
	TutorialCamera(Client client)
	{
		this.client = client;
	}

	/**
	 * Glides to the target (null: back to you): a new target starts an eased move from where the
	 * camera is, longer for longer distances, so a pan to another room reads as one smooth sweep.
	 */
	public void update(LocalPoint target)
	{
		if (target == null)
		{
			release();
			return;
		}
		detach();
		long now = System.currentTimeMillis();
		if (!target.equals(glideTo))
		{
			glideTo = target;
			fromX = client.getCameraFocalPointX();
			fromNorth = north();
			glideStart = now;
			double distance = Math.hypot(target.getX() - fromX, target.getY() - fromNorth);
			glideMs = (long) Math.max(GLIDE_MIN_MS, Math.min(GLIDE_MAX_MS, GLIDE_MIN_MS + distance * GLIDE_MS_PER_UNIT));
		}
		double t = Math.min(1, (now - glideStart) / (double) glideMs);
		double eased = t * t * (3 - 2 * t);
		set((float) (fromX + (target.getX() - fromX) * eased), (float) (fromNorth + (target.getY() - fromNorth) * eased));
	}

	/** Puts the camera right on the target: for a tour that is already moving smoothly. */
	public void follow(LocalPoint target)
	{
		if (target == null)
		{
			release();
			return;
		}
		detach();
		glideTo = null;
		set(target.getX(), target.getY());
	}

	/** Where the camera looks now: its focal point while detached, else you; null before login. */
	public LocalPoint here()
	{
		if (detached)
		{
			return new LocalPoint((int) client.getCameraFocalPointX(), (int) north(), client.getTopLevelWorldView());
		}
		Player me = client.getLocalPlayer();
		return me == null ? null : me.getLocalLocation();
	}

	private void detach()
	{
		if (detached)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		LocalPoint here = me == null ? null : me.getLocalLocation();
		if (here != null)
		{
			northIsZ = Math.abs(client.getCameraFocalPointZ() - here.getY())
				<= Math.abs(client.getCameraFocalPointY() - here.getY());
			log.debug("Tutorial camera: focal {}, {}, {} for you at local {}, {}: north-south is {}",
				client.getCameraFocalPointX(), client.getCameraFocalPointY(), client.getCameraFocalPointZ(),
				here.getX(), here.getY(), northIsZ ? "Z" : "Y");
		}
		client.setCameraMode(FREE);
		detached = true;
		glideTo = null;
	}

	private float north()
	{
		return northIsZ ? client.getCameraFocalPointZ() : client.getCameraFocalPointY();
	}

	private void set(float x, float north)
	{
		client.setCameraFocalPointX(x);
		if (northIsZ)
		{
			client.setCameraFocalPointZ(north);
		}
		else
		{
			client.setCameraFocalPointY(north);
		}
	}

	public void release()
	{
		if (detached)
		{
			client.setCameraMode(FOLLOW);
			detached = false;
			glideTo = null;
		}
	}
}
