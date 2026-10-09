package net.runelite.client.plugins.microbot.shortestpath.pathfinder.policy;

import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.function.IntUnaryOperator;

public final class TransportRequirementPolicy {
    private TransportRequirementPolicy() {
    }

    public static boolean completedQuests(Transport transport, List<QuestState> questStateOrder) {
        return transport.getQuests().entrySet().stream()
                .allMatch(entry -> {
                    QuestState playerState = Rs2Player.getQuestState(entry.getKey());
                    QuestState requiredState = entry.getValue();
                    int playerIndex = questStateOrder.indexOf(playerState);
                    int requiredIndex = questStateOrder.indexOf(requiredState);
                    if (requiredIndex < 0 || playerIndex < 0) {
                        return false;
                    }
                    return playerIndex >= requiredIndex;
                });
    }

    public static boolean varbitChecks(Transport transport) {
        return varbitChecks(transport, Microbot::getVarbitValue);
    }

    public static boolean varbitChecks(Transport transport, IntUnaryOperator values) {
        return transport.getVarbits().isEmpty()
                || transport.getVarbits().stream()
                .allMatch(check -> {
                    int actual = values.applyAsInt(check.getVarbitId());
                    String houseOption = transport.getHouseTeleportOption();
                    // Imported house rows encode this toggle backwards. Keep this adapter across data syncs.
                    if (check.getVarbitId() == VarbitID.POH_TELE_TOGGLE && houseOption != null) {
                        return actual == ("Outside".equals(houseOption) ? 1 : 0);
                    }
                    return check.matches(actual);
                });
    }

    public static boolean varplayerChecks(Transport transport) {
        return transport.getVarplayers().isEmpty()
                || transport.getVarplayers().stream()
                .allMatch(varplayerCheck -> varplayerCheck.matches(Microbot.getVarbitPlayerValue(varplayerCheck.getVarplayerId())));
    }
}
