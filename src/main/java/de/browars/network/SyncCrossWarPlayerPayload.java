package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncCrossWarPlayerPayload(int ammo, int reloadTicks, boolean isReloading) implements CustomPacketPayload {
    public static final Type<SyncCrossWarPlayerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_crosswar_player"));

    public static final StreamCodec<FriendlyByteBuf, SyncCrossWarPlayerPayload> CODEC = CustomPacketPayload.codec(
            SyncCrossWarPlayerPayload::write,
            SyncCrossWarPlayerPayload::new
    );

    public SyncCrossWarPlayerPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(ammo);
        buf.writeInt(reloadTicks);
        buf.writeBoolean(isReloading);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
