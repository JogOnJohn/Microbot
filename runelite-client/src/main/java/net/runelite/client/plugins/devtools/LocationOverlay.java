/*
 * Copyright (c) 2018, Seth <https://github.com/sethtroll>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.devtools;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.api.Constants.CHUNK_SIZE;

public class LocationOverlay extends OverlayPanel {
    private final Client client;
    private final DevToolsPlugin plugin;

    @Inject
    LocationOverlay(Client client, DevToolsPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    /**
     * Builds the same location block the overlay renders as plain text.
     * Must be called on the client thread with a logged-in local player.
     */
    static String buildLocationText(Client client) {
        if (client.getLocalPlayer() == null) {
            return null;
        }

        WorldPoint worldPoint = client.getLocalPlayer().getWorldLocation();
        LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();

        StringBuilder sb = new StringBuilder();

        if (client.isInInstancedRegion()) {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
            sb.append("Instance\n");
        }

        sb.append("Local: ").append(localPoint.getX()).append(", ").append(localPoint.getY()).append('\n');
        sb.append("World: ").append(worldPoint.getX()).append(", ").append(worldPoint.getY()).append(", ").append(worldPoint.getPlane()).append('\n');
        sb.append("Region: ").append(worldPoint.getRegionX()).append(", ").append(worldPoint.getRegionY()).append(", ").append(worldPoint.getRegionID()).append('\n');
        sb.append("Scene: ").append(localPoint.getSceneX()).append(", ").append(localPoint.getSceneY()).append('\n');

        int[][][] instanceTemplateChunks = client.getInstanceTemplateChunks();
        int z = client.getPlane();
        int chunkData = instanceTemplateChunks[z][localPoint.getSceneX() / CHUNK_SIZE][localPoint.getSceneY() / CHUNK_SIZE];

        int rotation = chunkData >> 1 & 0x3;
        int chunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
        int chunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;

        sb.append("Chunk ").append(localPoint.getSceneX() / CHUNK_SIZE).append(',').append(localPoint.getSceneY() / CHUNK_SIZE)
                .append(": ").append(rotation).append(' ').append(chunkX).append(' ').append(chunkY).append('\n');
        sb.append("Base: ").append(client.getBaseX()).append(", ").append(client.getBaseY()).append('\n');

        for (int i = 0; i < client.getMapRegions().length; i++) {
            int region = client.getMapRegions()[i];
            int mx = region >> 8;
            int my = region & 0xff;
            sb.append(i == 0 ? "Map regions: " : "             ").append(mx).append(", ").append(my)
                    .append(" (").append(region).append(')').append('\n');
        }

        return sb.toString();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.getLocation().isActive()) {
            return null;
        }

        WorldPoint worldPoint = client.getLocalPlayer().getWorldLocation();
        LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();

        if (client.isInInstancedRegion()) {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Instance")
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Local")
                .right(localPoint.getX() + ", " + localPoint.getY())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("World")
                .right(worldPoint.getX() + ", " + worldPoint.getY() + ", " + worldPoint.getPlane())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Region")
                .right(worldPoint.getRegionX() + ", " + worldPoint.getRegionY() + ", " + worldPoint.getRegionID())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Scene")
                .right(localPoint.getSceneX() + ", " + localPoint.getSceneY())
                .build());

        int[][][] instanceTemplateChunks = client.getInstanceTemplateChunks();
        int z = client.getPlane();
        int chunkData = instanceTemplateChunks[z][localPoint.getSceneX() / CHUNK_SIZE][localPoint.getSceneY() / CHUNK_SIZE];

        int rotation = chunkData >> 1 & 0x3;
        int chunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
        int chunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Chunk " + localPoint.getSceneX() / CHUNK_SIZE + "," + localPoint.getSceneY() / CHUNK_SIZE)
                .right(rotation + " " + chunkX + " " + chunkY)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Base")
                .right(client.getBaseX() + ", " + client.getBaseY())
                .build());

        for (int i = 0; i < client.getMapRegions().length; i++) {
            int region = client.getMapRegions()[i];
            int mx = region >> 8;
            int my = region & 0xff;

            panelComponent.getChildren().add(LineComponent.builder()
                    .left((i == 0) ? "Map regions" : " ")
                    .right(mx + ", " + my)
                    .rightColor((region == worldPoint.getRegionID()) ? Color.GREEN : Color.WHITE)
                    .build());
        }

        return super.render(graphics);
    }
}
