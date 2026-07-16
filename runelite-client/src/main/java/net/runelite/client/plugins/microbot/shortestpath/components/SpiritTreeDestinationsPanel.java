package net.runelite.client.plugins.microbot.shortestpath.components;

import net.runelite.client.plugins.microbot.Microbot;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.CONFIG_GROUP;

/**
 * Per-destination spirit tree toggles for the webwalker side panel. The config keys have always
 * existed in ShortestPathConfig (sectionSpiritTrees) and gate routing via
 * PathfinderConfig.isSpiritTreeRouteEnabled — but the Microbot client hides the RuneLite config
 * panel, so users had no way to disable FARMABLE destinations they haven't grown, and the
 * pathfinder routed through trees whose menu won't offer the destination (live: Karamja routes
 * picking an ungrown Brimhaven tree). The runtime failure blocklist recovers after one failed
 * attempt; these toggles avoid the attempt entirely.
 */
public class SpiritTreeDestinationsPanel extends JPanel {

    /** keyName in ShortestPathConfig -> display label. Order mirrors SPIRIT_TREE_DESTINATIONS_ORDERED. */
    private static final Map<String, String> DESTINATIONS = new LinkedHashMap<>();

    static {
        DESTINATIONS.put("spiritTreeEtceteria", "Etceteria");
        DESTINATIONS.put("spiritTreeBrimhaven", "Brimhaven");
        DESTINATIONS.put("spiritTreePortSarim", "Port Sarim");
        DESTINATIONS.put("spiritTreeHosidius", "Hosidius");
        DESTINATIONS.put("spiritTreeFarmingGuild", "Farming Guild");
    }

    public SpiritTreeDestinationsPanel() {
        setBorder(new TitledBorder("Spirit tree destinations (farmable)"));
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.set(2, 4, 2, 4);

        for (Map.Entry<String, String> entry : DESTINATIONS.entrySet()) {
            String key = entry.getKey();
            JCheckBox cb = new JCheckBox(entry.getValue());
            cb.setToolTipText("Untick if your " + entry.getValue() + " spirit tree isn't grown — the walker will stop routing through it");
            cb.setSelected(getBoolOrDefault(key, true));
            cb.addActionListener(e -> Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, key, cb.isSelected()));
            add(cb, gbc);
            gbc.gridy++;
        }
    }

    /**
     * ConfigManager returns null (not the @ConfigItem default) for keys never written — the same
     * gap that produces CheckboxPanel's "Failed to poh checkbox config" NPE logs. These keys
     * default to true in ShortestPathConfig, so mirror that here.
     */
    private static boolean getBoolOrDefault(String key, boolean def) {
        Boolean value = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, key, Boolean.class);
        return value == null ? def : value;
    }
}
