package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncPaintWarRoulettePayload(int rewardOrdinal, int totalSpinTicks) implements CustomPacketPayload {
    public static final Type<SyncPaintWarRoulettePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_paintwar_roulette"));

    public static final StreamCodec<FriendlyByteBuf, SyncPaintWarRoulettePayload> CODEC = CustomPacketPayload.codec(
            SyncPaintWarRoulettePayload::write,
            SyncPaintWarRoulettePayload::new
    );

    public SyncPaintWarRoulettePayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(rewardOrdinal);
        buf.writeInt(totalSpinTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
