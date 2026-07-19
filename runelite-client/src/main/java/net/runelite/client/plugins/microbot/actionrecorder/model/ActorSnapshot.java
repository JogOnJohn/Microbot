package net.runelite.client.plugins.microbot.actionrecorder.model;

import lombok.Value;

@Value
public class ActorSnapshot
{
	String type;
	Integer id;
	String name;
	LocationSnapshot location;
}
