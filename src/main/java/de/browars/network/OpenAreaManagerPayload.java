package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenAreaManagerPayload(
        BlockPos pos1,
        BlockPos pos2
) implements CustomPacketPayload {
    public static final Type<OpenAreaManagerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_area_manager"));

    public static final StreamCodec<FriendlyByteBuf, OpenAreaManagerPayload> CODEC = CustomPacketPayload.codec(
            OpenAreaManagerPayload::write,
            OpenAreaManagerPayload::new
    );

    public OpenAreaManagerPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos1);
        buf.writeBlockPos(pos2);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
