/*
 * Copyright (c) 2026, Microbot
 * All rights reserved.
 */
package net.runelite.client.plugins.microbot.inventorysetups;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

@Singleton
class InventorySetupsBankFilterButton
{
	private static final int BUTTON_SIZE = 25;
	private static final int BUTTON_X = 5;
	private static final int BUTTON_Y = 5;
	private static final String BUTTON_NAME = "Inventory Setups";
	private static final String BUTTON_ACTION = "Filter bank";

	private final Client client;
	private final MInventorySetupsPlugin plugin;
	private Widget button;

	@Inject
	InventorySetupsBankFilterButton(final Client client, final MInventorySetupsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	void init()
	{
		final Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null || parent.isHidden())
		{
			return;
		}

		if (button != null && !button.isHidden())
		{
			refresh();
			return;
		}

		button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setOriginalWidth(BUTTON_SIZE);
		button.setOriginalHeight(BUTTON_SIZE);
		button.setOriginalX(BUTTON_X);
		button.setOriginalY(BUTTON_Y);
		button.setName(BUTTON_NAME);
		button.setAction(1, BUTTON_ACTION);
		button.setOnOpListener((JavaScriptCallback) event ->
		{
			if (event.getOp() == 1)
			{
				plugin.toggleBankFilterFromButton();
			}
		});
		button.setOnOpListener(ScriptID.NULL);
		button.setHasListener(true);
		refresh();
	}

	void refresh()
	{
		if (button == null)
		{
			return;
		}

		button.setSpriteId(plugin.isInventorySetupTagOpen()
			? SpriteID.WORLDSWITCHER_FILTERED
			: SpriteID.WORLDSWITCHER_FILTER);
		button.revalidate();
	}

	void destroy()
	{
		if (button != null)
		{
			button.setHidden(true);
			button = null;
		}
	}
}
