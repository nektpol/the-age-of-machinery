
package com.TheAgeOfMachinery;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TheAgeofMachinery.MODID)
public class TheAgeofMachinery {

    public static final String MODID = "theageofmachinery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheAgeofMachinery(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("The Age of Machinery initialized.");
    }
}

