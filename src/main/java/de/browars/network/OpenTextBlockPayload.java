package de.browars.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenTextBlockPayload(
        BlockPos pos,
        String text
) implements CustomPacketPayload {
    public static final Type<OpenTextBlockPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "open_text_block"));

    public static final StreamCodec<FriendlyByteBuf, OpenTextBlockPayload> CODEC = CustomPacketPayload.codec(
            OpenTextBlockPayload::write,
            OpenTextBlockPayload::new
    );

    public OpenTextBlockPayload(FriendlyByteBuf buf) {
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
