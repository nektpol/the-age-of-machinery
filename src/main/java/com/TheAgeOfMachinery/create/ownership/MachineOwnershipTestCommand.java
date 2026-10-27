
package com.TheAgeOfMachinery.create.ownership;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

public final class MachineOwnershipTestCommand {

    private MachineOwnershipTestCommand() 
    {
    }

    public static void register(RegisterCommandsEvent event) 
    {
        event.getDispatcher().register(
                Commands.literal("tamowner")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("register")
                                .then(Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()))
                                        .executes(context -> registerMachine(context, BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()))
                                        .executes(context -> showInfo(context, BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()))
                                        .executes(context -> removeMachine(context, BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos")))))
        );
    }

    private static int registerMachine(CommandContext<CommandSourceStack> context, BlockPos position) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        try {
            MachineOwnership ownership = MachineOwnershipRegistry.registerPlacement(player, position);

            context.getSource().sendSuccess(() -> message("Registered installation: " + ownership.installationId()), false);
            context.getSource().sendSuccess(() -> message("Builder / owner: " + player.getUUID()), false);

            return 1;
        } 
        catch (IllegalArgumentException exception) 
        {
            context.getSource().sendFailure(message(exception.getMessage()));
            return 0;
        }
    }

    private static int showInfo(CommandContext<CommandSourceStack> context, BlockPos position) throws CommandSyntaxException 
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<MachineOwnership> result = MachineOwnershipRegistry.find(player.serverLevel(), position);

        if (result.isEmpty()) 
        {
            context.getSource().sendFailure(message("No active registered installation at this position"));
            return 0;
        }

        MachineOwnership ownership = result.get();

        context.getSource().sendSuccess(() -> message("Installation: " + ownership.installationId()), false);
        context.getSource().sendSuccess(() -> message("Block: " + ownership.blockId()), false);
        context.getSource().sendSuccess(() -> message("Dimension: " + ownership.location().dimension().location()), false);
        context.getSource().sendSuccess(() -> message("Owners: " + ownership.owners()), false);

        return 1;
    }

    private static int removeMachine(CommandContext<CommandSourceStack> context, BlockPos position) throws CommandSyntaxException 
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<MachineOwnership> result = MachineOwnershipRegistry.find(player.serverLevel(), position);

        if (result.isEmpty()) 
        {
            context.getSource().sendFailure(message("No active registered installation at this position"));
            return 0;
        }

        boolean removed = MachineOwnershipRegistry.unregister(player.serverLevel(), position, result.get().installationId());

        if (!removed) 
        {
            context.getSource().sendFailure(message("Installation was not removed"));
            return 0;
        }

        context.getSource().sendSuccess(() -> message("Machine ownership removed"), false);
        return 1;
    }

    @Nonnull
    private static Component message(String text) 
    {
        String safeText = text == null ? "Unknown error" : text;
        return Objects.requireNonNull(Component.literal(Objects.requireNonNull(safeText)));
    }
}
