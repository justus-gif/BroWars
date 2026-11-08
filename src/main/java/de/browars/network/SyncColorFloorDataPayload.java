package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncColorFloorDataPayload(
        boolean hasArena,
        BlockPos minPos,
        BlockPos maxPos,
        String dimension,
        boolean hasSpawn1,
        double s1X, double s1Y, double s1Z,
        boolean hasSpawn2,
        double s2X, double s2Y, double s2Z,
        String team1Color,
        String team2Color
) implements CustomPacketPayload {
    public static final Type<SyncColorFloorDataPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_color_floor_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncColorFloorDataPayload> CODEC = CustomPacketPayload.codec(
            SyncColorFloorDataPayload::write,
            SyncColorFloorDataPayload::new
    );

    public SyncColorFloorDataPayload(FriendlyByteBuf buf) {
        this(
                buf.readBoolean(),
                buf.readBlockPos(),
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(hasArena);
        buf.writeBlockPos(minPos);
        buf.writeBlockPos(maxPos);
        buf.writeUtf(dimension);
        buf.writeBoolean(hasSpawn1);
        buf.writeDouble(s1X); buf.writeDouble(s1Y); buf.writeDouble(s1Z);
        buf.writeBoolean(hasSpawn2);
        buf.writeDouble(s2X); buf.writeDouble(s2Y); buf.writeDouble(s2Z);
        buf.writeUtf(team1Color);
        buf.writeUtf(team2Color);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
