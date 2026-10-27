package com.TheAgeOfMachinery.create.ownership;

import com.TheAgeOfMachinery.create.MachineLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

final class MachineOwnershipSavedData extends SavedData 
{

    private static final String DATA_NAME = "theageofmachinery_machine_ownership";
    private static final int FORMAT_VERSION = 1;

    private static final SavedData.Factory<MachineOwnershipSavedData> FACTORY = new SavedData.Factory<>(
            MachineOwnershipSavedData::new,
            MachineOwnershipSavedData::load
    );

    private final Map<MachineLocation, MachineOwnership> installations = new HashMap<>();

    static MachineOwnershipSavedData get(MinecraftServer server) 
    {
        Objects.requireNonNull(server, "server");

        if (!server.isSameThread()) throw new IllegalStateException("Machine ownership must be accessed on the server thread");

        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "overworld");
        return overworld.getDataStorage().computeIfAbsent(Objects.requireNonNull(FACTORY), DATA_NAME);
    }

    Optional<MachineOwnership> find(MachineLocation location) 
    {
        Objects.requireNonNull(location, "location");
        return Optional.ofNullable(installations.get(location));
    }

    MachineOwnership registerInstallation(MachineLocation location, ResourceLocation blockId, UUID builderId) 
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(builderId, "builderId");

        if (installations.containsKey(location)) throw new IllegalArgumentException("Machine already registered at " + location);

        MachineOwnership ownership = new MachineOwnership( UUID.randomUUID(), location, blockId, Set.of(builderId));

        installations.put(location, ownership);
        setDirty();

        return ownership;
    }

    boolean unregisterInstallation(MachineLocation location, UUID expectedInstallationId) 
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(expectedInstallationId, "expectedInstallationId");

        MachineOwnership existing = installations.get(location);

        if (existing == null || !existing.installationId().equals(expectedInstallationId)) return false;

        installations.remove(location);
        setDirty();

        return true;
    }

    private static MachineOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider registries) 
    {
        if (!tag.contains("FormatVersion", Tag.TAG_INT)) throw new IllegalStateException("Machine ownership save is missing FormatVersion");
        
        int version = tag.getInt("FormatVersion");

        if (version != FORMAT_VERSION) throw new IllegalStateException("Unsupported machine ownership format version: " + version);

        if (!tag.contains("Installations", Tag.TAG_LIST)) throw new IllegalStateException("Machine ownership save is missing Installations");

        MachineOwnershipSavedData data = new MachineOwnershipSavedData();
        ListTag installationsTag = tag.getList("Installations", Tag.TAG_COMPOUND);

        for (int i = 0; i < installationsTag.size(); i++) 
        {
            try 
            {
                CompoundTag entry = installationsTag.getCompound(i);

                UUID installationId = UUID.fromString(entry.getString("InstallationId"));
                ResourceLocation dimensionId = ResourceLocation.parse(Objects.requireNonNull(entry.getString("Dimension")));
                ResourceLocation blockId = ResourceLocation.parse(Objects.requireNonNull(entry.getString("BlockId")));

                if (!entry.contains("Position", Tag.TAG_LONG)) throw new IllegalStateException("Missing Position");

                if (!entry.contains("Owners", Tag.TAG_LIST)) throw new IllegalStateException("Missing Owners");

                ResourceKey<Level> dimension = ResourceKey.create(Objects.requireNonNull(Registries.DIMENSION), Objects.requireNonNull(dimensionId));
                BlockPos position = BlockPos.of(entry.getLong("Position"));
                MachineLocation location = new MachineLocation(dimension, position);

                ListTag ownersTag = entry.getList("Owners", Tag.TAG_STRING);
                Set<UUID> owners = new HashSet<>();

                for (int j = 0; j < ownersTag.size(); j++) 
                {
                    UUID ownerId = UUID.fromString(ownersTag.getString(j));

                    if (!owners.add(ownerId)) throw new IllegalStateException("Duplicate owner UUID: " + ownerId);
                }

                MachineOwnership ownership = new MachineOwnership(installationId, location, blockId, owners);

                if (data.installations.putIfAbsent(location, ownership) != null) throw new IllegalStateException("Duplicate machine location: " + location);
                
            } 
            catch (RuntimeException exception) 
            {
                throw new IllegalStateException("Invalid machine ownership entry " + i + " in " + DATA_NAME + ".dat: " + exception.getMessage(), exception);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        tag.putInt("FormatVersion", FORMAT_VERSION);

        ListTag installationsTag = new ListTag();

        for (MachineOwnership ownership : installations.values()) 
        {
            CompoundTag entry = new CompoundTag();

            entry.putString("InstallationId", Objects.requireNonNull(ownership.installationId().toString()));
            entry.putString("Dimension", Objects.requireNonNull(ownership.location().dimension().location().toString()));
            entry.putLong("Position", ownership.location().position().asLong());
            entry.putString("BlockId", Objects.requireNonNull(ownership.blockId().toString()));

            ListTag ownersTag = new ListTag();

            ownership.owners().stream()
                    .map(ownerId -> Objects.requireNonNull(ownerId).toString())
                    .sorted()
                    .forEach(value -> ownersTag.add(StringTag.valueOf(Objects.requireNonNull(value))));

            entry.put("Owners", ownersTag);
            installationsTag.add(entry);
        }

        tag.put("Installations", installationsTag);
        return tag;
    }
}

