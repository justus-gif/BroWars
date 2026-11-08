package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenUpwardBoosterPayload(
        BlockPos pos,
        double height
) implements CustomPacketPayload {
    public static final Type<OpenUpwardBoosterPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_upward_booster"));

    public static final StreamCodec<FriendlyByteBuf, OpenUpwardBoosterPayload> CODEC = CustomPacketPayload.codec(
            OpenUpwardBoosterPayload::write,
            OpenUpwardBoosterPayload::new
    );

    public OpenUpwardBoosterPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readDouble());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(height);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
