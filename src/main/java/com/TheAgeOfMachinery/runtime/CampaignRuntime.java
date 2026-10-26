package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.persistence.CampaignSavedData;
import com.TheAgeOfMachinery.quest.CampaignAdvancementService;
import com.TheAgeOfMachinery.reward.RewardManager;
import com.TheAgeOfMachinery.quest.CampaignDefinition;
import com.TheAgeOfMachinery.reward.QuestRewardDefinition;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CampaignRuntime
{
    private static final Map<MinecraftServer, CampaignRuntime> ACTIVE_RUNTIMES = new IdentityHashMap<>();

    private final UUID sessionId = UUID.randomUUID();
    private final CampaignAdvancementService advancementService;
    private final RewardManager rewardManager;
    private final CampaignDefinition campaign;

    
    private CampaignRuntime(MinecraftServer server)
    {
        CampaignSavedData savedData = CampaignSavedData.get(server);

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

}

