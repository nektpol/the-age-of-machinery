package com.TheAgeOfMachinery.sync.client;

import com.TheAgeOfMachinery.sync.ClientCampaignState;
import com.TheAgeOfMachinery.sync.PlayerCampaignView;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Objects;

@EventBusSubscriber(modid = "theageofmachinery", value = Dist.CLIENT)
public final class ClientCampaignEvents
{
    private ClientCampaignEvents()
    {
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event)
    {
        ClientCampaignState.connected(event.getConnection());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientCampaignState.disconnected(event.getConnection());
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("tamclient").then(Commands.literal("state").executes(context -> inspect(context.getSource()))));
    }

    private static int inspect(CommandSourceStack source)
    {
        var snapshot = ClientCampaignState.snapshot();

        if (snapshot.isEmpty())
        {
            message(source, "No campaign snapshot received on this connection.");
            return 0;
        }

        var payload = snapshot.get();
        PlayerCampaignView view = payload.view();
        message(source, "Campaign snapshot #" + payload.sequence() + "; player revision " + view.playerRevision() + "; initialized=" + view.initialized() + "; complete=" + view.campaignComplete());

        if (view.activeQuest().isPresent())
        {
            var active = view.activeQuest().get();
            message(source, "Active: " + active.id() + " - " + active.title() + "; canAdvance=" + active.canAdvance());

            for (var objective : active.objectives())
            {
                message(source, objective.id() + ": " + objective.currentAmount() + "/" + objective.requiredAmount() + (objective.optional() ? " (optional)" : ""));
            }
        }

        message(source, "Completed history: " + view.history().size() + "; earned reward groups: " + view.rewards().size());

        for (var group : view.rewards())
        {
            for (var reward : group.entries())
            {
                message(source, group.questId() + "/" + reward.id() + ": " + reward.amount() + " " + reward.itemId().orElse("XP") + (reward.claimed() ? " (claimed)" : " (available)"));
            }
        }

        return 1;
    }

    private static void message(CommandSourceStack source, String text)
    {
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal(Objects.requireNonNull(text))), false);
    }
}
