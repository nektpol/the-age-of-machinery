package com.TheAgeOfMachinery.sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Objects;

public record PlayerCampaignPayload(long sequence, PlayerCampaignView view) implements CustomPacketPayload
{
    public static final Type<PlayerCampaignPayload> TYPE = new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("theageofmachinery", "player_campaign")));
    public static final StreamCodec<FriendlyByteBuf, PlayerCampaignPayload> CODEC = new StreamCodec<>()
    {
        @Override
        @Nonnull
        public PlayerCampaignPayload decode(@Nonnull FriendlyByteBuf buffer)
        {
            return new PlayerCampaignPayload(buffer.readVarLong(), CampaignViewCodec.CODEC.decode(buffer));
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buffer, @Nonnull PlayerCampaignPayload payload)
        {
            buffer.writeVarLong(payload.sequence());
            CampaignViewCodec.CODEC.encode(buffer, Objects.requireNonNull(payload.view()));
        }
    };

    public PlayerCampaignPayload
    {
        if (sequence <= 0) throw new IllegalArgumentException("Delivery sequence must be positive.");
        Objects.requireNonNull(view, "Player view cannot be null.");
    }

    @Override
    @Nonnull
    public Type<PlayerCampaignPayload> type()
    {
        return Objects.requireNonNull(TYPE);
    }
}
