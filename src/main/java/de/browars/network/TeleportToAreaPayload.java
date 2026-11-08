package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TeleportToAreaPayload(String areaId) implements CustomPacketPayload {
    public static final Type<TeleportToAreaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "teleport_to_area"));

    public static final StreamCodec<FriendlyByteBuf, TeleportToAreaPayload> CODEC = CustomPacketPayload.codec(
            TeleportToAreaPayload::write,
            TeleportToAreaPayload::new
    );

    public TeleportToAreaPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(areaId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
