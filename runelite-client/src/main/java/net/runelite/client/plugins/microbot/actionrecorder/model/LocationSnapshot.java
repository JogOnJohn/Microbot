package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class LocationSnapshot
{
	int worldX;
	int worldY;
	int plane;
	int regionId;
	int sceneX;
	int sceneY;
	int worldViewId;
	boolean instanced;
	int templateX;
	int templateY;
	int templatePlane;
	int templateRegionId;
}
