package net.runelite.client.plugins.microbot.inventorysetups;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class InventorySetupsBankFilteringTest
{
    private final MInventorySetupsPlugin plugin = new MInventorySetupsPlugin();
    private final Client client = mock(Client.class);
    private final BankTagsService tags = mock(BankTagsService.class);
    private final LayoutManager layouts = mock(LayoutManager.class);
    private final InventorySetupLayoutUtilities utilities = mock(InventorySetupLayoutUtilities.class);
    private final MInventorySetupsConfig config = mock(MInventorySetupsConfig.class);
    private final TagManager tagManager = mock(TagManager.class);
    private final InventorySetup setup = mock(InventorySetup.class);
    private String tagName;

    private void inject(String name, Object value) throws Exception
    {
        Field field = MInventorySetupsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    @Before
    public void setup() throws Exception
    {
        ClientThread thread = mock(ClientThread.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
        inject("clientThread", thread); inject("client", client); inject("bankTagsService", tags);
        inject("layoutManager", layouts); inject("layoutUtilities", utilities); inject("config", config); inject("tagManager", tagManager);
        when(setup.getName()).thenReturn("Test setup");
        tagName = InventorySetupLayoutUtilities.getTagNameForLayout("Test setup");
        when(setup.isFilterBank()).thenReturn(true);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(mock(Widget.class));
    }

    @Test
    public void missingLayoutIsCreatedBeforeOpeningClassicFilter()
    {
        Layout layout = new Layout(tagName);
        when(utilities.createSetupLayout(setup)).thenReturn(layout);
        plugin.toggleBankFilter(setup);
        verify(layouts).saveLayout(layout);
        verify(tagManager).setHidden(tagName, true);
        verify(tags).openBankTag(tagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME | BankTagsService.OPTION_NO_LAYOUT);
    }

    @Test
    public void existingLayoutIsRecalculatedBeforeOpeningLayoutFilter()
    {
        when(config.useLayouts()).thenReturn(true);
        when(layouts.loadLayout(tagName)).thenReturn(new Layout(tagName));
        when(tags.getActiveTag()).thenReturn(tagName);
        plugin.toggleBankFilter(setup);
        verify(utilities).recalculateLayout(setup);
        verify(tags).openBankTag(tagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME);
    }

    @Test
    public void disablingFilterClosesOnlyItsOwnTag()
    {
        when(setup.isFilterBank()).thenReturn(false);
        when(tags.getActiveTag()).thenReturn(tagName);
        plugin.toggleBankFilter(setup);
        verify(tags).closeBankTag();
        verify(tags, never()).openBankTag(anyString(), anyInt());
    }

    @Test
    public void closedBankDefersFiltering()
    {
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        plugin.toggleBankFilter(setup);
        verifyNoInteractions(layouts, utilities, tags);
    }
}
