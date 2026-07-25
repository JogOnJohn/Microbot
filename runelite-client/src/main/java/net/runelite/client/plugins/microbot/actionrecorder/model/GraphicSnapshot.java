package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class GraphicSnapshot
{
	int graphicId;
	int startCycle;
	int height;
	int frame;
	int cycle;
}
