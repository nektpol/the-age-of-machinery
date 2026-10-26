
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
import com.TheAgeOfMachinery.reward.RewardPersistenceTestCommand;
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
    }
}

