package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum JewelleryBox implements PohTeleport {
    // Games Necklace teleports
    BARBARIAN_ASSAULT(JewelleryLocationEnum.BARBARIAN_ASSAULT, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    BURTHORPE_GAMES_ROOM(JewelleryLocationEnum.BURTHORPE_GAMES_ROOM, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    TEARS_OF_GUTHIX(JewelleryLocationEnum.TEARS_OF_GUTHIX, Quest.TEARS_OF_GUTHIX, QuestState.FINISHED, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    CORPOREAL_BEAST(JewelleryLocationEnum.CORPOREAL_BEAST, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    WINTERTODT_CAMP(JewelleryLocationEnum.WINTERTODT_CAMP, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),

    // Ring of Dueling teleports
    PVP_ARENA(JewelleryLocationEnum.PVP_ARENA, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    FEROX_ENCLAVE(JewelleryLocationEnum.FEROX_ENCLAVE, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    CASTLE_WARS(JewelleryLocationEnum.CASTLE_WARS, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    FORTIS_COLOSSEUM(JewelleryLocationEnum.FORTIS_COLOSSEUM, Quest.CHILDREN_OF_THE_SUN, QuestState.FINISHED, JewelleryBoxType.BASIC, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),

    // Combat Bracelet teleports
    WARRIORS_GUILD(JewelleryLocationEnum.WARRIORS_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    CHAMPIONS_GUILD(JewelleryLocationEnum.CHAMPIONS_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    EDGEVILLE_MONASTERY(JewelleryLocationEnum.EDGEVILLE_MONASTERY, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    RANGING_GUILD(JewelleryLocationEnum.RANGING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),

    // Skills Necklace teleports
    FISHING_GUILD_NECK(JewelleryLocationEnum.FISHING_GUILD_NECK, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    MINING_GUILD(JewelleryLocationEnum.MINING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    CRAFTING_GUILD(JewelleryLocationEnum.CRAFTING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    COOKING_GUILD(JewelleryLocationEnum.COOKING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    WOODCUTTING_GUILD(JewelleryLocationEnum.WOODCUTTING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),
    FARMING_GUILD(JewelleryLocationEnum.FARMING_GUILD, JewelleryBoxType.FANCY, JewelleryBoxType.ORNATE),

    // Amulet of Glory teleports
    EDGEVILLE(JewelleryLocationEnum.EDGEVILLE, JewelleryBoxType.ORNATE),
    KARAMJA(JewelleryLocationEnum.KARAMJA, JewelleryBoxType.ORNATE),
    DRAYNOR_VILLAGE(JewelleryLocationEnum.DRAYNOR_VILLAGE, JewelleryBoxType.ORNATE),
    AL_KHARID(JewelleryLocationEnum.AL_KHARID, JewelleryBoxType.ORNATE),

    // Ring of Wealth teleports
    MISCELLANIA(JewelleryLocationEnum.MISCELLANIA, Quest.THE_FREMENNIK_TRIALS, QuestState.FINISHED, JewelleryBoxType.ORNATE),
    GRAND_EXCHANGE(JewelleryLocationEnum.GRAND_EXCHANGE, JewelleryBoxType.ORNATE),
    FALADOR_PARK(JewelleryLocationEnum.FALADOR_PARK, JewelleryBoxType.ORNATE),
    // Unlocks partway through Between a Rock, so started is the best data-side gate; a mid-quest
    // player who hasn't reached the unlock yet is caught by the failed-execute blocklist instead.
    DONDAKAN(JewelleryLocationEnum.DONDAKAN, Quest.BETWEEN_A_ROCK, QuestState.IN_PROGRESS, JewelleryBoxType.ORNATE);

    private final JewelleryLocationEnum location;
    private final List<JewelleryBoxType> availableInBoxTypes;
    private final Quest requiredQuest;
    private final QuestState requiredQuestState;

    JewelleryBox(JewelleryLocationEnum jewelleryLocationEnum, JewelleryBoxType... jewelleryBoxType) {
        this(jewelleryLocationEnum, null, null, jewelleryBoxType);
    }

    JewelleryBox(JewelleryLocationEnum jewelleryLocationEnum, Quest requiredQuest, QuestState requiredQuestState, JewelleryBoxType... jewelleryBoxType) {
        this.location = jewelleryLocationEnum;
        this.requiredQuest = requiredQuest;
        this.requiredQuestState = requiredQuestState;
        this.availableInBoxTypes = Arrays.asList(jewelleryBoxType);
    }

    /**
     * Player-side quest locks the game enforces on jewellery box options regardless of box tier:
     * requiredQuestState FINISHED = quest must be complete; IN_PROGRESS = quest at least started.
     */
    @Override
    public boolean isUnlockedForPlayer() {
        if (requiredQuest == null) {
            return true;
        }
        QuestState state = Rs2Player.getQuestState(requiredQuest);
        if (requiredQuestState == QuestState.FINISHED) {
            return state == QuestState.FINISHED;
        }
        return state == QuestState.IN_PROGRESS || state == QuestState.FINISHED;
    }

    @Override
    public WorldPoint getDestination() {
        return location.getLocation();
    }

    @Override
    public boolean execute() {
        return PohTeleports.useJewelleryBox(location);
    }

    @Override
    public int getDuration() {
        return 6;
    }

    @Override
    public String displayInfo() {
        return "JewelleryBox -> " + name();
    }

    /**
     * Checks if this teleport is available in the specified box type
     */
    public boolean isAvailableIn(JewelleryBoxType boxType) {
        return availableInBoxTypes.contains(boxType);
    }

    /**
     * Gets all teleports available for a specific jewellery box type
     */
    public static List<JewelleryBox> getAvailableTeleports(JewelleryBoxType boxType) {
        return Arrays.stream(values())
                .filter(teleport -> teleport.isAvailableIn(boxType))
                .collect(Collectors.toList());
    }

}