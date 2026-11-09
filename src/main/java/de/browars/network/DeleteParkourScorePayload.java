package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DeleteParkourScorePayload(
        BlockPos pos,
        int scoreIndex
) implements CustomPacketPayload {
    public static final Type<DeleteParkourScorePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "delete_parkour_score"));

    public static final StreamCodec<FriendlyByteBuf, DeleteParkourScorePayload> CODEC = CustomPacketPayload.codec(
            DeleteParkourScorePayload::write,
            DeleteParkourScorePayload::new
    );

    public DeleteParkourScorePayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(scoreIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
