package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenBoostPadPayload(
        BlockPos pos,
        double strength,
        int colorHex,
        boolean lookDirectionMode
) implements CustomPacketPayload {
    public static final Type<OpenBoostPadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_boost_pad"));

    public static final StreamCodec<FriendlyByteBuf, OpenBoostPadPayload> CODEC = CustomPacketPayload.codec(
            OpenBoostPadPayload::write,
            OpenBoostPadPayload::new
    );

    public OpenBoostPadPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readDouble(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(strength);
        buf.writeInt(colorHex);
        buf.writeBoolean(lookDirectionMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
