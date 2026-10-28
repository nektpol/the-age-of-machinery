package com.TheAgeOfMachinery.runtime;

import com.TheAgeOfMachinery.sync.CampaignDeliveryQueue;
import com.TheAgeOfMachinery.sync.CampaignViewCodec;
import com.TheAgeOfMachinery.sync.ClientCampaignState;
import com.TheAgeOfMachinery.sync.PlayerCampaignPayload;
import com.TheAgeOfMachinery.sync.PlayerCampaignView;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class CampaignNetworkRegression
{
    public static void main(String[] arguments)
    {
        UUID session = UUID.randomUUID();
        var objective = new PlayerCampaignView.Objective("craft", "Craft a table", 1, 1, false, List.of(new PlayerCampaignView.Ingredient("minecraft:planks", true, 4), new PlayerCampaignView.Ingredient("minecraft:iron_ingot", false, 1)));
        var active = new PlayerCampaignView.ActiveQuest("test:active", "Active quest — δοκιμή", "Description", List.of(objective), true);
        var completed = new PlayerCampaignView.CompletedQuest("test:past", "Past quest", "Past description", List.of(objective));
        var rewards = new PlayerCampaignView.RewardGroup("test:past", "Past quest", List.of(new PlayerCampaignView.Reward("xp", PlayerCampaignView.RewardKind.EXPERIENCE, 25, Optional.empty(), true), new PlayerCampaignView.Reward("iron", PlayerCampaignView.RewardKind.ITEM, 2, Optional.of("minecraft:iron_ingot"), false)));
        PlayerCampaignView view = new PlayerCampaignView(session, 7, true, false, Optional.of(active), List.of(completed), List.of(rewards));
        roundTrip(new PlayerCampaignPayload(1, view));
        roundTrip(new PlayerCampaignPayload(2, new PlayerCampaignView(session, 0, false, false, Optional.empty(), List.of(), List.of())));
        roundTrip(new PlayerCampaignPayload(3, new PlayerCampaignView(session, 9, true, true, Optional.empty(), List.of(completed), List.of(rewards))));

        FriendlyByteBuf negativeList = buffer();
        try
        {
            negativeList.writeUUID(Objects.requireNonNull(session));
            negativeList.writeVarLong(0);
            negativeList.writeBoolean(false);
            negativeList.writeBoolean(false);
            negativeList.writeBoolean(false);
            negativeList.writeVarInt(-1);
            expectFailure(() -> CampaignViewCodec.CODEC.decode(negativeList));
        }
        finally
        {
            negativeList.release();
        }

        FriendlyByteBuf excessive = buffer();
        try
        {
            excessive.writeZero(CampaignViewCodec.MAX_BYTES + 1);
            expectFailure(() -> CampaignViewCodec.CODEC.decode(excessive));
        }
        finally
        {
            excessive.release();
        }

        FriendlyByteBuf tooManyEntries = buffer();
        try
        {
            var huge = new PlayerCampaignView(session, 1, true, true, Optional.empty(), Collections.nCopies(4097, completed), List.of());
            expectFailure(() -> CampaignViewCodec.CODEC.encode(tooManyEntries, huge));
        }
        finally
        {
            tooManyEntries.release();
        }

        FriendlyByteBuf excessiveBody = buffer();
        try
        {
            var longHistory = new PlayerCampaignView.CompletedQuest("test:past", "Past quest", "X".repeat(8192), List.of(objective));
            var huge = new PlayerCampaignView(session, 1, true, true, Optional.empty(), Collections.nCopies(80, longHistory), List.of());
            expectFailure(() -> CampaignViewCodec.CODEC.encode(excessiveBody, huge));
        }
        finally
        {
            excessiveBody.release();
        }

        FriendlyByteBuf truncated = buffer();
        try
        {
            PlayerCampaignPayload.CODEC.encode(truncated, new PlayerCampaignPayload(1, view));
            truncated.writerIndex(truncated.writerIndex() - 1);
            expectFailure(() -> PlayerCampaignPayload.CODEC.decode(truncated));
        }
        finally
        {
            truncated.release();
        }

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Object connection = new Object();
        Object otherConnection = new Object();
        CampaignDeliveryQueue queue = new CampaignDeliveryQueue();
        queue.mark(first);
        queue.mark(first);
        queue.mark(second);
        require(queue.drain().equals(List.of(first, second)), "Player changes were not batched independently.");
        require(queue.drain().isEmpty(), "Queue replayed drained entries.");
        require(queue.nextSequence(first, connection) == 1 && queue.nextSequence(first, connection) == 2, "Sequence failed to advance.");
        require(queue.nextSequence(second, otherConnection) == 1, "One player's sequence contaminated another.");
        require(queue.nextSequence(first, otherConnection) == 1, "Reconnection did not reset the delivery sequence.");
        queue.mark(first);
        queue.forget(first);
        require(queue.drain().isEmpty() && queue.nextSequence(first, connection) == 1, "Logout left queued state or counters behind.");

        ClientCampaignState.connected(connection);
        require(ClientCampaignState.receive(connection, new PlayerCampaignPayload(1, view)), "Initial client snapshot was rejected.");
        require(!ClientCampaignState.receive(otherConnection, new PlayerCampaignPayload(5, view)), "Another connection overwrote client state.");
        require(!ClientCampaignState.receive(connection, new PlayerCampaignPayload(1, view)), "Duplicate sequence was accepted.");
        var renamed = new PlayerCampaignView.ActiveQuest(active.id(), "Reloaded title", active.description(), active.objectives(), true);
        var refreshed = new PlayerCampaignView(session, 7, true, false, Optional.of(renamed), view.history(), view.rewards());
        require(ClientCampaignState.receive(connection, new PlayerCampaignPayload(2, refreshed)), "Cosmetic reload at unchanged player revision was rejected.");
        require(!ClientCampaignState.receive(connection, new PlayerCampaignPayload(1, view)), "Stale state replaced a newer snapshot.");
        var otherSession = new PlayerCampaignView(UUID.randomUUID(), 7, true, false, view.activeQuest(), view.history(), view.rewards());
        require(!ClientCampaignState.receive(connection, new PlayerCampaignPayload(3, otherSession)), "Unexpected server session overwrote the current session.");
        ClientCampaignState.connected(otherConnection);
        require(ClientCampaignState.snapshot().isEmpty(), "Reconnection retained the previous player's cached data.");
        require(!ClientCampaignState.receive(connection, new PlayerCampaignPayload(99, view)), "Late old-connection packet was accepted.");
        require(ClientCampaignState.receive(otherConnection, new PlayerCampaignPayload(1, otherSession)), "New connection did not accept its first snapshot.");
        ClientCampaignState.disconnected(connection);
        require(ClientCampaignState.snapshot().isPresent(), "An old disconnect cleared a newer connection.");
        ClientCampaignState.disconnected(otherConnection);
        require(ClientCampaignState.snapshot().isEmpty(), "Disconnect left client data behind.");

        System.out.println("PASS: payload round trips, bounded/truncated decoding, batching, recipient sequences, reconnect clearing, stale packet rejection and same-revision reload refresh.");
    }

    @Nonnull
    private static FriendlyByteBuf buffer()
    {
        return new FriendlyByteBuf(Objects.requireNonNull(Unpooled.buffer()));
    }

    private static void roundTrip(PlayerCampaignPayload payload)
    {
        FriendlyByteBuf buffer = buffer();

        try
        {
            PlayerCampaignPayload.CODEC.encode(buffer, Objects.requireNonNull(payload));
            require(payload.equals(PlayerCampaignPayload.CODEC.decode(buffer)), "Payload changed while encoding/decoding.");
            require(buffer.readableBytes() == 0, "Payload decoder left unread fields.");
        }
        finally
        {
            buffer.release();
        }
    }

    private static void expectFailure(Runnable action)
    {
        try
        {
            action.run();
        }
        catch (RuntimeException expected)
        {
            return;
        }

        throw new AssertionError("Malformed or oversized payload was accepted.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
