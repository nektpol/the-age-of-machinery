
package com.TheAgeOfMachinery;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import com.TheAgeOfMachinery.command.RewardTestCommand;
import com.TheAgeOfMachinery.command.RewardClaimTestCommand;
import com.TheAgeOfMachinery.command.SaveTestCommand;
import com.TheAgeOfMachinery.command.QuestPersistenceTestCommand;
import com.TheAgeOfMachinery.command.ManagerIntegrationTestCommand;
import com.TheAgeOfMachinery.command.CampaignAdvancementTestCommand;
import com.TheAgeOfMachinery.command.StateIsolationTestCommand;
import com.TheAgeOfMachinery.command.QuestRevisionTestCommand;
import com.TheAgeOfMachinery.command.RewardRevisionTestCommand;
import com.TheAgeOfMachinery.command.CampaignFailureTestCommand;
import com.TheAgeOfMachinery.command.CampaignRuntimeTestCommand;
import com.TheAgeOfMachinery.command.CampaignJsonParserTestCommand;
import com.TheAgeOfMachinery.command.CampaignRegistryTestCommand;
import com.TheAgeOfMachinery.command.CampaignReloadTestCommand;

import com.TheAgeOfMachinery.reward.RewardPersistenceTestCommand;

import com.TheAgeOfMachinery.runtime.CampaignRuntime;
import com.TheAgeOfMachinery.runtime.CampaignPlayerEvents;
import com.TheAgeOfMachinery.runtime.CampaignCraftingEvents;
import com.TheAgeOfMachinery.runtime.CampaignReloadEvents;
import com.TheAgeOfMachinery.runtime.CampaignItemPickupEvents;

import com.TheAgeOfMachinery.create.ownership.MachineOwnershipTestCommand;
import com.TheAgeOfMachinery.create.ownership.MachineOwnershipEvents;

import net.neoforged.neoforge.common.NeoForge;

@Mod(TheAgeofMachinery.MODID)
public class TheAgeofMachinery 
{

    public static final String MODID = "theageofmachinery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheAgeofMachinery(IEventBus modEventBus, ModContainer modContainer) 
    {
        LOGGER.info("The Age of Machinery initialized.");
        NeoForge.EVENT_BUS.addListener(RewardTestCommand::register);
        NeoForge.EVENT_BUS.addListener(RewardClaimTestCommand::register);
        NeoForge.EVENT_BUS.addListener(RewardClaimTestCommand::reset);
        NeoForge.EVENT_BUS.addListener(SaveTestCommand::register);
        NeoForge.EVENT_BUS.addListener(QuestPersistenceTestCommand::register);
        NeoForge.EVENT_BUS.addListener(RewardPersistenceTestCommand::register);
        NeoForge.EVENT_BUS.addListener(ManagerIntegrationTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignAdvancementTestCommand::register);
        NeoForge.EVENT_BUS.addListener(StateIsolationTestCommand::register);
        NeoForge.EVENT_BUS.addListener(QuestRevisionTestCommand::register);
        NeoForge.EVENT_BUS.addListener(RewardRevisionTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignFailureTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignRuntimeTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignJsonParserTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignRegistryTestCommand::register);
        NeoForge.EVENT_BUS.addListener(CampaignReloadTestCommand::register);
        
        NeoForge.EVENT_BUS.addListener(CampaignRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CampaignRuntime::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CampaignPlayerEvents::onPlayerLoggedIn);

        NeoForge.EVENT_BUS.addListener(CampaignCraftingEvents::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(CampaignItemPickupEvents::onItemPickedUp);
        NeoForge.EVENT_BUS.addListener(CampaignReloadEvents::onDatapackSync);

        NeoForge.EVENT_BUS.addListener(MachineOwnershipTestCommand::register);
        NeoForge.EVENT_BUS.addListener(MachineOwnershipEvents::onPlacement);
        NeoForge.EVENT_BUS.addListener(MachineOwnershipEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(MachineOwnershipEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(MachineOwnershipEvents::onServerStopped);
    }
}

