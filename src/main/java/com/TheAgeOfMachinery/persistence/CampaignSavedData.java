package com.TheAgeOfMachinery.persistence;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;

import javax.annotation.Nonnull;

public class CampaignSavedData extends SavedData
{
    private static final String FILE_NAME = "theageofmachinery_campaign";
    private static final String TAG_VERSION = "FormatVersion";
    private static final String TAG_TEST_VALUE = "TestValue";
    private static final int FORMAT_VERSION = 1;

    private int testValue = 0;

    public static CampaignSavedData get(MinecraftServer server)
    {
        Objects.requireNonNull(server, "Minecraft server cannot be null.");

        SavedData.Factory<CampaignSavedData> factory = new SavedData.Factory<>(CampaignSavedData::new, CampaignSavedData::load);

        return server.overworld().getDataStorage().computeIfAbsent(factory, FILE_NAME);
    }

    public static CampaignSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        CampaignSavedData data = new CampaignSavedData();

        data.testValue = tag.getInt(TAG_TEST_VALUE);

        return data;
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag,@Nonnull HolderLookup.Provider registries)
    {
        tag.putInt(TAG_VERSION, FORMAT_VERSION);
        tag.putInt(TAG_TEST_VALUE, testValue);

        return tag;
    }

    public int getTestValue()
    {
        return testValue;
    }

    public void incrementTestValue()
    {
        testValue++;
        setDirty();
    }
}
