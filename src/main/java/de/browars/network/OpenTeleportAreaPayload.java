package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenTeleportAreaPayload(
        String areaId,
        BlockPos pos1,
        BlockPos pos2,
        double targetX,
        double targetY,
        double targetZ,
        float targetYaw,
        float targetPitch,
        String targetDimension,
        String name,
        int colorHex,
        boolean visibleInSurvival,
        boolean showNameHologram
) implements CustomPacketPayload {
    public static final Type<OpenTeleportAreaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_teleport_area"));

    public static final StreamCodec<FriendlyByteBuf, OpenTeleportAreaPayload> CODEC = CustomPacketPayload.codec(
            OpenTeleportAreaPayload::write,
            OpenTeleportAreaPayload::new
    );

    public OpenTeleportAreaPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readBlockPos(),
                buf.readBlockPos(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(areaId != null ? areaId : "");
        buf.writeBlockPos(pos1);
        buf.writeBlockPos(pos2);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeUtf(targetDimension);
        buf.writeUtf(name);
        buf.writeInt(colorHex);
        buf.writeBoolean(visibleInSurvival);
        buf.writeBoolean(showNameHologram);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
