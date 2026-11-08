package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CrossWarReloadPayload(boolean holding) implements CustomPacketPayload {
    public static final Type<CrossWarReloadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "crosswar_reload"));

    public static final StreamCodec<FriendlyByteBuf, CrossWarReloadPayload> CODEC = CustomPacketPayload.codec(
            CrossWarReloadPayload::write,
            CrossWarReloadPayload::new
    );

    public CrossWarReloadPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(holding);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
