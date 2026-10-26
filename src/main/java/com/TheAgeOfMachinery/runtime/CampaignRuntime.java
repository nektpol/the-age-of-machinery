package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.integration.MinecraftRewardDelivery;
import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.CampaignAdvancementResult;
import com.TheAgeOfMachinery.quest.CampaignAdvancementService;
import com.TheAgeOfMachinery.quest.PlayerQuestProgress;
import com.TheAgeOfMachinery.quest.QuestDefinition;
import com.TheAgeOfMachinery.reward.RewardClaimResult;
import com.TheAgeOfMachinery.reward.RewardEntry;
import com.TheAgeOfMachinery.reward.RewardManager;
import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

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
    private final CampaignAdvancementService advancementService;
    private final RewardManager rewardManager;
    private final CampaignDefinition campaign;

    private final MinecraftServer server;
    private final CampaignSavedData savedData;
  
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

        CampaignRuntime runtime = ACTIVE_RUNTIMES.get(server);

        if (runtime == null) throw new IllegalStateException("Campaign runtime has not been initialized for this server.");

        return runtime;
    }

    public UUID getSessionId()
    {
        return sessionId;
    }

    public CampaignAdvancementService getAdvancementService()
    {
        return advancementService;
    }

    public RewardManager getRewardManager()
    {
        return rewardManager;
    }

    public int getCampaignQuestCount()
    {
        return campaign.questCount();
    }

    public List<String> getCampaignQuestIds()
    {
        return campaign.getQuestIds();
    }
    
    public boolean startCampaign(UUID playerId)
    {
        return advancementService.startFirstQuest(playerId);
    }

    public boolean addObjectiveProgress(UUID playerId, String objectiveId, int amount)
    {
        return advancementService.addObjectiveProgress(playerId, objectiveId, amount);
    }

    public CampaignAdvancementResult advanceQuest(UUID playerId)
    {
        return advancementService.advanceQuest(playerId);
    }

    public Optional<QuestDefinition> getActiveQuestDefinition(UUID playerId)
    {
        return advancementService.getActiveQuestDefinition(playerId);
    }

    public Optional<PlayerQuestProgress> getPlayerQuestProgress(UUID playerId)
    {
        return savedData.getPlayerQuestProgress(playerId);
    }

    public Map<String, List<RewardEntry>> getUnlockedRewards(UUID playerId)
    {
        return rewardManager.getPlayerRewardsSnapshot(playerId);
    }

    public RewardClaimResult claimReward(ServerPlayer player, String questId, String rewardId)
    {
        Objects.requireNonNull(player, "Player cannot be null.");
        Objects.requireNonNull(questId, "Quest ID cannot be null.");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null.");

        if (player.getServer() != server) throw new IllegalArgumentException("Player belongs to another Minecraft server.");

        return rewardManager.claimReward(player.getUUID(), questId, rewardId, new MinecraftRewardDelivery(player));
    }
    
    public boolean initializePlayerCampaign(ServerPlayer player)
    {
        Objects.requireNonNull(player, "Player cannot be null.");

        if (player.getServer() != server) throw new IllegalArgumentException("Player belongs to another Minecraft server.");

        UUID playerId = player.getUUID();
        Optional<PlayerQuestProgress> existing = savedData.getPlayerQuestProgress(playerId);

        if (existing.isPresent() && !existing.get().getQuestProgressSnapshot().isEmpty()) return false;

        if (!advancementService.startFirstQuest(playerId))
        {
            throw new IllegalStateException("Failed to initialize campaign for player: " + playerId);
        }

        return true;
    }
}

