package net.runelite.client.plugins.microbot.util.poh;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PohTransportArrivalTest {
    @Test public void arrivedTransportDoesNotRepeatPoolOrTeleport() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        PohTeleport teleport = mock(PohTeleport.class);
        WorldPoint destination = new WorldPoint(1754, 3564, 0);
        when(teleport.getDestination()).thenReturn(destination);
        PohTransport transport = new PohTransport(new WorldPoint(1939, 7053, 2), teleport);
        try (MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class);
             MockedStatic<PohTeleports> pools = mockStatic(PohTeleports.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            players.when(Rs2Player::getWorldLocation).thenReturn(destination);
            assertEquals(PohExecutionResult.SUCCESS, transport.executeDetailed(() -> false));
            assertEquals(PohExecutionResult.CANCELLED, transport.executeDetailed(() -> true));
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            assertEquals(PohExecutionResult.SCENE_PENDING, transport.executeDetailed(() -> false));
            pools.verifyNoInteractions();
            verify(teleport, never()).execute();
        }
    }

    @Test public void arrivalDuringObstructionInspectionWinsOverStaleHouseEvidence() {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getWorld()).thenReturn(330);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call ->
                Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        PohTeleport teleport = mock(PohTeleport.class);
        WorldPoint destination = new WorldPoint(1754, 3564, 0);
        when(teleport.getDestination()).thenReturn(destination);
        AtomicReference<WorldPoint> location = new AtomicReference<>(new WorldPoint(1939, 7053, 2));
        PohTransport transport = new PohTransport(location.get(), teleport);
        try (MockedStatic<Rs2Player> players = mockStatic(Rs2Player.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<PohFacilityAccess> access = mockStatic(PohFacilityAccess.class);
             MockedStatic<PohTeleports> pools = mockStatic(PohTeleports.class)) {
            players.when(Rs2Player::getWorldLocation).thenAnswer(call -> location.get());
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            access.when(() -> PohFacilityAccess.inspect(teleport)).thenAnswer(call -> {
                location.set(destination);
                return PohFacilityAccess.Access.OBSTRUCTED;
            });
            assertEquals(PohExecutionResult.SUCCESS, transport.executeDetailed(() -> false));
            access.verify(() -> PohFacilityAccess.inspect(teleport));
            pools.verifyNoInteractions();
            verify(teleport, never()).execute();
        }
    }
}
