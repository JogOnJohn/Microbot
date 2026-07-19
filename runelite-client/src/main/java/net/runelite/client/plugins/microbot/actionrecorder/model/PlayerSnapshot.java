package net.runelite.client.plugins.microbot.actionrecorder.model;

import java.util.List;
import lombok.Value;

@Value
public class PlayerSnapshot
{
	int animationId;
	int poseAnimationId;
	List<GraphicSnapshot> graphics;
	int orientation;
	int runEnergy;
	LocationSnapshot destination;
	ActorSnapshot interacting;
}
