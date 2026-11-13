package de.browars.network;

import de.browars.BroWarsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncGameshowColorSelectPayload(
        boolean openOrKeepOpen,
        int playerNumber,
        String p1Name,
        String p1ColorId,
        boolean p1Ready,
        String p2Name,
        String p2ColorId,
        boolean p2Ready,
        int remainingSeconds
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncGameshowColorSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BroWarsMod.MOD_ID, "sync_gameshow_color_select"));

    public static final StreamCodec<FriendlyByteBuf, SyncGameshowColorSelectPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.openOrKeepOpen);
                buf.writeVarInt(p.playerNumber);
                buf.writeUtf(p.p1Name);
                buf.writeUtf(p.p1ColorId);
                buf.writeBoolean(p.p1Ready);
                buf.writeUtf(p.p2Name);
                buf.writeUtf(p.p2ColorId);
                buf.writeBoolean(p.p2Ready);
                buf.writeVarInt(p.remainingSeconds);
            },
            buf -> new SyncGameshowColorSelectPayload(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
