package net.runelite.client.plugins.microbot.agentserver.handler;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateHandlerTest
{
    @Test
    public void capturesResizableCameraValues()
    {
        Client client = mock(Client.class);
        when(client.isResized()).thenReturn(true);
        when(client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT)).thenReturn(299);
        when(client.getCameraPitch()).thenReturn(256);
        when(client.getCameraYaw()).thenReturn(1536);
        Map<String, Object> camera = StateHandler.cameraSnapshot(client);
        assertEquals(299, camera.get("zoom"));
        assertEquals(256, camera.get("pitch"));
        assertEquals(1536, camera.get("yaw"));
        assertEquals(true, camera.get("resized"));
    }

    @Test
    public void fixedViewportUsesItsOwnZoomValue()
    {
        Client client = mock(Client.class);
        when(client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT)).thenReturn(200);
        when(client.getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT)).thenReturn(500);
        Map<String, Object> camera = StateHandler.cameraSnapshot(client);
        assertEquals(200, camera.get("zoom"));
        assertEquals(false, camera.get("resized"));
    }
}
