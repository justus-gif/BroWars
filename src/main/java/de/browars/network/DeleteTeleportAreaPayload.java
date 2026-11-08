package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteTeleportAreaPayload(String areaId) implements CustomPacketPayload {
    public static final Type<DeleteTeleportAreaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "delete_teleport_area"));

    public static final StreamCodec<FriendlyByteBuf, DeleteTeleportAreaPayload> CODEC = CustomPacketPayload.codec(
            DeleteTeleportAreaPayload::write,
            DeleteTeleportAreaPayload::new
    );

    public DeleteTeleportAreaPayload(FriendlyByteBuf buf) {
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
