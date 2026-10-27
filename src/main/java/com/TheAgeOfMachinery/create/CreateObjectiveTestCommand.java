package com.TheAgeOfMachinery.create;

import com.TheAgeOfMachinery.create.ownership.MachineOwnershipRegistry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

public final class CreateObjectiveTestCommand
{
    private CreateObjectiveTestCommand()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        var root = Commands.literal("tamcreate").requires(source -> source.hasPermission(2));

        var kinetic = Commands.literal("kinetic");
        var kineticPosition = Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()));
        var kineticSpeed = Commands.argument("speed", Objects.requireNonNull(FloatArgumentType.floatArg(0.01f)));
        var overstressed = Commands.argument("overstressed", Objects.requireNonNull(BoolArgumentType.bool()));

        overstressed.executes(context -> simulateKinetic(context, BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos"), FloatArgumentType.getFloat(context, "speed"), BoolArgumentType.getBool(context, "overstressed")));

        kineticSpeed.then(overstressed);
        kineticPosition.then(kineticSpeed);
        kinetic.then(kineticPosition);
        root.then(kinetic);

        var process = Commands.literal("process");
        var processPosition = Commands.argument("pos", Objects.requireNonNull(BlockPosArgument.blockPos()));
        var output = Commands.argument("output", Objects.requireNonNull(ResourceLocationArgument.id()));
        var amount = Commands.argument("amount", Objects.requireNonNull(IntegerArgumentType.integer(1)));
        var operation = Commands.argument("operationId", Objects.requireNonNull(StringArgumentType.word()));

        operation.executes(context -> simulateProcessing(context, BlockPosArgument.getLoadedBlockPos(Objects.requireNonNull(context), "pos"), Objects.requireNonNull(ResourceLocationArgument.getId(context, "output")), IntegerArgumentType.getInteger(context, "amount"), StringArgumentType.getString(context, "operationId")));

        amount.then(operation);
        output.then(amount);
        processPosition.then(output);
        process.then(processPosition);
        root.then(process);

        event.getDispatcher().register(root);
    }

    private static int simulateKinetic(CommandContext<CommandSourceStack> context, BlockPos position, float speed, boolean overstressed) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        if (MachineOwnershipRegistry.find(level, position).isEmpty()) return failure(context.getSource(), "Machine is not registered.");

        ResourceLocation blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(level.getBlockState(Objects.requireNonNull(position)).getBlock())));
        MachineLocation location = new MachineLocation(level.dimension(), position);
        KineticPowerEvent event = new KineticPowerEvent(location, blockId, speed, overstressed);

        int updates = CreateObjectiveRouter.route(level, event);

        return success(context.getSource(), "Simulated kinetic event. Objective updates: " + updates);
    }

    private static int simulateProcessing(CommandContext<CommandSourceStack> context, BlockPos position, ResourceLocation outputId, int amount, String operationName) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        if (MachineOwnershipRegistry.find(level, position).isEmpty()) return failure(context.getSource(), "Machine is not registered.");

        UUID operationId;

        try
        {
            operationId = UUID.fromString(operationName);
        }
        catch (IllegalArgumentException exception)
        {
            return failure(context.getSource(), "Invalid operation UUID.");
        }

        if (!BuiltInRegistries.ITEM.containsKey(Objects.requireNonNull(outputId))) return failure(context.getSource(), "Output item is not registered.");

        ResourceLocation blockId = Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(level.getBlockState(Objects.requireNonNull(position)).getBlock())));
        MachineLocation location = new MachineLocation(level.dimension(), position);
        ProcessingCompletedEvent event = new ProcessingCompletedEvent(location, blockId, operationId, outputId, amount);

        int updates = CreateObjectiveRouter.route(level, event);

        return success(context.getSource(), "Simulated processing event. Objective updates: " + updates);
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
