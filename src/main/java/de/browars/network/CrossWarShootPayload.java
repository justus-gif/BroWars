package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CrossWarShootPayload() implements CustomPacketPayload {
    public static final Type<CrossWarShootPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "crosswar_shoot"));

    public static final StreamCodec<FriendlyByteBuf, CrossWarShootPayload> CODEC = CustomPacketPayload.codec(
            CrossWarShootPayload::write,
            CrossWarShootPayload::new
    );

    public CrossWarShootPayload(FriendlyByteBuf buf) {
        this();
    }

    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
