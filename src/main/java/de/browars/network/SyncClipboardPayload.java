package de.browars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncClipboardPayload(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String dimension
) implements CustomPacketPayload {
    public static final Type<SyncClipboardPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("browars", "sync_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, SyncClipboardPayload> CODEC = CustomPacketPayload.codec(
            SyncClipboardPayload::write,
            SyncClipboardPayload::new
    );

    public SyncClipboardPayload(FriendlyByteBuf buf) {
        this(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readUtf()
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeUtf(dimension);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
