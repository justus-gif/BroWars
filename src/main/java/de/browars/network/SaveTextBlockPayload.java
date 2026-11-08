package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SaveTextBlockPayload(
        BlockPos pos,
        String text
) implements CustomPacketPayload {
    public static final Type<SaveTextBlockPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "save_text_block"));

    public static final StreamCodec<FriendlyByteBuf, SaveTextBlockPayload> CODEC = CustomPacketPayload.codec(
            SaveTextBlockPayload::write,
            SaveTextBlockPayload::new
    );

    public SaveTextBlockPayload(FriendlyByteBuf buf) {
        this(
                buf.readBlockPos(),
                buf.readUtf(65535)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(text, 65535);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
