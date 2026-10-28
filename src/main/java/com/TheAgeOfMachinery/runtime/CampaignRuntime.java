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
import com.TheAgeOfMachinery.sync.PlayerCampaignView;
import com.TheAgeOfMachinery.sync.CampaignSyncService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import com.TheAgeOfMachinery.json.CampaignDataPackLoader;
import com.TheAgeOfMachinery.json.CampaignReloadCompatibility;

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
    private CampaignAdvancementService advancementService;
    private final RewardManager rewardManager;
    private CampaignDefinition campaign;
    private String lastReloadStatus = "No successful data-pack reload handled yet.";

    private CampaignRuntime(MinecraftServer server)
    {
        this.server = Objects.requireNonNull(server, "Minecraft server cannot be null.");
        campaign = CampaignDataPackLoader.load(server.getResourceManager());

        savedData = CampaignSavedData.get(server);
        advancementService = new CampaignAdvancementService(savedData);
        rewardManager = new RewardManager(savedData);

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

        boolean started = advancementService.startFirstQuest(playerId);
        if (started) CampaignSyncService.queue(player);
        return started;
    }

    public boolean addObjectiveProgress(ServerPlayer player, String objectiveId, int amount)
    {
        UUID playerId = validateOnlinePlayer(player);
        Objects.requireNonNull(objectiveId, "Objective ID cannot be null.");

        if (objectiveId.isBlank() || amount <= 0) return false;

        boolean changed = advancementService.addObjectiveProgress(playerId, objectiveId, amount);
        if (changed) CampaignSyncService.queue(player);
        return changed;
    }

    public CampaignAdvancementResult advanceQuest(ServerPlayer player)
    {
        UUID playerId = validateOnlinePlayer(player);

        CampaignAdvancementResult result = advancementService.advanceQuest(playerId);
        if (result == CampaignAdvancementResult.NEXT_QUEST_STARTED || result == CampaignAdvancementResult.CAMPAIGN_COMPLETED) CampaignSyncService.queue(player);
        return result;
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

    public PlayerCampaignView getPlayerView(ServerPlayer player)
    {
        UUID playerId = validateOnlinePlayer(player);
        Optional<CampaignSavedData.PlayerSnapshot> result = savedData.getPlayerSnapshot(playerId);

        if (result.isEmpty()) return PlayerCampaignViewFactory.create(sessionId, playerId, 0, campaign, Optional.empty(), Optional.empty());

        CampaignSavedData.PlayerSnapshot snapshot = result.get();
        return PlayerCampaignViewFactory.create(sessionId, playerId, snapshot.revision(), campaign, Optional.ofNullable(snapshot.quests()), Optional.ofNullable(snapshot.rewards()));
    }

    public RewardClaimResult claimReward(ServerPlayer player, String questId, String rewardId)
    {
        UUID playerId = validateOnlinePlayer(player);
        Objects.requireNonNull(questId, "Quest ID cannot be null.");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null.");

        if (questId.isBlank() || rewardId.isBlank()) return RewardClaimResult.NOT_FOUND;

        RewardClaimResult result = rewardManager.claimReward(playerId, questId, rewardId, new MinecraftRewardDelivery(player));
        if (result == RewardClaimResult.SUCCESS) CampaignSyncService.queue(player);
        return result;
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
    
    public static Optional<CampaignRuntime> findIfStarted(MinecraftServer server)
    {
        Objects.requireNonNull(server, "Minecraft server cannot be null.");

        if (!server.isSameThread()) throw new IllegalStateException("Campaign runtime must be accessed on the server thread.");

        return Optional.ofNullable(ACTIVE_RUNTIMES.get(server));
    }

    public void replaceCompatibleCampaign(CampaignDefinition candidate)
    {
        requireServerThread();
        Objects.requireNonNull(candidate, "Reload candidate cannot be null.");

        CampaignReloadCompatibility.requireCompatible(campaign, candidate);

        CampaignAdvancementService replacementService = new CampaignAdvancementService(savedData);

        for (QuestRewardDefinition definition : candidate.quests())
        {
            replacementService.registerQuest(definition);
        }

        advancementService = replacementService;
        campaign = candidate;
        lastReloadStatus = "APPLIED: Compatible presentation-only campaign reload.";
        CampaignSyncService.queueAll(server);
    }

    public void recordReloadFailure(String reason)
    {
        requireServerThread();

        lastReloadStatus = "REJECTED: " + Objects.toString(reason, "Unknown reload failure");
    }

    public String getLastReloadStatus()
    {
        requireServerThread();

        return lastReloadStatus;
    }

}
