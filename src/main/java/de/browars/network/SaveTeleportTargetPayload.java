package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SaveTeleportTargetPayload(
        BlockPos pos,
        String name,
        int colorHex
) implements CustomPacketPayload {
    public static final Type<SaveTeleportTargetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "save_teleport_target"));

    public static final StreamCodec<FriendlyByteBuf, SaveTeleportTargetPayload> CODEC = CustomPacketPayload.codec(
            SaveTeleportTargetPayload::write,
            SaveTeleportTargetPayload::new
    );

    public SaveTeleportTargetPayload(FriendlyByteBuf buf) {
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
