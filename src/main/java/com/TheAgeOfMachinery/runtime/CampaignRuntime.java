package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.CampaignAdvancementResult;
import com.TheAgeOfMachinery.quest.CampaignAdvancementService;
import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CampaignRuntime
{
    private static final Map<MinecraftServer, CampaignRuntime> ACTIVE_RUNTIMES = new IdentityHashMap<>();

    private final UUID sessionId = UUID.randomUUID();
    private final MinecraftServer server;
    private final CampaignSavedData savedData;
    private final CampaignAdvancementService advancementService;
    private final RewardManager rewardManager;
    private final CampaignDefinition campaign;

    private CampaignRuntime(MinecraftServer server)
    {
        this.server = Objects.requireNonNull(server, "Minecraft server cannot be null.");
        savedData = CampaignSavedData.get(server);

        advancementService = new CampaignAdvancementService(savedData);
        rewardManager = new RewardManager(savedData);

        campaign = BuiltInCampaign.create();

        for (QuestRewardDefinition definition : campaign.quests())
        {
            advancementService.registerQuest(definition);
        }
    }

    public static void onServerStarted(ServerStartedEvent event)
    {
        MinecraftServer server = event.getServer();

        if (ACTIVE_RUNTIMES.containsKey(server)) throw new IllegalStateException("Campaign runtime already exists for this server.");

        ACTIVE_RUNTIMES.put(server, new CampaignRuntime(server));
    }

    public static void onServerStopped(ServerStoppedEvent event)
    {
        ACTIVE_RUNTIMES.remove(event.getServer());
    }

    public static CampaignRuntime get(MinecraftServer server)
    {
        Objects.requireNonNull(server, "Minecraft server cannot be null.");

        if (!server.isSameThread()) throw new IllegalStateException("Campaign runtime must be accessed on the Minecraft server thread.");

        CampaignRuntime runtime = ACTIVE_RUNTIMES.get(server);

        if (runtime == null) throw new IllegalStateException("Campaign runtime has not been initialized for this server.");

        return runtime;
    }

    private void requireServerThread()
    {
        if (!server.isSameThread()) throw new IllegalStateException("Campaign operations must execute on the Minecraft server thread.");
    }

    private UUID validatePlayer(ServerPlayer player)
    {
        requireServerThread();
        Objects.requireNonNull(player, "Player cannot be null.");

        if (player.getServer() != server) throw new IllegalArgumentException("Player belongs to another Minecraft server.");

        return player.getUUID();
    }

    private UUID validateOnlinePlayer(ServerPlayer player)
    {
        UUID playerId = Objects.requireNonNull(validatePlayer(player),"Player cannot be null.");
        
        if (server.getPlayerList().getPlayer(playerId) != player)
        {
            throw new IllegalStateException("Player is not connected to this Minecraft server.");
        }

        return playerId;
    }

    public boolean initializePlayerCampaign(ServerPlayer player)
    {
        UUID playerId = validatePlayer(player);
        Optional<PlayerQuestProgress> existing = savedData.getPlayerQuestProgress(playerId);

        if (existing.isPresent() && !existing.get().getQuestProgressSnapshot().isEmpty()) return false;

        if (!advancementService.startFirstQuest(playerId))
        {
            throw new IllegalStateException("Failed to initialize campaign for player: " + playerId);
        }

        return true;
    }

    public boolean startCampaign(ServerPlayer player)
    {
        UUID playerId = validateOnlinePlayer(player);

        return advancementService.startFirstQuest(playerId);
    }

    public boolean addObjectiveProgress(ServerPlayer player, String objectiveId, int amount)
    {
        UUID playerId = validateOnlinePlayer(player);
        Objects.requireNonNull(objectiveId, "Objective ID cannot be null.");

        if (objectiveId.isBlank() || amount <= 0) return false;

        return advancementService.addObjectiveProgress(playerId, objectiveId, amount);
    }

    public CampaignAdvancementResult advanceQuest(ServerPlayer player)
    {
        UUID playerId = validateOnlinePlayer(player);

        return advancementService.advanceQuest(playerId);
    }

    public Optional<QuestDefinition> getActiveQuestDefinition(ServerPlayer player)
    {
        UUID playerId = validatePlayer(player);

        return advancementService.getActiveQuestDefinition(playerId);
    }

    public Optional<PlayerQuestProgress> getPlayerQuestProgress(ServerPlayer player)
    {
        UUID playerId = validatePlayer(player);

        return savedData.getPlayerQuestProgress(playerId);
    }

    public Map<String, List<RewardEntry>> getUnlockedRewards(ServerPlayer player)
    {
        UUID playerId = validatePlayer(player);

        return rewardManager.getPlayerRewardsSnapshot(playerId);
    }

    public RewardClaimResult claimReward(ServerPlayer player, String questId, String rewardId)
    {
        UUID playerId = validateOnlinePlayer(player);
        Objects.requireNonNull(questId, "Quest ID cannot be null.");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null.");

        if (questId.isBlank() || rewardId.isBlank()) return RewardClaimResult.NOT_FOUND;

        return rewardManager.claimReward(playerId, questId, rewardId, new MinecraftRewardDelivery(player));
    }

    public UUID getSessionId()
    {
        return sessionId;
    }

    public int getCampaignQuestCount()
    {
        return campaign.questCount();
    }

    public List<String> getCampaignQuestIds()
    {
        return campaign.getQuestIds();
    }
}
