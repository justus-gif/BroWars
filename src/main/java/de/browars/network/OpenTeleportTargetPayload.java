package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenTeleportTargetPayload(
        BlockPos pos,
        String name,
        int colorHex
) implements CustomPacketPayload {
    public static final Type<OpenTeleportTargetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_teleport_target"));

    public static final StreamCodec<FriendlyByteBuf, OpenTeleportTargetPayload> CODEC = CustomPacketPayload.codec(
            OpenTeleportTargetPayload::write,
            OpenTeleportTargetPayload::new
    );

    public OpenTeleportTargetPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readUtf(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(name != null ? name : "");
        buf.writeInt(colorHex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
