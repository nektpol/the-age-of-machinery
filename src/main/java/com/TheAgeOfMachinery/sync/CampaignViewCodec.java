package com.TheAgeOfMachinery.sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CampaignViewCodec
{
    public static final int MAX_BYTES = 524288;
    private static final int MAX_TEXT = 8192;
    private static final int MAX_LIST = 4096;
    public static final StreamCodec<FriendlyByteBuf, PlayerCampaignView> CODEC = new StreamCodec<>()
    {
        @Override
        @Nonnull
        public PlayerCampaignView decode(@Nonnull FriendlyByteBuf buffer)
        {
            return Objects.requireNonNull(new Reader(buffer).view());
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buffer, @Nonnull PlayerCampaignView view)
        {
            new Writer(buffer).view(view);
        }
    };

    private CampaignViewCodec()
    {
    }

    private static final class Writer
    {
        private final FriendlyByteBuf buffer;
        private final int start;

        private Writer(FriendlyByteBuf buffer)
        {
            this.buffer = buffer;
            start = buffer.writerIndex();
        }

        private void budget()
        {
            if (buffer.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Campaign view exceeds the network size limit.");
        }

        private void text(String value)
        {
            buffer.writeUtf(Objects.requireNonNull(value), MAX_TEXT);
            budget();
        }

        private <T> void list(List<T> values, Consumer<T> writer)
        {
            if (values.size() > MAX_LIST) throw new IllegalArgumentException("Campaign view list exceeds the network limit.");
            buffer.writeVarInt(values.size());

            for (T value : values)
            {
                writer.accept(value);
                budget();
            }
        }

        private void view(PlayerCampaignView view)
        {
            buffer.writeUUID(Objects.requireNonNull(view.sessionId()));
            buffer.writeVarLong(view.playerRevision());
            buffer.writeBoolean(view.initialized());
            buffer.writeBoolean(view.campaignComplete());
            buffer.writeBoolean(view.activeQuest().isPresent());
            view.activeQuest().ifPresent(this::active);
            list(view.history(), this::completed);
            list(view.rewards(), this::group);
            budget();
        }

        private void active(PlayerCampaignView.ActiveQuest quest)
        {
            text(quest.id());
            text(quest.title());
            text(quest.description());
            list(quest.objectives(), this::objective);
            buffer.writeBoolean(quest.canAdvance());
        }

        private void completed(PlayerCampaignView.CompletedQuest quest)
        {
            text(quest.id());
            text(quest.title());
            text(quest.description());
            list(quest.objectives(), this::objective);
        }

        private void objective(PlayerCampaignView.Objective objective)
        {
            text(objective.id());
            text(objective.description());
            buffer.writeVarInt(objective.currentAmount());
            buffer.writeVarInt(objective.requiredAmount());
            buffer.writeBoolean(objective.optional());
            list(objective.ingredients(), ingredient ->
            {
                text(ingredient.resourceId());
                buffer.writeBoolean(ingredient.tag());
                buffer.writeVarInt(ingredient.amount());
            });
        }

        private void group(PlayerCampaignView.RewardGroup group)
        {
            text(group.questId());
            text(group.questTitle());
            list(group.entries(), reward ->
            {
                text(reward.id());
                buffer.writeEnum(Objects.requireNonNull(reward.kind()));
                buffer.writeVarInt(reward.amount());
                buffer.writeBoolean(reward.itemId().isPresent());
                reward.itemId().ifPresent(this::text);
                buffer.writeBoolean(reward.claimed());
            });
        }
    }

    private static final class Reader
    {
        private final FriendlyByteBuf buffer;

        private Reader(FriendlyByteBuf buffer)
        {
            if (buffer.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Campaign view exceeds the network size limit.");
            this.buffer = buffer;
        }

        private String text()
        {
            return buffer.readUtf(MAX_TEXT);
        }

        private <T> List<T> list(Supplier<T> reader)
        {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_LIST) throw new IllegalArgumentException("Invalid campaign view list length.");
            List<T> result = new ArrayList<>(count);

            for (int index = 0; index < count; index++)
            {
                result.add(reader.get());
            }

            return result;
        }

        private PlayerCampaignView view()
        {
            var session = buffer.readUUID();
            long revision = buffer.readVarLong();
            boolean initialized = buffer.readBoolean();
            boolean complete = buffer.readBoolean();
            Optional<PlayerCampaignView.ActiveQuest> active = buffer.readBoolean() ? Optional.of(active()) : Optional.empty();
            return new PlayerCampaignView(session, revision, initialized, complete, active, list(this::completed), list(this::group));
        }

        private PlayerCampaignView.ActiveQuest active()
        {
            return new PlayerCampaignView.ActiveQuest(text(), text(), text(), list(this::objective), buffer.readBoolean());
        }

        private PlayerCampaignView.CompletedQuest completed()
        {
            return new PlayerCampaignView.CompletedQuest(text(), text(), text(), list(this::objective));
        }

        private PlayerCampaignView.Objective objective()
        {
            return new PlayerCampaignView.Objective(text(), text(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), list(() -> new PlayerCampaignView.Ingredient(text(), buffer.readBoolean(), buffer.readVarInt())));
        }

        private PlayerCampaignView.RewardGroup group()
        {
            return new PlayerCampaignView.RewardGroup(text(), text(), list(() -> new PlayerCampaignView.Reward(text(), buffer.readEnum(PlayerCampaignView.RewardKind.class), buffer.readVarInt(), buffer.readBoolean() ? Optional.of(text()) : Optional.empty(), buffer.readBoolean())));
        }
    }
}
