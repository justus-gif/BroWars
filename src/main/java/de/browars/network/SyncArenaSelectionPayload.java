package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record SyncArenaSelectionPayload(
        Optional<BlockPos> pos1,
        Optional<BlockPos> pos2
) implements CustomPacketPayload {
    public static final Type<SyncArenaSelectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_arena_selection"));

    public static final StreamCodec<FriendlyByteBuf, SyncArenaSelectionPayload> CODEC = CustomPacketPayload.codec(
            SyncArenaSelectionPayload::write,
            SyncArenaSelectionPayload::new
    );

    public SyncArenaSelectionPayload(FriendlyByteBuf buf) {
        this(
                buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty(),
                buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(pos1.isPresent());
        pos1.ifPresent(buf::writeBlockPos);
        buf.writeBoolean(pos2.isPresent());
        pos2.ifPresent(buf::writeBlockPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
