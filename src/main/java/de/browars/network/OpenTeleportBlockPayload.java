package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenTeleportBlockPayload(
        BlockPos pos,
        double targetX,
        double targetY,
        double targetZ,
        float targetYaw,
        float targetPitch,
        String targetDimension,
        String name,
        int colorHex
) implements CustomPacketPayload {
    public static final Type<OpenTeleportBlockPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_teleport_block"));

    public static final StreamCodec<FriendlyByteBuf, OpenTeleportBlockPayload> CODEC = CustomPacketPayload.codec(
            OpenTeleportBlockPayload::write,
            OpenTeleportBlockPayload::new
    );

    public OpenTeleportBlockPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeUtf(targetDimension);
        buf.writeUtf(name);
        buf.writeInt(colorHex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
