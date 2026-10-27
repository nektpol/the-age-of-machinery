package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.create.ownership.MachineOwnership;
import com.TheAgeOfMachinery.create.ownership.MachineOwnershipRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

public final class CreateKineticStatusCommand
{
    private CreateKineticStatusCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(
            Commands.literal("tamkinetic")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                    .then(Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()))
                        .executes(context -> inspect(context.getSource(), BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos")))))
        );
    }

    private static int inspect(CommandSourceStack source, BlockPos position) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos machinePosition = Objects.requireNonNull(position);

        Optional<MachineOwnership> result = MachineOwnershipRegistry.find(level, machinePosition);

        if (result.isEmpty()) return failure(source, "No registered kinetic machine at this position.");

        BlockEntity blockEntity = level.getBlockEntity(machinePosition);

        if (!(blockEntity instanceof KineticBlockEntity kinetic)) return failure(source, "Registered block has no Create kinetic block entity.");

        MachineOwnership ownership = result.get();

        float effectiveSpeed = kinetic.getSpeed();
        float theoreticalSpeed = kinetic.getTheoreticalSpeed();
        boolean overstressed = kinetic.isOverStressed();
        boolean meetsSpeedRequirement = kinetic.isSpeedRequirementFulfilled();

        success(source, "Machine: " + ownership.blockId());
        success(source, "Installation: " + ownership.installationId());
        success(source, "Effective speed: " + effectiveSpeed + " RPM");
        success(source, "Theoretical speed: " + theoreticalSpeed + " RPM");
        success(source, "Overstressed: " + overstressed);
        success(source, "Create speed requirement fulfilled: " + meetsSpeedRequirement);
        success(source, "Owners: " + ownership.owners());

        return 1;
    }

    private static int success(CommandSourceStack source, String text)
    {
        source.sendSuccess(() -> message(text), false);
        return 1;
    }

    private static int failure(CommandSourceStack source, String text)
    {
        source.sendFailure(message(text));
        return 0;
    }

    @Nonnull
    private static Component message(String text)
    {
        return Objects.requireNonNull(Component.literal(Objects.requireNonNull(text)));
    }
}

