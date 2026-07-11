package net.runelite.client.plugins.devtools;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class ClipboardUtil
{
	private ClipboardUtil()
	{
	}

	static void copy(String text)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}
		StringSelection selection = new StringSelection(text);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}
}
